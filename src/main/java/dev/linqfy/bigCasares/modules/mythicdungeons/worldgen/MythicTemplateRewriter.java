package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.nbt.Nbt;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.IntTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.ListTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.Tag;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/** Reads jigsaw blocks of templates and rewrites them (plus forced air cells) for extra pieces. */
final class MythicTemplateRewriter {

    /** A jigsaw block: {@code index} into the template's {@code blocks} list. */
    record Jigsaw(int index, int x, int y, int z, String orientation, CompoundTag nbt) {
        String name() {
            return nbt == null ? "" : String.valueOf(nbt.getString("name"));
        }

        String target() {
            return nbt == null ? "" : String.valueOf(nbt.getString("target"));
        }

        String pool() {
            return nbt == null ? "" : String.valueOf(nbt.getString("pool"));
        }

        String front() {
            return orientation.substring(0, orientation.indexOf('_'));
        }

        String top() {
            return orientation.substring(orientation.indexOf('_') + 1);
        }

        boolean horizontal() {
            return !front().equals("up") && !front().equals("down");
        }
    }

    private MythicTemplateRewriter() {
    }

    static int[] size(StructureTemplate template) {
        return ints(template.root().getList("size"));
    }

    static ListTag firstPalette(CompoundTag root) {
        if (root.getList("palette") != null) {
            return root.getList("palette");
        }
        ListTag palettes = root.getList("palettes");
        return palettes == null || palettes.values().isEmpty() ? new ListTag((byte) 10, List.of()) : (ListTag) palettes.values().getFirst();
    }

    static List<Jigsaw> jigsaws(StructureTemplate template) {
        CompoundTag root = template.root();
        List<Tag> palette = firstPalette(root).values();
        List<Jigsaw> result = new ArrayList<>();
        List<Tag> blocks = root.getList("blocks").values();
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = (CompoundTag) blocks.get(index);
            CompoundTag state = (CompoundTag) palette.get(((IntTag) block.get("state")).value());
            if (!"minecraft:jigsaw".equals(state.getString("Name"))) {
                continue;
            }
            CompoundTag properties = state.getCompound("Properties");
            String orientation = properties == null || properties.getString("orientation") == null
                ? "north_up" : properties.getString("orientation");
            int[] pos = ints(block.getList("pos"));
            result.add(new Jigsaw(index, pos[0], pos[1], pos[2], orientation, block.getCompound("nbt")));
        }
        return result;
    }

    /** True when any palette of the template contains {@code block}. */
    static boolean containsBlock(StructureTemplate template, String block) {
        return template.paletteBlocks().contains(block);
    }

    /** Every namespaced id in palettes, block entities and entities that is not {@code minecraft:}. */
    static List<String> nonVanillaIds(StructureTemplate template) {
        CompoundTag root = template.root();
        Set<String> ids = new TreeSet<>(template.paletteBlocks());
        for (Tag tag : root.getList("blocks").values()) {
            CompoundTag nbt = ((CompoundTag) tag).getCompound("nbt");
            if (nbt != null && nbt.getString("id") != null) {
                ids.add(nbt.getString("id"));
            }
        }
        ListTag entities = root.getList("entities");
        if (entities != null) {
            for (Tag tag : entities.values()) {
                CompoundTag nbt = ((CompoundTag) tag).getCompound("nbt");
                if (nbt != null && nbt.getString("id") != null) {
                    ids.add(nbt.getString("id"));
                }
            }
        }
        return ids.stream().filter(id -> !id.startsWith("minecraft:")).toList();
    }

    /**
     * Copies {@code template} under {@code id}, replacing each jigsaw's block-entity NBT with
     * {@code jigsawNbt.apply(jigsaw)} and turning {@code airCells} ({@code x,y,z} triples) into air.
     */
    static StructureTemplate rewrite(StructureTemplate template, NamespacedId id,
                                     Function<Jigsaw, CompoundTag> jigsawNbt, Set<List<Integer>> airCells) {
        CompoundTag root = template.root();
        LinkedHashMap<Integer, Jigsaw> byIndex = new LinkedHashMap<>();
        jigsaws(template).forEach(jigsaw -> byIndex.put(jigsaw.index(), jigsaw));
        int airIndex = -1;
        List<Tag> palette = firstPalette(root).values();
        for (int index = 0; index < palette.size(); index++) {
            CompoundTag state = (CompoundTag) palette.get(index);
            if ("minecraft:air".equals(state.getString("Name")) && state.get("Properties") == null) {
                airIndex = index;
            }
        }
        if (airIndex < 0 && !airCells.isEmpty()) {
            airIndex = palette.size();
            CompoundTag air = Nbt.compound().putString("Name", "minecraft:air").build();
            if (root.getList("palette") != null) {
                root = root.with("palette", append(root.getList("palette"), air));
            }
            if (root.getList("palettes") != null) {
                List<Tag> palettes = new ArrayList<>();
                for (Tag entry : root.getList("palettes").values()) {
                    palettes.add(append((ListTag) entry, air));
                }
                root = root.with("palettes", new ListTag((byte) 9, palettes));
            }
        }
        List<Tag> blocks = root.getList("blocks").values();
        List<Tag> rewritten = new ArrayList<>(blocks.size() + airCells.size());
        Set<List<Integer>> seen = new HashSet<>();
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = (CompoundTag) blocks.get(index);
            int[] pos = ints(block.getList("pos"));
            List<Integer> key = List.of(pos[0], pos[1], pos[2]);
            seen.add(key);
            if (airCells.contains(key)) {
                rewritten.add(Nbt.compound().put("pos", block.get("pos")).putInt("state", airIndex).build());
            } else if (byIndex.containsKey(index)) {
                rewritten.add(block.with("nbt", jigsawNbt.apply(byIndex.get(index))));
            } else {
                rewritten.add(block);
            }
        }
        for (List<Integer> cell : airCells) {
            if (!seen.contains(cell)) {
                rewritten.add(Nbt.compound().put("pos", Nbt.intList(cell.get(0), cell.get(1), cell.get(2)))
                    .putInt("state", airIndex).build());
            }
        }
        return StructureTemplate.of(id, root.with("blocks", new ListTag((byte) 10, rewritten)));
    }

    static CompoundTag jigsawNbt(NamespacedId name, NamespacedId target, NamespacedId pool, String finalState,
                                 int selectionPriority) {
        return jigsawNbt(name, target, pool, finalState, selectionPriority, 0);
    }

    /**
     * Jigsaw block entity. {@code placementPriority} orders the processing of the piece attached through
     * this jigsaw (vanilla processes higher priorities first, in placement order within a priority).
     */
    static CompoundTag jigsawNbt(NamespacedId name, NamespacedId target, NamespacedId pool, String finalState,
                                 int selectionPriority, int placementPriority) {
        return Nbt.compound()
            .putString("id", "minecraft:jigsaw")
            .putString("name", name.asString())
            .putString("target", target.asString())
            .putString("pool", pool.asString())
            .putString("final_state", finalState)
            .putString("joint", "aligned")
            .putInt("placement_priority", placementPriority)
            .putInt("selection_priority", selectionPriority)
            .build();
    }

    private static ListTag append(ListTag list, Tag value) {
        List<Tag> values = new ArrayList<>(list.values());
        values.add(value);
        return new ListTag((byte) 10, values);
    }

    private static int[] ints(ListTag list) {
        int[] values = new int[list.values().size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = ((IntTag) list.values().get(index)).value();
        }
        return values;
    }
}
