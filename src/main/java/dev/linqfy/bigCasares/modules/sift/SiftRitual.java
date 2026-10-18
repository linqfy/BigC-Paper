package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.dimension.travel.PortalShape;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import io.papermc.paper.event.player.PlayerItemCooldownEvent;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The Ancient City ritual that opens the Sift, ported from the mod
 * ({@code GoatHornMixin}, {@code SingerSummoner}, the Singer's horn song,
 * {@code NoteBlockMixin}, {@code SonorousConsoles}, {@code SonorousIgnition},
 * {@code SonorousAutoplay} and {@code PortalAutoActivation}):
 * <ol>
 *   <li>Ancient Cities generated with the mod's centre templates hold eight Sonorous
 *   Deepslate blocks (horn mode) beside the reinforced deepslate frame
 *   ({@link SiftAncientCity}).</li>
 *   <li>Blowing a goat horn within 40 blocks of the frame summons the Singer three
 *   seconds later. She rises in the frame, sings, and sends a sound wave to each
 *   horn-mode Sonorous Deepslate within 20 blocks, turning it into note mode, then
 *   digs back down.</li>
 *   <li>Note blocks on note-mode Sonorous Deepslate play the eight Sift notes instead
 *   of vanilla notes: right-click selects the note, striking plays it.</li>
 *   <li>Striking the notes {@code 1 3 7 6 5 2 4 8} makes the console replay the melody
 *   with coloured beams and lights the frame as a portal to the Sift; the reverse
 *   melody closes it.</li>
 * </ol>
 *
 * <p>Differences: the Singer is a BetterModel model on a marker armour stand (no
 * hitbox, no AI; the mod's roaming and bartering Singer is {@link SiftSingers}); sound waves and
 * beams are vanilla particles; a console is the set of note-mode blocks within 16
 * blocks horizontally and 8 vertically of the struck one (the mod chains groups);
 * console and autoplay state is not saved across restarts; the portal fills
 * instantly instead of growing from the edges.</p>
 */
final class SiftRitual implements Listener {

    private static final int FRAME_RADIUS = 40;
    private static final int SONOROUS_RADIUS = 20;
    private static final int ACTIVATION_RADIUS = 10;
    private static final int CONSOLE_HORIZONTAL = 16;
    private static final int CONSOLE_VERTICAL = 8;
    private static final long SUMMON_DELAY_TICKS = 60L;
    private static final long IGNITION_DELAY_TICKS = 10L;
    private static final long BURST_TO_AUTOPLAY_TICKS = 20L;
    private static final long AUTOPLAY_STEP_TICKS = 15L;
    /** {@code singer.animation.json} lengths (appear 7.375 s, sing 8.0417 s, disappear 5 s). */
    private static final int APPEAR_TICKS = 148;
    private static final int SING_TICKS = 161;
    private static final int DISAPPEAR_TICKS = 100;
    /** When each sound wave leaves during the song (the mod's {@code SOUND_WAVE_TICKS}). */
    private static final int[] WAVE_TICKS = {3, 25, 42, 57, 75, 92, 98, 117};
    private static final double WAVE_SPEED = 0.6;
    /** {@code SonorousColors.BASE_RGB}, one colour per Sift note. */
    private static final int[] NOTE_COLORS = {11546150, 3949738, 16351261, 8991416, 1481884, 8439583, 16701501, 15961002};

    private record ConsoleKey(UUID world, int x, int y, int z) {
    }

    private record NotePos(int x, int y, int z) {
    }

    private static final class Console {
        private final SonorousSequence<NotePos> sequence = new SonorousSequence<>();
        private int generation;
        private boolean autoplaying;
    }

    private final JavaPlugin plugin;
    private final BukkitRuntimeRegistrations registrations;
    private final Supplier<Optional<DimensionPlatform>> platform;
    private final JavaModelGateway models;
    private final BlockData hornData;
    private final BlockData noteData;
    private final BlockData reinforcedData = Material.REINFORCED_DEEPSLATE.createBlockData();
    private final Map<ConsoleKey, Console> consoles = new HashMap<>();
    private final Set<PortalShape> singingFrames = new HashSet<>();
    private final Set<Performance> performances = new LinkedHashSet<>();
    private boolean warnedMissingModel;

    SiftRitual(JavaPlugin plugin, BukkitRuntimeRegistrations registrations, Supplier<Optional<DimensionPlatform>> platform,
               JavaModelGateway models) {
        this.plugin = plugin;
        this.registrations = registrations;
        this.platform = platform;
        this.models = models;
        this.hornData = blockData(Map.of("mode", "horn"));
        this.noteData = blockData(Map.of("mode", "note"));
    }

    private static BlockData blockData(Map<String, String> properties) {
        BlockStateSpec spec = SiftBlocks.remap("sonorous_deepslate", properties).orElseThrow();
        return Bukkit.createBlockData(spec.asString());
    }

    void start() {
        registrations.registerListener("sift-ritual", this);
        registrations.ownCleanup("sift-ritual-performances", () -> {
            List.copyOf(performances).forEach(Performance::stop);
            consoles.clear();
        });
    }

    private boolean isHorn(Block block) {
        return block.getBlockData().equals(hornData);
    }

    private boolean isNote(Block block) {
        return block.getBlockData().equals(noteData);
    }

    // --- note blocks (NoteBlockMixin) ----------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        ItemStack item = event.getItem();
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.NOTE_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block below = block.getRelative(BlockFace.DOWN);
        boolean horn = isHorn(below);
        if (!horn && !isNote(below)) {
            return;
        }
        Player player = event.getPlayer();
        if (action == Action.RIGHT_CLICK_BLOCK) {
            if (player.isSneaking() && item != null && !item.getType().isAir()) {
                return;
            }
            event.setCancelled(true);
            if (horn) {
                glitch(block);
                return;
            }
            interrupt(console(below), block);
            NoteBlock data = (NoteBlock) block.getBlockData();
            int next = (data.getNote().getId() % 8 + 1) % 8;
            data.setNote(new Note(next));
            block.setBlockData(data, false);
            playSonorous(block, next);
            player.incrementStatistic(Statistic.NOTEBLOCK_TUNED);
        } else if (action == Action.LEFT_CLICK_BLOCK && !horn) {
            Console console = console(below);
            interrupt(console, block);
            int sound = ((NoteBlock) block.getBlockData()).getNote().getId() % 8 + 1;
            console.sequence.play(new NotePos(block.getX(), block.getY(), block.getZ()), sound)
                .ifPresent(match -> ignite(block.getWorld(), console, match, player));
        }
    }

    /** Every Sonorous note plays the Sift sound instead of the vanilla note (redstone included). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        Block block = event.getBlock();
        Block below = block.getRelative(BlockFace.DOWN);
        if (isHorn(below)) {
            event.setCancelled(true);
            glitch(block);
        } else if (isNote(below)) {
            event.setCancelled(true);
            playSonorous(block, event.getNote().getId() % 8);
        }
    }

    private void playSonorous(Block noteBlock, int index) {
        Location center = noteBlock.getLocation().add(0.5, 0.5, 0.5);
        noteBlock.getWorld().playSound(center, "the_sift:block.sonorous_deepslate.sound_" + (index + 1),
            SoundCategory.RECORDS, 3.0F, 1.0F);
        noteBlock.getWorld().spawnParticle(Particle.NOTE, noteBlock.getLocation().add(0.5, 1.2, 0.5), 0,
            index * 3 / 24.0, 0.0, 0.0, 1.0);
    }

    private void glitch(Block block) {
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), "the_sift:sonorous_autoplay_glitch",
            SoundCategory.RECORDS, 3.0F, 1.0F);
    }

    /** The console of a note-mode block, keyed by its lowest block (the mod's anchor). */
    private Console console(Block sonorous) {
        Block anchor = sonorous;
        for (int dx = -CONSOLE_HORIZONTAL; dx <= CONSOLE_HORIZONTAL; dx++) {
            for (int dy = -CONSOLE_VERTICAL; dy <= CONSOLE_VERTICAL; dy++) {
                for (int dz = -CONSOLE_HORIZONTAL; dz <= CONSOLE_HORIZONTAL; dz++) {
                    Block candidate = sonorous.getRelative(dx, dy, dz);
                    if (isLower(candidate, anchor) && isNote(candidate)) {
                        anchor = candidate;
                    }
                }
            }
        }
        ConsoleKey key = new ConsoleKey(sonorous.getWorld().getUID(), anchor.getX(), anchor.getY(), anchor.getZ());
        return consoles.computeIfAbsent(key, ignored -> new Console());
    }

    private static boolean isLower(Block candidate, Block anchor) {
        return Comparator.comparingInt(Block::getY).thenComparingInt(Block::getX).thenComparingInt(Block::getZ)
            .compare(candidate, anchor) < 0;
    }

    /** Touching a console cancels its pending ignition or replay; a broken-off replay crackles. */
    private void interrupt(Console console, Block touched) {
        console.generation++;
        if (console.autoplaying) {
            console.autoplaying = false;
            glitch(touched);
        }
    }

    // --- ignition and replay (SonorousIgnition, SonorousAutoplay) -------------------------

    private void ignite(World world, Console console, SonorousSequence.Match<NotePos> match, Player cause) {
        int generation = console.generation;
        registrations.scheduleDelayed("sift-ritual-ignition", () -> {
            if (console.generation != generation || !intact(world, match)) {
                return;
            }
            for (SonorousSequence.Note<NotePos> note : match.notes()) {
                burst(block(world, note), note.sound());
            }
            registrations.scheduleDelayed("sift-ritual-autoplay",
                () -> replay(world, console, generation, match, cause, 0), BURST_TO_AUTOPLAY_TICKS);
        }, IGNITION_DELAY_TICKS);
    }

    private void replay(World world, Console console, int generation, SonorousSequence.Match<NotePos> match,
                        Player cause, int index) {
        if (console.generation != generation) {
            return;
        }
        if (index >= match.notes().size()) {
            console.autoplaying = false;
            finish(world, match, cause);
            return;
        }
        console.autoplaying = true;
        SonorousSequence.Note<NotePos> note = match.notes().get(index);
        Block noteBlock = block(world, note);
        if (noteBlock.getType() != Material.NOTE_BLOCK || !isNote(noteBlock.getRelative(BlockFace.DOWN))) {
            console.autoplaying = false;
            glitch(noteBlock);
            return;
        }
        playSonorous(noteBlock, note.sound() - 1);
        burst(noteBlock, note.sound());
        beam(noteBlock, note.sound());
        registrations.scheduleDelayed("sift-ritual-autoplay",
            () -> replay(world, console, generation, match, cause, index + 1), AUTOPLAY_STEP_TICKS);
    }

    private boolean intact(World world, SonorousSequence.Match<NotePos> match) {
        for (SonorousSequence.Note<NotePos> note : match.notes()) {
            Block noteBlock = block(world, note);
            if (noteBlock.getType() != Material.NOTE_BLOCK || !isNote(noteBlock.getRelative(BlockFace.DOWN))) {
                return false;
            }
        }
        return true;
    }

    private static Block block(World world, SonorousSequence.Note<NotePos> note) {
        return world.getBlockAt(note.position().x(), note.position().y(), note.position().z());
    }

    private static Particle.DustOptions dust(int sound, float size) {
        return new Particle.DustOptions(Color.fromRGB(NOTE_COLORS[sound - 1]), size);
    }

    private static void burst(Block noteBlock, int sound) {
        noteBlock.getWorld().spawnParticle(Particle.DUST, noteBlock.getLocation().add(0.5, 1.1, 0.5), 18,
            0.35, 0.35, 0.35, 0.0, dust(sound, 1.4F));
    }

    /** A coloured column over the console note, shown for a few seconds (the mod's beam). */
    private void beam(Block noteBlock, int sound) {
        Location base = noteBlock.getLocation().add(0.5, 1.0, 0.5);
        Particle.DustOptions color = dust(sound, 1.1F);
        BukkitTask[] task = new BukkitTask[1];
        int[] runs = {0};
        task[0] = registrations.scheduleRepeating("sift-ritual-beam", () -> {
            for (double y = 0.0; y < 14.0; y += 0.5) {
                noteBlock.getWorld().spawnParticle(Particle.DUST, base.clone().add(0.0, y, 0.0), 1, 0.04, 0.0, 0.04,
                    0.0, color);
            }
            if (++runs[0] >= 30) {
                task[0].cancel();
            }
        }, 0L, 5L);
    }

    // --- portal (PortalAutoActivation) ---------------------------------------------------

    private void finish(World world, SonorousSequence.Match<NotePos> match, Player cause) {
        Optional<DimensionPlatform> dimensions = platform.get();
        if (dimensions.isEmpty()) {
            tell(cause, "§5La Sift no está disponible en este servidor.");
            return;
        }
        boolean opening = match.type() == SonorousSequence.Type.OPENING;
        for (Block frame : frameBlocksNear(world, match)) {
            if (opening) {
                Optional<PortalShape> lit = dimensions.get().lightPortal(SiftDimension.KEY, SiftDimension.ANCIENT_CITY_PORTAL,
                    frame, cause);
                if (lit.isPresent()) {
                    Location center = center(world, lit.get());
                    world.playSound(frame.getLocation(), "the_sift:block.sift_portal.trigger", SoundCategory.BLOCKS, 1.0F, 1.0F);
                    world.playSound(center, "the_sift:the_sift_portal_open", SoundCategory.BLOCKS, 4.0F, 1.0F);
                    world.spawnParticle(Particle.END_ROD, center, 60, lit.get().width() / 3.0, lit.get().height() / 3.0,
                        0.3, 0.02);
                    return;
                }
            } else if (dimensions.get().extinguishPortal(frame)) {
                world.playSound(frame.getLocation(), "the_sift:the_sift_portal_close", SoundCategory.BLOCKS, 4.0F, 1.0F);
                return;
            }
        }
        if (opening && dimensions.get().world(SiftDimension.KEY).isEmpty()) {
            tell(cause, "§5El portal tiembla, pero la Sift aún no está abierta (reinicio pendiente).");
        }
    }

    private List<Block> frameBlocksNear(World world, SonorousSequence.Match<NotePos> match) {
        Set<Block> seen = new LinkedHashSet<>();
        for (SonorousSequence.Note<NotePos> note : match.notes()) {
            for (int dx = -ACTIVATION_RADIUS; dx <= ACTIVATION_RADIUS; dx++) {
                for (int dy = -ACTIVATION_RADIUS; dy <= ACTIVATION_RADIUS; dy++) {
                    for (int dz = -ACTIVATION_RADIUS; dz <= ACTIVATION_RADIUS; dz++) {
                        Block candidate = world.getBlockAt(note.position().x() + dx, note.position().y() + dy,
                            note.position().z() + dz);
                        if (candidate.getType() == Material.REINFORCED_DEEPSLATE) {
                            seen.add(candidate);
                        }
                    }
                }
            }
        }
        return List.copyOf(seen);
    }

    private static Location center(World world, PortalShape shape) {
        return new Location(world, shape.centerX(), shape.minY() + shape.height() / 2.0, shape.centerZ());
    }

    private static void tell(Player player, String message) {
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    // --- the Singer (GoatHornMixin, SingerSummoner, SingerEntity) ----------------------------

    /** A goat horn gets its cooldown exactly when it is played (the mod hooks {@code InstrumentItem.play}). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHornCooldown(PlayerItemCooldownEvent event) {
        if (event.getType() == Material.GOAT_HORN) {
            hornBlown(event.getPlayer());
        }
    }

    private void hornBlown(Player player) {
        Location origin = player.getLocation().clone();
        Optional<DimensionPlatform> dimensions = platform.get();
        if (dimensions.isPresent() && dimensions.get().dimensionOf(player.getWorld()).isEmpty()
            && nearestFrame(dimensions.get(), origin).isPresent()) {
            SiftAdvancements.award(player, SiftAdvancements.SONG_OF_THE_PAST);
        }
        registrations.scheduleDelayed("sift-singer-summon", () -> summon(origin), SUMMON_DELAY_TICKS);
    }

    private void summon(Location origin) {
        Optional<DimensionPlatform> dimensions = platform.get();
        World world = origin.getWorld();
        if (dimensions.isEmpty() || world == null || dimensions.get().dimensionOf(world).isPresent()) {
            return;
        }
        Optional<PortalShape> frame = nearestFrame(dimensions.get(), origin);
        if (frame.isEmpty() || !singingFrames.add(frame.get())) {
            return;
        }
        Location spawn = new Location(world, frame.get().centerX(), frame.get().minY(), frame.get().centerZ());
        Block sonorous = nearest(world, spawn, SONOROUS_RADIUS, block -> isHorn(block) || isNote(block));
        spawn.setYaw(cardinalYaw(spawn, sonorous));
        Performance performance = new Performance(frame.get(), spawn);
        performances.add(performance);
        performance.begin();
    }

    /** Nearest complete Ancient City frame within 40 blocks, using chunk palettes to skip empty chunks. */
    private Optional<PortalShape> nearestFrame(DimensionPlatform dimensions, Location origin) {
        World world = origin.getWorld();
        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();
        PortalShape best = null;
        double bestDistance = FRAME_RADIUS * FRAME_RADIUS;
        Set<PortalShape> checked = new HashSet<>();
        for (int cx = (ox - FRAME_RADIUS) >> 4; cx <= (ox + FRAME_RADIUS) >> 4; cx++) {
            for (int cz = (oz - FRAME_RADIUS) >> 4; cz <= (oz + FRAME_RADIUS) >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    continue;
                }
                Chunk chunk = world.getChunkAt(cx, cz);
                if (!chunk.contains(reinforcedData)) {
                    continue;
                }
                ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
                int minY = Math.max(world.getMinHeight(), oy - FRAME_RADIUS);
                int maxY = Math.min(world.getMaxHeight() - 1, oy + FRAME_RADIUS);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = minY; y <= maxY; y++) {
                            if (snapshot.getBlockType(lx, y, lz) != Material.REINFORCED_DEEPSLATE) {
                                continue;
                            }
                            Block block = world.getBlockAt((cx << 4) + lx, y, (cz << 4) + lz);
                            Optional<PortalShape> shape = dimensions.findPortalFrame(SiftDimension.KEY,
                                SiftDimension.ANCIENT_CITY_PORTAL, block);
                            if (shape.isEmpty() || !checked.add(shape.get())) {
                                continue;
                            }
                            double dx = shape.get().centerX() - origin.getX();
                            double dy = shape.get().minY() - origin.getY();
                            double dz = shape.get().centerZ() - origin.getZ();
                            double distance = dx * dx + dy * dy + dz * dz;
                            if (distance <= bestDistance) {
                                bestDistance = distance;
                                best = shape.get();
                            }
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static Block nearest(World world, Location center, int radius, java.util.function.Predicate<Block> test) {
        Block best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Block block : sphere(world, center, radius)) {
            if (!test.test(block)) {
                continue;
            }
            double distance = block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = block;
            }
        }
        return best;
    }

    private static List<Block> sphere(World world, Location center, int radius) {
        List<Block> blocks = new ArrayList<>();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int y = cy + dy;
                    if (dx * dx + dy * dy + dz * dz <= radius * radius && y >= world.getMinHeight() && y < world.getMaxHeight()) {
                        blocks.add(world.getBlockAt(cx + dx, y, cz + dz));
                    }
                }
            }
        }
        return blocks;
    }

    /** {@code SingerSummoner.getCardinalDirection}: face the nearest Sonorous Deepslate. */
    private static float cardinalYaw(Location origin, Block target) {
        if (target == null) {
            return 0.0F;
        }
        double dx = target.getX() + 0.5 - origin.getX();
        double dz = target.getZ() + 0.5 - origin.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0.0 ? -90.0F : 90.0F;
        }
        return dz >= 0.0 ? 0.0F : 180.0F;
    }

    /** One appearance of the Singer: appear, sing (sound waves convert horns), disappear. */
    private final class Performance {

        private record Wave(Block target, Vector start, Vector step, int travelTicks) {
        }

        private final PortalShape frame;
        private final Location spawn;
        private final List<Wave> waves = new ArrayList<>();
        private final Map<Wave, Integer> launched = new HashMap<>();
        private List<Block> targets = List.of();
        private ArmorStand anchor;
        private JavaModelHandle model;
        private BukkitTask task;
        private int tick;

        private Performance(PortalShape frame, Location spawn) {
            this.frame = frame;
            this.spawn = spawn;
        }

        private void begin() {
            World world = spawn.getWorld();
            anchor = world.spawn(spawn, ArmorStand.class, stand -> {
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setSilent(true);
                stand.setPersistent(false);
                stand.setRotation(spawn.getYaw(), 0.0F);
            });
            try {
                model = models.attach(anchor, SiftCreatures.SINGER_MODEL);
                models.scale(model, 1.2F);
                models.animate(model, "appear");
            } catch (RuntimeException unavailable) {
                model = null;
                if (!warnedMissingModel) {
                    warnedMissingModel = true;
                    plugin.getLogger().log(Level.WARNING, "[TheSift] Singer model unavailable; the ritual uses particles only",
                        unavailable);
                }
            }
            world.playSound(spawn, "the_sift:entity.singer.emerge", SoundCategory.HOSTILE, 4.0F, 1.0F);
            task = registrations.scheduleRepeating("sift-singer", this::tick, 1L, 1L);
        }

        private void tick() {
            World world = spawn.getWorld();
            tick++;
            if (tick < APPEAR_TICKS || tick > APPEAR_TICKS + SING_TICKS) {
                world.spawnParticle(Particle.BLOCK, spawn, 6, 0.4, 0.05, 0.4, Material.DEEPSLATE.createBlockData());
            }
            if (tick == APPEAR_TICKS) {
                if (model != null) {
                    models.animate(model, "sing");
                }
                world.playSound(spawn, "the_sift:entity.singer.sing", SoundCategory.NEUTRAL, 16.0F, 1.0F);
                targets = hornTargets();
            }
            int song = tick - APPEAR_TICKS;
            for (int index = 0; index < WAVE_TICKS.length && index < targets.size(); index++) {
                if (song == WAVE_TICKS[index]) {
                    launch(targets.get(index));
                }
            }
            advanceWaves();
            if (tick == APPEAR_TICKS + SING_TICKS) {
                if (model != null) {
                    models.stopAnimation(model, "sing");
                    models.animate(model, "disappear");
                }
                world.playSound(spawn, "the_sift:entity.singer.dig", SoundCategory.HOSTILE, 4.0F, 1.0F);
            }
            if (tick >= APPEAR_TICKS + SING_TICKS + DISAPPEAR_TICKS && waves.isEmpty()) {
                stop();
            }
        }

        /** {@code SingerEntity.prepareHornTargets}: right-hand side first, outside-in then inside-out. */
        private List<Block> hornTargets() {
            World world = spawn.getWorld();
            double yaw = Math.toRadians(spawn.getYaw());
            Vector forward = new Vector(-Math.sin(yaw), 0.0, Math.cos(yaw));
            Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
            Vector origin = spawn.toVector();
            List<Block> candidates = new ArrayList<>();
            for (Block block : sphere(world, spawn, SONOROUS_RADIUS)) {
                if (isHorn(block)) {
                    candidates.add(block);
                }
            }
            candidates.sort(Comparator.comparingDouble(
                (Block block) -> centerOf(block).subtract(origin).dot(right)).reversed());
            if (candidates.size() > 8) {
                candidates = new ArrayList<>(candidates.subList(0, 8));
            }
            int split = (candidates.size() + 1) / 2;
            List<Block> first = new ArrayList<>(candidates.subList(0, split));
            List<Block> second = new ArrayList<>(candidates.subList(split, candidates.size()));
            first.sort(Comparator.comparingDouble((Block block) -> centerOf(block).distanceSquared(origin)).reversed());
            second.sort(Comparator.comparingDouble(block -> centerOf(block).distanceSquared(origin)));
            List<Block> result = new ArrayList<>(first);
            result.addAll(second);
            return result;
        }

        private void launch(Block target) {
            if (!isHorn(target)) {
                return;
            }
            Vector start = spawn.toVector().add(new Vector(0.0, 3.0, 0.0));
            Vector delta = centerOf(target).subtract(start);
            double distance = delta.length();
            if (distance < 0.001) {
                convert(target);
                return;
            }
            Wave wave = new Wave(target, start, delta.clone().multiply(WAVE_SPEED / distance),
                Math.max(1, (int) Math.ceil(distance / WAVE_SPEED)));
            waves.add(wave);
            launched.put(wave, 0);
        }

        private void advanceWaves() {
            World world = spawn.getWorld();
            for (Wave wave : List.copyOf(waves)) {
                int travelled = launched.merge(wave, 1, Integer::sum);
                Vector head = wave.start().clone().add(wave.step().clone().multiply(travelled));
                world.spawnParticle(Particle.SCULK_CHARGE_POP, head.toLocation(world), 2, 0.05, 0.05, 0.05, 0.0);
                world.spawnParticle(Particle.DUST, head.toLocation(world), 1, 0.0, 0.0, 0.0, 0.0,
                    new Particle.DustOptions(Color.fromRGB(0x5FD7E8), 1.3F));
                if (travelled >= wave.travelTicks()) {
                    convert(wave.target());
                    waves.remove(wave);
                    launched.remove(wave);
                }
            }
        }

        /** {@code SingerEntity.convertHornToNote}. */
        private void convert(Block target) {
            if (!isHorn(target)) {
                return;
            }
            target.setBlockData(noteData, false);
            target.getWorld().playEffect(target.getLocation(), Effect.DESTROY_BLOCK, hornData);
        }

        private void stop() {
            if (task != null) {
                task.cancel();
            }
            if (model != null) {
                models.close(model);
                model = null;
            }
            if (anchor != null) {
                anchor.remove();
            }
            singingFrames.remove(frame);
            performances.remove(this);
        }
    }

    private static Vector centerOf(Block block) {
        return block.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5));
    }
}
