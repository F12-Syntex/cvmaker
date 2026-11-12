package com.cvmaker.application.management;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.sheets.v4.SheetsScopes;

public class GmailService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Arrays.asList(
            GmailScopes.GMAIL_READONLY,
            GmailScopes.GMAIL_LABELS,
            SheetsScopes.DRIVE,
            SheetsScopes.SPREADSHEETS
    );

    private Gmail service;

    public void initialize() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                .setClientId(ApplicationConfig.require(ApplicationConfig.GOOGLE_CLIENT_ID))
                .setClientSecret(ApplicationConfig.require(ApplicationConfig.GOOGLE_CLIENT_SECRET));
        GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(ApplicationConfig.TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        this.service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(ApplicationConfig.APPLICATION_NAME)
                .build();
    }

    /** Upper bound on result pages read per query (500 IDs each), so a broad query cannot run forever. */
    static final int MAX_PAGES_PER_QUERY = 20;

    private static final String[] SEARCH_QUERIES = {
        // Basic job application terms
        "(\"application received\" OR \"thank you for applying\" OR \"application confirmation\")",
        // Interview related
        "(interview OR \"schedule an interview\" OR \"interview invitation\" OR \"phone screen\")",
        // Status updates
        "(\"application status\" OR \"hiring decision\" OR \"next steps\" OR \"moving forward\")",
        // Rejections and offers
        "(unfortunately OR \"not selected\" OR \"decided to move forward\" OR \"job offer\" OR \"pleased to offer\")",
        // Assessments and tests
        "(\"coding assessment\" OR \"technical test\" OR \"complete the following\" OR assessment OR challenge)",
        // From common job-related senders
        "from:(hr OR recruiting OR talent OR careers OR noreply OR no-reply)",
        // Broader job-related terms
        "(position OR role OR \"your application\" OR \"your interview\" OR \"your submission\" OR candidate)",
        // Company-specific patterns
        "(\"we received your\" OR \"thank you for your interest\" OR \"regarding your application\")",
        // Additional comprehensive search
        "subject:(job OR position OR interview OR application OR offer OR rejected OR assessment)",
    };

    /**
     * Runs the job-related searches and returns the IDs of messages not in {@code processedEmailIds}.
     * Every result page is read (up to {@link #MAX_PAGES_PER_QUERY} per query). The optional
     * settings {@value ApplicationConfig#GMAIL_SEARCH_AFTER} and
     * {@value ApplicationConfig#GMAIL_SEARCH_BEFORE} ({@code yyyy/MM/dd}) limit the date range.
     */
    public Set<String> findUnprocessedEmails(Set<String> processedEmailIds) throws IOException {
        String dateRange = buildDateRange(
                ApplicationConfig.get(ApplicationConfig.GMAIL_SEARCH_AFTER),
                ApplicationConfig.get(ApplicationConfig.GMAIL_SEARCH_BEFORE));

        Set<String> allMessageIds = new LinkedHashSet<>();

        for (int i = 0; i < SEARCH_QUERIES.length; i++) {
            String query = dateRange.isEmpty() ? SEARCH_QUERIES[i] : SEARCH_QUERIES[i] + " " + dateRange;
            System.out.println("Executing search query " + (i + 1) + "/" + SEARCH_QUERIES.length + ": " + query);

            try {
                List<Message> messages = listAllMessages(pageToken -> service.users().messages().list("me")
                        .setQ(query)
                        .setMaxResults(500L)
                        .setPageToken(pageToken)
                        .execute(), MAX_PAGES_PER_QUERY);

                if (!messages.isEmpty()) {
                    int newMessages = 0;
                    for (Message message : messages) {
                        String id = message.getId();
                        if (id != null && !processedEmailIds.contains(id) && allMessageIds.add(id)) {
                            newMessages++;
                        }
                    }
                    System.out.println("  Found " + messages.size() + " total, " + newMessages + " new unprocessed emails");
                } else {
                    System.out.println("  No emails found for this query");
                }

                // Small delay between queries to be respectful to the API
                Thread.sleep(200);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Email search interrupted after query " + (i + 1));
                break;
            } catch (Exception e) {
                System.err.println("Error executing query " + (i + 1) + ": " + e.getMessage());
            }
        }

        System.out.println("Total unique unprocessed emails found: " + allMessageIds.size());
        return allMessageIds;
    }

    /** Fetches one page of a message listing; {@code pageToken} is {@code null} for the first page. */
    @FunctionalInterface
    interface PageFetcher {
        ListMessagesResponse fetch(String pageToken) throws IOException;
    }

    /** Follows {@code nextPageToken} until the last page or {@code maxPages} pages. */
    static List<Message> listAllMessages(PageFetcher fetcher, int maxPages) throws IOException {
        List<Message> all = new ArrayList<>();
        String pageToken = null;
        int pages = 0;
        do {
            ListMessagesResponse response = fetcher.fetch(pageToken);
            pages++;
            if (response == null) {
                break;
            }
            if (response.getMessages() != null) {
                all.addAll(response.getMessages());
            }
            pageToken = response.getNextPageToken();
        } while (pageToken != null && !pageToken.isEmpty() && pages < maxPages);

        if (pageToken != null && !pageToken.isEmpty()) {
            System.err.println("  Stopped after " + maxPages + " pages; more results exist");
        }
        return all;
    }

    private static final Pattern GMAIL_DATE = Pattern.compile("(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})");

    /**
     * Builds the Gmail date filter, e.g. {@code after:2025/07/07 before:2025/07/15} (before is
     * exclusive). Either bound may be null or blank.
     *
     * @throws IllegalArgumentException when a bound is not {@code yyyy/MM/dd} or {@code yyyy-MM-dd}
     */
    static String buildDateRange(String after, String before) {
        StringBuilder range = new StringBuilder();
        appendDateBound(range, "after", ApplicationConfig.GMAIL_SEARCH_AFTER, after);
        appendDateBound(range, "before", ApplicationConfig.GMAIL_SEARCH_BEFORE, before);
        return range.toString();
    }

    private static void appendDateBound(StringBuilder range, String operator, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Matcher m = GMAIL_DATE.matcher(value.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException(key + " must be a date like 2025/07/07, got: " + value);
        }
        if (range.length() > 0) {
            range.append(' ');
        }
        range.append(operator).append(':').append(m.group(1)).append('/').append(m.group(2)).append('/').append(m.group(3));
    }

    public EmailData getEmailContent(String messageId) throws IOException {
        Message fullMessage = service.users().messages().get("me", messageId)
                .setFormat("full")
                .execute();
        return toEmailData(messageId, fullMessage);
    }

    /** Converts a message fetched with format {@code full}; null-safe throughout. */
    static EmailData toEmailData(String messageId, Message message) {
        MessagePart payload = message.getPayload();
        List<MessagePartHeader> headers = payload != null ? payload.getHeaders() : null;

        String subject = headerValue(headers, "Subject");
        String from = headerValue(headers, "From");
        String date = effectiveDate(headerValue(headers, "Date"), message.getInternalDate());

        String body = extractBody(payload);
        if (body.isEmpty() && message.getSnippet() != null) {
            body = message.getSnippet();
        }
        return new EmailData(messageId, subject, from, date, body);
    }

    /** First header named {@code name} (case-insensitive), or {@code ""}. */
    static String headerValue(List<MessagePartHeader> headers, String name) {
        if (headers == null) {
            return "";
        }
        for (MessagePartHeader header : headers) {
            if (header != null && name.equalsIgnoreCase(header.getName())) {
                return header.getValue() != null ? header.getValue() : "";
            }
        }
        return "";
    }

    /**
     * The {@code Date} header when it can be parsed, otherwise Gmail's {@code internalDate} (epoch
     * millis, when the message was received) as an RFC 1123 date in UTC. The result feeds
     * {@link JobApplicationData#getEmailTimestamp()}, which decides the latest status per company.
     */
    static String effectiveDate(String dateHeader, Long internalDateMillis) {
        if (internalDateMillis == null
                || JobApplicationData.parseEmailTimestamp(dateHeader, ZoneOffset.UTC) != null) {
            return dateHeader;
        }
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(
                Instant.ofEpochMilli(internalDateMillis).atZone(ZoneOffset.UTC));
    }

    /**
     * Text of a message: all {@code text/plain} parts, or the {@code text/html} parts when there is
     * no plain text (so a {@code multipart/alternative} body is not duplicated). Attachments are
     * skipped. Each part is decoded with the charset from its {@code Content-Type}, default UTF-8.
     */
    static String extractBody(MessagePart payload) {
        if (payload == null) {
            return "";
        }
        List<String> plain = new ArrayList<>();
        List<String> html = new ArrayList<>();
        collectText(payload, plain, html);
        return String.join("\n", plain.isEmpty() ? html : plain);
    }

    private static void collectText(MessagePart part, List<String> plain, List<String> html) {
        String mimeType = part.getMimeType() != null ? part.getMimeType().toLowerCase(Locale.ROOT) : "";
        if (part.getParts() != null && !part.getParts().isEmpty()) {
            for (MessagePart subPart : part.getParts()) {
                if (subPart != null) {
                    collectText(subPart, plain, html);
                }
            }
            return;
        }
        if (mimeType.startsWith("multipart/") || isAttachment(part)) {
            return;
        }
        String text = decodeText(part);
        if (text.isEmpty()) {
            return;
        }
        if (mimeType.startsWith("text/html")) {
            html.add(text);
        } else if (mimeType.isEmpty() || mimeType.startsWith("text/plain")) {
            plain.add(text);
        }
    }

    private static boolean isAttachment(MessagePart part) {
        if (part.getFilename() != null && !part.getFilename().isEmpty()) {
            return true;
        }
        return headerValue(part.getHeaders(), "Content-Disposition").trim().toLowerCase(Locale.ROOT)
                .startsWith("attachment");
    }

    private static String decodeText(MessagePart part) {
        if (part.getBody() == null || part.getBody().getData() == null) {
            return "";
        }
        byte[] bytes;
        try {
            bytes = part.getBody().decodeData();
        } catch (IllegalArgumentException e) {
            return "";
        }
        return bytes == null ? "" : new String(bytes, charsetOf(part));
    }

    private static final Pattern CHARSET = Pattern.compile("(?i)charset\\s*=\\s*\"?([^\";\\s]+)\"?");

    /** Charset named in the part's {@code Content-Type} header; UTF-8 when absent or unknown. */
    static Charset charsetOf(MessagePart part) {
        Matcher m = CHARSET.matcher(headerValue(part.getHeaders(), "Content-Type"));
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (IllegalArgumentException e) {
                // IllegalCharsetNameException and UnsupportedCharsetException: fall back to UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }

    public static class EmailData {

        private final String id;
        private final String subject;
        private final String from;
        private final String date;
        private final String body;

        public EmailData(String id, String subject, String from, String date, String body) {
            this.id = id;
            this.subject = subject;
            this.from = from;
            this.date = date;
            this.body = body;
        }

        public String getId() {
            return id;
        }

        public String getSubject() {
            return subject;
        }

        public String getFrom() {
            return from;
        }

        public String getDate() {
            return date;
        }

        public String getBody() {
            return body;
        }
    }
}
