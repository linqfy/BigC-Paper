package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty;

import dev.linqfy.bigCasares.dimension.nbt.Nbt;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.ShortTag;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.List;
import java.util.Map;

/**
 * Block entities and connector names shared by the third-party sources. Connector names only
 * have to be unique inside a template: the pack builder rewrites every jigsaw of an extra piece.
 */
final class ThirdPartyBlocks {

    static final String ENTRY = "bigc_myth:third_party/entry";
    static final String EXIT = "bigc_myth:third_party/exit";
    static final String DESCENT = "bigc_myth:third_party/descent";
    static final String SIDE = "bigc_myth:third_party/side";
    static final String SEAL = "bigc_myth:third_party/seal";
    private static final String EMPTY = "minecraft:empty";

    private ThirdPartyBlocks() {
    }

    /** A jigsaw block whose final state is air. */
    static void jigsaw(TemplateGrid grid, int x, int y, int z, String orientation, String name) {
        CompoundTag nbt = Nbt.compound()
            .putString("id", "minecraft:jigsaw")
            .putString("name", name)
            .putString("target", EMPTY)
            .putString("pool", EMPTY)
            .putString("final_state", "minecraft:air")
            .putString("joint", "aligned")
            .putInt("placement_priority", 0)
            .putInt("selection_priority", 0)
            .build();
        grid.set(x, y, z, BlockStateSpec.of("minecraft:jigsaw", Map.of("orientation", orientation)), nbt);
    }

    /** Same spawner data as the module's own pieces, so the runtime mob swap applies to it too. */
    static CompoundTag spawner(String mob) {
        CompoundTag range = Nbt.compound().putInt("min_inclusive", 0).putInt("max_inclusive", 15).build();
        CompoundTag spawnData = Nbt.compound()
            .put("entity", Nbt.compound().putString("id", mob).build())
            .put("custom_spawn_rules", Nbt.compound().put("block_light_limit", range).put("sky_light_limit", range).build())
            .build();
        CompoundTag potential = Nbt.compound().put("data", spawnData).putInt("weight", 1).build();
        return Nbt.compound()
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
    }

    static void spawner(TemplateGrid grid, int x, int y, int z, String mob) {
        grid.set(x, y, z, BlockStateSpec.of("minecraft:spawner"), spawner(mob));
    }

    static CompoundTag chest(String lootTable) {
        return Nbt.compound().putString("id", "minecraft:chest").putString("LootTable", lootTable).build();
    }

    static void chest(TemplateGrid grid, int x, int y, int z, String facing, String lootTable) {
        grid.set(x, y, z, BlockStateSpec.of("minecraft:chest", Map.of("facing", facing, "type", "single")),
            chest(lootTable));
    }
}
