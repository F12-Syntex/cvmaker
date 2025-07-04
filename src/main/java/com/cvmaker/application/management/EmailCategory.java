package com.cvmaker.application.management;

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