package dev.catgirlyannick.catclans.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ClanHomeView(
        UUID clanId,
        int unlockedSlots,
        int maximumSlots,
        List<ClanHome> homes,
        boolean canTeleport,
        boolean canSet,
        boolean canDelete
) {

    public ClanHomeView {
        Objects.requireNonNull(clanId, "clanId");
        homes = List.copyOf(homes);
        if (unlockedSlots < 1) {
            throw new IllegalArgumentException("At least one home slot must be unlocked");
        }
        if (maximumSlots < unlockedSlots) {
            throw new IllegalArgumentException("Maximum home slots must not be lower");
        }
    }

    public Optional<ClanHome> home(int number) {
        return homes.stream().filter(home -> home.number() == number).findFirst();
    }
}
