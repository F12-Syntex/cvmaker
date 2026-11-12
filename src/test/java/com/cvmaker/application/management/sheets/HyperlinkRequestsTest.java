package com.cvmaker.application.management.sheets;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cvmaker.application.management.JobApplicationData;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;

class HyperlinkRequestsTest {

    private static JobApplicationData job(String emailId, String applicationUrl) {
        JobApplicationData job = new JobApplicationData();
        job.setEmailId(emailId); // also sets the Gmail link
        job.setApplicationUrl(applicationUrl);
        return job;
    }

    private static String formula(Request request) {
        return request.getUpdateCells().getRows().get(0).getValues().get(0).getUserEnteredValue().getFormulaValue();
    }

    @Test
    void buildsOneCellUpdatePerLink() {
        List<Request> requests = HyperlinkRequests.forRow(7, 5, job("abc123", "https://acme.example/jobs/1"));

        assertThat(requests).hasSize(2);

        UpdateCellsRequest email = requests.get(0).getUpdateCells();
        GridRange emailRange = email.getRange();
        assertThat(emailRange.getSheetId()).isEqualTo(7);
        assertThat(emailRange.getStartRowIndex()).isEqualTo(4);
        assertThat(emailRange.getEndRowIndex()).isEqualTo(5);
        assertThat(emailRange.getStartColumnIndex()).isEqualTo(SheetColumn.EMAIL_LINK.index());
        assertThat(emailRange.getEndColumnIndex()).isEqualTo(SheetColumn.EMAIL_LINK.index() + 1);
        assertThat(email.getFields()).isEqualTo("userEnteredValue");
        assertThat(formula(requests.get(0)))
                .isEqualTo("=HYPERLINK(\"https://mail.google.com/mail/u/0/#inbox/abc123\", \"📧 View\")");

        GridRange applicationRange = requests.get(1).getUpdateCells().getRange();
        assertThat(applicationRange.getSheetId()).isEqualTo(7);
        assertThat(applicationRange.getStartRowIndex()).isEqualTo(4);
        assertThat(applicationRange.getStartColumnIndex()).isEqualTo(SheetColumn.APPLICATION_URL.index());
        assertThat(formula(requests.get(1))).isEqualTo("=HYPERLINK(\"https://acme.example/jobs/1\", \"🔗 Apply\")");
    }

    @Test
    void missingOrUnsafeLinksProduceNoRequest() {
        assertThat(HyperlinkRequests.forRow(0, 2, job(null, null))).isEmpty();
        assertThat(HyperlinkRequests.forRow(0, 2, job("", "javascript:alert(1)"))).isEmpty();
        assertThat(HyperlinkRequests.forRow(0, 2, job("abc", "not a url"))).hasSize(1);
    }

    @Test
    void injectedQuotesStayInsideTheUrlString() {
        JobApplicationData job = job(null, "https://evil.example/\")&IMPORTDATA(\"https://evil.example/x");

        List<Request> requests = HyperlinkRequests.forRow(0, 2, job);

        assertThat(requests).hasSize(1);
        assertThat(formula(requests.get(0))).isEqualTo(
                "=HYPERLINK(\"https://evil.example/\"\")&IMPORTDATA(\"\"https://evil.example/x\", \"🔗 Apply\")");
    }

    @Test
    void rowsStartBelowTheHeader() {
        List<JobApplicationData> jobs = List.of(
                job("a", null),
                job(null, null),
                job("c", "https://acme.example/3"));

        List<Request> requests = HyperlinkRequests.forRows(0, jobs);

        assertThat(requests).hasSize(3);
        // sheet rows 2 and 4 are 0-based indices 1 and 3; the second application has no links
        assertThat(requests).extracting(r -> r.getUpdateCells().getRange().getStartRowIndex())
                .containsExactly(1, 3, 3);
        assertThat(requests).extracting(r -> r.getUpdateCells().getRange().getStartColumnIndex())
                .containsExactly(SheetColumn.EMAIL_LINK.index(), SheetColumn.EMAIL_LINK.index(),
                        SheetColumn.APPLICATION_URL.index());
    }
}
