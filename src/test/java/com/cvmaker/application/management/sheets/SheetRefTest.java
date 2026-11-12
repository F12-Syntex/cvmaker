package com.cvmaker.application.management.sheets;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;

class SheetRefTest {

    private static Sheet sheet(Integer id, String title, Integer index) {
        return new Sheet().setProperties(new SheetProperties().setSheetId(id).setTitle(title).setIndex(index));
    }

    private static HttpResponseException httpError(int status, String message) {
        return new HttpResponseException.Builder(status, message, new HttpHeaders()).build();
    }

    @Test
    void firstSheetIsTheOneWithTheLowestIndex() {
        Spreadsheet spreadsheet = new Spreadsheet().setSheets(List.of(
                sheet(111, "Archive", 2),
                sheet(987654321, "Jobs", 0),
                sheet(222, "Notes", 1)));

        assertThat(SheetRef.firstOf(spreadsheet)).isEqualTo(new SheetRef(987654321, "Jobs"));
    }

    @Test
    void missingIndexCountsAsFirst() {
        // The API leaves out fields that hold their default value, such as index 0.
        Spreadsheet spreadsheet = new Spreadsheet().setSheets(List.of(
                sheet(5, "Second", 1),
                sheet(9, "First", null)));

        assertThat(SheetRef.firstOf(spreadsheet)).isEqualTo(new SheetRef(9, "First"));
    }

    @Test
    void fallsBackToTheDefaultWhenNoSheetIsListed() {
        assertThat(SheetRef.firstOf(null)).isEqualTo(SheetRef.DEFAULT);
        assertThat(SheetRef.firstOf(new Spreadsheet())).isEqualTo(SheetRef.DEFAULT);
        assertThat(SheetRef.firstOf(new Spreadsheet().setSheets(List.of()))).isEqualTo(SheetRef.DEFAULT);
        assertThat(SheetRef.firstOf(new Spreadsheet().setSheets(Arrays.asList(null, new Sheet()))))
                .isEqualTo(SheetRef.DEFAULT);
        assertThat(SheetRef.DEFAULT.sheetId()).isZero();
    }

    @Test
    void rangeIsQualifiedWithTheTitleWhenThereIsOne() {
        assertThat(new SheetRef(3, "Jobs 2025").range("A2:V")).isEqualTo("'Jobs 2025'!A2:V");
        assertThat(SheetRef.DEFAULT.range("A2:V")).isEqualTo("A2:V");
    }

    @Test
    void onlyHttp404MeansNotFound() {
        assertThat(SheetsErrors.isNotFound(httpError(404, "Not Found"))).isTrue();

        assertThat(SheetsErrors.isNotFound(httpError(400, "Bad Request"))).isFalse();
        assertThat(SheetsErrors.isNotFound(httpError(401, "Unauthorized"))).isFalse();
        assertThat(SheetsErrors.isNotFound(httpError(403, "Forbidden"))).isFalse();
        assertThat(SheetsErrors.isNotFound(httpError(429, "Too Many Requests"))).isFalse();
        assertThat(SheetsErrors.isNotFound(httpError(500, "Internal Server Error"))).isFalse();
        assertThat(SheetsErrors.isNotFound(new IOException("Connection reset"))).isFalse();
        assertThat(SheetsErrors.isNotFound(new SocketTimeoutException("Read timed out"))).isFalse();
    }

    @Test
    void describesErrorsBriefly() {
        assertThat(SheetsErrors.describe(httpError(403, "Forbidden"))).isEqualTo("HTTP 403 Forbidden");
        assertThat(SheetsErrors.describe(httpError(500, null))).isEqualTo("HTTP 500");
        assertThat(SheetsErrors.describe(new IOException("Connection reset"))).isEqualTo("Connection reset");
        assertThat(SheetsErrors.describe(new IOException())).isEqualTo("IOException");
    }
}
