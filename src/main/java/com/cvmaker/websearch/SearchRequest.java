package com.cvmaker.websearch;

public class SearchRequest {

    private final String query;
    private final int numResults;
    private final String includeDomains;
    private final String excludeDomains;
    private final boolean useAutoprompt;

    private SearchRequest(Builder builder) {
        this.query = builder.query;
        this.numResults = builder.numResults;
        this.includeDomains = builder.includeDomains;
        this.excludeDomains = builder.excludeDomains;
        this.useAutoprompt = builder.useAutoprompt;
    }

    public String getQuery() {
        return query;
    }

    public int getNumResults() {
        return numResults;
    }

    public String getIncludeDomains() {
        return includeDomains;
    }

    public String getExcludeDomains() {
        return excludeDomains;
    }

    public boolean isUseAutoprompt() {
        return useAutoprompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String query;
        private int numResults = 10;
        private String includeDomains = "";
        private String excludeDomains = "";
        private boolean useAutoprompt = true;

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder numResults(int numResults) {
            this.numResults = numResults;
            return this;
        }

        public Builder includeDomains(String includeDomains) {
            this.includeDomains = includeDomains;
            return this;
        }

        public Builder excludeDomains(String excludeDomains) {
            this.excludeDomains = excludeDomains;
            return this;
        }

        public Builder useAutoprompt(boolean useAutoprompt) {
            this.useAutoprompt = useAutoprompt;
            return this;
        }

        public SearchRequest build() {
            return new SearchRequest(this);
        }
    }
}
