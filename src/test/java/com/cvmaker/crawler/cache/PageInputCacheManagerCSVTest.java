package com.cvmaker.crawler.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PageInputCacheManagerCSVTest {

    @TempDir
    Path tempDir;

    private static Map<String, String> input(String tag, String type, String name, String id,
            String placeholder, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("tag", tag);
        m.put("type", type);
        m.put("name", name);
        m.put("id", id);
        m.put("placeholder", placeholder);
        m.put("label", label);
        return m;
    }

    @Test
    void missingFileGivesEmptyCacheAndCreatesFileOnWrite() throws Exception {
        Path file = tempDir.resolve("sub/dir/inputs.csv");

        PageInputCacheManagerCSV cache = new PageInputCacheManagerCSV(file);

        assertThat(cache.size()).isZero();
        assertThat(cache.getInputs("https://example.com")).isEmpty();
        assertThat(file).doesNotExist();

        cache.addInputs("https://example.com", List.of(input("input", "text", "q", "", "", "")));
        assertThat(file).exists();
    }

    @Test
    void roundTripsRowsThroughTheFile() throws Exception {
        Path file = tempDir.resolve("inputs.csv");
        PageInputCacheManagerCSV cache = new PageInputCacheManagerCSV(file);

        int added = cache.addInputs("https://example.com/apply", List.of(
                input("input", "email", "email", "email", "you@example.com", "Email"),
                input("textarea", "", "cover", "", "", "Cover letter")));
        int duplicates = cache.addInputs("https://example.com/apply", List.of(
                input("input", "email", "email", "email", "you@example.com", "Email")));

        assertThat(added).isEqualTo(2);
        assertThat(duplicates).isZero();

        PageInputCacheManagerCSV reloaded = new PageInputCacheManagerCSV(file);
        assertThat(reloaded.size()).isEqualTo(2);
        assertThat(reloaded.contains("https://example.com/apply")).isTrue();
        assertThat(reloaded.getInputs("https://example.com/apply"))
                .extracting(m -> m.get("name"))
                .containsExactly("email", "cover");
        assertThat(Files.readString(file, StandardCharsets.UTF_8))
                .startsWith("url,tag,type,name,id,placeholder,label\r\n");
    }

    @Test
    void quotesCommasQuotesNewlinesAndUnicode() throws Exception {
        Path file = tempDir.resolve("inputs.csv");
        String url = "https://example.com/a?x=1,2";
        String placeholder = "Say \"hello\", then\nnew line\r\nand CRLF";
        String label = "Prénom, 名前";

        new PageInputCacheManagerCSV(file)
                .addInputs(url, List.of(input("input", "text", "first,name", "", placeholder, label)));

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(content).contains("\"Say \"\"hello\"\", then\nnew line\r\nand CRLF\"");
        assertThat(content).contains("\"first,name\"");

        List<Map<String, String>> inputs = new PageInputCacheManagerCSV(file).getInputs(url);
        assertThat(inputs).hasSize(1);
        assertThat(inputs.get(0))
                .containsEntry("url", url)
                .containsEntry("name", "first,name")
                .containsEntry("placeholder", placeholder)
                .containsEntry("label", label)
                .containsEntry("id", "");
    }
}
