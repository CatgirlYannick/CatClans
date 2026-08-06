package dev.catgirlyannick.catclans.model;

import java.time.Instant;
import java.util.UUID;

public record ClanInvite(
        UUID clanId,
        UUID playerId,
        UUID invitedBy,
        Instant createdAt,
        Instant expiresAt
) {
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
