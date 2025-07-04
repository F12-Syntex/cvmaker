package com.cvmaker.service.ai;

public class LLMResponse {

    private final String content;
    private final UsageStats usage;
    private final String model;
    private final double promptLatency;

    public LLMResponse(Builder builder) {
        this.content = builder.content;
        this.usage = builder.usage;
        this.model = builder.model;
        this.promptLatency = builder.promptLatency;
    }

    public String getContent() {
        return content;
    }

    public UsageStats getUsage() {
        return usage;
    }

    public String getModel() {
        return model;
    }

    public double getPromptLatency() {
        return promptLatency;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String content;
        private UsageStats usage;
        private String model;
        private double promptLatency;

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder usage(UsageStats usage) {
            this.usage = usage;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder promptLatency(double promptLatency) {
            this.promptLatency = promptLatency;
            return this;
        }

        public LLMResponse build() {
            return new LLMResponse(this);
        }
    }
}
