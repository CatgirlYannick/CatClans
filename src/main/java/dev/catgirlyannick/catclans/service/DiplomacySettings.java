package dev.catgirlyannick.catclans.service;

import java.time.Duration;
import java.util.Set;

public record DiplomacySettings(
        boolean alliancesEnabled,
        boolean warsEnabled,
        Duration requestDuration,
        Set<Integer> allowedWarDurations,
        int maximumPendingRequestsPerClan
) {

    public DiplomacySettings {
        allowedWarDurations = Set.copyOf(allowedWarDurations);
    }

    public boolean enabled() {
        return alliancesEnabled || warsEnabled;
    }

    public boolean enabled(dev.catgirlyannick.catclans.model.DiplomacyType type) {
        return type == dev.catgirlyannick.catclans.model.DiplomacyType.ALLY
                ? alliancesEnabled
                : warsEnabled;
    }
}
