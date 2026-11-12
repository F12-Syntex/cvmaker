package com.cvmaker.crawler.generic.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.cvmaker.util.LlmJson;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Utility for parsing AI JSON responses into usable field mappings.
 */
public class AIResponseParser {

    /**
     * Extracts and parses the JSON object in an AI response string.
     * Code fences and text around the object are ignored. A nested object or array is
     * kept as compact JSON text, so its structure is not lost. Fields that are null are
     * left out.
     *
     * @param aiResponse Raw response from AI service; may be null
     * @return Parsed field-value map; empty (and modifiable) when the response holds no
     *         JSON object
     */
    public static Map<String, String> parse(String aiResponse) {
        Map<String, String> context = new HashMap<>();
        if (aiResponse == null || aiResponse.isEmpty()) {
            return context;
        }

        Optional<JsonNode> json = LlmJson.extractObject(aiResponse);
        if (json.isEmpty()) {
            System.out.println("Warning: Could not find a JSON object in the AI response");
            return context;
        }

        context.putAll(LlmJson.toStringMap(json.get()));
        return context;
    }
}
