package com.cvmaker.application.management;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

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

    public static String getGoogleSheetId(){
        return dotenv.get("GOOGLE_SHEETS_ID", "");
    }

    // // Method to get/save the Google Sheets ID
    // public static String getGoogleSheetsId() {
    //     File file = new File(GOOGLE_SHEETS_ID_FILE);
    //     if (file.exists()) {
    //         try (Scanner scanner = new Scanner(file)) {
    //             if (scanner.hasNextLine()) {
    //                 return scanner.nextLine().trim();
    //             }
    //         } catch (IOException e) {
    //             System.err.println("Error reading Google Sheets ID: " + e.getMessage());
    //         }
    //     }
    //     return dotenv.get("GOOGLE_SHEETS_ID", ""); // Fallback to env or empty string
    // }
    // public static void saveGoogleSheetsId(String sheetsId) {
    //     try (FileWriter writer = new FileWriter(GOOGLE_SHEETS_ID_FILE)) {
    //         writer.write(sheetsId);
    //     } catch (IOException e) {
    //         System.err.println("Error saving Google Sheets ID: " + e.getMessage());
    //     }
    // }
}
