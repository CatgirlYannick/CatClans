package dev.catgirlyannick.catclans.service;

public record HomeSettings(
        boolean enabled,
        int defaultSlots,
        int absoluteMaxSlots,
        int teleportCooldownSeconds,
        boolean allowCrossWorld
) {

    public HomeSettings {
        if (defaultSlots < 1 || absoluteMaxSlots < defaultSlots) {
            throw new IllegalArgumentException("Clan home limits are invalid");
        }
        if (teleportCooldownSeconds < 0) {
            throw new IllegalArgumentException("Home cooldown must not be negative");
        }
    }

    public static HomeSettings disabled() {
        return new HomeSettings(false, 3, 103, 0, true);
    }
}
