package com.cvmaker.crawler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.cvmaker.configuration.CrawlerConfig;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * Shared fixtures for crawler tests: a crawler configuration that lives entirely
 * in a temp directory, and a mocked Playwright object graph so that no browser
 * is launched.
 */
public final class CrawlerTestSupport {

    private CrawlerTestSupport() {
    }

    /**
     * Write a crawler config, the CV config it points to and a user data file
     * into {@code dir}, and load the crawler config. Output goes to {@code dir/out}.
     */
    public static CrawlerConfig crawlerConfig(Path dir) throws IOException {
        return crawlerConfig(dir, "");
    }

    /**
     * Same as {@link #crawlerConfig(Path)}, with extra lines appended to the
     * crawler properties file.
     */
    public static CrawlerConfig crawlerConfig(Path dir, String extraCrawlerProperties) throws IOException {
        Path userData = dir.resolve("userdata.md");
        Files.writeString(userData, "Test User\n");

        Path cvConfig = dir.resolve("cv.properties");
        Files.writeString(cvConfig, String.join("\n",
                "input.user.data.file=" + propertyPath(userData),
                "input.cv.prompt.file=" + propertyPath(dir.resolve("missing_cv_prompt.md")),
                "input.cover.letter.prompt.file=" + propertyPath(dir.resolve("missing_cover_prompt.md")),
                "output.directory=" + propertyPath(dir.resolve("out")),
                "output.pdf.name=cv.pdf",
                ""));

        Path crawlerConfig = dir.resolve("crawler.properties");
        Files.writeString(crawlerConfig, String.join("\n",
                "cv.config.file=" + propertyPath(cvConfig),
                "browser.data.dir=" + propertyPath(dir.resolve("session")),
                extraCrawlerProperties,
                ""));

        return new CrawlerConfig(crawlerConfig.toString());
    }

    private static String propertyPath(Path path) {
        // Backslashes are escape characters in .properties files.
        return path.toString().replace('\\', '/');
    }

    /**
     * A mocked Playwright whose Chromium launch returns {@link #context}, which
     * in turn returns {@link #page} from {@code newPage()}.
     */
    public static final class MockBrowser {

        public final Playwright playwright = mock(Playwright.class);
        public final BrowserType chromium = mock(BrowserType.class);
        public final BrowserContext context = mock(BrowserContext.class);
        public final Page page = mock(Page.class);

        public MockBrowser() {
            when(playwright.chromium()).thenReturn(chromium);
            when(chromium.launchPersistentContext(any(Path.class), any())).thenReturn(context);
            when(context.newPage()).thenReturn(page);
        }
    }
}
