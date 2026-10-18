package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.datapack.DatapackCompiler;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import dev.linqfy.bigCasares.dimension.nbt.Nbt;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftAncientCityTest {

    @Test
    void cityCentresCarryEightHornModeSonorousBlocksAsBackingStates() throws IOException {
        SiftData data = SiftData.fromClasspath();
        BlockStateSpec horn = SiftBlocks.remap("sonorous_deepslate", Map.of("mode", "horn")).orElseThrow();
        for (String path : SiftAncientCity.CITY_CENTERS) {
            byte[] source = data.bytes("minecraft/structure/" + path + ".nbt").orElseThrow();
            Nbt.CompoundTag translated = Nbt.readGzip(SiftAncientCity.translate(source, path));
            Nbt.CompoundTag original = Nbt.readGzip(source);

            assertEquals(original.get("DataVersion"), translated.get("DataVersion"), path);
            assertEquals(original.getList("blocks").values().size(), translated.getList("blocks").values().size(), path);
            List<Nbt.Tag> palette = translated.getList("palette").values();
            int hornIndex = -1;
            for (int index = 0; index < palette.size(); index++) {
                Nbt.CompoundTag entry = (Nbt.CompoundTag) palette.get(index);
                assertTrue(entry.getString("Name").startsWith("minecraft:"), path + " still has " + entry.getString("Name"));
                if (entry.getString("Name").equals(horn.block().asString()) && entry.getCompound("Properties") != null
                    && horn.properties().equals(properties(entry))) {
                    hornIndex = index;
                }
            }
            assertTrue(hornIndex >= 0, path + " has no horn-mode backing state");
            int horns = 0;
            for (Nbt.Tag tag : translated.getList("blocks").values()) {
                Nbt.CompoundTag block = (Nbt.CompoundTag) tag;
                if (((Nbt.IntTag) block.get("state")).value() == hornIndex) {
                    horns++;
                    assertNull(block.get("nbt"), "mod block entities are dropped");
                }
            }
            assertEquals(8, horns, path);
        }
    }

    @Test
    void theRitualSettingControlsTheTemplateOverride() {
        SiftWorldgen worldgen = new SiftWorldgen(SiftData.fromClasspath(), Logger.getLogger("test"));
        GeneratedDatapack withRitual = new DatapackCompiler().compile(List.of(
            SiftDimension.create(SiftSettings.defaults(), worldgen, List.of()))).datapack();
        assertNotNull(withRitual.file("data/minecraft/structure/ancient_city/city_center/city_center_1.nbt"));

        SiftSettings defaults = SiftSettings.defaults();
        SiftSettings noRitual = new SiftSettings(defaults.seedSalt(), defaults.worldBorderDiameter(), defaults.entryPermission(),
            defaults.travelCooldownSeconds(), defaults.craftedPortal(), false, defaults.tides(), defaults.ichor(),
            defaults.blubs(), defaults.rifts(), defaults.sifters(), defaults.singers(), defaults.echoGolems(), List.of());
        GeneratedDatapack without = new DatapackCompiler().compile(List.of(SiftDimension.create(noRitual,
            new SiftWorldgen(SiftData.fromClasspath(), Logger.getLogger("test")), List.of()))).datapack();
        assertNull(without.file("data/minecraft/structure/ancient_city/city_center/city_center_1.nbt"));
        assertTrue(SiftDimension.travel(noRitual).portals().stream()
            .noneMatch(portal -> portal.id().equals(SiftDimension.ANCIENT_CITY_PORTAL)));
    }

    @Test
    void translationIsReproducible() {
        byte[] source = SiftData.fromClasspath().bytes("minecraft/structure/" + SiftAncientCity.CITY_CENTERS.getFirst() + ".nbt")
            .orElseThrow();
        assertArrayEquals(SiftAncientCity.translate(source, "a"), SiftAncientCity.translate(source, "a"));
    }

    private static Map<String, String> properties(Nbt.CompoundTag entry) {
        Map<String, String> out = new LinkedHashMap<>();
        entry.getCompound("Properties").values().forEach((key, value) -> out.put(key, ((Nbt.StringTag) value).value()));
        return out;
    }
}
