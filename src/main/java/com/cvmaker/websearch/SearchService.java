package com.cvmaker.websearch;

import java.io.IOException;
import java.util.List;

public class SearchService {

    private final ExaSearchClient searchClient;

    public SearchService(String apiKey) {
        this.searchClient = new ExaSearchClient(apiKey);
    }

    public List<SearchResult> search(String query) throws IOException {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .numResults(10)
                .build();
        return searchClient.search(request);
    }

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
