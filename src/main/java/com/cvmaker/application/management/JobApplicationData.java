package com.cvmaker.application.management;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

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
    private String applicationUrl;
    
    
    // New fields for enhanced information
    private String emailLink;
    private String provider; // LinkedIn, Indeed, company website, etc.
    private String salaryRange;
    private String workLocation; // Remote, Hybrid, On-site
    private String workType; // Full-time, Part-time, Contract
    private String contactPerson; // Recruiter or HR contact name
    private String contactEmail;
    private String nextSteps;
    private String applicationDeadline;
    private String requiredSkills;
    private String rejectionReason;
    private String offerDetails;
    private String lastUpdated;
    private String emailTimestamp; // Added for email chronology tracking

    // Constructors
    public JobApplicationData() {
    }

    /**
     * @param date the email's RFC 2822 {@code Date} header; it also sets {@link #getEmailTimestamp()}
     *             (in the system time zone). Only when it cannot be parsed does the timestamp fall
     *             back to the processing time.
     */
    public JobApplicationData(String emailId, String subject, String fromEmail, String date) {
        this.emailId = emailId;
        this.subject = subject;
        this.fromEmail = fromEmail;
        this.date = date;
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.processedTimestamp = now;
        String sent = parseEmailTimestamp(date, ZoneId.systemDefault());
        this.emailTimestamp = sent != null ? sent : now;
        this.emailLink = generateGmailLink(emailId);
    }

    /** RFC 2822 date with a named zone ({@code UTC}, {@code EST}, ...), which RFC 1123 parsing rejects. */
    private static final DateTimeFormatter NAMED_ZONE_DATE = DateTimeFormatter
            .ofPattern("[EEE, ]d MMM yyyy HH:mm[:ss] z", Locale.ENGLISH);

    private static final List<DateTimeFormatter> EMAIL_DATE_FORMATS = List.of(
            DateTimeFormatter.RFC_1123_DATE_TIME, NAMED_ZONE_DATE);

    /**
     * Converts an email {@code Date} header (RFC 2822, e.g. {@code Tue, 3 Jun 2025 11:05:30 +0200
     * (CEST)}) to an ISO local date-time in {@code zone}, the format {@code emailTimestamp} uses.
     * Comments in parentheses, extra spaces, a missing or wrong weekday and named zones such as
     * {@code UT}, {@code UTC}, {@code GMT} or {@code EST} are tolerated.
     *
     * @return e.g. {@code 2025-06-03T09:05:30} for zone UTC, or {@code null} when it cannot be parsed
     */
    public static String parseEmailTimestamp(String dateHeader, ZoneId zone) {
        if (dateHeader == null || dateHeader.isBlank()) {
            return null;
        }
        String cleaned = dateHeader.replaceAll("\\([^)]*\\)", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.endsWith(" UT")) {
            cleaned = cleaned + "C";
        }
        // Retry without the weekday: some senders put a weekday that does not match the date
        String withoutWeekday = cleaned.replaceFirst("^[A-Za-z]{3},\\s*", "");
        for (String candidate : List.of(cleaned, withoutWeekday)) {
            for (DateTimeFormatter format : EMAIL_DATE_FORMATS) {
                try {
                    return ZonedDateTime.parse(candidate, format)
                            .withZoneSameInstant(zone)
                            .toLocalDateTime()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (DateTimeParseException e) {
                    // try the next format
                }
            }
        }
        return null;
    }

    private String generateGmailLink(String emailId) {
        if (emailId != null && !emailId.isEmpty()) {
            return "https://mail.google.com/mail/u/0/#inbox/" + emailId;
        }
        return "";
    }

    // Existing getters and setters...
    public String getEmailId() { return emailId; }
    public void setEmailId(String emailId) { 
        this.emailId = emailId; 
        this.emailLink = generateGmailLink(emailId);
    }

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

    public String getApplicationUrl() { return applicationUrl; }
    public void setApplicationUrl(String applicationUrl) { this.applicationUrl = applicationUrl; }

    // New getters and setters
    public String getEmailLink() { return emailLink; }
    public void setEmailLink(String emailLink) { this.emailLink = emailLink; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getSalaryRange() { return salaryRange; }
    public void setSalaryRange(String salaryRange) { this.salaryRange = salaryRange; }

    public String getWorkLocation() { return workLocation; }
    public void setWorkLocation(String workLocation) { this.workLocation = workLocation; }

    public String getWorkType() { return workType; }
    public void setWorkType(String workType) { this.workType = workType; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getNextSteps() { return nextSteps; }
    public void setNextSteps(String nextSteps) { this.nextSteps = nextSteps; }

    public String getApplicationDeadline() { return applicationDeadline; }
    public void setApplicationDeadline(String applicationDeadline) { this.applicationDeadline = applicationDeadline; }

    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getOfferDetails() { return offerDetails; }
    public void setOfferDetails(String offerDetails) { this.offerDetails = offerDetails; }
    
    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }
    
    public String getEmailTimestamp() { return emailTimestamp; }
    public void setEmailTimestamp(String emailTimestamp) { this.emailTimestamp = emailTimestamp; }

    /**
     * Encodes this record as a single-line JSON object (the storage format of {@link DataStorage}).
     * Null fields are omitted. Newlines and other control characters inside values are escaped,
     * so the result never contains a line break.
     */
    public String toJsonLine() {
        JSONObject json = new JSONObject();
        json.put("emailId", emailId);
        json.put("subject", subject);
        json.put("fromEmail", fromEmail);
        json.put("companyName", companyName);
        json.put("positionTitle", positionTitle);
        json.put("date", date);
        json.put("category", category != null ? category.name() : null);
        json.put("extractedInfo", extractedInfo);
        json.put("interviewDate", interviewDate);
        json.put("interviewLocation", interviewLocation);
        json.put("applicationStatus", applicationStatus);
        // JSONObject rejects NaN and infinities
        json.put("confidenceScore", Double.isFinite(confidenceScore) ? confidenceScore : 0.0);
        json.put("isJobRelated", isJobRelated);
        json.put("processedTimestamp", processedTimestamp);
        json.put("applicationUrl", applicationUrl);
        json.put("emailLink", emailLink);
        json.put("provider", provider);
        json.put("salaryRange", salaryRange);
        json.put("workLocation", workLocation);
        json.put("workType", workType);
        json.put("contactPerson", contactPerson);
        json.put("contactEmail", contactEmail);
        json.put("nextSteps", nextSteps);
        json.put("applicationDeadline", applicationDeadline);
        json.put("requiredSkills", requiredSkills);
        json.put("rejectionReason", rejectionReason);
        json.put("offerDetails", offerDetails);
        json.put("lastUpdated", lastUpdated);
        json.put("emailTimestamp", emailTimestamp);
        return json.toString();
    }

    /**
     * Decodes a line written by {@link #toJsonLine()}.
     *
     * @return the record, or {@code null} when the line is not a valid JSON object
     */
    public static JobApplicationData fromJsonLine(String line) {
        if (line == null) {
            return null;
        }
        JSONObject json;
        try {
            json = new JSONObject(line);
        } catch (JSONException e) {
            return null;
        }
        JobApplicationData data = new JobApplicationData();
        data.setEmailId(json.optString("emailId", null));
        data.setSubject(json.optString("subject", null));
        data.setFromEmail(json.optString("fromEmail", null));
        data.setCompanyName(json.optString("companyName", null));
        data.setPositionTitle(json.optString("positionTitle", null));
        data.setDate(json.optString("date", null));
        String categoryName = json.optString("category", null);
        if (categoryName != null) {
            try {
                data.setCategory(EmailCategory.valueOf(categoryName));
            } catch (IllegalArgumentException e) {
                // Unknown category (renamed enum constant): leave it unset
            }
        }
        data.setExtractedInfo(json.optString("extractedInfo", null));
        data.setInterviewDate(json.optString("interviewDate", null));
        data.setInterviewLocation(json.optString("interviewLocation", null));
        data.setApplicationStatus(json.optString("applicationStatus", null));
        data.setConfidenceScore(json.optDouble("confidenceScore", 0.0));
        data.setJobRelated(json.optBoolean("isJobRelated", false));
        data.setProcessedTimestamp(json.optString("processedTimestamp", null));
        data.setApplicationUrl(json.optString("applicationUrl", null));
        // setEmailId regenerated the link; the stored value wins (also when it was absent)
        data.setEmailLink(json.optString("emailLink", null));
        data.setProvider(json.optString("provider", null));
        data.setSalaryRange(json.optString("salaryRange", null));
        data.setWorkLocation(json.optString("workLocation", null));
        data.setWorkType(json.optString("workType", null));
        data.setContactPerson(json.optString("contactPerson", null));
        data.setContactEmail(json.optString("contactEmail", null));
        data.setNextSteps(json.optString("nextSteps", null));
        data.setApplicationDeadline(json.optString("applicationDeadline", null));
        data.setRequiredSkills(json.optString("requiredSkills", null));
        data.setRejectionReason(json.optString("rejectionReason", null));
        data.setOfferDetails(json.optString("offerDetails", null));
        data.setLastUpdated(json.optString("lastUpdated", null));
        data.setEmailTimestamp(json.optString("emailTimestamp", null));
        return data;
    }

    /**
     * Legacy {@code ||}-separated format. It cannot represent newlines or a {@code |} at the end of
     * a field, so {@link DataStorage} now writes {@link #toJsonLine()} and only reads this format.
     *
     * @deprecated use {@link #toJsonLine()}
     */
    @Deprecated
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
                processedTimestamp != null ? processedTimestamp : "",
                applicationUrl != null ? applicationUrl : "",
                emailLink != null ? emailLink : "",
                provider != null ? provider : "",
                salaryRange != null ? salaryRange : "",
                workLocation != null ? workLocation : "",
                workType != null ? workType : "",
                contactPerson != null ? contactPerson : "",
                contactEmail != null ? contactEmail : "",
                nextSteps != null ? nextSteps : "",
                applicationDeadline != null ? applicationDeadline : "",
                requiredSkills != null ? requiredSkills : "",
                rejectionReason != null ? rejectionReason : "",
                offerDetails != null ? offerDetails : "",
                lastUpdated != null ? lastUpdated : "",
                emailTimestamp != null ? emailTimestamp : ""
        );
    }

    /**
     * Number of {@code ||}-separated fields in a legacy record, or 0 for {@code null}. A legacy
     * record needs at least {@link #LEGACY_MIN_FIELDS}.
     */
    static int legacyFieldCount(String serialized) {
        return serialized == null ? 0 : serialized.split("\\|\\|", -1).length;
    }

    /** Minimum number of fields of a legacy {@code ||} record (the oldest format had 14). */
    static final int LEGACY_MIN_FIELDS = 14;

    /**
     * Decodes the legacy {@code ||}-separated format written by {@link #serialize()}.
     *
     * @return the record, or {@code null} when the line has fewer than 14 fields
     */
    public static JobApplicationData deserialize(String serialized) {
        if (serialized == null) {
            return null;
        }
        // -1 keeps trailing empty fields, which the old split silently dropped
        String[] parts = serialized.split("\\|\\|", -1);
        if (parts.length < LEGACY_MIN_FIELDS) {
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

        // Handle new fields with backward compatibility
        if (parts.length > 14) data.setApplicationUrl(parts[14].isEmpty() ? null : parts[14]);
        if (parts.length > 15) data.setEmailLink(parts[15].isEmpty() ? null : parts[15]);
        if (parts.length > 16) data.setProvider(parts[16].isEmpty() ? null : parts[16]);
        if (parts.length > 17) data.setSalaryRange(parts[17].isEmpty() ? null : parts[17]);
        if (parts.length > 18) data.setWorkLocation(parts[18].isEmpty() ? null : parts[18]);
        if (parts.length > 19) data.setWorkType(parts[19].isEmpty() ? null : parts[19]);
        if (parts.length > 20) data.setContactPerson(parts[20].isEmpty() ? null : parts[20]);
        if (parts.length > 21) data.setContactEmail(parts[21].isEmpty() ? null : parts[21]);
        if (parts.length > 22) data.setNextSteps(parts[22].isEmpty() ? null : parts[22]);
        if (parts.length > 23) data.setApplicationDeadline(parts[23].isEmpty() ? null : parts[23]);
        if (parts.length > 24) data.setRequiredSkills(parts[24].isEmpty() ? null : parts[24]);
        if (parts.length > 25) data.setRejectionReason(parts[25].isEmpty() ? null : parts[25]);
        if (parts.length > 26) data.setOfferDetails(parts[26].isEmpty() ? null : parts[26]);
        if (parts.length > 27) data.setLastUpdated(parts[27].isEmpty() ? null : parts[27]);
        if (parts.length > 28) data.setEmailTimestamp(parts[28].isEmpty() ? null : parts[28]);

        return data;
    }

    @Override
    public String toString() {
        return String.format(
                "JobApplicationData{emailId='%s', subject='%s', company='%s', position='%s', category=%s, status='%s', provider='%s', workLocation='%s', confidence=%.2f, jobRelated=%s, processed='%s'}",
                emailId, subject, companyName, positionTitle, category, applicationStatus, provider, workLocation, confidenceScore, isJobRelated, processedTimestamp
        );
    }
}