package dev.catgirlyannick.catclans.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BattlepassProgress(
        UUID clanId,
        int level,
        BigDecimal currentXp,
        Instant updatedAt
) {

    public BattlepassProgress {
        Objects.requireNonNull(clanId, "clanId");
        Objects.requireNonNull(currentXp, "currentXp");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (level < 0) {
            throw new IllegalArgumentException("Battlepass level must not be negative");
        }
        if (currentXp.signum() < 0) {
            throw new IllegalArgumentException("Battlepass XP must not be negative");
        }
    }

    public static BattlepassProgress initial(UUID clanId, Instant now) {
        return new BattlepassProgress(clanId, 0, BigDecimal.ZERO, now);
    }
}
