package dev.catgirlyannick.catclans.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClanHome(
        UUID clanId,
        int number,
        UUID worldId,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        UUID updatedBy,
        Instant updatedAt
) {

    public ClanHome {
        Objects.requireNonNull(clanId, "clanId");
        Objects.requireNonNull(worldId, "worldId");
        worldName = Objects.requireNonNull(worldName, "worldName").trim();
        Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (number < 1) {
            throw new IllegalArgumentException("Home number must be positive");
        }
        if (worldName.isEmpty()) {
            throw new IllegalArgumentException("Home world name must not be empty");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Home coordinates must be finite");
        }
    }
}
