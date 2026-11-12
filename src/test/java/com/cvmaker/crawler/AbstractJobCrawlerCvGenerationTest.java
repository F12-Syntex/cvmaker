package com.cvmaker.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cvmaker.CVGenerator;
import com.cvmaker.configuration.CrawlerConfig;
import com.microsoft.playwright.Playwright;

/**
 * CV generation from a crawler: one generator per session, output directory
 * restored after every job, generator shut down in close().
 */
class AbstractJobCrawlerCvGenerationTest {

    @TempDir
    Path tempDir;

    private TestCrawler crawler;
    private String configuredOutputDir;

    /** Concrete crawler with a mocked CV generator and no browser. */
    private static class TestCrawler extends AbstractJobCrawler {

        final CVGenerator generator = mock(CVGenerator.class);
        int generatorsCreated;

        TestCrawler(CrawlerConfig config) throws Exception {
            super(config);
        }

        @Override
        protected CVGenerator createCvGenerator() {
            generatorsCreated++;
            return generator;
        }

        @Override
        protected Playwright createPlaywright() {
            throw new AssertionError("no browser expected in this test");
        }

        @Override
        public void processJobsAndApply() {
        }

        @Override
        public String getCrawlerName() {
            return "Test";
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        crawler = new TestCrawler(CrawlerTestSupport.crawlerConfig(tempDir));
        configuredOutputDir = crawler.getConfig().getOutputDirectory();
    }

    /** Make the mocked generator write the PDF into the current output directory. */
    private List<String> generatorWritesPdf() throws Exception {
        List<String> outputDirsSeen = new ArrayList<>();
        doAnswer(invocation -> {
            String dir = crawler.getConfig().getOutputDirectory();
            outputDirsSeen.add(dir);
            Files.writeString(Paths.get(dir).resolve(crawler.getConfig().getOutputPdfName()), "pdf");
            return null;
        }).when(crawler.generator).generate();
        return outputDirsSeen;
    }

    @Test
    void generatesIntoAJobFolderAndRestoresTheOutputDirectory() throws Exception {
        List<String> outputDirsSeen = generatorWritesPdf();

        Path cv = crawler.generateCVForJob(new JobInfo("Java Developer", "Acme"), "We need a Java developer.");

        assertThat(cv).isNotNull().exists();
        assertThat(cv.getFileName()).hasToString("cv.pdf");
        // During generation the output directory was a folder below the configured one...
        assertThat(outputDirsSeen).hasSize(1);
        assertThat(Paths.get(outputDirsSeen.get(0)).getParent()).isEqualTo(Paths.get(configuredOutputDir));
        assertThat(cv.getParent()).isEqualTo(Paths.get(outputDirsSeen.get(0)));
        // ...and afterwards it is back to the configured one.
        assertThat(crawler.getConfig().getOutputDirectory()).isEqualTo(configuredOutputDir);
        assertThat(crawler.getConfig().getJobDescriptionContent()).isEqualTo("We need a Java developer.");
    }

    @Test
    void failedGenerationReturnsNullAndStillRestoresTheOutputDirectory() throws Exception {
        doThrow(new IllegalStateException("AI request failed")).when(crawler.generator).generate();

        Path cv = crawler.generateCVForJob(new JobInfo("Java Developer", "Acme"), "We need a Java developer.");

        assertThat(cv).isNull();
        assertThat(crawler.getConfig().getOutputDirectory()).isEqualTo(configuredOutputDir);
    }

    @Test
    void jobAfterAFailedOneIsNotNestedInsideTheFailedJobsFolder() throws Exception {
        doThrow(new IllegalStateException("AI request failed")).when(crawler.generator).generate();
        crawler.generateCVForJob(new JobInfo("First Job", "Acme"), "First description.");

        List<String> outputDirsSeen = generatorWritesPdf();
        Path cv = crawler.generateCVForJob(new JobInfo("Second Job", "Acme"), "Second description.");

        assertThat(cv).isNotNull();
        assertThat(Paths.get(outputDirsSeen.get(0)).getParent()).isEqualTo(Paths.get(configuredOutputDir));
    }

    @Test
    void oneGeneratorIsReusedForAllJobsAndNotShutDownBetweenThem() throws Exception {
        generatorWritesPdf();

        crawler.generateCVForJob(new JobInfo("First Job", "Acme"), "First description.");
        crawler.generateCVForJob(new JobInfo("Second Job", "Acme"), "Second description.");

        assertThat(crawler.generatorsCreated).isEqualTo(1);
        verify(crawler.generator, times(2)).generate();
        verify(crawler.generator, never()).shutdown();
    }

    @Test
    void emptyJobContentSkipsGenerationWithoutCreatingAGenerator() {
        assertThat(crawler.generateCVForJob(new JobInfo("Java Developer", "Acme"), "  ")).isNull();
        assertThat(crawler.generateCVForJob(new JobInfo("Java Developer", "Acme"), null)).isNull();

        assertThat(crawler.generatorsCreated).isZero();
    }

    @Test
    void closeShutsTheGeneratorDownOnce() throws Exception {
        generatorWritesPdf();
        crawler.generateCVForJob(new JobInfo("Java Developer", "Acme"), "We need a Java developer.");

        crawler.close();
        crawler.close();

        verify(crawler.generator, times(1)).shutdown();
    }

    @Test
    void closeWithoutAnyGenerationDoesNotCreateAGenerator() {
        crawler.close();

        assertThat(crawler.generatorsCreated).isZero();
    }

    @Test
    void closeSurvivesAFailingGeneratorShutdown() throws Exception {
        generatorWritesPdf();
        crawler.generateCVForJob(new JobInfo("Java Developer", "Acme"), "We need a Java developer.");
        doThrow(new IllegalStateException("executor stuck")).when(crawler.generator).shutdown();

        assertThatCode(crawler::close).doesNotThrowAnyException();
    }
}
