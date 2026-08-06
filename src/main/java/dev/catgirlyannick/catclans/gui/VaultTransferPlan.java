package dev.catgirlyannick.catclans.gui;

import dev.catgirlyannick.catclans.service.VaultMutationType;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

record VaultTransferPlan(
        VaultMutationType mutation,
        ItemStack beforeSlot,
        ItemStack beforeCursor,
        ItemStack afterSlot,
        ItemStack afterCursor
) {

    static VaultTransferPlan create(
            ItemStack slot,
            ItemStack cursor,
            ClickType clickType
    ) {
        if (clickType != ClickType.LEFT && clickType != ClickType.RIGHT) {
            return null;
        }
        ItemStack beforeSlot = cloneOrNull(slot);
        ItemStack beforeCursor = cloneOrNull(cursor);
        if (beforeCursor == null && beforeSlot == null) {
            return null;
        }
        if (beforeCursor == null) {
            int moved = withdrawAmount(beforeSlot.getAmount(), clickType);
            ItemStack afterCursor = beforeSlot.clone();
            afterCursor.setAmount(moved);
            int remaining = beforeSlot.getAmount() - moved;
            ItemStack afterSlot = remaining == 0 ? null : beforeSlot.clone();
            if (afterSlot != null) {
                afterSlot.setAmount(remaining);
            }
            return new VaultTransferPlan(
                    VaultMutationType.WITHDRAW,
                    beforeSlot,
                    null,
                    afterSlot,
                    afterCursor
            );
        }
        if (beforeSlot == null) {
            int moved = depositAmount(
                    beforeCursor.getAmount(),
                    beforeCursor.getMaxStackSize(),
                    clickType
            );
            ItemStack afterSlot = beforeCursor.clone();
            afterSlot.setAmount(moved);
            int remaining = beforeCursor.getAmount() - moved;
            ItemStack afterCursor = remaining == 0 ? null : beforeCursor.clone();
            if (afterCursor != null) {
                afterCursor.setAmount(remaining);
            }
            return new VaultTransferPlan(
                    VaultMutationType.DEPOSIT,
                    null,
                    beforeCursor,
                    afterSlot,
                    afterCursor
            );
        }
        if (beforeSlot.isSimilar(beforeCursor)
                && beforeSlot.getAmount() < beforeSlot.getMaxStackSize()) {
            int available = beforeSlot.getMaxStackSize() - beforeSlot.getAmount();
            int moved = depositAmount(beforeCursor.getAmount(), available, clickType);
            ItemStack afterSlot = beforeSlot.clone();
            afterSlot.setAmount(beforeSlot.getAmount() + moved);
            int remaining = beforeCursor.getAmount() - moved;
            ItemStack afterCursor = remaining == 0 ? null : beforeCursor.clone();
            if (afterCursor != null) {
                afterCursor.setAmount(remaining);
            }
            return new VaultTransferPlan(
                    VaultMutationType.DEPOSIT,
                    beforeSlot,
                    beforeCursor,
                    afterSlot,
                    afterCursor
            );
        }
        if (clickType == ClickType.RIGHT || beforeSlot.isSimilar(beforeCursor)) {
            return null;
        }
        return new VaultTransferPlan(
                VaultMutationType.REPLACE,
                beforeSlot,
                beforeCursor,
                beforeCursor.clone(),
                beforeSlot.clone()
        );
    }

    static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    static int withdrawAmount(int stackAmount, ClickType clickType) {
        if (stackAmount < 1) {
            return 0;
        }
        return clickType == ClickType.RIGHT
                ? (stackAmount + 1) / 2
                : stackAmount;
    }

    static int depositAmount(
            int cursorAmount,
            int availableSpace,
            ClickType clickType
    ) {
        if (cursorAmount < 1 || availableSpace < 1) {
            return 0;
        }
        int requested = clickType == ClickType.RIGHT ? 1 : cursorAmount;
        return Math.min(requested, availableSpace);
    }
}
