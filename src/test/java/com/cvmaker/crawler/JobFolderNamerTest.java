package com.cvmaker.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JobFolderNamerTest {

    private static final String SAFE_NAME = "[A-Za-z0-9_-]+";

    private static JobInfo job(String title, String company, String jobId, String url) {
        JobInfo job = new JobInfo(title, company);
        job.setJobId(jobId);
        job.setUrl(url);
        return job;
    }

    // ---- collisions ----

    @Test
    void sameTitleAndCompanyWithDifferentJobIdsGetDifferentFolders() {
        String first = JobFolderNamer.folderName(job("Java Developer", "Acme", "1001", ""), "text");
        String second = JobFolderNamer.folderName(job("Java Developer", "Acme", "1002", ""), "text");

        assertThat(first).isNotEqualTo(second);
        assertThat(first).startsWith("Job-Java_Developer_Acme_");
        assertThat(second).startsWith("Job-Java_Developer_Acme_");
    }

    @Test
    void sameTitleAndCompanyWithDifferentUrlsGetDifferentFolders() {
        String first = JobFolderNamer.folderName(job("Java Developer", "Acme", "", "https://example.com/jobs/1"), "text");
        String second = JobFolderNamer.folderName(job("Java Developer", "Acme", "", "https://example.com/jobs/2"), "text");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void withoutIdOrUrlTheDescriptionTellsJobsApart() {
        // What the Reed crawler produces today: title and company only.
        String first = JobFolderNamer.folderName(new JobInfo("Java Developer", "Agency"), "Fintech client in Leeds.");
        String second = JobFolderNamer.folderName(new JobInfo("Java Developer", "Agency"), "Retail client in York.");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theSameJobAlwaysGetsTheSameFolder() {
        String first = JobFolderNamer.folderName(job("Java Developer", "Acme", "1001", ""), "text");
        String again = JobFolderNamer.folderName(job("Java Developer", "Acme", "1001", ""), "text");

        assertThat(first).isEqualTo(again);
    }

    @Test
    void jobIdWinsOverUrlAndDescription() {
        String withId = JobFolderNamer.folderName(job("Java Developer", "Acme", "1001", "https://example.com/a"), "one");
        String sameIdOtherRest = JobFolderNamer.folderName(job("Java Developer", "Acme", " 1001 ", "https://example.com/b"), "two");

        assertThat(withId).isEqualTo(sameIdOtherRest);
    }

    @Test
    void urlWinsOverDescription() {
        String first = JobFolderNamer.folderName(job("Java Developer", "Acme", null, "https://example.com/a"), "one");
        String second = JobFolderNamer.folderName(job("Java Developer", "Acme", null, "https://example.com/a"), "two");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void whitespaceChangesInTheDescriptionDoNotMakeANewFolder() {
        String first = JobFolderNamer.folderName(new JobInfo("Java Developer", "Acme"), "Great  job.\n\nApply now.");
        String second = JobFolderNamer.folderName(new JobInfo("Java Developer", "Acme"), "  Great job. Apply now.\n");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentCompaniesGetDifferentFoldersEvenWithoutAKey() {
        String first = JobFolderNamer.folderName("Java Developer", "Acme", null);
        String second = JobFolderNamer.folderName("Java Developer", "Globex", null);

        assertThat(first).isEqualTo("Job-Java_Developer_Acme");
        assertThat(second).isEqualTo("Job-Java_Developer_Globex");
    }

    // ---- shape of the name ----

    @Test
    void nameIsPrefixTitleCompanyAndAnEightDigitHash() {
        String name = JobFolderNamer.folderName(job("Java Developer", "Acme Ltd", "1001", ""), "text");

        assertThat(name).matches("Job-Java_Developer_Acme_Ltd_[0-9a-f]{8}");
    }

    @Test
    void titleIsCutToFiveWords() {
        String name = JobFolderNamer.folderName("Senior Java Developer with Spring Boot and Kafka", "Acme", null);

        assertThat(name).isEqualTo("Job-Senior_Java_Developer_with_Spring_Acme");
    }

    @Test
    void longPartsAreCutWithoutADanglingSeparator() {
        String title = "Supercalifragilisticexpialidocious Administrator-Coordinator Extraordinaire";

        String slug = JobFolderNamer.slug(title, JobFolderNamer.MAX_TITLE_WORDS);

        assertThat(slug).hasSizeLessThanOrEqualTo(JobFolderNamer.MAX_PART_LENGTH);
        assertThat(slug).doesNotEndWith("_").doesNotEndWith("-");
        assertThat(slug).startsWith("Supercalifragilisticexpialidocious_");
    }

    @Test
    void searchResultNumberingIsDroppedButAbbreviationsAreKept() {
        assertThat(JobFolderNamer.slug("3. Java Developer", 5)).isEqualTo("Java_Developer");
        assertThat(JobFolderNamer.slug("12) Java Developer", 5)).isEqualTo("Java_Developer");
        // The old code dropped everything before the first '.', which turned this into "Developer".
        assertThat(JobFolderNamer.slug("Sr. Java Developer", 5)).isEqualTo("Sr_Java_Developer");
        assertThat(JobFolderNamer.slug("3D Artist", 5)).isEqualTo("3D_Artist");
    }

    @Test
    void punctuationSeparatesWordsAndAccentsAreFolded() {
        assertThat(JobFolderNamer.slug("C#/.NET Developer (Remote)", 5)).isEqualTo("C_NET_Developer_Remote");
        assertThat(JobFolderNamer.slug("Développeur Zürich", 5)).isEqualTo("Developpeur_Zurich");
        assertThat(JobFolderNamer.slug("Front-end Engineer", 5)).isEqualTo("Front-end_Engineer");
    }

    // ---- hostile or missing input ----

    @Test
    void nullJobAndNullFieldsDoNotThrow() {
        assertThat(JobFolderNamer.folderName(null, null)).isEqualTo("Job-untitled");
        assertThat(JobFolderNamer.folderName(new JobInfo(null, null), null)).isEqualTo("Job-untitled");
        assertThat(JobFolderNamer.folderName(null, null, null)).isEqualTo("Job-untitled");
        assertThat(JobFolderNamer.folderName(job(null, null, null, null), "text")).matches("Job-untitled_[0-9a-f]{8}");
    }

    @Test
    void titleWithNoUsableCharactersBecomesUntitled() {
        assertThat(JobFolderNamer.folderName("***", "", "  ")).isEqualTo("Job-untitled");
        assertThat(JobFolderNamer.folderName("职位", "Acme", null)).isEqualTo("Job-untitled_Acme");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "../../etc/passwd",
        "..\\..\\Windows\\System32",
        "/absolute/path",
        "C:\\Users\\me",
        "a/b\\c:d*e?f\"g<h>i|j",
        "name.with.dots.",
        "tab\tand\nnewline",
        "~/.ssh/id_rsa"
    })
    void nameIsAlwaysASingleSafePathSegment(String hostile) {
        String name = JobFolderNamer.folderName(job(hostile, hostile, hostile, hostile), hostile);

        assertThat(name).matches(SAFE_NAME);
        assertThat(name).startsWith("Job-");
    }

    @Test
    void shortHashIsStableLowercaseHex() {
        // SHA-256("abc") starts with ba7816bf
        assertThat(JobFolderNamer.shortHash("abc")).isEqualTo("ba7816bf");
    }
}
