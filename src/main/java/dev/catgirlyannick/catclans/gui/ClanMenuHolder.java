package dev.catgirlyannick.catclans.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

final class ClanMenuHolder implements InventoryHolder {

    private final ClanMenuType type;
    private final UUID clanId;
    private final UUID targetId;
    private final int page;
    private final String firstValue;
    private final String secondValue;
    private final ConfirmAction action;
    private Inventory inventory;

    ClanMenuHolder(
            ClanMenuType type,
            UUID clanId,
            UUID targetId,
            int page,
            String firstValue,
            String secondValue,
            ConfirmAction action
    ) {
        this.type = type;
        this.clanId = clanId;
        this.targetId = targetId;
        this.page = page;
        this.firstValue = firstValue;
        this.secondValue = secondValue;
        this.action = action;
    }

    ClanMenuType type() {
        return type;
    }

    UUID clanId() {
        return clanId;
    }

    UUID targetId() {
        return targetId;
    }

    int page() {
        return page;
    }

    String firstValue() {
        return firstValue;
    }

    String secondValue() {
        return secondValue;
    }

    ConfirmAction action() {
        return action;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Inventory has not been created yet");
        }
        return inventory;
    }
}
