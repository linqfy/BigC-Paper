package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty;

import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicAssemblyChecks;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicDungeonDatapack;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicDungeonTheme;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicExtraPiece;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicTemplateSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThirdPartySourcesTest {


    /** Pack whose extra pieces are limited to one culture (each culture is checked on its own to keep memory low). */
    private static GeneratedDatapack packFor(String culture) {
        List<MythicTemplateSource> sources = new ArrayList<>();
        for (MythicTemplateSource source : ThirdPartySources.all()) {
            sources.add(requested -> requested.equals(culture) ? source.piecesFor(requested) : List.of());
        }
        return MythicDungeonDatapack.build(sources);
    }

    @Test
    void packIsDeterministicAndContainsBothSources() throws IOException {
        GeneratedDatapack pack = MythicDungeonDatapack.build(ThirdPartySources.all());
        assertEquals(pack.contentHash(), MythicDungeonDatapack.build(ThirdPartySources.all()).contentHash());
        assertNotEquals(MythicDungeonDatapack.build().contentHash(), pack.contentHash());
        // the licence files travel inside the pack
        for (String file : List.of("yungs_better_dungeons/COPYING", "yungs_better_dungeons/LICENSE",
            "yungs_better_dungeons/NOTICE", "doomlike_dungeons/LICENSE", "doomlike_dungeons/NOTICE")) {
            assertArrayEquals(resource("/mythicdungeons/third_party/" + file), pack.file("licenses/" + file), file);
        }
        assertFalse(MythicDungeonDatapack.build().paths().stream().anyMatch(path -> path.startsWith("licenses/")));
        for (MythicTemplateSource source : ThirdPartySources.all()) {
            // The module's own validation: an invalid piece makes build() throw.
            MythicDungeonDatapack.build(List.of(source));
        }
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            String prefix = "data/bigc_myth/structure/" + theme.culture() + "/extra/";
            List<String> extras = pack.paths().stream().filter(path -> path.startsWith(prefix)).toList();
            assertTrue(extras.stream().anyMatch(id -> id.contains("/yungs/")), theme.culture() + " " + extras);
            assertTrue(extras.stream().anyMatch(id -> id.contains("/doomlike/dungeon_")), theme.culture() + " " + extras);
            assertTrue(extras.stream().anyMatch(id -> id.contains("/doomlike/room_")), theme.culture() + " " + extras);
        }
    }

    /**
     * Per culture with both sources merged: every template (built-in and third-party) passes the reference,
     * vanilla-id, altar/footprint, walkability and clearance checks, and the assembly simulation always
     * reaches the arena (walkable from the entrance) without overlaps, with bounded chests.
     */
    @Test
    void piecesValidateAndTheArenaIsAlwaysReached() {
        int fullDungeons = 0;
        int yungs = 0;
        int doomlikeRooms = 0;
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            GeneratedDatapack pack = packFor(theme.culture());
            // YUNG's crypt rooms (CORRIDOR role) are 4 blocks high inside: no 5x5x5 passage for large mobs, so
            // the module leaves them out.
            assertTrue(pack.paths().stream().noneMatch(path -> path.contains("/extra/corridor/yungs/")), theme.culture());
            assertEquals(List.of(), MythicAssemblyChecks.checkTemplates(pack, theme.culture()), theme.culture());
            Set<String> used = MythicAssemblyChecks.simulate(pack, theme.culture());
            fullDungeons += (int) used.stream().filter(path -> path.contains("/extra/full_dungeon/")
                || path.contains("/extra/wing_building/")).count();
            yungs += (int) used.stream().filter(path -> path.contains("/yungs/")).count();
            doomlikeRooms += (int) used.stream().filter(path -> path.contains("/extra/side_room/doomlike/")).count();
        }
        assertTrue(fullDungeons > 0 && yungs > 0 && doomlikeRooms > 0,
            "full " + fullDungeons + " yungs " + yungs + " doomlike rooms " + doomlikeRooms);
    }

    @Test
    void yungsPlaceholdersAreResolvedAndOriginalsShipUnmodified() throws Exception {
        YungsBetterDungeonsSource source = new YungsBetterDungeonsSource();
        for (YungsBetterDungeonsSource.Spec spec : YungsBetterDungeonsSource.ALL) {
            for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
                TemplateGrid grid = source.resolved(spec, theme);
                for (TemplateGrid.Cell cell : grid.cells()) {
                    assertFalse(YungsPlaceholders.PLACEHOLDERS.contains(cell.name()), spec.path() + " keeps " + cell.name());
                    if (cell.name().equals("minecraft:jigsaw")) {
                        assertTrue(cell.nbt().getString("name").startsWith("bigc_myth:third_party/"), spec.path());
                    }
                }
            }
            byte[] shipped = YungsBetterDungeonsSource.class
                .getResourceAsStream(YungsBetterDungeonsSource.RESOURCE_ROOT + spec.path() + ".nbt").readAllBytes();
            Path original = Path.of("work/mods/yungs/data/betterdungeons/structure", spec.path() + ".nbt");
            if (Files.exists(original)) {
                assertArrayEquals(Files.readAllBytes(original), shipped, spec.path());
            }
        }
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            Set<String> paletteIds = paletteIds(theme);
            for (MythicExtraPiece piece : source.piecesFor(theme.culture())) {
                for (String block : piece.template().paletteBlocks()) {
                    assertTrue(!YungsPlaceholders.PLACEHOLDERS.contains(block) || paletteIds.contains(block),
                        theme.culture() + " " + piece.name() + " keeps placeholder " + block);
                }
            }
        }
    }

    @Test
    void creditsNameExactlyTheTwoMods() {
        assertEquals(List.of("YUNG's Better Dungeons", "Doomlike Dungeons"),
            ThirdPartyCredits.ALL.stream().map(ThirdPartyCredits.Credit::name).toList());
        assertEquals(List.of("LGPL-3.0", "MIT"), ThirdPartyCredits.ALL.stream().map(ThirdPartyCredits.Credit::license).toList());
        for (String path : List.of("yungs_better_dungeons/LICENSE", "yungs_better_dungeons/NOTICE",
            "doomlike_dungeons/LICENSE", "doomlike_dungeons/NOTICE")) {
            assertNotNull(ThirdPartyCredits.class.getResource("/mythicdungeons/third_party/" + path), path);
        }
    }

    private static Set<String> paletteIds(MythicDungeonTheme theme) {
        MythicDungeonTheme.Palette p = theme.palette();
        Set<String> ids = new HashSet<>();
        for (BlockStateSpec spec : List.of(p.wall(), p.wallAlt(), p.floor(), p.floorAlt(), p.ceiling(), p.pillar(), p.light(),
            p.accent(), p.accent2(), p.clutter(), p.hangingLight(), p.roof())) {
            ids.add(spec.block().asString());
        }
        ids.add(p.stairs());
        ids.add(p.slab());
        return ids;
    }

    /** True when Bukkit's BlockType declares the vanilla id (read without initialising the class). */
    static boolean declaresConstant(String id) {
        if (!id.startsWith("minecraft:")) {
            return false;
        }
        String field = id.substring("minecraft:".length()).toUpperCase(Locale.ROOT);
        try {
            Class<?> type = Class.forName("org.bukkit.block.BlockType", false, ThirdPartySourcesTest.class.getClassLoader());
            Field declared = type.getDeclaredField(field);
            return type.isAssignableFrom(declared.getType());
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static byte[] resource(String path) throws IOException {
        try (InputStream input = ThirdPartySourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return input.readAllBytes();
        }
    }
}
