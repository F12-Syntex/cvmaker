package com.cvmaker.application.management.sheets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class A1NotationTest {

    @ParameterizedTest
    @CsvSource({
        "1, A", "2, B", "22, V", "26, Z",
        "27, AA", "28, AB", "52, AZ", "53, BA",
        "702, ZZ", "703, AAA", "18278, ZZZ"
    })
    void columnLetterIsOneBased(int columnNumber, String expected) {
        assertThat(A1Notation.columnLetter(columnNumber)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void columnLetterRejectsNumbersBelowOne(int columnNumber) {
        assertThatIllegalArgumentException().isThrownBy(() -> A1Notation.columnLetter(columnNumber));
    }

    @Test
    void onSheetQuotesTheTitle() {
        assertThat(A1Notation.onSheet("Sheet1", "A2:V")).isEqualTo("'Sheet1'!A2:V");
        assertThat(A1Notation.onSheet("Job Applications", "A1:V1")).isEqualTo("'Job Applications'!A1:V1");
        assertThat(A1Notation.onSheet("Bob's jobs", "A2:V9")).isEqualTo("'Bob''s jobs'!A2:V9");
    }

    @Test
    void onSheetWithoutTitleLeavesTheRangeAlone() {
        assertThat(A1Notation.onSheet(null, "A2:V")).isEqualTo("A2:V");
        assertThat(A1Notation.onSheet("", "A2:V")).isEqualTo("A2:V");
    }
}
