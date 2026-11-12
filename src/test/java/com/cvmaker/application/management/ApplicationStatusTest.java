package com.cvmaker.application.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationStatusTest {

    @ParameterizedTest
    @CsvSource({
        // the spellings the two old rankings disagreed on
        "Offer, OFFERED",
        "Offered, OFFERED",
        "offered, OFFERED",
        "Job Offer, OFFERED",
        "Offer extended, OFFERED",
        "Interview, INTERVIEW",
        "Interview Scheduled, INTERVIEW",
        "INTERVIEW SCHEDULED, INTERVIEW",
        "Phone interview, INTERVIEW",
        "Second interview, INTERVIEW",
        "Interview confirmed, INTERVIEW",
        "Final Interview, FINAL_INTERVIEW",
        "Final round, FINAL_INTERVIEW",
        "Interview (final stage), FINAL_INTERVIEW",
        "Assessment, ASSESSMENT",
        "Online assessment, ASSESSMENT",
        "Technical test, ASSESSMENT",
        "Take-home assignment, ASSESSMENT",
        "Coding challenge, ASSESSMENT",
        "Screening, SCREENING",
        "Screening Call, SCREENING",
        "Phone screen, SCREENING",
        "Under Review, SCREENING",
        "under_review, SCREENING",
        "Shortlisted, SCREENING",
        "Under consideration, SCREENING",
        "Applied, APPLIED",
        "APPLIED, APPLIED",
        "  applied  , APPLIED",
        "Application Received, APPLIED",
        "Received, APPLIED",
        "Application Confirmation, APPLIED",
        "Submitted, APPLIED",
        "Application accepted, APPLIED",
        "Pending, APPLIED",
        "Rejected, REJECTED",
        "REJECTED, REJECTED",
        "Rejection, REJECTED",
        "Application rejected, REJECTED",
        "Not selected, REJECTED",
        "Not selected for interview, REJECTED",
        "Unsuccessful, REJECTED",
        "Declined, REJECTED",
        "Not moving forward, REJECTED",
        "No longer under consideration, REJECTED",
        "Position filled, REJECTED",
        "Offer rescinded, REJECTED",
        "No offer, REJECTED",
        "Withdrawn, WITHDRAWN",
        "Application withdrawn, WITHDRAWN",
        "Candidate withdrew, WITHDRAWN",
        "Accepted, ACCEPTED",
        "Offer accepted, ACCEPTED",
        "Accepted offer, ACCEPTED",
        "Offer Acceptance, ACCEPTED",
        "Hired, ACCEPTED"
    })
    void normalizesFreeText(String text, ApplicationStatus expected) {
        assertThat(ApplicationStatus.normalize(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Unknown", "N/A", "Recruiter Outreach", "Company Update", "latest news", "???"})
    void unrecognisedTextIsUnknown(String text) {
        assertThat(ApplicationStatus.normalize(text)).isEqualTo(ApplicationStatus.UNKNOWN);
        assertThat(ApplicationStatus.rankOf(text)).isZero();
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus.class)
    void everyLabelNormalizesToItsOwnStatus(ApplicationStatus status) {
        assertThat(ApplicationStatus.normalize(status.label())).isEqualTo(status);
        assertThat(ApplicationStatus.normalize(status.label().toUpperCase())).isEqualTo(status);
        assertThat(ApplicationStatus.normalize(status.name())).isEqualTo(status);
    }

    @Test
    void thereIsOneRankingWithDistinctRanks() {
        assertThat(Arrays.stream(ApplicationStatus.values()).map(ApplicationStatus::rank))
                .doesNotHaveDuplicates()
                .isSorted();
        assertThat(ApplicationStatus.values()).containsExactly(
                ApplicationStatus.UNKNOWN,
                ApplicationStatus.APPLIED,
                ApplicationStatus.SCREENING,
                ApplicationStatus.ASSESSMENT,
                ApplicationStatus.INTERVIEW,
                ApplicationStatus.FINAL_INTERVIEW,
                ApplicationStatus.REJECTED,
                ApplicationStatus.WITHDRAWN,
                ApplicationStatus.OFFERED,
                ApplicationStatus.ACCEPTED);
    }

    @Test
    void rejectionBeatsEveryInProgressStageButNotAnOffer() {
        // ApplicationManager ranked "Rejected" highest; GoogleSheetsService ranked it lowest.
        assertThat(ApplicationStatus.isMoreAdvanced("Rejected", "Applied")).isTrue();
        assertThat(ApplicationStatus.isMoreAdvanced("Rejected", "Interview Scheduled")).isTrue();
        assertThat(ApplicationStatus.isMoreAdvanced("Rejected", "Final interview")).isTrue();
        assertThat(ApplicationStatus.isMoreAdvanced("Interview", "Rejected")).isFalse();
        assertThat(ApplicationStatus.isMoreAdvanced("Rejected", "Offered")).isFalse();
        assertThat(ApplicationStatus.isMoreAdvanced("Offer", "Rejected")).isTrue();
        assertThat(ApplicationStatus.isMoreAdvanced("Accepted", "Offered")).isTrue();
    }

    @Test
    void pipelineStagesRankInOrder() {
        assertThat(ApplicationStatus.isMoreAdvanced("Screening", "Applied")).isTrue();
        assertThat(ApplicationStatus.isMoreAdvanced("Assessment", "Screening")).isTrue();
        assertThat(ApplicationStatus.isMoreAdvanced("Interview", "Assessment")).isTrue();
        assertThat(ApplicationStatus.isMoreAdvanced("Final Interview", "Interview")).isTrue();
        assertThat(ApplicationStatus.isMoreAdvanced("Offered", "Final Interview")).isTrue();
        assertThat(ApplicationStatus.isMoreAdvanced("Applied", "Interview")).isFalse();
    }

    @Test
    void differentSpellingsOfOneStatusTie() {
        assertThat(ApplicationStatus.isMoreAdvanced("Offer", "Offered")).isFalse();
        assertThat(ApplicationStatus.isMoreAdvanced("Offered", "Offer")).isFalse();
        assertThat(ApplicationStatus.isMoreAdvanced("Interview Scheduled", "interview")).isFalse();
    }

    @Test
    void unknownNeverReplacesAKnownStatus() {
        // The old sheet ranking treated any unknown status as more advanced.
        assertThat(ApplicationStatus.isMoreAdvanced("something else", "Applied")).isFalse();
        assertThat(ApplicationStatus.isMoreAdvanced(null, "Applied")).isFalse();
        assertThat(ApplicationStatus.isMoreAdvanced("something else", null)).isFalse();
        assertThat(ApplicationStatus.isMoreAdvanced(null, null)).isFalse();
        assertThat(ApplicationStatus.isMoreAdvanced("Applied", null)).isTrue();
        assertThat(ApplicationStatus.isMoreAdvanced("Applied", "something else")).isTrue();
    }

    @Test
    void terminalStatuses() {
        assertThat(Arrays.stream(ApplicationStatus.values()).filter(ApplicationStatus::isTerminal))
                .containsExactly(ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN, ApplicationStatus.ACCEPTED);
    }

    @Test
    void displayLabelIsCanonicalForKnownTextAndTheTextItselfOtherwise() {
        assertThat(ApplicationStatus.displayLabel("offer")).isEqualTo("Offered");
        assertThat(ApplicationStatus.displayLabel("Interview Scheduled")).isEqualTo("Interview");
        assertThat(ApplicationStatus.displayLabel("  On hold  ")).isEqualTo("On hold");
        assertThat(ApplicationStatus.displayLabel(null)).isEmpty();
        assertThat(ApplicationStatus.displayLabel("  ")).isEmpty();
    }
}
