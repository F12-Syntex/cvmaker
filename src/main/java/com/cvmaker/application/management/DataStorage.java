package com.cvmaker.application.management;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class DataStorage {

    public Set<String> loadProcessedEmailIds() {
        Set<String> ids = new HashSet<>();
        File file = new File(ApplicationConfig.PROCESSED_EMAILS_FILE);
        if (file.exists()) {
            try (Scanner scanner = new Scanner(file)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (!line.isEmpty()) {
                        ids.add(line);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error loading processed email IDs: " + e.getMessage());
            }
        }
        return ids;
    }

// Add this method to your DataStorage class
    public int exportJobApplicationsToCSV(Map<String, JobApplicationData> jobApplicationsDb, String filePath) throws IOException {
        int count = 0;
        try (FileWriter writer = new FileWriter(filePath)) {
            // Write CSV header
            writer.write("Email ID,Subject,From,Company,Position,Date,Category,Status,Interview Date,Interview Location,Notes,Confidence,Processed Timestamp\n");

            // Write each application as a CSV row
            for (JobApplicationData data : jobApplicationsDb.values()) {
                if (data.isJobRelated()) {
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.2f,\"%s\"\n",
                            escape(data.getEmailId()),
                            escape(data.getSubject()),
                            escape(data.getFromEmail()),
                            escape(data.getCompanyName()),
                            escape(data.getPositionTitle()),
                            escape(data.getDate()),
                            data.getCategory() != null ? escape(data.getCategory().getDisplayName()) : "",
                            escape(data.getApplicationStatus()),
                            escape(data.getInterviewDate()),
                            escape(data.getInterviewLocation()),
                            escape(data.getExtractedInfo()),
                            data.getConfidenceScore(),
                            escape(data.getProcessedTimestamp())
                    ));
                    count++;
                }
            }
        }
        return count;
    }

// Helper method for escaping CSV fields
    private String escape(String field) {
        if (field == null) {
            return "";
        }
        // Double quotes need to be escaped with another double quote in CSV
        return field.replace("\"", "\"\"");
    }

    public Map<String, JobApplicationData> loadJobApplicationsDb() {
        Map<String, JobApplicationData> db = new HashMap<>();
        File file = new File(ApplicationConfig.JOB_APPLICATIONS_DB_FILE);
        if (file.exists()) {
            try (Scanner scanner = new Scanner(file)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (!line.isEmpty()) {
                        JobApplicationData data = JobApplicationData.deserialize(line);
                        if (data != null) {
                            db.put(data.getEmailId(), data);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error loading job applications database: " + e.getMessage());
            }
        }
        return db;
    }

    public void saveProcessedEmailId(String emailId) {
        try (FileWriter writer = new FileWriter(ApplicationConfig.PROCESSED_EMAILS_FILE, true)) {
            writer.write(emailId + "\n");
        } catch (IOException e) {
            System.err.println("Error saving processed email ID: " + e.getMessage());
        }
    }

    public void saveJobApplicationData(JobApplicationData data) {
        data.setProcessedTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        try (FileWriter writer = new FileWriter(ApplicationConfig.JOB_APPLICATIONS_DB_FILE, true)) {
            writer.write(data.serialize() + "\n");
        } catch (IOException e) {
            System.err.println("Error saving job application data: " + e.getMessage());
        }
    }

    public void saveSystemState() {
        try (FileWriter writer = new FileWriter(ApplicationConfig.SYSTEM_STATE_FILE)) {
            writer.write("last_run=" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n");
        } catch (IOException e) {
            System.err.println("Error saving system state: " + e.getMessage());
        }
    }

    public void loadSystemState() {
        File stateFile = new File(ApplicationConfig.SYSTEM_STATE_FILE);
        if (stateFile.exists()) {
            try (Scanner scanner = new Scanner(stateFile)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.startsWith("last_run=")) {
                        String lastRun = line.substring(9);
                        System.out.println("Last run: " + lastRun);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error loading system state: " + e.getMessage());
            }
        }
    }
}
