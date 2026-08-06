package dev.catgirlyannick.catclans.gui;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeSafetyTest {

    private static final Set<Material> UNSAFE = Set.of(
            Material.AIR,
            Material.WATER,
            Material.LAVA,
            Material.POWDER_SNOW,
            Material.CACTUS,
            Material.MAGMA_BLOCK
    );

    @Test
    void allowsAirForPlayersButNeverAsGround() {
        assertTrue(HomeSafety.safeMaterials(
                Material.STONE,
                Material.AIR,
                Material.AIR,
                UNSAFE,
                true,
                true
        ));
        assertFalse(HomeSafety.safeMaterials(
                Material.AIR,
                Material.AIR,
                Material.AIR,
                UNSAFE,
                true,
                false
        ));
    }

    @Test
    void rejectsHazardsAndNonSolidGround() {
        assertFalse(HomeSafety.safeMaterials(
                Material.MAGMA_BLOCK,
                Material.AIR,
                Material.AIR,
                UNSAFE,
                true,
                true
        ));
        assertFalse(HomeSafety.safeMaterials(
                Material.STONE,
                Material.POWDER_SNOW,
                Material.AIR,
                UNSAFE,
                true,
                true
        ));
        assertFalse(HomeSafety.safeMaterials(
                Material.OAK_SAPLING,
                Material.AIR,
                Material.AIR,
                UNSAFE,
                true,
                false
        ));
    }
}
