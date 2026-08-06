package dev.catgirlyannick.catclans.gui;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Set;

final class HomeSafety {

    private HomeSafety() {
    }

    static boolean isSafe(
            Location location,
            Set<Material> unsafeMaterials,
            boolean requireSolidGround
    ) {
        if (location.getWorld() == null
                || location.getBlockY() <= location.getWorld().getMinHeight()
                || location.getBlockY() + 1 >= location.getWorld().getMaxHeight()) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);
        if (!safeMaterials(
                ground.getType(),
                feet.getType(),
                head.getType(),
                unsafeMaterials,
                requireSolidGround
        ) || blocked(feet, unsafeMaterials) || blocked(head, unsafeMaterials)) {
            return false;
        }
        if (ground.isLiquid()) {
            return false;
        }
        return !requireSolidGround || ground.getType().isSolid() && !ground.isPassable();
    }

    static boolean safeMaterials(
            Material ground,
            Material feet,
            Material head,
            Set<Material> unsafeMaterials,
            boolean requireSolidGround
    ) {
        return safeMaterials(
                ground,
                feet,
                head,
                unsafeMaterials,
                requireSolidGround,
                ground.isSolid()
        );
    }

    static boolean safeMaterials(
            Material ground,
            Material feet,
            Material head,
            Set<Material> unsafeMaterials,
            boolean requireSolidGround,
            boolean groundSolid
    ) {
        if (!safeOccupantMaterial(feet, unsafeMaterials)
                || !safeOccupantMaterial(head, unsafeMaterials)
                || isAir(ground)
                || unsafeMaterials.contains(ground)
                || ground == Material.WATER
                || ground == Material.LAVA) {
            return false;
        }
        return !requireSolidGround || groundSolid;
    }

    private static boolean blocked(Block block, Set<Material> unsafeMaterials) {
        return !safeOccupantMaterial(block.getType(), unsafeMaterials)
                || block.isLiquid()
                || !block.isPassable();
    }

    private static boolean safeOccupantMaterial(
            Material material,
            Set<Material> unsafeMaterials
    ) {
        return isAir(material) || !unsafeMaterials.contains(material);
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR;
    }
}
