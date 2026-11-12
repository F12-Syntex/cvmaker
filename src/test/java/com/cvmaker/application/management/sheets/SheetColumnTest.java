package com.cvmaker.application.management.sheets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SheetColumnTest {

    @Test
    void layoutIsStable() {
        // Existing spreadsheets depend on these positions.
        assertThat(SheetColumn.count()).isEqualTo(22);
        assertThat(SheetColumn.lastLetter()).isEqualTo("V");
        assertThat(SheetColumn.COMPANY.letter()).isEqualTo("A");
        assertThat(SheetColumn.STATUS.letter()).isEqualTo("D");
        assertThat(SheetColumn.EMAIL_LINK.index()).isEqualTo(5);
        assertThat(SheetColumn.APPLICATION_URL.index()).isEqualTo(6);
        assertThat(SheetColumn.NOTES.letter()).isEqualTo("H");
        assertThat(SheetColumn.EMAIL_TIMESTAMP.letter()).isEqualTo("V");
    }

    @Test
    void visibleColumnsComeFirst() {
        assertThat(SheetColumn.visibleColumnEnd()).isEqualTo(8);
        assertThat(SheetColumn.all().subList(0, 8)).allMatch(SheetColumn::isVisible);
        assertThat(SheetColumn.all().subList(8, SheetColumn.count())).noneMatch(SheetColumn::isVisible);
    }
}
