package dev.catgirlyannick.catclans.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record ClanRankingStats(
        UUID clanId,
        long combatKills,
        int warsWon,
        int warsLost,
        long activeDays,
        LocalDate lastActiveDate,
        BigDecimal bankBalance
) {

    public ClanRankingStats {
        Objects.requireNonNull(clanId, "clanId");
        Objects.requireNonNull(bankBalance, "bankBalance");
        if (combatKills < 0 || warsWon < 0 || warsLost < 0 || activeDays < 0) {
            throw new IllegalArgumentException("Leaderboard counters must not be negative");
        }
        if (bankBalance.signum() < 0) {
            throw new IllegalArgumentException("Clan bank balance must not be negative");
        }
    }

    public static ClanRankingStats empty(UUID clanId) {
        return new ClanRankingStats(
                clanId,
                0,
                0,
                0,
                0,
                null,
                BigDecimal.ZERO
        );
    }
}
