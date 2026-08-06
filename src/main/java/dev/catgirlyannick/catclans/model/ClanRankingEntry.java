package dev.catgirlyannick.catclans.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public record ClanRankingEntry(
        int position,
        Clan clan,
        BigDecimal totalPoints,
        Map<RankingCategory, BigDecimal> categoryPoints,
        ClanRankingStats stats
) {

    public ClanRankingEntry {
        if (position < 1) {
            throw new IllegalArgumentException("Leaderboard position must be positive");
        }
        Objects.requireNonNull(clan, "clan");
        Objects.requireNonNull(totalPoints, "totalPoints");
        categoryPoints = Map.copyOf(categoryPoints);
        Objects.requireNonNull(stats, "stats");
    }

    public BigDecimal points(RankingCategory category) {
        return category == RankingCategory.TOTAL
                ? totalPoints
                : categoryPoints.getOrDefault(category, BigDecimal.ZERO);
    }
}
