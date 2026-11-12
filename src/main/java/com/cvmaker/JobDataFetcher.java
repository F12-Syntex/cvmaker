package com.cvmaker;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JobDataFetcher {

    /** Descriptions shorter than this are treated as "nothing found" (likely a JS-only page). */
    static final int MIN_DESCRIPTION_LENGTH = 80;

    /** Upper bound on the description passed to the AI, to keep prompts bounded. */
    static final int MAX_DESCRIPTION_LENGTH = 20_000;

    static final String UNKNOWN_TITLE = "Unknown Position";
    static final String UNKNOWN_COMPANY = "Unknown Company";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Private-use character marking line breaks while jsoup flattens text. */
    private static final String LINE_BREAK = "\uE000";

    /** Fetches the HTML of a page; the seam that keeps tests offline. */
    @FunctionalInterface
    public interface PageFetcher {
        String fetch(URI uri) throws IOException;
    }

    public static class JobData {
        private final String jobTitle;
        private final String companyName;
        private final String jobDescription;
        private final String jobName;

        public JobData(String jobTitle, String companyName, String jobDescription) {
            this.jobTitle = jobTitle;
            this.companyName = companyName;
            this.jobDescription = jobDescription;
            this.jobName = generateJobName(jobTitle, companyName);
        }

        static String generateJobName(String title, String company) {
            String raw = (title == null ? "" : title) + "_" + (company == null ? "" : company);
            String safeName = raw
                .replaceAll("[^a-zA-Z0-9\\s_-]", "")
                .trim()
                .replaceAll("\\s+", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^[_-]+|[_-]+$", "")
                .toLowerCase(Locale.ROOT);
            if (safeName.length() > 50) {
                safeName = safeName.substring(0, 50).replaceAll("[_-]+$", "");
            }
            return safeName.isEmpty() ? "job" : safeName;
        }

        public String getJobTitle() { return jobTitle; }
        public String getCompanyName() { return companyName; }
        public String getJobDescription() { return jobDescription; }
        public String getJobName() { return jobName; }

        @Override
        public String toString() {
            return "JobData{title='" + jobTitle + "', company='" + companyName + "', jobName='" + jobName
                + "', descriptionLength=" + (jobDescription == null ? 0 : jobDescription.length()) + "}";
        }
    }

    private final PageFetcher pageFetcher;

    public JobDataFetcher() {
        this(new HttpPageFetcher());
    }

    public JobDataFetcher(PageFetcher pageFetcher) {
        this.pageFetcher = pageFetcher;
    }

    public JobData fetchJobData(String source) throws IOException {
        if (source == null || source.trim().isEmpty()) {
            throw new IOException("No job source given (input.job.url is empty)");
        }
        String trimmed = source.trim();
        if (isHttpUrl(trimmed)) {
            return fetchFromUrl(trimmed);
        }
        return fetchFromFile(trimmed);
    }

    /** True for absolute http(s) URLs with a host; anything else is treated as a file path. */
    static boolean isHttpUrl(String source) {
        if (source == null) {
            return false;
        }
        try {
            URI uri = new URI(source.trim());
            String scheme = uri.getScheme();
            return scheme != null
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private JobData fetchFromFile(String filePath) throws IOException {
        Path path;
        try {
            path = Paths.get(filePath);
        } catch (RuntimeException e) {
            throw new IOException("Not a valid file path or http(s) URL: " + filePath, e);
        }

        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }

        String content = Files.readString(path);

        String jobTitle = extractJobTitle(content);
        String companyName = extractCompanyName(content);

        return new JobData(jobTitle, companyName, content);
    }

    private JobData fetchFromUrl(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid URL format: " + url, e);
        }
        String html = pageFetcher.fetch(uri);
        return parseJobPage(html, url);
    }

    /**
     * Extract title, company and description from a job page. JSON-LD {@code JobPosting} wins; then
     * microdata, {@code og:title}/{@code <title>}/{@code <h1>}, and the main page text.
     *
     * @throws IOException if no usable description can be found (for example a page rendered by
     *                     JavaScript), so no CV is generated for an empty or made-up job
     */
    static JobData parseJobPage(String html, String url) throws IOException {
        Document doc = Jsoup.parse(html == null ? "" : html, url == null ? "" : url);

        String title = null;
        String company = null;
        String location = null;
        String description = null;

        JsonNode posting = findJobPosting(doc);
        if (posting != null) {
            title = text(posting.get("title"));
            company = organizationName(posting.get("hiringOrganization"));
            location = locationText(posting.get("jobLocation"));
            String descHtml = text(posting.get("description"));
            if (descHtml != null) {
                description = htmlToText(descHtml);
            }
        }

        if (title == null) {
            title = firstText(doc, "[itemprop=title]");
        }
        if (company == null) {
            company = firstText(doc, "[itemprop=hiringOrganization] [itemprop=name]");
        }
        if (title == null || company == null) {
            String[] fromHeading = splitPageTitle(pageTitle(doc), siteName(doc), hostOf(url));
            if (title == null) {
                title = fromHeading[0];
            }
            if (company == null) {
                company = fromHeading[1];
            }
        }
        if (description == null || description.length() < MIN_DESCRIPTION_LENGTH) {
            String fallback = mainText(doc);
            if (fallback != null && (description == null || fallback.length() > description.length())) {
                description = fallback;
            }
        }

        if (description == null || description.length() < MIN_DESCRIPTION_LENGTH) {
            throw new IOException("Could not find a job description at " + url
                + ". The page may need JavaScript or a login; save the description to a text file"
                + " and set input.job.description.file to it (leave input.job.url empty) instead.");
        }

        title = title == null ? UNKNOWN_TITLE : title;
        company = company == null ? UNKNOWN_COMPANY : company;
        return new JobData(title, company, buildDescription(title, company, location, url, description));
    }

    private static String buildDescription(String title, String company, String location, String url,
            String description) {
        StringBuilder sb = new StringBuilder();
        sb.append("Title: ").append(title).append('\n');
        sb.append("Company: ").append(company).append('\n');
        if (location != null) {
            sb.append("Location: ").append(location).append('\n');
        }
        if (url != null) {
            sb.append("Source: ").append(url).append('\n');
        }
        sb.append('\n');
        String body = description.length() > MAX_DESCRIPTION_LENGTH
            ? description.substring(0, MAX_DESCRIPTION_LENGTH) + "\n[truncated]"
            : description;
        sb.append(body);
        return sb.toString();
    }

    // ---- JSON-LD ----------------------------------------------------------------------------

    private static JsonNode findJobPosting(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            String json = script.data();
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                JsonNode found = findJobPosting(JSON.readTree(json));
                if (found != null) {
                    return found;
                }
            } catch (IOException e) {
                // Malformed JSON-LD is common; try the next block
            }
        }
        return null;
    }

    private static JsonNode findJobPosting(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findJobPosting(child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (!node.isObject()) {
            return null;
        }
        if (hasType(node.get("@type"), "JobPosting")) {
            return node;
        }
        return findJobPosting(node.get("@graph"));
    }

    private static boolean hasType(JsonNode type, String wanted) {
        if (type == null) {
            return false;
        }
        if (type.isArray()) {
            for (JsonNode t : type) {
                if (hasType(t, wanted)) {
                    return true;
                }
            }
            return false;
        }
        String value = type.asText("");
        return value.equalsIgnoreCase(wanted) || value.endsWith("/" + wanted);
    }

    private static String organizationName(JsonNode org) {
        if (org == null) {
            return null;
        }
        if (org.isArray()) {
            return org.isEmpty() ? null : organizationName(org.get(0));
        }
        if (org.isObject()) {
            return text(org.get("name"));
        }
        return text(org);
    }

    private static String locationText(JsonNode location) {
        if (location == null) {
            return null;
        }
        if (location.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode l : location) {
                String text = locationText(l);
                if (text != null) {
                    parts.add(text);
                }
            }
            return parts.isEmpty() ? null : String.join("; ", parts);
        }
        JsonNode address = location.isObject() ? location.get("address") : location;
        if (address == null) {
            return null;
        }
        if (!address.isObject()) {
            return text(address);
        }
        List<String> parts = new ArrayList<>();
        for (String field : new String[] {"addressLocality", "addressRegion", "addressCountry"}) {
            JsonNode value = address.get(field);
            String text = value != null && value.isObject() ? text(value.get("name")) : text(value);
            if (text != null) {
                parts.add(text);
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isContainerNode()) {
            return null;
        }
        return clean(node.asText());
    }

    // ---- HTML fallbacks -----------------------------------------------------------------------

    private static String pageTitle(Document doc) {
        String og = metaContent(doc, "meta[property=og:title]");
        if (og != null) {
            return og;
        }
        String title = clean(doc.title());
        if (title != null) {
            return title;
        }
        return firstText(doc, "h1");
    }

    private static String siteName(Document doc) {
        return metaContent(doc, "meta[property=og:site_name]");
    }

    private static String metaContent(Document doc, String selector) {
        Element meta = doc.selectFirst(selector);
        return meta == null ? null : clean(meta.attr("content"));
    }

    private static String firstText(Document doc, String selector) {
        Element el = doc.selectFirst(selector);
        return el == null ? null : clean(el.text());
    }

    /**
     * Split a page title such as "Engineer at Acme | Jobs" or "Engineer - Acme - Reed.co.uk" into
     * {title, company}. A trailing segment that names the site (og:site_name or the URL host) is
     * dropped. The company is null when it cannot be told apart from the site name.
     */
    static String[] splitPageTitle(String pageTitle, String siteName, String host) {
        if (pageTitle == null) {
            return new String[] {null, null};
        }
        List<String> segments = new ArrayList<>();
        for (String s : pageTitle.split("\\s+[|\u2013\u2014-]\\s+")) {
            String c = clean(s);
            if (c != null) {
                segments.add(c);
            }
        }
        while (segments.size() > 1 && namesSite(segments.get(segments.size() - 1), siteName, host)) {
            segments.remove(segments.size() - 1);
        }
        if (segments.isEmpty()) {
            return new String[] {null, null};
        }

        String first = segments.get(0);
        String[] atSplit = first.split("\\s+at\\s+", 2);
        if (atSplit.length == 2 && clean(atSplit[0]) != null && clean(atSplit[1]) != null) {
            return new String[] {clean(atSplit[0]), clean(atSplit[1])};
        }
        String company = segments.size() >= 2 ? segments.get(1) : null;
        return new String[] {first, company};
    }

    private static boolean namesSite(String segment, String siteName, String host) {
        String s = segment.toLowerCase(Locale.ROOT);
        if (siteName != null && s.equals(siteName.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        String compact = s.replaceAll("[^a-z0-9.]", "");
        if (compact.equals(h)) {
            return true;
        }
        for (String label : h.split("\\.")) {
            if (label.length() >= 3 && !GENERIC_HOST_LABELS.contains(label)
                && (compact.equals(label) || compact.startsWith(label + "."))) {
                return true;
            }
        }
        return false;
    }

    private static final Set<String> GENERIC_HOST_LABELS =
        Set.of("www", "com", "org", "net", "gov", "edu", "co", "uk");

    private static String hostOf(String url) {
        if (url == null) {
            return null;
        }
        try {
            return new URI(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /** Text of the most specific job-description container, else the page body without chrome. */
    private static String mainText(Document doc) {
        Document copy = doc.clone();
        copy.select("script, style, noscript, template, svg, nav, header, footer, form, iframe").remove();
        String[] selectors = {
            "[itemprop=description]",
            "#job-description, .job-description, .jobDescription, .job_description, [data-testid=job-description]",
            "article",
            "main",
            "[role=main]",
        };
        for (String selector : selectors) {
            Elements found = copy.select(selector);
            if (!found.isEmpty()) {
                String text = htmlToText(found.first().html());
                if (text != null && text.length() >= MIN_DESCRIPTION_LENGTH) {
                    return text;
                }
            }
        }
        return copy.body() == null ? null : htmlToText(copy.body().html());
    }

    /** Convert an HTML fragment to plain text, keeping paragraph and list breaks as new lines. */
    static String htmlToText(String html) {
        if (html == null) {
            return null;
        }
        String source = html;
        // JSON-LD descriptions are sometimes entity-escaped HTML ("&lt;p&gt;...")
        if (source.contains("&lt;") && !source.contains("<")) {
            source = Parser.unescapeEntities(source, false);
        }
        Document fragment = Jsoup.parseBodyFragment(source);
        fragment.select("br").before(LINE_BREAK);
        fragment.select("p, div, li, h1, h2, h3, h4, h5, h6, tr, ul, ol").before(LINE_BREAK);
        fragment.select("li").prepend("- ");
        String text = fragment.body().text().replace(LINE_BREAK, "\n");
        String normalised = text
            .replaceAll("[ \\t\\x0B\\f\\r\u00a0]+", " ")
            .replaceAll(" *\n *", "\n")
            .replaceAll("\n{2,}", "\n")
            .trim();
        return normalised.isEmpty() ? null : normalised;
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String c = s.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        return c.isEmpty() ? null : c;
    }

    // ---- File mode ----------------------------------------------------------------------------

    private String extractJobTitle(String content) {
        // Simple extraction - you may want to improve this
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("title:") ||
                line.toLowerCase().contains("position:") ||
                line.toLowerCase().contains("role:")) {
                return line.substring(line.indexOf(":") + 1).trim();
            }
        }
        return "Software Developer"; // Default
    }

    private String extractCompanyName(String content) {
        // Simple extraction - you may want to improve this
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("company:") ||
                line.toLowerCase().contains("employer:")) {
                return line.substring(line.indexOf(":") + 1).trim();
            }
        }
        return "Unknown Company"; // Default
    }

    // ---- Network ------------------------------------------------------------------------------

    /** Real fetcher using {@code java.net.http}: follows redirects, times out, requires a 2xx. */
    static final class HttpPageFetcher implements PageFetcher {
        private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
        private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

        private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        @Override
        public String fetch(URI uri) throws IOException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (compatible; cvmaker job fetcher)")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en")
                .GET()
                .build();
            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching " + uri, e);
            }
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Fetching " + uri + " failed with HTTP " + status);
            }
            return response.body();
        }
    }
}
