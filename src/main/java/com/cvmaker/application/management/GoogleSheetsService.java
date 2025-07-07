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

    // NEW METHOD: Bulk update all applications in one operation
    public void bulkUpdateAllApplications(Map<String, JobApplicationData> consolidatedApplications) throws IOException {
        System.out.println("Starting bulk update of " + consolidatedApplications.size() + " applications...");

        // Clear existing data (keeping headers)
        clearExistingData();

        // Prepare all data rows
        List<List<Object>> allRows = new ArrayList<>();
        List<JobApplicationData> applicationsWithUrls = new ArrayList<>();

        for (JobApplicationData jobData : consolidatedApplications.values()) {
            if (jobData.isJobRelated()) {
                String formattedDate = formatDateToHumanReadable(jobData.getDate());
                String formattedInterviewDate = formatDateToHumanReadable(jobData.getInterviewDate());

                List<Object> rowData = Arrays.asList(
                        // VISIBLE COLUMNS (A-J)
                        jobData.getCompanyName() != null ? jobData.getCompanyName() : "", // A: Company
                        jobData.getPositionTitle() != null ? jobData.getPositionTitle() : "", // B: Position
                        formattedDate, // C: Date Applied
                        jobData.getApplicationStatus() != null ? jobData.getApplicationStatus() : "Applied", // D: Status
                        jobData.getProvider() != null ? jobData.getProvider() : "", // E: Provider
                        jobData.getWorkLocation() != null ? jobData.getWorkLocation() : "", // F: Work Location
                        formattedInterviewDate, // G: Interview Date
                        "View Email", // H: Email Link (will be converted to hyperlink)
                        jobData.getApplicationUrl() != null ? "Apply" : "", // I: Application URL (will be converted to hyperlink)
                        combineNotes(jobData), // J: Notes

                        // HIDDEN COLUMNS (K-U) - for data storage
                        jobData.getWorkType() != null ? jobData.getWorkType() : "", // K: Work Type
                        jobData.getSalaryRange() != null ? jobData.getSalaryRange() : "", // L: Salary Range
                        jobData.getContactPerson() != null ? jobData.getContactPerson() : "", // M: Contact Person
                        jobData.getNextSteps() != null ? jobData.getNextSteps() : "", // N: Next Steps
                        jobData.getEmailId(), // O: Email ID
                        jobData.getContactEmail() != null ? jobData.getContactEmail() : "", // P: Contact Email
                        jobData.getApplicationDeadline() != null ? jobData.getApplicationDeadline() : "", // Q: Deadline
                        jobData.getRequiredSkills() != null ? jobData.getRequiredSkills() : "", // R: Skills
                        jobData.getRejectionReason() != null ? jobData.getRejectionReason() : "", // S: Rejection Reason
                        jobData.getOfferDetails() != null ? jobData.getOfferDetails() : "", // T: Offer Details
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) // U: Last Updated
                );

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
                .update(spreadsheetId, "A2:U" + (allRows.size() + 1), body)
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
                .get(spreadsheetId, "A2:U")
                .execute();

        List<List<Object>> values = response.getValues();
        if (values != null && !values.isEmpty()) {
            int rowsToDelete = values.size();

            // Clear the range (this preserves formatting but removes data)
            com.google.api.services.sheets.v4.model.ClearValuesRequest clearRequest = new com.google.api.services.sheets.v4.model.ClearValuesRequest();
            sheetsService.spreadsheets().values()
                    .clear(spreadsheetId, "A2:U" + (rowsToDelete + 1), clearRequest)
                    .execute();

            System.out.println("Cleared " + rowsToDelete + " existing rows");
        }
    }

    private void addAllHyperlinksInBatch(List<JobApplicationData> applications) throws IOException {
        List<Request> requests = new ArrayList<>();

        for (int i = 0; i < applications.size(); i++) {
            JobApplicationData jobData = applications.get(i);
            int rowIndex = i + 2; // +2 because data starts at row 2 (1-indexed)

            // Add hyperlink for email link (column H = index 7)
            if (jobData.getEmailLink() != null && !jobData.getEmailLink().isEmpty()) {
                requests.add(new Request().setUpdateCells(new UpdateCellsRequest()
                        .setRange(new GridRange()
                                .setSheetId(0)
                                .setStartRowIndex(rowIndex - 1).setEndRowIndex(rowIndex)
                                .setStartColumnIndex(7).setEndColumnIndex(8))
                        .setRows(Collections.singletonList(new RowData()
                                .setValues(Collections.singletonList(new CellData()
                                        .setUserEnteredValue(new ExtendedValue()
                                                .setFormulaValue("=HYPERLINK(\"" + jobData.getEmailLink() + "\", \"📧 View\")"))))))
                        .setFields("userEnteredValue")));
            }

            // Add hyperlink for application URL (column I = index 8)
            if (jobData.getApplicationUrl() != null && !jobData.getApplicationUrl().isEmpty()) {
                requests.add(new Request().setUpdateCells(new UpdateCellsRequest()
                        .setRange(new GridRange()
                                .setSheetId(0)
                                .setStartRowIndex(rowIndex - 1).setEndRowIndex(rowIndex)
                                .setStartColumnIndex(8).setEndColumnIndex(9))
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

    // Keep the original single update method for individual updates
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
                        if (row.size() >= 21 && row.get(20) != null) { // Last Updated column
                            LocalDateTime entryDate = LocalDateTime.parse(row.get(20).toString());
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

        // Format dates
        String formattedDate = formatDateToHumanReadable(jobData.getDate());
        String formattedInterviewDate = formatDateToHumanReadable(jobData.getInterviewDate());

        // Create row data in the exact column order (A-U)
        List<Object> rowData = Arrays.asList(
                // VISIBLE COLUMNS (A-J)
                jobData.getCompanyName() != null ? jobData.getCompanyName() : "", // A: Company
                jobData.getPositionTitle() != null ? jobData.getPositionTitle() : "", // B: Position
                formattedDate, // C: Date Applied
                jobData.getApplicationStatus() != null ? jobData.getApplicationStatus() : "Applied", // D: Status
                jobData.getProvider() != null ? jobData.getProvider() : "", // E: Provider
                jobData.getWorkLocation() != null ? jobData.getWorkLocation() : "", // F: Work Location
                formattedInterviewDate, // G: Interview Date
                "View Email", // Will be converted to hyperlink                                      // H: Email Link
                jobData.getApplicationUrl() != null ? "Apply" : "", // Will be converted to hyperlink // I: Application URL
                combineNotes(jobData), // J: Notes

                // HIDDEN COLUMNS (K-U) - for data storage
                jobData.getWorkType() != null ? jobData.getWorkType() : "", // K: Work Type
                jobData.getSalaryRange() != null ? jobData.getSalaryRange() : "", // L: Salary Range
                jobData.getContactPerson() != null ? jobData.getContactPerson() : "", // M: Contact Person
                jobData.getNextSteps() != null ? jobData.getNextSteps() : "", // N: Next Steps
                jobData.getEmailId(), // O: Email ID
                jobData.getContactEmail() != null ? jobData.getContactEmail() : "", // P: Contact Email
                jobData.getApplicationDeadline() != null ? jobData.getApplicationDeadline() : "", // Q: Deadline
                jobData.getRequiredSkills() != null ? jobData.getRequiredSkills() : "", // R: Skills
                jobData.getRejectionReason() != null ? jobData.getRejectionReason() : "", // S: Rejection Reason
                jobData.getOfferDetails() != null ? jobData.getOfferDetails() : "", // T: Offer Details
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) // U: Last Updated
        );

        if (companyRowIndices.isEmpty()) {
            // Add new row
            ValueRange body = new ValueRange().setValues(Collections.singletonList(rowData));
            sheetsService.spreadsheets().values()
                    .append(spreadsheetId, "A2:U", body)
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
            String range = "A" + latestRowIndex + ":U" + latestRowIndex;
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

        // Add hyperlink for email link (column H = index 7)
        if (jobData.getEmailLink() != null && !jobData.getEmailLink().isEmpty()) {
            requests.add(new Request().setUpdateCells(new UpdateCellsRequest()
                    .setRange(new GridRange()
                            .setSheetId(0)
                            .setStartRowIndex(rowIndex - 1).setEndRowIndex(rowIndex)
                            .setStartColumnIndex(7).setEndColumnIndex(8))
                    .setRows(Collections.singletonList(new RowData()
                            .setValues(Collections.singletonList(new CellData()
                                    .setUserEnteredValue(new ExtendedValue()
                                            .setFormulaValue("=HYPERLINK(\"" + jobData.getEmailLink() + "\", \"📧 View\")"))))))
                    .setFields("userEnteredValue")));
        }

        // Add hyperlink for application URL (column I = index 8)
        if (jobData.getApplicationUrl() != null && !jobData.getApplicationUrl().isEmpty()) {
            requests.add(new Request().setUpdateCells(new UpdateCellsRequest()
                    .setRange(new GridRange()
                            .setSheetId(0)
                            .setStartRowIndex(rowIndex - 1).setEndRowIndex(rowIndex)
                            .setStartColumnIndex(8).setEndColumnIndex(9))
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
                .get(spreadsheetId, "A2:U")
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

    private void setupEnhancedSpreadsheetHeaders() throws IOException {
        // Headers with visible and hidden columns organized
        List<List<Object>> headerValues = Collections.singletonList(Arrays.asList(
                // VISIBLE COLUMNS (A-J)
                "Company", "Position", "Date Applied", "Status", "Provider",
                "Work Location", "Interview Date", "Email Link", "Application URL", "Notes",
                // HIDDEN COLUMNS (K-U) - for data storage
                "Work Type", "Salary Range", "Contact Person", "Next Steps", "Email ID",
                "Contact Email", "Deadline", "Skills", "Rejection Reason", "Offer Details", "Last Updated"
        ));

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, "A1:U1",
                        new ValueRange().setValues(headerValues))
                .setValueInputOption("RAW")
                .execute();

        List<Request> requests = new ArrayList<>();

        // Style header row with formatting
        List<CellData> headerCells = new ArrayList<>();
        for (int i = 0; i < 21; i++) { // 21 total columns
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
                        .setStartColumnIndex(0).setEndColumnIndex(21))
                .setRows(Collections.singletonList(new RowData().setValues(headerCells)))
                .setFields("userEnteredFormat")));

        // Add conditional formatting for rejected applications
        requests.add(new Request().setAddConditionalFormatRule(
                new com.google.api.services.sheets.v4.model.AddConditionalFormatRuleRequest()
                        .setRule(new com.google.api.services.sheets.v4.model.ConditionalFormatRule()
                                .setRanges(Collections.singletonList(new GridRange()
                                        .setSheetId(0)
                                        .setStartRowIndex(1) // Start from row 2 (after header)
                                        .setStartColumnIndex(0) // Column A
                                        .setEndColumnIndex(10))) // Through column J (visible columns)
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
                                        .setEndColumnIndex(10)))
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
                                        .setEndColumnIndex(10)))
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

        // Set optimized column widths for VISIBLE columns only
        int[] visibleColumnWidths = {
            200, // A: Company
            280, // B: Position  
            120, // C: Date Applied
            120, // D: Status
            100, // E: Provider
            130, // F: Work Location
            120, // G: Interview Date
            80, // H: Email Link
            80, // I: Application URL
            350 // J: Notes
        };

        // Set widths for visible columns (A-J)
        for (int i = 0; i < visibleColumnWidths.length; i++) {
            requests.add(new Request().setUpdateDimensionProperties(
                    new UpdateDimensionPropertiesRequest()
                            .setRange(new DimensionRange().setSheetId(0).setDimension("COLUMNS")
                                    .setStartIndex(i).setEndIndex(i + 1))
                            .setProperties(new DimensionProperties().setPixelSize(visibleColumnWidths[i]))
                            .setFields("pixelSize")));
        }

        // Hide all the data storage columns (K through U, which are indices 10-20)
        requests.add(new Request().setUpdateDimensionProperties(
                new UpdateDimensionPropertiesRequest()
                        .setRange(new DimensionRange().setSheetId(0).setDimension("COLUMNS")
                                .setStartIndex(10).setEndIndex(21))
                        .setProperties(new DimensionProperties().setHiddenByUser(true))
                        .setFields("hiddenByUser")));

        // Freeze the first row
        requests.add(new Request().setUpdateSheetProperties(
                new UpdateSheetPropertiesRequest().setProperties(
                        new SheetProperties().setSheetId(0).setGridProperties(
                                new GridProperties().setFrozenRowCount(1)))
                        .setFields("gridProperties.frozenRowCount")));

        // Create a basic filter for VISIBLE columns only (A-J)
        requests.add(new Request().setSetBasicFilter(new SetBasicFilterRequest()
                .setFilter(new BasicFilter().setRange(
                        new GridRange().setSheetId(0)
                                .setStartRowIndex(0).setStartColumnIndex(0)
                                .setEndColumnIndex(10)))));

        sheetsService.spreadsheets().batchUpdate(spreadsheetId,
                new BatchUpdateSpreadsheetRequest().setRequests(requests)).execute();
    }
}
