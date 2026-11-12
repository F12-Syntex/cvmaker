package com.cvmaker.application.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EmailRuleClassifierTest {

    private static final String PERSON = "Jane Doe <jane.doe@acme.example>";

    static Stream<Arguments> jobRelatedCases() {
        return Stream.of(
                // Words that merely contain an old veto keyword ("all", "update", "connect", "network").
                arguments(true, "Thank you for applying", PERSON,
                        "We will call you. Our small team will finally review your application."),
                arguments(true, "Update on your application", PERSON,
                        "We wanted to update you: your application is under review."),
                arguments(true, "Your application to Networked Systems Ltd", PERSON,
                        "We received your CV and will connect you with the hiring manager."),
                arguments(true, "Interview invitation", PERSON,
                        "Please confirm the interview slot for the general manager role."),
                arguments(true, "Your application", PERSON,
                        "Unfortunately we are not moving forward. We wish you well with future opportunities."),
                arguments(true, "Reed.co.uk", "Reed <no-reply@reed.example>",
                        "We’ve sent your application to Acme Ltd."),
                arguments(true, "Next steps", PERSON,
                        "Could we schedule an interview for Tuesday?"),
                arguments(true, "Good news", PERSON,
                        "We would like to offer you the position of Test Analyst."),
                arguments(true, "Outcome", PERSON,
                        "Your application has been rejected."),
                // Mass mailings are refused even when they talk about applications.
                arguments(false, "Weekly newsletter", "news@jobboard.example",
                        "Tips to improve your application."),
                arguments(false, "12 new roles", "Job Alerts <alerts@jobboard.example>",
                        "Roles picked for you. Thank you for applying through us before."),
                arguments(false, "Free webinar: interview skills", PERSON,
                        "Schedule your interview practice and confirm your seat."),
                arguments(false, "Your daily digest", PERSON, "Your application tips inside."),
                // No signal at all.
                arguments(false, "Lunch on Friday?", PERSON, "Are you free at noon?"),
                arguments(false, "Your order has shipped", "shop@store.example",
                        "Finally! All items are on the way."),
                // Substrings of the positive keywords must not count either.
                arguments(false, "Projector", PERSON,
                        "The projector is broken. See the application note."),
                arguments(false, null, null, null));
    }

    @ParameterizedTest(name = "[{index}] {0}: {1}")
    @MethodSource("jobRelatedCases")
    void isJobRelated(boolean expected, String subject, String from, String body) {
        assertThat(EmailRuleClassifier.isJobRelated(subject, from, body)).isEqualTo(expected);
    }

    static Stream<Arguments> categoryCases() {
        return Stream.of(
                arguments(EmailCategory.INTERVIEW_INVITATION, "Interview",
                        "We would like to invite you to an interview."),
                arguments(EmailCategory.INTERVIEW_INVITATION, "Next steps",
                        "Can we schedule an interview for Tuesday?"),
                arguments(EmailCategory.INTERVIEW_INVITATION, "Change of plan",
                        "We need to reschedule your interview."),
                arguments(EmailCategory.OFFER, "Good news",
                        "We are pleased to offer you the role. Thank you for attending the interview we scheduled."),
                arguments(EmailCategory.OFFER, "Your job offer", "Details attached."),
                arguments(EmailCategory.OFFER, "Offer", "Please find the offer for the position attached."),
                arguments(EmailCategory.REJECTION, "Your application",
                        "Thank you for applying. Unfortunately we will not be inviting you to interview."),
                arguments(EmailCategory.REJECTION, "Outcome",
                        "We regret to inform you that we are unable to offer you the position."),
                arguments(EmailCategory.REJECTION, "Outcome",
                        "After your interview we have decided not to proceed. We scheduled many candidates."),
                arguments(EmailCategory.REJECTION, "Outcome", "Your application was rejected."),
                arguments(EmailCategory.APPLICATION_CONFIRMATION, "Application received",
                        "We will be in touch."),
                // "latest" contains "test"; the role title contains "Test"; neither is an assessment.
                arguments(EmailCategory.APPLICATION_CONFIRMATION, "Thank you for applying",
                        "Your application for the Test Analyst role is the latest we received."),
                // A company describing what it offers is not a job offer.
                arguments(EmailCategory.APPLICATION_CONFIRMATION, "Thank you for applying",
                        "We offer flexible hours for this role."),
                arguments(EmailCategory.ASSESSMENT_REQUEST, "Next step",
                        "Please complete the online assessment within 5 days."),
                arguments(EmailCategory.ASSESSMENT_REQUEST, "Next step",
                        "Here is your coding challenge."),
                arguments(EmailCategory.SCREENING_CALL, "Quick chat",
                        "Are you free for a phone screen on Monday?"),
                arguments(EmailCategory.SCREENING_CALL, "Quick chat",
                        "I would like to screen candidates by phone this week."),
                // "smartphone" and "screenshot" are not a phone screen.
                arguments(EmailCategory.APPLICATION_CONFIRMATION, "Hello",
                        "Attach a screenshot from your smartphone."),
                arguments(EmailCategory.APPLICATION_CONFIRMATION, "Hello", "No clear signal here."),
                arguments(EmailCategory.APPLICATION_CONFIRMATION, null, null));
    }

    @ParameterizedTest(name = "[{index}] {0}: {1}")
    @MethodSource("categoryCases")
    void categorize(EmailCategory expected, String subject, String body) {
        assertThat(EmailRuleClassifier.categorize(subject, body)).isEqualTo(expected);
    }

    static Stream<Arguments> providerCases() {
        return Stream.of(
                arguments("LinkedIn", "LinkedIn <jobs-noreply@linkedin.com>", "Your application was sent."),
                arguments("Indeed", "Indeed Apply <indeedapply@indeed.com>", ""),
                arguments("Dice", "Dice <jobs@dice.com>", ""),
                arguments("Glassdoor", PERSON, "You applied via Glassdoor last week."),
                arguments("Indeed", PERSON, "Thanks for your application through Indeed."),
                // Plain mentions in the body say nothing about the source.
                arguments("Direct Email", PERSON, "We are indeed pleased. Follow us on LinkedIn."),
                arguments("Direct Email", "Candice Monsterson <candice@prejudice.example>", "Hello."),
                arguments("Company Website", "Acme Careers <no-reply@acme.example>", "Hello."),
                arguments("Company Website", "noreply@acme.example", null),
                arguments("Direct Email", null, null));
    }

    @ParameterizedTest(name = "[{index}] {0}: {1}")
    @MethodSource("providerCases")
    void provider(String expected, String from, String body) {
        assertThat(EmailRuleClassifier.provider(from, body)).isEqualTo(expected);
    }

    @Test
    void keywordsMatchWholeWordsAndPhrasesOnly() {
        assertThat(EmailRuleClassifier.words("all").matcher("call small finally").find()).isFalse();
        assertThat(EmailRuleClassifier.words("all").matcher("That is ALL.").find()).isTrue();
        assertThat(EmailRuleClassifier.words("job alert*").matcher("Your job   alerts").find()).isTrue();
        assertThat(EmailRuleClassifier.words("reject*").matcher("projector").find()).isFalse();
        assertThat(EmailRuleClassifier.words("reject*").matcher("was rejected,").find()).isTrue();
        assertThat(EmailRuleClassifier.words("c++ (senior)").matcher("a C++ (Senior) role").find()).isTrue();
    }
}
