package com.cvmaker.application.management.sheets;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.cvmaker.application.management.ApplicationStatus;

/**
 * Builds the custom formulas of the conditional formatting that colours rows by status.
 *
 * <p>The formulas compare the Status cell with the labels of {@link ApplicationStatus}, which
 * are also what {@link SheetRowMapper} writes into that cell, so the two cannot drift apart.
 */
public final class StatusFormatRules {

    /** Rows coloured red. */
    public static final List<ApplicationStatus> NEGATIVE = List.of(ApplicationStatus.REJECTED);
    /** Rows coloured green. */
    public static final List<ApplicationStatus> POSITIVE = List.of(ApplicationStatus.OFFERED,
            ApplicationStatus.ACCEPTED);
    /** Rows coloured yellow. */
    public static final List<ApplicationStatus> INTERVIEWING = List.of(ApplicationStatus.INTERVIEW,
            ApplicationStatus.FINAL_INTERVIEW);

    private StatusFormatRules() {
    }

    /**
     * Builds the formula that is true for rows whose status is one of the given statuses. It is
     * written for the first data row (row 2); Sheets shifts it for the rows below.
     *
     * @param statuses the statuses to match, at least one
     * @return for example {@code =OR(UPPER(TRIM($D2))="OFFERED",UPPER(TRIM($D2))="ACCEPTED")}
     * @throws IllegalArgumentException when no status is given
     */
    public static String formulaFor(List<ApplicationStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            throw new IllegalArgumentException("At least one status is needed");
        }
        String cell = "UPPER(TRIM($" + SheetColumn.STATUS.letter() + HyperlinkRequests.FIRST_DATA_ROW + "))";
        List<String> tests = statuses.stream()
                .map(status -> cell + "=\"" + status.label().toUpperCase(Locale.ROOT) + "\"")
                .collect(Collectors.toList());
        return tests.size() == 1 ? "=" + tests.get(0) : "=OR(" + String.join(",", tests) + ")";
    }
}
