package com.cvmaker.crawler.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.CrawlerTestSupport;
import com.cvmaker.crawler.CrawlerTestSupport.MockBrowser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

class GenericCrawlerTest {

    @TempDir
    Path tempDir;

    /** GenericCrawler with the browser, the AI services and stdin replaced. */
    private static final class TestCrawler extends GenericCrawler {

        final MockBrowser browser = new MockBrowser();
        final FormAnalyzer analyzer = mock(FormAnalyzer.class);
        final FormFiller filler = mock(FormFiller.class);
        final List<Page> analyzerPages = new ArrayList<>();
        final List<Page> fillerPages = new ArrayList<>();
        int enterPrompts;

        TestCrawler(CrawlerConfig config) throws Exception {
            super(config);
        }

        @Override
        protected Playwright createPlaywright() {
            return browser.playwright;
        }

        @Override
        protected FormAnalyzer createAnalyzer(Page page) {
            analyzerPages.add(page);
            return analyzer;
        }

        @Override
        protected FormFiller createFiller(Page page) {
            fillerPages.add(page);
            return filler;
        }

        @Override
        protected void waitForEnter() {
            enterPrompts++;
        }
    }

    @Test
    void constructorDoesNotBuildFormServicesBeforeThePageExists() throws Exception {
        TestCrawler crawler = new TestCrawler(CrawlerTestSupport.crawlerConfig(tempDir));

        assertThat(crawler.getPage()).isNull();
        assertThat(crawler.analyzerPages).isEmpty();
        assertThat(crawler.fillerPages).isEmpty();
    }

    @Test
    void setupBrowserWiresAnalyzerAndFillerToTheLivePage() throws Exception {
        TestCrawler crawler = new TestCrawler(CrawlerTestSupport.crawlerConfig(tempDir));

        crawler.setupBrowser();

        assertThat(crawler.getPage()).isSameAs(crawler.browser.page);
        assertThat(crawler.analyzerPages).containsExactly(crawler.browser.page);
        assertThat(crawler.fillerPages).containsExactly(crawler.browser.page);
    }

    @Test
    void processJobsAndApplyAnalyzesThenFillsWithTheAnalyzedContext() throws Exception {
        TestCrawler crawler = new TestCrawler(CrawlerTestSupport.crawlerConfig(tempDir));
        Map<String, String> context = Map.of("email", "someone@example.com");
        when(crawler.analyzer.getFormContext()).thenReturn(context);

        crawler.setupBrowser();
        crawler.processJobsAndApply();

        assertThat(crawler.enterPrompts).isEqualTo(1);
        InOrder order = inOrder(crawler.analyzer, crawler.filler);
        order.verify(crawler.analyzer).analyzeForm();
        order.verify(crawler.filler).fillForm(context);
    }

    @Test
    void processJobsAndApplyBeforeSetupBrowserDoesNothing() throws Exception {
        TestCrawler crawler = new TestCrawler(CrawlerTestSupport.crawlerConfig(tempDir));

        crawler.processJobsAndApply();

        assertThat(crawler.enterPrompts).isZero();
        verifyNoInteractions(crawler.analyzer, crawler.filler);
    }
}
