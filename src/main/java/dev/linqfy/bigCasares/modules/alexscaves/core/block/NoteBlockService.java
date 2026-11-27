package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameEvent;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Note;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.io.File;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Server side of the module blocks drawn by vanilla states ({@link BlockBase}: note block, tripwire,
 * chorus plant): placing, breaking, keeping vanilla note blocks on harp states so they never look like a
 * module block, and migrating chunks. Chunks are checked once (marked in their PDC) to convert note blocks
 * placed before the module was enabled, and once more ({@link #MIGRATION_VERSION}) to move blocks that
 * changed base from their legacy note block state to the new one.
 */
public final class NoteBlockService {
    static final String CHUNK_MARKER = "alexscaves_noteblocks_v1";
    static final String MIGRATION_KEY = "alexscaves_blocks_version";
    /** 2: blocks that are not full cubes moved from note blocks to tripwire and chorus plant states. */
    static final int MIGRATION_VERSION = 2;
    /** Non-empty sections scanned per tick while migrating. */
    static final int SECTIONS_PER_TICK = 8;

    /** Cheap test for the materials of the bases, i.e. blocks that may be a module block. */
    public static boolean isBase(Material type) {
        return type == Material.NOTE_BLOCK || type == Material.TRIPWIRE || type == Material.CHORUS_PLANT;
    }

    private final Plugin plugin;
    private final ModBlockRegistry registry;
    private final CustomItemRegistry items;
    private final NamespacedKey chunkMarker;
    private final NamespacedKey migrationKey;
    private final List<BlockData> customStates = new ArrayList<>();
    private final Map<BlockData, ModBlockRegistry.Placed> placedByData = new HashMap<>();
    private final LegacyBlockMigration<BlockData> migration;
    private final Deque<PendingChunk> pending = new ArrayDeque<>();
    private final Set<PendingChunk> queued = new HashSet<>();

    public NoteBlockService(Plugin plugin, ModBlockRegistry registry, CustomItemRegistry items) {
        this.plugin = plugin;
        this.registry = registry;
        this.items = items;
        this.chunkMarker = new NamespacedKey(plugin, CHUNK_MARKER);
        this.migrationKey = new NamespacedKey(plugin, MIGRATION_KEY);
        for (CustomBlockState state : registry.usedStates()) {
            BlockData data = Bukkit.createBlockData(state.blockDataString());
            placedByData.put(data, registry.at(state).orElseThrow());
            if (state.base() == BlockBase.NOTE_BLOCK) {
                customStates.add(data);
            }
        }
        Map<BlockData, BlockData> legacy = new HashMap<>();
        registry.legacy().forEach((old, current) ->
            legacy.put(Bukkit.createBlockData(old.blockDataString()), Bukkit.createBlockData(current.blockDataString())));
        this.migration = new LegacyBlockMigration<>(legacy);
    }

    /** Paper's global config ({@code config/paper-global.yml}); empty when the file does not exist. */
    public static YamlConfiguration loadPaperGlobal(Plugin plugin) {
        File file = new File(new File(plugin.getServer().getWorldContainer(), "config"), "paper-global.yml");
        if (!file.isFile()) {
            file = new File("config", "paper-global.yml");
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /**
     * The tripwire and chorus plant bases are only used when Paper no longer rewrites their states from their
     * neighbours: {@code disable-tripwire-updates} and {@code disable-chorus-plant-updates} both on.
     */
    public static boolean shapedBasesActive(ConfigurationSection paperGlobal) {
        return updatesDisabled(paperGlobal, "tripwire") && updatesDisabled(paperGlobal, "chorus-plant");
    }

    public ModBlockRegistry registry() {
        return registry;
    }

    /** The module block drawn at {@code block}, if any. */
    public Optional<ModBlockRegistry.Placed> placedAt(Block block) {
        if (!isBase(block.getType())) {
            return Optional.empty();
        }
        return Optional.ofNullable(placedByData.get(block.getBlockData()));
    }

    /** The module block a block state draws, if any (for events that only carry the old state). */
    public Optional<ModBlockRegistry.Placed> placedIn(BlockData data) {
        if (!isBase(data.getMaterial())) {
            return Optional.empty();
        }
        return Optional.ofNullable(placedByData.get(data));
    }

    /**
     * Vanilla {@code TripWireHookBlock.calculateState}: from a hook, up to 41 blocks of string along its facing. A
     * module string state in that run would have its attached and powered flags rewritten (another module block).
     */
    public boolean stringRunHasModuleBlock(Block hook, BlockFace facing) {
        if (!registry.shapedBasesActive()) {
            return false;
        }
        for (int i = 1; i < 42; i++) {
            Block next = hook.getRelative(facing, i);
            if (next.getType() != Material.TRIPWIRE) {
                return false;
            }
            if (isModuleState(next.getBlockData())) {
                return true;
            }
        }
        return false;
    }

    /** Whether a chorus plant module state is below or next to {@code flower} (where a flower would link to it). */
    public boolean touchesModuleChorus(Block flower) {
        if (!registry.shapedBasesActive()) {
            return false;
        }
        for (BlockFace face : new BlockFace[] {BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Block side = flower.getRelative(face);
            if (side.getType() == Material.CHORUS_PLANT && isModuleState(side.getBlockData())) {
                return true;
            }
        }
        return false;
    }

    /** Whether a block state draws a module block (current or legacy state). */
    public boolean isModuleState(BlockData data) {
        return isBase(data.getMaterial()) && placedByData.containsKey(data);
    }

    public Optional<BlockData> blockData(String blockId, String variant) {
        return registry.state(blockId, variant).map(state -> Bukkit.createBlockData(state.blockDataString()));
    }

    public boolean place(Block block, String blockId, String clickedFace) {
        String variant = BlockVariants.choose(registry.variants(blockId), clickedFace);
        Optional<BlockData> data = blockData(blockId, variant);
        data.ifPresent(value -> block.setBlockData(value, false));
        return data.isPresent();
    }

    /**
     * The top half of a two-block plant whose bottom half stands at {@code block}. Done once the placement is final
     * (MONITOR), so a plugin that cancels the placement later never leaves an orphan top half.
     */
    public void placeUpperHalf(Block block) {
        Optional<ModBlockRegistry.Placed> placed = placedAt(block);
        if (placed.isEmpty() || !TwoBlockPlants.isTall(registry, placed.get().blockId())
            || TwoBlockPlants.UPPER.equals(placed.get().variant())) {
            return;
        }
        Block above = block.getRelative(BlockFace.UP);
        if (above.isEmpty()) {
            blockData(placed.get().blockId(), TwoBlockPlants.UPPER).ifPresent(top -> above.setBlockData(top, false));
        }
    }

    /** False for a two-block plant without room for its top half (block above taken, or the build limit). */
    public boolean canPlace(Block block, String blockId) {
        return !TwoBlockPlants.isTall(registry, blockId) || TwoBlockPlants.roomForTop(block.getY(),
            block.getWorld().getMaxHeight(), block.getRelative(BlockFace.UP).isEmpty());
    }

    /** The other half of a two-block plant when it is still that plant's other half. */
    public Optional<Block> otherHalf(Block block, ModBlockRegistry.Placed placed) {
        var y = TwoBlockPlants.otherHalf(registry, placed, block.getY(),
            height -> placedAt(block.getWorld().getBlockAt(block.getX(), height, block.getZ())));
        return y.isPresent() ? Optional.of(block.getWorld().getBlockAt(block.getX(), y.getAsInt(), block.getZ()))
            : Optional.empty();
    }

    /** Vanilla note blocks stay on {@code instrument=harp}; their real instrument only matters for the sound. */
    public void normalizeVanilla(Block block) {
        if (block.getType() != Material.NOTE_BLOCK || !(block.getBlockData() instanceof NoteBlock note)) {
            return;
        }
        if (note.getInstrument() != Instrument.PIANO) {
            note.setInstrument(Instrument.PIANO);
            block.setBlockData(note, false);
        }
    }

    /** Instrument of a vanilla note block: a mob head above wins over the block below, as in vanilla. */
    public Instrument soundInstrument(Block noteBlock) {
        String head = VanillaInstrument.above(noteBlock.getRelative(0, 1, 0).getType().name());
        String name = head != null ? head : VanillaInstrument.below(noteBlock.getRelative(0, -1, 0).getType().name());
        try {
            return Instrument.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return Instrument.PIANO;
        }
    }

    public static boolean hasMobHeadAbove(Block noteBlock) {
        return VanillaInstrument.above(noteBlock.getRelative(0, 1, 0).getType().name()) != null;
    }

    /**
     * Plays a vanilla note block whose state is pinned to harp, the way vanilla would: only with
     * air or a mob head above, through {@link NotePlayEvent}, with the note particle and the
     * {@code note_block_play} game event (allays and sculk sensors listen to it).
     */
    public void playVanilla(Block block, Note note) {
        boolean head = hasMobHeadAbove(block);
        if (!head && !block.getRelative(0, 1, 0).getType().isAir()) {
            return;
        }
        NotePlayEvent play = new NotePlayEvent(block, soundInstrument(block), note);
        if (!play.callEvent()) {
            return;
        }
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().playNote(center, play.getInstrument(), play.getNote());
        if (!head) {
            block.getWorld().spawnParticle(Particle.NOTE, block.getLocation().add(0.5, 1.2, 0.5), 0,
                play.getNote().getId() / 24.0, 0, 0, 1);
        }
        block.getWorld().sendGameEvent(null, GameEvent.NOTE_BLOCK_PLAY, center.toVector());
    }

    /**
     * Paper's {@code block-updates.disable-noteblock-updates} keeps pistons, neighbours and a
     * disabled module from changing note block states. Missing file or key reads as not set.
     */
    public static boolean noteblockUpdatesDisabled(ConfigurationSection paperGlobal) {
        return updatesDisabled(paperGlobal, "noteblock");
    }

    /** Paper's {@code block-updates.disable-<kind>-updates} ({@code noteblock}, {@code tripwire}, {@code chorus-plant}). */
    public static boolean updatesDisabled(ConfigurationSection paperGlobal, String kind) {
        return paperGlobal != null && paperGlobal.getBoolean("block-updates.disable-" + kind + "-updates", false);
    }

    /**
     * Blocks that do not drop themselves without silk touch (mod loot tables): item (module id, or a vanilla
     * {@code minecraft:} id), min, max.
     */
    static final Map<String, LootDrop> LOOT = Map.of(
        "radrock_uranium_ore", new LootDrop("ac_uranium", 1, 1),
        "sulfur", new LootDrop("ac_sulfur_dust", 1, 3),
        "coprolith_coal_ore", new LootDrop("minecraft:coal", 1, 1),
        "guanostone_redstone_ore", new LootDrop("minecraft:redstone", 4, 5),
        "peering_coprolith", new LootDrop("ac_porous_coprolith", 1, 1),
        "guano_block", new LootDrop("ac_guano", 4, 4),
        // Amount: the layer count (see amount).
        "guano_layer", new LootDrop("ac_guano", 1, 1));

    /** Mod thornwood_branch loot: without shears or silk touch it drops itself half of the time (no Fortune here). */
    static final double BRANCH_DROP_CHANCE = 0.5;

    record LootDrop(String item, int min, int max) {
    }

    /**
     * Items a drop gives: guano layers drop one guano (or, with silk touch, one layer) per layer, as in the mod; a moth ball block one ball per ball;
     * a double frostmint two slabs.
     */
    static int amount(ModBlockRegistry.Placed placed, LootDrop loot) {
        if (placed.blockId().equals("frostmint") && placed.variant().equals("double")) {
            return 2;
        }
        if (placed.blockId().equals("guano_layer") || placed.blockId().equals("moth_ball")) {
            try {
                return Integer.parseInt(placed.variant());
            } catch (NumberFormatException notLayers) {
                return 1;
            }
        }
        return loot == null ? 1 : ThreadLocalRandom.current().nextInt(loot.min(), loot.max() + 1);
    }

    public List<ItemStack> drops(ModBlockRegistry.Placed placed, boolean silkTouch) {
        LootDrop loot = silkTouch ? null : LOOT.get(placed.blockId());
        if (!silkTouch && placed.blockId().equals("thornwood_branch")
            && ThreadLocalRandom.current().nextDouble() >= BRANCH_DROP_CHANCE) {
            return List.of();
        }
        String id = loot == null ? "ac_" + placed.blockId() : loot.item();
        int amount = amount(placed, loot);
        if (id.startsWith("minecraft:")) {
            Material vanilla = Material.matchMaterial(id);
            return vanilla == null ? List.of() : List.of(new ItemStack(vanilla, amount));
        }
        try {
            return List.of(items.createItemStack(id, amount));
        } catch (RuntimeException missing) {
            plugin.getLogger().warning("[AlexsCaves] Sin ítem de catálogo para el bloque " + id);
            return List.of();
        }
    }

    /**
     * First visit of a chunk with the module active: new chunks are only marked; older chunks
     * that contain module states can only hold vanilla note blocks there, so those are moved back
     * to harp states.
     */
    public int visitChunk(Chunk chunk, boolean newChunk) {
        var container = chunk.getPersistentDataContainer();
        if (container.has(chunkMarker, PersistentDataType.BYTE)) {
            return 0;
        }
        int converted = 0;
        if (!newChunk && containsCustomState(chunk)) {
            converted = convertLegacy(chunk);
        }
        container.set(chunkMarker, PersistentDataType.BYTE, (byte) 1);
        return converted;
    }

    /**
     * Base migration of a loaded chunk: new chunks are generated with the current states; older ones are
     * queued only when their palettes hold a legacy note block state, and are migrated a few sections per
     * tick by {@link #tickMigration()}. The chunk is marked once nothing is left.
     */
    public void migrateChunk(Chunk chunk, boolean newChunk) {
        if (!registry.shapedBasesActive()) {
            // The tripwire / chorus plant bases are off: nothing to move, and the chunk must not be marked as done.
            return;
        }
        var container = chunk.getPersistentDataContainer();
        if (container.getOrDefault(migrationKey, PersistentDataType.INTEGER, 0) >= MIGRATION_VERSION) {
            return;
        }
        if (newChunk || migration.isEmpty() || !containsLegacy(chunk)) {
            container.set(migrationKey, PersistentDataType.INTEGER, MIGRATION_VERSION);
            return;
        }
        PendingChunk key = new PendingChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (queued.add(key)) {
            pending.addLast(key);
        }
    }

    private boolean containsLegacy(Chunk chunk) {
        for (BlockData legacy : migration.mapping().keySet()) {
            if (chunk.contains(legacy)) {
                return true;
            }
        }
        return false;
    }

    /** Runs every tick: migrates up to {@link #SECTIONS_PER_TICK} non-empty sections of the queued chunks. */
    public void tickMigration() {
        int budget = SECTIONS_PER_TICK;
        while (budget > 0 && !pending.isEmpty()) {
            PendingChunk next = pending.peekFirst();
            World world = plugin.getServer().getWorld(next.world);
            if (world == null || !world.isChunkLoaded(next.x, next.z)) {
                // Unloaded meanwhile: it is not marked, so it is queued again when it loads.
                pending.pollFirst();
                queued.remove(next);
                continue;
            }
            Chunk chunk = world.getChunkAt(next.x, next.z);
            int minSection = world.getMinHeight() >> 4;
            if (next.snapshot != null && !chunk.isLoaded()) {
                // Defensive: an unload we did not hear about. Start the chunk over when it is back.
                pending.pollFirst();
                queued.remove(next);
                continue;
            }
            if (next.snapshot == null) {
                next.snapshot = chunk.getChunkSnapshot(false, false, false);
                next.section = minSection;
            }
            int top = (world.getMaxHeight() - 1) >> 4;
            while (budget > 0 && next.section <= top) {
                int section = next.section++;
                if (next.snapshot.isSectionEmpty(section - minSection)) {
                    continue;
                }
                budget--;
                next.migrated += migrateSection(world, chunk, next.snapshot, section);
            }
            if (next.section > top) {
                pending.pollFirst();
                queued.remove(next);
                chunk.getPersistentDataContainer().set(migrationKey, PersistentDataType.INTEGER, MIGRATION_VERSION);
                if (next.migrated > 0) {
                    plugin.getLogger().info("[AlexsCaves] " + next.migrated + " bloque(s) pasados a su base nueva en el chunk "
                        + chunk.getX() + "," + chunk.getZ());
                }
            }
        }
    }

    private int migrateSection(World world, Chunk chunk, ChunkSnapshot snapshot, int section) {
        int baseY = section << 4;
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        return migration.migrate(new LegacyBlockMigration.Section<>() {
            @Override
            public boolean candidate(int x, int y, int z) {
                return snapshot.getBlockType(x, baseY + y, z) == Material.NOTE_BLOCK;
            }

            @Override
            public BlockData get(int x, int y, int z) {
                // The live block: the snapshot may be a few ticks old.
                return world.getBlockAt(baseX + x, baseY + y, baseZ + z).getBlockData();
            }

            @Override
            public void set(int x, int y, int z, BlockData state) {
                world.getBlockAt(baseX + x, baseY + y, baseZ + z).setBlockData(state, false);
            }
        });
    }

    /**
     * A chorus flower grew at {@code source} ({@code BlockSpreadEvent}, fired before the flower becomes a plant),
     * and the new plant is linked to the chorus around it. A branch tip that grows sideways again gets two
     * horizontal links and nothing below, which is one of the module's states: the next tick the plant also links
     * down (module states never do), so a real plant never turns into a module block.
     */
    public void guardChorusGrowth(Block source) {
        if (!registry.shapedBasesActive()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (source.getType() == Material.CHORUS_PLANT && source.getBlockData() instanceof MultipleFacing plant
                && isModuleState(plant)) {
                // Module chorus states all have down=false: a plant that also links down is never one of them.
                MultipleFacing fixed = (MultipleFacing) plant.clone();
                fixed.setFace(BlockFace.DOWN, true);
                source.setBlockData(fixed, false);
            }
        });
    }

    /**
     * A chunk unloaded: drop its queued migration and snapshot (the snapshot would be stale when it loads again).
     * It was not marked as done, so loading it queues it again from the start.
     */
    public void chunkUnloaded(Chunk chunk) {
        if (queued.isEmpty()) {
            return;
        }
        PendingChunk key = new PendingChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (queued.remove(key)) {
            pending.remove(key);
        }
    }

    /** Queued chunks are not saved: unmarked chunks are queued again the next time they load. */
    public void clearMigration() {
        pending.clear();
        queued.clear();
        ModBlockStates.clear();
    }

    /** A chunk waiting for the base migration and how far it got. Identity is the position only. */
    static final class PendingChunk {
        private final UUID world;
        private final int x;
        private final int z;
        private ChunkSnapshot snapshot;
        private int section;
        private int migrated;

        PendingChunk(UUID world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof PendingChunk that && world.equals(that.world) && x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return (world.hashCode() * 31 + x) * 31 + z;
        }
    }

    public void visitLoadedChunks() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                try {
                    visitChunk(chunk, false);
                    migrateChunk(chunk, false);
                } catch (RuntimeException failure) {
                    plugin.getLogger().log(Level.WARNING, "[AlexsCaves] No se pudo revisar el chunk "
                        + chunk.getX() + "," + chunk.getZ(), failure);
                }
            }
        }
    }

    private boolean containsCustomState(Chunk chunk) {
        for (BlockData state : customStates) {
            if (chunk.contains(state)) {
                return true;
            }
        }
        return false;
    }

    private int convertLegacy(Chunk chunk) {
        World world = chunk.getWorld();
        int converted = 0;
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                    if (block.getType() == Material.NOTE_BLOCK && stateOf(block.getBlockData()).filter(NoteBlockState::isCustom).isPresent()) {
                        normalizeVanilla(block);
                        converted++;
                    }
                }
            }
        }
        if (converted > 0) {
            plugin.getLogger().info("[AlexsCaves] " + converted + " note block(s) previos pasados a estados vanilla en el chunk "
                + chunk.getX() + "," + chunk.getZ());
        }
        return converted;
    }

    static Optional<NoteBlockState> stateOf(BlockData data) {
        if (!(data instanceof NoteBlock note)) {
            return Optional.empty();
        }
        String instrument = instrumentName(note.getInstrument());
        return NoteBlockState.of(instrument, note.getNote().getId(), note.isPowered());
    }

    private static String instrumentName(Instrument instrument) {
        return switch (instrument.name()) {
            case "PIANO" -> "harp";
            case "BASS_DRUM" -> "basedrum";
            case "SNARE_DRUM" -> "snare";
            case "STICKS" -> "hat";
            case "BASS_GUITAR" -> "bass";
            case "FLUTE" -> "flute";
            case "BELL" -> "bell";
            case "GUITAR" -> "guitar";
            case "CHIME" -> "chime";
            case "XYLOPHONE" -> "xylophone";
            case "IRON_XYLOPHONE" -> "iron_xylophone";
            case "COW_BELL" -> "cow_bell";
            case "DIDGERIDOO" -> "didgeridoo";
            case "BIT" -> "bit";
            case "BANJO" -> "banjo";
            case "PLING" -> "pling";
            default -> "other";
        };
    }
}
