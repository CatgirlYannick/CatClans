package dev.catgirlyannick.catclans.model;

import java.time.Instant;
import java.util.UUID;

public record ClanWar(
        UUID id,
        UUID firstClanId,
        UUID secondClanId,
        int durationHours,
        Instant startedAt,
        Instant endsAt,
        UUID acceptedBy
) {

    public boolean active(Instant now) {
        return endsAt.isAfter(now);
    }
}
