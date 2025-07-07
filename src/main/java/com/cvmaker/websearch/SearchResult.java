package com.cvmaker.websearch;

public class SearchResult {

    private final String title;
    private final String url;
    private final String snippet;
    private final String publishedDate;

    public SearchResult(String title, String url, String snippet, String publishedDate) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.publishedDate = publishedDate;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getSnippet() {
        return snippet;
    }

    public String getPublishedDate() {
        return publishedDate;
    }
}
