package com.cvmaker.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;

class LlmJsonTest {

    private static JsonNode object(String answer) {
        Optional<JsonNode> node = LlmJson.extractObject(answer);
        assertThat(node).as("object in <%s>", answer).isPresent();
        return node.get();
    }

    // ---- extractObject ----

    @Test
    void bareObjectIsParsed() {
        JsonNode node = object("{\"isJobRelated\": true, \"confidenceScore\": 0.9}");

        assertThat(node.get("isJobRelated").booleanValue()).isTrue();
        assertThat(node.get("confidenceScore").doubleValue()).isEqualTo(0.9);
    }

    @Test
    void jsonCodeFenceIsStripped() {
        JsonNode node = object("""
                ```json
                {
                  "companyName": "Acme Ltd"
                }
                ```
                """);

        assertThat(node.get("companyName").textValue()).isEqualTo("Acme Ltd");
    }

    @Test
    void fenceWithoutLanguageOrWithOtherTagIsStripped() {
        assertThat(object("```\n{\"a\": 1}\n```").get("a").intValue()).isEqualTo(1);
        assertThat(object("```JSON\n{\"a\": 2}\n```").get("a").intValue()).isEqualTo(2);
        assertThat(object("```json5\n{\"a\": 3}\n```").get("a").intValue()).isEqualTo(3);
    }

    @Test
    void textBeforeAndAfterTheObjectIsIgnored() {
        JsonNode node = object("""
                Sure! Here is the analysis you asked for:

                {"isJobRelated": false, "confidenceScore": 0.9}

                Let me know if you need anything else {really}.
                """);

        assertThat(node.get("isJobRelated").booleanValue()).isFalse();
    }

    @Test
    void textAfterAClosingFenceIsIgnored() {
        JsonNode node = object("```json\n{\"a\": 1}\n```\nNote: the value {a} is a guess.");

        assertThat(node.get("a").intValue()).isEqualTo(1);
    }

    @Test
    void fencedObjectWinsOverExampleBracesInTheProse() {
        JsonNode node = object("""
                The format {"isJobRelated": false} was requested. My answer:
                ```json
                {"isJobRelated": true}
                ```
                """);

        assertThat(node.get("isJobRelated").booleanValue()).isTrue();
    }

    @Test
    void unclosedFenceStillYieldsTheObject() {
        assertThat(object("```json\n{\"a\": 1}").get("a").intValue()).isEqualTo(1);
    }

    @Test
    void nestedObjectsAndArraysStayNested() {
        JsonNode node = object("""
                {"fields": {"firstName": {"value": "Sam", "required": true}},
                 "skills": ["Java", "SQL"],
                 "after": "x"}
                """);

        assertThat(node.get("fields").get("firstName").get("value").textValue()).isEqualTo("Sam");
        assertThat(node.get("skills")).hasSize(2);
        assertThat(node.get("after").textValue()).isEqualTo("x");
    }

    @Test
    void bracesAndEscapedQuotesInsideStringsDoNotEndTheObject() {
        JsonNode node = object("""
                Answer: {"extractedInfo": "She said \\"start } soon\\" and {maybe} more", "n": 1} done
                """);

        assertThat(node.get("extractedInfo").textValue()).isEqualTo("She said \"start } soon\" and {maybe} more");
        assertThat(node.get("n").intValue()).isEqualTo(1);
    }

    @Test
    void nonJsonBracesBeforeTheObjectAreSkipped() {
        JsonNode node = object("Use the template {see below} then {\"a\": 1}");

        assertThat(node.get("a").intValue()).isEqualTo(1);
    }

    @Test
    void trailingCommaAndSingleQuotesAreTolerated() {
        assertThat(object("{\"a\": 1, \"b\": [1, 2,],}").get("b")).hasSize(2);
        assertThat(object("{'company': 'O\\'Neil {Co}'}").get("company").textValue()).isEqualTo("O'Neil {Co}");
    }

    @Test
    void objectInsideATopLevelArrayIsFound() {
        assertThat(object("[{\"a\": 1}, {\"a\": 2}]").get("a").intValue()).isEqualTo(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "I'm sorry, I can't help with that.",
        "{\"isJobRelated\": true, \"companyName\": \"Acm",     // cut off by a token limit
        "{not json at all}",
        "```json\n```",
        "[1, 2, 3]",
        "}{"
    })
    void answersWithoutACompleteObjectGiveEmpty(String answer) {
        assertThat(LlmJson.extractObject(answer)).isEmpty();
    }

    // ---- text ----

    @Test
    void textReturnsNullForMissingNullBlankAndTheWordNull() {
        JsonNode node = object("{\"a\": null, \"b\": \"\", \"c\": \"   \", \"d\": \"null\", \"e\": \"NULL\"}");

        for (String field : new String[] {"a", "b", "c", "d", "e", "missing"}) {
            assertThat(LlmJson.text(node, field)).as(field).isNull();
        }
        assertThat(LlmJson.text(null, "a")).isNull();
        assertThat(LlmJson.text(node, null)).isNull();
    }

    @Test
    void textRendersScalarsArraysAndObjects() {
        JsonNode node = object("""
                {"company": "  Acme Ltd ", "count": 3, "ratio": 0.5, "remote": true,
                 "skills": ["Java", null, " SQL ", ""],
                 "offer": {"salary": "£50,000", "start": "1 March", "bonus": null,
                           "benefits": ["pension", "gym"]},
                 "empty": [], "hollow": {"x": null}}
                """);

        assertThat(LlmJson.text(node, "company")).isEqualTo("Acme Ltd");
        assertThat(LlmJson.text(node, "count")).isEqualTo("3");
        assertThat(LlmJson.text(node, "ratio")).isEqualTo("0.5");
        assertThat(LlmJson.text(node, "remote")).isEqualTo("true");
        assertThat(LlmJson.text(node, "skills")).isEqualTo("Java, SQL");
        assertThat(LlmJson.text(node, "offer"))
                .isEqualTo("salary: £50,000; start: 1 March; benefits: pension, gym");
        assertThat(LlmJson.text(node, "empty")).isNull();
        assertThat(LlmJson.text(node, "hollow")).isNull();
    }

    @Test
    void fieldNamesMatchIgnoringCaseAndSeparatorsButExactWins() {
        JsonNode node = object("{\"company_name\": \"Snake\", \"Position-Title\": \"Tester\", "
                + "\"workType\": \"exact\", \"work_type\": \"loose\"}");

        assertThat(LlmJson.text(node, "companyName")).isEqualTo("Snake");
        assertThat(LlmJson.text(node, "positionTitle")).isEqualTo("Tester");
        assertThat(LlmJson.text(node, "workType")).isEqualTo("exact");
    }

    // ---- bool and number ----

    @Test
    void boolAcceptsJsonBooleansAndCommonTexts() {
        JsonNode node = object("{\"a\": true, \"b\": false, \"c\": \"True\", \"d\": \" no \", "
                + "\"e\": \"maybe\", \"f\": 1, \"g\": null}");

        assertThat(LlmJson.bool(node, "a")).isTrue();
        assertThat(LlmJson.bool(node, "b")).isFalse();
        assertThat(LlmJson.bool(node, "c")).isTrue();
        assertThat(LlmJson.bool(node, "d")).isFalse();
        assertThat(LlmJson.bool(node, "e")).isNull();
        assertThat(LlmJson.bool(node, "f")).isNull();
        assertThat(LlmJson.bool(node, "g")).isNull();
        assertThat(LlmJson.bool(node, "missing")).isNull();
        assertThat(LlmJson.bool(null, "a")).isNull();
    }

    @Test
    void numberAcceptsJsonNumbersAndNumericText() {
        JsonNode node = object("{\"a\": 0.85, \"b\": 1, \"c\": \" 0.4 \", \"d\": \"high\", "
                + "\"e\": \"NaN\", \"f\": null, \"g\": true}");

        assertThat(LlmJson.number(node, "a")).isEqualTo(0.85);
        assertThat(LlmJson.number(node, "b")).isEqualTo(1.0);
        assertThat(LlmJson.number(node, "c")).isEqualTo(0.4);
        assertThat(LlmJson.number(node, "d")).isNull();
        assertThat(LlmJson.number(node, "e")).isNull();
        assertThat(LlmJson.number(node, "f")).isNull();
        assertThat(LlmJson.number(node, "g")).isNull();
        assertThat(LlmJson.number(node, "missing")).isNull();
    }

    // ---- toStringMap ----

    @Test
    void toStringMapKeepsOrderDropsNullsAndKeepsNestedValuesAsJson() {
        JsonNode node = object("""
                {"firstName": "Sam", "years": 7, "relocate": false, "middleName": null,
                 "address": {"city": "Leeds", "postcode": "LS1"}, "languages": ["en", "fr"]}
                """);

        Map<String, String> map = LlmJson.toStringMap(node);

        assertThat(map).containsExactly(
                Map.entry("firstName", "Sam"),
                Map.entry("years", "7"),
                Map.entry("relocate", "false"),
                Map.entry("address", "{\"city\":\"Leeds\",\"postcode\":\"LS1\"}"),
                Map.entry("languages", "[\"en\",\"fr\"]"));
        map.put("extra", "modifiable");
    }

    @Test
    void toStringMapOfNonObjectIsEmpty() {
        assertThat(LlmJson.toStringMap(null)).isEmpty();
        assertThat(LlmJson.toStringMap(object("{\"a\": [1]}").get("a"))).isEmpty();
    }
}
