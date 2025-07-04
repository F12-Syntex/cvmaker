package com.cvmaker.application.management;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class JobApplicationData {
    private String emailId;
    private String subject;
    private String fromEmail;
    private String companyName;
    private String positionTitle;
    private String date;
    private EmailCategory category;
    private String extractedInfo;
    private String interviewDate;
    private String interviewLocation;
    private String applicationStatus;
    private double confidenceScore;
    private boolean isJobRelated;
    private String processedTimestamp;

    // Constructors
    public JobApplicationData() {}

    public JobApplicationData(String emailId, String subject, String fromEmail, String date) {
        this.emailId = emailId;
        this.subject = subject;
        this.fromEmail = fromEmail;
        this.date = date;
        this.processedTimestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // Getters and setters
    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { this.emailId = emailId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getFromEmail() { return fromEmail; }
    public void setFromEmail(String fromEmail) { this.fromEmail = fromEmail; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getPositionTitle() { return positionTitle; }
    public void setPositionTitle(String positionTitle) { this.positionTitle = positionTitle; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public EmailCategory getCategory() { return category; }
    public void setCategory(EmailCategory category) { this.category = category; }

    public String getExtractedInfo() { return extractedInfo; }
    public void setExtractedInfo(String extractedInfo) { this.extractedInfo = extractedInfo; }

    public String getInterviewDate() { return interviewDate; }
    public void setInterviewDate(String interviewDate) { this.interviewDate = interviewDate; }

    public String getInterviewLocation() { return interviewLocation; }
    public void setInterviewLocation(String interviewLocation) { this.interviewLocation = interviewLocation; }

    public String getApplicationStatus() { return applicationStatus; }
    public void setApplicationStatus(String applicationStatus) { this.applicationStatus = applicationStatus; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

    public boolean isJobRelated() { return isJobRelated; }
    public void setJobRelated(boolean jobRelated) { isJobRelated = jobRelated; }

    public String getProcessedTimestamp() { return processedTimestamp; }
    public void setProcessedTimestamp(String processedTimestamp) { this.processedTimestamp = processedTimestamp; }

    // Serialization methods
    public String serialize() {
        return String.join("||",
                emailId != null ? emailId : "",
                subject != null ? subject : "",
                fromEmail != null ? fromEmail : "",
                companyName != null ? companyName : "",
                positionTitle != null ? positionTitle : "",
                date != null ? date : "",
                category != null ? category.name() : "",
                extractedInfo != null ? extractedInfo.replace("||", "|") : "",
                interviewDate != null ? interviewDate : "",
                interviewLocation != null ? interviewLocation : "",
                applicationStatus != null ? applicationStatus : "",
                String.valueOf(confidenceScore),
                String.valueOf(isJobRelated),
                processedTimestamp != null ? processedTimestamp : ""
        );
    }

    public static JobApplicationData deserialize(String serialized) {
        String[] parts = serialized.split("\\|\\|");
        if (parts.length < 14) {
            return null;
        }

        JobApplicationData data = new JobApplicationData();
        data.setEmailId(parts[0].isEmpty() ? null : parts[0]);
        data.setSubject(parts[1].isEmpty() ? null : parts[1]);
        data.setFromEmail(parts[2].isEmpty() ? null : parts[2]);
        data.setCompanyName(parts[3].isEmpty() ? null : parts[3]);
        data.setPositionTitle(parts[4].isEmpty() ? null : parts[4]);
        data.setDate(parts[5].isEmpty() ? null : parts[5]);

        if (!parts[6].isEmpty()) {
            try {
                data.setCategory(EmailCategory.valueOf(parts[6]));
            } catch (IllegalArgumentException e) {
                // Ignore invalid categories
            }
        }

        data.setExtractedInfo(parts[7].isEmpty() ? null : parts[7]);
        data.setInterviewDate(parts[8].isEmpty() ? null : parts[8]);
        data.setInterviewLocation(parts[9].isEmpty() ? null : parts[9]);
        data.setApplicationStatus(parts[10].isEmpty() ? null : parts[10]);

        try {
            data.setConfidenceScore(Double.parseDouble(parts[11]));
        } catch (NumberFormatException e) {
            data.setConfidenceScore(0.0);
        }

        data.setJobRelated(Boolean.parseBoolean(parts[12]));
        data.setProcessedTimestamp(parts[13].isEmpty() ? null : parts[13]);

        return data;
    }

    @Override
    public String toString() {
        return String.format(
                "JobApplicationData{emailId='%s', subject='%s', company='%s', position='%s', category=%s, status='%s', confidence=%.2f, jobRelated=%s, processed='%s'}",
                emailId, subject, companyName, positionTitle, category, applicationStatus, confidenceScore, isJobRelated, processedTimestamp
        );
    }
}