package com.cvmaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cvmaker.JobDataFetcher.JobData;

class JobDataFetcherTest {

    private static final String LONG_TEXT =
        "We are looking for an engineer to build and run reliable services for our customers every day.";

    private static String fixture(String name) throws IOException {
        try (InputStream in = JobDataFetcherTest.class.getResourceAsStream("/cvgen/" + name)) {
            assertThat(in).as("fixture " + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---- Parsing saved pages -----------------------------------------------------------------

    @Test
    void parsesJsonLdJobPostingInsideGraph() throws Exception {
        JobData data = JobDataFetcher.parseJobPage(fixture("jobposting-jsonld.html"),
            "https://www.reed.co.uk/jobs/senior-java-developer/123");

        assertThat(data.getJobTitle()).isEqualTo("Senior Java Developer");
        assertThat(data.getCompanyName()).isEqualTo("Northwind Analytics Ltd");
        assertThat(data.getJobName()).isEqualTo("senior_java_developer_northwind_analytics_ltd");
        assertThat(data.getJobDescription())
            .startsWith("Title: Senior Java Developer\nCompany: Northwind Analytics Ltd\nLocation: Leeds, GB\n")
            .contains("Source: https://www.reed.co.uk/jobs/senior-java-developer/123")
            .contains("hiring a Senior Java Developer to build data pipelines.")
            .contains("- Java 21 and Spring\n- PostgreSQL and Kafka")
            .contains("Hybrid working")
            .doesNotContain("&lt;")
            .doesNotContain("<p>")
            .doesNotContain("Cookie settings");
    }

    @Test
    void fallsBackToOgTitleAndDescriptionContainer() throws Exception {
        JobData data = JobDataFetcher.parseJobPage(fixture("og-title-only.html"),
            "https://uk.indeed.com/viewjob?jk=abc");

        assertThat(data.getJobTitle()).isEqualTo("Data Engineer");
        assertThat(data.getCompanyName()).isEqualTo("Contoso Health");
        assertThat(data.getJobDescription())
            .contains("About the role")
            .contains("- Build batch and streaming pipelines in Python")
            .contains("Model data in BigQuery\nand keep it documented")
            .doesNotContain("ignore me")
            .doesNotContain("Similar jobs")
            .doesNotContain("color: red");
    }

    @Test
    void javaScriptOnlyPageIsRejectedInsteadOfInventingAJob() throws Exception {
        String html = fixture("js-shell.html");

        assertThatThrownBy(() -> JobDataFetcher.parseJobPage(html, "https://example.com/job/1"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Could not find a job description at https://example.com/job/1");
    }

    @Test
    void readsMicrodataAndTopLevelJsonLdArray() throws Exception {
        String microdata = "<html><body><div itemscope itemtype='https://schema.org/JobPosting'>"
            + "<h1 itemprop='title'>QA Lead</h1>"
            + "<div itemprop='hiringOrganization' itemscope><span itemprop='name'>Fabrikam</span></div>"
            + "<div itemprop='description'><p>" + LONG_TEXT + "</p></div></div></body></html>";
        JobData fromMicrodata = JobDataFetcher.parseJobPage(microdata, "https://jobs.example.org/1");
        assertThat(fromMicrodata.getJobTitle()).isEqualTo("QA Lead");
        assertThat(fromMicrodata.getCompanyName()).isEqualTo("Fabrikam");
        assertThat(fromMicrodata.getJobDescription()).contains(LONG_TEXT);

        String jsonLdArray = "<html><head><script type='application/ld+json'>"
            + "[{\"@type\":\"Organization\",\"name\":\"Nope\"},"
            + "{\"@type\":\"http://schema.org/JobPosting\",\"title\":\"Platform Engineer\","
            + "\"hiringOrganization\":\"Tailspin Toys\",\"description\":\"" + LONG_TEXT + "\"}]"
            + "</script></head><body></body></html>";
        JobData fromArray = JobDataFetcher.parseJobPage(jsonLdArray, "https://example.com/2");
        assertThat(fromArray.getJobTitle()).isEqualTo("Platform Engineer");
        assertThat(fromArray.getCompanyName()).isEqualTo("Tailspin Toys");
    }

    @Test
    void unknownTitleAndCompanyUsePlaceholdersNotAFakeJob() throws Exception {
        String html = "<html><body><main><p>" + LONG_TEXT + "</p></main></body></html>";

        JobData data = JobDataFetcher.parseJobPage(html, "https://example.com/3");

        assertThat(data.getJobTitle()).isEqualTo(JobDataFetcher.UNKNOWN_TITLE);
        assertThat(data.getCompanyName()).isEqualTo(JobDataFetcher.UNKNOWN_COMPANY);
        assertThat(data.getJobDescription()).contains(LONG_TEXT);
    }

    @Test
    void veryLongDescriptionsAreTruncated() throws Exception {
        String body = "word ".repeat(10_000);
        String html = "<html><body><article><p>" + body + "</p></article></body></html>";

        JobData data = JobDataFetcher.parseJobPage(html, "https://example.com/4");

        assertThat(data.getJobDescription())
            .hasSizeLessThan(JobDataFetcher.MAX_DESCRIPTION_LENGTH + 500)
            .endsWith("[truncated]");
    }

    @Test
    void splitsPageTitles() {
        assertThat(JobDataFetcher.splitPageTitle("Engineer - Acme - Reed.co.uk", "Reed.co.uk", "www.reed.co.uk"))
            .containsExactly("Engineer", "Acme");
        assertThat(JobDataFetcher.splitPageTitle("Engineer | LinkedIn", null, "www.linkedin.com"))
            .containsExactly("Engineer", null);
        assertThat(JobDataFetcher.splitPageTitle("Engineer at Acme", null, null))
            .containsExactly("Engineer", "Acme");
        assertThat(JobDataFetcher.splitPageTitle("Back-end Engineer \u2013 Acme", null, null))
            .containsExactly("Back-end Engineer", "Acme");
        assertThat(JobDataFetcher.splitPageTitle(null, null, null)).containsExactly(null, null);
    }

    @Test
    void htmlToTextKeepsLineStructure() {
        assertThat(JobDataFetcher.htmlToText("<p>One</p><p>Two<br>Three</p><ul><li>A</li><li>B</li></ul>"))
            .isEqualTo("One\nTwo\nThree\n- A\n- B");
        assertThat(JobDataFetcher.htmlToText("   ")).isNull();
        assertThat(JobDataFetcher.htmlToText(null)).isNull();
    }

    // ---- Source routing ----------------------------------------------------------------------

    @Test
    void urlSourceUsesTheFetcherAndParsesTheRealPage() throws Exception {
        List<URI> requested = new ArrayList<>();
        String page = fixture("og-title-only.html");
        JobDataFetcher fetcher = new JobDataFetcher(uri -> {
            requested.add(uri);
            return page;
        });

        JobData data = fetcher.fetchJobData("  https://uk.indeed.com/viewjob?jk=abc ");

        assertThat(requested).containsExactly(URI.create("https://uk.indeed.com/viewjob?jk=abc"));
        // The old stub returned "Software Engineer" at "Tech Company" for every URL
        assertThat(data.getJobTitle()).isEqualTo("Data Engineer");
        assertThat(data.getCompanyName()).isEqualTo("Contoso Health");
        assertThat(data.getJobDescription()).doesNotContain("Job description from URL");
    }

    @Test
    void fetchErrorsPropagate() {
        JobDataFetcher fetcher = new JobDataFetcher(uri -> {
            throw new IOException("HTTP 404");
        });

        assertThatThrownBy(() -> fetcher.fetchJobData("https://example.com/gone"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("404");
    }

    @Test
    void blankOrNullSourceIsAnError() {
        JobDataFetcher fetcher = new JobDataFetcher(uri -> "");

        assertThatThrownBy(() -> fetcher.fetchJobData(null)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> fetcher.fetchJobData("   ")).isInstanceOf(IOException.class);
    }

    @Test
    void recognisesHttpUrls() {
        assertThat(JobDataFetcher.isHttpUrl("https://www.reed.co.uk/jobs/1")).isTrue();
        assertThat(JobDataFetcher.isHttpUrl("HTTP://EXAMPLE.COM")).isTrue();
        assertThat(JobDataFetcher.isHttpUrl("http://")).isFalse();
        assertThat(JobDataFetcher.isHttpUrl("httpdocs/job.txt")).isFalse();
        assertThat(JobDataFetcher.isHttpUrl("ftp://example.com/job")).isFalse();
        assertThat(JobDataFetcher.isHttpUrl("C:\\jobs\\job.txt")).isFalse();
        assertThat(JobDataFetcher.isHttpUrl("/tmp/job.txt")).isFalse();
        assertThat(JobDataFetcher.isHttpUrl(null)).isFalse();
    }

    @Test
    void fileSourceIsReadFromDisk(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("job.txt"), "Title: Analyst\nCompany: Contoso\nDetails here");
        JobDataFetcher fetcher = new JobDataFetcher(uri -> {
            throw new AssertionError("must not fetch for a file");
        });

        JobData data = fetcher.fetchJobData(file.toString());

        assertThat(data.getJobTitle()).isEqualTo("Analyst");
        assertThat(data.getCompanyName()).isEqualTo("Contoso");
        assertThat(data.getJobDescription()).contains("Details here");
    }

    @Test
    void missingFileWithoutSlashIsNotTreatedAsAUrl() {
        JobDataFetcher fetcher = new JobDataFetcher(uri -> {
            throw new AssertionError("must not fetch for a file");
        });

        assertThatThrownBy(() -> fetcher.fetchJobData("no-such-job-file.txt"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("File not found");
    }

    // ---- Job folder names --------------------------------------------------------------------

    @Test
    void jobNameIsASafeFolderName() {
        assertThat(JobData.generateJobName("Senior Java Developer", "Northwind"))
            .isEqualTo("senior_java_developer_northwind");
        assertThat(JobData.generateJobName("../../etc", "Acme")).isEqualTo("etc_acme");
        assertThat(JobData.generateJobName(null, null)).isEqualTo("job");
        assertThat(JobData.generateJobName("!!!", "???")).isEqualTo("job");
        assertThat(JobData.generateJobName("C++ / C# Engineer", "")).isEqualTo("c_c_engineer");

        String longName = JobData.generateJobName("Principal Software Engineer Distributed Systems", "Very Long Company");
        assertThat(longName).hasSizeLessThanOrEqualTo(50).doesNotEndWith("_").doesNotEndWith("-");
    }
}
