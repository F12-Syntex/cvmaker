package com.cvmaker.websearch;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class SearchService {

    private final ExaSearchClient searchClient;

    public SearchService(String apiKey) {
        this(new ExaSearchClient(apiKey));
    }

    /**
     * Uses the given client. The service owns it from here on:
     * {@link #shutdown()} shuts it down.
     */
    public SearchService(ExaSearchClient searchClient) {
        this.searchClient = Objects.requireNonNull(searchClient, "searchClient");
    }

    public List<SearchResult> search(String query) throws IOException {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .numResults(10)
                .build();
        return searchClient.search(request);
    }

    /**
     * Searches with explicit options.
     *
     * @param numResults how many results to ask for, 1 to 100
     * @param includeDomains comma-separated domains to restrict the search to,
     * or null or empty for no restriction
     * @param excludeDomains comma-separated domains to leave out, or null or
     * empty for none
     */
    public List<SearchResult> searchWithOptions(String query, int numResults,
            String includeDomains, String excludeDomains) throws IOException {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .numResults(numResults)
                .includeDomains(includeDomains)
                .excludeDomains(excludeDomains)
                .build();
        return searchClient.search(request);
    }

    public void shutdown() {
        searchClient.shutdown();
    }
}
