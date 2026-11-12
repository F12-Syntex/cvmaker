package com.cvmaker.application.management;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailAnalysisServiceTest {

    private static final String SUBJECT = "Interview for Test Analyst at Acme";
    private static final String FROM = "Jane Doe <jane.doe@acme.example>";
    private static final String DATE = "Mon, 9 Nov 2026 10:15:00 +0000";
    private static final String BODY = "Hi, we would like to schedule an interview with you next week.";

    private static JobApplicationData analyze(String aiAnswer) {
        EmailAnalysisService service = new EmailAnalysisService(prompt -> aiAnswer);
        return service.analyzeEmail("id-1", SUBJECT, FROM, DATE, BODY);
    }

    // ---- item 3: category parsing ----

    @Test
    void categoryGivenAsDisplayNameIsParsedAndAiFieldsAreKept() {
        JobApplicationData data = analyze("""
                {"isJobRelated": true, "confidenceScore": 0.93, "category": "Interview Follow-up",
                 "companyName": "Acme Ltd", "positionTitle": "Test Analyst",
                 "applicationStatus": "Interview Scheduled"}
                """);

        assertThat(data.isJobRelated()).isTrue();
        assertThat(data.getCategory()).isEqualTo(EmailCategory.INTERVIEW_FOLLOWUP);
        assertThat(data.getCompanyName()).isEqualTo("Acme Ltd");
        assertThat(data.getPositionTitle()).isEqualTo("Test Analyst");
        assertThat(data.getApplicationStatus()).isEqualTo("Interview Scheduled");
        assertThat(data.getConfidenceScore()).isEqualTo(0.93);
    }

    @Test
    void categoryGivenAsEnumNameIsParsed() {
        JobApplicationData data = analyze("""
                {"isJobRelated": true, "confidenceScore": 0.8, "category": "RECRUITER_OUTREACH",
                 "companyName": "Acme Ltd"}
                """);

        assertThat(data.getCategory()).isEqualTo(EmailCategory.RECRUITER_OUTREACH);
    }

    @Test
    void missingCategoryKeepsAiFieldsAndUsesRuleBasedCategory() {
        JobApplicationData data = analyze("""
                {"isJobRelated": true, "confidenceScore": 0.9,
                 "companyName": "Acme Ltd", "positionTitle": "Test Analyst"}
                """);

        // Before the fix a missing category threw NPE and every AI field was replaced by fallback data.
        assertThat(data.getCompanyName()).isEqualTo("Acme Ltd");
        assertThat(data.getPositionTitle()).isEqualTo("Test Analyst");
        assertThat(data.getConfidenceScore()).isEqualTo(0.9);
        assertThat(data.getCategory()).isEqualTo(EmailCategory.INTERVIEW_INVITATION);
    }

    @Test
    void unknownCategoryKeepsAiFieldsAndUsesRuleBasedCategory() {
        JobApplicationData data = analyze("""
                {"isJobRelated": true, "confidenceScore": 0.9, "category": "Something Else",
                 "companyName": "Acme Ltd"}
                """);

        assertThat(data.getCompanyName()).isEqualTo("Acme Ltd");
        assertThat(data.getCategory()).isEqualTo(EmailCategory.INTERVIEW_INVITATION);
    }

    @Test
    void notJobRelatedAnswerLeavesCategoryUnset() {
        JobApplicationData data = analyze("{\"isJobRelated\": false, \"confidenceScore\": 0.9}");

        assertThat(data.isJobRelated()).isFalse();
        assertThat(data.getCategory()).isNull();
        assertThat(data.getEmailId()).isEqualTo("id-1");
    }

    // ---- item 4: rule-based fallback, reached when the AI call fails ----

    private static JobApplicationData analyzeWithBrokenAi(String subject, String from, String body) {
        EmailAnalysisService service = new EmailAnalysisService(prompt -> {
            throw new IllegalStateException("AI is down");
        });
        return service.analyzeEmail("id-2", subject, from, DATE, body);
    }

    @Test
    void fallbackAcceptsAnOrdinaryApplicationConfirmation() {
        // "call", "finally" and "small" contain "all", which the old keyword list rejected.
        JobApplicationData data = analyzeWithBrokenAi(
                "Thank you for applying to Acme",
                "Acme Careers <no-reply@acme.example>",
                "Thank you for applying. Our small team will finally review your application and call you.");

        assertThat(data.isJobRelated()).isTrue();
        assertThat(data.getCategory()).isEqualTo(EmailCategory.APPLICATION_CONFIRMATION);
        assertThat(data.getConfidenceScore()).isEqualTo(0.5);
        assertThat(data.getProvider()).isEqualTo("Company Website");
    }

    @Test
    void fallbackAcceptsARejectionThatMentionsFutureOpportunities() {
        JobApplicationData data = analyzeWithBrokenAi(
                "Update on your application",
                "Jane Doe <jane.doe@acme.example>",
                "Unfortunately we are not moving forward with your application. "
                        + "We wish you all the best with future opportunities.");

        assertThat(data.isJobRelated()).isTrue();
        assertThat(data.getCategory()).isEqualTo(EmailCategory.REJECTION);
        assertThat(data.getProvider()).isEqualTo("Direct Email");
    }

    @Test
    void fallbackStillRejectsAJobAlertNewsletter() {
        JobApplicationData data = analyzeWithBrokenAi(
                "Your weekly job alert: 12 new roles",
                "Job Alerts <alerts@jobboard.example>",
                "Improve your application with these roles picked for you.");

        assertThat(data.isJobRelated()).isFalse();
        assertThat(data.getCategory()).isNull();
    }
}
