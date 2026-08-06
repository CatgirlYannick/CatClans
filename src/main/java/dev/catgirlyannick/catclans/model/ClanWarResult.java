package dev.catgirlyannick.catclans.model;

import java.util.Optional;
import java.util.UUID;

public record ClanWarResult(
        UUID warId,
        UUID firstClanId,
        UUID secondClanId,
        int firstDeaths,
        int secondDeaths,
        UUID winnerClanId,
        UUID loserClanId
) {

    public boolean draw() {
        return winnerClanId == null;
    }

    public Optional<UUID> winner() {
        return Optional.ofNullable(winnerClanId);
    }

    public Optional<UUID> loser() {
        return Optional.ofNullable(loserClanId);
    }
}
