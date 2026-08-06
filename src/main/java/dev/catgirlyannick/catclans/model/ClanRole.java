package dev.catgirlyannick.catclans.model;

import java.util.Objects;
import java.util.UUID;

public record ClanRole(
        UUID clanId,
        String id,
        String displayName,
        int priority,
        boolean standard
) {
    public ClanRole {
        Objects.requireNonNull(clanId, "clanId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        if (id.isBlank() || displayName.isBlank()) {
            throw new IllegalArgumentException("Role ID and display name must not be empty");
        }
    }
}
