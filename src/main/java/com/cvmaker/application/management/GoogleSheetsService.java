package com.cvmaker.application.management;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cvmaker.application.management.sheets.DateParsing;
import com.cvmaker.application.management.sheets.HyperlinkRequests;
import com.cvmaker.application.management.sheets.SheetColumn;
import com.cvmaker.application.management.sheets.SheetRowMapper;
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
import com.google.api.services.sheets.v4.model.BasicFilter;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.DimensionProperties;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.GridProperties;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.SetBasicFilterRequest;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.TextFormat;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;
import com.google.api.services.sheets.v4.model.UpdateDimensionPropertiesRequest;
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;

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

        this.spreadsheetId = ApplicationConfig.getGoogleSheetId();
    }

    public void initializeSpreadsheet() throws IOException {
        try {
            sheetsService.spreadsheets().get(spreadsheetId).execute();
        } catch (IOException e) {
            Spreadsheet spreadsheet = new Spreadsheet()
                    .setProperties(new SpreadsheetProperties()
                            .setTitle("Job Application Tracker"));

            Spreadsheet created = sheetsService.spreadsheets().create(spreadsheet).execute();
            spreadsheetId = created.getSpreadsheetId();

            System.out.println("Created new spreadsheet with ID: " + spreadsheetId);
            setupEnhancedSpreadsheetHeaders();
        }
    }

    // Modified bulkUpdateAllApplications to ensure only latest updates are shown
    public void bulkUpdateAllApplications(Map<String, JobApplicationData> consolidatedApplications) throws IOException {
        System.out.println("Starting bulk update of " + consolidatedApplications.size() + " applications...");

        // Clear existing data (keeping headers)
        clearExistingData();

        // Get the most recent application per company
        Map<String, JobApplicationData> latestApplications = new HashMap<>();
        
        for (JobApplicationData jobData : consolidatedApplications.values()) {
            if (jobData.isJobRelated()) {
                String companyKey = jobData.getCompanyName().trim().toLowerCase();
                
                // If we don't have this company yet, or this email is newer than what we have
                if (!latestApplications.containsKey(companyKey) || 
                    isNewerApplication(jobData, latestApplications.get(companyKey))) {
                    latestApplications.put(companyKey, jobData);
                }
            }
        }

        // Prepare data rows from the filtered map
        List<List<Object>> allRows = new ArrayList<>();
        List<JobApplicationData> applicationsWithUrls = new ArrayList<>();

        for (JobApplicationData jobData : latestApplications.values()) {
            List<Object> rowData = SheetRowMapper.toRow(jobData, LocalDateTime.now());
            allRows.add(rowData);
            applicationsWithUrls.add(jobData);
        }

        if (allRows.isEmpty()) {
            System.out.println("No job-related applications to sync.");
            return;
        }

        // Bulk insert all data at once
        ValueRange body = new ValueRange().setValues(allRows);
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, "A2:" + SheetColumn.lastLetter() + (allRows.size() + 1), body)
                .setValueInputOption("RAW")
                .execute();

        System.out.println("Inserted " + allRows.size() + " rows of data");

        // Add hyperlinks for all rows in batch
        addAllHyperlinksInBatch(applicationsWithUrls);

        System.out.println("Bulk update completed successfully!");
    }

    private void clearExistingData() throws IOException {
        // Get the current data to determine how many rows to clear
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "A2:" + SheetColumn.lastLetter())
                .execute();

        List<List<Object>> values = response.getValues();
        if (values != null && !values.isEmpty()) {
            int rowsToDelete = values.size();

            // Clear the range (this preserves formatting but removes data)
            com.google.api.services.sheets.v4.model.ClearValuesRequest clearRequest = new com.google.api.services.sheets.v4.model.ClearValuesRequest();
            sheetsService.spreadsheets().values()
                    .clear(spreadsheetId, "A2:" + SheetColumn.lastLetter() + (rowsToDelete + 1), clearRequest)
                    .execute();

            System.out.println("Cleared " + rowsToDelete + " existing rows");
        }
    }

    private void addAllHyperlinksInBatch(List<JobApplicationData> applications) throws IOException {
        List<Request> requests = HyperlinkRequests.forRows(0, applications);

        // Execute all hyperlink updates in batches to avoid request size limits
        if (!requests.isEmpty()) {
            int batchSize = 100; // Process in batches of 100 requests
            for (int i = 0; i < requests.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, requests.size());
                List<Request> batch = requests.subList(i, endIndex);

                sheetsService.spreadsheets().batchUpdate(spreadsheetId,
                        new BatchUpdateSpreadsheetRequest().setRequests(batch)).execute();

                System.out.println("Added hyperlinks for batch " + (i / batchSize + 1) + "/"
                        + ((requests.size() + batchSize - 1) / batchSize));
            }
        }
    }

    // Updated to ensure only the latest application data is shown/updated
    public void updateJobApplicationData(JobApplicationData jobData) throws IOException {
        List<List<Object>> existingData = getExistingData();

        String companyName = jobData.getCompanyName() != null ? jobData.getCompanyName().trim() : "";

        // Find existing entries for this company
        List<Integer> companyRowIndices = new ArrayList<>();
        int latestRowIndex = -1;
        JobApplicationData latestJobData = null;

        for (int i = 0; i < existingData.size(); i++) {
            List<Object> row = existingData.get(i);
            if (row.size() >= 1 && row.get(0) != null) {
                String existingCompany = row.get(0).toString().trim();

                if (existingCompany.equalsIgnoreCase(companyName)) {
                    int actualRowIndex = i + 2;
                    companyRowIndices.add(actualRowIndex);

                    // Extract the job data from this row to compare timestamps
                    JobApplicationData existingJobData = extractJobDataFromRow(row);
                    
                    if (latestJobData == null || isNewerApplication(existingJobData, latestJobData)) {
                        latestJobData = existingJobData;
                        latestRowIndex = actualRowIndex;
                    }
                }
            }
        }

        // Now compare if the new job data is newer than what's already in the sheet
        if (latestJobData != null && !isNewerApplication(jobData, latestJobData)) {
            System.out.println("Skipping update for " + jobData.getCompanyName() + 
                    " as we already have a newer entry in the spreadsheet.");
            return;
        }

        // Create row data using the metadata
        List<Object> rowData = SheetRowMapper.toRow(jobData, LocalDateTime.now());

        if (companyRowIndices.isEmpty()) {
            // Add new row
            ValueRange body = new ValueRange().setValues(Collections.singletonList(rowData));
            sheetsService.spreadsheets().values()
                    .append(spreadsheetId, "A2:" + SheetColumn.lastLetter() + "2", body)
                    .setValueInputOption("RAW")
                    .setInsertDataOption("INSERT_ROWS")
                    .execute();

            // Get the row number of the newly added row and add hyperlinks
            List<List<Object>> newData = getExistingData();
            int newRowIndex = newData.size() + 1; // +1 for header row
            addHyperlinks(newRowIndex, jobData);

            System.out.println("Added new job application: " + jobData.getCompanyName() + " - " + jobData.getPositionTitle());
        } else {
            // Update existing row
            ValueRange body = new ValueRange().setValues(Collections.singletonList(rowData));
            String range = "A" + latestRowIndex + ":" + SheetColumn.lastLetter() + latestRowIndex;
            sheetsService.spreadsheets().values()
                    .update(spreadsheetId, range, body)
                    .setValueInputOption("RAW")
                    .execute();

            // Add hyperlinks to updated row
            addHyperlinks(latestRowIndex, jobData);

            // Remove duplicates
            if (companyRowIndices.size() > 1) {
                deleteDuplicateRows(companyRowIndices, latestRowIndex);
            }

            System.out.println("Updated application: " + jobData.getCompanyName() + " - " + jobData.getPositionTitle() + " (" + jobData.getApplicationStatus() + ")");
        }
    }

    // Helper method to determine which application is newer
    private boolean isNewerApplication(JobApplicationData newApp, JobApplicationData existingApp) {
        // First try to compare emailTimestamp
        if (newApp.getEmailTimestamp() != null && existingApp.getEmailTimestamp() != null) {
            try {
                LocalDateTime newTime = LocalDateTime.parse(newApp.getEmailTimestamp());
                LocalDateTime existingTime = LocalDateTime.parse(existingApp.getEmailTimestamp());
                return newTime.isAfter(existingTime);
            } catch (DateTimeParseException e) {
                // Fall through to next comparison method
            }
        }
        
        // Then try to compare lastUpdated
        if (newApp.getLastUpdated() != null && existingApp.getLastUpdated() != null) {
            try {
                LocalDateTime newTime = LocalDateTime.parse(newApp.getLastUpdated());
                LocalDateTime existingTime = LocalDateTime.parse(existingApp.getLastUpdated());
                return newTime.isAfter(existingTime);
            } catch (DateTimeParseException e) {
                // Fall through to next comparison method
            }
        }
        
        // Then try to compare processedTimestamp
        if (newApp.getProcessedTimestamp() != null && existingApp.getProcessedTimestamp() != null) {
            try {
                LocalDateTime newTime = LocalDateTime.parse(newApp.getProcessedTimestamp());
                LocalDateTime existingTime = LocalDateTime.parse(existingApp.getProcessedTimestamp());
                return newTime.isAfter(existingTime);
            } catch (DateTimeParseException e) {
                // Fall through to next comparison method
            }
        }
        
        // Finally, if all else fails, try to compare the application date
        if (newApp.getDate() != null && existingApp.getDate() != null) {
            LocalDate newDate = DateParsing.parseDate(newApp.getDate()).orElse(null);
            LocalDate existingDate = DateParsing.parseDate(existingApp.getDate()).orElse(null);
            if (newDate != null && existingDate != null) {
                return newDate.isAfter(existingDate);
            }
        }
        
        // If we can't determine which is newer, prefer the one with a "more advanced" status
        return isMoreAdvancedStatus(newApp.getApplicationStatus(), existingApp.getApplicationStatus());
    }

    // Helper method to determine if a status is "more advanced" in the application process
    private boolean isMoreAdvancedStatus(String newStatus, String existingStatus) {
        if (newStatus == null) return false;
        if (existingStatus == null) return true;
        
        // Define a hierarchy of statuses (higher index = more advanced)
        Map<String, Integer> statusRanking = new HashMap<>();
        statusRanking.put("applied", 0);
        statusRanking.put("received", 1);
        statusRanking.put("screening", 2);
        statusRanking.put("interview", 3);
        statusRanking.put("interview scheduled", 3);
        statusRanking.put("assessment", 4);
        statusRanking.put("final interview", 5);
        statusRanking.put("offered", 6);
        statusRanking.put("accepted", 7);
        statusRanking.put("rejected", -1);  // Special case, not more advanced
        
        String newStatusLower = newStatus.toLowerCase();
        String existingStatusLower = existingStatus.toLowerCase();
        
        Integer newRank = null;
        Integer existingRank = null;
        
        // Find the best match for each status
        for (Map.Entry<String, Integer> entry : statusRanking.entrySet()) {
            if (newStatusLower.contains(entry.getKey())) {
                if (newRank == null || entry.getValue() > newRank) {
                    newRank = entry.getValue();
                }
            }
            if (existingStatusLower.contains(entry.getKey())) {
                if (existingRank == null || entry.getValue() > existingRank) {
                    existingRank = entry.getValue();
                }
            }
        }
        
        // If we couldn't match the status to our known rankings, fall back to string comparison
        if (newRank == null || existingRank == null) {
            return true; // Default to considering the new status more advanced
        }
        
        return newRank > existingRank;
    }

    // Helper method to extract JobApplicationData from a spreadsheet row
    private JobApplicationData extractJobDataFromRow(List<Object> row) {
        JobApplicationData data = new JobApplicationData();
        
        for (int i = 0; i < Math.min(row.size(), SheetColumn.count()); i++) {
            if (row.get(i) != null) {
                String value = row.get(i).toString();
                
                switch (SheetColumn.all().get(i)) {
                    case COMPANY: data.setCompanyName(value); break;
                    case POSITION: data.setPositionTitle(value); break;
                    case DATE_APPLIED: data.setDate(value); break;
                    case STATUS: data.setApplicationStatus(value); break;
                    case PROVIDER: data.setProvider(value); break;
                    case WORK_LOCATION: data.setWorkLocation(value); break;
                    case INTERVIEW_DATE: data.setInterviewDate(value); break;
                    case NOTES: data.setExtractedInfo(value); break;
                    case WORK_TYPE: data.setWorkType(value); break;
                    case SALARY_RANGE: data.setSalaryRange(value); break;
                    case CONTACT_PERSON: data.setContactPerson(value); break;
                    case NEXT_STEPS: data.setNextSteps(value); break;
                    case EMAIL_ID: data.setEmailId(value); break;
                    case CONTACT_EMAIL: data.setContactEmail(value); break;
                    case DEADLINE: data.setApplicationDeadline(value); break;
                    case SKILLS: data.setRequiredSkills(value); break;
                    case REJECTION_REASON: data.setRejectionReason(value); break;
                    case OFFER_DETAILS: data.setOfferDetails(value); break;
                    case LAST_UPDATED: data.setLastUpdated(value); break;
                    case EMAIL_TIMESTAMP: data.setEmailTimestamp(value); break;
                    default: break; // link cells hold display text, not data
                }
            }
        }
        
        return data;
    }

    private void addHyperlinks(int rowIndex, JobApplicationData jobData) throws IOException {
        List<Request> requests = HyperlinkRequests.forRow(0, rowIndex, jobData);

        if (!requests.isEmpty()) {
            sheetsService.spreadsheets().batchUpdate(spreadsheetId,
                    new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute();
        }
    }

    private void deleteDuplicateRows(List<Integer> rowIndices, int keepRowIndex) throws IOException {
        List<Integer> toDelete = new ArrayList<>();
        for (Integer rowIndex : rowIndices) {
            if (!rowIndex.equals(keepRowIndex)) {
                toDelete.add(rowIndex);
            }
        }

        toDelete.sort((a, b) -> b.compareTo(a));

        for (Integer rowIndex : toDelete) {
            deleteRow(rowIndex);
            System.out.println("Deleted duplicate row at index: " + rowIndex);
        }
    }

    private void deleteRow(int rowIndex) throws IOException {
        List<Request> requests = new ArrayList<>();

        requests.add(new Request().setDeleteDimension(
                new com.google.api.services.sheets.v4.model.DeleteDimensionRequest()
                        .setRange(new DimensionRange()
                                .setSheetId(0)
                                .setDimension("ROWS")
                                .setStartIndex(rowIndex - 1)
                                .setEndIndex(rowIndex))));

        sheetsService.spreadsheets().batchUpdate(spreadsheetId,
                new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute();
    }

    private List<List<Object>> getExistingData() throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, "A2:" + SheetColumn.lastLetter())
                .execute();

        List<List<Object>> values = response.getValues();
        return values != null ? values : new ArrayList<>();
    }

    // Modified setupEnhancedSpreadsheetHeaders to use column metadata
    private void setupEnhancedSpreadsheetHeaders() throws IOException {
        List<Object> headerValues = SheetRowMapper.headerRow();

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, "A1:" + SheetColumn.lastLetter() + "1",
                        new ValueRange().setValues(Collections.singletonList(headerValues)))
                .setValueInputOption("RAW")
                .execute();

        List<Request> requests = new ArrayList<>();

        // Style header row with formatting
        List<CellData> headerCells = new ArrayList<>();
        for (int i = 0; i < SheetColumn.count(); i++) {
            CellData cellData = new CellData()
                    .setUserEnteredFormat(new CellFormat()
                            .setHorizontalAlignment("CENTER")
                            .setWrapStrategy("WRAP")
                            .setTextFormat(new TextFormat().setBold(true))
                            .setBackgroundColor(new Color().setRed(0.9f).setGreen(0.9f).setBlue(0.9f)));
            headerCells.add(cellData);
        }

        requests.add(new Request().setUpdateCells(new UpdateCellsRequest()
                .setRange(new GridRange()
                        .setSheetId(0)
                        .setStartRowIndex(0).setEndRowIndex(1)
                        .setStartColumnIndex(0).setEndColumnIndex(SheetColumn.count()))
                .setRows(Collections.singletonList(new RowData().setValues(headerCells)))
                .setFields("userEnteredFormat")));

        // Set column widths and visibility based on metadata
        for (SheetColumn column : SheetColumn.all()) {
            requests.add(new Request().setUpdateDimensionProperties(
                    new UpdateDimensionPropertiesRequest()
                            .setRange(new DimensionRange().setSheetId(0).setDimension("COLUMNS")
                                    .setStartIndex(column.index()).setEndIndex(column.index() + 1))
                            .setProperties(new DimensionProperties()
                                    .setPixelSize(column.pixelWidth())
                                    .setHiddenByUser(!column.isVisible()))
                            .setFields("pixelSize,hiddenByUser")));
        }

        // End (exclusive) of the columns the user sees
        int lastVisibleColumnIndex = SheetColumn.visibleColumnEnd();

        // Add conditional formatting for rejected applications
        requests.add(new Request().setAddConditionalFormatRule(
                new com.google.api.services.sheets.v4.model.AddConditionalFormatRuleRequest()
                        .setRule(new com.google.api.services.sheets.v4.model.ConditionalFormatRule()
                                .setRanges(Collections.singletonList(new GridRange()
                                        .setSheetId(0)
                                        .setStartRowIndex(1) // Start from row 2 (after header)
                                        .setStartColumnIndex(0) // Column A
                                        .setEndColumnIndex(lastVisibleColumnIndex))) // Through last visible column
                                .setBooleanRule(new com.google.api.services.sheets.v4.model.BooleanRule()
                                        .setCondition(new com.google.api.services.sheets.v4.model.BooleanCondition()
                                                .setType("CUSTOM_FORMULA")
                                                .setValues(Collections.singletonList(
                                                        new com.google.api.services.sheets.v4.model.ConditionValue()
                                                                .setUserEnteredValue("=UPPER($D2)=\"REJECTED\""))))
                                        .setFormat(new CellFormat()
                                                .setBackgroundColor(new Color()
                                                        .setRed(1.0f)
                                                        .setGreen(0.8f)
                                                        .setBlue(0.8f))
                                                .setTextFormat(new TextFormat()
                                                        .setForegroundColor(new Color()
                                                                .setRed(0.8f)
                                                                .setGreen(0.0f)
                                                                .setBlue(0.0f))))))
                        .setIndex(0)));

        // Add conditional formatting for offered applications (green)
        requests.add(new Request().setAddConditionalFormatRule(
                new com.google.api.services.sheets.v4.model.AddConditionalFormatRuleRequest()
                        .setRule(new com.google.api.services.sheets.v4.model.ConditionalFormatRule()
                                .setRanges(Collections.singletonList(new GridRange()
                                        .setSheetId(0)
                                        .setStartRowIndex(1)
                                        .setStartColumnIndex(0)
                                        .setEndColumnIndex(lastVisibleColumnIndex)))
                                .setBooleanRule(new com.google.api.services.sheets.v4.model.BooleanRule()
                                        .setCondition(new com.google.api.services.sheets.v4.model.BooleanCondition()
                                                .setType("CUSTOM_FORMULA")
                                                .setValues(Collections.singletonList(
                                                        new com.google.api.services.sheets.v4.model.ConditionValue()
                                                                .setUserEnteredValue("=OR(UPPER($D2)=\"OFFERED\",UPPER($D2)=\"ACCEPTED\")"))))
                                        .setFormat(new CellFormat()
                                                .setBackgroundColor(new Color()
                                                        .setRed(0.8f)
                                                        .setGreen(1.0f)
                                                        .setBlue(0.8f))
                                                .setTextFormat(new TextFormat()
                                                        .setForegroundColor(new Color()
                                                                .setRed(0.0f)
                                                                .setGreen(0.6f)
                                                                .setBlue(0.0f))))))
                        .setIndex(1)));

        // Add conditional formatting for interview scheduled (yellow)
        requests.add(new Request().setAddConditionalFormatRule(
                new com.google.api.services.sheets.v4.model.AddConditionalFormatRuleRequest()
                        .setRule(new com.google.api.services.sheets.v4.model.ConditionalFormatRule()
                                .setRanges(Collections.singletonList(new GridRange()
                                        .setSheetId(0)
                                        .setStartRowIndex(1)
                                        .setStartColumnIndex(0)
                                        .setEndColumnIndex(lastVisibleColumnIndex)))
                                .setBooleanRule(new com.google.api.services.sheets.v4.model.BooleanRule()
                                        .setCondition(new com.google.api.services.sheets.v4.model.BooleanCondition()
                                                .setType("CUSTOM_FORMULA")
                                                .setValues(Collections.singletonList(
                                                        new com.google.api.services.sheets.v4.model.ConditionValue()
                                                                .setUserEnteredValue("=OR(UPPER($D2)=\"INTERVIEW\",UPPER($D2)=\"INTERVIEW SCHEDULED\")"))))
                                        .setFormat(new CellFormat()
                                                .setBackgroundColor(new Color()
                                                        .setRed(1.0f)
                                                        .setGreen(1.0f)
                                                        .setBlue(0.8f))
                                                .setTextFormat(new TextFormat()
                                                        .setForegroundColor(new Color()
                                                                .setRed(0.8f)
                                                                .setGreen(0.6f)
                                                                .setBlue(0.0f))))))
                        .setIndex(2)));

        // Freeze the first row
        requests.add(new Request().setUpdateSheetProperties(
                new UpdateSheetPropertiesRequest().setProperties(
                        new SheetProperties().setSheetId(0).setGridProperties(
                                new GridProperties().setFrozenRowCount(1)))
                        .setFields("gridProperties.frozenRowCount")));

        // Create a basic filter for visible columns only
        requests.add(new Request().setSetBasicFilter(new SetBasicFilterRequest()
                .setFilter(new BasicFilter().setRange(
                        new GridRange().setSheetId(0)
                                .setStartRowIndex(0).setStartColumnIndex(0)
                                .setEndColumnIndex(lastVisibleColumnIndex)))));

        sheetsService.spreadsheets().batchUpdate(spreadsheetId,
                new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute();
    }
}
