package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.model.BattlepassProgress;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;

public final class BattlepassCurve {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    private final BigDecimal baseXp;
    private final BigDecimal growthFactor;
    private final int scale;
    private final RoundingMode roundingMode;

    public BattlepassCurve(
            BigDecimal baseXp,
            BigDecimal growthPercent,
            int scale,
            RoundingMode roundingMode
    ) {
        if (baseXp.signum() <= 0) {
            throw new IllegalArgumentException("Base XP must be positive");
        }
        if (growthPercent.signum() < 0) {
            throw new IllegalArgumentException("XP growth must not be negative");
        }
        if (scale < 0 || scale > 8) {
            throw new IllegalArgumentException("XP decimal scale must be between 0 and 8");
        }
        if (baseXp.setScale(scale, roundingMode).signum() <= 0) {
            throw new IllegalArgumentException(
                    "Base XP rounds down to zero with the selected rounding mode"
            );
        }
        this.baseXp = baseXp;
        this.growthFactor = BigDecimal.ONE.add(
                growthPercent.movePointLeft(2),
                CALCULATION_CONTEXT
        );
        this.scale = scale;
        this.roundingMode = roundingMode;
    }

    public BigDecimal requiredXp(int currentLevel) {
        if (currentLevel < 0) {
            throw new IllegalArgumentException("Level must not be negative");
        }
        BigDecimal required = baseXp.multiply(
                growthFactor.pow(currentLevel, CALCULATION_CONTEXT),
                CALCULATION_CONTEXT
        ).setScale(scale, roundingMode);
        if (required.signum() <= 0) {
            throw new IllegalStateException("Required battlepass XP rounded down to zero");
        }
        return required;
    }

    public ProgressionResult addXp(BattlepassProgress progress, BigDecimal awardedXp) {
        if (awardedXp.signum() < 0) {
            throw new IllegalArgumentException("Awarded XP must not be negative");
        }
        int previousLevel = progress.level();
        int level = previousLevel;
        BigDecimal xp = progress.currentXp().add(awardedXp);
        BigDecimal required = requiredXp(level);
        while (xp.compareTo(required) >= 0) {
            xp = xp.subtract(required);
            if (level == Integer.MAX_VALUE) {
                throw new IllegalStateException("Technisches Battlepass-Level-Limit erreicht");
            }
            level++;
            required = requiredXp(level);
        }
        BattlepassProgress updated = new BattlepassProgress(
                progress.clanId(),
                level,
                xp.setScale(scale, roundingMode),
                Instant.now()
        );
        return new ProgressionResult(updated, level - previousLevel);
    }

    public BattlepassProgress removeWithinCurrentLevel(
            BattlepassProgress progress,
            BigDecimal removedXp
    ) {
        if (removedXp.signum() < 0) {
            throw new IllegalArgumentException("Deducted XP must not be negative");
        }
        return new BattlepassProgress(
                progress.clanId(),
                progress.level(),
                progress.currentXp().subtract(removedXp).max(BigDecimal.ZERO)
                        .setScale(scale, roundingMode),
                Instant.now()
        );
    }

    public record ProgressionResult(BattlepassProgress progress, int levelsGained) {
    }
}
