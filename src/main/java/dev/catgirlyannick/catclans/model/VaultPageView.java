package dev.catgirlyannick.catclans.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record VaultPageView(
        UUID clanId,
        int page,
        int maximumPages,
        Map<Integer, byte[]> items,
        boolean canDeposit,
        boolean canWithdraw,
        boolean canViewLog,
        boolean canManageExtensions
) {

    public VaultPageView {
        Map<Integer, byte[]> defensive = new HashMap<>();
        items.forEach((slot, data) -> defensive.put(slot, data.clone()));
        items = Map.copyOf(defensive);
    }
}
