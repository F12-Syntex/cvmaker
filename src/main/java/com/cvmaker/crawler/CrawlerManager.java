package com.cvmaker.crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.cvmaker.configuration.CrawlerConfig;

/**
 * Manager class for handling multiple crawler instances.
 */
public class CrawlerManager {
    
    private ExecutorService executorService;
    private List<Future<?>> crawlerTasks;
    private List<JobCrawler> activeCrawlers;
    
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
        Future<?> task = executorService.submit(() -> {
            try {
                crawler.initialize();
                crawler.setupBrowser();
                crawler.openForLogin();
                crawler.processJobsAndApply();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                crawler.close();
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
     * Stop all crawlers and clean up resources.
     */
    public void shutdown() {
        // Cancel all tasks
        for (Future<?> task : crawlerTasks) {
            if (!task.isDone()) {
                task.cancel(true);
            }
        }
        
        // Close all crawlers
        for (JobCrawler crawler : activeCrawlers) {
            crawler.close();
        }
        
        // Shutdown executor
        executorService.shutdownNow();
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