package com.cvmaker.application.management.sheets;

/** Helpers for the A1 notation that Google Sheets uses for cell ranges. */
public final class A1Notation {

    private A1Notation() {
    }

    /**
     * Converts a column number to its letters: 1 is A, 26 is Z, 27 is AA, 703 is AAA.
     *
     * @param columnNumber the 1-based column number
     * @return the column letters
     * @throws IllegalArgumentException when the number is smaller than 1
     */
    public static String columnLetter(int columnNumber) {
        if (columnNumber < 1) {
            throw new IllegalArgumentException("Column number must be 1 or greater, got " + columnNumber);
        }
        StringBuilder letters = new StringBuilder();
        int remaining = columnNumber;
        while (remaining > 0) {
            int digit = (remaining - 1) % 26;
            letters.insert(0, (char) ('A' + digit));
            remaining = (remaining - 1) / 26;
        }
        return letters.toString();
    }

    /**
     * Puts a sheet name in front of a range, so that the range does not silently mean
     * "the first visible sheet".
     *
     * @param sheetTitle the sheet (tab) title; when null or empty the range is returned unchanged
     * @param range a range such as {@code A2:V10}
     * @return for example {@code 'Job Applications'!A2:V10}; a quote in the title is doubled
     */
    public static String onSheet(String sheetTitle, String range) {
        if (sheetTitle == null || sheetTitle.isEmpty()) {
            return range;
        }
        return "'" + sheetTitle.replace("'", "''") + "'!" + range;
    }
}
