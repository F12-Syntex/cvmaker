package com.cvmaker.service.ai;

public class LLMRequest {

    /**
     * Limit reported by {@link #getMaxTokens()} when the caller set none.
     */
    public static final int DEFAULT_MAX_TOKENS = 2048;

    private final String prompt;
    private final String model;
    private final double temperature;
    private final Integer maxTokens;

    private LLMRequest(Builder builder) {
        this.prompt = builder.prompt;
        this.model = builder.model;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getModel() {
        return model;
    }

    public double getTemperature() {
        return temperature;
    }

    /**
     * The token limit to use: the value given to the builder, or
     * {@link #DEFAULT_MAX_TOKENS} when none was given. A client that must not
     * apply a default checks {@link #hasMaxTokens()} first.
     */
    public int getMaxTokens() {
        return maxTokens != null ? maxTokens : DEFAULT_MAX_TOKENS;
    }

    /**
     * True when the caller set a token limit. The OpenAI client sends a limit
     * only in that case: for reasoning models the limit also covers hidden
     * reasoning tokens, so a silent default would cut answers short or leave
     * them empty.
     */
    public boolean hasMaxTokens() {
        return maxTokens != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String prompt;
        private String model;
        private double temperature = 0.7;
        private Integer maxTokens;

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Sets the limit on generated tokens. Zero or a negative value means
         * "no explicit limit" and clears an earlier value.
         */
        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens > 0 ? maxTokens : null;
            return this;
        }

        public LLMRequest build() {
            return new LLMRequest(this);
        }
    }
}
