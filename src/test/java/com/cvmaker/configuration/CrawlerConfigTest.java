package com.cvmaker.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrawlerConfigTest {

    @TempDir
    Path tempDir;

    private CrawlerConfig load(String... lines) throws Exception {
        Path file = tempDir.resolve("testcrawler.properties");
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return new CrawlerConfig(file.toString());
    }

    @Test
    void missingFileUsesDefaults() throws Exception {
        CrawlerConfig config = new CrawlerConfig(tempDir.resolve("absent.properties").toString());

        assertThat(config.getMaxApplications()).isEqualTo(10);
        assertThat(config.getPageTimeout()).isEqualTo(60000);
        assertThat(config.isHeadless()).isFalse();
    }

    @Test
    void defaultCvConfigFilePointsAtRealConfigurationFile() throws Exception {
        CrawlerConfig config = new CrawlerConfig(tempDir.resolve("absent.properties").toString());

        assertThat(config.getCvConfigFile()).isEqualTo("configuration/config.properties");
    }

    @Test
    void malformedIntegerFallsBackToDefaultInsteadOfThrowing() throws Exception {
        CrawlerConfig config = load(
                "crawler.max.applications=abc",
                "timing.page.timeout=60s",
                "browser.viewport.width=");

        assertThat(config.getMaxApplications()).isEqualTo(10);
        assertThat(config.getPageTimeout()).isEqualTo(60000);
        assertThat(config.getViewportWidth()).isEqualTo(1920);
    }

    @Test
    void numbersWithSurroundingWhitespaceAreAccepted() throws Exception {
        CrawlerConfig config = load(
                "crawler.max.applications= 150 ",
                "timing.page.timeout=5000   ");

        assertThat(config.getMaxApplications()).isEqualTo(150);
        assertThat(config.getPageTimeout()).isEqualTo(5000);
    }

    @Test
    void negativeDelaysAndSizesFallBackToDefault() throws Exception {
        CrawlerConfig config = load(
                "timing.page.load.delay=-1",
                "browser.slow.mo=-100",
                "browser.viewport.height=0",
                "crawler.polling.rate=0");

        assertThat(config.getPageLoadDelay()).isEqualTo(3000);
        assertThat(config.getSlowMo()).isEqualTo(1000);
        assertThat(config.getViewportHeight()).isEqualTo(1080);
        assertThat(config.getPollingRate()).isEqualTo(500);
    }

    @Test
    void booleansAreTrimmedAndCaseInsensitive() throws Exception {
        CrawlerConfig config = load(
                "browser.headless= TRUE ",
                "crawler.debug.mode=True");

        assertThat(config.isHeadless()).isTrue();
        assertThat(config.isDebugMode()).isTrue();
    }

    @Test
    void misspelledBooleanFallsBackToDefault() throws Exception {
        CrawlerConfig config = load(
                "crawler.visualization.enabled=flase",
                "browser.headless=ture");

        assertThat(config.isVisualizationEnabled()).isTrue();
        assertThat(config.isHeadless()).isFalse();
    }

    @Test
    void crawlingSpeedIsStillClampedToOneToTen() throws Exception {
        assertThat(load("crawler.speed=0").getCrawlingSpeed()).isEqualTo(1);
        assertThat(load("crawler.speed=99").getCrawlingSpeed()).isEqualTo(10);
        assertThat(load("crawler.speed=fast").getCrawlingSpeed()).isEqualTo(5);
    }

    @Test
    void stringValuesAreTrimmed() throws Exception {
        CrawlerConfig config = load(
                "cv.config.file=configuration/autoapplication.config.properties   ",
                "browser.data.dir= reed-session ");

        assertThat(config.getCvConfigFile()).isEqualTo("configuration/autoapplication.config.properties");
        assertThat(config.getBrowserDataDir()).isEqualTo("reed-session");
    }
}
