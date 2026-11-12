package com.cvmaker.crawler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CrawlDelaysTest {

    @ParameterizedTest(name = "base {0} at speed {1} -> {2}")
    @CsvSource({
        // multiplier = 1.5 - 0.12 * speed
        "1000, 1, 1380",
        "1000, 5, 900",
        "1000, 10, 300",
        "3000, 5, 2700",
        "2000, 10, 600",
        "5000, 1, 6900",
    })
    void scalesBaseDelayBySpeed(int base, int speed, int expected) {
        assertThat(CrawlDelays.adjustedDelay(base, speed)).isEqualTo(expected);
    }

    @Test
    void matchesTheLegacyFloatFormulaForAllSpeedsInRange() {
        int[] bases = {0, 100, 250, 999, 1000, 1234, 3000, 30000};
        for (int speed = CrawlDelays.MIN_SPEED; speed <= CrawlDelays.MAX_SPEED; speed++) {
            for (int base : bases) {
                float legacyMultiplier = 1.5f - (speed * 0.12f);
                int legacy = Math.max(100, Math.round(base * legacyMultiplier));
                assertThat(CrawlDelays.adjustedDelay(base, speed))
                        .as("base %d speed %d", base, speed)
                        .isEqualTo(legacy);
            }
        }
    }

    @Test
    void neverGoesBelowTheFloor() {
        assertThat(CrawlDelays.adjustedDelay(0, 5)).isEqualTo(CrawlDelays.MIN_DELAY_MS);
        assertThat(CrawlDelays.adjustedDelay(50, 10)).isEqualTo(CrawlDelays.MIN_DELAY_MS);
        assertThat(CrawlDelays.adjustedDelay(-500, 5)).isEqualTo(CrawlDelays.MIN_DELAY_MS);
    }

    @Test
    void clampsSpeedAboveRangeInsteadOfCollapsingToTheFloor() {
        // The legacy formula gave a negative multiplier for speed > 12, so every delay became 100 ms.
        assertThat(CrawlDelays.adjustedDelay(3000, 20)).isEqualTo(CrawlDelays.adjustedDelay(3000, 10));
        assertThat(CrawlDelays.adjustedDelay(3000, 20)).isEqualTo(900);
    }

    @Test
    void clampsSpeedBelowRange() {
        assertThat(CrawlDelays.adjustedDelay(1000, 0)).isEqualTo(CrawlDelays.adjustedDelay(1000, 1));
        assertThat(CrawlDelays.adjustedDelay(1000, -7)).isEqualTo(1380);
    }

    @Test
    void doesNotOverflowForHugeDelays() {
        assertThat(CrawlDelays.adjustedDelay(Integer.MAX_VALUE, 1)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void slowerSpeedsNeverWaitLessThanFasterOnes() {
        for (int speed = CrawlDelays.MIN_SPEED; speed < CrawlDelays.MAX_SPEED; speed++) {
            assertThat(CrawlDelays.adjustedDelay(2000, speed))
                    .isGreaterThanOrEqualTo(CrawlDelays.adjustedDelay(2000, speed + 1));
        }
    }
}
