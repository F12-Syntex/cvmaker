package com.cvmaker.application.management;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

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

import io.github.cdimascio.dotenv.Dotenv;

public class ApplicationManager {

    private static final String APPLICATION_NAME = "Job Application Manager";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    private static Credential getCredentials() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Dotenv dotenv = Dotenv.load();

        String clientId = dotenv.get("GOOGLE_CLIENT_ID");
        String clientSecret = dotenv.get("GOOGLE_CLIENT_SECRET");

        // Create GoogleClientSecrets object
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                .setClientId(clientId)
                .setClientSecret(clientSecret);
        GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)
                .build();

        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    /**
     * Build Gmail service
     */
    private static Gmail buildGmailService() throws GeneralSecurityException, IOException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        return new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials())
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Get job-related emails using search queries
     */
    public static void getJobRelatedEmails(Gmail service) throws IOException {
        // Define search queries for job-related emails
        String[] searchQueries = {
            "subject:(application OR interview OR position OR job OR hiring OR recruitment)",
            "from:(noreply OR hr OR recruiting OR talent OR careers)",
            "body:(\"thank you for applying\" OR \"interview\" OR \"position\" OR \"application received\")",
            "newer_than:6m" // Last 6 months
        };

        for (String query : searchQueries) {
            System.out.println("Searching with query: " + query);

            ListMessagesResponse response = service.users().messages().list("me")
                    .setQ(query)
                    .setMaxResults(100L)
                    .execute();

            List<Message> messages = response.getMessages();
            if (messages != null && !messages.isEmpty()) {
                System.out.println("Found " + messages.size() + " messages");

                for (Message message : messages) {
                    processMessage(service, message);
                }
            } else {
                System.out.println("No messages found for query: " + query);
            }
        }
    }

    /**
     * Process individual email message
     */
    private static void processMessage(Gmail service, Message message) throws IOException {
        Message fullMessage = service.users().messages().get("me", message.getId()).execute();

        // Extract basic info
        String messageId = fullMessage.getId();
        String threadId = fullMessage.getThreadId();

        // Get headers
        List<MessagePartHeader> headers = fullMessage.getPayload().getHeaders();
        String subject = "";
        String from = "";
        String date = "";

        for (MessagePartHeader header : headers) {
            String name = header.getName();
            if ("Subject".equals(name)) {
                subject = header.getValue();
            } else if ("From".equals(name)) {
                from = header.getValue();
            } else if ("Date".equals(name)) {
                date = header.getValue();
            }
        }

        // Get email body
        String body = getMessageBody(fullMessage.getPayload());

        // Print extracted info (you'll want to store this in your database)
        System.out.println("=== EMAIL ===");
        System.out.println("ID: " + messageId);
        System.out.println("Subject: " + subject);
        System.out.println("From: " + from);
        System.out.println("Date: " + date);
        System.out.println("Body Preview: " + (body.length() > 200 ? body.substring(0, 200) + "..." : body));
        System.out.println("Full Body Length: " + body.length());
        System.out.println();

        // Here you would typically:
        // 1. Use AI to classify this email (application confirmation, rejection, interview, etc.)
        // 2. Extract structured data (company name, position, dates, etc.)
        // 3. Store in your database
        // 4. Update your application tracking system
    }

    /**
     * Extract message body from MessagePart
     */
    private static String getMessageBody(MessagePart part) {
        StringBuilder body = new StringBuilder();

        if (part.getBody() != null && part.getBody().getData() != null) {
            byte[] data = Base64.getUrlDecoder().decode(part.getBody().getData());
            body.append(new String(data));
        }

        if (part.getParts() != null) {
            for (MessagePart subPart : part.getParts()) {
                if ("text/plain".equals(subPart.getMimeType())
                        || "text/html".equals(subPart.getMimeType())) {
                    body.append(getMessageBody(subPart));
                }
            }
        }

        return body.toString();
    }

    /**
     * Get all emails (be careful with this - could be thousands)
     */
    public static void getAllEmails(Gmail service) throws IOException {
        System.out.println("WARNING: This will retrieve ALL emails. Consider using getJobRelatedEmails() instead.");

        String pageToken = null;
        do {
            ListMessagesResponse response = service.users().messages().list("me")
                    .setMaxResults(100L)
                    .setPageToken(pageToken)
                    .execute();

            List<Message> messages = response.getMessages();
            if (messages != null) {
                System.out.println("Processing " + messages.size() + " messages...");
                for (Message message : messages) {
                    processMessage(service, message);
                }
            }

            pageToken = response.getNextPageToken();
        } while (pageToken != null);
    }

    /**
     * Get emails from specific time period
     */
    public static void getEmailsFromPeriod(Gmail service, String timeQuery) throws IOException {
        System.out.println("Retrieving emails from period: " + timeQuery);

        String pageToken = null;
        do {
            ListMessagesResponse response = service.users().messages().list("me")
                    .setQ(timeQuery)
                    .setMaxResults(100L)
                    .setPageToken(pageToken)
                    .execute();

            List<Message> messages = response.getMessages();
            if (messages != null) {
                System.out.println("Processing " + messages.size() + " messages...");
                for (Message message : messages) {
                    processMessage(service, message);
                }
            }

            pageToken = response.getNextPageToken();
        } while (pageToken != null);
    }

    /**
     * Get emails from specific senders
     */
    public static void getEmailsFromSenders(Gmail service, String[] senders) throws IOException {
        for (String sender : senders) {
            System.out.println("Retrieving emails from: " + sender);

            String query = "from:" + sender;
            ListMessagesResponse response = service.users().messages().list("me")
                    .setQ(query)
                    .setMaxResults(100L)
                    .execute();

            List<Message> messages = response.getMessages();
            if (messages != null && !messages.isEmpty()) {
                System.out.println("Found " + messages.size() + " messages from " + sender);

                for (Message message : messages) {
                    processMessage(service, message);
                }
            } else {
                System.out.println("No messages found from: " + sender);
            }
        }
    }

    /**
     * Search emails with custom query
     */
    public static void searchEmails(Gmail service, String customQuery) throws IOException {
        System.out.println("Searching emails with query: " + customQuery);

        String pageToken = null;
        do {
            ListMessagesResponse response = service.users().messages().list("me")
                    .setQ(customQuery)
                    .setMaxResults(100L)
                    .setPageToken(pageToken)
                    .execute();

            List<Message> messages = response.getMessages();
            if (messages != null) {
                System.out.println("Found " + messages.size() + " messages");
                for (Message message : messages) {
                    processMessage(service, message);
                }
            }

            pageToken = response.getNextPageToken();
        } while (pageToken != null);
    }

    public static void main(String[] args) {
        try {
            Gmail service = buildGmailService();

            // Use this for targeted job-related email retrieval
            getJobRelatedEmails(service);

            // Other usage examples:
            // Get emails from last 3 months
            // getEmailsFromPeriod(service, "newer_than:3m");
            // Get emails from specific senders
            // String[] jobSites = {"linkedin.com", "indeed.com", "glassdoor.com"};
            // getEmailsFromSenders(service, jobSites);
            // Custom search
            // searchEmails(service, "subject:interview newer_than:1m");
            // Uncomment below to get ALL emails (not recommended for initial testing)
            // getAllEmails(service);
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }
}
