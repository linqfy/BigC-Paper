package dev.linqfy.bigCasares.modules.sift;

import com.destroystokyo.paper.entity.Pathfinder;
import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.logging.Level;

/**
 * The Echo Golem, ported from the mod's {@code EchoGolemEntity} and {@code SiftEchoGolemSpawner}: a golem that
 * wanders the Sift looking for Soul Canyons, takes a Soul Block out of one and carries it to a Singer, or to the
 * player who gave it an Echo Shard. Vanilla clients cannot know the mod's entity type, so a golem is a silent
 * <b>wolf</b> (the server-side body, marked in its PersistentDataContainer) dressed with the converted GeckoLib
 * model through BetterModel, like the Blub. {@link EchoGolemBrain} is the mod's mind, written without Bukkit;
 * this class gives it a body, a world and a model.
 *
 * <p><b>Why a wolf.</b> The body has to be a peaceful, goal-based {@code Mob} (so {@code MobGoals} can strip it of
 * every behaviour of its own and the brain steers it through the pathfinder) whose size can be made the mod's
 * 1.15 x 1.65 by the {@code scale} attribute alone: a wolf is 0.6 x 0.85, and at 1.94 it is 1.16 x 1.65. An iron golem,
 * the nearer relative, would have to be both shrunk and flattened to fit, and it heals with iron and is attacked
 * by zombies.</p>
 *
 * <p>Ported from the mod: the spawner ({@link EchoGolemSpawning}: one golem at every Soul Canyon, and one near each
 * player where none is within 165 blocks), 34 health, 0.23 speed, 128 follow range, 1.5 step height, 0.25
 * knockback resistance, the whole mission and route state machine with its timers, rejection memories and
 * watchdogs, picking a Soul Block (the block is removed after 18 ticks of the pickup animation), carrying it (the
 * model shows it, the golem keeps loaded), delivery to a Singer through the soul exchange (bow, hand-over,
 * {@code like_father_and_son}) or to the bonded player ({@code friend_of_the_sift}), bonding with an Echo Shard
 * (hearts), dropping the Soul Block when right-clicked or hurt and sitting for 30 seconds afterwards, 4-7 experience,
 * the {@code entity.echo_golem.*} sounds, and the save data (the golem's state is kept in its entity data).</p>
 *
 * <p>Differences:</p>
 * <ul>
 *   <li>The body is a wolf 1.16 x 1.65 (eye height 1.32 instead of 1.17). The model is pinned to scale 1 so BetterModel
 *       does not grow it with the scale attribute. The converted model renames the mod's walk animation
 *       ({@code search_walk}) and leaves out the animations that move nothing ({@code idle}, {@code carry_idle},
 *       {@code soul_visible}, the head poses), so the poses of {@link ModelPoses} are the only animations playing; the
 *       carried Soul Block is hidden by a constant {@code soul_hidden} animation that stops when the golem has one.</li>
 *   <li>Walking is Paper's pathfinder, not the mod's: the penalties for leaves, cocoa and cautious damage
 *       and the search limit are not adjustable, so a golem does not walk through leaves (the willows' foliage);
 *       searches over sets of goals and prepared paths become single-goal searches (see {@link EchoGolemBrain}); whether
 *       a path is stuck is judged by the watchdogs only; "pressed against a step" is a collision test one tenth of a
 *       block ahead. The golem has no float goal, like the mod's: it walks on the bottom of Ichor.</li>
 *   <li>Soul Blocks and canyons are found through {@link SiftLandmarks}, filled by canyon generation, by placing a
 *       Soul Block and by loading a chunk that has one. A canyon carved before this version has Soul Blocks but no
 *       recorded entrance or midpoint, so golems do not route to it (they still take Soul Blocks they come across).
 *       The mod's legacy scan of canyons without records is not ported, nor the spawn table entry in the biomes
 *       (the spawner replaces it).</li>
 *   <li>The golem looks where it walks; the mod's looking at players within six blocks and looking around, and the
 *       head following the head rotation, are not ported, nor is the glowing outline layer. The Soul Block's glow
 *       and dynamic light are client-side features of the mod and have substitutes: the {@code soul_block} bone (which
 *       the mod's {@code EchoGolemSoulGlowLayer} draws at full brightness, and which is hidden unless the golem
 *       carries one) is drawn at full brightness through BetterModel's per-bone brightness, and
 *       {@link SiftDynamicLights} moves a level-14 light block 2.125 blocks above the golem while it carries a Soul Block.
 *       The golem has no drops (the mod's table does not exist) and anything the wolf would drop is removed.</li>
 *   <li>Step sounds are played when the golem has covered a stride of about 1.7 blocks on the ground (Bukkit has no
 *       step hook). It is kept out of the biome spawn tables and out of the wolf conversion of {@link SiftCreatures}.</li>
 *   <li>The spawn egg is a content item (a wolf spawn egg that spawns a golem instead) that works in the Sift only.</li>
 *   <li>Anyone may right-click a golem that carries a Soul Block and has not been bonded, as in the mod; a click with a
 *       lead or a name tag works as for any mob and every other click does nothing.</li>
 * </ul>
 */
final class SiftEchoGolems implements Listener {

    static final String MODEL = SiftModels.ECHO_GOLEM;
    static final String ENTITY_ID = "the_sift:echo_golem";

    private static final double BODY_SCALE = 1.94;
    private static final double MAX_HEALTH = 34.0;
    private static final double MOVEMENT_SPEED = 0.23;
    private static final double FOLLOW_RANGE = 128.0;
    private static final double STEP_HEIGHT = 1.5;
    private static final double KNOCKBACK_RESISTANCE = 0.25;
    /** The mod's entity size, for the spawn collision check. */
    private static final double MOD_WIDTH = 1.15;
    private static final double MOD_HEIGHT = 1.65;
    private static final String CUSTOM_ENTITY_KEY = "custom_entity";
    /** Keeps {@link SiftCreatures} from turning the wolf body into a Blub; the key is its own. */
    private static final String PLAIN_WOLF_KEY = "sift_plain_wolf";
    private static final long SAVE_INTERVAL_TICKS = 100L;
    private static final double STRIDE = 1.0 / 0.6;
    private static final String SEARCH_WALK = "search_walk";
    private static final String SOUL_HIDDEN = "soul_hidden";
    /** The model's {@code soul_block} bone, which the mod's {@code EchoGolemSoulGlowLayer} draws at full brightness. */
    private static final Set<String> SOUL_BONES = Set.of("soul_block");
    /** The mod's {@code EchoLightBehavior.LUMINANCE}. */
    static final int SOUL_LIGHT_LEVEL = 14;
    /** The {@code soul_light} locator: 34 pixels up, in a model at scale one. */
    static final double SOUL_LIGHT_HEIGHT = 34.0 / 16.0;

    private final JavaPlugin plugin;
    private final JavaModelGateway models;
    private final BiFunction<String, Integer, Optional<ItemStack>> items;
    private final SiftLandmarks landmarks;
    private final NamespacedKey golemKey;
    private final NamespacedKey stateKey;
    private final NamespacedKey customEntityKey;
    private final NamespacedKey plainWolfKey;
    private final GolemShared shared = new GolemShared();
    private final Map<UUID, Runtime> golems = new HashMap<>();
    private final Random random = new Random();
    private SiftSingers singers;
    private SoulBlockWorld soulBlocks;
    private boolean warnedMissingModel;
    private ActiveDimension dimension;

    SiftEchoGolems(JavaPlugin plugin, JavaModelGateway models, BiFunction<String, Integer, Optional<ItemStack>> items,
                   SiftLandmarks landmarks) {
        this.plugin = plugin;
        this.models = models;
        this.items = items;
        this.landmarks = landmarks;
        this.golemKey = new NamespacedKey(plugin, "sift_echo_golem");
        this.stateKey = new NamespacedKey(plugin, "sift_echo_golem_state");
        this.customEntityKey = new NamespacedKey(plugin, CUSTOM_ENTITY_KEY);
        this.plainWolfKey = new NamespacedKey(plugin, PLAIN_WOLF_KEY);
    }

    /** The Singers the golems deliver to; unset when Singers are switched off. */
    void setSingers(SiftSingers singers) {
        this.singers = singers;
    }

    void activate(ActiveDimension active) {
        this.dimension = active;
        this.soulBlocks = new SoulBlockWorld(active.world());
        shared.clear();
        active.registrations().registerListener("sift-echo-golems", this);
        active.registrations().ownCleanup("sift-echo-golem-models", this::detachAll);
        for (org.bukkit.Chunk chunk : active.world().getLoadedChunks()) {
            soulBlocks.scan(chunk, landmarks);
        }
        for (Entity entity : active.world().getEntities()) {
            if (isGolem(entity)) {
                adopt((Wolf) entity);
            }
        }
        active.registrations().scheduleRepeating("echo-golem-tick", this::tickAll, 1L, 1L);
        active.registrations().scheduleRepeating("echo-golem-spawner", this::spawnPass, 1L, EchoGolemSpawning.CHECK_INTERVAL_TICKS);
    }

    /** Dresses loaded golems that have no model yet, e.g. after BetterModel finished loading. */
    void redress() {
        for (Runtime runtime : List.copyOf(golems.values())) {
            runtime.dress();
        }
    }

    boolean isGolem(Entity entity) {
        return entity instanceof Wolf && entity.getPersistentDataContainer().has(golemKey, PersistentDataType.BYTE);
    }

    private boolean inSift(World world) {
        return dimension != null && dimension.world().getUID().equals(world.getUID());
    }

    /** The golem with this id for a Singer to receive from; null when it is not loaded or not free to deliver. */
    SoulExchange.Golem golem(UUID id) {
        Runtime runtime = golems.get(id);
        return runtime != null && runtime.isAlive() && runtime.brain.canInteractWithSinger() ? runtime.brain : null;
    }

    /** The nearest golem within the box that carries a Soul Block and is not bonded; null when there is none. */
    SoulExchange.Golem nearestOffering(Location around, double horizontal, double vertical) {
        Runtime best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Runtime runtime : golems.values()) {
            Location at = runtime.body.getLocation();
            if (!runtime.isAlive() || !at.getWorld().equals(around.getWorld()) || !runtime.brain.hasSoulBlock()
                || !runtime.brain.canInteractWithSinger() || Math.abs(at.getX() - around.getX()) > horizontal
                || Math.abs(at.getY() - around.getY()) > vertical || Math.abs(at.getZ() - around.getZ()) > horizontal) {
                continue;
            }
            double distance = at.distanceSquared(around);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = runtime;
            }
        }
        return best == null ? null : best.brain;
    }

    // --- spawner (SiftEchoGolemSpawner.tick) ------------------------------------------------------

    private void spawnPass() {
        ActiveDimension active = dimension;
        if (active == null || !Boolean.TRUE.equals(active.world().getGameRuleValue(GameRules.SPAWN_MOBS))) {
            return;
        }
        World world = active.world();
        List<SiftPos> players = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (!player.isDead() && player.getGameMode() != GameMode.SPECTATOR) {
                Location at = player.getLocation();
                players.add(new SiftPos(at.getBlockX(), at.getBlockY(), at.getBlockZ()));
            }
        }
        EchoGolemSpawning.pass(new WorldLevel(world), landmarks.snapshot(SiftLandmarks.Kind.GOLEM_PENDING),
            entrance -> landmarks.forget(SiftLandmarks.Kind.GOLEM_PENDING, entrance), players, random,
            (feet, reason) -> spawnGolem(world, feet));
    }

    private boolean spawnGolem(World world, SiftPos feet) {
        Location at = new Location(world, feet.x() + 0.5, feet.y(), feet.z() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        BoundingBox box = new BoundingBox(at.getX() - MOD_WIDTH / 2, at.getY(), at.getZ() - MOD_WIDTH / 2,
            at.getX() + MOD_WIDTH / 2, at.getY() + MOD_HEIGHT, at.getZ() + MOD_WIDTH / 2);
        if (world.hasCollisionsIn(box)) {
            return false;
        }
        try {
            // Not NATURAL: SiftCreatures turns wolves that spawn naturally into Blubs.
            Wolf body = world.spawn(at, Wolf.class, CreatureSpawnEvent.SpawnReason.CUSTOM, this::configure);
            if (!body.isValid()) {
                return false;
            }
            Runtime runtime = new Runtime(body);
            golems.put(body.getUniqueId(), runtime);
            runtime.dress();
            runtime.save();
            world.playSound(at, "the_sift:entity.echo_golem.spawn", SoundCategory.NEUTRAL, 0.8F, 0.94F);
            return true;
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[TheSift] Could not spawn an Echo Golem at " + feet, failure);
            return false;
        }
    }

    /** Spawns a golem for an operator or an item; null when it could not be placed. */
    Runtime spawnAt(Location at) {
        SiftPos feet = SiftPos.containing(at.getX(), at.getY(), at.getZ());
        World world = at.getWorld();
        if (!spawnGolem(world, feet)) {
            return null;
        }
        return golems.values().stream().filter(runtime -> runtime.body.getLocation().distanceSquared(at) < 4.0).findFirst().orElse(null);
    }

    /** Marks the body and sets the mod's stats, before the spawn event so other listeners see them. */
    private void configure(Wolf body) {
        PersistentDataContainer data = body.getPersistentDataContainer();
        data.set(golemKey, PersistentDataType.BYTE, (byte) 1);
        data.set(plainWolfKey, PersistentDataType.BYTE, (byte) 1);
        // Any value keeps the mob out of the mob-scaling module.
        data.set(customEntityKey, PersistentDataType.STRING, ENTITY_ID);
        setBase(body, Attribute.SCALE, BODY_SCALE);
        setBase(body, Attribute.MAX_HEALTH, MAX_HEALTH);
        setBase(body, Attribute.MOVEMENT_SPEED, MOVEMENT_SPEED);
        setBase(body, Attribute.FOLLOW_RANGE, FOLLOW_RANGE);
        setBase(body, Attribute.STEP_HEIGHT, STEP_HEIGHT);
        setBase(body, Attribute.MOVEMENT_EFFICIENCY, 1.0);
        setBase(body, Attribute.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
        body.setHealth(MAX_HEALTH);
        body.setSilent(true);
        body.setRemoveWhenFarAway(false);
        body.setCanPickupItems(false);
        body.customName(Component.translatable("entity.the_sift.echo_golem", "Echo Golem"));
        body.setCustomNameVisible(false);
        prepare(body);
    }

    private static void setBase(Wolf body, Attribute attribute, double value) {
        AttributeInstance instance = body.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Goals are not saved with the entity, so the body is stripped of its own every time it loads. */
    private static void prepare(Wolf body) {
        Bukkit.getMobGoals().removeAllGoals(body);
        body.setTarget(null);
        body.setAngry(false);
        body.setSitting(false);
        body.getPathfinder().setCanFloat(true);
    }

    private void adopt(Wolf body) {
        if (golems.containsKey(body.getUniqueId())) {
            return;
        }
        prepare(body);
        Runtime runtime = new Runtime(body);
        golems.put(body.getUniqueId(), runtime);
        runtime.restore();
        runtime.dress();
    }

    // --- lifecycle -----------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!inSift(event.getWorld())) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            if (isGolem(entity)) {
                adopt((Wolf) entity);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            Runtime runtime = golems.remove(entity.getUniqueId());
            if (runtime != null) {
                runtime.save();
                runtime.brain.release();
                runtime.close();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        if (inSift(event.getWorld())) {
            golems.values().forEach(Runtime::save);
        }
    }

    private void tickAll() {
        for (Runtime runtime : List.copyOf(golems.values())) {
            if (!runtime.body.isValid()) {
                runtime.brain.release();
                runtime.close();
                golems.remove(runtime.body.getUniqueId());
                continue;
            }
            if (runtime.body.isTicking()) {
                runtime.tick();
            }
        }
    }

    private void detachAll() {
        for (Runtime runtime : golems.values()) {
            runtime.save();
            runtime.brain.release();
            runtime.close();
        }
        golems.clear();
        shared.clear();
    }

    /**
     * The light of every golem that carries a Soul Block: level 14 at the mod's {@code soul_light} locator, 2.125
     * blocks above the golem, as the mod's {@code EchoLightBehavior}.
     */
    List<SiftDynamicLights.Source> lightSources() {
        List<SiftDynamicLights.Source> sources = new ArrayList<>();
        for (Runtime runtime : golems.values()) {
            if (runtime.body.isValid() && runtime.body.isTicking() && runtime.brain.hasSoulBlock()) {
                sources.add(new SiftDynamicLights.Source(runtime.body.getUniqueId(),
                    runtime.body.getLocation().add(0.0, SOUL_LIGHT_HEIGHT, 0.0), SOUL_LIGHT_LEVEL));
            }
        }
        return sources;
    }

    // --- spawn egg ---------------------------------------------------------------------------------------

    /** The spawn egg content item: right-clicking a block puts a golem on top of it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnEgg(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        Block clicked = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null || item == null || !inSift(clicked.getWorld())
            || ContentItemFactory.idOf(item).filter(SiftItems.ECHO_GOLEM_SPAWN_EGG::equals).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Block target = clicked.getRelative(event.getBlockFace());
        if (spawnAt(target.getLocation().add(0.5, 0.0, 0.5)) != null && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            item.subtract(1);
        }
    }

    // --- Soul Block records ----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (inSift(block.getWorld()) && soulBlocks.isSoulBlock(block.getBlockData())) {
            landmarks.record(SiftLandmarks.Kind.SOUL_BLOCK, new SiftPos(block.getX(), block.getY(), block.getZ()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (inSift(block.getWorld()) && soulBlocks.isSoulBlock(block.getBlockData())) {
            landmarks.forget(SiftLandmarks.Kind.SOUL_BLOCK, new SiftPos(block.getX(), block.getY(), block.getZ()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (inSift(event.getWorld())) {
            soulBlocks.scan(event.getChunk(), landmarks);
        }
    }

    // --- interaction (mobInteract) -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Runtime golem = golems.get(event.getRightClicked().getUniqueId());
        if (golem == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (held.getType() == Material.LEAD || held.getType() == Material.NAME_TAG) {
            return;
        }
        event.setCancelled(true);
        if (held.getType() == Material.ECHO_SHARD) {
            if (golem.brain.bondTo(player.getUniqueId(), player.getName())) {
                if (player.getGameMode() != GameMode.CREATIVE) {
                    held.subtract(1);
                }
                Location at = golem.body.getLocation();
                golem.body.getWorld().spawnParticle(Particle.HEART, at.getX(), at.getY() + golem.body.getHeight() * 0.7, at.getZ(),
                    7, 0.45, 0.55, 0.45, 0.08);
                golem.save();
            }
            return;
        }
        if (golem.brain.hasSoulBlock() && golem.brain.isBondedTo(player.getUniqueId(), player.getName())) {
            golem.brain.releaseCarriedSoul();
            golem.save();
        }
    }

    // --- damage, death and sounds ------------------------------------------------------------------------

    /** {@code hurtServer} first lets go of the Soul Block, whatever the damage turns out to be. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageStart(EntityDamageEvent event) {
        Runtime golem = golems.get(event.getEntity().getUniqueId());
        if (golem != null && golem.brain.releaseCarriedSoul()) {
            golem.save();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Runtime golem = golems.get(event.getEntity().getUniqueId());
        if (golem == null) {
            return;
        }
        golem.brain.onHurt();
        golem.body.setAngry(false);
        // A killing blow plays the death sound instead.
        if (golem.body.getHealth() - event.getFinalDamage() > 0.0) {
            golem.play("hurt", 1.0F, voicePitch());
        }
    }

    /** The golem never attacks: the wolf's own goals are gone, but a damage source can still name it. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (golems.containsKey(event.getDamager().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (golems.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        Runtime golem = golems.remove(event.getEntity().getUniqueId());
        if (golem == null) {
            return;
        }
        golem.brain.release();
        golem.close();
        event.getDrops().clear();
        if (event.getDroppedExp() > 0) {
            event.setDroppedExp(4 + random.nextInt(4));
        }
        golem.play("death", 1.0F, voicePitch());
    }

    /** {@code LivingEntity#getVoicePitch}. */
    private float voicePitch() {
        return (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
    }

    // --- the running golem ---------------------------------------------------------------------------

    /** One golem in the world: its body, its mind and what it looks like. */
    final class Runtime {

        final Wolf body;
        final GolemEnv env;
        final EchoGolemBrain brain;
        private JavaModelHandle handle;
        private ModelPoses poses;
        private ModelPoses soulPoses;
        private Location lastLocation;
        private double stride;
        private int age;
        private EchoGolemBrain.Mission lastMission = EchoGolemBrain.Mission.SEARCH;
        private boolean lastHadSoul;

        Runtime(Wolf body) {
            this.body = body;
            // After the body: the environment's walker keeps the body it is given.
            this.env = new GolemEnv(this);
            this.brain = new EchoGolemBrain(env, random);
            this.lastLocation = body.getLocation();
        }

        boolean isAlive() {
            return body.isValid() && !body.isDead();
        }

        void tick() {
            age++;
            brain.tick();
            updateModel();
            stepSound();
            if (brain.mission() != lastMission || brain.hasSoulBlock() != lastHadSoul || age % SAVE_INTERVAL_TICKS == 0) {
                lastMission = brain.mission();
                lastHadSoul = brain.hasSoulBlock();
                save();
            }
        }

        void play(String sound, float volume, float pitch) {
            body.getWorld().playSound(body.getLocation(), "the_sift:entity.echo_golem." + sound, SoundCategory.NEUTRAL, volume, pitch);
        }

        /** {@code playStepSound}: 0.62 volume, pitch 0.94-1.06, once per stride. */
        private void stepSound() {
            Location at = body.getLocation();
            if (body.isOnGround() && at.getWorld().equals(lastLocation.getWorld())) {
                double dx = at.getX() - lastLocation.getX();
                double dz = at.getZ() - lastLocation.getZ();
                stride += Math.sqrt(dx * dx + dz * dz);
                if (stride >= STRIDE) {
                    stride = 0.0;
                    play("step", 0.62F, 0.94F + random.nextFloat() * 0.12F);
                }
            }
            lastLocation = at;
        }

        // --- model ---

        void dress() {
            if (handle != null || !body.isValid()) {
                return;
            }
            try {
                handle = models.attach(body, MODEL);
                // The body is scaled to get its hitbox; the model must keep the mod's size.
                models.scale(handle, 1.0F);
                models.brighten(handle, 15, 15, SOUL_BONES);
                poses = new ModelPoses(models, handle);
                soulPoses = new ModelPoses(models, handle);
                updateModel();
            } catch (RuntimeException unavailable) {
                handle = null;
                poses = null;
                soulPoses = null;
                if (!warnedMissingModel) {
                    warnedMissingModel = true;
                    plugin.getLogger().log(Level.WARNING, "[TheSift] Echo Golem model unavailable; Echo Golems render as wolves",
                        unavailable);
                }
            }
        }

        /** The mod's two controllers: the mission picks the pose, and the carried Soul Block shows or hides. */
        private void updateModel() {
            if (poses == null) {
                return;
            }
            Vector velocity = body.getVelocity();
            boolean walking = EchoGolemBrain.isWalking(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
            poses.show(switch (brain.mission()) {
                case PICKUP -> "pickup";
                case BOW -> "bow";
                case SIT -> "sit";
                case CARRY -> walking ? "carry_walk" : null;
                case SEARCH -> walking ? SEARCH_WALK : null;
            });
            soulPoses.show(brain.hasSoulBlock() ? null : SOUL_HIDDEN);
        }

        void close() {
            if (handle != null) {
                models.close(handle);
                handle = null;
                poses = null;
                soulPoses = null;
            }
        }

        // --- saving (addAdditionalSaveData, readAdditionalSaveData) ---

        void save() {
            if (!body.isValid()) {
                return;
            }
            EchoGolemBrain.Snapshot snapshot = brain.snapshot();
            PersistentDataContainer state = body.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
            put(state, "mission_state", snapshot.mission());
            state.set(key("has_soul"), PersistentDataType.BYTE, (byte) (snapshot.hasSoul() ? 1 : 0));
            put(state, "state_ticks", snapshot.stateTicks());
            if (snapshot.route() != null) {
                put(state, "route_phase", snapshot.route());
            }
            put(state, "route_ticks", snapshot.routeTicks());
            put(state, "soul_target", snapshot.soulTarget());
            put(state, "soul_approach", snapshot.soulApproach());
            put(state, "canyon_entrance", snapshot.canyonEntrance());
            put(state, "canyon_midpoint", snapshot.canyonMidpoint());
            put(state, "canyon_cluster", snapshot.canyonCluster());
            put(state, "ignored_canyon", snapshot.ignoredCanyon());
            put(state, "ignored_canyon_ticks", snapshot.ignoredCanyonTicks());
            state.set(key("entered_canyon"), PersistentDataType.BYTE, (byte) (snapshot.enteredCanyon() ? 1 : 0));
            if (snapshot.exitMidpointPassed() != null) {
                state.set(key("exit_midpoint_passed"), PersistentDataType.BYTE, (byte) (snapshot.exitMidpointPassed() ? 1 : 0));
            }
            state.set(key("exploration_heading"), PersistentDataType.DOUBLE, snapshot.explorationHeading());
            put(state, "singer_target", snapshot.singerTarget());
            put(state, "bonded_player", snapshot.bondedPlayer());
            if (snapshot.bondedPlayerName() != null && !snapshot.bondedPlayerName().isBlank()) {
                state.set(key("bonded_player_name"), PersistentDataType.STRING, snapshot.bondedPlayerName());
            }
            put(state, "delivery_anchor", snapshot.deliveryAnchor());
            body.getPersistentDataContainer().set(stateKey, PersistentDataType.TAG_CONTAINER, state);
        }

        void restore() {
            PersistentDataContainer state = body.getPersistentDataContainer().get(stateKey, PersistentDataType.TAG_CONTAINER);
            if (state == null) {
                return;
            }
            brain.restore(new EchoGolemBrain.Snapshot(
                integer(state, "mission_state", 0), flag(state, "has_soul", false), integer(state, "state_ticks", 0),
                state.get(key("route_phase"), PersistentDataType.INTEGER), integer(state, "route_ticks", 0),
                state.get(key("soul_target"), PersistentDataType.LONG), state.get(key("soul_approach"), PersistentDataType.LONG),
                state.get(key("canyon_entrance"), PersistentDataType.LONG), state.get(key("canyon_midpoint"), PersistentDataType.LONG),
                state.get(key("canyon_cluster"), PersistentDataType.LONG), state.get(key("ignored_canyon"), PersistentDataType.LONG),
                integer(state, "ignored_canyon_ticks", 0), flag(state, "entered_canyon", false),
                state.has(key("exit_midpoint_passed"), PersistentDataType.BYTE) ? flag(state, "exit_midpoint_passed", false) : null,
                state.getOrDefault(key("exploration_heading"), PersistentDataType.DOUBLE, 0.0),
                uuid(state, "singer_target"), uuid(state, "bonded_player"),
                state.get(key("bonded_player_name"), PersistentDataType.STRING), state.get(key("delivery_anchor"), PersistentDataType.LONG)));
            lastMission = brain.mission();
            lastHadSoul = brain.hasSoulBlock();
        }

        private void put(PersistentDataContainer state, String name, Integer value) {
            if (value != null) {
                state.set(key(name), PersistentDataType.INTEGER, value);
            }
        }

        private void put(PersistentDataContainer state, String name, Long value) {
            if (value != null) {
                state.set(key(name), PersistentDataType.LONG, value);
            }
        }

        private void put(PersistentDataContainer state, String name, UUID value) {
            if (value != null) {
                state.set(key(name), PersistentDataType.STRING, value.toString());
            }
        }

        private int integer(PersistentDataContainer state, String name, int fallback) {
            return state.getOrDefault(key(name), PersistentDataType.INTEGER, fallback);
        }

        private boolean flag(PersistentDataContainer state, String name, boolean fallback) {
            Byte value = state.get(key(name), PersistentDataType.BYTE);
            return value == null ? fallback : value == 1;
        }

        private UUID uuid(PersistentDataContainer state, String name) {
            String value = state.get(key(name), PersistentDataType.STRING);
            try {
                return value == null || value.isEmpty() ? null : UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        private NamespacedKey key(String name) {
            return new NamespacedKey(plugin, name);
        }
    }

    // --- the brain's view of the world --------------------------------------------------------------------

    private final class GolemEnv implements EchoGolemBrain.Env {

        private final Runtime golem;
        private final Walker walker;

        GolemEnv(Runtime golem) {
            this.golem = golem;
            this.walker = new Walker(golem.body);
        }

        private Wolf body() {
            return golem.body;
        }

        @Override
        public long gameTime() {
            return body().getWorld().getGameTime();
        }

        @Override
        public int entityId() {
            return body().getEntityId();
        }

        @Override
        public UUID uuid() {
            return body().getUniqueId();
        }

        @Override
        public double x() {
            return body().getX();
        }

        @Override
        public double y() {
            return body().getY();
        }

        @Override
        public double z() {
            return body().getZ();
        }

        @Override
        public boolean onGround() {
            return body().isOnGround();
        }

        /** The golem would collide with something a tenth of a block ahead on its path. */
        @Override
        public boolean horizontalCollision() {
            Pathfinder.PathResult path = body().getPathfinder().getCurrentPath();
            Location next = path == null ? null : path.getNextPoint();
            if (next == null) {
                return false;
            }
            Location at = body().getLocation();
            Vector ahead = new Vector(next.getX() + 0.5 - at.getX(), 0.0, next.getZ() + 0.5 - at.getZ());
            if (ahead.lengthSquared() < 1.0E-6) {
                return false;
            }
            return body().collidesAt(at.add(ahead.normalize().multiply(0.1)));
        }

        @Override
        public EchoGolemBrain.Navigation navigation() {
            return walker;
        }

        @Override
        public GolemShared shared() {
            return shared;
        }

        @Override
        public SiftLandmarks landmarks() {
            return landmarks;
        }

        @Override
        public boolean isLoaded(SiftPos pos) {
            return soulBlocks.isLoaded(pos);
        }

        @Override
        public boolean isSoulBlock(SiftPos pos) {
            return soulBlocks.isSoulBlock(pos);
        }

        /** {@code canSeeSky}: the block has full sky light. */
        @Override
        public boolean canSeeSky(SiftPos pos) {
            return soulBlocks.isLoaded(pos) && body().getWorld().getBlockAt(pos.x(), pos.y(), pos.z()).getLightFromSky() >= 15;
        }

        @Override
        public int surfaceY(int x, int z) {
            World world = body().getWorld();
            return world.isChunkLoaded(x >> 4, z >> 4) ? world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1
                : world.getMinHeight();
        }

        @Override
        public boolean isChunkLoaded(int x, int z) {
            return body().getWorld().isChunkLoaded(x >> 4, z >> 4);
        }

        /**
         * {@code canReachSoulWithHands}: the golem's box is within 1.75 blocks of the Soul Block and a line from the
         * nearest point of the box to the middle of the block's upper part meets no other solid block.
         */
        @Override
        public boolean handsCanReach(SiftPos soul) {
            if (!soulBlocks.isSoulBlock(soul)) {
                return false;
            }
            BoundingBox box = body().getBoundingBox();
            double gapX = Math.max(0.0, Math.max(soul.x() - box.getMaxX(), box.getMinX() - (soul.x() + 1)));
            double gapY = Math.max(0.0, Math.max(soul.y() - box.getMaxY(), box.getMinY() - (soul.y() + 1)));
            double gapZ = Math.max(0.0, Math.max(soul.z() - box.getMaxZ(), box.getMinZ() - (soul.z() + 1)));
            if (gapX * gapX + gapY * gapY + gapZ * gapZ > 3.0625) {
                return false;
            }
            Vector target = new Vector(soul.x() + 0.5, soul.y() + 0.85, soul.z() + 0.5);
            Vector origin = new Vector(Math.max(box.getMinX() + 0.01, Math.min(box.getMaxX() - 0.01, target.getX())),
                Math.max(box.getMinY() + 0.1, Math.min(box.getMaxY() - 0.1, target.getY())),
                Math.max(box.getMinZ() + 0.01, Math.min(box.getMaxZ() - 0.01, target.getZ())));
            Vector line = target.clone().subtract(origin);
            double length = line.length();
            if (length < 1.0E-6) {
                return true;
            }
            RayTraceResult hit = body().getWorld().rayTraceBlocks(origin.toLocation(body().getWorld()), line.multiply(1.0 / length),
                length, FluidCollisionMode.NEVER, true);
            return hit == null || hit.getHitBlock() == null || hit.getHitBlock().getX() == soul.x()
                && hit.getHitBlock().getY() == soul.y() && hit.getHitBlock().getZ() == soul.z();
        }

        @Override
        public Optional<SiftPoint> randomPosTowards(SiftPoint target, int horizontal, int vertical, double maximumAngle) {
            return LandSpots.towards(body().getWorld(), body().getLocation(), target, horizontal, vertical, maximumAngle, 3, random)
                .map(spot -> new SiftPoint(spot.getX(), spot.getY(), spot.getZ()));
        }

        @Override
        public void removeSoulBlock(SiftPos pos) {
            body().getWorld().getBlockAt(pos.x(), pos.y(), pos.z()).setType(Material.AIR);
            landmarks.forget(SiftLandmarks.Kind.SOUL_BLOCK, pos);
        }

        @Override
        public Optional<SiftPos> soulTargetOf(UUID id) {
            Runtime other = golems.get(id);
            return other == null || !other.isAlive() ? Optional.empty() : Optional.ofNullable(other.brain.soulTarget());
        }

        @Override
        public SoulExchange.Singer singer(UUID id) {
            return singers == null ? null : singers.singer(id);
        }

        @Override
        public SoulExchange.Singer requestSinger(EchoGolemBrain self) {
            if (singers == null) {
                return null;
            }
            return singers.request(self, body().getLocation(), position -> {
                if (!self.tryAcquirePathBudget(6)) {
                    return SoulExchange.Reach.NO_BUDGET;
                }
                EchoGolemBrain.Path path = walker.createPath(position, 2);
                return path != null && path.canReach() ? SoulExchange.Reach.REACHABLE : SoulExchange.Reach.UNREACHABLE;
            });
        }

        @Override
        public EchoGolemBrain.Player player(UUID id) {
            Player online = Bukkit.getPlayer(id);
            return online == null ? null : new PlayerRef(online, body().getWorld());
        }

        @Override
        public EchoGolemBrain.Player playerNamed(String name) {
            Player online = Bukkit.getPlayerExact(name);
            return online == null ? null : new PlayerRef(online, body().getWorld());
        }

        @Override
        public void faceToward(SiftPoint point) {
            double dx = point.x() - body().getX();
            double dz = point.z() - body().getZ();
            float yaw = (float) (Math.atan2(dz, dx) * 57.29577951308232) - 90.0F;
            body().setRotation(yaw, body().getLocation().getPitch());
            body().setBodyYaw(yaw);
        }

        @Override
        public void hop(double velocity) {
            Vector current = body().getVelocity();
            body().setVelocity(current.setY(Math.max(current.getY(), velocity)));
        }

        @Override
        public void playItemGet(float pitch) {
            golem.play("item_get", 1.0F, pitch);
        }

        @Override
        public void playItemDrop(float pitch) {
            golem.play("item_drop", 1.0F, pitch);
        }

        @Override
        public void dropSoulBlock() {
            soulBlockStack().ifPresent(stack -> body().getWorld().dropItem(body().getLocation().add(0.0, 0.8, 0.0), stack));
        }

        @Override
        public void giveSoulBlock(EchoGolemBrain.Player player) {
            Player online = Bukkit.getPlayer(player.id());
            if (online == null) {
                return;
            }
            soulBlockStack().ifPresent(stack -> {
                for (ItemStack left : online.getInventory().addItem(stack).values()) {
                    online.getWorld().dropItem(online.getLocation(), left);
                }
            });
        }

        private Optional<ItemStack> soulBlockStack() {
            return items.apply(SiftCreatures.SOUL_BLOCK.asString(), 1);
        }

        @Override
        public void awardFriendOfTheSift(EchoGolemBrain.Player player) {
            Player online = Bukkit.getPlayer(player.id());
            if (online != null) {
                SiftAdvancements.award(online, SiftAdvancements.FRIEND_OF_THE_SIFT);
            }
        }

        /** {@code awardNearby}: living players within 16 blocks in every direction. */
        @Override
        public void awardLikeFatherAndSon() {
            Location at = body().getLocation();
            for (Player player : body().getWorld().getPlayers()) {
                Location where = player.getLocation();
                if (!player.isDead() && player.getGameMode() != GameMode.SPECTATOR && Math.abs(where.getX() - at.getX()) <= 16.0
                    && Math.abs(where.getY() - at.getY()) <= 16.0 && Math.abs(where.getZ() - at.getZ()) <= 16.0) {
                    SiftAdvancements.award(player, SiftAdvancements.LIKE_FATHER_AND_SON);
                }
            }
        }

        @Override
        public void keepLoaded() {
            body().setRemoveWhenFarAway(false);
        }
    }

    /** An online player, as the brain sees them. */
    private record PlayerRef(Player player, World golemWorld) implements EchoGolemBrain.Player {

        @Override
        public UUID id() {
            return player.getUniqueId();
        }

        @Override
        public String name() {
            return player.getName();
        }

        @Override
        public SiftPoint position() {
            return new SiftPoint(player.getX(), player.getY(), player.getZ());
        }

        @Override
        public boolean inWorld() {
            return player.getWorld().equals(golemWorld);
        }
    }

    // --- walking ------------------------------------------------------------------------------------------

    /** The golem's {@link EchoGolemBrain.Navigation} over Paper's pathfinder. */
    private static final class Walker implements EchoGolemBrain.Navigation {

        private static final MethodHandle FIND_PATH_WITH_REACH = findPathWithReach();

        /** At most this many goals of a set are searched, nearest first. */
        private static final int GOALS_TRIED = 2;

        private final Wolf body;

        Walker(Wolf body) {
            this.body = body;
        }

        private record Found(Pathfinder.PathResult result, boolean reach, SiftPos end) implements EchoGolemBrain.Path {
            @Override
            public boolean canReach() {
                return reach;
            }
        }

        @Override
        public EchoGolemBrain.Path createPath(SiftPos target, int reach) {
            Location at = new Location(body.getWorld(), target.x() + 0.5, target.y(), target.z() + 0.5);
            return wrap(findPath(body.getPathfinder(), at, reach));
        }

        /**
         * {@code Pathfinder#findPath(Location, int)} (the mod's {@code createPath(pos, reach)}) exists on current
         * 26.2 servers but not in the API this plugin compiles against; without it the reach is 0.
         */
        private static Pathfinder.PathResult findPath(Pathfinder pathfinder, Location at, int reach) {
            if (FIND_PATH_WITH_REACH != null) {
                try {
                    return (Pathfinder.PathResult) FIND_PATH_WITH_REACH.invoke(pathfinder, at, reach);
                } catch (Throwable failure) {
                    if (failure instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    if (failure instanceof Error error) {
                        throw error;
                    }
                    throw new IllegalStateException(failure);
                }
            }
            return pathfinder.findPath(at);
        }

        @Override
        public EchoGolemBrain.Path createPath(Collection<SiftPos> targets, int reach) {
            SiftPos here = SiftPos.containing(body.getX(), body.getY(), body.getZ());
            List<SiftPos> nearest = new ArrayList<>(targets);
            nearest.sort(Comparator.comparingDouble(candidate -> candidate.distSqr(here)));
            EchoGolemBrain.Path partial = null;
            for (int index = 0; index < Math.min(GOALS_TRIED, nearest.size()); index++) {
                EchoGolemBrain.Path path = createPath(nearest.get(index), reach);
                if (path != null && path.canReach()) {
                    return path;
                }
                if (partial == null) {
                    partial = path;
                }
            }
            return partial;
        }

        private static MethodHandle findPathWithReach() {
            try {
                return MethodHandles.publicLookup().findVirtual(Pathfinder.class, "findPath",
                    MethodType.methodType(Pathfinder.PathResult.class, Location.class, int.class));
            } catch (NoSuchMethodException | IllegalAccessException missing) {
                return null;
            }
        }

        private static EchoGolemBrain.Path wrap(Pathfinder.PathResult result) {
            if (result == null) {
                return null;
            }
            Location end = result.getFinalPoint();
            return new Found(result, result.canReachFinalPoint(), end == null ? null : SiftPos.containing(end.getX(), end.getY(), end.getZ()));
        }

        @Override
        public boolean moveTo(EchoGolemBrain.Path path, double speed) {
            return path instanceof Found found && body.getPathfinder().moveTo(found.result(), speed);
        }

        @Override
        public boolean isInProgress() {
            return body.getPathfinder().hasPath();
        }

        @Override
        public boolean isDone() {
            return !body.getPathfinder().hasPath();
        }

        @Override
        public Integer nextNodeY() {
            Pathfinder.PathResult current = body.getPathfinder().getCurrentPath();
            Location next = current == null ? null : current.getNextPoint();
            return next == null ? null : next.getBlockY();
        }

        @Override
        public void stop() {
            body.getPathfinder().stopPathfinding();
        }
    }

    // --- spawn search ----------------------------------------------------------------------------------

    /** {@link EchoGolemSpawning.Level} over a loaded Bukkit world. */
    private final class WorldLevel implements EchoGolemSpawning.Level {

        private final World world;

        WorldLevel(World world) {
            this.world = world;
        }

        @Override
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return world.isChunkLoaded(chunkX, chunkZ);
        }

        @Override
        public int height(int x, int z) {
            // LevelReader.getHeight: the first free Y; an unloaded column reads as the world bottom.
            return world.isChunkLoaded(x >> 4, z >> 4)
                ? world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1 : world.getMinHeight();
        }

        @Override
        public boolean isFullBlock(SiftPos pos) {
            if (!soulBlocks.isLoaded(pos)) {
                return false;
            }
            double volume = 0.0;
            for (BoundingBox box : world.getBlockAt(pos.x(), pos.y(), pos.z()).getCollisionShape().getBoundingBoxes()) {
                volume += box.getVolume();
            }
            return volume >= 1.0 - 1.0E-6;
        }

        @Override
        public boolean isEmptySpace(SiftPos pos) {
            if (!soulBlocks.isLoaded(pos)) {
                return false;
            }
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            return !block.isLiquid() && block.getCollisionShape().getBoundingBoxes().isEmpty();
        }

        @Override
        public boolean golemNear(SiftPos around, double horizontal, double vertical) {
            for (Runtime runtime : golems.values()) {
                Location at = runtime.body.getLocation();
                if (at.getWorld().equals(world) && Math.abs(at.getX() - (around.x() + 0.5)) <= horizontal + 0.5
                    && Math.abs(at.getY() - (around.y() + 0.5)) <= vertical + 0.5
                    && Math.abs(at.getZ() - (around.z() + 0.5)) <= horizontal + 0.5) {
                    return true;
                }
            }
            return false;
        }
    }
}
