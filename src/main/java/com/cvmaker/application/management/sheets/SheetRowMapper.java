package com.cvmaker.application.management.sheets;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.cvmaker.application.management.JobApplicationData;

/**
 * Turns a {@link JobApplicationData} into the values of one tracker row. Pure: no Sheets API
 * calls, and the current time is passed in.
 */
public final class SheetRowMapper {

    /** Placeholder shown in the Email Link column until the hyperlink formula replaces it. */
    public static final String EMAIL_LINK_TEXT = "View Email";
    /** Placeholder shown in the Application URL column until the hyperlink formula replaces it. */
    public static final String APPLICATION_LINK_TEXT = "Apply";
    /** Status shown when the application has none. */
    public static final String DEFAULT_STATUS = "Applied";

    private static final String NOTE_SEPARATOR = " | ";

    private SheetRowMapper() {
    }

    /** The header row: one cell per {@link SheetColumn}, in sheet order. */
    public static List<Object> headerRow() {
        List<Object> row = new ArrayList<>(SheetColumn.count());
        for (SheetColumn column : SheetColumn.all()) {
            row.add(column.header());
        }
        return row;
    }

    /**
     * Builds the row for one application: one value per {@link SheetColumn}, in sheet order.
     * No value is null. The two link cells hold placeholder text, and stay empty when the link
     * is not a safe URL (see {@link HyperlinkFormula}).
     *
     * @param job the application
     * @param now the time written to Last Updated, and to Email Timestamp when the application
     *            has none
     * @return the row values
     */
    public static List<Object> toRow(JobApplicationData job, LocalDateTime now) {
        String nowText = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        List<Object> row = new ArrayList<>(SheetColumn.count());
        for (SheetColumn column : SheetColumn.all()) {
            row.add(switch (column) {
                case COMPANY -> orEmpty(job.getCompanyName());
                case POSITION -> orEmpty(job.getPositionTitle());
                case DATE_APPLIED -> DateParsing.toDisplayDate(job.getDate());
                case STATUS -> job.getApplicationStatus() != null ? job.getApplicationStatus() : DEFAULT_STATUS;
                case PROVIDER -> orEmpty(job.getProvider());
                case EMAIL_LINK -> HyperlinkFormula.isSafeUrl(job.getEmailLink()) ? EMAIL_LINK_TEXT : "";
                case APPLICATION_URL -> HyperlinkFormula.isSafeUrl(job.getApplicationUrl()) ? APPLICATION_LINK_TEXT : "";
                case NOTES -> combineNotes(job);
                case CONTACT_EMAIL -> orEmpty(job.getContactEmail());
                case WORK_LOCATION -> orEmpty(job.getWorkLocation());
                case INTERVIEW_DATE -> DateParsing.toDisplayDate(job.getInterviewDate());
                case WORK_TYPE -> orEmpty(job.getWorkType());
                case SALARY_RANGE -> orEmpty(job.getSalaryRange());
                case CONTACT_PERSON -> orEmpty(job.getContactPerson());
                case NEXT_STEPS -> orEmpty(job.getNextSteps());
                case EMAIL_ID -> orEmpty(job.getEmailId());
                case DEADLINE -> orEmpty(job.getApplicationDeadline());
                case SKILLS -> orEmpty(job.getRequiredSkills());
                case REJECTION_REASON -> orEmpty(job.getRejectionReason());
                case OFFER_DETAILS -> orEmpty(job.getOfferDetails());
                case LAST_UPDATED -> nowText;
                case EMAIL_TIMESTAMP -> job.getEmailTimestamp() != null ? job.getEmailTimestamp() : nowText;
            });
        }
        return row;
    }

    /**
     * Builds the Notes cell. Parts are joined with {@code " | "}, blank parts are skipped, and
     * the order is fixed:
     * <ol>
     * <li>{@code ❌ REJECTED: <reason>}, only for a rejected application with a reason</li>
     * <li>the extracted info</li>
     * <li>{@code 💰} salary range</li>
     * <li>{@code 👤} contact person</li>
     * <li>{@code ➡️} next steps</li>
     * <li>{@code ❌ <reason>}, only when the application is not rejected</li>
     * <li>{@code 🎉} offer details</li>
     * <li>{@code 🔧} required skills</li>
     * </ol>
     *
     * @param job the application
     * @return the notes text, empty when there is nothing to say
     */
    public static String combineNotes(JobApplicationData job) {
        boolean rejected = isRejected(job.getApplicationStatus());
        List<String> parts = new ArrayList<>();

        if (rejected) {
            addPart(parts, "❌ REJECTED: ", job.getRejectionReason());
        }
        addPart(parts, "", job.getExtractedInfo());
        addPart(parts, "💰 ", job.getSalaryRange());
        addPart(parts, "👤 ", job.getContactPerson());
        addPart(parts, "➡️ ", job.getNextSteps());
        if (!rejected) {
            addPart(parts, "❌ ", job.getRejectionReason());
        }
        addPart(parts, "🎉 ", job.getOfferDetails());
        addPart(parts, "🔧 ", job.getRequiredSkills());

        return String.join(NOTE_SEPARATOR, parts);
    }

    private static boolean isRejected(String status) {
        return status != null && status.trim().equalsIgnoreCase("Rejected");
    }

    private static void addPart(List<String> parts, String prefix, String text) {
        if (text != null && !text.trim().isEmpty()) {
            parts.add(prefix + text.trim());
        }
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
