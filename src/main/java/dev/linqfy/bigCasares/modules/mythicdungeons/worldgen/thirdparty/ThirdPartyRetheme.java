package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty;

import dev.linqfy.bigCasares.dimension.nbt.Nbt;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicDungeonTheme;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Palette-level re-theming of third-party templates: the structural vanilla blocks the templates
 * are built from (cobblestone, stone/nether bricks and their stairs and slabs...) become the
 * culture's palette, everything else (props, chests, fences, liquids) stays as it is.
 */
final class ThirdPartyRetheme {

    private enum Slot { WALL, WALL_ALT, FLOOR_ALT, CEILING, PILLAR, ACCENT, STAIRS, SLAB }

    private static final Map<String, Slot> SLOTS = new LinkedHashMap<>();

    static {
        for (String id : new String[]{"cobblestone", "stone_bricks", "nether_bricks", "stone"}) {
            SLOTS.put("minecraft:" + id, Slot.WALL);
        }
        for (String id : new String[]{"mossy_cobblestone", "mossy_stone_bricks", "cracked_stone_bricks",
            "red_nether_bricks", "cracked_nether_bricks"}) {
            SLOTS.put("minecraft:" + id, Slot.WALL_ALT);
        }
        SLOTS.put("minecraft:coarse_dirt", Slot.FLOOR_ALT);
        SLOTS.put("minecraft:smooth_stone", Slot.CEILING);
        SLOTS.put("minecraft:polished_andesite", Slot.PILLAR);
        SLOTS.put("minecraft:chiseled_stone_bricks", Slot.ACCENT);
        SLOTS.put("minecraft:chiseled_nether_bricks", Slot.ACCENT);
        for (String id : new String[]{"stone_brick_stairs", "cobblestone_stairs", "mossy_cobblestone_stairs",
            "nether_brick_stairs", "polished_andesite_stairs", "red_nether_brick_stairs"}) {
            SLOTS.put("minecraft:" + id, Slot.STAIRS);
        }
        for (String id : new String[]{"stone_brick_slab", "cobblestone_slab", "mossy_cobblestone_slab",
            "smooth_stone_slab", "nether_brick_slab", "red_nether_brick_slab"}) {
            SLOTS.put("minecraft:" + id, Slot.SLAB);
        }
    }

    /** Properties a stairs/slab replacement keeps; everything else is dropped. */
    private static final Set<String> STAIR_PROPERTIES = Set.of("facing", "half", "shape", "waterlogged");
    private static final Set<String> SLAB_PROPERTIES = Set.of("type", "waterlogged");

    private ThirdPartyRetheme() {
    }

    /** Block ids this re-theming replaces; tests use it. */
    static Set<String> sourceIds() {
        return SLOTS.keySet();
    }

    static Function<CompoundTag, CompoundTag> mapper(MythicDungeonTheme theme) {
        MythicDungeonTheme.Palette palette = theme.palette();
        return state -> {
            Slot slot = SLOTS.get(state.getString("Name"));
            if (slot == null) {
                return state;
            }
            return switch (slot) {
                case WALL -> compound(palette.wall());
                case WALL_ALT -> compound(palette.wallAlt());
                case FLOOR_ALT -> compound(palette.floorAlt());
                case CEILING -> compound(palette.ceiling());
                case PILLAR -> compound(palette.pillar());
                case ACCENT -> compound(palette.accent2());
                case STAIRS -> withKeptProperties(palette.stairs(), state, STAIR_PROPERTIES);
                case SLAB -> withKeptProperties(palette.slab(), state, SLAB_PROPERTIES);
            };
        };
    }

    private static CompoundTag compound(BlockStateSpec spec) {
        Nbt.CompoundBuilder entry = Nbt.compound().putString("Name", spec.block().asString());
        if (!spec.properties().isEmpty()) {
            Nbt.CompoundBuilder properties = Nbt.compound();
            spec.properties().forEach(properties::putString);
            entry.put("Properties", properties.build());
        }
        return entry.build();
    }

    private static CompoundTag withKeptProperties(String id, CompoundTag state, Set<String> kept) {
        Nbt.CompoundBuilder entry = Nbt.compound().putString("Name", id);
        CompoundTag source = state.getCompound("Properties");
        if (source != null) {
            Nbt.CompoundBuilder properties = Nbt.compound();
            source.values().forEach((key, value) -> {
                if (kept.contains(key)) {
                    properties.put(key, value);
                }
            });
            entry.put("Properties", properties.build());
        }
        return entry.build();
    }
}
