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
}
