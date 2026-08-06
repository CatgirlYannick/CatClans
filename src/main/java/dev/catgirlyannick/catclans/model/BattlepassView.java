package dev.catgirlyannick.catclans.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record BattlepassView(
        UUID clanId,
        BattlepassProgress progress,
        BigDecimal requiredXp,
        List<BattlepassReward> rewards,
        Set<String> claimedRewardKeys
) {

    public BattlepassView {
        Objects.requireNonNull(clanId, "clanId");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(requiredXp, "requiredXp");
        rewards = List.copyOf(rewards);
        claimedRewardKeys = Set.copyOf(claimedRewardKeys);
    }

    public boolean claimed(BattlepassReward reward) {
        return claimedRewardKeys.contains(reward.level() + ":" + reward.type().name());
    }
}
