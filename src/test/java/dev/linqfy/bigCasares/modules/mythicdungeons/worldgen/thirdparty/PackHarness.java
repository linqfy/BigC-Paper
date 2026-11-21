package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty;

import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.IntTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.ListTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.Tag;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/** Reads jigsaw blocks and sizes of templates for the third-party tests. */
final class PackHarness {

    record Jigsaw(int x, int y, int z, String front, String name, String target, String pool) {
    }

    private PackHarness() {
    }

    static List<Jigsaw> jigsaws(StructureTemplate template) {
        CompoundTag root = template.root();
        ListTag palette = root.getList("palette") != null ? root.getList("palette")
            : (ListTag) root.getList("palettes").values().getFirst();
        List<Jigsaw> result = new ArrayList<>();
        for (Tag tag : root.getList("blocks").values()) {
            CompoundTag block = (CompoundTag) tag;
            CompoundTag state = (CompoundTag) palette.values().get(((IntTag) block.get("state")).value());
            if (!"minecraft:jigsaw".equals(state.getString("Name"))) {
                continue;
            }
            String orientation = state.getCompound("Properties").getString("orientation");
            List<Tag> pos = block.getList("pos").values();
            CompoundTag nbt = block.getCompound("nbt");
            result.add(new Jigsaw(((IntTag) pos.get(0)).value(), ((IntTag) pos.get(1)).value(), ((IntTag) pos.get(2)).value(),
                orientation.substring(0, orientation.indexOf('_')), nbt.getString("name"), nbt.getString("target"),
                nbt.getString("pool")));
        }
        return result;
    }

    static int[] size(StructureTemplate template) {
        List<Tag> values = template.root().getList("size").values();
        return new int[]{((IntTag) values.get(0)).value(), ((IntTag) values.get(1)).value(), ((IntTag) values.get(2)).value()};
    }
}
