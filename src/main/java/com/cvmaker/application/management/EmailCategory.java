package com.cvmaker.application.management;

import java.util.Locale;
import java.util.Optional;

public enum EmailCategory {
    APPLICATION_CONFIRMATION("Application Confirmation"),
    INTERVIEW_INVITATION("Interview Invitation"),
    INTERVIEW_CONFIRMATION("Interview Confirmation"),
    INTERVIEW_FOLLOWUP("Interview Follow-up"),
    REJECTION("Rejection"),
    OFFER("Job Offer"),
    ACCEPTANCE("Offer Acceptance"),
    ASSESSMENT_REQUEST("Assessment/Test Request"),
    SCREENING_CALL("Screening Call"),
    RECRUITER_OUTREACH("Recruiter Outreach"),
    COMPANY_UPDATE("Company Update");

    private final String displayName;

    EmailCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Finds the category for a label written by a person or an LLM.
     * Accepts the enum name ("INTERVIEW_INVITATION") and the display name
     * ("Interview Invitation"). Case, surrounding space and punctuation are ignored,
     * so "interview-invitation" and "Assessment / Test request" also match.
     *
     * @param label the label to look up; may be null
     * @return the category, or empty when the label is null, blank or unknown
     */
    public static Optional<EmailCategory> fromLabel(String label) {
        String wanted = normalise(label);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        for (EmailCategory category : values()) {
            if (wanted.equals(normalise(category.name())) || wanted.equals(normalise(category.displayName))) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    private static String normalise(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public static String getAllCategories() {
        StringBuilder sb = new StringBuilder();
        for (EmailCategory category : EmailCategory.values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(category.getDisplayName());
        }
        return sb.toString();
    }
}
