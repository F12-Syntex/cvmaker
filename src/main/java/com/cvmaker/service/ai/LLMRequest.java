package com.cvmaker.service.ai;

public class LLMRequest {

    private final String prompt;
    private final String model;
    private final double temperature;
    private final int maxTokens;

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

    public int getMaxTokens() {
        return maxTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String prompt;
        private String model;
        private double temperature = 0.7;
        private int maxTokens = 2048;

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

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public LLMRequest build() {
            return new LLMRequest(this);
        }
    }
}
