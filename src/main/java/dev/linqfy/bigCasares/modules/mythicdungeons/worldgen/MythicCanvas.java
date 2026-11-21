package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.nbt.Nbt;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.ShortTag;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Drawing helper over {@link StructureTemplate.Builder} with the theme palette. Randomness
 * (wall weathering) comes from a {@link Random} seeded by the template id, so the same
 * theme always yields the same bytes.
 */
final class MythicCanvas {

    static final BlockStateSpec AIR = BlockStateSpec.of("minecraft:air");
    private static final double ALT_CHANCE = 0.2;

    private final StructureTemplate.Builder builder;
    private final MythicDungeonTheme.Palette palette;
    private final Random random;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;

    MythicCanvas(NamespacedId id, MythicDungeonTheme theme, int sizeX, int sizeY, int sizeZ) {
        this.builder = StructureTemplate.builder(id, sizeX, sizeY, sizeZ);
        this.palette = theme.palette();
        this.random = new Random(id.asString().hashCode());
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    int sizeX() {
        return sizeX;
    }

    int sizeY() {
        return sizeY;
    }

    int sizeZ() {
        return sizeZ;
    }

    MythicDungeonTheme.Palette palette() {
        return palette;
    }

    Random random() {
        return random;
    }

    MythicCanvas set(int x, int y, int z, BlockStateSpec state) {
        builder.set(x, y, z, state);
        return this;
    }

    MythicCanvas set(int x, int y, int z, BlockStateSpec state, CompoundTag blockEntity) {
        builder.set(x, y, z, state, blockEntity);
        return this;
    }

    boolean isSet(int x, int y, int z) {
        return builder.isSet(x, y, z);
    }

    boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    MythicCanvas fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockStateSpec state) {
        builder.fill(x1, y1, z1, x2, y2, z2, state);
        return this;
    }

    MythicCanvas air(int x1, int y1, int z1, int x2, int y2, int z2) {
        return fill(x1, y1, z1, x2, y2, z2, AIR);
    }

    BlockStateSpec wallBlock() {
        return random.nextDouble() < ALT_CHANCE ? palette.wallAlt() : palette.wall();
    }

    MythicCanvas wall(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, wallBlock());
                }
            }
        }
        return this;
    }

    /** A closed box: walls, floor (checkered with the alt floor), ceiling and air inside. */
    MythicCanvas room(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int y = y1; y <= y2; y++) {
                    boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                    if (y == y1) {
                        set(x, y, z, edge ? wallBlock() : ((x + z) % 4 == 0 ? palette.floorAlt() : palette.floor()));
                    } else if (y == y2) {
                        set(x, y, z, edge ? wallBlock() : palette.ceiling());
                    } else {
                        set(x, y, z, edge ? wallBlock() : AIR);
                    }
                }
            }
        }
        return this;
    }

    /** Ceiling lights on a grid, embedded in the ceiling layer. */
    MythicCanvas ceilingLights(int x1, int z1, int x2, int z2, int y, int step) {
        for (int x = x1; x <= x2; x += step) {
            for (int z = z1; z <= z2; z += step) {
                set(x, y, z, palette.light());
            }
        }
        return this;
    }

    /** Vertical pillar from {@code y1} to {@code y2}, capped with the secondary accent. */
    MythicCanvas pillar(int x, int y1, int z, int y2) {
        for (int y = y1; y < y2; y++) {
            set(x, y, z, palette.pillar());
        }
        return set(x, y2, z, palette.accent2());
    }

    /** Carves a 3-wide, 4-tall doorway through a wall layer, centred on {@code (x, z)}. */
    MythicCanvas doorway(Face face, int x, int y, int z) {
        for (int offset = -1; offset <= 1; offset++) {
            for (int dy = 0; dy < 4; dy++) {
                if (face.alongX()) {
                    set(x + offset, y + dy, z, AIR);
                } else {
                    set(x, y + dy, z + offset, AIR);
                }
            }
        }
        // Lintel trim above the opening, when it stays inside the template.
        if (inside(x, y + 4, z)) {
            set(x, y + 4, z, palette.accent2());
        }
        return this;
    }

    /** Places a jigsaw block; see {@link MythicPieces} for the naming contract. */
    MythicCanvas jigsaw(int x, int y, int z, String orientation, NamespacedId name, NamespacedId target,
                        NamespacedId pool, BlockStateSpec finalState, int selectionPriority) {
        return jigsaw(x, y, z, orientation, name, target, pool, finalState, selectionPriority, 0);
    }

    MythicCanvas jigsaw(int x, int y, int z, String orientation, NamespacedId name, NamespacedId target,
                        NamespacedId pool, BlockStateSpec finalState, int selectionPriority, int placementPriority) {
        // "aligned" everywhere: for the vertical entrance->shaft link it keeps the shaft rotated
        // like the entrance; horizontal links ignore the joint.
        CompoundTag nbt = MythicTemplateRewriter.jigsawNbt(name, target, pool, finalState.asString(), selectionPriority,
            placementPriority);
        return set(x, y, z, BlockStateSpec.of("minecraft:jigsaw", Map.of("orientation", orientation)), nbt);
    }

    /**
     * A vanilla mob spawner. The module's runtime swaps the spawned entity for the culture's mobs.
     * {@code custom_spawn_rules} with full light ranges make vanilla skip the mob's own darkness
     * check, so the lit dungeon rooms still spawn.
     */
    MythicCanvas spawner(int x, int y, int z, String mob) {
        CompoundTag range = Nbt.compound().putInt("min_inclusive", 0).putInt("max_inclusive", 15).build();
        CompoundTag spawnData = Nbt.compound()
            .put("entity", Nbt.compound().putString("id", mob).build())
            .put("custom_spawn_rules", Nbt.compound().put("block_light_limit", range).put("sky_light_limit", range).build())
            .build();
        CompoundTag potential = Nbt.compound().put("data", spawnData).putInt("weight", 1).build();
        CompoundTag nbt = Nbt.compound()
            .putString("id", "minecraft:mob_spawner")
            .put("Delay", new ShortTag((short) 20))
            .put("MinSpawnDelay", new ShortTag((short) 200))
            .put("MaxSpawnDelay", new ShortTag((short) 600))
            .put("SpawnCount", new ShortTag((short) 3))
            .put("MaxNearbyEntities", new ShortTag((short) 6))
            .put("RequiredPlayerRange", new ShortTag((short) 16))
            .put("SpawnRange", new ShortTag((short) 4))
            .put("SpawnData", spawnData)
            .put("SpawnPotentials", new Nbt.ListTag((byte) 10, List.of(potential)))
            .build();
        return set(x, y, z, BlockStateSpec.of("minecraft:spawner"), nbt);
    }

    MythicCanvas chest(int x, int y, int z, String facing, String lootTable) {
        CompoundTag nbt = Nbt.compound()
            .putString("id", "minecraft:chest")
            .putString("LootTable", lootTable)
            .build();
        return set(x, y, z, BlockStateSpec.of("minecraft:chest", Map.of("facing", facing, "type", "single")), nbt);
    }

    StructureTemplate build() {
        return builder.build();
    }

    enum Face {
        NORTH("north_up", true), SOUTH("south_up", true), WEST("west_up", false), EAST("east_up", false);

        private final String orientation;
        private final boolean alongX;

        Face(String orientation, boolean alongX) {
            this.orientation = orientation;
            this.alongX = alongX;
        }

        String orientation() {
            return orientation;
        }

        /** True when the wall plane runs along X (north/south faces). */
        boolean alongX() {
            return alongX;
        }
    }
}
