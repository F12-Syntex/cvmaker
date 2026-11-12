package com.cvmaker.application.management.sheets;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cvmaker.application.management.JobApplicationData;

class ApplicationRecencyTest {

    private static JobApplicationData job(String company, String status) {
        JobApplicationData job = new JobApplicationData();
        job.setJobRelated(true);
        job.setCompanyName(company);
        job.setApplicationStatus(status);
        return job;
    }

    private static JobApplicationData withEmailTime(JobApplicationData job, String emailTimestamp) {
        job.setEmailTimestamp(emailTimestamp);
        return job;
    }

    @Test
    void laterEmailTimestampWins() {
        JobApplicationData older = withEmailTime(job("Acme", "Offered"), "2025-11-10T09:00:00");
        JobApplicationData newer = withEmailTime(job("Acme", "Applied"), "2025-11-11T09:00:00");

        assertThat(ApplicationRecency.isNewer(newer, older)).isTrue();
        assertThat(ApplicationRecency.isNewer(older, newer)).isFalse();
    }

    @Test
    void emailTimestampBeatsTheOtherFields() {
        JobApplicationData older = withEmailTime(job("Acme", "Applied"), "2025-11-10T09:00:00");
        older.setLastUpdated("2025-11-30T00:00:00");
        older.setProcessedTimestamp("2025-11-30T00:00:00");
        older.setDate("2025-11-30");
        JobApplicationData newer = withEmailTime(job("Acme", "Applied"), "2025-11-11T09:00:00");
        newer.setLastUpdated("2025-11-01T00:00:00");
        newer.setProcessedTimestamp("2025-11-01T00:00:00");
        newer.setDate("2025-11-01");

        assertThat(ApplicationRecency.isNewer(newer, older)).isTrue();
    }

    @Test
    void fallsBackThroughLastUpdatedProcessedAndDate() {
        JobApplicationData a = job("Acme", "Applied");
        JobApplicationData b = job("Acme", "Applied");
        // email timestamp missing on one side: not comparable
        a.setEmailTimestamp("2025-11-20T00:00:00");
        a.setLastUpdated("2025-11-01T00:00:00");
        b.setLastUpdated("2025-11-02T00:00:00");
        assertThat(ApplicationRecency.isNewer(b, a)).isTrue();
        assertThat(ApplicationRecency.isNewer(a, b)).isFalse();

        JobApplicationData c = job("Acme", "Applied");
        JobApplicationData d = job("Acme", "Applied");
        c.setProcessedTimestamp("2025-11-01T00:00:00");
        d.setProcessedTimestamp("2025-11-02T00:00:00");
        assertThat(ApplicationRecency.isNewer(d, c)).isTrue();

        JobApplicationData e = job("Acme", "Applied");
        JobApplicationData f = job("Acme", "Applied");
        e.setDate("Mon, 10 Nov 2025 09:00:00 +0000");
        f.setDate("11/11/2025");
        assertThat(ApplicationRecency.isNewer(f, e)).isTrue();
        assertThat(ApplicationRecency.isNewer(e, f)).isFalse();
    }

    @Test
    void equalTimestampsFallThroughToTheNextField() {
        JobApplicationData a = withEmailTime(job("Acme", "Applied"), "2025-11-10T09:00:00");
        JobApplicationData b = withEmailTime(job("Acme", "Applied"), "2025-11-10T09:00:00");
        a.setLastUpdated("2025-11-10T10:00:00");
        b.setLastUpdated("2025-11-10T11:00:00");

        assertThat(ApplicationRecency.isNewer(b, a)).isTrue();
        assertThat(ApplicationRecency.isNewer(a, b)).isFalse();
    }

    @Test
    void unparsableTimestampsAreIgnored() {
        JobApplicationData a = withEmailTime(job("Acme", "Applied"), "not a time");
        JobApplicationData b = withEmailTime(job("Acme", "Interview"), "also not a time");

        assertThat(ApplicationRecency.isNewer(b, a)).isTrue(); // decided by status
        assertThat(ApplicationRecency.isNewer(a, b)).isFalse();
    }

    @Test
    void withoutDatesTheStatusRankDecides() {
        assertThat(ApplicationRecency.isNewer(job("Acme", "Interview Scheduled"), job("Acme", "Applied"))).isTrue();
        assertThat(ApplicationRecency.isNewer(job("Acme", "Rejected"), job("Acme", "Interview"))).isTrue();
        assertThat(ApplicationRecency.isNewer(job("Acme", "Interview"), job("Acme", "Rejected"))).isFalse();
        assertThat(ApplicationRecency.isNewer(job("Acme", "Offered"), job("Acme", "Rejected"))).isTrue();
    }

    @Test
    void tiesAndUnknownStatusesKeepTheCurrentRecord() {
        assertThat(ApplicationRecency.isNewer(job("Acme", "Applied"), job("Acme", "Applied"))).isFalse();
        assertThat(ApplicationRecency.isNewer(job("Acme", "Offer"), job("Acme", "Offered"))).isFalse();
        assertThat(ApplicationRecency.isNewer(job("Acme", "mystery"), job("Acme", "Applied"))).isFalse();
        assertThat(ApplicationRecency.isNewer(job("Acme", null), job("Acme", null))).isFalse();
    }

    @Test
    void latestPerCompanyKeepsTheNewestRecordOfEachCompany() {
        JobApplicationData acmeOld = withEmailTime(job("Acme", "Applied"), "2025-11-01T09:00:00");
        JobApplicationData acmeNew = withEmailTime(job(" ACME ", "Rejected"), "2025-11-05T09:00:00");
        JobApplicationData globex = withEmailTime(job("Globex", "Interview"), "2025-11-03T09:00:00");

        Map<String, JobApplicationData> latest = ApplicationRecency.latestPerCompany(
                List.of(acmeOld, globex, acmeNew));

        assertThat(latest).containsOnlyKeys("acme", "globex");
        assertThat(latest.get("acme")).isSameAs(acmeNew);
        assertThat(latest.get("globex")).isSameAs(globex);
        assertThat(latest.keySet()).containsExactly("acme", "globex"); // order of first appearance
    }

    @Test
    void latestPerCompanySkipsRecordsThatCannotBeShown() {
        JobApplicationData notJob = job("Newsletter Inc", "Applied");
        notJob.setJobRelated(false);

        Map<String, JobApplicationData> latest = ApplicationRecency.latestPerCompany(
                Arrays.asList(notJob, job(null, "Applied"), job("   ", "Applied"), null, job("Acme", "Applied")));

        assertThat(latest).containsOnlyKeys("acme");
    }
}
