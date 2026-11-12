package com.cvmaker.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrawlerConfigBrowserArgsTest {

    private static final String NO_SANDBOX = "--no-sandbox";
    private static final String NO_WEB_SECURITY = "--disable-web-security";

    @TempDir
    Path tempDir;

    private CrawlerConfig load(String... lines) throws Exception {
        Path file = tempDir.resolve("testcrawler.properties");
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return new CrawlerConfig(file.toString());
    }

    /** Split exactly as AbstractJobCrawler does before passing args to Chromium. */
    private static List<String> launchArgs(CrawlerConfig config) {
        return Arrays.asList(config.getBrowserArgs().split(","));
    }

    @Test
    void defaultsDoNotIncludeUnsafeFlags() throws Exception {
        CrawlerConfig config = new CrawlerConfig(tempDir.resolve("absent.properties").toString());

        assertThat(launchArgs(config)).doesNotContain(NO_SANDBOX, NO_WEB_SECURITY)
                .contains("--disable-blink-features=AutomationControlled", "--disable-dev-shm-usage");
        assertThat(config.isUnsafeBrowserArgsEnabled()).isFalse();
    }

    @Test
    void unsafeFlagsInBrowserArgsAreDroppedWithoutOptIn() throws Exception {
        CrawlerConfig config = load(
                "browser.args=--disable-dev-shm-usage, --no-sandbox ,--disable-web-security,--lang=en-GB");

        assertThat(launchArgs(config)).containsExactly("--disable-dev-shm-usage", "--lang=en-GB");
    }

    @Test
    void unsafeFlagsWithValuesAreAlsoDroppedWithoutOptIn() throws Exception {
        CrawlerConfig config = load("browser.args=--no-sandbox=1,--disable-web-security=true,--mute-audio");

        assertThat(launchArgs(config)).containsExactly("--mute-audio");
    }

    @Test
    void misspelledOptInStaysOff() throws Exception {
        CrawlerConfig config = load(
                "browser.unsafe.args.enabled=yes please",
                "browser.args=--no-sandbox");

        assertThat(config.isUnsafeBrowserArgsEnabled()).isFalse();
        assertThat(launchArgs(config)).doesNotContain(NO_SANDBOX);
    }

    @Test
    void explicitOptInAddsUnsafeFlagsOnce() throws Exception {
        CrawlerConfig config = load(
                "browser.unsafe.args.enabled=true",
                "browser.args=--disable-dev-shm-usage,--no-sandbox");

        assertThat(config.isUnsafeBrowserArgsEnabled()).isTrue();
        assertThat(launchArgs(config)).containsExactly("--disable-dev-shm-usage", NO_SANDBOX, NO_WEB_SECURITY);
    }

    @Test
    void setterCannotBypassTheOptIn() throws Exception {
        CrawlerConfig config = new CrawlerConfig(tempDir.resolve("absent.properties").toString());

        config.setBrowserArgs("--no-sandbox,--disable-web-security,--mute-audio");

        assertThat(launchArgs(config)).containsExactly("--mute-audio");
        assertThat(config.getBrowserArgList()).containsExactly("--mute-audio");
    }

    @Test
    void emptyBrowserArgsGiveEmptyList() throws Exception {
        CrawlerConfig config = load("browser.args=");

        assertThat(config.getBrowserArgs()).isEmpty();
        assertThat(config.getBrowserArgList()).isEmpty();
    }

    @Test
    void shippedReedConfigDoesNotEnableUnsafeFlags() throws Exception {
        Path shipped = Paths.get("configuration/reedcrawler.properties");
        assertThat(shipped).exists();

        String text = Files.readString(shipped, StandardCharsets.UTF_8);
        CrawlerConfig config = new CrawlerConfig(shipped.toString());

        assertThat(text).contains("browser.unsafe.args.enabled=false");
        assertThat(config.isUnsafeBrowserArgsEnabled()).isFalse();
        assertThat(launchArgs(config)).doesNotContain(NO_SANDBOX, NO_WEB_SECURITY);
        // the property line itself must not list the unsafe flags
        assertThat(text.lines().filter(l -> l.startsWith("browser.args=")))
                .singleElement().asString().doesNotContain(NO_SANDBOX).doesNotContain(NO_WEB_SECURITY);
        // other user settings are kept
        assertThat(config.getMaxApplications()).isEqualTo(150);
        assertThat(config.getSearchKeywords()).isEqualTo("labour");
    }
}
