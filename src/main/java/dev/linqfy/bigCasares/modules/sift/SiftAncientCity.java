package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.datapack.DatapackBuilder;
import dev.linqfy.bigCasares.dimension.nbt.Nbt;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.ListTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.Tag;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The mod's Ancient City centre templates: vanilla's three {@code city_center}
 * pieces with eight Sonorous Deepslate blocks (horn mode) beside the reinforced
 * deepslate frame. They are written into the generated datapack under the
 * {@code minecraft} namespace, so Ancient Cities generated afterwards contain the
 * ritual blocks; existing cities are not changed.
 *
 * <p>Mod blocks in the palette become their backing states and lose the mod's
 * block-entity data (the server keeps ritual state itself, see {@link SiftRitual}).</p>
 */
final class SiftAncientCity {

    static final List<String> CITY_CENTERS = List.of(
        "ancient_city/city_center/city_center_1", "ancient_city/city_center/city_center_2",
        "ancient_city/city_center/city_center_3");

    private SiftAncientCity() {
    }

    static void contribute(SiftData data, DatapackBuilder datapack) {
        for (String path : CITY_CENTERS) {
            byte[] source = data.bytes("minecraft/structure/" + path + ".nbt")
                .orElseThrow(() -> new IllegalStateException("Missing imported template " + path));
            datapack.structureTemplate(NamespacedId.minecraft(path), translate(source, path));
        }
    }

    /** Remaps mod palette entries to backing states and drops their block entities. */
    static byte[] translate(byte[] gzipped, String name) {
        CompoundTag root;
        try {
            root = Nbt.readGzip(gzipped);
        } catch (IOException failure) {
            throw new UncheckedIOException("Unreadable template " + name, failure);
        }
        List<Tag> palette = new ArrayList<>();
        Set<Integer> remapped = new HashSet<>();
        List<Tag> source = root.getList("palette").values();
        for (int index = 0; index < source.size(); index++) {
            CompoundTag entry = (CompoundTag) source.get(index);
            NamespacedId block = NamespacedId.parse(entry.getString("Name"));
            if (block.namespace().equals("minecraft")) {
                palette.add(entry);
                continue;
            }
            BlockStateSpec state = SiftBlocks.remap(block.path(), properties(entry))
                .orElseThrow(() -> new IllegalStateException("No backing state for " + block + " in " + name));
            Nbt.CompoundBuilder properties = Nbt.compound();
            state.properties().forEach(properties::putString);
            Nbt.CompoundBuilder remappedEntry = Nbt.compound().putString("Name", state.block().asString());
            if (!state.properties().isEmpty()) {
                remappedEntry.put("Properties", properties.build());
            }
            palette.add(remappedEntry.build());
            remapped.add(index);
        }
        List<Tag> blocks = new ArrayList<>();
        for (Tag tag : root.getList("blocks").values()) {
            CompoundTag block = (CompoundTag) tag;
            int state = ((Nbt.IntTag) block.get("state")).value();
            if (remapped.contains(state) && block.get("nbt") != null) {
                Nbt.CompoundBuilder copy = Nbt.compound();
                block.values().forEach((key, value) -> {
                    if (!key.equals("nbt")) {
                        copy.put(key, value);
                    }
                });
                block = copy.build();
            }
            blocks.add(block);
        }
        CompoundTag translated = root.with("palette", ListTag.of(palette)).with("blocks", ListTag.of(blocks));
        return Nbt.writeGzip(translated);
    }

    /** Mod defaults are explicit here: Sonorous Deepslate without properties is in horn mode. */
    private static Map<String, String> properties(CompoundTag entry) {
        CompoundTag properties = entry.getCompound("Properties");
        if (properties == null) {
            return entry.getString("Name").equals("the_sift:sonorous_deepslate") ? Map.of("mode", "horn") : Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        properties.values().forEach((key, value) -> out.put(key, ((Nbt.StringTag) value).value()));
        return out;
    }
}
