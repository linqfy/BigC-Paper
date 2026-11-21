package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.IntTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.ListTag;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.Tag;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;
import dev.linqfy.bigCasares.modules.mythicdungeons.AltarRule;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicDungeonTheme.Layout;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicTemplateRewriter.Jigsaw;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MythicDungeonDatapackTest {

    /** Vanilla chest loot tables the pieces may reference (all exist in 26.2). */
    private static final Set<String> VANILLA_LOOT = Set.of(
        "minecraft:chests/simple_dungeon", "minecraft:chests/abandoned_mineshaft", "minecraft:chests/stronghold_corridor",
        "minecraft:chests/stronghold_crossing", "minecraft:chests/stronghold_library", "minecraft:chests/desert_pyramid",
        "minecraft:chests/jungle_temple");
    private static final Set<String> VANILLA_HOSTILES = Set.of(
        "minecraft:zombie", "minecraft:skeleton", "minecraft:husk", "minecraft:stray", "minecraft:spider");
    private static final Map<String, Layout> FAMILIES = Map.of(
        "medieval", Layout.SURFACE, "egyptian", Layout.SURFACE, "greek", Layout.SURFACE, "arabian", Layout.SURFACE,
        "mesoamerican", Layout.SURFACE, "norse", Layout.UNDERGROUND, "mesopotamian", Layout.UNDERGROUND,
        "japanese", Layout.UNDERGROUND, "andean", Layout.UNDERGROUND);
    /** Biomes of the vanilla 26.2 {@code has_structure/village_*} tags. */
    private static final Set<String> VILLAGE_BIOMES = Set.of("minecraft:plains", "minecraft:meadow", "minecraft:desert",
        "minecraft:savanna", "minecraft:snowy_plains", "minecraft:taiga");
    private static final int MIN_TEMPLATES = 12;
    private static final int MIN_SPAWNER_TEMPLATES = 5;
    private static final int MIN_CHEST_TEMPLATES = 4;

    private static GeneratedDatapack pack;

    @BeforeAll
    static void build() {
        pack = MythicDungeonDatapack.build();
    }

    /** The decoded pack is large; the whole suite shares one test JVM, so do not keep it alive. */
    @AfterAll
    static void release() {
        pack = null;
    }

    @Test
    void buildIsDeterministic() {
        assertEquals(pack.contentHash(), MythicDungeonDatapack.build().contentHash());
        assertEquals(pack.contentHash(), MythicDungeonDatapack.build(List.of()).contentHash());
        assertTrue(pack.paths().contains("pack.mcmeta"));
    }

    @Test
    void everyCultureHasItsFamilyStructureSetTagAndPools() {
        Set<Integer> salts = new HashSet<>();
        assertEquals(FAMILIES.keySet(), MythicDungeonTheme.all().stream().map(MythicDungeonTheme::culture).collect(Collectors.toSet()));
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            MythicPackIndex index = new MythicPackIndex(pack, theme.culture());
            assertEquals(FAMILIES.get(theme.culture()), theme.layout(), theme.culture());
            String id = theme.structureId().asString();
            JsonObject structure = index.json(MythicPackIndex.jsonPath("worldgen/structure", id)).getAsJsonObject();
            assertEquals("minecraft:jigsaw", structure.get("type").getAsString());
            assertEquals("#bigc_myth:has_structure/" + theme.culture(), structure.get("biomes").getAsString());
            assertTrue(structure.get("size").getAsInt() >= 1 && structure.get("size").getAsInt() <= 20);
            String adaptation = structure.get("terrain_adaptation").getAsString();
            int maxDistance = structure.get("max_distance_from_center").getAsInt();
            // vanilla refuses the whole pack (the server does not start) above 128 including the adaptation margin
            int adaptationMargin = "none".equals(adaptation) ? 0 : 12;
            assertTrue(maxDistance + adaptationMargin <= 128, theme.culture());
            if (theme.layout() == Layout.SURFACE) {
                assertTrue(List.of("beard_thin", "beard_box").contains(adaptation), theme.culture() + " " + adaptation);
                assertEquals(116, maxDistance, theme.culture());
            } else {
                assertEquals("none", adaptation, theme.culture());
                assertEquals(128, maxDistance, theme.culture());
            }
            assertEquals("WORLD_SURFACE_WG", structure.get("project_start_to_heightmap").getAsString());
            assertTrue(index.pools.containsKey(structure.get("start_pool").getAsString()));

            JsonObject set = index.json(MythicPackIndex.jsonPath("worldgen/structure_set", id)).getAsJsonObject();
            assertEquals(id, set.getAsJsonArray("structures").get(0).getAsJsonObject().get("structure").getAsString());
            JsonObject placement = set.getAsJsonObject("placement");
            int spacing = placement.get("spacing").getAsInt();
            int separation = placement.get("separation").getAsInt();
            assertTrue(spacing > separation && spacing <= 64, theme.culture() + " spacing " + spacing);
            // two starts of one set are at least separation + 1 chunks apart; the start piece may sit up to 48 blocks
            // from its chunk corner: the footprints (max distance each way) never overlap
            assertTrue(separation * 16 - 48 > 2 * maxDistance, theme.culture() + " separation " + separation);
            assertTrue(salts.add(placement.get("salt").getAsInt()), "salts must differ");
            // a culture that shares a biome with villages keeps 10 chunks away from every village candidate
            boolean villageBiome = theme.biomes().stream().anyMatch(VILLAGE_BIOMES::contains);
            if (villageBiome) {
                JsonObject exclusion = placement.getAsJsonObject("exclusion_zone");
                assertNotNull(exclusion, theme.culture());
                assertEquals("minecraft:villages", exclusion.get("other_set").getAsString());
                assertEquals(10, exclusion.get("chunk_count").getAsInt());
            } else {
                assertFalse(placement.has("exclusion_zone"), theme.culture());
            }

            JsonObject tag = index.json(MythicPackIndex.jsonPath("tags/worldgen/biome", "bigc_myth:has_structure/" + theme.culture()))
                .getAsJsonObject();
            assertFalse(tag.getAsJsonArray("values").isEmpty());
            for (JsonElement biome : tag.getAsJsonArray("values")) {
                assertTrue(isVanillaBiome(biome.getAsString()), "unknown biome " + biome);
            }
            String startTemplate = index.pools.get(structure.get("start_pool").getAsString()).elements().getFirst().location();
            assertTrue(startTemplate.endsWith(theme.layout() == Layout.SURFACE ? "/pieces/plaza" : "/pieces/entrance"), startTemplate);
        }
    }

    /** No biome belongs to two cultures: their sets would otherwise place overlapping complexes. */
    @Test
    void cultureBiomesArePairwiseDisjoint() {
        Map<String, String> owners = new HashMap<>();
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            for (String biome : theme.biomes()) {
                String previous = owners.putIfAbsent(biome, theme.culture());
                assertTrue(previous == null, biome + " is shared by " + previous + " and " + theme.culture());
            }
        }
        // the cultures that do meet villages are exactly the ones with the exclusion zone
        assertEquals(Set.of("greek", "norse", "egyptian", "mesopotamian"), MythicDungeonTheme.all().stream()
            .filter(MythicDungeonDatapack::sharesVillageBiome).map(MythicDungeonTheme::culture).collect(Collectors.toSet()));
    }

    @Test
    void poolsTemplatesAndJigsawsResolve() {
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            checkReferences(new MythicPackIndex(pack, theme.culture()));
        }
    }

    static void checkReferences(MythicPackIndex index) {
        for (MythicPackIndex.Pool pool : index.pools.values()) {
            assertFalse(pool.elements().isEmpty(), pool.id());
            for (MythicPackIndex.Element element : pool.elements()) {
                assertNotNull(index.templates.get(element.location()), "missing template " + element.location());
            }
            assertTrue(pool.fallback().equals("minecraft:empty") || index.pools.containsKey(pool.fallback()), pool.fallback());
        }
        for (var entry : index.templates.entrySet()) {
            for (Jigsaw jigsaw : MythicTemplateRewriter.jigsaws(entry.getValue())) {
                if (jigsaw.pool().equals("minecraft:empty")) {
                    assertEquals("minecraft:empty", jigsaw.target(), entry.getKey());
                    continue;
                }
                assertTrue(index.pools.containsKey(jigsaw.pool()), entry.getKey() + " -> " + jigsaw.pool());
                MythicPackIndex.Pool pool = index.pools.get(jigsaw.pool());
                List<StructureTemplate> candidates = new ArrayList<>(index.poolTemplates(pool.id()));
                candidates.addAll(index.poolTemplates(pool.fallback()));
                for (StructureTemplate candidate : candidates) {
                    assertTrue(MythicTemplateRewriter.jigsaws(candidate).stream().anyMatch(child -> child.name().equals(jigsaw.target())),
                        candidate.id() + " has no jigsaw named " + jigsaw.target() + " for " + entry.getKey());
                }
            }
        }
    }

    @Test
    void templatesUseOnlyVanillaBlocksAndValidBlockEntities() {
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            checkTemplates(new MythicPackIndex(pack, theme.culture()));
        }
    }

    static void checkTemplates(MythicPackIndex index) {
        for (var entry : index.templates.entrySet()) {
            StructureTemplate template = entry.getValue();
            int[] size = MythicTemplateRewriter.size(template);
            for (int axis : size) {
                assertTrue(axis >= 1 && axis <= 48, entry.getKey());
            }
            assertEquals(List.of(), MythicTemplateRewriter.nonVanillaIds(template), entry.getKey());
            for (String block : template.paletteBlocks()) {
                assertTrue(Material.matchMaterial(block) != null && declaresConstant("org.bukkit.block.BlockType", block),
                    entry.getKey() + " uses unknown block " + block);
            }
            for (Tag tag : template.root().getList("blocks").values()) {
                CompoundTag nbt = ((CompoundTag) tag).getCompound("nbt");
                if (nbt == null) {
                    continue;
                }
                switch (nbt.getString("id")) {
                    case "minecraft:chest" -> assertTrue(VANILLA_LOOT.contains(nbt.getString("LootTable")), nbt.getString("LootTable"));
                    case "minecraft:mob_spawner" -> {
                        String mob = nbt.getCompound("SpawnData").getCompound("entity").getString("id");
                        assertTrue(VANILLA_HOSTILES.contains(mob), mob);
                        assertNotNull(nbt.getList("SpawnPotentials"));
                    }
                    case "minecraft:jigsaw" -> assertNotNull(nbt.getString("final_state"));
                    default -> throw new AssertionError("Unexpected block entity " + nbt.getString("id"));
                }
            }
        }
    }

    /**
     * Exactly one lodestone per culture, standing on the arena floor near its centre (the runtime altar rule),
     * and no other piece with the arena's 35x35 footprint.
     */
    @Test
    void onlyTheArenaHasTheAltarAndTheArenaFootprint() {
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            checkAltarAndFootprints(new MythicPackIndex(pack, theme.culture()), theme);
        }
    }

    static void checkAltarAndFootprints(MythicPackIndex index, MythicDungeonTheme theme) {
        int lodestones = 0;
        for (StructureTemplate template : index.cultureTemplates(theme.culture())) {
            Grid grid = new Grid(template);
            int here = grid.count("minecraft:lodestone");
            lodestones += here;
            boolean arena = template.id().path().endsWith("/pieces/arena");
            if (here > 0) {
                assertTrue(arena, "lodestone outside arena: " + template.id());
            }
            boolean arenaFootprint = grid.sizeX == AltarRule.ARENA_SIDE && grid.sizeZ == AltarRule.ARENA_SIDE;
            assertEquals(arena, arenaFootprint, template.id() + " footprint " + grid.sizeX + "x" + grid.sizeZ);
        }
        assertEquals(1, lodestones, theme.culture());
        Grid arena = new Grid(index.templates.get("bigc_myth:" + theme.culture() + "/pieces/arena"));
        int altarX = MythicPieces.ALTAR_X;
        int altarZ = MythicPieces.ALTAR_Z;
        assertEquals("minecraft:lodestone", arena.name(altarX, 1, altarZ));
        assertTrue(AltarRule.isAltarSpot(0, 0, 0, arena.sizeX - 1, arena.sizeZ - 1, altarX, 1, altarZ), theme.culture());
        assertTrue(arena.solid(altarX, 0, altarZ), "altar stands on the floor");
    }

    @Test
    void eachCultureHasAnOpenWatertightArenaAndEnoughVariety() {
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            MythicPackIndex index = new MythicPackIndex(pack, theme.culture());
            int spawnerTemplates = 0;
            int chestTemplates = 0;
            Set<String> pieces = new HashSet<>();
            for (StructureTemplate template : index.cultureTemplates(theme.culture())) {
                Grid grid = new Grid(template);
                pieces.add(template.id().path());
                spawnerTemplates += grid.count("minecraft:spawner") > 0 ? 1 : 0;
                chestTemplates += grid.count("minecraft:chest") > 0 ? 1 : 0;
            }
            assertTrue(pieces.size() >= MIN_TEMPLATES, theme.culture() + " has " + pieces.size() + " templates");
            assertTrue(spawnerTemplates >= MIN_SPAWNER_TEMPLATES, theme.culture() + " spawner rooms " + spawnerTemplates);
            assertTrue(chestTemplates >= MIN_CHEST_TEMPLATES, theme.culture() + " chest rooms " + chestTemplates);

            Grid arena = new Grid(index.templates.get("bigc_myth:" + theme.culture() + "/pieces/arena"));
            assertEquals(MythicPieces.ARENA_SIZE, arena.sizeX);
            assertEquals(MythicPieces.ARENA_SIZE, arena.sizeZ);
            assertTrue(arena.largestOpenSquare(10) >= 27, theme.culture() + " arena open square " + arena.largestOpenSquare(10));
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx != 0 || dz != 0) {
                        assertEquals("minecraft:air", arena.name(MythicPieces.ALTAR_X + dx, 1, MythicPieces.ALTAR_Z + dz),
                            "free space around altar");
                    }
                }
            }
            assertTrue(arena.watertightBelow(MythicPieces.ARENA_ENTRY_STAND), theme.culture() + " arena shell has holes");
        }
    }

    /** Walkability, doorway size, large-mob passages and spawner clearance, template by template. */
    @Test
    void everyTemplateIsWalkableAndRoomyEnough() {
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            MythicPackIndex index = new MythicPackIndex(pack, theme.culture());
            List<String> problems = new ArrayList<>();
            for (StructureTemplate template : index.cultureTemplates(theme.culture())) {
                problems.addAll(MythicTemplateRules.violations(template));
            }
            assertEquals(List.of(), problems, theme.culture());
        }
    }

    static boolean isVanillaBiome(String id) {
        return declaresConstant("org.bukkit.block.Biome", id);
    }

    /**
     * True when the API type declares a constant for the vanilla id. Read reflectively without
     * initialising the class, which would need a live server registry.
     */
    static boolean declaresConstant(String typeName, String id) {
        if (!id.startsWith("minecraft:")) {
            return false;
        }
        String field = id.substring("minecraft:".length()).toUpperCase(Locale.ROOT);
        try {
            Class<?> type = Class.forName(typeName, false, MythicDungeonDatapackTest.class.getClassLoader());
            Field declared = type.getDeclaredField(field);
            return type.isAssignableFrom(declared.getType());
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    /** Dense view of a template's blocks; unset cells are null. */
    static final class Grid {
        final int sizeX;
        final int sizeY;
        final int sizeZ;
        private final String[][][] names;

        Grid(StructureTemplate template) {
            int[] size = MythicTemplateRewriter.size(template);
            sizeX = size[0];
            sizeY = size[1];
            sizeZ = size[2];
            names = new String[sizeX][sizeY][sizeZ];
            List<Tag> palette = MythicTemplateRewriter.firstPalette(template.root()).values();
            for (Tag tag : template.root().getList("blocks").values()) {
                CompoundTag block = (CompoundTag) tag;
                ListTag pos = block.getList("pos");
                int state = ((IntTag) block.get("state")).value();
                names[value(pos, 0)][value(pos, 1)][value(pos, 2)] = ((CompoundTag) palette.get(state)).getString("Name");
            }
        }

        private static int value(ListTag list, int index) {
            return ((IntTag) list.values().get(index)).value();
        }

        String name(int x, int y, int z) {
            return names[x][y][z];
        }

        boolean solid(int x, int y, int z) {
            String name = names[x][y][z];
            return name != null && !name.equals("minecraft:air");
        }

        int count(String block) {
            int count = 0;
            for (String[][] plane : names) {
                for (String[] column : plane) {
                    for (String name : column) {
                        if (block.equals(name)) {
                            count++;
                        }
                    }
                }
            }
            return count;
        }

        /** Largest square of cells with solid floor at y=0 and air for y=1..headroom (altar allowed). */
        int largestOpenSquare(int headroom) {
            int[][] best = new int[sizeX][sizeZ];
            int result = 0;
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    boolean open = solid(x, 0, z);
                    for (int y = 1; open && y <= headroom; y++) {
                        String name = names[x][y][z];
                        open = "minecraft:air".equals(name) || (y == 1 && "minecraft:lodestone".equals(name));
                    }
                    if (open) {
                        best[x][z] = x == 0 || z == 0 ? 1
                            : 1 + Math.min(best[x - 1][z], Math.min(best[x][z - 1], best[x - 1][z - 1]));
                        result = Math.max(result, best[x][z]);
                    }
                }
            }
            return result;
        }

        /** Floor, walls and ceiling are solid except at or above the raised entrance level. */
        boolean watertightBelow(int entranceStand) {
            for (int x = 0; x < sizeX; x++) {
                for (int y = 0; y < sizeY; y++) {
                    for (int z = 0; z < sizeZ; z++) {
                        boolean shell = x == 0 || y == 0 || z == 0 || x == sizeX - 1 || y == sizeY - 1 || z == sizeZ - 1;
                        if (shell && !solid(x, y, z) && !(z == 0 && y >= entranceStand)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
    }
}
