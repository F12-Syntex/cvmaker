package com.cvmaker.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Reads the JSON object out of an LLM answer.
 *
 * <p>Models rarely return bare JSON. They wrap it in a Markdown code fence, put a
 * sentence before or after it, leave a trailing comma, or use single quotes. This class
 * finds the object and parses it with Jackson, so callers never use regex or
 * {@code indexOf("{")} on model output. All methods are static, pure and null-safe.
 */
public final class LlmJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .build();

    /** The contents of a Markdown code fence, with or without a language tag. */
    private static final Pattern CODE_FENCE = Pattern.compile("```[A-Za-z0-9_-]*\\s*(.*?)```", Pattern.DOTALL);

    private LlmJson() {
    }

    /**
     * Finds and parses the first JSON object in an LLM answer.
     * Code fences are searched first, because text around them may hold example braces;
     * then the whole answer. Within a text the first balanced {@code {...}} that parses
     * as an object wins, so its nested objects stay nested.
     *
     * @param answer the raw model output; may be null
     * @return the object, or empty when the answer holds no complete JSON object
     */
    public static Optional<JsonNode> extractObject(String answer) {
        if (answer == null || answer.isBlank()) {
            return Optional.empty();
        }
        Matcher fence = CODE_FENCE.matcher(answer);
        while (fence.find()) {
            Optional<JsonNode> fenced = firstObjectIn(fence.group(1));
            if (fenced.isPresent()) {
                return fenced;
            }
        }
        return firstObjectIn(answer);
    }

    /**
     * Returns a field as display text.
     * <ul>
     * <li>missing field, JSON {@code null}, blank text and the word "null": Java null</li>
     * <li>text, number, boolean: its text, trimmed</li>
     * <li>array: its items joined with ", "</li>
     * <li>object: "key: value" pairs joined with "; "</li>
     * </ul>
     * The field name is matched exactly first, then ignoring case and separators, so
     * "company_name" answers a request for "companyName".
     *
     * @param object a parsed object; may be null
     * @param field  the field name
     */
    public static String text(JsonNode object, String field) {
        String rendered = render(find(object, field));
        if (rendered == null || rendered.isBlank() || rendered.trim().equalsIgnoreCase("null")) {
            return null;
        }
        return rendered.trim();
    }

    /**
     * Returns a field as a boolean. Accepts JSON booleans and the texts
     * true/false/yes/no in any case.
     *
     * @return the value, or null when the field is missing or is not a boolean
     */
    public static Boolean bool(JsonNode object, String field) {
        JsonNode node = find(object, field);
        if (node == null) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            switch (node.textValue().trim().toLowerCase(Locale.ROOT)) {
                case "true":
                case "yes":
                    return Boolean.TRUE;
                case "false":
                case "no":
                    return Boolean.FALSE;
                default:
                    return null;
            }
        }
        return null;
    }

    /**
     * Returns a field as a number. Accepts JSON numbers and numeric text ("0.9").
     *
     * @return the value, or null when the field is missing or is not a number
     */
    public static Double number(JsonNode object, String field) {
        JsonNode node = find(object, field);
        if (node == null) {
            return null;
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        if (node.isTextual()) {
            try {
                double value = Double.parseDouble(node.textValue().trim());
                return Double.isFinite(value) ? value : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Turns the top-level fields of an object into a string map, in the order the model
     * wrote them. Text, numbers and booleans become their text. Nested objects and arrays
     * become compact JSON, so their structure survives. Null fields are left out.
     *
     * @param object a parsed object; may be null
     * @return a new, modifiable map; empty when {@code object} is not an object
     */
    public static Map<String, String> toStringMap(JsonNode object) {
        Map<String, String> result = new LinkedHashMap<>();
        if (object == null || !object.isObject()) {
            return result;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            if (value == null || value.isNull() || value.isMissingNode()) {
                continue;
            }
            result.put(entry.getKey(), value.isContainerNode() ? value.toString() : value.asText());
        }
        return result;
    }

    // ---- internals ----

    /** Tries each '{' from the left until one starts a balanced span that parses as an object. */
    private static Optional<JsonNode> firstObjectIn(String text) {
        int start = text.indexOf('{');
        while (start >= 0) {
            int end = matchingBrace(text, start);
            if (end > start) {
                try {
                    JsonNode node = MAPPER.readTree(text.substring(start, end + 1));
                    if (node != null && node.isObject()) {
                        return Optional.of(node);
                    }
                } catch (JsonProcessingException e) {
                    // Not JSON (for example "{see below}"); try the next brace.
                }
            }
            start = text.indexOf('{', start + 1);
        }
        return Optional.empty();
    }

    /**
     * Returns the index of the '}' that closes the '{' at {@code open}, or -1 when the
     * text ends first. Braces inside quoted strings do not count, and a backslash
     * escapes the next character inside a string.
     */
    private static int matchingBrace(String text, int open) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Exact field first, then a field whose name differs only in case or separators. */
    private static JsonNode find(JsonNode object, String field) {
        if (object == null || !object.isObject() || field == null) {
            return null;
        }
        JsonNode exact = object.get(field);
        if (exact != null) {
            return exact;
        }
        String wanted = normalise(field);
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (normalise(entry.getKey()).equals(wanted)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String normalise(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String render(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isArray()) {
            List<String> items = new ArrayList<>();
            for (JsonNode item : node) {
                String text = render(item);
                if (text != null && !text.isBlank()) {
                    items.add(text.trim());
                }
            }
            return items.isEmpty() ? null : String.join(", ", items);
        }
        if (node.isObject()) {
            List<String> pairs = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String text = render(entry.getValue());
                if (text != null && !text.isBlank()) {
                    pairs.add(entry.getKey() + ": " + text.trim());
                }
            }
            return pairs.isEmpty() ? null : String.join("; ", pairs);
        }
        return node.asText();
    }
}
