package com.cvmaker.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.CrawlerFactory.CrawlerType;
import com.cvmaker.crawler.form.FormCrawler;
import com.cvmaker.crawler.generic.GenericCrawler;
import com.cvmaker.crawler.reed.ReedCrawler;

class CrawlerFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void createsTheCrawlerClassForEachType() throws Exception {
        CrawlerConfig config = CrawlerTestSupport.crawlerConfig(tempDir);

        assertThat(CrawlerFactory.createCrawler(CrawlerType.REED, config)).isExactlyInstanceOf(ReedCrawler.class);
        assertThat(CrawlerFactory.createCrawler(CrawlerType.FORM, config)).isExactlyInstanceOf(FormCrawler.class);
        assertThat(CrawlerFactory.createCrawler(CrawlerType.GENERIC, config)).isExactlyInstanceOf(GenericCrawler.class);
    }

    @ParameterizedTest
    @EnumSource(CrawlerType.class)
    void everyTypeGivesACrawlerThatUsesTheGivenConfigAndHasNoBrowserYet(CrawlerType type) throws Exception {
        CrawlerConfig config = CrawlerTestSupport.crawlerConfig(tempDir);

        JobCrawler crawler = CrawlerFactory.createCrawler(type, config);

        assertThat(crawler.getCrawlerName()).isNotBlank();
        assertThat(crawler).isInstanceOf(AbstractJobCrawler.class);
        AbstractJobCrawler base = (AbstractJobCrawler) crawler;
        assertThat(base.getCrawlerConfig()).isSameAs(config);
        // Creating a crawler must not launch anything; that is setupBrowser()'s job.
        assertThat(base.getPage()).isNull();
        assertThat(base.getPlaywright()).isNull();
        // Closing a crawler that never opened a browser is harmless.
        crawler.close();
    }

    @Test
    void setMaxApplicationsReachesTheConfig() throws Exception {
        CrawlerConfig config = CrawlerTestSupport.crawlerConfig(tempDir);
        JobCrawler crawler = CrawlerFactory.createCrawler(CrawlerType.REED, config);

        crawler.setMaxApplications(3);

        assertThat(config.getMaxApplications()).isEqualTo(3);
    }

    @Test
    void nullTypeIsRejectedWithAClearMessage() throws Exception {
        CrawlerConfig config = CrawlerTestSupport.crawlerConfig(tempDir);

        assertThatThrownBy(() -> CrawlerFactory.createCrawler(null, config))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("crawler type");
    }
}
