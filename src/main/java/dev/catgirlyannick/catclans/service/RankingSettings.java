package dev.catgirlyannick.catclans.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

public record RankingSettings(
        boolean enabled,
        ZoneId activityZone,
        Duration maintenanceInterval,
        Duration repeatedVictimCooldown,
        BigDecimal combatPointsPerKill,
        BigDecimal memberPointsPerMember,
        BigDecimal moneyAmountPerPoint,
        BigDecimal pointsPerWarWin,
        BigDecimal pointsPerWarLoss,
        BigDecimal negativePointMultiplier,
        BigDecimal activityPointsPerDay,
        boolean bankEnabled,
        boolean refreshPersistedStats
) {

    public RankingSettings {
        Objects.requireNonNull(activityZone, "activityZone");
        Objects.requireNonNull(maintenanceInterval, "maintenanceInterval");
        Objects.requireNonNull(repeatedVictimCooldown, "repeatedVictimCooldown");
        Objects.requireNonNull(combatPointsPerKill, "combatPointsPerKill");
        Objects.requireNonNull(memberPointsPerMember, "memberPointsPerMember");
        Objects.requireNonNull(moneyAmountPerPoint, "moneyAmountPerPoint");
        Objects.requireNonNull(pointsPerWarWin, "pointsPerWarWin");
        Objects.requireNonNull(pointsPerWarLoss, "pointsPerWarLoss");
        Objects.requireNonNull(negativePointMultiplier, "negativePointMultiplier");
        Objects.requireNonNull(activityPointsPerDay, "activityPointsPerDay");
        if (maintenanceInterval.isNegative() || maintenanceInterval.isZero()) {
            throw new IllegalArgumentException("Ranking maintenance interval must be positive");
        }
        if (repeatedVictimCooldown.isNegative()) {
            throw new IllegalArgumentException("PvP cooldown must not be negative");
        }
        if (combatPointsPerKill.signum() < 0
                || memberPointsPerMember.signum() < 0
                || moneyAmountPerPoint.signum() <= 0
                || pointsPerWarWin.signum() < 0
                || pointsPerWarLoss.signum() > 0
                || negativePointMultiplier.signum() < 0
                || negativePointMultiplier.compareTo(BigDecimal.ONE) > 0
                || activityPointsPerDay.signum() < 0) {
            throw new IllegalArgumentException("Invalid ranking point values");
        }
    }
}
