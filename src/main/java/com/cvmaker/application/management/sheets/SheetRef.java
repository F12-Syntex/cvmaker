package com.cvmaker.application.management.sheets;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;

/**
 * The sheet (tab) of a spreadsheet that the tracker reads and writes.
 *
 * <p>The Sheets API addresses a sheet in two ways: grid ranges use the numeric sheet id, value
 * ranges use the title. The id of the first sheet is 0 only in a spreadsheet that was created
 * empty and never reorganised, so both are looked up.
 *
 * @param sheetId the numeric id used in grid ranges
 * @param title the title used in A1 ranges; null when unknown, which leaves ranges unqualified
 */
public record SheetRef(int sheetId, String title) {

    /** What the service assumed before it looked the sheet up: id 0, ranges unqualified. */
    public static final SheetRef DEFAULT = new SheetRef(0, null);

    /**
     * Finds the first sheet of a spreadsheet: the one with the lowest index.
     *
     * @param spreadsheet a spreadsheet as returned by the API, with its sheet properties
     * @return the first sheet, or {@link #DEFAULT} when the response lists no sheets
     */
    public static SheetRef firstOf(Spreadsheet spreadsheet) {
        List<Sheet> sheets = spreadsheet == null ? null : spreadsheet.getSheets();
        if (sheets == null) {
            return DEFAULT;
        }
        return sheets.stream()
                .filter(Objects::nonNull)
                .map(Sheet::getProperties)
                .filter(properties -> properties != null && properties.getSheetId() != null)
                .min(Comparator.comparingInt(SheetRef::indexOf))
                .map(properties -> new SheetRef(properties.getSheetId(), properties.getTitle()))
                .orElse(DEFAULT);
    }

    /**
     * Qualifies an A1 range with this sheet's title.
     *
     * @param a1Range a range such as {@code A2:V}
     * @return for example {@code 'Sheet1'!A2:V}
     */
    public String range(String a1Range) {
        return A1Notation.onSheet(title, a1Range);
    }

    private static int indexOf(SheetProperties properties) {
        return properties.getIndex() == null ? 0 : properties.getIndex();
    }
}
