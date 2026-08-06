package dev.catgirlyannick.catclans.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BattlepassReward(
        int level,
        BattlepassRewardType type,
        int amount,
        UUID createdBy,
        Instant createdAt
) {

    public BattlepassReward {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        if (level < 1) {
            throw new IllegalArgumentException("Reward level must be at least 1");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("Reward amount must be at least 1");
        }
    }
}
