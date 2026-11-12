package com.cvmaker.crawler.generic.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AIResponseParserTest {

    @Test
    void flatObjectBecomesAStringMap() {
        Map<String, String> map = AIResponseParser.parse(
                "{\"first_name\": \"Sam\", \"years_experience\": 7, \"willing_to_relocate\": true}");

        assertThat(map).containsOnly(
                Map.entry("first_name", "Sam"),
                Map.entry("years_experience", "7"),
                Map.entry("willing_to_relocate", "true"));
    }

    @Test
    void fencedAnswerWithProseAroundItIsParsed() {
        Map<String, String> map = AIResponseParser.parse("""
                Here are the suggested values:
                ```json
                {"email": "sam@example.com", "cover_letter": "I am {very} keen."}
                ```
                Fields marked {optional} can be left blank.
                """);

        assertThat(map).containsOnly(
                Map.entry("email", "sam@example.com"),
                Map.entry("cover_letter", "I am {very} keen."));
    }

    @Test
    void bracesInTheProseBeforeTheObjectAreSkipped() {
        // Before: the substring started at the first "{", so the whole answer failed to parse.
        Map<String, String> map = AIResponseParser.parse(
                "Replace {name} with the applicant's name: {\"first_name\": \"Sam\"}");

        assertThat(map).containsOnly(Map.entry("first_name", "Sam"));
    }

    @Test
    void nestedValuesAreKeptAsJsonNotAsMapToString() {
        Map<String, String> map = AIResponseParser.parse("""
                {"fields": {"first_name": {"value": "Sam", "required": true}},
                 "required": ["first_name", "email"],
                 "notes": null}
                """);

        // Before: "{first_name={value=Sam, required=true}}", which is not JSON and loses quoting.
        assertThat(map).containsOnly(
                Map.entry("fields", "{\"first_name\":{\"value\":\"Sam\",\"required\":true}}"),
                Map.entry("required", "[\"first_name\",\"email\"]"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "I could not analyse this form.",       // no brace: used to throw inside substring()
        "} stray brace before { another",
        "{\"first_name\": \"Sa"                  // cut off
    })
    void answersWithoutAnObjectGiveAnEmptyModifiableMap(String answer) {
        Map<String, String> map = AIResponseParser.parse(answer);

        assertThat(map).isEmpty();
        map.put("still", "modifiable");
    }
}
