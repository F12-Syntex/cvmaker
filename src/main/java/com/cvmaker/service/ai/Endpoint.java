package com.cvmaker.service.ai;

public enum Endpoint {
    OPENAI_API("https://api.openai.com/v1"),
    ANTHROPIC_API("https://api.anthropic.com/v1"),
    OLLAMA_API("http://localhost:1234/v1"),
    CUSTOM;

    private String url;

    Endpoint(String url) {
        this.url = url;
    }

    Endpoint() {
        // For CUSTOM endpoint
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
