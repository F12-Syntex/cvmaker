package com.cvmaker.crawler.generic.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for parsing AI JSON responses into usable field mappings.
 */
public class AIResponseParser {

    /**
     * Extracts and parses JSON from an AI response string.
     *
     * @param aiResponse Raw response from AI service
     * @return Parsed field-value map
     */
    public static Map<String, String> parse(String aiResponse) {
        Map<String, String> context = new HashMap<>();
        if (aiResponse == null || aiResponse.isEmpty()) {
            return context;
        }

        try {
            String jsonStr = aiResponse.substring(
                aiResponse.indexOf("{"),
                aiResponse.lastIndexOf("}") + 1
            );

            Map<String, Object> parsed = new ObjectMapper().readValue(jsonStr, Map.class);

            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                if (entry.getValue() != null) {
                    context.put(entry.getKey(), entry.getValue().toString());
                }
            }
        } catch (Exception e) {
            System.out.println("Warning: Could not parse AI response as JSON: " + e.getMessage());
        }

        return context;
    }
}