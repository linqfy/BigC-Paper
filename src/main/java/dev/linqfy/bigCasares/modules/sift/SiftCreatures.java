package dev.linqfy.bigCasares.modules.sift;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * The Blub, ported from the mod's {@code BlubEntity}: a neutral, tamable
 * companion of the overgrown biomes. Vanilla clients cannot know the mod's entity
 * type, so a Blub is a silent wolf (the vanilla mob with the same sit/follow/
 * defend-owner AI) dressed with the converted GeckoLib model through BetterModel.
 *
 * <p>Ported from the mod: biome spawning, stats (20 health, 0.3 speed, 3 attack,
 * 24 follow range, 1 block step), Soul Block temptation, taming with a Soul Block
 * (one in three, smoke on failure, hearts on success), healing with a Soul Block or
 * food, sit toggling by the owner, owner defence, the wild/tamed textures, the
 * idle/walk/happy/sitting animations and the Blub sounds. Wild-wolf behaviour the
 * Blub does not have (hunting sheep/rabbits/foxes/skeletons, begging for bones,
 * fleeing llamas, pouncing, bone taming) is removed.</p>
 *
 * <p>Not ported: the mod's swimming behaviour and ear droop by health; the
 * {@code the_sift/blub} advancement.</p>
 */
final class SiftCreatures implements Listener {

    static final String BLUB_MODEL = "the_sift_blub";
    static final String BLUB_TAMED_MODEL = "the_sift_blub_tamed";
    static final String SINGER_MODEL = "the_sift_singer";
    static final NamespacedId SOUL_BLOCK = NamespacedId.of(SiftBlocks.NS, "soul_block");
    private static final double TEMPT_RANGE = 10.0;
    private static final String SITTING = "sitting";

    private final JavaPlugin plugin;
    private final JavaModelGateway models;
    private final NamespacedKey blubKey;
    private final NamespacedKey plainWolfKey;
    private final GoalKey<Wolf> temptKey;
    private final Map<UUID, JavaModelHandle> attached = new HashMap<>();
    private boolean warnedMissingModel;
    private ActiveDimension dimension;

    SiftCreatures(JavaPlugin plugin, JavaModelGateway models) {
        this.plugin = plugin;
        this.models = models;
        this.blubKey = new NamespacedKey(plugin, "sift_blub");
        this.plainWolfKey = new NamespacedKey(plugin, "sift_plain_wolf");
        this.temptKey = GoalKey.of(Wolf.class, new NamespacedKey(plugin, "blub_tempt"));
    }

    void activate(ActiveDimension active) {
        this.dimension = active;
        active.registrations().registerListener("sift-creatures", this);
        active.registrations().ownCleanup("sift-creature-models", this::detachAll);
        for (Entity entity : active.world().getEntities()) {
            if (entity instanceof Wolf wolf && isBlub(wolf)) {
                prepare(wolf);
            }
        }
        active.registrations().scheduleRepeating("blub-ambience", this::ambience, 100L, 100L);
    }

    /** Dresses loaded Blubs that have no model yet, e.g. after BetterModel finished loading. */
    void redress() {
        ActiveDimension active = dimension;
        if (active == null) {
            return;
        }
        for (Entity entity : active.world().getEntities()) {
            if (entity instanceof Wolf wolf && isBlub(wolf)) {
                dress(wolf);
            }
        }
    }

    boolean isBlub(Entity entity) {
        return entity instanceof Wolf && entity.getPersistentDataContainer().has(blubKey, PersistentDataType.BYTE);
    }

    private boolean inSift(World world) {
        return dimension != null && dimension.world().getUID().equals(world.getUID());
    }

    // --- lifecycle ----------------------------------------------------------------------

    /**
     * The biome spawn table lists wolves for Blubs: natural spawns become Blubs,
     * while wolves spawned any other way (eggs, commands, breeding) stay wolves.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf) || !inSift(wolf.getWorld())) {
            return;
        }
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            convert(wolf);
        } else {
            wolf.getPersistentDataContainer().set(plainWolfKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private void convert(Wolf wolf) {
        wolf.getPersistentDataContainer().set(blubKey, PersistentDataType.BYTE, (byte) 1);
        setBase(wolf, Attribute.MAX_HEALTH, 20.0);
        setBase(wolf, Attribute.MOVEMENT_SPEED, 0.3);
        setBase(wolf, Attribute.ATTACK_DAMAGE, 3.0);
        setBase(wolf, Attribute.FOLLOW_RANGE, 24.0);
        setBase(wolf, Attribute.STEP_HEIGHT, 1.0);
        wolf.setHealth(20.0);
        wolf.setSilent(true);
        prepare(wolf);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!inSift(event.getWorld())) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof Wolf wolf)) {
                continue;
            }
            if (isBlub(wolf)) {
                prepare(wolf);
            } else if (!wolf.isTamed() && !wolf.getPersistentDataContainer().has(plainWolfKey, PersistentDataType.BYTE)) {
                // Chunk-generation spawns fire no spawn event; they are first seen here.
                convert(wolf);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            undress(entity.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        if (!isBlub(event.getEntity())) {
            return;
        }
        undress(event.getEntity().getUniqueId());
        // The mod's Blub loot table is empty.
        event.getDrops().clear();
        event.getEntity().getWorld().playSound(event.getEntity().getLocation(), "the_sift:entity.blub.death", 1.0F, 1.0F);
    }

    /** Goals are not saved with the entity, so they are adjusted every time a Blub loads. */
    private void prepare(Wolf wolf) {
        MobGoals goals = Bukkit.getMobGoals();
        for (GoalKey<?> wildWolf : List.of(VanillaGoal.NON_TAME_RANDOM, VanillaGoal.NEAREST_ATTACKABLE,
            VanillaGoal.WOLF_BEG, VanillaGoal.WOLF_AVOID_ENTITY, VanillaGoal.LEAP_AT)) {
            removeGoal(goals, wolf, wildWolf);
        }
        if (!goals.hasGoal(wolf, temptKey)) {
            goals.addGoal(wolf, 3, new SoulBlockTemptGoal(wolf));
        }
        dress(wolf);
    }

    @SuppressWarnings("unchecked")
    private static void removeGoal(MobGoals goals, Wolf wolf, GoalKey<?> key) {
        goals.removeGoal(wolf, (GoalKey<Wolf>) key);
    }

    private static void setBase(Wolf wolf, Attribute attribute, double value) {
        AttributeInstance instance = wolf.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    // --- interaction (BlubEntity#mobInteract) -------------------------------------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Wolf wolf) || !isBlub(wolf)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (held.getType() == Material.LEAD || held.getType() == Material.NAME_TAG) {
            return;
        }
        // Everything else is the Blub's own behaviour, not the wolf's (bones, dyes, armour).
        event.setCancelled(true);
        boolean soulBlock = ContentItemFactory.idOf(held).filter(SOUL_BLOCK::equals).isPresent();
        FoodProperties food = held.isEmpty() ? null : held.getData(DataComponentTypes.FOOD);
        double maxHealth = maxHealth(wolf);

        if (wolf.isTamed() && (soulBlock || food != null) && wolf.getHealth() < maxHealth) {
            consumeOne(player, held);
            double heal = food != null ? food.nutrition() : 4.0;
            wolf.setHealth(Math.min(maxHealth, wolf.getHealth() + heal));
            wolf.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, wolf.getLocation().add(0, 0.6, 0), 4, 0.3, 0.3, 0.3);
            return;
        }
        if (!wolf.isTamed() && soulBlock && wolf.getTarget() == null && !wolf.isAngry()) {
            consumeOne(player, held);
            if (ThreadLocalRandom.current().nextInt(3) == 0) {
                tame(wolf, player);
            } else {
                wolf.getWorld().spawnParticle(Particle.SMOKE, wolf.getLocation().add(0, 0.6, 0), 7, 0.3, 0.3, 0.3, 0.02);
            }
            return;
        }
        if (wolf.isTamed() && player.getUniqueId().equals(ownerId(wolf))) {
            wolf.setSitting(!wolf.isSitting());
            wolf.getPathfinder().stopPathfinding();
            wolf.setTarget(null);
            syncPose(wolf);
        }
    }

    /** Only Soul Blocks tame a Blub; vanilla wolf taming (bones) never applies. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (isBlub(event.getEntity()) && !event.getEntity().getPersistentDataContainer()
            .has(tamingKey(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    private void tame(Wolf wolf, Player player) {
        wolf.getPersistentDataContainer().set(tamingKey(), PersistentDataType.BYTE, (byte) 1);
        try {
            wolf.setOwner(player);
        } finally {
            wolf.getPersistentDataContainer().remove(tamingKey());
        }
        wolf.setRemoveWhenFarAway(false);
        wolf.getPathfinder().stopPathfinding();
        wolf.setTarget(null);
        wolf.setAngry(false);
        wolf.setSitting(false);
        wolf.getWorld().spawnParticle(Particle.HEART, wolf.getLocation().add(0, 0.7, 0), 7, 0.3, 0.3, 0.3);
        wolf.getWorld().playSound(wolf.getLocation(), "the_sift:entity.blub.idle_air", 0.95F, 1.08F);
        // The tamed Blub uses the mod's tamed texture.
        undress(wolf.getUniqueId());
        dress(wolf);
        JavaModelHandle handle = attached.get(wolf.getUniqueId());
        if (handle != null) {
            models.animateOnce(handle, "happy", () -> { });
        }
    }

    private NamespacedKey tamingKey() {
        return new NamespacedKey(plugin, "sift_blub_taming");
    }

    private static UUID ownerId(Wolf wolf) {
        return wolf.getOwnerUniqueId();
    }

    private static double maxHealth(Wolf wolf) {
        AttributeInstance instance = wolf.getAttribute(Attribute.MAX_HEALTH);
        return instance == null ? 20.0 : instance.getValue();
    }

    private static void consumeOne(Player player, ItemStack held) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract(1);
        }
    }

    // --- sounds and pose ----------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Wolf wolf) || !isBlub(wolf)) {
            return;
        }
        wolf.getWorld().playSound(wolf.getLocation(), "the_sift:entity.blub.hurt", 1.0F, 1.0F);
        // Taking damage makes a sitting wolf stand up; follow it with the model pose.
        dimension.registrations().scheduleDelayed("blub-pose", () -> {
            if (wolf.isValid()) {
                syncPose(wolf);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (isBlub(event.getDamager())) {
            event.getDamager().getWorld().playSound(event.getDamager().getLocation(), "the_sift:entity.blub.attack", 1.0F, 1.0F);
        }
    }

    private void syncPose(Wolf wolf) {
        JavaModelHandle handle = attached.get(wolf.getUniqueId());
        if (handle == null) {
            return;
        }
        if (wolf.isSitting()) {
            models.animate(handle, SITTING);
        } else {
            models.stopAnimation(handle, SITTING);
        }
    }

    private void dress(Wolf wolf) {
        if (attached.containsKey(wolf.getUniqueId())) {
            return;
        }
        try {
            attached.put(wolf.getUniqueId(), models.attach(wolf, wolf.isTamed() ? BLUB_TAMED_MODEL : BLUB_MODEL));
            syncPose(wolf);
        } catch (RuntimeException unavailable) {
            // Without the model the Blub stays a visible wolf rather than nothing;
            // the next load or redress() tries again.
            if (!warnedMissingModel) {
                warnedMissingModel = true;
                plugin.getLogger().log(Level.WARNING, "[TheSift] Blub model unavailable; Blubs render as wolves", unavailable);
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
    }

    /** Occasional calls near players, in or out of water, like the mod's ambient sounds. */
    private void ambience() {
        ActiveDimension active = dimension;
        if (active == null || active.world().getPlayers().isEmpty()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (UUID id : attached.keySet()) {
            Entity entity = active.world().getEntity(id);
            if (entity == null || random.nextFloat() > 0.25F || !nearPlayer(active, entity)) {
                continue;
            }
            String sound = entity.isInWater() ? "the_sift:entity.blub.idle_water" : "the_sift:entity.blub.idle_air";
            entity.getWorld().playSound(entity.getLocation(), sound, 0.8F, 0.9F + random.nextFloat() * 0.2F);
        }
    }

    private static boolean nearPlayer(ActiveDimension active, Entity entity) {
        for (Player player : active.world().getPlayers()) {
            if (player.getLocation().distanceSquared(entity.getLocation()) < 24 * 24) {
                return true;
            }
        }
        return false;
    }

    /** {@code TemptGoal(blub, 1.1, soul_block)}: walks toward a nearby player holding a Soul Block. */
    private final class SoulBlockTemptGoal implements Goal<Wolf> {

        private final Wolf wolf;
        private Player target;
        private int cooldown;

        SoulBlockTemptGoal(Wolf wolf) {
            this.wolf = wolf;
        }

        @Override
        public boolean shouldActivate() {
            if (cooldown > 0) {
                cooldown--;
                return false;
            }
            if (wolf.isSitting()) {
                return false;
            }
            target = temptingPlayer();
            return target != null;
        }

        @Override
        public boolean shouldStayActive() {
            return !wolf.isSitting() && target != null && target.isValid() && holdsSoulBlock(target)
                && target.getWorld().equals(wolf.getWorld())
                && target.getLocation().distanceSquared(wolf.getLocation()) <= TEMPT_RANGE * TEMPT_RANGE;
        }

        @Override
        public void tick() {
            wolf.lookAt(target);
            if (wolf.getLocation().distanceSquared(target.getLocation()) < 6.25) {
                wolf.getPathfinder().stopPathfinding();
            } else {
                wolf.getPathfinder().moveTo(target, 1.1);
            }
        }

        @Override
        public void stop() {
            target = null;
            cooldown = 100;
            wolf.getPathfinder().stopPathfinding();
        }

        private Player temptingPlayer() {
            Player closest = null;
            double best = TEMPT_RANGE * TEMPT_RANGE;
            for (Player player : wolf.getWorld().getPlayers()) {
                double distance = player.getLocation().distanceSquared(wolf.getLocation());
                if (distance <= best && player.getGameMode() != GameMode.SPECTATOR && holdsSoulBlock(player)) {
                    best = distance;
                    closest = player;
                }
            }
            return closest;
        }

        private boolean holdsSoulBlock(Player player) {
            return ContentItemFactory.idOf(player.getInventory().getItemInMainHand()).filter(SOUL_BLOCK::equals).isPresent()
                || ContentItemFactory.idOf(player.getInventory().getItemInOffHand()).filter(SOUL_BLOCK::equals).isPresent();
        }

        @Override
        public GoalKey<Wolf> getKey() {
            return temptKey;
        }

        @Override
        public EnumSet<GoalType> getTypes() {
            return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
        }
    }
}
