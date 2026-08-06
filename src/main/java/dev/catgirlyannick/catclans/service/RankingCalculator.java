package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanRankingStats;
import dev.catgirlyannick.catclans.model.RankingCategory;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

final class RankingCalculator {

    private RankingCalculator() {
    }

    static Map<RankingCategory, BigDecimal> categoryPoints(
            Clan clan,
            ClanRankingStats stats,
            RankingSettings settings
    ) {
        EnumMap<RankingCategory, BigDecimal> points =
                new EnumMap<>(RankingCategory.class);
        points.put(
                RankingCategory.COMBAT,
                BigDecimal.valueOf(stats.combatKills())
                        .multiply(settings.combatPointsPerKill())
        );
        points.put(
                RankingCategory.MEMBERS,
                BigDecimal.valueOf(clan.members().size())
                        .multiply(settings.memberPointsPerMember())
        );
        points.put(
                RankingCategory.MONEY,
                settings.bankEnabled()
                        ? stats.bankBalance()
                        .divideToIntegralValue(settings.moneyAmountPerPoint())
                        : BigDecimal.ZERO
        );
        points.put(
                RankingCategory.WARS_WON,
                BigDecimal.valueOf(stats.warsWon())
                        .multiply(settings.pointsPerWarWin())
        );
        points.put(
                RankingCategory.WARS_LOST,
                BigDecimal.valueOf(stats.warsLost())
                        .multiply(settings.pointsPerWarLoss())
                        .multiply(settings.negativePointMultiplier())
        );
        points.put(
                RankingCategory.ACTIVITY,
                BigDecimal.valueOf(stats.activeDays())
                        .multiply(settings.activityPointsPerDay())
        );
        return Map.copyOf(points);
    }

    static BigDecimal total(Map<RankingCategory, BigDecimal> categoryPoints) {
        return categoryPoints.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static int compare(
            Clan left,
            BigDecimal leftPoints,
            Clan right,
            BigDecimal rightPoints
    ) {
        int byPoints = rightPoints.compareTo(leftPoints);
        if (byPoints != 0) {
            return byPoints;
        }
        int byTagIgnoringCase = String.CASE_INSENSITIVE_ORDER.compare(
                left.tag(),
                right.tag()
        );
        if (byTagIgnoringCase != 0) {
            return byTagIgnoringCase;
        }
        int byTag = left.tag().compareTo(right.tag());
        return byTag != 0
                ? byTag
                : left.id().toString().compareTo(right.id().toString());
    }
}
