package dev.linqfy.bigCasares.modules.staffevents;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Builds a fresh arena for each event from {@link ArenaLayout}. Runs on the main thread. */
final class ArenaBuilder {

    private final World world;
    private final ArenaLayout layout;

    ArenaBuilder(World world, ArenaLayout layout) {
        this.world = world;
        this.layout = layout;
    }

    void build() {
        loadChunks();
        clear();
        switch (layout.type()) {
            case KOTH -> buildKoth();
            case TNT_TAG -> buildTntTag();
            case SPLEEF -> buildSpleef();
            case LIFESTEAL -> {
            }
        }
    }

    Location center() {
        return new Location(world, layout.centerX() + 0.5, layout.floorY(), layout.centerZ() + 0.5);
    }

    /** Spread spawn points on a ring inside the arena, facing the centre. */
    List<Location> spawnPoints(int count, Random random) {
        List<Location> points = new ArrayList<>(count);
        double ring = Math.max(2.0, layout.radius() - 3.0);
        double offset = random.nextDouble() * Math.PI * 2;
        for (int index = 0; index < count; index++) {
            double angle = offset + index * (Math.PI * 2 / Math.max(1, count));
            double x = layout.centerX() + 0.5 + Math.cos(angle) * ring;
            double z = layout.centerZ() + 0.5 + Math.sin(angle) * ring;
            Location point = new Location(world, x, layout.floorY(), z);
            point.setDirection(center().toVector().subtract(point.toVector()).setY(0));
            points.add(point);
        }
        return points;
    }

    /** Waiting spot during the lobby: the arena centre, above the hill if there is one. */
    Location lobbySpot() {
        Location spot = center();
        if (layout.type() == StaffEventType.KOTH) {
            spot.setY(layout.hillTopY());
        }
        return spot;
    }

    private void loadChunks() {
        int radius = layout.clearRadius();
        for (int cx = (layout.centerX() - radius) >> 4; cx <= (layout.centerX() + radius) >> 4; cx++) {
            for (int cz = (layout.centerZ() - radius) >> 4; cz <= (layout.centerZ() + radius) >> 4; cz++) {
                world.getChunkAt(cx, cz).load(true);
            }
        }
    }

    private void clear() {
        int radius = layout.clearRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = layout.clearMinY(); y <= layout.clearMaxY(); y++) {
                    set(dx, y, dz, Material.AIR);
                }
            }
        }
    }

    private void buildKoth() {
        int floor = layout.floorY() - 1;
        int radius = layout.radius();
        int hill = layout.hillRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!ArenaLayout.inDisc(dx, dz, radius)) {
                    continue;
                }
                boolean edge = !ArenaLayout.inDisc(dx, dz, radius - 1);
                set(dx, floor, dz, edge ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_STONE);
                if (ArenaLayout.inDisc(dx, dz, hill + 2)) {
                    set(dx, layout.floorY(), dz, Material.STONE_BRICKS);
                }
                if (ArenaLayout.inDisc(dx, dz, hill)) {
                    set(dx, layout.floorY(), dz, Material.GOLD_BLOCK);
                }
            }
        }
    }

    private void buildTntTag() {
        int floor = layout.floorY() - 1;
        int radius = layout.radius();
        for (int dx = -radius - 1; dx <= radius + 1; dx++) {
            for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                if (ArenaLayout.inDisc(dx, dz, radius)) {
                    boolean stripe = ((dx + radius) / 3 + (dz + radius) / 3) % 2 == 0;
                    set(dx, floor, dz, stripe ? Material.RED_CONCRETE : Material.WHITE_CONCRETE);
                } else if (ArenaLayout.inDisc(dx, dz, radius + 1)) {
                    set(dx, floor, dz, Material.BLACK_CONCRETE);
                    for (int y = layout.floorY(); y < layout.floorY() + 4; y++) {
                        set(dx, y, dz, Material.BARRIER);
                    }
                }
            }
        }
    }

    private void buildSpleef() {
        int radius = layout.radius();
        for (int floor : layout.layerFloors()) {
            for (int dx = -radius - 1; dx <= radius + 1; dx++) {
                for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                    if (ArenaLayout.inDisc(dx, dz, radius)) {
                        set(dx, floor, dz, Material.SNOW_BLOCK);
                    } else if (ArenaLayout.inDisc(dx, dz, radius + 1)) {
                        for (int y = floor; y < floor + layout.layerGap(); y++) {
                            set(dx, y, dz, Material.BARRIER);
                        }
                    }
                }
            }
        }
    }

    private void set(int dx, int y, int dz, Material material) {
        var block = world.getBlockAt(layout.centerX() + dx, y, layout.centerZ() + dz);
        if (block.getType() != material) {
            block.setType(material, false);
        }
    }
}
