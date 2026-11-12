package com.cvmaker.application.management.sheets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cvmaker.application.management.JobApplicationData;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;

/**
 * Builds the batch-update requests that turn the two link cells of a row into hyperlinks.
 * It only builds request objects and makes no API call.
 */
public final class HyperlinkRequests {

    /** Text shown for the link to the email. */
    public static final String EMAIL_LINK_LABEL = "📧 View";
    /** Text shown for the link to the job advert or application page. */
    public static final String APPLICATION_LINK_LABEL = "🔗 Apply";

    /** The 1-based sheet row of the first data row; row 1 holds the headers. */
    public static final int FIRST_DATA_ROW = 2;

    private HyperlinkRequests() {
    }

    /**
     * Builds the requests for one row. A link that is missing or is not a safe URL produces no
     * request, so its cell keeps whatever {@link SheetRowMapper} wrote (nothing).
     *
     * @param sheetId the id of the sheet (tab) the row is on
     * @param rowNumber the 1-based row number, as shown in the sheet
     * @param job the application on that row
     * @return zero, one or two requests
     */
    public static List<Request> forRow(int sheetId, int rowNumber, JobApplicationData job) {
        List<Request> requests = new ArrayList<>(2);
        HyperlinkFormula.build(job.getEmailLink(), EMAIL_LINK_LABEL)
                .ifPresent(formula -> requests.add(formulaCell(sheetId, rowNumber, SheetColumn.EMAIL_LINK, formula)));
        HyperlinkFormula.build(job.getApplicationUrl(), APPLICATION_LINK_LABEL)
                .ifPresent(formula -> requests.add(formulaCell(sheetId, rowNumber, SheetColumn.APPLICATION_URL, formula)));
        return requests;
    }

    /**
     * Builds the requests for consecutive rows, the first application being on
     * {@link #FIRST_DATA_ROW}.
     *
     * @param sheetId the id of the sheet (tab) the rows are on
     * @param jobs the applications, in row order
     * @return the requests for all rows
     */
    public static List<Request> forRows(int sheetId, List<JobApplicationData> jobs) {
        List<Request> requests = new ArrayList<>();
        for (int i = 0; i < jobs.size(); i++) {
            requests.addAll(forRow(sheetId, FIRST_DATA_ROW + i, jobs.get(i)));
        }
        return requests;
    }

    private static Request formulaCell(int sheetId, int rowNumber, SheetColumn column, String formula) {
        return new Request().setUpdateCells(new UpdateCellsRequest()
                .setRange(new GridRange()
                        .setSheetId(sheetId)
                        .setStartRowIndex(rowNumber - 1).setEndRowIndex(rowNumber)
                        .setStartColumnIndex(column.index()).setEndColumnIndex(column.index() + 1))
                .setRows(Collections.singletonList(new RowData()
                        .setValues(Collections.singletonList(new CellData()
                                .setUserEnteredValue(new ExtendedValue().setFormulaValue(formula))))))
                .setFields("userEnteredValue"));
    }
}
