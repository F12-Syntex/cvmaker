package com.cvmaker.application.management;

import io.github.cdimascio.dotenv.Dotenv;

public class ApplicationConfig {

    public static final String APPLICATION_NAME = "Job Application Manager";
    public static final String TOKENS_DIRECTORY_PATH = "tokens";
    public static final String PROCESSED_EMAILS_FILE = "processed_emails.txt";
    public static final String JOB_APPLICATIONS_DB_FILE = "job_applications.txt";
    public static final String SYSTEM_STATE_FILE = "system_state.txt";

    private static final Dotenv dotenv = Dotenv.load();

    public static String getGoogleClientId() {
        return dotenv.get("GOOGLE_CLIENT_ID");
    }

    public static String getGoogleClientSecret() {
        return dotenv.get("GOOGLE_CLIENT_SECRET");
    }

    public static String getGoogleSheetsId() {
        return dotenv.get("GOOGLE_SHEETS_CLIENT_ID");
    }

    public static String getGoogleSheetsSecret() {
        return dotenv.get("GOOGLE_SHEETS_CLIENT_SECRET");
    }

    public static String getGoogleSheetId() {
        return dotenv.get("GOOGLE_SHEETS_ID", "");
    }

    public static String getExaApiKey() {
        return dotenv.get("EXA_API_KEY");
    }
}
