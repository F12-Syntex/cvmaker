package com.cvmaker.crawler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cvmaker.configuration.CrawlerConfig;
import com.cvmaker.crawler.reed.ReedCrawler;

/**
 * Manager class for handling multiple crawler instances.
 */
public class CrawlerManager {

    /** How long shutdown() waits for cancelled crawler threads to finish on their own. */
    private static final long SHUTDOWN_WAIT_SECONDS = 10;

    private ExecutorService executorService;
    private List<Future<?>> crawlerTasks;
    private List<JobCrawler> activeCrawlers;

    // Crawlers that are already closed. Compared by identity, because crawler
    // classes may define equals/hashCode over mutable state.
    private final Set<JobCrawler> closedCrawlers
            = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    public CrawlerManager() {
        this.executorService = Executors.newCachedThreadPool();
        this.crawlerTasks = new ArrayList<>();
        this.activeCrawlers = new ArrayList<>();
    }

    /**
     * Add a crawler instance to be managed.
     *
     * @param crawler The crawler to add
     * @return The added crawler for method chaining
     */
    public JobCrawler addCrawler(JobCrawler crawler) {
        activeCrawlers.add(crawler);
        return crawler;
    }

    /**
     * Create and add a Reed crawler with default configuration.
     *
     * @return The created crawler
     * @throws Exception If crawler creation fails
     */
    public JobCrawler addReedCrawler() throws Exception {
        JobCrawler crawler = new ReedCrawler();
        activeCrawlers.add(crawler);
        return crawler;
    }

    /**
     * Create and add a Reed crawler with custom configuration.
     *
     * @param config The configuration to use
     * @return The created crawler
     * @throws Exception If crawler creation fails
     */
    public JobCrawler addReedCrawler(CrawlerConfig config) throws Exception {
        JobCrawler crawler = new ReedCrawler(config);
        activeCrawlers.add(crawler);
        return crawler;
    }

    /**
     * Start a specific crawler in a new thread.
     *
     * @param crawler The crawler to start
     * @return Future representing the running task
     */
    public Future<?> startCrawler(JobCrawler crawler) {
        // Track crawlers that were started without addCrawler(), so that
        // shutdown() also closes them.
        if (!isManaged(crawler)) {
            activeCrawlers.add(crawler);
        }

        Future<?> task = executorService.submit(() -> {
            // The crawler is (re)opened by this run, so it needs closing again.
            closedCrawlers.remove(crawler);
            try {
                crawler.initialize();
                crawler.setupBrowser();
                crawler.openForLogin();
                crawler.processJobsAndApply();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                closeOnce(crawler);
            }
        });

        crawlerTasks.add(task);
        return task;
    }

    /**
     * Start all added crawlers in separate threads.
     */
    public void startAllCrawlers() {
        for (JobCrawler crawler : activeCrawlers) {
            startCrawler(crawler);
        }
    }

    /**
     * Stop all crawlers and clean up resources. Each crawler is closed exactly
     * once: by its own thread when its run ends, or here if it never ran or
     * did not stop in time.
     */
    public void shutdown() {
        // Cancel all tasks
        for (Future<?> task : crawlerTasks) {
            if (!task.isDone()) {
                task.cancel(true);
            }
        }

        // Shutdown executor and give running crawlers the chance to close their
        // browser on their own thread (Playwright objects are not thread-safe).
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                System.out.println("Crawler threads did not stop within " + SHUTDOWN_WAIT_SECONDS
                        + "s; closing their browsers anyway");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Close whatever is still open
        for (JobCrawler crawler : activeCrawlers) {
            closeOnce(crawler);
        }
    }

    private boolean isManaged(JobCrawler crawler) {
        for (JobCrawler managed : activeCrawlers) {
            if (managed == crawler) {
                return true;
            }
        }
        return false;
    }

    /**
     * Close a crawler unless it is already closed. A failing close is reported
     * and does not stop the other crawlers from being closed.
     */
    private void closeOnce(JobCrawler crawler) {
        if (!closedCrawlers.add(crawler)) {
            return;
        }
        try {
            crawler.close();
        } catch (Exception e) {
            System.out.println("Error closing " + crawler.getCrawlerName() + ": " + e.getMessage());
        }
    }

    /**
     * Wait for all crawler tasks to complete.
     */
    public void waitForCompletion() {
        for (Future<?> task : crawlerTasks) {
            try {
                task.get(); // Wait for task to complete
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
