package com.cvmaker.service.ai;

import java.time.Duration;

/**
 * How {@link AiService} runs one query: how long it waits for an answer, how
 * often it tries again after a failure, how long it waits between attempts,
 * and an optional limit on generated tokens. Instances are immutable.
 *
 * <p>
 * To use the values from {@code config.properties} ({@code ai.timeout_seconds},
 * {@code ai.max_retries}, {@code ai.request_delay_ms}):
 * <pre>
 * new AiService(config.getAiModel(), config.getAiTemperature(),
 *         AiOptions.of(config.getAiTimeoutSeconds(), config.getAiMaxRetries(), config.getAiRequestDelayMs()));
 * </pre>
 */
public final class AiOptions {

    /**
     * Used when the caller gives no options. Long enough for a reasoning model
     * to write a full CV.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(300);
    public static final int DEFAULT_MAX_RETRIES = 2;
    public static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(1);

    /**
     * Upper bound for the wait between two attempts, however many attempts
     * have failed.
     */
    static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

    private final Duration timeout;
    private final int maxRetries;
    private final Duration retryDelay;
    private final Integer maxTokens;

    private AiOptions(Builder builder) {
        this.timeout = builder.timeout;
        this.maxRetries = builder.maxRetries;
        this.retryDelay = builder.retryDelay;
        this.maxTokens = builder.maxTokens;
    }

    /**
     * Options for callers that configure nothing: a 300 second timeout, 2
     * retries, 1 second before the first retry, no token limit.
     */
    public static AiOptions defaults() {
        return builder().build();
    }

    /**
     * Options from the three {@code ai.*} performance settings.
     *
     * @param timeoutSeconds time allowed for one attempt; 0 or less means no
     * timeout
     * @param maxRetries extra attempts after the first one fails; 0 or less
     * means a single attempt
     * @param retryDelayMs wait before the first retry, doubled for each
     * further retry; 0 or less means no wait
     */
    public static AiOptions of(int timeoutSeconds, int maxRetries, int retryDelayMs) {
        return builder()
                .timeout(Duration.ofSeconds(Math.max(0, timeoutSeconds)))
                .maxRetries(maxRetries)
                .retryDelay(Duration.ofMillis(Math.max(0, retryDelayMs)))
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Time allowed for one attempt. {@link Duration#ZERO} means no timeout.
     */
    public Duration getTimeout() {
        return timeout;
    }

    public boolean hasTimeout() {
        return !timeout.isZero();
    }

    /**
     * Extra attempts after the first one fails.
     */
    public int getMaxRetries() {
        return maxRetries;
    }

    /**
     * Wait before the first retry.
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }

    /**
     * Limit on generated tokens, or null to send no limit.
     */
    public Integer getMaxTokens() {
        return maxTokens;
    }

    /**
     * Wait after the given failed attempt (1 for the first): the retry delay,
     * doubled for each earlier failure and capped at 30 seconds.
     */
    long backoffMillis(int failedAttempt) {
        long base = retryDelay.toMillis();
        if (base <= 0) {
            return 0;
        }
        int doublings = Math.min(Math.max(failedAttempt - 1, 0), 20);
        return Math.min(base << doublings, MAX_RETRY_DELAY.toMillis());
    }

    @Override
    public String toString() {
        return "AiOptions{timeout=" + (hasTimeout() ? timeout : "none")
                + ", maxRetries=" + maxRetries
                + ", retryDelay=" + retryDelay
                + ", maxTokens=" + (maxTokens == null ? "none" : maxTokens) + "}";
    }

    public static final class Builder {

        private Duration timeout = DEFAULT_TIMEOUT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private Duration retryDelay = DEFAULT_RETRY_DELAY;
        private Integer maxTokens;

        private Builder() {
        }

        /**
         * Time allowed for one attempt. Null, zero or negative means no
         * timeout.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = positiveOrZero(timeout);
            return this;
        }

        /**
         * Extra attempts after the first one fails. A negative value counts as
         * 0.
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = Math.max(0, maxRetries);
            return this;
        }

        /**
         * Wait before the first retry. Null, zero or negative means no wait.
         */
        public Builder retryDelay(Duration retryDelay) {
            this.retryDelay = positiveOrZero(retryDelay);
            return this;
        }

        /**
         * Limit on generated tokens. Zero or a negative value means no limit.
         * For OpenAI reasoning models the limit includes hidden reasoning
         * tokens, so leave room for them.
         */
        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens > 0 ? maxTokens : null;
            return this;
        }

        public AiOptions build() {
            return new AiOptions(this);
        }

        private static Duration positiveOrZero(Duration value) {
            return value == null || value.isNegative() ? Duration.ZERO : value;
        }
    }
}
