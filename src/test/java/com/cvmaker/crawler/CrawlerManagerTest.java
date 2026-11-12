package com.cvmaker.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.InOrder;

@Timeout(30)
class CrawlerManagerTest {

    @Test
    void runsTheLifecycleInOrderAndClosesTheCrawlerExactlyOnce() throws Exception {
        JobCrawler crawler = mock(JobCrawler.class);
        CrawlerManager manager = new CrawlerManager();
        manager.addCrawler(crawler);

        manager.startCrawler(crawler);
        manager.waitForCompletion();
        manager.shutdown();

        InOrder order = inOrder(crawler);
        order.verify(crawler).initialize();
        order.verify(crawler).setupBrowser();
        order.verify(crawler).openForLogin();
        order.verify(crawler).processJobsAndApply();
        order.verify(crawler).close();
        // The task's finally block and shutdown() used to close it twice.
        verify(crawler, times(1)).close();
    }

    @Test
    void failedBrowserSetupSkipsTheRestAndStillCloses() throws Exception {
        JobCrawler crawler = mock(JobCrawler.class);
        doThrow(new IllegalStateException("launch failed")).when(crawler).setupBrowser();
        CrawlerManager manager = new CrawlerManager();

        manager.startCrawler(crawler);
        manager.waitForCompletion();
        manager.shutdown();

        verify(crawler, never()).openForLogin();
        verify(crawler, never()).processJobsAndApply();
        verify(crawler, times(1)).close();
    }

    @Test
    void shutdownClosesCrawlersThatWereNeverStarted() {
        JobCrawler crawler = mock(JobCrawler.class);
        CrawlerManager manager = new CrawlerManager();
        manager.addCrawler(crawler);

        manager.shutdown();
        manager.shutdown();

        verify(crawler, times(1)).close();
    }

    @Test
    void oneFailingCloseDoesNotStopTheOthersFromClosing() {
        JobCrawler failing = mock(JobCrawler.class);
        JobCrawler healthy = mock(JobCrawler.class);
        doThrow(new IllegalStateException("close failed")).when(failing).close();
        CrawlerManager manager = new CrawlerManager();
        manager.addCrawler(failing);
        manager.addCrawler(healthy);

        assertThatCode(manager::shutdown).doesNotThrowAnyException();

        verify(failing, times(1)).close();
        verify(healthy, times(1)).close();
    }

    @Test
    void shutdownLetsARunningCrawlerCloseItselfOnItsOwnThread() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        Thread[] closingThread = new Thread[1];
        JobCrawler crawler = new StubCrawler() {
            @Override
            public void processJobsAndApply() {
                running.countDown();
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(20));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public void close() {
                closeCalls++;
                closingThread[0] = Thread.currentThread();
            }
        };
        CrawlerManager manager = new CrawlerManager();

        manager.startCrawler(crawler);
        assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();
        manager.shutdown();

        assertThat(((StubCrawler) crawler).closeCalls).isEqualTo(1);
        assertThat(closingThread[0]).isNotNull().isNotSameAs(Thread.currentThread());
    }

    /** Minimal crawler whose steps do nothing. */
    private static class StubCrawler implements JobCrawler {

        volatile int closeCalls;

        @Override
        public void initialize() {
        }

        @Override
        public void setupBrowser() {
        }

        @Override
        public void openForLogin() {
        }

        @Override
        public void processJobsAndApply() {
        }

        @Override
        public void close() {
            closeCalls++;
        }

        @Override
        public void setMaxApplications(int maxApplications) {
        }

        @Override
        public String getCrawlerName() {
            return "Stub";
        }
    }
}
