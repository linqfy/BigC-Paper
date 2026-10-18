package dev.linqfy.bigCasares.modules.sift;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * The Sifter, ported from the mod's {@code SifterEntity} and {@code SiftSifterSpawner}: a
 * hostile, fast, hard-biting creature that spawns in groups near players across the
 * Sift. Vanilla clients cannot know the mod's entity type, so a Sifter is a silent
 * <b>silverfish</b> (the server-side body, marked in its PersistentDataContainer) dressed
 * with the converted GeckoLib model through BetterModel, like the Blub.
 *
 * <p><b>Why a silverfish.</b> The body has to be a hostile {@code Monster} with goal-based
 * (not Brain-based, so Paper's {@code MobGoals} can shape it) melee AI that targets players
 * on its own, must not burn in daylight (the Sift has a day cycle: zombies and skeletons
 * would), must not climb walls (spiders do, and nothing in the API turns it off) and
 * should be as close as practical to the mod's 0.9 x 0.95 hitbox. A silverfish is exactly
 * float + melee + hurt-by/nearest-player targeting on a 0.4 x 0.3 body; the {@code scale}
 * attribute at 2.25 makes it 0.9 x 0.675. The width matters most: it sets the melee
 * reach, the collision with the world and, as a width under 1.0, the one-block footprint
 * the pathfinder plans with (from 1.0 up it plans with 2x2 and could not follow a player
 * through a one-block corridor); the height cannot be had without a wider body. Zombies
 * and skeletons are too tall and burn, spiders climb, endermites despawn after two
 * minutes and glow with portal particles on every client, slimes hop, and the hoglin family
 * runs on the Brain.</p>
 *
 * <p>Ported from the mod: the spawner ({@link SifterSpawning}: every 80 ticks near each
 * player, never on Peaceful, floors, caves, biome profiles, caps and groups), stats (40
 * health, 0.31 speed with the melee goal's 1.18 modifier, 10 attack, 28 follow range, 1
 * block step height), the AI (float, melee that chases players, retaliation against whoever
 * hurt it, strolling at 0.95 away from water), the 14-tick bite (it stops, the attack
 * sound plays on tick 8, the damage lands on tick 13 only if the target is still within 2.5
 * blocks and in sight, and the attack animation plays), 7 experience, the loot table
 * ({@link SifterLoot}: raw Sifter meat, cooked when the Sifter dies on fire, plus Looting;
 * nothing vanilla), the textures, the walk and attack animations (the run animation at the
 * mod's 2.5x speed) and the {@code entity.sifter.*} sounds.</p>
 *
 * <p>Differences:</p>
 * <ul>
 *   <li>The body is a silverfish (a {@code Monster} in the {@code MONSTER} category; the mod's
 *       type is {@code CREATURE}) and is an arthropod, so Bane of Arthropods hits it. Its
 *       hitbox is 0.9 x 0.675 instead of 0.9 x 0.95, with a lower eye height. The model is
 *       pinned to scale 1 so BetterModel does not grow it with the scale attribute.</li>
 *   <li>Silverfish goals are changed: hiding in stone and waking friends are removed, the
 *       melee goal keeps vanilla's speed 1.0 so the base speed is 0.31 x 1.18 and the stroll
 *       goal (a port of {@code WaterAvoidingRandomStrollGoal} on Paper's goal API, with a
 *       simpler position search) uses 0.95 / 1.18. Looking at nearby players and looking
 *       around ({@code LookAtPlayerGoal}, {@code RandomLookAroundGoal}) are not ported. The
 *       silverfish alerts nearby silverfish when hurt, the mod's Sifter does not: those
 *       alerts are cancelled.</li>
 *   <li>The bite is the mod's, started when the vanilla melee goal attacks: that hit is
 *       cancelled and the damage is applied later as a mob attack from the Sifter. The
 *       Sifter stands still from the next goal update (at most two ticks late) and keeps
 *       closing in without attacking until vanilla's 20-tick attack cooldown is over, so
 *       the rate matches the mod's. Bites in progress are not saved. The attack
 *       animation starts with the bite, the mod triggers it in the same place.</li>
 *   <li>Step sounds are not played (Bukkit has no step hook); ambient sounds are rolled by a
 *       20-tick task (about one every six seconds near players) instead of vanilla's
 *       per-tick roll. All sounds use the hostile category.</li>
 *   <li>Experience is 7 only when vanilla grants any (a player killed it); Looting comes from the
 *       killing player's main hand, vanilla reads any killer's.</li>
 *   <li>The body carries the name "Sifter" (hidden, shown while aimed at) so deaths read
 *       "slain by Sifter", and the {@code custom_entity} marker that keeps it out of the
 *       mob-scaling module, whose random mutations (an "infestation" removes the mob when
 *       it hits) and stat scaling would otherwise apply to every silverfish.</li>
 *   <li>The spawner works on loaded chunks only (the mod's block reads load them on the main
 *       thread), spawns with {@code SpawnReason.NATURAL} as the mod's {@code EntitySpawnReason},
 *       and treats the mod's collision check of the new Sifter as a check of its 0.9 x
 *       0.95 box. {@code checkSifterSpawnRules}, only used for the mod's natural spawning
 *       (which has no entry in any biome), is not ported. The mod's cooldown is global,
 *       here the task belongs to the Sift dimension.</li>
 *   <li>The Sifter spawn egg is a content item on a silverfish egg ({@link SiftSpawnEggs}) that calls
 *       {@link #spawnFromEgg}. Not ported: the {@code certified_sifter} advancement (it only asks for
 *       visiting biomes).</li>
 *   <li>GeckoLib's easings between keyframes become linear in the converted model (see
 *       {@code tools/convert_geckolib_bbmodel.py}).</li>
 * </ul>
 */
final class SiftSifters implements Listener {

    static final String MODEL = "the_sift_sifter";
    static final String ENTITY_ID = "the_sift:sifter";

    private static final double SCALE = 2.25;
    private static final double MAX_HEALTH = 40.0;
    private static final double BASE_SPEED = 0.31;
    /** The mod's {@code MeleeAttackGoal} speed modifier; vanilla's silverfish goal uses 1.0. */
    private static final double PURSUIT_FACTOR = 1.18;
    private static final double STROLL_FACTOR = 0.95;
    private static final double ATTACK_DAMAGE = 10.0;
    private static final double FOLLOW_RANGE = 28.0;
    private static final double STEP_HEIGHT = 1.0;
    private static final int EXPERIENCE = 7;
    /** The mod's entity size, for the spawn collision check. */
    private static final double MOD_WIDTH = 0.9;
    private static final double MOD_HEIGHT = 0.95;
    private static final String CUSTOM_ENTITY_KEY = "custom_entity";
    private static final String ATTACK_ANIMATION = "attack";
    private static final float AMBIENT_CHANCE = 1.0F / 6.0F;
    private static final double AMBIENT_RANGE_SQUARED = 32.0 * 32.0;
    private static final int STROLL_CHECKS = 60;
    private static final int STROLL_RADIUS = 10;
    private static final int STROLL_HEIGHT = 7;
    private static final BoundingBox FULL_BLOCK = new BoundingBox(0, 0, 0, 1, 1, 1);

    private final JavaPlugin plugin;
    private final JavaModelGateway models;
    private final BiFunction<String, Integer, Optional<ItemStack>> items;
    private final SifterLoot loot;
    private final NamespacedKey sifterKey;
    private final NamespacedKey customEntityKey;
    private final GoalKey<Silverfish> biteGoalKey;
    private final GoalKey<Silverfish> strollGoalKey;
    private final Map<UUID, JavaModelHandle> attached = new HashMap<>();
    private final Map<UUID, Bite> bites = new HashMap<>();
    private final Random random = new Random();
    private Set<BlockData> floors = Set.of();
    private boolean applyingBite;
    private boolean warnedMissingModel;
    private ActiveDimension dimension;

    /** A bite in progress: who bites whom, and where it is in its timeline. */
    private record Bite(Silverfish sifter, LivingEntity target, SifterBite timeline) {
    }

    SiftSifters(JavaPlugin plugin, JavaModelGateway models, BiFunction<String, Integer, Optional<ItemStack>> items,
                SifterLoot loot) {
        this.plugin = plugin;
        this.models = models;
        this.items = items;
        this.loot = loot;
        this.sifterKey = new NamespacedKey(plugin, "sift_sifter");
        this.customEntityKey = new NamespacedKey(plugin, CUSTOM_ENTITY_KEY);
        this.biteGoalKey = GoalKey.of(Silverfish.class, new NamespacedKey(plugin, "sifter_bite"));
        this.strollGoalKey = GoalKey.of(Silverfish.class, new NamespacedKey(plugin, "sifter_stroll"));
    }

    void activate(ActiveDimension active) {
        this.dimension = active;
        this.floors = SifterSpawning.FLOORS.stream()
            .map(state -> Bukkit.createBlockData(state.asString()))
            .collect(Collectors.toUnmodifiableSet());
        active.registrations().registerListener("sift-sifters", this);
        active.registrations().ownCleanup("sift-sifter-models", this::detachAll);
        for (Entity entity : active.world().getEntities()) {
            if (entity instanceof Silverfish silverfish && isSifter(silverfish)) {
                prepare(silverfish);
            }
        }
        active.registrations().scheduleRepeating("sifter-spawner", this::spawnPass, 1L, SifterSpawning.CHECK_INTERVAL_TICKS);
        active.registrations().scheduleRepeating("sifter-bites", this::tickBites, 1L, 1L);
        active.registrations().scheduleRepeating("sifter-ambience", this::ambience, 20L, 20L);
    }

    /** Dresses loaded Sifters that have no model yet, e.g. after BetterModel finished loading. */
    void redress() {
        ActiveDimension active = dimension;
        if (active == null) {
            return;
        }
        for (Entity entity : active.world().getEntities()) {
            if (entity instanceof Silverfish silverfish && isSifter(silverfish)) {
                dress(silverfish);
            }
        }
    }

    boolean isSifter(Entity entity) {
        return entity instanceof Silverfish && entity.getPersistentDataContainer().has(sifterKey, PersistentDataType.BYTE);
    }

    private boolean inSift(World world) {
        return dimension != null && dimension.world().getUID().equals(world.getUID());
    }

    // --- spawner (SiftSifterSpawner.tick) -----------------------------------------------

    private void spawnPass() {
        ActiveDimension active = dimension;
        if (active == null) {
            return;
        }
        World world = active.world();
        List<SifterSpawning.Anchor> anchors = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (!player.isDead() && player.getGameMode() != GameMode.SPECTATOR) {
                Location at = player.getLocation();
                anchors.add(new SifterSpawning.Anchor(at.getBlockX(), at.getBlockZ()));
            }
        }
        if (anchors.isEmpty()) {
            return;
        }
        SifterSpawning.pass(world.getDifficulty() == Difficulty.PEACEFUL, anchors, new WorldLevel(world), random,
            (feet, yaw) -> spawnSifter(world, feet, yaw));
    }

    private boolean spawnSifter(World world, SifterSpawning.Feet feet, float yaw) {
        Location at = new Location(world, feet.x() + 0.5, feet.y(), feet.z() + 0.5, yaw, 0.0F);
        BoundingBox box = new BoundingBox(at.getX() - MOD_WIDTH / 2, at.getY(), at.getZ() - MOD_WIDTH / 2,
            at.getX() + MOD_WIDTH / 2, at.getY() + MOD_HEIGHT, at.getZ() + MOD_WIDTH / 2);
        if (world.hasCollisionsIn(box)) {
            return false;
        }
        return spawn(at, CreatureSpawnEvent.SpawnReason.NATURAL);
    }

    /**
     * The Sifter spawn egg: the same Sifter the spawner makes, with the egg's spawn reason. There is no
     * collision check; like a vanilla egg it spawns where the player points. False when the Sift is not active
     * here or the spawn was refused.
     */
    boolean spawnFromEgg(Location at) {
        return inSift(at.getWorld()) && spawn(at, CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);
    }

    private boolean spawn(Location at, CreatureSpawnEvent.SpawnReason reason) {
        try {
            Silverfish sifter = at.getWorld().spawn(at, Silverfish.class, reason, this::configure);
            if (!sifter.isValid()) {
                return false;
            }
            prepare(sifter);
            return true;
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[TheSift] Could not spawn a Sifter at " + at, failure);
            return false;
        }
    }

    /** Marks the body and sets the mod's stats, before the spawn event so other listeners see them. */
    private void configure(Silverfish sifter) {
        PersistentDataContainer data = sifter.getPersistentDataContainer();
        data.set(sifterKey, PersistentDataType.BYTE, (byte) 1);
        // Any value keeps the mob out of the mob-scaling module (see the class documentation).
        data.set(customEntityKey, PersistentDataType.STRING, ENTITY_ID);
        setBase(sifter, Attribute.SCALE, SCALE);
        setBase(sifter, Attribute.MAX_HEALTH, MAX_HEALTH);
        setBase(sifter, Attribute.MOVEMENT_SPEED, BASE_SPEED * PURSUIT_FACTOR);
        setBase(sifter, Attribute.ATTACK_DAMAGE, ATTACK_DAMAGE);
        setBase(sifter, Attribute.FOLLOW_RANGE, FOLLOW_RANGE);
        setBase(sifter, Attribute.STEP_HEIGHT, STEP_HEIGHT);
        sifter.setHealth(MAX_HEALTH);
        sifter.setSilent(true);
        sifter.customName(Component.translatable("entity.the_sift.sifter", "Sifter"));
        sifter.setCustomNameVisible(false);
    }

    private static void setBase(Silverfish sifter, Attribute attribute, double value) {
        AttributeInstance instance = sifter.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** {@link SifterSpawning.Level} over a loaded Bukkit world. */
    private final class WorldLevel implements SifterSpawning.Level {

        private final World world;

        private WorldLevel(World world) {
            this.world = world;
        }

        @Override
        public int minY() {
            return world.getMinHeight();
        }

        @Override
        public boolean isLoaded(int x, int z) {
            return world.isChunkLoaded(x >> 4, z >> 4);
        }

        @Override
        public int height(int x, int z) {
            // LevelReader.getHeight: the first free Y; an unloaded column reads as the world bottom.
            return isLoaded(x, z) ? world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1 : minY();
        }

        @Override
        public boolean isSpawnFloor(int x, int y, int z) {
            if (!isLoaded(x, z) || y < world.getMinHeight() || y >= world.getMaxHeight()) {
                return false;
            }
            Block block = world.getBlockAt(x, y, z);
            return floors.contains(block.getBlockData()) && isFullCube(block);
        }

        @Override
        public boolean hasEmptyCollision(int x, int y, int z) {
            if (!isLoaded(x, z)) {
                return false;
            }
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                return true;
            }
            return world.getBlockAt(x, y, z).getCollisionShape().getBoundingBoxes().isEmpty();
        }

        @Override
        public SifterSpawning.Profile profileAt(int x, int y, int z) {
            return SifterSpawning.Profile.ofBiome(world.getBiome(x, y, z).getKey().asString());
        }

        @Override
        public int sifterCount(SifterSpawning.Feet around) {
            int radius = SifterSpawning.POPULATION_RADIUS;
            int height = SifterSpawning.POPULATION_HEIGHT;
            BoundingBox area = new BoundingBox(around.x() - radius, around.y() - height, around.z() - radius,
                around.x() + 1 + radius, around.y() + 1 + height, around.z() + 1 + radius);
            return world.getNearbyEntities(area, SiftSifters.this::isSifter).size();
        }
    }

    /** {@code isCollisionShapeFullBlock}: the collision boxes add up to one block. */
    private static boolean isFullCube(Block block) {
        Collection<BoundingBox> boxes = block.getCollisionShape().getBoundingBoxes();
        double volume = 0.0;
        for (BoundingBox box : boxes) {
            volume += box.getVolume();
        }
        return volume >= FULL_BLOCK.getVolume() - 1.0E-6;
    }

    // --- lifecycle -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!inSift(event.getWorld())) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Silverfish silverfish && isSifter(silverfish)) {
                prepare(silverfish);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            bites.remove(entity.getUniqueId());
            undress(entity.getUniqueId());
        }
    }

    /** Goals are not saved with the entity, so they are adjusted every time a Sifter loads. */
    private void prepare(Silverfish sifter) {
        MobGoals goals = Bukkit.getMobGoals();
        goals.removeGoal(sifter, VanillaGoal.SILVERFISH_MERGE_WITH_STONE);
        goals.removeGoal(sifter, VanillaGoal.SILVERFISH_WAKE_UP_FRIENDS);
        if (!goals.hasGoal(sifter, biteGoalKey)) {
            goals.addGoal(sifter, 2, new BiteGoal(sifter));
        }
        if (!goals.hasGoal(sifter, strollGoalKey)) {
            goals.addGoal(sifter, 5, new StrollGoal(sifter));
        }
        dress(sifter);
    }

    private void dress(Silverfish sifter) {
        if (attached.containsKey(sifter.getUniqueId())) {
            return;
        }
        try {
            JavaModelHandle handle = models.attach(sifter, MODEL);
            // The body is scaled to get its hitbox; the model must keep the mod's size.
            models.scale(handle, 1.0F);
            attached.put(sifter.getUniqueId(), handle);
        } catch (RuntimeException unavailable) {
            // Without the model the Sifter stays a visible (big) silverfish rather than nothing;
            // the next load or redress() tries again.
            if (!warnedMissingModel) {
                warnedMissingModel = true;
                plugin.getLogger().log(Level.WARNING, "[TheSift] Sifter model unavailable; Sifters render as silverfish",
                    unavailable);
            }
        }
    }

    private void undress(UUID entityId) {
        JavaModelHandle handle = attached.remove(entityId);
        if (handle != null) {
            models.close(handle);
        }
    }

    private void detachAll() {
        for (JavaModelHandle handle : attached.values()) {
            models.close(handle);
        }
        attached.clear();
        bites.clear();
    }

    // --- targeting, bite and damage ------------------------------------------------------

    /** The mod's {@code HurtByTargetGoal} does not alert other Sifters, vanilla's silverfish one does. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getReason() == EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY && isSifter(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Vanilla's melee goal attacks: the hit becomes the mod's bite ({@code doHurtTarget}), whose
     * damage lands {@value SifterBite#DAMAGE_TICK} ticks later (see {@link #tickBites}).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (applyingBite || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
            || !(event.getDamager() instanceof Silverfish sifter) || !isSifter(sifter)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getEntity() instanceof LivingEntity target) || bites.containsKey(sifter.getUniqueId())
            || !canAttack(sifter, target)) {
            return;
        }
        bites.put(sifter.getUniqueId(), new Bite(sifter, target, new SifterBite()));
        sifter.getPathfinder().stopPathfinding();
        JavaModelHandle handle = attached.get(sifter.getUniqueId());
        if (handle != null) {
            models.animateOnce(handle, ATTACK_ANIMATION, () -> { });
        }
    }

    /** {@code canAttack}: not on Peaceful, and a target that can be seen as an enemy. */
    private static boolean canAttack(Silverfish sifter, LivingEntity target) {
        if (sifter.getWorld().getDifficulty() == Difficulty.PEACEFUL || !target.isValid() || target.isDead()) {
            return false;
        }
        if (target instanceof Player player) {
            return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
        }
        return !target.isInvulnerable();
    }

    private void tickBites() {
        if (bites.isEmpty()) {
            return;
        }
        // Damage can kill the biter (thorns), which removes it from the map.
        for (Bite bite : List.copyOf(bites.values())) {
            Silverfish sifter = bite.sifter();
            if (!sifter.isValid() || sifter.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
                bites.remove(sifter.getUniqueId());
                continue;
            }
            switch (bite.timeline().advance()) {
                case SOUND -> play(sifter.getLocation(), "attack", 0.9F, 1.15F + random.nextFloat() * 0.15F);
                case DAMAGE -> strike(bite);
                case FINISHED -> bites.remove(sifter.getUniqueId());
                default -> { }
            }
        }
    }

    /** {@code applyBiteDamage}: the target must still be alive, attackable, within 2.5 blocks and in sight. */
    private void strike(Bite bite) {
        Silverfish sifter = bite.sifter();
        LivingEntity target = bite.target();
        if (!target.isValid() || !target.getWorld().equals(sifter.getWorld()) || !canAttack(sifter, target)
            || sifter.getLocation().distanceSquared(target.getLocation()) > SifterBite.REACH_SQUARED
            || !sifter.hasLineOfSight(target)) {
            return;
        }
        AttributeInstance damage = sifter.getAttribute(Attribute.ATTACK_DAMAGE);
        DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK)
            .withCausingEntity(sifter).withDirectEntity(sifter).build();
        applyingBite = true;
        try {
            target.damage(damage == null ? ATTACK_DAMAGE : damage.getValue(), source);
        } finally {
            applyingBite = false;
        }
    }

    // --- death, sounds -------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isSifter(entity)) {
            return;
        }
        bites.remove(entity.getUniqueId());
        undress(entity.getUniqueId());
        // Nothing vanilla drops, only the mod's table.
        event.getDrops().clear();
        Player killer = entity.getKiller();
        int looting = killer == null ? 0 : killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        for (SifterLoot.Drop drop : loot.roll(ThreadLocalRandom.current(), looting, entity.getFireTicks() > 0)) {
            items.apply(drop.item(), drop.count()).ifPresent(event.getDrops()::add);
        }
        if (event.getDroppedExp() > 0) {
            event.setDroppedExp(EXPERIENCE);
        }
        play(entity.getLocation(), "death", 1.0F, voicePitch());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Silverfish sifter) || !isSifter(sifter)) {
            return;
        }
        // A killing blow plays the death sound instead.
        if (sifter.getHealth() - event.getFinalDamage() > 0.0) {
            play(sifter.getLocation(), "hurt", 1.0F, voicePitch());
        }
    }

    /** Occasional calls near players, in place of vanilla's per-tick ambient roll. */
    private void ambience() {
        ActiveDimension active = dimension;
        if (active == null || active.world().getPlayers().isEmpty()) {
            return;
        }
        for (Silverfish sifter : active.world().getEntitiesByClass(Silverfish.class)) {
            if (isSifter(sifter) && random.nextFloat() < AMBIENT_CHANCE && nearPlayer(active.world(), sifter.getLocation())) {
                play(sifter.getLocation(), "ambient", 1.0F, voicePitch());
            }
        }
    }

    private static boolean nearPlayer(World world, Location location) {
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) < AMBIENT_RANGE_SQUARED) {
                return true;
            }
        }
        return false;
    }

    /** {@code LivingEntity#getVoicePitch}. */
    private float voicePitch() {
        return (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
    }

    private static void play(Location at, String event, float volume, float pitch) {
        at.getWorld().playSound(at, "the_sift:entity.sifter." + event, SoundCategory.HOSTILE, volume, pitch);
    }

    // --- goals ---------------------------------------------------------------------------

    /**
     * Holds the Sifter during its bite: still and facing the target while the animation
     * plays, then closing in until vanilla's melee cooldown is over. It outranks the melee
     * goal, which resumes (with its cooldown spent) once the bite is finished.
     */
    private final class BiteGoal implements Goal<Silverfish> {

        private final Silverfish sifter;

        private BiteGoal(Silverfish sifter) {
            this.sifter = sifter;
        }

        @Override
        public boolean shouldActivate() {
            return bites.containsKey(sifter.getUniqueId());
        }

        @Override
        public void tick() {
            Bite bite = bites.get(sifter.getUniqueId());
            if (bite == null) {
                return;
            }
            if (bite.timeline().frozen()) {
                sifter.getPathfinder().stopPathfinding();
                if (bite.target().isValid()) {
                    sifter.lookAt(bite.target(), 30.0F, 30.0F);
                }
            } else if (bite.target().isValid() && !sifter.getPathfinder().hasPath()) {
                sifter.getPathfinder().moveTo(bite.target(), 1.0);
            }
        }

        @Override
        public void stop() {
            sifter.getPathfinder().stopPathfinding();
        }

        @Override
        public GoalKey<Silverfish> getKey() {
            return biteGoalKey;
        }

        @Override
        public EnumSet<GoalType> getTypes() {
            return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
        }
    }

    /**
     * {@code WaterAvoidingRandomStrollGoal(sifter, 0.95)}: now and then walks to a random dry
     * spot within 10 blocks (7 up or down) while it has no target.
     */
    private final class StrollGoal implements Goal<Silverfish> {

        private final Silverfish sifter;
        private Location destination;

        private StrollGoal(Silverfish sifter) {
            this.sifter = sifter;
        }

        @Override
        public boolean shouldActivate() {
            // Evaluated every other tick, like a vanilla goal with an interval of 120.
            if (sifter.getTarget() != null || ThreadLocalRandom.current().nextInt(STROLL_CHECKS) != 0) {
                return false;
            }
            destination = randomDrySpot(sifter);
            return destination != null;
        }

        @Override
        public boolean shouldStayActive() {
            return sifter.getTarget() == null && sifter.getPathfinder().hasPath();
        }

        @Override
        public void start() {
            if (destination != null) {
                sifter.getPathfinder().moveTo(destination, STROLL_FACTOR / PURSUIT_FACTOR);
            }
        }

        @Override
        public void stop() {
            destination = null;
            sifter.getPathfinder().stopPathfinding();
        }

        @Override
        public GoalKey<Silverfish> getKey() {
            return strollGoalKey;
        }

        @Override
        public EnumSet<GoalType> getTypes() {
            return EnumSet.of(GoalType.MOVE);
        }
    }

    /** A spot with a solid, dry floor and two free blocks above it, in a loaded chunk; null after ten misses. */
    private static Location randomDrySpot(Silverfish sifter) {
        World world = sifter.getWorld();
        Location from = sifter.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = from.getBlockX() + random.nextInt(2 * STROLL_RADIUS + 1) - STROLL_RADIUS;
            int z = from.getBlockZ() + random.nextInt(2 * STROLL_RADIUS + 1) - STROLL_RADIUS;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int top = Math.min(world.getMaxHeight() - 2, from.getBlockY() + STROLL_HEIGHT);
            int bottom = Math.max(world.getMinHeight() + 1, from.getBlockY() - STROLL_HEIGHT);
            for (int y = top; y >= bottom; y--) {
                Block floor = world.getBlockAt(x, y - 1, z);
                if (!floor.getCollisionShape().getBoundingBoxes().isEmpty() && !floor.isLiquid() && isFree(world, x, y, z)
                    && isFree(world, x, y + 1, z)) {
                    return new Location(world, x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    private static boolean isFree(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        return block.getCollisionShape().getBoundingBoxes().isEmpty() && !block.isLiquid();
    }
}
