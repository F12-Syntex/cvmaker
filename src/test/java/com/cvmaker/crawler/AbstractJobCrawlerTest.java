package com.cvmaker.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.CrawlerTestSupport.MockBrowser;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;

class AbstractJobCrawlerTest {

    @TempDir
    Path tempDir;

    /** Concrete crawler running against a mocked browser. */
    private static class TestCrawler extends AbstractJobCrawler {

        final MockBrowser browser = new MockBrowser();
        RuntimeException playwrightStartFailure;

        TestCrawler(CrawlerConfig config) throws Exception {
            super(config);
        }

        @Override
        protected Playwright createPlaywright() {
            if (playwrightStartFailure != null) {
                throw playwrightStartFailure;
            }
            return browser.playwright;
        }

        @Override
        public void processJobsAndApply() {
        }

        @Override
        public String getCrawlerName() {
            return "Test";
        }
    }

    private TestCrawler newCrawler() throws Exception {
        return new TestCrawler(CrawlerTestSupport.crawlerConfig(tempDir));
    }

    // ---- adjustedDelay ----

    @Test
    void adjustedDelayMatchesSharedCrawlDelays() throws Exception {
        TestCrawler crawler = newCrawler();
        for (int speed = 1; speed <= 10; speed++) {
            crawler.getCrawlerConfig().setCrawlingSpeed(speed);
            assertThat(crawler.adjustedDelay(3000)).isEqualTo(CrawlDelays.adjustedDelay(3000, speed));
        }
    }

    @Test
    void adjustedDelayClampsSpeedSetAboveTenInsteadOfCollapsingToFloor() throws Exception {
        TestCrawler crawler = newCrawler();
        crawler.getCrawlerConfig().setCrawlingSpeed(20);

        assertThat(crawler.adjustedDelay(3000)).isEqualTo(900);
    }

    // ---- setupBrowser ----

    @Test
    void setupBrowserOpensAPageOnTheConfiguredSession() throws Exception {
        TestCrawler crawler = newCrawler();

        crawler.setupBrowser();

        assertThat(crawler.getPlaywright()).isSameAs(crawler.browser.playwright);
        assertThat(crawler.getContext()).isSameAs(crawler.browser.context);
        assertThat(crawler.getPage()).isSameAs(crawler.browser.page);
        verify(crawler.browser.chromium).launchPersistentContext(
                org.mockito.ArgumentMatchers.eq(tempDir.resolve("session")), any());
    }

    @Test
    void setupBrowserRethrowsLaunchFailureAndReleasesPlaywright() throws Exception {
        TestCrawler crawler = newCrawler();
        PlaywrightException launchFailure = new PlaywrightException("Executable doesn't exist");
        when(crawler.browser.chromium.launchPersistentContext(any(Path.class), any())).thenThrow(launchFailure);

        assertThatThrownBy(crawler::setupBrowser).isSameAs(launchFailure);

        // Nothing is left half-open, and the driver process is not leaked.
        verify(crawler.browser.playwright).close();
        assertThat(crawler.getPlaywright()).isNull();
        assertThat(crawler.getContext()).isNull();
        assertThat(crawler.getPage()).isNull();
    }

    @Test
    void setupBrowserClosesContextWhenPageCreationFails() throws Exception {
        TestCrawler crawler = newCrawler();
        when(crawler.browser.context.newPage()).thenThrow(new PlaywrightException("Target closed"));

        assertThatThrownBy(crawler::setupBrowser).isInstanceOf(PlaywrightException.class);

        verify(crawler.browser.context).close();
        verify(crawler.browser.playwright).close();
        assertThat(crawler.getContext()).isNull();
    }

    @Test
    void setupBrowserRethrowsWhenPlaywrightItselfCannotStart() throws Exception {
        TestCrawler crawler = newCrawler();
        crawler.playwrightStartFailure = new PlaywrightException("Failed to create driver");

        assertThatThrownBy(crawler::setupBrowser).isSameAs(crawler.playwrightStartFailure);

        assertThat(crawler.getPlaywright()).isNull();
        assertThatCode(crawler::close).doesNotThrowAnyException();
    }

    @Test
    void openForLoginWithoutABrowserFailsWithAClearMessage() throws Exception {
        TestCrawler crawler = newCrawler();

        assertThatThrownBy(crawler::openForLogin)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("setupBrowser()");
    }

    // ---- visualizer ----

    @Test
    void visualizerIsANoOpBeforeSetupBrowser() throws Exception {
        TestCrawler crawler = newCrawler();

        assertThat(crawler.getVisualizer()).isNotNull();
        assertThat(crawler.getVisualizer().isEnabled()).isFalse();
    }

    @Test
    void setupBrowserCreatesAnEnabledVisualizerWhenConfigured() throws Exception {
        TestCrawler crawler = new TestCrawler(
                CrawlerTestSupport.crawlerConfig(tempDir, "crawler.visualization.enabled=true"));

        crawler.setupBrowser();

        assertThat(crawler.getVisualizer().isEnabled()).isTrue();
        verify(crawler.browser.page).addInitScript(PageVisualizer.INIT_SCRIPT);
    }

    @Test
    void setupBrowserLeavesTheVisualizerOffWhenDisabledInConfig() throws Exception {
        TestCrawler crawler = new TestCrawler(
                CrawlerTestSupport.crawlerConfig(tempDir, "crawler.visualization.enabled=false"));

        crawler.setupBrowser();

        assertThat(crawler.getVisualizer().isEnabled()).isFalse();
        verify(crawler.browser.page, never()).addInitScript(PageVisualizer.INIT_SCRIPT);
    }

    @Test
    void closeSwitchesTheVisualizerOffAgain() throws Exception {
        TestCrawler crawler = new TestCrawler(
                CrawlerTestSupport.crawlerConfig(tempDir, "crawler.visualization.enabled=true"));
        crawler.setupBrowser();

        crawler.close();

        assertThat(crawler.getVisualizer().isEnabled()).isFalse();
    }

    // ---- close ----

    @Test
    void closeBeforeSetupBrowserDoesNothing() throws Exception {
        TestCrawler crawler = newCrawler();

        assertThatCode(crawler::close).doesNotThrowAnyException();
    }

    @Test
    void closeReleasesEverythingAndClearsTheFields() throws Exception {
        TestCrawler crawler = newCrawler();
        crawler.setupBrowser();

        crawler.close();

        verify(crawler.browser.page).close();
        verify(crawler.browser.context).close();
        verify(crawler.browser.playwright).close();
        assertThat(crawler.getPage()).isNull();
        assertThat(crawler.getContext()).isNull();
        assertThat(crawler.getPlaywright()).isNull();
    }

    @Test
    void closeKeepsGoingWhenOneResourceFailsToClose() throws Exception {
        TestCrawler crawler = newCrawler();
        crawler.setupBrowser();
        doThrow(new PlaywrightException("Target page already closed")).when(crawler.browser.page).close();
        doThrow(new PlaywrightException("Context already closed")).when(crawler.browser.context).close();

        assertThatCode(crawler::close).doesNotThrowAnyException();

        verify(crawler.browser.context).close();
        verify(crawler.browser.playwright).close();
    }

    @Test
    void closeIsIdempotent() throws Exception {
        TestCrawler crawler = newCrawler();
        crawler.setupBrowser();

        crawler.close();
        crawler.close();

        verify(crawler.browser.page, times(1)).close();
        verify(crawler.browser.context, times(1)).close();
        verify(crawler.browser.playwright, times(1)).close();
    }

    @Test
    void launchArgsKeepAnArgumentWithCommasWhole() {
        CrawlerConfig config = org.mockito.Mockito.mock(CrawlerConfig.class);
        when(config.getBrowserArgList())
                .thenReturn(java.util.List.of("--disable-features=A,B", "--disable-dev-shm-usage"));

        assertThat(AbstractJobCrawler.launchArgs(config))
                .containsExactly("--disable-features=A,B", "--disable-dev-shm-usage");
    }
}
