package com.cvmaker.application.management;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                .setClientId(ApplicationConfig.getGoogleClientId())
                .setClientSecret(ApplicationConfig.getGoogleClientSecret());
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

    public Set<String> findUnprocessedEmails(Set<String> processedEmailIds) throws IOException {
        // Define date range: 07/07/2025 to 07/14/2025 (one week)
        String dateRange = "after:2025/07/07 before:2025/07/15"; // end date is exclusive

        // More comprehensive search queries with date range
        String[] searchQueries = {
            // Basic job application terms
            "(\"application received\" OR \"thank you for applying\" OR \"application confirmation\") " + dateRange,
            // Interview related
            "(interview OR \"schedule an interview\" OR \"interview invitation\" OR \"phone screen\") " + dateRange,
            // Status updates
            "(\"application status\" OR \"hiring decision\" OR \"next steps\" OR \"moving forward\") " + dateRange,
            // Rejections and offers
            "(unfortunately OR \"not selected\" OR \"decided to move forward\" OR \"job offer\" OR \"pleased to offer\") " + dateRange,
            // Assessments and tests
            "(\"coding assessment\" OR \"technical test\" OR \"complete the following\" OR assessment OR challenge) " + dateRange,
            // From common job-related senders
            "from:(hr OR recruiting OR talent OR careers OR noreply OR no-reply) " + dateRange,
            // Broader job-related terms
            "(position OR role OR \"your application\" OR \"your interview\" OR \"your submission\" OR candidate) " + dateRange,
            // Company-specific patterns
            "(\"we received your\" OR \"thank you for your interest\" OR \"regarding your application\") " + dateRange,
            // Additional comprehensive search
            "subject:(job OR position OR interview OR application OR offer OR rejected OR assessment) " + dateRange,};

        Set<String> allMessageIds = new HashSet<>();
        int totalFound = 0;

        for (int i = 0; i < searchQueries.length; i++) {
            String query = searchQueries[i];
            System.out.println("Executing search query " + (i + 1) + "/" + searchQueries.length + ": " + query);

            try {
                ListMessagesResponse response = service.users().messages().list("me")
                        .setQ(query)
                        .setMaxResults(500L)
                        .execute();

                List<Message> messages = response.getMessages();
                if (messages != null && !messages.isEmpty()) {
                    int newMessages = 0;
                    for (Message message : messages) {
                        if (!processedEmailIds.contains(message.getId())) {
                            if (allMessageIds.add(message.getId())) { // Only count if newly added
                                newMessages++;
                            }
                        }
                    }
                    System.out.println("  Found " + messages.size() + " total, " + newMessages + " new unprocessed emails");
                    totalFound += newMessages;
                } else {
                    System.out.println("  No emails found for this query");
                }

                // Small delay between queries to be respectful to the API
                Thread.sleep(200);

            } catch (Exception e) {
                System.err.println("Error executing query " + (i + 1) + ": " + e.getMessage());
            }
        }

        System.out.println("Total unique unprocessed emails found: " + allMessageIds.size());
        return allMessageIds;
    }

    public EmailData getEmailContent(String messageId) throws IOException {
        Message fullMessage = service.users().messages().get("me", messageId)
                .setFormat("full")
                .execute();

        List<MessagePartHeader> headers = fullMessage.getPayload().getHeaders();
        String subject = "", from = "", date = "";

        for (MessagePartHeader header : headers) {
            String name = header.getName();
            switch (name) {
                case "Subject":
                    subject = header.getValue();
                    break;
                case "From":
                    from = header.getValue();
                    break;
                case "Date":
                    date = header.getValue();
                    break;
            }
        }

        String body = getMessageBody(fullMessage.getPayload());
        return new EmailData(messageId, subject, from, date, body);
    }

    private String getMessageBody(MessagePart part) {
        StringBuilder body = new StringBuilder();

        if (part.getBody() != null && part.getBody().getData() != null) {
            byte[] data = Base64.getUrlDecoder().decode(part.getBody().getData());
            body.append(new String(data));
        }

        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                if ("text/plain".equals(subPart.getMimeType()) || "text/html".equals(subPart.getMimeType())) {
                    body.append(getMessageBody(subPart));
                }
            }
        }

        return body.toString();
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
