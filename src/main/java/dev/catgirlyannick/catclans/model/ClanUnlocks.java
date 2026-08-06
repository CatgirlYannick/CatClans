package dev.catgirlyannick.catclans.model;

public record ClanUnlocks(int bonusHomeSlots, int vaultPages) {

    public ClanUnlocks {
        if (bonusHomeSlots < 0) {
            throw new IllegalArgumentException("Home bonus must not be negative");
        }
        if (vaultPages < 1) {
            throw new IllegalArgumentException("A clan requires at least one vault page");
        }
    }
}
