package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.datapack.DatapackBuilder;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.worldgen.StructureDefinition;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;
import dev.linqfy.bigCasares.dimension.worldgen.VerticalAnchor;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicCanvas.Face;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicPieces.Exit;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicPieces.RoomVariant;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicPieces.SideVariant;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicTemplateRewriter.Jigsaw;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the {@code bigcasares-mythic-dungeons} datapack: one jigsaw structure per culture
 * ({@code bigc_myth:<culture>}), each in its own random-spread structure set, with biome tags
 * {@code bigc_myth:has_structure/<culture>}, template pools and code-built templates.
 *
 * <p>Two layout families ({@link MythicDungeonTheme.Layout}). Each has a <em>spine</em>: a chain of
 * pools with one stage per pool, every element of a stage leading to the next, so the arena is
 * always generated. Spine exits have the highest selection and placement priority, so vanilla lays
 * the whole spine before any branch grows; spine pieces are straight (entered north, left south, all
 * centred) and their side doorways only take wing heads that stay within the parent's length, so
 * nothing placed before the spine is complete can stand in its way.</p>
 * <ul>
 *     <li>Surface: {@code start} (41x41 courtyard, start at the centre) -> {@code gatehouse} ->
 *     {@code avenue} -> {@code vestibule} -> {@code arena}; the courtyard's three other gates and the
 *     spine's side doors open onto {@code wing}, which grows into {@code branch}.</li>
 *     <li>Underground: {@code start} (surface entrance) -> {@code shaft} -> {@code stairs_a} ->
 *     {@code hub} -> {@code stairs_b} -> {@code gallery} -> {@code arena}; hub and gallery open onto
 *     {@code wing}, which grows into {@code branch} (with descents to deeper levels).</li>
 * </ul>
 * <p>Branches are trees of corridors, junctions (corner, T, cross), rooms and dead ends (treasuries,
 * dens, towers, shrines); the {@code seal} pool is the fallback that plugs any doorway that could not
 * receive a piece. Extra pieces from {@link MythicTemplateSource}s join these pools (see
 * {@link MythicExtraPiece}).</p>
 *
 * <p>The build is pure and deterministic: the same sources give the same bytes and hash.</p>
 */
public final class MythicDungeonDatapack {

    public static final String FOLDER = "bigcasares-mythic-dungeons";
    public static final String DESCRIPTION = "BigCasares: mazmorras mitologicas";
    /** Weight of each code-built element in its pool; extra pieces choose their own. */
    public static final int BUILT_IN_WEIGHT = 4;
    public static final int SIDE_ROOM_HALF_WIDTH = 4;
    public static final int CORRIDOR_MAX_DROP = 8;
    public static final int SURFACE_ENTRANCE_MAX_CONNECTOR_OFFSET = 4;
    /**
     * The underground spine ends 109 blocks past the descent (shaft, stairs, hub, stairs, gallery, arena);
     * a descent at most 16 from the start centre keeps the arena inside the 128 limit.
     */
    public static final int FULL_DUNGEON_MAX_CONNECTOR_OFFSET = 16;
    public static final int JIGSAW_SIZE = 20;
    /**
     * Vanilla rejects a jigsaw structure (and with it the whole datapack: the server does not start) when
     * {@code max_distance_from_center} plus this margin exceeds 128; every adaptation other than
     * {@code none} adds it. See {@link MythicDungeonTheme.Layout}.
     */
    public static final int ADAPTATION_MARGIN = 12;
    /** Footprint side reserved for the boss arena (runtime {@code AltarRule}); no other piece may have it. */
    public static final int ARENA_FOOTPRINT = MythicPieces.ARENA_SIZE;
    static final int MAX_TEMPLATE_SIZE = 48;
    /** Biomes of the vanilla 26.2 {@code has_structure/village_*} tags. */
    static final Set<String> VILLAGE_BIOMES = Set.of("minecraft:plains", "minecraft:meadow", "minecraft:desert",
        "minecraft:savanna", "minecraft:snowy_plains", "minecraft:taiga");
    /**
     * Cultures that share a biome with villages skip any start with a village candidate chunk within 10 chunks:
     * a complex reaches about 7 chunks from its start and a village about 3. Vanilla counts every candidate cell
     * whatever its biome, so 16 (the codec maximum) would leave only about a quarter of the starts.
     */
    static final StructureDefinition.ExclusionZone VILLAGE_EXCLUSION =
        new StructureDefinition.ExclusionZone(NamespacedId.of("minecraft", "villages"), 10);
    /** Folder of the third-party licence files inside the pack (outside {@code data/}, ignored by the game). */
    public static final String LICENSES_FOLDER = "licenses/";
    private static final String AIR = "minecraft:air";

    private MythicDungeonDatapack() {
    }

    /** The pack with only the code-built dungeons. */
    public static GeneratedDatapack build() {
        return build(List.of());
    }

    /**
     * The pack with extra pieces merged in. Throws {@link IllegalArgumentException} naming the
     * piece when a source supplies an invalid one.
     */
    public static GeneratedDatapack build(List<MythicTemplateSource> sources) {
        Objects.requireNonNull(sources, "sources");
        DatapackBuilder builder = new DatapackBuilder();
        for (MythicDungeonTheme theme : MythicDungeonTheme.all()) {
            List<MythicExtraPiece> extras = new ArrayList<>();
            for (MythicTemplateSource source : sources) {
                extras.addAll(Objects.requireNonNull(source.piecesFor(theme.culture()), "pieces"));
            }
            writeCulture(builder, theme, extras);
        }
        for (MythicTemplateSource source : sources) {
            // the licences travel with the pack, so a distributed world keeps them
            Objects.requireNonNull(source.licenseFiles(), "licenseFiles").forEach((path, content) ->
                builder.file(LICENSES_FOLDER + path, content));
        }
        return builder.build(DESCRIPTION);
    }

    /** Pool ids of a culture; also used by tests. */
    static NamespacedId pool(MythicDungeonTheme theme, String name) {
        return theme.id(name);
    }

    private record Entry(StructureTemplate template, int weight) {
    }

    private static final class Pools {
        private final LinkedHashMap<NamespacedId, List<Entry>> entries = new LinkedHashMap<>();
        private final Map<NamespacedId, NamespacedId> fallbacks = new LinkedHashMap<>();

        void define(NamespacedId pool, NamespacedId fallback) {
            entries.put(pool, new ArrayList<>());
            fallbacks.put(pool, fallback);
        }

        void add(NamespacedId pool, StructureTemplate template, int weight) {
            entries.get(pool).add(new Entry(template, weight));
        }
    }

    private static void writeCulture(DatapackBuilder builder, MythicDungeonTheme theme, List<MythicExtraPiece> extras) {
        NamespacedId start = pool(theme, "start");
        NamespacedId arena = pool(theme, "arena");
        NamespacedId wing = pool(theme, "wing");
        NamespacedId branch = pool(theme, "branch");
        NamespacedId seal = pool(theme, "seal");
        Pools pools = new Pools();
        pools.define(start, MythicPieces.EMPTY);
        StructureTemplate sealTemplate = MythicPieces.seal(theme);
        NamespacedId shaft = null;
        if (theme.layout() == MythicDungeonTheme.Layout.SURFACE) {
            surface(pools, theme, start, arena, wing, branch, seal, sealTemplate);
        } else {
            shaft = pool(theme, "shaft");
            underground(pools, theme, start, shaft, arena, wing, branch, seal, sealTemplate);
        }
        pools.add(seal, sealTemplate, 1);

        Set<String> names = new HashSet<>();
        ExtraContext context = new ExtraContext(theme, shaft, branch, seal);
        for (MythicExtraPiece extra : extras) {
            if (!names.add(extra.role() + "/" + extra.name())) {
                throw new IllegalArgumentException("Duplicate extra piece " + extra.name() + " for " + theme.culture());
            }
            boolean surface = theme.layout() == MythicDungeonTheme.Layout.SURFACE;
            // Validation throws for contract violations; pieces that do not suit the family, or cannot be made
            // walkable and roomy enough (MythicPieceSanitizer), are left out.
            switch (extra.role()) {
                case SIDE_ROOM -> {
                    // third-party side rooms are flavour without loot: the built-in dead ends hold the treasure
                    StructureTemplate room = sanitized(sideRoom(context, extra), 0);
                    if (room != null) {
                        pools.add(branch, room, extra.weight());
                    }
                }
                case CORRIDOR -> {
                    StructureTemplate corridor = sanitized(corridor(context, extra), 1);
                    if (corridor != null) {
                        pools.add(branch, corridor, extra.weight());
                    }
                }
                case SURFACE_ENTRANCE -> {
                    StructureTemplate entrance = sanitized(descentStart(context, extra, false), 1);
                    if (entrance != null && !surface) {
                        pools.add(start, entrance, extra.weight());
                    }
                }
                case FULL_DUNGEON -> {
                    StructureTemplate complex = sanitized(surface ? wingBuilding(context, extra) : descentStart(context, extra, true), 1);
                    if (complex != null) {
                        pools.add(surface ? branch : start, complex, extra.weight());
                    }
                }
            }
        }

        Set<NamespacedId> written = new HashSet<>();
        for (Map.Entry<NamespacedId, List<Entry>> pool : pools.entries.entrySet()) {
            JsonArray elements = new JsonArray();
            for (Entry entry : pool.getValue()) {
                if (written.add(entry.template().id())) {
                    builder.structureTemplate(entry.template().id(), entry.template().toBytes());
                }
                elements.add(Json.object()
                    .put("element", Json.object()
                        .put("element_type", "minecraft:single_pool_element")
                        .put("location", entry.template().id())
                        .put("processors", "minecraft:empty")
                        .put("projection", "rigid")
                        .build())
                    .put("weight", entry.weight())
                    .build());
            }
            JsonObject json = Json.object()
                .put("elements", elements)
                .put("fallback", pools.fallbacks.get(pool.getKey()))
                .build();
            builder.json(DatapackBuilder.Registry.TEMPLATE_POOL, pool.getKey(), json);
        }

        StructureDefinition definition = definition(theme, pools.entries.get(start).getFirst().template(), start);
        builder.json(DatapackBuilder.Registry.STRUCTURE, definition.id(), definition.toJson());
        builder.json(DatapackBuilder.Registry.STRUCTURE_SET, definition.id(), definition.structureSetJson());
        builder.tag("worldgen/biome", definition.biomeTag(), theme.biomes());
    }

    private static void surface(Pools pools, MythicDungeonTheme theme, NamespacedId start, NamespacedId arena,
                                NamespacedId wing, NamespacedId branch, NamespacedId seal, StructureTemplate sealTemplate) {
        NamespacedId gatehouse = pool(theme, "gatehouse");
        NamespacedId avenue = pool(theme, "avenue");
        NamespacedId vestibule = pool(theme, "vestibule");
        for (NamespacedId id : List.of(gatehouse, avenue, vestibule, wing, branch)) {
            pools.define(id, seal);
        }
        pools.define(arena, MythicPieces.EMPTY);
        pools.define(seal, MythicPieces.EMPTY);
        pools.add(start, MythicSurfacePieces.plaza(theme, gatehouse, wing), BUILT_IN_WEIGHT);
        pools.add(gatehouse, MythicSurfacePieces.gatehouse(theme, avenue, wing), BUILT_IN_WEIGHT);
        pools.add(avenue, MythicSurfacePieces.avenue(theme, vestibule, wing), BUILT_IN_WEIGHT);
        pools.add(vestibule, MythicSurfacePieces.vestibule(theme, arena, wing), BUILT_IN_WEIGHT);
        pools.add(arena, MythicPieces.arena(theme), 1);

        StructureTemplate corridor = MythicSurfacePieces.corridor(theme, branch);
        StructureTemplate cornerEast = MythicSurfacePieces.junction(theme, "corner_e", List.of(Face.EAST), branch);
        StructureTemplate cornerWest = MythicSurfacePieces.junction(theme, "corner_w", List.of(Face.WEST), branch);
        StructureTemplate tee = MythicSurfacePieces.junction(theme, "tee", List.of(Face.WEST, Face.EAST), branch);
        StructureTemplate cross = MythicSurfacePieces.junction(theme, "cross", List.of(Face.WEST, Face.EAST, Face.SOUTH), branch);
        StructureTemplate tower = MythicSurfacePieces.tower(theme);
        StructureTemplate treasury = MythicSurfacePieces.treasury(theme);
        StructureTemplate shrine = MythicSurfacePieces.shrine(theme);
        weighted(pools, wing, corridor, 5, cornerEast, 2, cornerWest, 2, tee, 3, cross, 1, tower, 3, treasury, 1, shrine, 1);
        weighted(pools, branch, corridor, 3, cornerEast, 2, cornerWest, 2, tee, 2, cross, 1,
            MythicSurfacePieces.hall(theme, branch), 2, MythicSurfacePieces.court(theme, branch), 2, tower, 2, treasury, 1,
            MythicSurfacePieces.barracks(theme), 2, shrine, 1, sealTemplate, 6);
    }

    private static void underground(Pools pools, MythicDungeonTheme theme, NamespacedId start, NamespacedId shaft,
                                    NamespacedId arena, NamespacedId wing, NamespacedId branch, NamespacedId seal,
                                    StructureTemplate sealTemplate) {
        NamespacedId stairsA = pool(theme, "stairs_a");
        NamespacedId hub = pool(theme, "hub");
        NamespacedId stairsB = pool(theme, "stairs_b");
        NamespacedId gallery = pool(theme, "gallery");
        pools.define(shaft, MythicPieces.EMPTY);
        for (NamespacedId id : List.of(stairsA, hub, stairsB, gallery, wing, branch)) {
            pools.define(id, seal);
        }
        pools.define(arena, MythicPieces.EMPTY);
        pools.define(seal, MythicPieces.EMPTY);
        pools.add(start, MythicEntrances.entrance(theme, shaft), BUILT_IN_WEIGHT);
        pools.add(shaft, MythicPieces.shaft(theme, "shaft", stairsA), 1);
        pools.add(stairsA, MythicPieces.stairs(theme, "stairs_a", hub, true), 1);
        pools.add(hub, MythicUndergroundPieces.hub(theme, stairsB, wing), 1);
        pools.add(stairsB, MythicPieces.stairs(theme, "stairs_b", gallery, true), 1);
        pools.add(gallery, MythicUndergroundPieces.gallery(theme, arena, wing), 1);
        pools.add(arena, MythicPieces.arena(theme), 1);

        StructureTemplate tunnel = MythicUndergroundPieces.tunnel(theme, branch);
        StructureTemplate cornerEast = MythicUndergroundPieces.junction(theme, "corner_e", List.of(Face.EAST), branch);
        StructureTemplate cornerWest = MythicUndergroundPieces.junction(theme, "corner_w", List.of(Face.WEST), branch);
        StructureTemplate tee = MythicUndergroundPieces.junction(theme, "tee", List.of(Face.WEST, Face.EAST), branch);
        StructureTemplate cross = MythicUndergroundPieces.junction(theme, "cross", List.of(Face.WEST, Face.EAST, Face.SOUTH), branch);
        StructureTemplate treasury = MythicPieces.sideRoom(theme, SideVariant.TREASURY);
        StructureTemplate den = MythicPieces.sideRoom(theme, SideVariant.DEN);
        StructureTemplate ossuary = MythicPieces.sideRoom(theme, SideVariant.OSSUARY);
        weighted(pools, wing, tunnel, 4, cornerEast, 2, cornerWest, 2, tee, 3, cross, 2, treasury, 1);
        weighted(pools, branch, tunnel, 2, cornerEast, 2, cornerWest, 2, tee, 2, cross, 1,
            room(theme, RoomVariant.CRYPT, branch, Face.SOUTH), 3,
            room(theme, RoomVariant.SHRINE, branch, Face.SOUTH), 2,
            room(theme, RoomVariant.PILLARED, branch, Face.WEST, Face.EAST, Face.SOUTH), 1,
            room(theme, RoomVariant.LIBRARY, branch, Face.EAST), 2,
            MythicUndergroundPieces.cavern(theme, branch), 2,
            MythicPieces.stairs(theme, "descent", branch, false), 2,
            treasury, 1, den, 3, ossuary, 1, sealTemplate, 6);
    }

    private static StructureTemplate room(MythicDungeonTheme theme, RoomVariant variant, NamespacedId branch, Face... faces) {
        List<Exit> exits = new ArrayList<>();
        for (Face face : faces) {
            exits.add(Exit.branch(face, 7, branch));
        }
        return MythicPieces.room(theme, "room_" + lower(variant), variant, exits);
    }

    /** Adds {@code template, weight} pairs to a pool. */
    private static void weighted(Pools pools, NamespacedId pool, Object... pairs) {
        for (int index = 0; index < pairs.length; index += 2) {
            pools.add(pool, (StructureTemplate) pairs[index], (Integer) pairs[index + 1]);
        }
    }

    static StructureDefinition definition(MythicDungeonTheme theme, StructureTemplate startTemplate, NamespacedId startPool) {
        StructureDefinition.TemplatePool pool = new StructureDefinition.TemplatePool(startPool,
            List.of(new StructureDefinition.PoolElement(startTemplate, null, "rigid", BUILT_IN_WEIGHT)));
        MythicDungeonTheme.Spread spread = theme.spread();
        // Start at the surface (WORLD_SURFACE_WG, sampled at the start piece's centre); start_height 0 puts
        // template layer y = 0 on the top terrain block there. Surface complexes are bearded into the
        // terrain; underground dungeons use no adaptation (see MythicDungeonTheme.Layout).
        return new StructureDefinition(theme.structureId(),
            theme.biomes().stream().map(NamespacedId::parse).toList(),
            pool, List.of(), "surface_structures", JIGSAW_SIZE, VerticalAnchor.absolute(0), "WORLD_SURFACE_WG",
            theme.layout().maxDistanceFromCenter(), theme.layout().terrainAdaptation(),
            new StructureDefinition.Placement(spread.spacing(), spread.separation(), spread.salt(),
                sharesVillageBiome(theme) ? VILLAGE_EXCLUSION : null));
    }

    /** True when a vanilla village can start in one of the culture's biomes. */
    static boolean sharesVillageBiome(MythicDungeonTheme theme) {
        return theme.biomes().stream().anyMatch(VILLAGE_BIOMES::contains);
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static StructureTemplate sanitized(StructureTemplate template, int maxChests) {
        return template == null ? null : MythicPieceSanitizer.sanitize(template, maxChests);
    }

    private record ExtraContext(MythicDungeonTheme theme, NamespacedId shaft, NamespacedId branch, NamespacedId seal) {
        NamespacedId door() {
            return MythicPieces.door(theme);
        }
    }

    private static IllegalArgumentException invalid(MythicDungeonTheme theme, MythicExtraPiece extra, String reason) {
        return new IllegalArgumentException("Extra piece " + extra.name() + " (" + extra.role() + ") for "
            + theme.culture() + ": " + reason);
    }

    private static Jigsaw connector(MythicDungeonTheme theme, MythicExtraPiece extra, List<Jigsaw> jigsaws, String name) {
        List<Jigsaw> matches = jigsaws.stream().filter(jigsaw -> jigsaw.name().equals(name)).toList();
        if (matches.size() != 1) {
            throw invalid(theme, extra, "expected exactly one jigsaw named " + name + ", found " + matches.size());
        }
        return matches.getFirst();
    }

    private static int[] validated(MythicDungeonTheme theme, MythicExtraPiece extra) {
        List<String> foreign = MythicTemplateRewriter.nonVanillaIds(extra.template());
        if (!foreign.isEmpty()) {
            throw invalid(theme, extra, "non-vanilla ids " + foreign);
        }
        int[] size = MythicTemplateRewriter.size(extra.template());
        for (int axis : size) {
            if (axis < 1 || axis > MAX_TEMPLATE_SIZE) {
                throw invalid(theme, extra, "template size must be within 1.." + MAX_TEMPLATE_SIZE + " per axis");
            }
        }
        if (size[0] == ARENA_FOOTPRINT && size[2] == ARENA_FOOTPRINT) {
            throw invalid(theme, extra, "a " + ARENA_FOOTPRINT + "x" + ARENA_FOOTPRINT + " footprint is reserved for the boss arena");
        }
        if (MythicTemplateRewriter.containsBlock(extra.template(), "minecraft:lodestone")) {
            throw invalid(theme, extra, "lodestone is reserved for the boss altar");
        }
        return size;
    }

    private static String finalState(Jigsaw jigsaw) {
        String state = jigsaw.nbt() == null ? null : jigsaw.nbt().getString("final_state");
        return state == null || state.isBlank() ? AIR : state;
    }

    private static CompoundTag neutral(Jigsaw jigsaw) {
        return MythicTemplateRewriter.jigsawNbt(MythicPieces.EMPTY, MythicPieces.EMPTY, MythicPieces.EMPTY, finalState(jigsaw), 0);
    }

    private static CompoundTag sealed(ExtraContext context, Jigsaw jigsaw) {
        return MythicTemplateRewriter.jigsawNbt(MythicPieces.EMPTY, context.door(), context.seal(), finalState(jigsaw), 0);
    }

    private static CompoundTag opening(ExtraContext context, Jigsaw jigsaw) {
        return MythicTemplateRewriter.jigsawNbt(MythicPieces.EMPTY, context.door(), context.branch(), finalState(jigsaw), 0);
    }

    private static CompoundTag entryNbt(ExtraContext context) {
        return MythicTemplateRewriter.jigsawNbt(context.door(), MythicPieces.EMPTY, MythicPieces.EMPTY, AIR, 0);
    }

    /** Position along the face plane (x for north/south faces, z for east/west faces). */
    private static int alongFace(Jigsaw jigsaw) {
        return jigsaw.front().equals("north") || jigsaw.front().equals("south") ? jigsaw.x() : jigsaw.z();
    }

    private static int faceLength(Jigsaw jigsaw, int[] size) {
        return jigsaw.front().equals("north") || jigsaw.front().equals("south") ? size[0] : size[2];
    }

    private static StructureTemplate sideRoom(ExtraContext context, MythicExtraPiece extra) {
        MythicDungeonTheme theme = context.theme();
        int[] size = validated(theme, extra);
        List<Jigsaw> jigsaws = MythicTemplateRewriter.jigsaws(extra.template());
        Jigsaw entry = connector(theme, extra, jigsaws, extra.entryConnector());
        if (!entry.horizontal()) {
            throw invalid(theme, extra, "entry connector must face horizontally");
        }
        int position = alongFace(entry);
        if (position > SIDE_ROOM_HALF_WIDTH || faceLength(entry, size) - 1 - position > SIDE_ROOM_HALF_WIDTH) {
            throw invalid(theme, extra, "extends more than " + SIDE_ROOM_HALF_WIDTH + " blocks beside its entry");
        }
        return MythicTemplateRewriter.rewrite(extra.template(), theme.id("extra/side_room/" + extra.name()),
            jigsaw -> jigsaw.index() == entry.index() ? entryNbt(context) : sealed(context, jigsaw), Set.of());
    }

    /**
     * A branch corridor; {@code null} for a surface culture when the exit is lower than the entry (surface
     * complexes keep one floor level). Side jigsaws at the entry's level open onto the branch pool.
     */
    private static StructureTemplate corridor(ExtraContext context, MythicExtraPiece extra) {
        MythicDungeonTheme theme = context.theme();
        validated(theme, extra);
        List<Jigsaw> jigsaws = MythicTemplateRewriter.jigsaws(extra.template());
        Jigsaw entry = connector(theme, extra, jigsaws, extra.entryConnector());
        Jigsaw exit = connector(theme, extra, jigsaws, extra.exitConnector());
        if (!entry.horizontal() || !exit.horizontal() || !opposite(entry.front()).equals(exit.front())) {
            throw invalid(theme, extra, "entry and exit must face horizontally in opposite directions");
        }
        int drop = entry.y() - exit.y();
        if (drop < 0 || drop > CORRIDOR_MAX_DROP) {
            throw invalid(theme, extra, "exit must be 0.." + CORRIDOR_MAX_DROP + " blocks below the entry, not " + drop);
        }
        boolean surface = theme.layout() == MythicDungeonTheme.Layout.SURFACE;
        if (surface && drop != 0) {
            return null;
        }
        return MythicTemplateRewriter.rewrite(extra.template(), theme.id("extra/corridor/" + extra.name()), jigsaw -> {
            if (jigsaw.index() == entry.index()) {
                return entryNbt(context);
            }
            if (jigsaw.index() == exit.index()) {
                return opening(context, jigsaw);
            }
            if (!jigsaw.horizontal()) {
                return neutral(jigsaw);
            }
            boolean level = !surface || jigsaw.y() == entry.y();
            return level && !jigsaw.front().equals(entry.front()) ? opening(context, jigsaw) : sealed(context, jigsaw);
        }, Set.of());
    }

    /**
     * Underground start: the entry connector faces down in the bottom layer and leads into the shaft (the
     * whole underground dungeon hangs below it). A 3x3, 4-tall opening is cleared above it; every other
     * jigsaw is neutralised, so nothing else is built at the surface.
     */
    private static StructureTemplate descentStart(ExtraContext context, MythicExtraPiece extra, boolean fullDungeon) {
        MythicDungeonTheme theme = context.theme();
        int[] size = validated(theme, extra);
        List<Jigsaw> jigsaws = MythicTemplateRewriter.jigsaws(extra.template());
        Jigsaw descent = connector(theme, extra, jigsaws, extra.entryConnector());
        int limit = fullDungeon ? FULL_DUNGEON_MAX_CONNECTOR_OFFSET : SURFACE_ENTRANCE_MAX_CONNECTOR_OFFSET;
        if (!descent.front().equals("down") || descent.y() != 0) {
            throw invalid(theme, extra, "entry connector must face down in the bottom layer");
        }
        double offsetX = Math.abs(descent.x() - (size[0] - 1) / 2.0);
        double offsetZ = Math.abs(descent.z() - (size[2] - 1) / 2.0);
        if (offsetX > limit || offsetZ > limit) {
            throw invalid(theme, extra, "entry connector is more than " + limit + " blocks from the centre");
        }
        if (context.shaft() == null) {
            return null;
        }
        Set<List<Integer>> opening = new HashSet<>();
        for (int x = descent.x() - 1; x <= descent.x() + 1; x++) {
            for (int z = descent.z() - 1; z <= descent.z() + 1; z++) {
                for (int y = 0; y <= Math.min(3, size[1] - 1); y++) {
                    if (x >= 0 && z >= 0 && x < size[0] && z < size[2] && !(x == descent.x() && y == 0 && z == descent.z())) {
                        opening.add(List.of(x, y, z));
                    }
                }
            }
        }
        String role = fullDungeon ? "full_dungeon" : "entrance";
        return MythicTemplateRewriter.rewrite(extra.template(), theme.id("extra/" + role + "/" + extra.name()), jigsaw -> {
            if (jigsaw.index() == descent.index()) {
                return MythicTemplateRewriter.jigsawNbt(MythicPieces.EMPTY, MythicPieces.shaftTop(theme), context.shaft(), AIR,
                    MythicPieces.SPINE_SELECTION, MythicPieces.SPINE_PLACEMENT);
            }
            return neutral(jigsaw);
        }, opening);
    }

    /**
     * Surface culture: a complete building becomes a dead-end wing building of the complex. Its first
     * horizontal jigsaw (template order) is the entry; every other jigsaw is neutralised (horizontal ones
     * stay open gates to the outside, the descent keeps the floor closed). {@code null} when the template
     * has no horizontal jigsaw.
     */
    private static StructureTemplate wingBuilding(ExtraContext context, MythicExtraPiece extra) {
        MythicDungeonTheme theme = context.theme();
        validated(theme, extra);
        List<Jigsaw> jigsaws = MythicTemplateRewriter.jigsaws(extra.template());
        Jigsaw descent = connector(theme, extra, jigsaws, extra.entryConnector());
        if (!descent.front().equals("down") || descent.y() != 0) {
            throw invalid(theme, extra, "entry connector must face down in the bottom layer");
        }
        Jigsaw entry = jigsaws.stream().filter(Jigsaw::horizontal).findFirst().orElse(null);
        if (entry == null) {
            return null;
        }
        String floor = theme.palette().floor().asString();
        return MythicTemplateRewriter.rewrite(extra.template(), theme.id("extra/wing_building/" + extra.name()), jigsaw -> {
            if (jigsaw.index() == entry.index()) {
                return entryNbt(context);
            }
            if (jigsaw.index() == descent.index()) {
                return MythicTemplateRewriter.jigsawNbt(MythicPieces.EMPTY, MythicPieces.EMPTY, MythicPieces.EMPTY, floor, 0);
            }
            return neutral(jigsaw);
        }, Set.of());
    }

    private static String opposite(String direction) {
        return switch (direction) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            case "up" -> "down";
            default -> "up";
        };
    }
}
