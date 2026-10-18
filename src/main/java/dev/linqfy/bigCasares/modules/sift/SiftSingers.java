package dev.linqfy.bigCasares.modules.sift;

import com.destroystokyo.paper.entity.Pathfinder;
import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * The Singer that comes for a soul, ported from the mod's {@code SingerEntity} (its soul event, roaming and
 * bartering, the permanent roamer), {@code SingerBarter} and {@code SoulExchangeManager}. Vanilla clients cannot
 * know the mod's entity type, so a Singer is a silent <b>wandering trader</b> (the server-side body, marked in its
 * PersistentDataContainer) dressed with the converted GeckoLib model through BetterModel, like the Blub. The
 * ritual's Singer (the one that sings at the Ancient City portal) is a different thing and lives in
 * {@link SiftRitual}; both use the same model.
 *
 * <p><b>Why a wandering trader.</b> The body has to be tall and narrow (the mod's Singer is 0.8 x 4.0 and scaled
 * 1.2), peaceful (a {@code Monster} is removed on Peaceful, an enderman fills the air around her with portal
 * particles on every client, zombies and skeletons burn), and goal-based so that {@code MobGoals} can strip it to a
 * body with no behaviour of its own. A wandering trader is 0.6 x 1.95; the {@code scale} attribute at 2.46 makes it
 * 1.48 x 4.8.</p>
 *
 * <p>Ported from the mod ({@link SingerEvent} holds the state machine): the soul event with its phases and tick
 * counts (rise during the appear animation, roam at 0.78, walk to an Echo Golem that offers a Soul Block, receive
 * it, sink back after 30 seconds of roaming), bartering (right-click with a Soul Block, or drop one near her: she
 * takes it, holds it for 40 ticks, then throws the gift of {@link SingerBarter} toward the player and the
 * {@code pitch_perfect} advancement), gentle avoidance of canyons that still have Soul Blocks, a name tag making
 * her stay for good, 20 health, 0.27 speed, 8-12 experience, the {@code entity.singer.*} sounds and the portal
 * sounds of the event, and saving of the event across restarts. A Singer that is summoned any other way
 * ({@link #spawnPermanent}) roams for good.</p>
 *
 * <p>Differences:</p>
 * <ul>
 *   <li>The body is a wandering trader 1.48 x 4.8 instead of 0.96 x 4.8; her hitbox is wider than the model. The model
 *       is pinned to the mod's scale of 1.2 so BetterModel does not grow it with the scale attribute. The
 *       converted model renames the mod's idle and walk animations ({@code stand}, {@code stride}) so the poses of
 *       {@link ModelPoses} are the only animations playing.</li>
 *   <li>The Soul Block she holds is a block display in front of her chest (the mod draws it in the model's
 *       {@code held_soul} bone). The glowmask ({@code singer_glowmask.png}) and the dynamic light at her head are
 *       client-side features of the mod and have substitutes: the bones whose faces the glowmask mostly covers (the
 *       head, antlers, arms, hands and torso; the legs are a third covered) are drawn at full brightness through
 *       BetterModel's per-bone brightness, so the unlit pixels of those bones glow too, and
 *       {@link SiftDynamicLights} moves a level-15 light block with her head.</li>
 *   <li>Digging particles of the appear and disappear phases are block particles sent by the server (30 a tick for
 *       the first 90 ticks, as the client does in the mod). While she is intangible (rising, sinking) she has no
 *       physics, as in the mod.</li>
 *   <li>Roaming picks spots with {@link LandSpots}, which stands in for {@code DefaultRandomPos}. Ambient sounds
 *       use vanilla's roll (one in a thousand times the ticks since the last, from -80). The Music Disc Wellspring
 *       she can give is the port's stand-in disc ({@link SiftItems#MUSIC_DISC_WELLSPRING}: a vanilla disc and song
 *       under the mod's name and model; the real recording is not imported). Rewards roll again only if an outcome's
 *       item does not exist on the server.</li>
 *   <li>The spawn egg is a content item (a wandering trader spawn egg that makes a roaming Singer) that works in the Sift only.</li>
 *   <li>Players cannot trade with her: every right-click is cancelled except the name tag, which names her. She is
 *       not part of the biome spawn tables and appears only for Echo Golems and the spawn egg. Her state is written to the entity data when the phase changes, when the chunk
 *       unloads and when the world saves; a restart loses at most the ticks since the last of those.</li>
 *   <li>The Singer takes a dropped Soul Block that anyone dropped; the thrower is the player who is thanked. The
 *       mod's pick-up animation is not shown.</li>
 * </ul>
 */
final class SiftSingers implements Listener {

    static final String MODEL = SiftCreatures.SINGER_MODEL;
    static final String ENTITY_ID = "the_sift:singer";

    private static final double BODY_SCALE = 2.46;
    private static final float MODEL_SCALE = 1.2F;
    private static final double MAX_HEALTH = 20.0;
    private static final double MOVEMENT_SPEED = 0.27;
    private static final String CUSTOM_ENTITY_KEY = "custom_entity";
    private static final double SEARCH_X = SoulExchange.SINGER_SEARCH_HORIZONTAL;
    private static final double SEARCH_Y = SoulExchange.SINGER_SEARCH_VERTICAL;
    private static final int DIGGING_TICKS = 90;
    private static final int DIGGING_PARTICLES = 30;
    private static final double CANYON_AVOID_DISTANCE = 36.0;
    private static final long SAVE_INTERVAL_TICKS = 100L;
    /**
     * The bones the glowmask covers by half or more of their pixels (head and antlers whole, arms and hands about
     * 58 to 84 percent, torso 59, legs 38): BetterModel can only brighten whole bones, not the mask's pixels.
     */
    static final Set<String> GLOWING_BONES = Set.of("head", "left_antler", "right_antler", "left_arm",
        "right_arm", "left_hand", "right_hand", "torso");
    /** The mod's {@code SingerHeadLightBehavior.LUMINANCE}. */
    static final int HEAD_LIGHT_LEVEL = 15;
    /** The {@code head_light} locator: 34 pixels up, in the model scaled by {@link #MODEL_SCALE}. */
    static final double HEAD_LIGHT_HEIGHT = 34.0 / 16.0 * MODEL_SCALE;

    private final JavaPlugin plugin;
    private final JavaModelGateway models;
    private final BiFunction<String, Integer, Optional<ItemStack>> items;
    private final SiftLandmarks landmarks;
    private final NamespacedKey singerKey;
    private final NamespacedKey stateKey;
    private final NamespacedKey customEntityKey;
    private final Map<UUID, Runtime> singers = new HashMap<>();
    private final Random random = new Random();
    private SiftEchoGolems golems;
    private SoulBlockWorld soulBlocks;
    private boolean warnedMissingModel;
    private ActiveDimension dimension;

    SiftSingers(JavaPlugin plugin, JavaModelGateway models, BiFunction<String, Integer, Optional<ItemStack>> items,
                SiftLandmarks landmarks) {
        this.plugin = plugin;
        this.models = models;
        this.items = items;
        this.landmarks = landmarks;
        this.singerKey = new NamespacedKey(plugin, "sift_singer");
        this.stateKey = new NamespacedKey(plugin, "sift_singer_event");
        this.customEntityKey = new NamespacedKey(plugin, CUSTOM_ENTITY_KEY);
    }

    /** The golems the Singers walk to; unset when Echo Golems are switched off. */
    void setGolems(SiftEchoGolems golems) {
        this.golems = golems;
    }

    void activate(ActiveDimension active) {
        this.dimension = active;
        this.soulBlocks = new SoulBlockWorld(active.world());
        active.registrations().registerListener("sift-singers", this);
        active.registrations().ownCleanup("sift-singer-models", this::detachAll);
        for (Entity entity : active.world().getEntities()) {
            if (isSinger(entity)) {
                adopt((WanderingTrader) entity);
            }
        }
        active.registrations().scheduleRepeating("singer-tick", this::tickAll, 1L, 1L);
    }

    /**
     * The light of every Singer that is out: level 15 at the mod's {@code head_light} locator (34 pixels up in a model
     * scaled {@value #MODEL_SCALE}), always, as the mod's {@code SingerHeadLightBehavior}.
     */
    List<SiftDynamicLights.Source> lightSources() {
        List<SiftDynamicLights.Source> sources = new ArrayList<>();
        for (Runtime runtime : singers.values()) {
            if (runtime.body.isValid() && runtime.body.isTicking()) {
                sources.add(new SiftDynamicLights.Source(runtime.body.getUniqueId(),
                    runtime.body.getLocation().add(0.0, HEAD_LIGHT_HEIGHT, 0.0), HEAD_LIGHT_LEVEL));
            }
        }
        return sources;
    }

    /** Dresses loaded Singers that have no model yet, e.g. after BetterModel finished loading. */
    void redress() {
        for (Runtime runtime : List.copyOf(singers.values())) {
            runtime.dress();
        }
    }

    boolean isSinger(Entity entity) {
        return entity instanceof WanderingTrader && entity.getPersistentDataContainer().has(singerKey, PersistentDataType.BYTE);
    }

    private boolean inSift(World world) {
        return dimension != null && dimension.world().getUID().equals(world.getUID());
    }

    /** The Singer with this id for an Echo Golem to deliver to; null when she is not out. */
    SoulExchange.Singer singer(UUID id) {
        Runtime runtime = singers.get(id);
        return runtime != null && runtime.isAlive() ? runtime : null;
    }

    // --- summoning ----------------------------------------------------------------------------------

    /**
     * {@code SoulExchangeManager.requestSinger}: the Singer an Echo Golem should walk to. Finds the nearest
     * Singer on a soul event within 52x32x52 blocks and checks the golem can walk to her; if there is none,
     * makes one appear 7 to 12 blocks from the golem. Returns null when the golem has to wait or there is no room.
     */
    SoulExchange.Singer request(SoulExchange.Golem golem, Location golemAt, Function<SiftPos, SoulExchange.Reach> reach) {
        World world = golemAt.getWorld();
        if (!golem.hasSoulBlock() || !golem.canInteractWithSinger() || !inSift(world)) {
            return null;
        }
        Runtime existing = null;
        double best = Double.MAX_VALUE;
        for (Runtime runtime : singers.values()) {
            Location at = runtime.body.getLocation();
            if (!runtime.isAlive() || !at.getWorld().equals(world) || Math.abs(at.getX() - golemAt.getX()) > SEARCH_X
                || Math.abs(at.getY() - golemAt.getY()) > SEARCH_Y || Math.abs(at.getZ() - golemAt.getZ()) > SEARCH_X) {
                continue;
            }
            double distance = at.distanceSquared(golemAt);
            if (distance < best) {
                best = distance;
                existing = runtime;
            }
        }
        if (existing != null) {
            SoulExchange.Reach reachable = reach.apply(existing.blockPosition());
            if (reachable == SoulExchange.Reach.NO_BUDGET) {
                return null;
            }
            if (reachable == SoulExchange.Reach.REACHABLE) {
                existing.offerSoulGolem(golem);
                return existing;
            }
        }
        SiftPos around = SiftPos.containing(golemAt.getX(), golemAt.getY(), golemAt.getZ());
        SiftPos canyon = landmarks.nearestActiveCluster(around, 48.0, Set.of(),
            cluster -> !soulBlocks.activeNear(landmarks, cluster, 36, 28).isEmpty()).orElse(null);
        Optional<SiftPos> spot = SoulExchange.findNaturalSpawn(new SpawnLevel(world), around, canyon, random);
        if (spot.isEmpty()) {
            return null;
        }
        Location at = new Location(world, spot.get().x() + 0.5, spot.get().y(), spot.get().z() + 0.5,
            random.nextFloat() * 360.0F, 0.0F);
        return spawn(at, golem.id(), false);
    }

    /** A Singer that roams for good: what the mod's spawn egg and {@code /summon} make. */
    Runtime spawnPermanent(Location at) {
        return spawn(at, null, true);
    }

    private Runtime spawn(Location at, UUID golemId, boolean permanent) {
        try {
            WanderingTrader body = at.getWorld().spawn(at, WanderingTrader.class, CreatureSpawnEvent.SpawnReason.CUSTOM,
                this::configure);
            if (!body.isValid()) {
                return null;
            }
            Runtime runtime = new Runtime(body);
            singers.put(body.getUniqueId(), runtime);
            if (permanent) {
                runtime.event.beginPermanent(runtime.env);
            } else {
                runtime.event.begin(golemId, runtime.env);
            }
            runtime.dress();
            runtime.save();
            return runtime;
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[TheSift] Could not spawn a Singer at " + at, failure);
            return null;
        }
    }

    /** Marks the body and sets the mod's stats, before the spawn event so other listeners see them. */
    private void configure(WanderingTrader body) {
        PersistentDataContainer data = body.getPersistentDataContainer();
        data.set(singerKey, PersistentDataType.BYTE, (byte) 1);
        // Any value keeps the mob out of the mob-scaling module.
        data.set(customEntityKey, PersistentDataType.STRING, ENTITY_ID);
        setBase(body, Attribute.SCALE, BODY_SCALE);
        setBase(body, Attribute.MAX_HEALTH, MAX_HEALTH);
        setBase(body, Attribute.MOVEMENT_SPEED, MOVEMENT_SPEED);
        setBase(body, Attribute.KNOCKBACK_RESISTANCE, 0.0);
        body.setHealth(MAX_HEALTH);
        body.setSilent(true);
        body.setRemoveWhenFarAway(false);
        body.setCanPickupItems(false);
        body.setCanDrinkPotion(false);
        body.setCanDrinkMilk(false);
        body.setDespawnDelay(0);
        body.customName(null);
        prepare(body);
    }

    private static void setBase(WanderingTrader body, Attribute attribute, double value) {
        AttributeInstance instance = body.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Goals are not saved with the entity, so the body is stripped of its own every time it loads. */
    private static void prepare(WanderingTrader body) {
        Bukkit.getMobGoals().removeAllGoals(body);
        body.setTarget(null);
    }

    /** Takes a Singer that is already in the world (loaded chunk or activation) under the event again. */
    private void adopt(WanderingTrader body) {
        if (singers.containsKey(body.getUniqueId())) {
            return;
        }
        prepare(body);
        Runtime runtime = new Runtime(body);
        singers.put(body.getUniqueId(), runtime);
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
            if (isSinger(entity)) {
                adopt((WanderingTrader) entity);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            Runtime runtime = singers.get(entity.getUniqueId());
            if (runtime != null) {
                runtime.save();
                runtime.close();
                singers.remove(entity.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        if (inSift(event.getWorld())) {
            singers.values().forEach(Runtime::save);
        }
    }

    private void tickAll() {
        for (Runtime runtime : List.copyOf(singers.values())) {
            if (!runtime.body.isValid()) {
                runtime.close();
                singers.remove(runtime.body.getUniqueId());
                continue;
            }
            if (runtime.body.isTicking()) {
                runtime.tick();
            }
        }
    }

    private void detachAll() {
        for (Runtime runtime : singers.values()) {
            runtime.save();
            runtime.close();
        }
        singers.clear();
    }

    /** The spawn egg content item: right-clicking a block puts a Singer that roams for good on top of it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnEgg(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        Block clicked = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || clicked == null || item == null || !inSift(clicked.getWorld())
            || ContentItemFactory.idOf(item).filter(SiftItems.SINGER_SPAWN_EGG::equals).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Block target = clicked.getRelative(event.getBlockFace());
        if (spawnPermanent(target.getLocation().add(0.5, 0.0, 0.5)) != null && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            item.subtract(1);
        }
    }

    // --- interaction (SingerBarter.interact) ----------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Runtime singer = singers.get(event.getRightClicked().getUniqueId());
        if (singer == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (held.getType() == Material.NAME_TAG) {
            return;
        }
        // She does not trade, lead or breed; the Soul Block is her own business.
        event.setCancelled(true);
        if (!isSoulBlockItem(held) || !singer.event.canBarter(singer.isAlive())) {
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract(1);
        }
        Location at = player.getLocation();
        singer.event.beginBarter(player.getUniqueId(), new SiftPoint(at.getX(), at.getY(), at.getZ()), singer.env);
    }

    private static boolean isSoulBlockItem(ItemStack stack) {
        return !stack.isEmpty() && ContentItemFactory.idOf(stack).filter(SiftCreatures.SOUL_BLOCK::equals).isPresent();
    }

    // --- damage, death and sounds ----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Runtime singer = singers.get(event.getEntity().getUniqueId());
        if (singer == null) {
            return;
        }
        // A killing blow plays the death sound instead.
        if (singer.body.getHealth() - event.getFinalDamage() > 0.0) {
            singer.play("hurt", 1.0F, voicePitch());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        Runtime singer = singers.remove(event.getEntity().getUniqueId());
        if (singer == null) {
            return;
        }
        singer.close();
        event.getDrops().clear();
        if (event.getEntity().getKiller() != null) {
            event.setDroppedExp(8 + random.nextInt(5));
        }
        singer.play("death", 1.0F, voicePitch());
    }

    /** {@code LivingEntity#getVoicePitch}. */
    private float voicePitch() {
        return (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
    }

    // --- the running Singer ---------------------------------------------------------------------------

    /** One Singer in the world: her body, her event and what she looks like. */
    final class Runtime implements SoulExchange.Singer {

        final WanderingTrader body;
        final SingerEvent event = new SingerEvent(SingerTimings.MOD, random);
        final SingerEnv env = new SingerEnv(this);
        private JavaModelHandle handle;
        private ModelPoses poses;
        private BlockDisplay held;
        private Item payment;
        private int age;
        private int ambientTime = -80;
        private int canyonAwarenessCooldown;
        private SiftPos avoidedCanyon;
        private Location lastLocation;

        Runtime(WanderingTrader body) {
            this.body = body;
        }

        @Override
        public UUID id() {
            return body.getUniqueId();
        }

        @Override
        public boolean isAlive() {
            return body.isValid() && !body.isDead();
        }

        @Override
        public boolean isSoulEvent() {
            return true;
        }

        @Override
        public SiftPos blockPosition() {
            return SiftPos.containing(body.getX(), body.getY(), body.getZ());
        }

        @Override
        public SiftPoint position() {
            return new SiftPoint(body.getX(), body.getY(), body.getZ());
        }

        @Override
        public void offerSoulGolem(SoulExchange.Golem golem) {
            event.offer(golem, env);
        }

        void tick() {
            age++;
            if (body.customName() != null) {
                body.setRemoveWhenFarAway(false);
                if (event.phase() == SingerEvent.Phase.DISAPPEAR) {
                    event.stay(env);
                }
            }
            event.tick(env);
            diggingParticles();
            followHeldBlock();
            updatePose();
            ambientSound();
            if (age % SAVE_INTERVAL_TICKS == 0) {
                save();
            }
        }

        private void ambientSound() {
            if (random.nextInt(1000) < ambientTime++) {
                ambientTime = -80;
                play("idle", 1.0F, voicePitch());
            }
        }

        void play(String sound, float volume, float pitch) {
            body.getWorld().playSound(body.getLocation(), "the_sift:entity.singer." + sound, SoundCategory.NEUTRAL, volume, pitch);
        }

        /** The client draws these in the mod: 30 block particles at the feet a tick, for the first 90 ticks of rising and of sinking. */
        private void diggingParticles() {
            SingerEvent.Phase phase = event.phase();
            if (phase != SingerEvent.Phase.APPEAR && phase != SingerEvent.Phase.DISAPPEAR || event.phaseTick() >= DIGGING_TICKS) {
                return;
            }
            Location at = body.getLocation();
            Block below = at.getBlock().getRelative(0, -1, 0);
            if (below.getType().isAir()) {
                return;
            }
            body.getWorld().spawnParticle(Particle.BLOCK, at, DIGGING_PARTICLES, 0.4, 0.0, 0.4, 0.0, below.getBlockData());
        }

        // --- model ---

        void dress() {
            if (handle != null || !body.isValid()) {
                return;
            }
            try {
                handle = models.attach(body, MODEL);
                models.scale(handle, MODEL_SCALE);
                models.brighten(handle, 15, 15, GLOWING_BONES);
                poses = new ModelPoses(models, handle);
                updatePose();
            } catch (RuntimeException unavailable) {
                handle = null;
                poses = null;
                if (!warnedMissingModel) {
                    warnedMissingModel = true;
                    plugin.getLogger().log(Level.WARNING, "[TheSift] Singer model unavailable; Singers render as wandering traders",
                        unavailable);
                }
            }
        }

        private void updatePose() {
            if (poses == null) {
                return;
            }
            poses.show(switch (event.phase()) {
                case APPEAR -> "appear";
                case DISAPPEAR -> "disappear";
                case EXCHANGE -> "receive_soul";
                case BARTER -> event.isHoldingSoul() ? "barter_hold" : "stand";
                case APPROACH, ROAMING -> isWalking() ? "stride" : "stand";
            });
        }

        /** {@code isSoulEventWalkingForAnimation}. */
        private boolean isWalking() {
            Vector velocity = body.getVelocity();
            return velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ() > 9.0E-4
                || body.getPathfinder().hasPath();
        }

        void close() {
            removeHeldBlock();
            if (handle != null) {
                models.close(handle);
                handle = null;
                poses = null;
            }
        }

        // --- the Soul Block in her hands ---

        void showHeldBlock(boolean holding) {
            if (!holding) {
                removeHeldBlock();
                return;
            }
            if (held != null && held.isValid()) {
                return;
            }
            Location at = heldLocation();
            float size = 1.75F * MODEL_SCALE;
            BlockData block = SoulBlockWorld.soulBlockData();
            held = body.getWorld().spawn(at, BlockDisplay.class, display -> {
                display.setPersistent(false);
                display.setBlock(block);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setTransformation(new Transformation(new Vector3f(-size / 2.0F, -size / 2.0F, -size / 2.0F),
                    new AxisAngle4f(), new Vector3f(size, size, size), new AxisAngle4f()));
            });
        }

        /** The mod's {@code held_soul} bone: 11 pixels in front of the body and 15 up, in a model scaled 1.2. */
        private Location heldLocation() {
            Location at = body.getLocation();
            double yaw = Math.toRadians(at.getYaw());
            double forward = 11.0 / 16.0 * MODEL_SCALE;
            return at.add(-Math.sin(yaw) * forward, 15.0 / 16.0 * MODEL_SCALE, Math.cos(yaw) * forward);
        }

        private void followHeldBlock() {
            if (held != null) {
                if (held.isValid()) {
                    held.teleport(heldLocation());
                } else {
                    held = null;
                }
            }
        }

        private void removeHeldBlock() {
            if (held != null) {
                held.remove();
                held = null;
            }
        }

        // --- saving (addAdditionalSaveData, readAdditionalSaveData) ---

        void save() {
            if (!body.isValid()) {
                return;
            }
            PersistentDataContainer state = body.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
            state.set(key("soul_event_phase"), PersistentDataType.INTEGER, event.phase().ordinal());
            state.set(key("soul_event_tick"), PersistentDataType.INTEGER, event.phaseTick());
            state.set(key("permanent_soul_event"), PersistentDataType.BYTE, (byte) (event.isPermanent() ? 1 : 0));
            if (event.golemId() != null) {
                state.set(key("soul_event_golem"), PersistentDataType.STRING, event.golemId().toString());
            }
            if (event.barterPlayer() != null) {
                state.set(key("barter_player"), PersistentDataType.STRING, event.barterPlayer().toString());
            }
            SiftPoint target = event.barterTarget();
            if (target != null) {
                state.set(key("barter_target_x"), PersistentDataType.DOUBLE, target.x());
                state.set(key("barter_target_y"), PersistentDataType.DOUBLE, target.y());
                state.set(key("barter_target_z"), PersistentDataType.DOUBLE, target.z());
            }
            body.getPersistentDataContainer().set(stateKey, PersistentDataType.TAG_CONTAINER, state);
        }

        void restore() {
            PersistentDataContainer state = body.getPersistentDataContainer().get(stateKey, PersistentDataType.TAG_CONTAINER);
            if (state == null) {
                event.begin(null, env);
                return;
            }
            Integer ordinal = state.get(key("soul_event_phase"), PersistentDataType.INTEGER);
            SingerEvent.Phase[] phases = SingerEvent.Phase.values();
            SingerEvent.Phase phase = phases[Math.max(0, Math.min(phases.length - 1, ordinal == null ? 0 : ordinal))];
            Integer tick = state.get(key("soul_event_tick"), PersistentDataType.INTEGER);
            Byte permanent = state.get(key("permanent_soul_event"), PersistentDataType.BYTE);
            Double x = state.get(key("barter_target_x"), PersistentDataType.DOUBLE);
            Double y = state.get(key("barter_target_y"), PersistentDataType.DOUBLE);
            Double z = state.get(key("barter_target_z"), PersistentDataType.DOUBLE);
            SiftPoint target = x == null || y == null || z == null ? null : new SiftPoint(x, y, z);
            event.restore(phase, tick == null ? 0 : tick, uuid(state, "soul_event_golem"), permanent != null && permanent == 1,
                uuid(state, "barter_player"), target);
            boolean intangible = event.isIntangible();
            body.setNoPhysics(intangible);
            body.setGravity(!intangible);
            body.setAI(!intangible);
            body.setInvulnerable(false);
            showHeldBlock(event.isHoldingSoul());
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

    // --- the event's view of the world ------------------------------------------------------------------

    private final class SingerEnv implements SingerEvent.Env {

        private final Runtime singer;

        SingerEnv(Runtime singer) {
            this.singer = singer;
        }

        private WanderingTrader body() {
            return singer.body;
        }

        @Override
        public SoulExchange.Golem golem(UUID id) {
            return golems == null ? null : golems.golem(id);
        }

        @Override
        public boolean isLoaded(UUID id) {
            return Bukkit.getEntity(id) != null;
        }

        @Override
        public SoulExchange.Golem nearestOfferingGolem() {
            return golems == null ? null : golems.nearestOffering(body().getLocation(), 48.0, 28.0);
        }

        @Override
        public double distanceSqrTo(SoulExchange.Golem golem) {
            return singer.position().distanceSqr(golem.position());
        }

        @Override
        public SiftPoint position() {
            return singer.position();
        }

        @Override
        public Optional<SiftPoint> barterPartner(UUID player) {
            Player online = Bukkit.getPlayer(player);
            if (online == null || !online.getWorld().equals(body().getWorld())
                || online.getLocation().distanceSquared(body().getLocation()) > SingerEvent.BARTER_PARTNER_RANGE_SQUARED) {
                return Optional.empty();
            }
            return Optional.of(new SiftPoint(online.getX(), online.getY(), online.getZ()));
        }

        @Override
        public boolean isNamed() {
            return body().customName() != null;
        }

        @Override
        public void playAppearEffects() {
            Location at = body().getLocation();
            at.getWorld().playSound(at, "the_sift:the_sift_portal_open", SoundCategory.BLOCKS, 1.0F, 1.0F);
            at.getWorld().playSound(at, "the_sift:entity.singer.emerge", SoundCategory.HOSTILE, 1.0F, 1.0F);
        }

        @Override
        public void playDisappearEffects() {
            Location at = body().getLocation();
            at.getWorld().playSound(at, "the_sift:entity.singer.dig", SoundCategory.HOSTILE, 1.0F, 1.0F);
            at.getWorld().playSound(at, "the_sift:the_sift_portal_close", SoundCategory.BLOCKS, 1.0F, 1.0F);
        }

        @Override
        public void becomeSolid() {
            body().setNoPhysics(false);
            body().setGravity(true);
            body().setAI(true);
            body().setInvulnerable(false);
        }

        @Override
        public void becomeIntangible() {
            body().getPathfinder().stopPathfinding();
            body().setNoPhysics(true);
            body().setGravity(false);
            body().setAI(false);
            body().setInvulnerable(false);
        }

        @Override
        public void stopNavigation() {
            body().getPathfinder().stopPathfinding();
        }

        @Override
        public boolean navigationDone() {
            return !body().getPathfinder().hasPath();
        }

        @Override
        public void moveToGolem(SoulExchange.Golem golem, double speed) {
            SiftPoint at = golem.position();
            body().getPathfinder().moveTo(new Location(body().getWorld(), at.x(), at.y(), at.z()), speed);
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
        public void lookAt(SoulExchange.Golem golem) {
            SiftPoint at = golem.position();
            body().lookAt(at.x(), at.y(), at.z(), 12.0F, 8.0F);
        }

        @Override
        public void playHappy(float pitch) {
            singer.play("happy", 1.0F, pitch);
        }

        @Override
        public void holdSoulBlock(boolean holding) {
            singer.showHeldBlock(holding);
        }

        /** {@code seekBarterPayment}: walks to a Soul Block lying within 8 blocks and takes it. */
        @Override
        public boolean seekPayment() {
            Item target = singer.payment;
            if (target != null && (!target.isValid() || !isSoulBlockItem(target.getItemStack())
                || target.getLocation().distanceSquared(body().getLocation()) > 64.0)) {
                target = null;
            }
            if (target == null && singer.age % 10 == 0) {
                target = nearestSoulBlockItem();
            }
            singer.payment = target;
            if (target == null) {
                return false;
            }
            BoundingBox reach = body().getBoundingBox().expand(0.8);
            if (reach.overlaps(target.getBoundingBox())) {
                takePayment(target);
                singer.payment = null;
                return true;
            }
            Pathfinder pathfinder = body().getPathfinder();
            if ((!pathfinder.hasPath() || singer.age % 10 == 0) && !pathfinder.moveTo(target.getLocation(), 1.0)) {
                singer.payment = null;
                return false;
            }
            return true;
        }

        private Item nearestSoulBlockItem() {
            Location at = body().getLocation();
            return body().getWorld().getNearbyEntities(body().getBoundingBox().expand(8.0, 3.0, 8.0),
                    entity -> entity instanceof Item item && item.isValid() && isSoulBlockItem(item.getItemStack()))
                .stream().map(entity -> (Item) entity)
                .min(Comparator.comparingDouble(item -> item.getLocation().distanceSquared(at))).orElse(null);
        }

        /** {@code SingerBarter.pickUp}. */
        private void takePayment(Item dropped) {
            if (!singer.event.canBarter(singer.isAlive())) {
                return;
            }
            UUID thrower = dropped.getThrower();
            Player owner = thrower == null ? null : Bukkit.getPlayer(thrower);
            Location at = owner != null ? owner.getLocation() : dropped.getLocation();
            singer.event.beginBarter(owner == null ? null : owner.getUniqueId(), new SiftPoint(at.getX(), at.getY(), at.getZ()), this);
            ItemStack stack = dropped.getItemStack();
            stack.subtract(1);
            if (stack.isEmpty()) {
                dropped.remove();
            } else {
                dropped.setItemStack(stack);
            }
        }

        /** {@code gentlyAvoidActiveSoulCanyon}: within 17 blocks of a canyon with Soul Blocks, below its rim, she walks away. */
        @Override
        public boolean avoidSoulCanyon() {
            SiftPos here = singer.blockPosition();
            if (--singer.canyonAwarenessCooldown <= 0) {
                singer.canyonAwarenessCooldown = 40;
                singer.avoidedCanyon = landmarks.nearestActiveCluster(here, CANYON_AVOID_DISTANCE, Set.of(),
                    cluster -> !soulBlocks.activeNear(landmarks, cluster, 36, 28).isEmpty()).orElse(null);
            }
            SiftPos canyon = singer.avoidedCanyon;
            if (canyon == null) {
                return false;
            }
            boolean belowRim = body().getY() <= canyon.y() + 10.0;
            if (!belowRim || here.horizontalDistanceSqr(canyon) > 289.0) {
                return false;
            }
            if (!body().getPathfinder().hasPath() || singer.event.phaseTick() % 40 == 1) {
                Optional<Location> away = LandSpots.away(body().getWorld(), body().getLocation(), canyon.center(), 15, 8, 5, random);
                if (away.isPresent()) {
                    body().getPathfinder().moveTo(away.get(), 0.66);
                } else if (!body().getPathfinder().hasPath()) {
                    return false;
                }
            }
            return true;
        }

        /** {@code chooseSoulRoamDestination}: five tries at a spot within 12 blocks that is 18 or more from a canyon with Soul Blocks. */
        @Override
        public void roam(double speed) {
            SiftPos canyon = singer.avoidedCanyon;
            for (int attempt = 0; attempt < 5; attempt++) {
                Optional<Location> destination = LandSpots.any(body().getWorld(), body().getLocation(), 12, 6, 5, random);
                if (destination.isEmpty()) {
                    continue;
                }
                Location at = destination.get();
                if (canyon != null && SiftPos.containing(at.getX(), at.getY(), at.getZ()).horizontalDistanceSqr(canyon) < 324.0) {
                    continue;
                }
                body().getPathfinder().moveTo(at, speed);
                return;
            }
        }

        /** {@code SingerBarter.reward}: the gift lands at her chest and flies toward the player. */
        @Override
        public void reward(UUID playerId, SiftPoint target) {
            ItemStack stack = rollGift();
            Location at = body().getLocation();
            Vector direction = target == null ? new Vector() : new Vector(target.x() - at.getX(), 0.0, target.z() - at.getZ());
            if (direction.lengthSquared() < 0.001) {
                direction = at.getDirection().setY(0.0);
            }
            direction = direction.lengthSquared() < 1.0E-6 ? new Vector(0, 0, 1) : direction.normalize();
            Location drop = at.clone().add(direction.getX() * 0.6, 1.2, direction.getZ() * 0.6);
            Vector push = direction.clone().multiply(0.3).setY(0.2);
            body().getWorld().dropItem(drop, stack, item -> {
                item.setVelocity(push);
                item.setPickupDelay(10);
            });
            Player player = playerId == null ? null : Bukkit.getPlayer(playerId);
            if (player != null) {
                SiftAdvancements.award(player, SiftAdvancements.PITCH_PERFECT);
            }
        }

        private ItemStack rollGift() {
            SingerBarter.Reward reward = SingerBarter.rollAvailable(random, id -> stackFor(id, 1).isPresent());
            return stackFor(reward.item(), reward.count()).orElseThrow();
        }

        private Optional<ItemStack> stackFor(String id, int count) {
            if (id.startsWith("the_sift:")) {
                return items.apply(id, count);
            }
            Material material = Material.matchMaterial(id);
            return material == null ? Optional.empty() : Optional.of(new ItemStack(material, count));
        }

        @Override
        public void discard() {
            Runtime removed = singers.remove(body().getUniqueId());
            if (removed != null) {
                removed.close();
            }
            body().remove();
        }

        @Override
        public void phaseChanged() {
            singer.save();
        }
    }

    // --- spawn search ----------------------------------------------------------------------------------

    /** {@link SoulExchange.Level} over a loaded Bukkit world. */
    private static final class SpawnLevel implements SoulExchange.Level {

        private final World world;

        SpawnLevel(World world) {
            this.world = world;
        }

        @Override
        public int height(int x, int z) {
            return world.isChunkLoaded(x >> 4, z >> 4)
                ? world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1 : world.getMinHeight();
        }

        @Override
        public boolean isLoaded(SiftPos pos) {
            return pos.y() >= world.getMinHeight() && pos.y() < world.getMaxHeight() && world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4);
        }

        @Override
        public boolean isFullBlock(SiftPos pos) {
            if (!isLoaded(pos)) {
                return false;
            }
            double volume = 0.0;
            for (BoundingBox box : world.getBlockAt(pos.x(), pos.y(), pos.z()).getCollisionShape().getBoundingBoxes()) {
                volume += box.getVolume();
            }
            return volume >= 1.0 - 1.0E-6;
        }

        @Override
        public boolean isAir(SiftPos pos) {
            return isLoaded(pos) && world.getBlockAt(pos.x(), pos.y(), pos.z()).getType().isAir();
        }
    }
}
