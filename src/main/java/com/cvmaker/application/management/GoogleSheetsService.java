package com.cvmaker.application.management;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
import com.google.api.services.sheets.v4.model.ExtendedValue;
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

    // Define column metadata to make management easier
    private static final List<ColumnMetadata> COLUMNS = Arrays.asList(
            // VISIBLE COLUMNS (A-J)
            new ColumnMetadata("Company", 200, true, "companyName", ColumnType.TEXT),
            new ColumnMetadata("Position", 280, true, "positionTitle", ColumnType.TEXT),
            new ColumnMetadata("Date Applied", 120, true, "date", ColumnType.DATE),
            new ColumnMetadata("Status", 120, true, "applicationStatus", ColumnType.STATUS),
            new ColumnMetadata("Provider", 100, true, "provider", ColumnType.TEXT),
            new ColumnMetadata("Email Link", 80, true, "emailLink", ColumnType.HYPERLINK),
            new ColumnMetadata("Application URL", 80, true, "applicationUrl", ColumnType.HYPERLINK),
            new ColumnMetadata("Notes", 350, true, "extractedInfo", ColumnType.NOTES),
            new ColumnMetadata("Contact Email", 150, true, "contactEmail", ColumnType.TEXT),

            // HIDDEN COLUMNS (K-U) - for data storage
            new ColumnMetadata("Work Location", 130, false, "workLocation", ColumnType.TEXT),
            new ColumnMetadata("Interview Date", 120, false, "interviewDate", ColumnType.DATE),
            new ColumnMetadata("Work Type", 100, false, "workType", ColumnType.TEXT),
            new ColumnMetadata("Salary Range", 150, false, "salaryRange", ColumnType.TEXT),
            new ColumnMetadata("Contact Person", 150, false, "contactPerson", ColumnType.TEXT),
            new ColumnMetadata("Next Steps", 200, false, "nextSteps", ColumnType.TEXT),
            new ColumnMetadata("Email ID", 150, false, "emailId", ColumnType.TEXT),
            new ColumnMetadata("Deadline", 120, false, "applicationDeadline", ColumnType.TEXT),
            new ColumnMetadata("Skills", 200, false, "requiredSkills", ColumnType.TEXT),
            new ColumnMetadata("Rejection Reason", 200, false, "rejectionReason", ColumnType.TEXT),
            new ColumnMetadata("Offer Details", 200, false, "offerDetails", ColumnType.TEXT),
            new ColumnMetadata("Last Updated", 150, false, "lastUpdated", ColumnType.TIMESTAMP)
    );

    // Enum to define column types for specific formatting/handling
    private enum ColumnType {
        TEXT, DATE, STATUS, HYPERLINK, NOTES, TIMESTAMP
    }

    // Class to encapsulate column metadata
    private static class ColumnMetadata {

        private final String displayName;
        private final int pixelWidth;
        private final boolean visible;
        private final String fieldName;
        private final ColumnType type;

        public ColumnMetadata(String displayName, int pixelWidth, boolean visible, String fieldName, ColumnType type) {
            this.displayName = displayName;
            this.pixelWidth = pixelWidth;
            this.visible = visible;
            this.fieldName = fieldName;
            this.type = type;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getPixelWidth() {
            return pixelWidth;
        }

        public boolean isVisible() {
            return visible;
        }

        public String getFieldName() {
            return fieldName;
        }

        public ColumnType getType() {
            return type;
        }
    }

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

    // Modified bulkUpdateAllApplications to use column metadata
    public void bulkUpdateAllApplications(Map<String, JobApplicationData> consolidatedApplications) throws IOException {
        System.out.println("Starting bulk update of " + consolidatedApplications.size() + " applications...");

        // Clear existing data (keeping headers)
        clearExistingData();

        // Prepare all data rows
        List<List<Object>> allRows = new ArrayList<>();
        List<JobApplicationData> applicationsWithUrls = new ArrayList<>();

        for (JobApplicationData jobData : consolidatedApplications.values()) {
            if (jobData.isJobRelated()) {
                List<Object> rowData = createRowData(jobData);
                allRows.add(rowData);
                applicationsWithUrls.add(jobData);
            }
        }

        if (allRows.isEmpty()) {
            System.out.println("No job-related applications to sync.");
            return;
        }

        // Bulk insert all data at once
        ValueRange body = new ValueRange().setValues(allRows);
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, "A2:" + getColumnLetter(COLUMNS.size()) + (allRows.size() + 1), body)
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
                .get(spreadsheetId, "A2:" + getColumnLetter(COLUMNS.size()))
                .execute();

        List<List<Object>> values = response.getValues();
        if (values != null && !values.isEmpty()) {
            int rowsToDelete = values.size();

            // Clear the range (this preserves formatting but removes data)
            com.google.api.services.sheets.v4.model.ClearValuesRequest clearRequest = new com.google.api.services.sheets.v4.model.ClearValuesRequest();
            sheetsService.spreadsheets().values()
                    .clear(spreadsheetId, "A2:" + getColumnLetter(COLUMNS.size()) + (rowsToDelete + 1), clearRequest)
                    .execute();

            System.out.println("Cleared " + rowsToDelete + " existing rows");
        }
    }

    private void addAllHyperlinksInBatch(List<JobApplicationData> applications) throws IOException {
        List<Request> requests = new ArrayList<>();

        // Find indices for hyperlink columns
        int emailLinkIndex = -1;
        int applicationUrlIndex = -1;

        for (int i = 0; i < COLUMNS.size(); i++) {
            ColumnMetadata column = COLUMNS.get(i);
            if (column.getType() == ColumnType.HYPERLINK) {
                if (column.getFieldName().equals("emailLink")) {
                    emailLinkIndex = i;
                } else if (column.getFieldName().equals("applicationUrl")) {
                    applicationUrlIndex = i;
                }
            }
        }

        for (int i = 0; i < applications.size(); i++) {
            JobApplicationData jobData = applications.get(i);
            int rowIndex = i + 2; // +2 because data starts at row 2 (1-indexed)

            // Add hyperlink for email link
            if (emailLinkIndex >= 0 && jobData.getEmailLink() != null && !jobData.getEmailLink().isEmpty()) {
                requests.add(new Request().setUpdateCells(new UpdateCellsRequest()
                        .setRange(new GridRange()
                                .setSheetId(0)
                                .setStartRowIndex(rowIndex - 1).setEndRowIndex(rowIndex)
                                .setStartColumnIndex(emailLinkIndex).setEndColumnIndex(emailLinkIndex + 1))
                        .setRows(Collections.singletonList(new RowData()
                                .setValues(Collections.singletonList(new CellData()
                                        .setUserEnteredValue(new ExtendedValue()
                                                .setFormulaValue("=HYPERLINK(\"" + jobData.getEmailLink() + "\", \"📧 View\")"))))))
                        .setFields("userEnteredValue")));
            }

            // Add hyperlink for application URL
            if (applicationUrlIndex >= 0 && jobData.getApplicationUrl() != null && !jobData.getApplicationUrl().isEmpty()) {
                requests.add(new Request().setUpdateCells(new UpdateCellsRequest()
                        .setRange(new GridRange()
                                .setSheetId(0)
                                .setStartRowIndex(rowIndex - 1).setEndRowIndex(rowIndex)
                                .setStartColumnIndex(applicationUrlIndex).setEndColumnIndex(applicationUrlIndex + 1))
                        .setRows(Collections.singletonList(new RowData()
                                .setValues(Collections.singletonList(new CellData()
                                        .setUserEnteredValue(new ExtendedValue()
                                                .setFormulaValue("=HYPERLINK(\"" + jobData.getApplicationUrl() + "\", \"🔗 Apply\")"))))))
                        .setFields("userEnteredValue")));
            }
        }

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

    // Update updateJobApplicationData to use column metadata
    public void updateJobApplicationData(JobApplicationData jobData) throws IOException {
        List<List<Object>> existingData = getExistingData();

        String companyName = jobData.getCompanyName() != null ? jobData.getCompanyName().trim() : "";

        // Find existing entries for this company
        List<Integer> companyRowIndices = new ArrayList<>();
        int latestRowIndex = -1;
        LocalDateTime latestDate = null;

        for (int i = 0; i < existingData.size(); i++) {
            List<Object> row = existingData.get(i);
            if (row.size() >= 1 && row.get(0) != null) {
                String existingCompany = row.get(0).toString().trim();

                if (existingCompany.equalsIgnoreCase(companyName)) {
                    int actualRowIndex = i + 2;
                    companyRowIndices.add(actualRowIndex);

                    try {
                        // Find index of lastUpdated column
                        int lastUpdatedIndex = -1;
                        for (int j = 0; j < COLUMNS.size(); j++) {
                            if (COLUMNS.get(j).getFieldName().equals("lastUpdated")) {
                                lastUpdatedIndex = j;
                                break;
                            }
                        }

                        if (lastUpdatedIndex >= 0 && row.size() > lastUpdatedIndex && row.get(lastUpdatedIndex) != null) {
                            LocalDateTime entryDate = LocalDateTime.parse(row.get(lastUpdatedIndex).toString());
                            if (latestDate == null || entryDate.isAfter(latestDate)) {
                                latestDate = entryDate;
                                latestRowIndex = actualRowIndex;
                            }
                        }
                    } catch (DateTimeParseException e) {
                        if (latestRowIndex == -1) {
                            latestRowIndex = actualRowIndex;
                        }
                    }
                }
            }
        }

        // Create row data using the metadata
        List<Object> rowData = createRowData(jobData);

        if (companyRowIndices.isEmpty()) {
            // Add new row
            ValueRange body = new ValueRange().setValues(Collections.singletonList(rowData));
            sheetsService.spreadsheets().values()
                    .append(spreadsheetId, "A2:" + getColumnLetter(COLUMNS.size()) + "2", body)
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
            String range = "A" + latestRowIndex + ":" + getColumnLetter(COLUMNS.size()) + latestRowIndex;
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

    private void addHyperlinks(int rowIndex, JobApplicationData jobData) throws IOException {
        List<Request> requests = new ArrayList<>();

        // Find indices for hyperlink columns
        int emailLinkIndex = -1;
        int applicationUrlIndex = -1;

        for (int i = 0; i < COLUMNS.size(); i++) {
            ColumnMetadata column = COLUMNS.get(i);
            if (column.getType() == ColumnType.HYPERLINK) {
                if (column.getFieldName().equals("emailLink")) {
                    emailLinkIndex = i;
                } else if (column.getFieldName().equals("applicationUrl")) {
                    applicationUrlIndex = i;
                }
            }
        }

        // Add hyperlink for email link
        if (emailLinkIndex >= 0 && jobData.getEmailLink() != null && !jobData.getEmailLink().isEmpty()) {
            requests.add(new Request().setUpdateCells(new UpdateCellsRequest()
                    .setRange(new GridRange()
                            .setSheetId(0)
                            .setStartRowIndex(rowIndex - 1).setEndRowIndex(rowIndex)
                            .setStartColumnIndex(emailLinkIndex).setEndColumnIndex(emailLinkIndex + 1))
                    .setRows(Collections.singletonList(new RowData()
                            .setValues(Collections.singletonList(new CellData()
                                    .setUserEnteredValue(new ExtendedValue()
                                            .setFormulaValue("=HYPERLINK(\"" + jobData.getEmailLink() + "\", \"📧 View\")"))))))
                    .setFields("userEnteredValue")));
        }

        // Add hyperlink for application URL
        if (applicationUrlIndex >= 0 && jobData.getApplicationUrl() != null && !jobData.getApplicationUrl().isEmpty()) {
            requests.add(new Request().setUpdateCells(new UpdateCellsRequest()
                    .setRange(new GridRange()
                            .setSheetId(0)
                            .setStartRowIndex(rowIndex - 1).setEndRowIndex(rowIndex)
                            .setStartColumnIndex(applicationUrlIndex).setEndColumnIndex(applicationUrlIndex + 1))
                    .setRows(Collections.singletonList(new RowData()
                            .setValues(Collections.singletonList(new CellData()
                                    .setUserEnteredValue(new ExtendedValue()
                                            .setFormulaValue("=HYPERLINK(\"" + jobData.getApplicationUrl() + "\", \"🔗 Apply\")"))))))
                    .setFields("userEnteredValue")));
        }

        if (!requests.isEmpty()) {
            sheetsService.spreadsheets().batchUpdate(spreadsheetId,
                    new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute();
        }
    }

    private String combineNotes(JobApplicationData jobData) {
        StringBuilder notes = new StringBuilder();

        // If status is Rejected, prominently show the rejection reason at the beginning
        if (jobData.getApplicationStatus() != null
                && jobData.getApplicationStatus().trim().equalsIgnoreCase("Rejected")
                && jobData.getRejectionReason() != null
                && !jobData.getRejectionReason().trim().isEmpty()) {

            notes.append("❌ REJECTED: ").append(jobData.getRejectionReason().trim());
        }

        // Add the rest of the extracted info
        if (jobData.getExtractedInfo() != null && !jobData.getExtractedInfo().trim().isEmpty()) {
            if (notes.length() > 0) {
                notes.append(" | ");
            }
            notes.append(jobData.getExtractedInfo().trim());
        }

        // Include important hidden information in notes for visibility
        if (jobData.getSalaryRange() != null && !jobData.getSalaryRange().trim().isEmpty()) {
            if (notes.length() > 0) {
                notes.append(" | ");
            }
            notes.append("💰 ").append(jobData.getSalaryRange().trim());
        }

        if (jobData.getContactPerson() != null && !jobData.getContactPerson().trim().isEmpty()) {
            if (notes.length() > 0) {
                notes.append(" | ");
            }
            notes.append("👤 ").append(jobData.getContactPerson().trim());
        }

        if (jobData.getNextSteps() != null && !jobData.getNextSteps().trim().isEmpty()) {
            if (notes.length() > 0) {
                notes.append(" | ");
            }
            notes.append("➡️ ").append(jobData.getNextSteps().trim());
        }

        // Only add rejection reason here if it wasn't already added at the beginning
        if (!(jobData.getApplicationStatus() != null
                && jobData.getApplicationStatus().trim().equalsIgnoreCase("Rejected"))
                && jobData.getRejectionReason() != null
                && !jobData.getRejectionReason().trim().isEmpty()) {

            if (notes.length() > 0) {
                notes.append(" | ");
            }
            notes.append("❌ ").append(jobData.getRejectionReason().trim());
        }

        if (jobData.getOfferDetails() != null && !jobData.getOfferDetails().trim().isEmpty()) {
            if (notes.length() > 0) {
                notes.append(" | ");
            }
            notes.append("🎉 ").append(jobData.getOfferDetails().trim());
        }

        if (jobData.getRequiredSkills() != null && !jobData.getRequiredSkills().trim().isEmpty()) {
            if (notes.length() > 0) {
                notes.append(" | ");
            }
            notes.append("🔧 ").append(jobData.getRequiredSkills().trim());
        }

        return notes.toString();
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
                .get(spreadsheetId, "A2:" + getColumnLetter(COLUMNS.size()))
                .execute();

        List<List<Object>> values = response.getValues();
        return values != null ? values : new ArrayList<>();
    }

    private String formatDateToHumanReadable(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return "";
        }

        try {
            // Handle email date format
            if (dateString.contains("(UTC)") || dateString.contains("+0000") || dateString.contains("-")) {
                java.time.format.DateTimeFormatter emailFormatter = java.time.format.DateTimeFormatter.ofPattern(
                        "EEE, d MMM yyyy HH:mm:ss Z '(UTC)'", java.util.Locale.ENGLISH);
                try {
                    java.time.ZonedDateTime zonedDateTime = java.time.ZonedDateTime.parse(dateString, emailFormatter);
                    return zonedDateTime.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                } catch (DateTimeParseException e) {
                    java.time.format.DateTimeFormatter emailFormatter2 = java.time.format.DateTimeFormatter.ofPattern(
                            "EEE, d MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH);
                    try {
                        java.time.ZonedDateTime zonedDateTime = java.time.ZonedDateTime.parse(dateString, emailFormatter2);
                        return zonedDateTime.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    } catch (DateTimeParseException e2) {
                        // Continue to other parsing methods
                    }
                }
            }

            // Try parsing ISO date format
            LocalDate date = LocalDate.parse(dateString);
            return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (DateTimeParseException e) {
            try {
                LocalDateTime dateTime = LocalDateTime.parse(dateString);
                return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } catch (DateTimeParseException e2) {
                try {
                    DateTimeFormatter[] formatters = {
                        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                        DateTimeFormatter.ofPattern("MM-dd-yyyy"),
                        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                        DateTimeFormatter.ofPattern("MMM d, yyyy"),
                        DateTimeFormatter.ofPattern("MMMM d, yyyy"),
                        DateTimeFormatter.ofPattern("d MMM yyyy"),
                        DateTimeFormatter.ofPattern("d MMMM yyyy")
                    };

                    for (DateTimeFormatter formatter : formatters) {
                        try {
                            LocalDate parsedDate = LocalDate.parse(dateString, formatter);
                            return parsedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                        } catch (DateTimeParseException ignored) {
                            // Try next formatter
                        }
                    }
                } catch (Exception ignored) {
                    // Fall through to manual parsing
                }

                // Manual extraction for complex email date formats
                try {
                    String[] parts = dateString.split(" ");
                    if (parts.length >= 4) {
                        String day = parts[1].replace(",", "");
                        String month = parts[2];
                        String year = parts[3];

                        String simpleDateString = day + " " + month + " " + year;
                        DateTimeFormatter simpleFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH);
                        LocalDate parsedDate = LocalDate.parse(simpleDateString, simpleFormatter);
                        return parsedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    }
                } catch (Exception ignored) {
                    // Fall through
                }

                return dateString;
            }
        }
    }

    // Modified setupEnhancedSpreadsheetHeaders to use column metadata
    private void setupEnhancedSpreadsheetHeaders() throws IOException {
        // Generate header values from column metadata
        List<Object> headerValues = COLUMNS.stream()
                .map(ColumnMetadata::getDisplayName)
                .collect(java.util.stream.Collectors.toList());

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, "A1:" + getColumnLetter(COLUMNS.size()) + "1",
                        new ValueRange().setValues(Collections.singletonList(headerValues)))
                .setValueInputOption("RAW")
                .execute();

        List<Request> requests = new ArrayList<>();

        // Style header row with formatting
        List<CellData> headerCells = new ArrayList<>();
        for (int i = 0; i < COLUMNS.size(); i++) {
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
                        .setStartColumnIndex(0).setEndColumnIndex(COLUMNS.size()))
                .setRows(Collections.singletonList(new RowData().setValues(headerCells)))
                .setFields("userEnteredFormat")));

        // Set column widths and visibility based on metadata
        for (int i = 0; i < COLUMNS.size(); i++) {
            ColumnMetadata column = COLUMNS.get(i);
            requests.add(new Request().setUpdateDimensionProperties(
                    new UpdateDimensionPropertiesRequest()
                            .setRange(new DimensionRange().setSheetId(0).setDimension("COLUMNS")
                                    .setStartIndex(i).setEndIndex(i + 1))
                            .setProperties(new DimensionProperties()
                                    .setPixelSize(column.getPixelWidth())
                                    .setHiddenByUser(!column.isVisible()))
                            .setFields("pixelSize,hiddenByUser")));
        }

        // Find index of the last visible column
        int lastVisibleColumnIndex = 0;
        for (int i = 0; i < COLUMNS.size(); i++) {
            if (COLUMNS.get(i).isVisible()) {
                lastVisibleColumnIndex = i + 1;
            }
        }

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

    // Helper method to create row data using column metadata
    private List<Object> createRowData(JobApplicationData jobData) {
        List<Object> rowData = new ArrayList<>();

        for (ColumnMetadata column : COLUMNS) {
            Object value = "";

            switch (column.getFieldName()) {
                case "companyName":
                    value = jobData.getCompanyName() != null ? jobData.getCompanyName() : "";
                    break;
                case "positionTitle":
                    value = jobData.getPositionTitle() != null ? jobData.getPositionTitle() : "";
                    break;
                case "date":
                    value = formatDateToHumanReadable(jobData.getDate());
                    break;
                case "applicationStatus":
                    value = jobData.getApplicationStatus() != null ? jobData.getApplicationStatus() : "Applied";
                    break;
                case "provider":
                    value = jobData.getProvider() != null ? jobData.getProvider() : "";
                    break;
                case "workLocation":
                    value = jobData.getWorkLocation() != null ? jobData.getWorkLocation() : "";
                    break;
                case "interviewDate":
                    value = formatDateToHumanReadable(jobData.getInterviewDate());
                    break;
                case "emailLink":
                    value = "View Email";  // Will be converted to hyperlink
                    break;
                case "applicationUrl":
                    value = jobData.getApplicationUrl() != null ? "Apply" : "";  // Will be converted to hyperlink
                    break;
                case "extractedInfo":
                    value = combineNotes(jobData);
                    break;
                case "workType":
                    value = jobData.getWorkType() != null ? jobData.getWorkType() : "";
                    break;
                case "salaryRange":
                    value = jobData.getSalaryRange() != null ? jobData.getSalaryRange() : "";
                    break;
                case "contactPerson":
                    value = jobData.getContactPerson() != null ? jobData.getContactPerson() : "";
                    break;
                case "nextSteps":
                    value = jobData.getNextSteps() != null ? jobData.getNextSteps() : "";
                    break;
                case "emailId":
                    value = jobData.getEmailId();
                    break;
                case "contactEmail":
                    value = jobData.getContactEmail() != null ? jobData.getContactEmail() : "";
                    break;
                case "applicationDeadline":
                    value = jobData.getApplicationDeadline() != null ? jobData.getApplicationDeadline() : "";
                    break;
                case "requiredSkills":
                    value = jobData.getRequiredSkills() != null ? jobData.getRequiredSkills() : "";
                    break;
                case "rejectionReason":
                    value = jobData.getRejectionReason() != null ? jobData.getRejectionReason() : "";
                    break;
                case "offerDetails":
                    value = jobData.getOfferDetails() != null ? jobData.getOfferDetails() : "";
                    break;
                case "lastUpdated":
                    value = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    break;
            }

            rowData.add(value);
        }

        return rowData;
    }

    // Helper method to get column letter from index (0-based)
    private String getColumnLetter(int index) {
        StringBuilder columnName = new StringBuilder();

        if (index <= 0) {
            return "A";
        }

        while (index > 0) {
            int remainder = (index - 1) % 26;
            columnName.insert(0, (char) (remainder + 'A'));
            index = (index - 1) / 26;
        }
        return columnName.toString();
    }
}
