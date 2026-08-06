package dev.catgirlyannick.catclans.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class LoginStreakCalculator {

    private static final MathContext CONTEXT = MathContext.DECIMAL128;

    private final BigDecimal baseXp;
    private final BigDecimal earlyMultiplier;
    private final int lateGrowthStartsAtDay;
    private final BigDecimal lateGrowthFactor;
    private final int scale;
    private final RoundingMode roundingMode;

    public LoginStreakCalculator(
            BigDecimal baseXp,
            BigDecimal earlyMultiplier,
            int lateGrowthStartsAtDay,
            BigDecimal lateGrowthPercent,
            int scale,
            RoundingMode roundingMode
    ) {
        this.baseXp = baseXp;
        this.earlyMultiplier = earlyMultiplier;
        this.lateGrowthStartsAtDay = lateGrowthStartsAtDay;
        this.lateGrowthFactor = BigDecimal.ONE.add(lateGrowthPercent.movePointLeft(2));
        this.scale = scale;
        this.roundingMode = roundingMode;
        if (baseXp.signum() <= 0 || earlyMultiplier.signum() <= 0) {
            throw new IllegalArgumentException("Login XP and multiplier must be positive");
        }
        if (lateGrowthStartsAtDay < 3 || lateGrowthPercent.signum() < 0) {
            throw new IllegalArgumentException("Invalid late login streak curve");
        }
    }

    public BigDecimal xpForDay(int streakDay) {
        if (streakDay < 1) {
            throw new IllegalArgumentException("Streak day must be at least 1");
        }
        if (streakDay == 1) {
            return baseXp.setScale(scale, roundingMode);
        }
        int lastEarlyDay = lateGrowthStartsAtDay - 1;
        BigDecimal lastEarlyXp = baseXp
                .multiply(BigDecimal.valueOf(lastEarlyDay - 1L), CONTEXT)
                .multiply(earlyMultiplier, CONTEXT);
        if (streakDay <= lastEarlyDay) {
            return baseXp
                    .multiply(BigDecimal.valueOf(streakDay - 1L), CONTEXT)
                    .multiply(earlyMultiplier, CONTEXT)
                    .setScale(scale, roundingMode);
        }
        return lastEarlyXp.multiply(
                lateGrowthFactor.pow(streakDay - lastEarlyDay, CONTEXT),
                CONTEXT
        ).setScale(scale, roundingMode);
    }
}
