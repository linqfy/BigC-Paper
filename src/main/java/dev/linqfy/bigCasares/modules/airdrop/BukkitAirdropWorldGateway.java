package dev.linqfy.bigCasares.modules.airdrop;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Set;

public final class BukkitAirdropWorldGateway implements AirdropWorldGateway {

    private static final Set<Material> INVALID_GROUNDS = Set.of(
            Material.WATER,
            Material.LAVA,
            Material.VOID_AIR
    );

    private final World world;

    public BukkitAirdropWorldGateway(World world) {
        this.world = world;
    }

    @Override
    public int getHighestBlockY(int x, int z) {
        return world.getHighestBlockYAt(x, z);
    }

    @Override
    public boolean isSafeLandingBlock(int x, int y, int z) {
        Material material = world.getBlockAt(x, y, z).getType();
        if (INVALID_GROUNDS.contains(material)) {
            return false;
        }
        return material.isSolid();
    }

    @Override
    public boolean isSafeOpenSpace(int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        Material material = block.getType();
        if (material == Material.LAVA || material == Material.WATER) {
            return false;
        }
        return block.isPassable();
    }
}
