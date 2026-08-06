package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.model.BattlepassProgress;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattlepassCurveTest {

    private final BattlepassCurve curve = new BattlepassCurve(
            new BigDecimal("100"),
            new BigDecimal("1.75"),
            2,
            RoundingMode.HALF_UP
    );

    @Test
    void increasesRequiredXpByOnePointSevenFivePercentPerLevel() {
        assertEquals(new BigDecimal("100.00"), curve.requiredXp(0));
        assertEquals(new BigDecimal("101.75"), curve.requiredXp(1));
        assertEquals(new BigDecimal("103.53"), curve.requiredXp(2));
    }

    @Test
    void carriesOverflowAcrossMultipleLevels() {
        BattlepassProgress initial = BattlepassProgress.initial(
                UUID.randomUUID(),
                Instant.EPOCH
        );

        BattlepassCurve.ProgressionResult result = curve.addXp(
                initial,
                new BigDecimal("250")
        );

        assertEquals(2, result.progress().level());
        assertEquals(new BigDecimal("48.25"), result.progress().currentXp());
        assertEquals(2, result.levelsGained());
    }

    @Test
    void warLossNeverRemovesAnEarnedLevel() {
        BattlepassProgress progress = new BattlepassProgress(
                UUID.randomUUID(),
                7,
                new BigDecimal("12.50"),
                Instant.EPOCH
        );

        BattlepassProgress result = curve.removeWithinCurrentLevel(
                progress,
                new BigDecimal("50")
        );

        assertEquals(7, result.level());
        assertEquals(new BigDecimal("0.00"), result.currentXp());
    }

    @Test
    void rejectsBaseXpThatRoundsToZero() {
        assertThrows(IllegalArgumentException.class, () -> new BattlepassCurve(
                new BigDecimal("0.1"),
                BigDecimal.ZERO,
                0,
                RoundingMode.HALF_UP
        ));
    }
}
