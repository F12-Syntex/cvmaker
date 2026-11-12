package com.cvmaker.application.management.sheets;

import java.util.Arrays;
import java.util.List;

/**
 * The columns of the tracker sheet, in sheet order. The order of the constants is the order of
 * the columns, so adding, moving or removing a constant changes the sheet layout.
 */
public enum SheetColumn {

    // Visible columns (A-H)
    COMPANY("Company", 200, true),
    POSITION("Position", 280, true),
    DATE_APPLIED("Date Applied", 120, true),
    STATUS("Status", 120, true),
    PROVIDER("Provider", 100, true),
    EMAIL_LINK("Email Link", 80, true),
    APPLICATION_URL("Application URL", 80, true),
    NOTES("Notes", 350, true),

    // Hidden columns (I-V), kept for data storage
    CONTACT_EMAIL("Contact Email", 150, false),
    WORK_LOCATION("Work Location", 130, false),
    INTERVIEW_DATE("Interview Date", 120, false),
    WORK_TYPE("Work Type", 100, false),
    SALARY_RANGE("Salary Range", 150, false),
    CONTACT_PERSON("Contact Person", 150, false),
    NEXT_STEPS("Next Steps", 200, false),
    EMAIL_ID("Email ID", 150, false),
    DEADLINE("Deadline", 120, false),
    SKILLS("Skills", 200, false),
    REJECTION_REASON("Rejection Reason", 200, false),
    OFFER_DETAILS("Offer Details", 200, false),
    LAST_UPDATED("Last Updated", 150, false),
    EMAIL_TIMESTAMP("Email Timestamp", 150, false);

    private static final List<SheetColumn> ALL = List.of(values());

    private final String header;
    private final int pixelWidth;
    private final boolean visible;

    SheetColumn(String header, int pixelWidth, boolean visible) {
        this.header = header;
        this.pixelWidth = pixelWidth;
        this.visible = visible;
    }

    /** The text of the header cell. */
    public String header() {
        return header;
    }

    /** The column width in pixels. */
    public int pixelWidth() {
        return pixelWidth;
    }

    /** False for the columns that are hidden from the user and only store data. */
    public boolean isVisible() {
        return visible;
    }

    /** The 0-based column index, as used by grid ranges in the Sheets API. */
    public int index() {
        return ordinal();
    }

    /** The column letter in A1 notation, for example {@code D} for {@link #STATUS}. */
    public String letter() {
        return A1Notation.columnLetter(ordinal() + 1);
    }

    /** All columns in sheet order. */
    public static List<SheetColumn> all() {
        return ALL;
    }

    /** The number of columns. */
    public static int count() {
        return ALL.size();
    }

    /** The letter of the last column, for example {@code V}. */
    public static String lastLetter() {
        return A1Notation.columnLetter(ALL.size());
    }

    /**
     * The number of leading columns up to and including the last visible one. Used as the
     * exclusive end index of ranges that cover what the user sees.
     */
    public static int visibleColumnEnd() {
        return Arrays.stream(values())
                .filter(SheetColumn::isVisible)
                .mapToInt(column -> column.index() + 1)
                .max()
                .orElse(0);
    }
}
