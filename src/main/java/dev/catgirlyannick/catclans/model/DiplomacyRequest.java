package dev.catgirlyannick.catclans.model;

import java.time.Instant;
import java.util.UUID;

public record DiplomacyRequest(
        UUID id,
        UUID sourceClanId,
        UUID targetClanId,
        DiplomacyType type,
        int warDurationHours,
        UUID requestedBy,
        Instant createdAt,
        Instant expiresAt
) {

    public boolean expired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
