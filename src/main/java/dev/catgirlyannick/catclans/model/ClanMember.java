package dev.catgirlyannick.catclans.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClanMember(
        UUID playerId,
        String lastKnownName,
        RankId rank,
        String roleId,
        Instant joinedAt
) {
    public ClanMember(
            UUID playerId,
            String lastKnownName,
            RankId rank,
            Instant joinedAt
    ) {
        this(playerId, lastKnownName, rank, rank.configKey(), joinedAt);
    }

    public ClanMember {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lastKnownName, "lastKnownName");
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(joinedAt, "joinedAt");
        if (roleId.isBlank()) {
            throw new IllegalArgumentException("roleId must not be empty");
        }
    }

    public ClanMember withRole(String newRoleId, RankId fallbackRank) {
        return new ClanMember(playerId, lastKnownName, fallbackRank, newRoleId, joinedAt);
    }
}
