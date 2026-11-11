package dev.linqfy.bigCasares.modules.duels;

import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.util.BoundingBox;

import java.util.BitSet;
import java.util.Random;

/**
 * The separate dimension for duels: an empty world where every running duel gets its own arena, built when
 * the duel starts and wiped when it ends. Arenas sit {@link #SLOT_SPACING} blocks apart along X.
 */
final class DuelArenaWorld {

    static final int FLOOR_Y = 64;
    static final int WALL_HEIGHT = 12;
    static final int SLOT_SPACING = 1024;
    private static final int ORIGIN_X = 4096;

    private final Server server;
    private final String worldName;
    private final BitSet usedSlots = new BitSet();
    private World world;

    DuelArenaWorld(Server server, String worldName) {
        this.server = server;
        this.worldName = worldName;
    }

    /** Loads or creates the duel world. Returns null when the server refuses to create it. */
    World world() {
        if (world != null && server.getWorld(world.getUID()) != null) {
            return world;
        }
        World existing = server.getWorld(worldName);
        world = existing != null ? existing : new WorldCreator(worldName)
            .environment(World.Environment.NORMAL)
            .generator(new Empty())
            .generateStructures(false)
            .createWorld();
        if (world != null) {
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            world.setGameRule(GameRules.SPAWN_MOBS, false);
            world.setGameRule(GameRules.SPAWN_MONSTERS, false);
            world.setGameRule(GameRules.SPAWN_PATROLS, false);
            world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
            world.setGameRule(GameRules.SPAWN_WARDENS, false);
            world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
            world.setGameRule(GameRules.KEEP_INVENTORY, true);
            world.setDifficulty(Difficulty.NORMAL);
            world.setStorm(false);
            world.setTime(6000L);
        }
        return world;
    }

    boolean isDuelWorld(World candidate) {
        return candidate != null && candidate.getName().equals(worldName);
    }

    int claimSlot() {
        int slot = usedSlots.nextClearBit(0);
        usedSlots.set(slot);
        return slot;
    }

    void releaseSlot(int slot) {
        usedSlots.clear(slot);
    }

    static double centerX(int slot) {
        return ORIGIN_X + (double) slot * SLOT_SPACING + 0.5;
    }

    Location center(int slot) {
        return new Location(world(), centerX(slot), FLOOR_Y + 1, 0.5);
    }

    /** Spawn point of each side, facing the other one. */
    Location spawn(int slot, DuelArenaSize size, boolean first) {
        double offset = Math.max(2.0, size.halfSide() - 3.0);
        Location spot = center(slot).add(first ? -offset : offset, 0.0, 0.0);
        spot.setYaw(first ? -90f : 90f);
        return spot;
    }

    /** Inside the walls, from the floor up to the top of the walls. */
    BoundingBox bounds(int slot, DuelArenaSize size) {
        int half = (int) Math.ceil(size.halfSide());
        double cx = Math.floor(centerX(slot));
        return new BoundingBox(cx - half, FLOOR_Y, -half, cx + half, FLOOR_Y + WALL_HEIGHT + 1, half);
    }

    void build(int slot, DuelArenaSize size) {
        World target = world();
        clear(slot);
        int half = (int) Math.ceil(size.halfSide());
        int cx = (int) Math.floor(centerX(slot));
        for (int dx = -half; dx < half; dx++) {
            for (int dz = -half; dz < half; dz++) {
                boolean border = dx == -half || dz == -half || dx == half - 1 || dz == half - 1;
                boolean stripe = dx == 0 || dx == -1;
                Material floor = border ? Material.POLISHED_BLACKSTONE_BRICKS
                    : stripe ? Material.RED_CONCRETE : Material.STONE_BRICKS;
                target.getBlockAt(cx + dx, FLOOR_Y, dz).setType(floor, false);
            }
        }
        for (int y = FLOOR_Y; y <= FLOOR_Y + WALL_HEIGHT; y++) {
            for (int i = -half - 1; i <= half; i++) {
                target.getBlockAt(cx + i, y, -half - 1).setType(Material.BARRIER, false);
                target.getBlockAt(cx + i, y, half).setType(Material.BARRIER, false);
                target.getBlockAt(cx - half - 1, y, i).setType(Material.BARRIER, false);
                target.getBlockAt(cx + half, y, i).setType(Material.BARRIER, false);
            }
        }
    }

    /** Wipes the largest arena footprint of the slot, blocks and dropped entities alike. */
    void clear(int slot) {
        World target = world();
        if (target == null) {
            return;
        }
        int half = (int) Math.ceil(DuelArenaSize.GRANDE.halfSide()) + 2;
        int cx = (int) Math.floor(centerX(slot));
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int y = FLOOR_Y - 2; y <= FLOOR_Y + WALL_HEIGHT + 2; y++) {
                    if (!target.getBlockAt(cx + dx, y, dz).getType().isAir()) {
                        target.getBlockAt(cx + dx, y, dz).setType(Material.AIR, false);
                    }
                }
            }
        }
        BoundingBox area = new BoundingBox(cx - half, FLOOR_Y - 64, -half, cx + half, FLOOR_Y + 128, half);
        for (Entity entity : target.getNearbyEntities(area)) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    /** Generates nothing at all. */
    private static final class Empty extends ChunkGenerator {
        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, FLOOR_Y + 1, 0.5);
        }
    }
}
