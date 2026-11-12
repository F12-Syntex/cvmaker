package com.cvmaker.crawler.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.microsoft.playwright.Page;

/**
 * CSV-backed cache of the form inputs (input, select, textarea) seen on pages
 * visited by a crawler. One CSV file per crawler, stored under {@code cache/}
 * relative to the working directory, with the columns in {@link #HEADER}.
 * Field values are never recorded, only the field descriptors.
 *
 * <p>The file is UTF-8 and RFC 4180 quoted. It is loaded when an instance is
 * constructed (a missing file means an empty cache) and rewritten under the
 * instance lock whenever new rows are added. Duplicate rows are ignored.
 *
 * <p>NOTE: this class was reconstructed from its call sites on 2026-11-12
 * because the original source was git-ignored (the {@code cache/} pattern in
 * .gitignore) and never committed. The only known call site is
 * {@code AbstractJobCrawler}, which uses
 * {@link #saveInputCache(String, Page)}. If the owner's local copy differs, the
 * local copy should replace this file.
 */
public class PageInputCacheManagerCSV {

    /** Directory, relative to the working directory, holding the cache files. */
    public static final Path DEFAULT_CACHE_DIR = Paths.get("cache");

    /** CSV columns, in file order. */
    public static final List<String> HEADER =
            List.of("url", "tag", "type", "name", "id", "placeholder", "label");

    private static final Map<Path, PageInputCacheManagerCSV> INSTANCES = new ConcurrentHashMap<>();

    private static final String EXTRACT_INPUTS_JS = "() => Array.from(document.querySelectorAll('input, select, textarea'))"
            + ".filter(e => (e.getAttribute('type') || '').toLowerCase() !== 'hidden')"
            + ".map(e => {"
            + "  let l = null;"
            + "  if (e.id) { try { l = document.querySelector('label[for=\"' + CSS.escape(e.id) + '\"]'); } catch (x) {} }"
            + "  if (!l) { l = e.closest('label'); }"
            + "  return {"
            + "    tag: e.tagName.toLowerCase(),"
            + "    type: e.getAttribute('type') || '',"
            + "    name: e.getAttribute('name') || '',"
            + "    id: e.id || '',"
            + "    placeholder: e.getAttribute('placeholder') || '',"
            + "    label: l ? (l.textContent || '').trim() : ''"
            + "  };"
            + "})";

    private final Path file;
    private final Set<List<String>> rows = new LinkedHashSet<>();

    /**
     * Creates a cache backed by the given CSV file and loads its rows. A
     * missing file yields an empty cache; the file is created on first write.
     *
     * @param file the CSV file
     * @throws IOException if the file exists but cannot be read
     */
    public PageInputCacheManagerCSV(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            List<List<String>> records = parse(Files.readString(file, StandardCharsets.UTF_8));
            boolean first = true;
            for (List<String> record : records) {
                if (first) {
                    first = false;
                    if (record.equals(HEADER)) {
                        continue;
                    }
                }
                rows.add(normalize(record));
            }
        }
    }

    /**
     * Extracts the visible form inputs of {@code page} and appends any new ones
     * to the crawler's cache file ({@code cache/<crawlerName>_page_inputs.csv}).
     * Intended for Playwright load listeners: it never throws, errors are
     * logged to stderr.
     *
     * @param crawlerName the crawler name, used for the file name
     * @param page the loaded page
     */
    public static void saveInputCache(String crawlerName, Page page) {
        if (page == null) {
            return;
        }
        try {
            Object result = page.evaluate(EXTRACT_INPUTS_JS);
            List<Map<String, String>> inputs = new ArrayList<>();
            if (result instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, String> input = new LinkedHashMap<>();
                        map.forEach((k, v) -> input.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                        inputs.add(input);
                    }
                }
            }
            forCrawler(crawlerName).addInputs(page.url(), inputs);
        } catch (Exception e) {
            System.err.println("Failed to cache page inputs for " + crawlerName + ": " + e.getMessage());
        }
    }

    /**
     * Returns the shared cache instance for a crawler, backed by
     * {@code cache/<crawlerName>_page_inputs.csv}.
     *
     * @param crawlerName the crawler name
     * @return the shared instance
     * @throws IOException if an existing cache file cannot be read
     */
    public static PageInputCacheManagerCSV forCrawler(String crawlerName) throws IOException {
        String safe = (crawlerName == null || crawlerName.isBlank() ? "default" : crawlerName)
                .replaceAll("[^A-Za-z0-9._-]", "_");
        Path path = DEFAULT_CACHE_DIR.resolve(safe + "_page_inputs.csv").toAbsolutePath().normalize();
        try {
            return INSTANCES.computeIfAbsent(path, p -> {
                try {
                    return new PageInputCacheManagerCSV(p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Adds the inputs found on {@code url} and rewrites the file if anything
     * new was added. Each input map is read by the {@link #HEADER} keys other
     * than {@code url}; missing keys become empty strings.
     *
     * @param url the page URL
     * @param inputs the input descriptors
     * @return the number of rows that were new
     * @throws IOException if the file cannot be written
     */
    public synchronized int addInputs(String url, List<Map<String, String>> inputs) throws IOException {
        int added = 0;
        for (Map<String, String> input : inputs) {
            List<String> row = new ArrayList<>(HEADER.size());
            row.add(url == null ? "" : url);
            for (String column : HEADER.subList(1, HEADER.size())) {
                String value = input.get(column);
                row.add(value == null ? "" : value);
            }
            if (rows.add(List.copyOf(row))) {
                added++;
            }
        }
        if (added > 0) {
            write();
        }
        return added;
    }

    /**
     * Returns the cached inputs for a URL, as maps keyed by {@link #HEADER}.
     *
     * @param url the page URL
     * @return the inputs, in insertion order; empty if none
     */
    public synchronized List<Map<String, String>> getInputs(String url) {
        List<Map<String, String>> result = new ArrayList<>();
        for (List<String> row : rows) {
            if (row.get(0).equals(url)) {
                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < HEADER.size(); i++) {
                    map.put(HEADER.get(i), row.get(i));
                }
                result.add(map);
            }
        }
        return result;
    }

    /** @return true if at least one input is cached for {@code url} */
    public synchronized boolean contains(String url) {
        return rows.stream().anyMatch(r -> r.get(0).equals(url));
    }

    /** @return an immutable snapshot of all rows, without the header */
    public synchronized List<List<String>> getRows() {
        return Collections.unmodifiableList(new ArrayList<>(rows));
    }

    /** @return the number of cached rows */
    public synchronized int size() {
        return rows.size();
    }

    /** @return the backing CSV file */
    public Path getFile() {
        return file;
    }

    private void write() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(toCsvLine(HEADER)).append("\r\n");
        for (List<String> row : rows) {
            sb.append(toCsvLine(row)).append("\r\n");
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, sb, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<String> normalize(List<String> record) {
        List<String> row = new ArrayList<>(HEADER.size());
        for (int i = 0; i < HEADER.size(); i++) {
            row.add(i < record.size() ? record.get(i) : "");
        }
        return List.copyOf(row);
    }

    /**
     * Formats one CSV record per RFC 4180: a field is quoted when it contains
     * a comma, double quote, CR or LF, and embedded quotes are doubled.
     */
    static String toCsvLine(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String f = fields.get(i) == null ? "" : fields.get(i);
            if (f.indexOf(',') >= 0 || f.indexOf('"') >= 0 || f.indexOf('\n') >= 0 || f.indexOf('\r') >= 0) {
                sb.append('"').append(f.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(f);
            }
        }
        return sb.toString();
    }

    /**
     * Parses RFC 4180 CSV text (CRLF or LF line endings, quoted fields may
     * contain separators, doubled quotes and line breaks). Blank lines are
     * skipped.
     */
    static List<List<String>> parse(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        int n = text.length();
        int i = 0;
        if (n > 0 && text.charAt(0) == '﻿') {
            i = 1;
        }
        for (; i < n; i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                fieldStarted = true;
            } else if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
                fieldStarted = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (fieldStarted || field.length() > 0 || !record.isEmpty()) {
                    record.add(field.toString());
                    records.add(record);
                }
                record = new ArrayList<>();
                field.setLength(0);
                fieldStarted = false;
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        if (fieldStarted || field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }
}
