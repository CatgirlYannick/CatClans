package dev.catgirlyannick.catclans.gui;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VaultTransferPlanTest {

    @Test
    void leftClickDepositsTheCompleteCursorStack() {
        assertEquals(10, VaultTransferPlan.depositAmount(10, 64, ClickType.LEFT));
    }

    @Test
    void rightClickDepositsOneItem() {
        assertEquals(1, VaultTransferPlan.depositAmount(10, 64, ClickType.RIGHT));
    }

    @Test
    void rightClickWithdrawsHalfRoundedUp() {
        assertEquals(5, VaultTransferPlan.withdrawAmount(9, ClickType.RIGHT));
    }

    @Test
    void leftClickMergesOnlyUpToTheMaterialStackLimit() {
        assertEquals(4, VaultTransferPlan.depositAmount(10, 4, ClickType.LEFT));
    }

    @Test
    void emptySpaceMovesNothing() {
        assertEquals(0, VaultTransferPlan.depositAmount(10, 0, ClickType.LEFT));
    }
}
