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

    // ---- item 5: the caller can tell a failed AI call from a real answer ----

    @Test
    void usedAiAnswerIsReportedAsAiAnswer() {
        EmailAnalysisService service = new EmailAnalysisService(
                prompt -> "{\"isJobRelated\": true, \"confidenceScore\": 0.9, \"companyName\": \"Acme Ltd\"}");

        EmailAnalysisService.AnalysisResult result = service.analyzeEmailDetailed("id-3", SUBJECT, FROM, DATE, BODY);

        assertThat(result.outcome()).isEqualTo(EmailAnalysisService.Outcome.AI_ANSWER);
        assertThat(result.shouldRetry()).isFalse();
        assertThat(result.data().getCompanyName()).isEqualTo("Acme Ltd");
    }

    @Test
    void failedAiCallIsReportedAsRetryableAndStillCarriesRuleBasedData() {
        EmailAnalysisService service = new EmailAnalysisService(prompt -> {
            throw new IllegalStateException("429 quota exceeded");
        });

        EmailAnalysisService.AnalysisResult result = service.analyzeEmailDetailed(
                "id-3", "Thank you for applying", FROM, DATE, "We received your application.");

        assertThat(result.outcome()).isEqualTo(EmailAnalysisService.Outcome.RULES_AFTER_AI_FAILURE);
        assertThat(result.shouldRetry()).isTrue();
        assertThat(result.data().getEmailId()).isEqualTo("id-3");
        assertThat(result.data().isJobRelated()).isTrue();
        assertThat(result.data().getCategory()).isEqualTo(EmailCategory.APPLICATION_CONFIRMATION);
    }

    @Test
    void nullOrBlankAiAnswerIsReportedAsRetryable() {
        for (String answer : new String[] {null, "", "  \n "}) {
            EmailAnalysisService service = new EmailAnalysisService(prompt -> answer);

            EmailAnalysisService.AnalysisResult result = service.analyzeEmailDetailed("id-3", SUBJECT, FROM, DATE, BODY);

            assertThat(result.shouldRetry()).as("answer <%s>", answer).isTrue();
        }
    }

    // ---- item 6: real-looking LLM answers ----

    @Test
    void fencedAnswerWithProseNullsEscapesAndNestedValuesIsParsed() {
        JobApplicationData data = analyze("""
                Here is the JSON you asked for:

                ```json
                {
                  "isJobRelated": true,
                  "confidenceScore": 0.95,
                  "category": "Job Offer",
                  "companyName": "Acme \\"Rockets\\" Ltd, London",
                  "positionTitle": "Senior Tester (Payments, Cards)",
                  "applicationStatus": "Offered",
                  "provider": null,
                  "workLocation": "null",
                  "workType": "",
                  "salaryRange": null,
                  "contactPerson": "Jane Doe",
                  "requiredSkills": ["Java", "SQL", "Selenium"],
                  "offerDetails": {"salary": "£50,000", "startDate": "1 March"},
                  "extractedInfo": "Offer made; reply by Friday {see portal}.",
                  "applicationUrl": "https://careers.acme.example/offers/123?ref=a,b"
                }
                ```

                Let me know if you need anything else!
                """);

        assertThat(data.isJobRelated()).isTrue();
        assertThat(data.getConfidenceScore()).isEqualTo(0.95);
        assertThat(data.getCategory()).isEqualTo(EmailCategory.OFFER);
        // The regex parser stopped at the first escaped quote and at the first comma.
        assertThat(data.getCompanyName()).isEqualTo("Acme \"Rockets\" Ltd, London");
        assertThat(data.getPositionTitle()).isEqualTo("Senior Tester (Payments, Cards)");
        assertThat(data.getApplicationUrl()).isEqualTo("https://careers.acme.example/offers/123?ref=a,b");
        assertThat(data.getExtractedInfo()).isEqualTo("Offer made; reply by Friday {see portal}.");
        // JSON null, the text "null" and "" all mean "not given", never the word "null".
        assertThat(data.getWorkLocation()).isNull();
        assertThat(data.getWorkType()).isNull();
        assertThat(data.getSalaryRange()).isNull();
        assertThat(data.getInterviewDate()).isNull();
        // A null provider falls back to the sender-based guess.
        assertThat(data.getProvider()).isEqualTo("Direct Email");
        assertThat(data.getRequiredSkills()).isEqualTo("Java, SQL, Selenium");
        assertThat(data.getOfferDetails()).isEqualTo("salary: £50,000; startDate: 1 March");
    }

    @Test
    void jsonNullCompanyStaysNullInsteadOfTheWordNull() {
        JobApplicationData data = analyze(
                "{\"isJobRelated\": true, \"confidenceScore\": 0.8, \"companyName\": null, \"positionTitle\": \"Tester\"}");

        assertThat(data.getCompanyName()).isNull();
        assertThat(data.getPositionTitle()).isEqualTo("Tester");
    }

    @Test
    void booleanAndNumberGivenAsTextAreAccepted() {
        JobApplicationData data = analyze(
                "{\"isJobRelated\": \"true\", \"confidenceScore\": \"0.6\", \"companyName\": \"Acme Ltd\"}");

        assertThat(data.isJobRelated()).isTrue();
        assertThat(data.getConfidenceScore()).isEqualTo(0.6);
    }

    @Test
    void confidenceIsDefaultedAndKeptWithinZeroToOne() {
        assertThat(EmailAnalysisService.confidenceFrom(null)).isEqualTo(EmailAnalysisService.DEFAULT_CONFIDENCE);
        assertThat(EmailAnalysisService.confidenceFrom(0.85)).isEqualTo(0.85);
        assertThat(EmailAnalysisService.confidenceFrom(1.0)).isEqualTo(1.0);
        assertThat(EmailAnalysisService.confidenceFrom(90.0)).isEqualTo(0.9);
        assertThat(EmailAnalysisService.confidenceFrom(-0.2)).isEqualTo(0.0);
        assertThat(EmailAnalysisService.confidenceFrom(250.0)).isEqualTo(1.0);
    }

    @Test
    void answerWithoutUsableJsonFallsBackToRulesAndIsNotRetried() {
        String[] unusable = {
            "I'm sorry, I can't help with that.",
            "{\"isJobRelated\": true, \"companyName\": \"Acm",
            "{\"companyName\": \"Acme Ltd\"}",
            "{\"isJobRelated\": \"perhaps\"}"
        };
        for (String answer : unusable) {
            EmailAnalysisService service = new EmailAnalysisService(prompt -> answer);

            EmailAnalysisService.AnalysisResult result = service.analyzeEmailDetailed(
                    "id-4", "Thank you for applying", FROM, DATE, "We received your application.");

            assertThat(result.outcome()).as(answer).isEqualTo(EmailAnalysisService.Outcome.RULES_AFTER_BAD_ANSWER);
            assertThat(result.shouldRetry()).as(answer).isFalse();
            // The keyword rules decide, so this confirmation is still recognised.
            assertThat(result.data().isJobRelated()).as(answer).isTrue();
            assertThat(result.data().getConfidenceScore()).as(answer).isEqualTo(0.5);
        }
    }

    private static String promptSeenFor(String body) {
        String[] seen = new String[1];
        EmailAnalysisService service = new EmailAnalysisService(prompt -> {
            seen[0] = prompt;
            return "{}";
        });
        service.analyzeEmailDetailed("id-5", SUBJECT, FROM, DATE, body);
        return seen[0];
    }

    @Test
    void multiLineStyleBlockIsNotSentToTheModel() {
        String prompt = promptSeenFor("<html><head><style>\n.btn {\n  color: red;\n}\n</style></head>"
                + "<body><p>Please confirm your interview slot.</p></body></html>");

        assertThat(prompt).contains("Please confirm your interview slot.");
        assertThat(prompt).doesNotContain("color: red").doesNotContain(".btn");
    }

    @Test
    void multiLineScriptBlockIsNotSentToTheModel() {
        String prompt = promptSeenFor("<body><script type=\"text/javascript\">\nvar tracker = 1;\n"
                + "window.track(tracker);\n</script><div>We received your application.</div></body>");

        assertThat(prompt).contains("We received your application.");
        assertThat(prompt).doesNotContain("var tracker").doesNotContain("window.track");
    }

    @Test
    void plainTextBodyKeepsItsMeaning() {
        String prompt = promptSeenFor("Hi,   we would like to\n schedule an interview.");

        assertThat(prompt).contains("Hi, we would like to schedule an interview.");
    }

    @Test
    void nullAndBlankBodiesDoNotFail() {
        assertThat(promptSeenFor(null)).isNotNull();
        assertThat(promptSeenFor("   ")).isNotNull();
    }
}
