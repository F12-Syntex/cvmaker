package com.cvmaker.crawler;

/**
 * Pure delay arithmetic shared by the crawlers.
 *
 * <p>A configured base delay is scaled by the crawling speed (1 = slowest, 10 = fastest) with the
 * multiplier {@code 1.5 - 0.12 * speed}: speed 1 waits 1.38x the base delay, speed 5 waits 0.9x and
 * speed 10 waits 0.3x. The result never drops below {@link #MIN_DELAY_MS}.
 */
public final class CrawlDelays {

    /** Lowest delay ever returned, in milliseconds. */
    public static final int MIN_DELAY_MS = 100;

    /** Slowest crawling speed. Lower configured values are treated as this. */
    public static final int MIN_SPEED = 1;

    /** Fastest crawling speed. Higher configured values are treated as this. */
    public static final int MAX_SPEED = 10;

    private CrawlDelays() {
    }

    /**
     * Scale a base delay by the crawling speed.
     *
     * @param baseDelayMs   the configured delay in milliseconds
     * @param crawlingSpeed the configured speed; values outside 1..10 are clamped into that range
     * @return the delay to wait, in milliseconds, at least {@link #MIN_DELAY_MS}
     */
    public static int adjustedDelay(int baseDelayMs, int crawlingSpeed) {
        int speed = clampSpeed(crawlingSpeed);
        // Same float arithmetic as the original AbstractJobCrawler.adjustedDelay, so delegating to
        // this method does not change any delay for speeds 1..10. Math.round(float) saturates at
        // Integer.MAX_VALUE, so very large delays cannot overflow.
        float speedMultiplier = 1.5f - (speed * 0.12f);
        return Math.max(MIN_DELAY_MS, Math.round(baseDelayMs * speedMultiplier));
    }

    /**
     * Clamp a configured crawling speed into {@link #MIN_SPEED}..{@link #MAX_SPEED}.
     *
     * @param crawlingSpeed the configured speed
     * @return the speed actually used for delay scaling
     */
    public static int clampSpeed(int crawlingSpeed) {
        return Math.max(MIN_SPEED, Math.min(MAX_SPEED, crawlingSpeed));
    }
}
