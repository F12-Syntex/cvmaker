package com.cvmaker.application.management.sheets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.cvmaker.application.management.ApplicationStatus;
import com.cvmaker.application.management.JobApplicationData;

class StatusFormatRulesTest {

    @Test
    void singleStatusFormula() {
        assertThat(StatusFormatRules.formulaFor(StatusFormatRules.NEGATIVE))
                .isEqualTo("=UPPER(TRIM($D2))=\"REJECTED\"");
    }

    @Test
    void severalStatusesAreJoinedWithOr() {
        assertThat(StatusFormatRules.formulaFor(StatusFormatRules.POSITIVE))
                .isEqualTo("=OR(UPPER(TRIM($D2))=\"OFFERED\",UPPER(TRIM($D2))=\"ACCEPTED\")");
        assertThat(StatusFormatRules.formulaFor(StatusFormatRules.INTERVIEWING))
                .isEqualTo("=OR(UPPER(TRIM($D2))=\"INTERVIEW\",UPPER(TRIM($D2))=\"FINAL INTERVIEW\")");
    }

    @Test
    void needsAtLeastOneStatus() {
        assertThatIllegalArgumentException().isThrownBy(() -> StatusFormatRules.formulaFor(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> StatusFormatRules.formulaFor(null));
    }

    @Test
    void whatTheRowMapperWritesIsWhatTheRulesMatch() {
        // The old rules matched only the exact text "REJECTED", "OFFERED", "INTERVIEW"..., while
        // the AI writes "Offer", "Interview Scheduled" or "Not selected".
        assertMatches("Offer", StatusFormatRules.POSITIVE);
        assertMatches("offer accepted", StatusFormatRules.POSITIVE);
        assertMatches("Interview Scheduled", StatusFormatRules.INTERVIEWING);
        assertMatches("Final round", StatusFormatRules.INTERVIEWING);
        assertMatches("Not selected", StatusFormatRules.NEGATIVE);
        assertMatches("rejected", StatusFormatRules.NEGATIVE);
    }

    private static void assertMatches(String rawStatus, List<ApplicationStatus> group) {
        JobApplicationData job = new JobApplicationData();
        job.setApplicationStatus(rawStatus);
        Object cell = SheetRowMapper.toRow(job, LocalDateTime.of(2025, 1, 1, 0, 0)).get(SheetColumn.STATUS.index());

        String cellAsTheFormulaSeesIt = cell.toString().trim().toUpperCase(Locale.ROOT);
        assertThat(StatusFormatRules.formulaFor(group)).contains("=\"" + cellAsTheFormulaSeesIt + "\"");
    }
}
