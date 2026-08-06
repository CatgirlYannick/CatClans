package dev.catgirlyannick.catclans.audit;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuditLogEntry(
        Instant timestamp,
        String action,
        UUID actorId,
        String actorName,
        String details
) {

    public AuditLogEntry {
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(actorName, "actorName");
        Objects.requireNonNull(details, "details");
    }
}
