package com.cvmaker.crawler.reed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

class ReedTextTest {

    @Test
    void detectsEasyApplyKeywordsCaseInsensitively() {
        assertThat(ReedText.mentionsEasyApply("Java Developer  London  Easy Apply")).isTrue();
        assertThat(ReedText.mentionsEasyApply("QUICK APPLY")).isTrue();
        assertThat(ReedText.mentionsEasyApply("quick apply")).isTrue();
    }

    @Test
    void rejectsCardsWithoutTheKeywords() {
        assertThat(ReedText.mentionsEasyApply("Apply on company website")).isFalse();
        assertThat(ReedText.mentionsEasyApply("")).isFalse();
        assertThat(ReedText.mentionsEasyApply(null)).isFalse();
    }

    @Test
    void detectsKeywordsWhateverTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            // In Turkish, "I".toLowerCase() is a dotless i, so "QUICK APPLY" became "quıck apply".
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(ReedText.mentionsEasyApply("QUICK APPLY")).isTrue();
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void usableDescriptionRejectsNullBlankAndShortText() {
        assertThat(ReedText.usableDescription(null, 0)).isNull();
        assertThat(ReedText.usableDescription("   \n\t ", 0)).isNull();
        assertThat(ReedText.usableDescription("short", 50)).isNull();
    }

    @Test
    void usableDescriptionTrimsAndKeepsLineStructure() {
        String text = "  Role overview\n\nWe need a Java developer.  ";
        assertThat(ReedText.usableDescription(text, 0))
                .isEqualTo("Role overview\n\nWe need a Java developer.");
    }

    @Test
    void usableDescriptionMeasuresLengthAfterTrimming() {
        String padded = " ".repeat(60) + "tiny" + " ".repeat(60);
        assertThat(ReedText.usableDescription(padded, 50)).isNull();
        String exactlyFiftyOne = "x".repeat(51);
        assertThat(ReedText.usableDescription(exactlyFiftyOne, 50)).isEqualTo(exactlyFiftyOne);
        assertThat(ReedText.usableDescription("x".repeat(50), 50)).isNull();
    }

    @Test
    void toleratesExtraWhitespaceBetweenWords() {
        assertThat(ReedText.mentionsEasyApply("Easy\n   Apply")).isTrue();
        assertThat(ReedText.mentionsEasyApply("Quick Apply")).isTrue();
    }
}
