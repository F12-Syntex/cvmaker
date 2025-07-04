package com.cvmaker.application.management;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GoogleSheetsService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);

    private Sheets sheetsService;
    private String spreadsheetId;

    public void initialize() throws IOException, GeneralSecurityException {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                .setClientId(ApplicationConfig.getGoogleClientId())
                .setClientSecret(ApplicationConfig.getGoogleClientSecret());
        GoogleClientSecrets clientSecrets = new GoogleClientSecrets().setInstalled(details);

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(ApplicationConfig.TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        this.sheetsService = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(ApplicationConfig.APPLICATION_NAME)
                .build();

        // Use the spreadsheet ID from config
        this.spreadsheetId = ApplicationConfig.getGoogleSheetId();
    }

    public void initializeSpreadsheet() throws IOException {
        // Check if spreadsheet exists
        try {
            sheetsService.spreadsheets().get(spreadsheetId).execute();
        } catch (IOException e) {
            // Create a new spreadsheet if it doesn't exist
            Spreadsheet spreadsheet = new Spreadsheet()
                    .setProperties(new SpreadsheetProperties()
                            .setTitle("Job Application Tracker"));

            Spreadsheet created = sheetsService.spreadsheets().create(spreadsheet).execute();
            spreadsheetId = created.getSpreadsheetId();

            // Save the new ID to config
            // This would need to be implemented in your ApplicationConfig
            System.out.println("Created new spreadsheet with ID: " + spreadsheetId);

            // Set up the spreadsheet with headers
            setupSpreadsheetHeaders();
        }
    }

    private void setupSpreadsheetHeaders() throws IOException {
        List<List<Object>> values = Collections.singletonList(
                Arrays.asList(
                        "Company", "Position", "Date Applied", "Category",
                        "Status", "Interview Date", "Location", "Notes",
                        "Email ID", "Last Updated"
                )
        );

        ValueRange body = new ValueRange().setValues(values);

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, "A1:J1", body)
                .setValueInputOption("RAW")
                .execute();

        // Format headers
        // (bold formatting is handled in the UpdateCellsRequest below)
        List<Request> requests = new ArrayList<>();
        // Create a RowData object with 10 bolded cells for the header row
        List<CellData> headerCells = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            headerCells.add(new CellData()
                    .setUserEnteredFormat(new CellFormat()
                            .setTextFormat(new TextFormat().setBold(true))));
        }
        List<RowData> rows = Collections.singletonList(new RowData().setValues(headerCells));
        requests.add(new Request()
                .setUpdateCells(new UpdateCellsRequest()
                        .setRange(new GridRange()
                                .setSheetId(0)
                                .setStartRowIndex(0)
                                .setEndRowIndex(1)
                                .setStartColumnIndex(0)
                                .setEndColumnIndex(10))
                        .setRows(rows)
                        .setFields("userEnteredFormat.textFormat.bold")
                )
        );

        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(requests);

        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
    }

    public void updateJobApplicationData(JobApplicationData jobData) throws IOException {
        // Check if this email ID already exists in the spreadsheet
        List<List<Object>> existingData = getExistingData();
        int rowIndex = -1;

        // Start from row 1 (after headers)
        for (int i = 0; i < existingData.size(); i++) {
            List<Object> row = existingData.get(i);
            if (row.size() >= 9 && row.get(8) != null && row.get(8).equals(jobData.getEmailId())) {
                rowIndex = i + 1; // +1 because of 0-indexing and we skipped header
                break;
            }
        }

        List<Object> rowData = Arrays.asList(
                jobData.getCompanyName() != null ? jobData.getCompanyName() : "",
                jobData.getPositionTitle() != null ? jobData.getPositionTitle() : "",
                jobData.getDate() != null ? jobData.getDate() : "",
                jobData.getCategory() != null ? jobData.getCategory().getDisplayName() : "",
                jobData.getApplicationStatus() != null ? jobData.getApplicationStatus() : "",
                jobData.getInterviewDate() != null ? jobData.getInterviewDate() : "",
                jobData.getInterviewLocation() != null ? jobData.getInterviewLocation() : "",
                jobData.getExtractedInfo() != null ? jobData.getExtractedInfo() : "",
                jobData.getEmailId(),
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        ValueRange body = new ValueRange().setValues(Collections.singletonList(rowData));

        if (rowIndex == -1) {
            // Append a new row
            sheetsService.spreadsheets().values()
                    .append(spreadsheetId, "A2:J", body)
                    .setValueInputOption("RAW")
                    .setInsertDataOption("INSERT_ROWS")
                    .execute();

            System.out.println("Added new job application to spreadsheet: " + jobData.getCompanyName() + " - " + jobData.getPositionTitle());
        } else {
            // Update existing row
            String range = "A" + (rowIndex + 1) + ":J" + (rowIndex + 1);
            sheetsService.spreadsheets().values()
                    .update(spreadsheetId, range, body)
                    .setValueInputOption("RAW")
                    .execute();

            System.out.println("Updated existing job application in spreadsheet: " + jobData.getCompanyName() + " - " + jobData.getPositionTitle());
        }
    }

    private List<List<Object>> getExistingData() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "A2:J")
                .execute();

        List<List<Object>> values = response.getValues();
        return values != null ? values : new ArrayList<>();
    }

    public void formatSpreadsheet() throws IOException {
        // Add alternating row colors for readability
        List<Request> requests = new ArrayList<>();

        // Add conditional formatting for status column
        requests.add(new Request()
                .setAddConditionalFormatRule(new AddConditionalFormatRuleRequest()
                        .setRule(new ConditionalFormatRule()
                                .setRanges(Collections.singletonList(
                                        new GridRange()
                                                .setSheetId(0)
                                                .setStartColumnIndex(4)
                                                .setEndColumnIndex(5)
                                                .setStartRowIndex(1)
                                ))
                                .setBooleanRule(new BooleanRule()
                                        .setCondition(new BooleanCondition()
                                                .setType("TEXT_CONTAINS")
                                                .setValues(Collections.singletonList(
                                                        new ConditionValue().setUserEnteredValue("Rejected")
                                                ))
                                        )
                                        .setFormat(new CellFormat()
                                                .setBackgroundColor(new Color()
                                                        .setRed(1.0f)
                                                        .setGreen(0.8f)
                                                        .setBlue(0.8f)
                                                ))
                                )
                        )
                )
        );

        // Add conditional formatting for offers
        requests.add(new Request()
                .setAddConditionalFormatRule(new AddConditionalFormatRuleRequest()
                        .setRule(new ConditionalFormatRule()
                                .setRanges(Collections.singletonList(
                                        new GridRange()
                                                .setSheetId(0)
                                                .setStartColumnIndex(4)
                                                .setEndColumnIndex(5)
                                                .setStartRowIndex(1)
                                ))
                                .setBooleanRule(new BooleanRule()
                                        .setCondition(new BooleanCondition()
                                                .setType("TEXT_CONTAINS")
                                                .setValues(Collections.singletonList(
                                                        new ConditionValue().setUserEnteredValue("Offer")
                                                ))
                                        )
                                        .setFormat(new CellFormat()
                                                .setBackgroundColor(new Color()
                                                        .setRed(0.8f)
                                                        .setGreen(1.0f)
                                                        .setBlue(0.8f)
                                                ))
                                )
                        )
                )
        );

        // Add conditional formatting for interview scheduled
        requests.add(new Request()
                .setAddConditionalFormatRule(new AddConditionalFormatRuleRequest()
                        .setRule(new ConditionalFormatRule()
                                .setRanges(Collections.singletonList(
                                        new GridRange()
                                                .setSheetId(0)
                                                .setStartColumnIndex(4)
                                                .setEndColumnIndex(5)
                                                .setStartRowIndex(1)
                                ))
                                .setBooleanRule(new BooleanRule()
                                        .setCondition(new BooleanCondition()
                                                .setType("TEXT_CONTAINS")
                                                .setValues(Collections.singletonList(
                                                        new ConditionValue().setUserEnteredValue("Interview")
                                                ))
                                        )
                                        .setFormat(new CellFormat()
                                                .setBackgroundColor(new Color()
                                                        .setRed(0.8f)
                                                        .setGreen(0.8f)
                                                        .setBlue(1.0f)
                                                ))
                                )
                        )
                )
        );

        // Autosize columns
        for (int i = 0; i < 10; i++) {
            requests.add(new Request()
                    .setAutoResizeDimensions(new AutoResizeDimensionsRequest()
                            .setDimensions(new DimensionRange()
                                    .setSheetId(0)
                                    .setDimension("COLUMNS")
                                    .setStartIndex(i)
                                    .setEndIndex(i + 1)
                            )
                    )
            );
        }

        // Execute all formatting requests
        BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(requests);

        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
        System.out.println("Spreadsheet formatting applied");
    }
}
