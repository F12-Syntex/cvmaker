package com.cvmaker.application.management;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ReportingService {

    public void displayJobApplicationData(JobApplicationData data) {
        System.out.println("┌─────────────────────────────────────────────────────────────────────");
        System.out.printf("│ 📧 EMAIL ID: %s\n", data.getEmailId());
        System.out.printf("│ 📋 SUBJECT: %s\n", data.getSubject());
        System.out.printf("│ 🏢 COMPANY: %s\n", data.getCompanyName() != null ? data.getCompanyName() : "Unknown");
        System.out.printf("│ 💼 POSITION: %s\n", data.getPositionTitle() != null ? data.getPositionTitle() : "Unknown");
        System.out.printf("│ 📊 CATEGORY: %s\n", data.getCategory().getDisplayName());
        System.out.printf("│ 🎯 STATUS: %s\n", data.getApplicationStatus() != null ? data.getApplicationStatus() : "Unknown");
        System.out.printf("│ 📅 DATE: %s\n", data.getDate());
        System.out.printf("│ ⚡ CONFIDENCE: %.1f%%\n", data.getConfidenceScore() * 100);
        System.out.printf("│ 🕐 PROCESSED: %s\n", data.getProcessedTimestamp() != null ? data.getProcessedTimestamp() : "Just now");

        if (data.getInterviewDate() != null) {
            System.out.printf("│ 📅 INTERVIEW: %s\n", data.getInterviewDate());
        }
        if (data.getInterviewLocation() != null) {
            System.out.printf("│ 📍 LOCATION: %s\n", data.getInterviewLocation());
        }
        if (data.getExtractedInfo() != null) {
            System.out.printf("│ ℹ️  INFO: %s\n", data.getExtractedInfo());
        }

        System.out.println("└─────────────────────────────────────────────────────────────────────");
    }

    public void displayProcessingSummary(List<JobApplicationData> newlyProcessedEmails,
            List<JobApplicationData> newJobRelatedEmails,
            int ignoredEmails,
            int skippedEmails,
            int totalProcessedEmails,
            int totalJobApplications,
            boolean isFirstRun) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📊 PROCESSING SUMMARY");
        System.out.println("=".repeat(70));
        System.out.println("Run type: " + (isFirstRun ? "FIRST RUN" : "INCREMENTAL RUN"));
        System.out.println("Total emails examined: " + (newlyProcessedEmails.size() + skippedEmails));
        System.out.println("Newly processed emails: " + newlyProcessedEmails.size());
        System.out.println("New job-related emails: " + newJobRelatedEmails.size());
        System.out.println("Non-job emails ignored: " + ignoredEmails);
        System.out.println("Previously processed (skipped): " + skippedEmails);
        System.out.println("Total processed emails in database: " + totalProcessedEmails);
        System.out.println("Total job applications tracked: " + totalJobApplications);

        if (!newJobRelatedEmails.isEmpty()) {
            Map<EmailCategory, Long> categoryCount = newJobRelatedEmails.stream()
                    .collect(Collectors.groupingBy(
                            JobApplicationData::getCategory,
                            Collectors.counting()
                    ));

            System.out.println("\nNew job-related category breakdown:");
            categoryCount.forEach((category, count)
                    -> System.out.printf("  %s: %d emails\n", category.getDisplayName(), count)
            );

            Set<String> companies = newJobRelatedEmails.stream()
                    .map(JobApplicationData::getCompanyName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Set<String> positions = newJobRelatedEmails.stream()
                    .map(JobApplicationData::getPositionTitle)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (!companies.isEmpty()) {
                System.out.println("\nNew companies identified: " + companies.size());
                companies.forEach(company -> System.out.println("  • " + company));
            }

            if (!positions.isEmpty()) {
                System.out.println("\nNew positions identified: " + positions.size());
                positions.forEach(position -> System.out.println("  • " + position));
            }
        }

        System.out.println("\n💾 Data persistence:");
        System.out.println("  • Processed emails: processed_emails.txt");
        System.out.println("  • Job applications: job_applications.txt");
        System.out.println("  • System state: system_state.txt");
        System.out.println("=".repeat(70));
    }

    public void displayExistingJobApplications(Map<String, JobApplicationData> jobApplicationsDb) {
        System.out.println("\n=== EXISTING JOB APPLICATIONS ===");

        if (jobApplicationsDb.isEmpty()) {
            System.out.println("No job applications found in database.");
            return;
        }

        jobApplicationsDb.values().stream()
                .filter(JobApplicationData::isJobRelated)
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .forEach(this::displayJobApplicationData);
    }
}
