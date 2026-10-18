package dev.linqfy.bigCasares.modules.sift;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.sift.DarkSnifferBrain.Combatant;
import dev.linqfy.bigCasares.modules.sift.DarkSnifferBrain.Pose;
import dev.linqfy.bigCasares.modules.sift.SiftGeometry.Aabb;
import dev.linqfy.bigCasares.modules.sift.SiftGeometry.Vec;
import dev.linqfy.bigCasares.modules.sift.feature.WorldRegion;
import io.papermc.paper.event.entity.WardenAngerChangeEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Difficulty;
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
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sniffer;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.logging.Level;

/**
 * The Dark Sniffer, ported from the mod's {@code DarkSnifferEntity}, {@code SnifferInfectionMixin}
 * and {@code WardenDarkSnifferMixin}: a hostile sniffer of the sculk regions that hunts players and
 * ordinary sniffers, pulls its prey in with a sonic sniff, digs Sculkflower Seeds out of sculk, is
 * petted by calm Wardens, and turns the sniffers that dig sculk into more of its kind.
 *
 * <p><b>Why a sniffer under a model.</b> The mod's entity is a {@code Sniffer} subclass with only a
 * different texture, and a vanilla client cannot be given a new entity type, nor a second texture for the
 * sniffer: a resource pack replaces the texture of every sniffer. So the Dark Sniffer is a real, vanilla
 * {@code Sniffer} (marked in its persistent data) with the mod's hitbox, brain (digging, sniffing,
 * walking), sounds and loot hooks, and BetterModel hides it under a model built from the vanilla sniffer
 * geometry with the mod's {@code dark_sniffer.png} ({@code tools/generate_dark_sniffer_bbmodel.py}). A
 * silverfish or wolf body, as for the Sifter and the Blub, would have the wrong hitbox and none of the
 * sniffer's digging; a display entity alone would have no collision or AI. Wardens and ordinary sniffers
 * interact with the body as with any sniffer.</p>
 *
 * <p>Ported from the mod: the stats (40 health, 0.1 speed, 7 attack, 32 follow range, 1.25 step height, 0.75
 * attack knockback), the whole of {@code DarkSnifferEntity#tick} ({@link DarkSnifferBrain}: targets,
 * chase, bite every 24 ticks, sonic sniff with its wind-up, lead, pull, cooldown and range rules, wandering
 * around a saved home), no breeding, feeding or baby form, persistence, digging only sculk and the
 * {@code dark_sniffer_digging} loot (Sculkflower Seeds), the {@code dark_sniffer} death loot (sculk), the sniff
 * sound, Wardens that neither anger at it nor hunt it and pet it ({@link WardenPetting}), the sculk
 * infection and bloom of ordinary sniffers ({@link SnifferInfection}), their fleeing, and the
 * {@code food_chain} advancement.</p>
 *
 * <p>Differences:</p>
 * <ul>
 *   <li>Only in the Sift world. Dark Sniffers are made by its generation and by infection there; the
 *       datapack tag that lets sniffers dig sculk is global, but sniffers elsewhere are not infected.</li>
 *   <li>The brain ticks from a task each server tick, not from the entity's own tick: the mod's
 *       {@code canSniff} override (no sniffing or digging while it has a target) becomes forcing the
 *       idle pose every tick of the hunt. The sonic sniff pose is set through the API's {@code setState}.</li>
 *   <li>Not ported: the path costs through leaves, cocoa and cautious damage blocks and path-to-targets-
 *       below-the-surface (no API); the jump when blocked is a velocity push, used when a sniffer on the
 *       ground that has a path has stopped moving; the Warden's petting animation and the vibration and
 *       disturbance checks of its calm test (no API; its pose, anger and target are checked); the sound-wave
 *       particle (the Singer's vanilla particles stand in, one burst per step, not one moving particle); the
 *       mod's mob category (monster) for sounds and spawn caps; the client shaking of an infected sniffer
 *       is vanilla's frozen shake, and its freeze damage is cancelled.</li>
 *   <li>A sniffer that infection converts stays the same entity (no replacement): its health is kept, it
 *       becomes an adult. The dark sniffer digs only where the sculk is under its head (a dig elsewhere
 *       drops nothing) instead of never starting.</li>
 *   <li>Fleeing ordinary sniffers pick a new spot every 10 ticks, or when their path ends, instead of every
 *       tick, and a spot is accepted when it has solid ground and room to stand within the 8 blocks
 *       of the random height.</li>
 *   <li>Spawn egg not ported. The Dark Sniffer keeps the vanilla sniffer's sounds (the hostile category,
 *       the mod's, is used only for the sonic sniff).</li>
 * </ul>
 */
final class SiftDarkSniffers implements Listener {

    static final String MODEL = "the_sift_dark_sniffer";
    static final String SNIFF_SOUND = "the_sift:entity.dark_sniffer.sniff";
    static final String BLOOM_SOUND = "the_sift:block.dark_sniffer.bloom";
    static final double MAX_HEALTH = 40.0;
    static final double ATTACK_DAMAGE = 7.0;
    static final double ATTACK_KNOCKBACK = 0.75;
    static final double FOLLOW_RANGE = 32.0;
    static final double STEP_HEIGHT = 1.25;
    private static final byte MARKED = 1;
    private static final byte CONFIGURED = 2;
    private static final int FLEE_REFRESH_TICKS = 10;
    private static final int FLEE_ATTEMPTS = 10;
    private static final int SAVE_INFECTION_TICKS = 20;
    private static final Color WAVE_COLOR = Color.fromRGB(0x5FD7E8);
    private static final String CUSTOM_NAME_KEY = "entity.the_sift.dark_sniffer";

    private final JavaPlugin plugin;
    private final JavaModelGateway models;
    private final BiFunction<String, Integer, Optional<ItemStack>> items;
    private final SiftFixedLoot diggingLoot;
    private final SiftFixedLoot deathLoot;
    private final SiftCreatures creatures;
    private final NamespacedKey markerKey;
    private final NamespacedKey homeKey;
    private final NamespacedKey infectionKey;
    private final Map<UUID, DarkSniffer> darks = new HashMap<>();
    private final Map<UUID, Ordinary> ordinaries = new HashMap<>();
    private final Map<UUID, WardenState> wardens = new HashMap<>();
    private final Random random = new Random();
    private boolean warnedMissingModel;
    private ActiveDimension dimension;

    SiftDarkSniffers(JavaPlugin plugin, JavaModelGateway models, BiFunction<String, Integer, Optional<ItemStack>> items,
                     SiftData data, SiftCreatures creatures) {
        this.plugin = plugin;
        this.models = models;
        this.items = items;
        this.diggingLoot = SiftFixedLoot.parse(data.json("loot_table/gameplay/dark_sniffer_digging.json"));
        this.deathLoot = SiftFixedLoot.parse(data.json("loot_table/entities/dark_sniffer.json"));
        // The infected table (no pools) makes infected sniffers dig up nothing; see onDrop.
        SiftFixedLoot.parse(data.json("loot_table/gameplay/infected_sniffer_digging.json"));
        this.creatures = creatures;
        this.markerKey = java.util.Objects.requireNonNull(NamespacedKey.fromString(WorldRegion.DARK_SNIFFER_MARKER));
        this.homeKey = new NamespacedKey(plugin, "sift_dark_sniffer_home");
        this.infectionKey = new NamespacedKey(plugin, "sift_sniffer_infection");
    }

    void activate(ActiveDimension active) {
        this.dimension = active;
        active.registrations().registerListener("sift-dark-sniffers", this);
        active.registrations().ownCleanup("sift-dark-sniffer-models", this::detachAll);
        for (Entity entity : active.world().getEntities()) {
            track(entity);
        }
        active.registrations().scheduleRepeating("dark-sniffer-tick", this::tick, 1L, 1L);
    }

    /** Dresses loaded Dark Sniffers that have no model yet, e.g. after BetterModel finished loading. */
    void redress() {
        for (DarkSniffer dark : List.copyOf(darks.values())) {
            dress(dark);
        }
    }

    boolean isDarkSniffer(Entity entity) {
        return entity instanceof Sniffer && entity.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    private boolean inSift(World world) {
        return dimension != null && dimension.world().getUID().equals(world.getUID());
    }

    // --- tracking ------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(EntityAddToWorldEvent event) {
        if (inSift(event.getEntity().getWorld())) {
            track(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        UUID id = event.getEntity().getUniqueId();
        DarkSniffer dark = darks.remove(id);
        if (dark != null) {
            undress(dark);
        }
        Ordinary ordinary = ordinaries.remove(id);
        if (ordinary != null) {
            ordinary.save();
        }
        wardens.remove(id);
    }

    private void track(Entity entity) {
        if (entity instanceof Sniffer sniffer) {
            if (isDarkSniffer(sniffer)) {
                DarkSniffer dark = new DarkSniffer(sniffer);
                darks.put(sniffer.getUniqueId(), dark);
                prepare(dark);
            } else {
                Integer stored = sniffer.getPersistentDataContainer().get(infectionKey, PersistentDataType.INTEGER);
                Ordinary ordinary = new Ordinary(sniffer, new SnifferInfection(stored == null ? 0 : stored));
                ordinaries.put(sniffer.getUniqueId(), ordinary);
            }
        } else if (entity instanceof Warden warden) {
            wardens.put(warden.getUniqueId(), new WardenState(warden));
        }
    }

    // --- becoming and dressing a Dark Sniffer ---------------------------------------------

    private void prepare(DarkSniffer dark) {
        PersistentDataContainer data = dark.entity.getPersistentDataContainer();
        Byte mark = data.get(markerKey, PersistentDataType.BYTE);
        if (mark == null || mark == MARKED) {
            configure(dark.entity, true);
        }
        String home = data.get(homeKey, PersistentDataType.STRING);
        if (home != null) {
            parseHome(home).ifPresent(dark.brain::home);
        }
        // Not inside the event that adds the entity: BetterModel tracks it from the next tick.
        dimension.registrations().scheduleDelayed("dark-sniffer-dress", () -> {
            if (dark.entity.isValid() && darks.get(dark.entity.getUniqueId()) == dark) {
                dress(dark);
            }
        }, 1L);
    }

    /** The mod's attributes and persistence; a fresh sniffer starts at full health. */
    private void configure(Sniffer sniffer, boolean fullHealth) {
        sniffer.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, CONFIGURED);
        sniffer.setAdult();
        for (Attribute attribute : List.of(Attribute.ATTACK_DAMAGE, Attribute.ATTACK_KNOCKBACK)) {
            try {
                sniffer.registerAttribute(attribute);
            } catch (RuntimeException alreadyThere) {
                // Registering is idempotent in practice; a failure only means the value cannot be set below.
            }
        }
        setBase(sniffer, Attribute.MAX_HEALTH, MAX_HEALTH);
        setBase(sniffer, Attribute.MOVEMENT_SPEED, DarkSnifferBrain.NORMAL_SPEED);
        setBase(sniffer, Attribute.ATTACK_DAMAGE, ATTACK_DAMAGE);
        setBase(sniffer, Attribute.FOLLOW_RANGE, FOLLOW_RANGE);
        setBase(sniffer, Attribute.STEP_HEIGHT, STEP_HEIGHT);
        setBase(sniffer, Attribute.MOVEMENT_EFFICIENCY, 1.0);
        setBase(sniffer, Attribute.ATTACK_KNOCKBACK, ATTACK_KNOCKBACK);
        sniffer.setHealth(fullHealth ? MAX_HEALTH : Math.min(MAX_HEALTH, sniffer.getHealth()));
        sniffer.setRemoveWhenFarAway(false);
        sniffer.setPersistent(true);
        sniffer.customName(Component.translatable(CUSTOM_NAME_KEY, "Dark Sniffer"));
        sniffer.setCustomNameVisible(false);
    }

    private static void setBase(Sniffer sniffer, Attribute attribute, double value) {
        AttributeInstance instance = sniffer.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private void dress(DarkSniffer dark) {
        if (dark.handle != null) {
            return;
        }
        try {
            dark.handle = models.attach(dark.entity, MODEL);
            // The body keeps the vanilla sniffer's size, so the model keeps scale 1.
            models.scale(dark.handle, 1.0F);
            dark.lastState = null;
        } catch (RuntimeException unavailable) {
            // Without the model the Dark Sniffer stays a visible ordinary sniffer rather than nothing;
            // the next load or redress() tries again.
            if (!warnedMissingModel) {
                warnedMissingModel = true;
                plugin.getLogger().log(Level.WARNING, "[TheSift] Dark Sniffer model unavailable; Dark Sniffers render as sniffers",
                    unavailable);
            }
        }
    }

    private void undress(DarkSniffer dark) {
        if (dark.handle != null) {
            models.close(dark.handle);
            dark.handle = null;
        }
    }

    private void detachAll() {
        for (DarkSniffer dark : darks.values()) {
            undress(dark);
        }
        darks.clear();
        ordinaries.clear();
        wardens.clear();
    }

    /** Infection completes: the sniffer becomes a Dark Sniffer in place. */
    private void bloom(Ordinary ordinary) {
        Sniffer sniffer = ordinary.entity;
        sniffer.getWorld().playSound(sniffer.getLocation(), BLOOM_SOUND, SoundCategory.NEUTRAL, 1.0F, 0.72F);
        sniffer.lockFreezeTicks(false);
        sniffer.setFreezeTicks(0);
        sniffer.getPersistentDataContainer().remove(infectionKey);
        configure(sniffer, false);
        ordinaries.remove(sniffer.getUniqueId());
        DarkSniffer dark = new DarkSniffer(sniffer);
        darks.put(sniffer.getUniqueId(), dark);
        prepare(dark);
    }

    // --- the tick ------------------------------------------------------------------------------

    private void tick() {
        for (DarkSniffer dark : List.copyOf(darks.values())) {
            if (dark.entity.isValid() && dark.entity.isTicking()) {
                dark.brain.tick(dark);
                syncAnimation(dark);
                dark.previous = dark.entity.getLocation().toVector();
            }
        }
        for (Ordinary ordinary : List.copyOf(ordinaries.values())) {
            if (ordinary.entity.isValid() && ordinary.entity.isTicking()) {
                tickOrdinary(ordinary);
            }
        }
        for (WardenState warden : List.copyOf(wardens.values())) {
            if (warden.entity.isValid() && warden.entity.isTicking()) {
                warden.petting.tick(warden);
            }
        }
    }

    /** What the BetterModel animation of a sniffer state is called, or null when the state has none. */
    static String animationFor(Sniffer.State state) {
        return switch (state) {
            case DIGGING -> "dig";
            case SNIFFING, SCENTING -> "sniff";
            case SEARCHING -> "search";
            case RISING -> "rise";
            default -> null;
        };
    }

    private void syncAnimation(DarkSniffer dark) {
        if (dark.handle == null) {
            return;
        }
        Sniffer.State state = dark.entity.getState();
        if (state == dark.lastState) {
            return;
        }
        String previous = dark.lastState == null ? null : animationFor(dark.lastState);
        String next = animationFor(state);
        dark.lastState = state;
        if (previous != null && !previous.equals(next)) {
            models.stopAnimation(dark.handle, previous);
        }
        if (next == null) {
            return;
        }
        if (state == Sniffer.State.RISING) {
            models.animateOnce(dark.handle, next, () -> { });
        } else {
            models.animate(dark.handle, next);
        }
    }

    // --- ordinary sniffers: infection and fleeing -------------------------------------------

    private void tickOrdinary(Ordinary ordinary) {
        Sniffer sniffer = ordinary.entity;
        Sniffer.State state = sniffer.getState();
        if (state == Sniffer.State.DIGGING && ordinary.lastState != Sniffer.State.DIGGING) {
            ordinary.infection.diggingStarted(sculkUnderHead(sniffer));
        }
        ordinary.lastState = state;
        flee(ordinary);
        if (!ordinary.infection.infected()) {
            return;
        }
        if (!sniffer.isFreezeTickingLocked()) {
            // The client shakes an entity that is fully frozen; the lock keeps it so without any packets.
            sniffer.setFreezeTicks(sniffer.getMaxFreezeTicks());
            sniffer.lockFreezeTicks(true);
        }
        if (ordinary.infection.tick()) {
            bloom(ordinary);
        } else if (ordinary.infection.ticks() % SAVE_INFECTION_TICKS == 0) {
            ordinary.save();
        }
    }

    private boolean sculkUnderHead(Sniffer sniffer) {
        World world = sniffer.getWorld();
        Location at = sniffer.getLocation();
        Vec head = SnifferInfection.headPoint(new Vec(at.getX(), at.getY(), at.getZ()), at.getYaw());
        int x = (int) Math.floor(head.x());
        int z = (int) Math.floor(head.z());
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        return world.getBlockAt(x, (int) Math.floor(at.getY() + 0.2) - 1, z).getType() == Material.SCULK;
    }

    private void flee(Ordinary ordinary) {
        Sniffer sniffer = ordinary.entity;
        BoundingBox area = sniffer.getBoundingBox().expand(SnifferInfection.FLEE_RANGE);
        DarkSniffer threat = null;
        double nearest = Double.MAX_VALUE;
        for (DarkSniffer dark : darks.values()) {
            if (dark.entity.isValid() && dark.entity.getWorld().equals(sniffer.getWorld())
                && area.overlaps(dark.entity.getBoundingBox())) {
                double distance = dark.entity.getLocation().distanceSquared(sniffer.getLocation());
                if (distance < nearest) {
                    nearest = distance;
                    threat = dark;
                }
            }
        }
        if (threat == null) {
            ordinary.fleeCooldown = 0;
            return;
        }
        if (--ordinary.fleeCooldown > 0 && sniffer.getPathfinder().hasPath()) {
            return;
        }
        ordinary.fleeCooldown = FLEE_REFRESH_TICKS;
        Location from = sniffer.getLocation();
        Location danger = threat.entity.getLocation();
        for (int attempt = 0; attempt < FLEE_ATTEMPTS; attempt++) {
            Optional<Vec> offset = SnifferInfection.awayOffset(random, new Vec(from.getX(), from.getY(), from.getZ()),
                new Vec(danger.getX(), danger.getY(), danger.getZ()));
            if (offset.isEmpty()) {
                continue;
            }
            Location spot = standableNear(sniffer.getWorld(), from.getX() + offset.get().x(),
                (int) Math.floor(from.getY() + offset.get().y()), from.getZ() + offset.get().z());
            if (spot != null) {
                sniffer.getPathfinder().moveTo(spot, SnifferInfection.FLEE_PATH_SPEED);
                return;
            }
        }
    }

    /** A spot in a loaded chunk with solid ground and two free blocks, searched down from {@code y}. */
    private static Location standableNear(World world, double x, int y, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
            return null;
        }
        int top = Math.min(world.getMaxHeight() - 2, y);
        for (int level = top; level > Math.max(world.getMinHeight() + 1, y - 8); level--) {
            Block floor = world.getBlockAt(blockX, level - 1, blockZ);
            if (!floor.getCollisionShape().getBoundingBoxes().isEmpty() && !floor.isLiquid()
                && passable(world.getBlockAt(blockX, level, blockZ)) && passable(world.getBlockAt(blockX, level + 1, blockZ))) {
                return new Location(world, blockX + 0.5, level, blockZ + 0.5);
            }
        }
        return null;
    }

    private static boolean passable(Block block) {
        return block.getCollisionShape().getBoundingBoxes().isEmpty() && !block.isLiquid();
    }

    // --- events -------------------------------------------------------------------------------

    /** Dark Sniffers cannot be fed, bred or interacted with like sniffers ({@code isFood}, {@code canMate} are false). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (isDarkSniffer(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (isDarkSniffer(event.getMother()) || isDarkSniffer(event.getFather())) {
            event.setCancelled(true);
        }
    }

    /** {@code canTargetEntity}: a Warden never targets a Dark Sniffer. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWardenTarget(EntityTargetEvent event) {
        if (event.getEntity() instanceof Warden && event.getTarget() != null && isDarkSniffer(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWardenAnger(WardenAngerChangeEvent event) {
        if (isDarkSniffer(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    /** The infected sniffer's frozen shake must not hurt it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFreeze(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE) {
            Ordinary ordinary = ordinaries.get(event.getEntity().getUniqueId());
            if (ordinary != null && ordinary.infection.infected()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Digging loot ({@code dropSeed}): a Dark Sniffer digs the Sculkflower Seeds of its table, and only out of
     * sculk; an infected sniffer's table has no pools, so it digs up nothing.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(EntityDropItemEvent event) {
        if (!(event.getEntity() instanceof Sniffer sniffer)) {
            return;
        }
        Ordinary ordinary = ordinaries.get(sniffer.getUniqueId());
        if (ordinary != null && ordinary.infection.infected()) {
            event.setCancelled(true);
        } else if (isDarkSniffer(sniffer)) {
            Block dug = event.getItemDrop().getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
            List<SiftFixedLoot.Drop> drops = diggingLoot.roll(random, SiftFixedLoot.Context.none());
            Optional<ItemStack> stack = dug.getType() == Material.SCULK && !drops.isEmpty()
                ? items.apply(drops.get(0).item(), drops.get(0).count()) : Optional.empty();
            if (stack.isPresent()) {
                event.getItemDrop().setItemStack(stack.get());
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        if (!isDarkSniffer(event.getEntity())) {
            return;
        }
        Entity killer = event.getDamageSource().getCausingEntity();
        if (killer instanceof Wolf wolf && creatures.isBlub(wolf) && wolf.isTamed() && wolf.getOwner() instanceof Player owner) {
            SiftAdvancements.award(owner, SiftAdvancements.FOOD_CHAIN);
        }
        event.getDrops().clear();
        for (SiftFixedLoot.Drop drop : deathLoot.roll(random, SiftFixedLoot.Context.none())) {
            items.apply(drop.item(), drop.count()).ifPresent(event.getDrops()::add);
        }
    }

    // --- the Bukkit side of the brain -----------------------------------------------------------

    /** Home positions are saved as {@code x,y,z}. */
    static String formatHome(Vec home) {
        return home.x() + "," + home.y() + "," + home.z();
    }

    static Optional<Vec> parseHome(String text) {
        String[] parts = text.split(",");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Vec(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
        } catch (NumberFormatException invalid) {
            return Optional.empty();
        }
    }

    private static Vec vec(Location location) {
        return new Vec(location.getX(), location.getY(), location.getZ());
    }

    private static Vec vec(Vector vector) {
        return new Vec(vector.getX(), vector.getY(), vector.getZ());
    }

    private static Aabb aabb(BoundingBox box) {
        return new Aabb(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }

    /** A living entity seen by the brain. */
    private record Creature(LivingEntity entity) implements Combatant {

        @Override
        public UUID id() {
            return entity.getUniqueId();
        }

        @Override
        public boolean alive() {
            return entity.isValid() && !entity.isDead();
        }

        @Override
        public Vec position() {
            return vec(entity.getLocation());
        }

        @Override
        public Vec eyePosition() {
            return vec(entity.getEyeLocation());
        }

        @Override
        public Vec velocity() {
            return vec(entity.getVelocity());
        }

        @Override
        public Aabb box() {
            return aabb(entity.getBoundingBox());
        }
    }

    private static boolean survivalLike(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private final class DarkSniffer implements DarkSnifferBrain.Body, WardenPetting.Pettable {

        private final Sniffer entity;
        private final DarkSnifferBrain brain = new DarkSnifferBrain(random);
        private JavaModelHandle handle;
        private Sniffer.State lastState;
        private Vector previous;

        private DarkSniffer(Sniffer entity) {
            this.entity = entity;
            this.previous = entity.getLocation().toVector();
        }

        @Override
        public UUID id() {
            return entity.getUniqueId();
        }

        @Override
        public boolean alive() {
            return entity.isValid() && !entity.isDead();
        }

        @Override
        public Vec position() {
            return vec(entity.getLocation());
        }

        @Override
        public Vec eyePosition() {
            return vec(entity.getEyeLocation());
        }

        @Override
        public Vec velocity() {
            return vec(entity.getVelocity());
        }

        @Override
        public Aabb box() {
            return aabb(entity.getBoundingBox());
        }

        @Override
        public boolean peaceful() {
            return entity.getWorld().getDifficulty() == Difficulty.PEACEFUL;
        }

        @Override
        public Combatant target() {
            LivingEntity target = entity.getTarget();
            return target == null ? null : new Creature(target);
        }

        @Override
        public void target(Combatant target) {
            entity.setTarget(target == null ? null : ((Creature) target).entity());
        }

        @Override
        public Combatant nearestPlayer() {
            Player nearest = null;
            double best = DarkSnifferBrain.AGGRO_RANGE * DarkSnifferBrain.AGGRO_RANGE;
            for (Player player : entity.getWorld().getPlayers()) {
                if (!survivalLike(player) || player.isDead()) {
                    continue;
                }
                double distance = player.getLocation().distanceSquared(entity.getLocation());
                if (distance < best) {
                    best = distance;
                    nearest = player;
                }
            }
            return nearest == null ? null : new Creature(nearest);
        }

        @Override
        public Combatant nearestOrdinarySniffer() {
            Sniffer nearest = null;
            double best = DarkSnifferBrain.AGGRO_RANGE * DarkSnifferBrain.AGGRO_RANGE;
            for (Ordinary ordinary : ordinaries.values()) {
                Sniffer candidate = ordinary.entity;
                if (!candidate.isValid() || candidate.isDead() || !candidate.getWorld().equals(entity.getWorld())) {
                    continue;
                }
                double distance = candidate.getLocation().distanceSquared(entity.getLocation());
                if (distance < best) {
                    best = distance;
                    nearest = candidate;
                }
            }
            return nearest == null ? null : new Creature(nearest);
        }

        @Override
        public boolean canAttack(Combatant candidate) {
            if (peaceful() || !(candidate instanceof Creature creature)) {
                return false;
            }
            LivingEntity target = creature.entity();
            if (target instanceof Player player) {
                return survivalLike(player) && !player.isInvulnerable();
            }
            return target instanceof Sniffer && !isDarkSniffer(target) && !target.isInvulnerable();
        }

        @Override
        public boolean hasLineOfSight(Combatant candidate) {
            return entity.hasLineOfSight(((Creature) candidate).entity());
        }

        @Override
        public boolean navigationDone() {
            return !entity.getPathfinder().hasPath();
        }

        @Override
        public void moveTo(Vec destination, double pathSpeed) {
            entity.getPathfinder().moveTo(new Location(entity.getWorld(), destination.x(), destination.y(), destination.z()), pathSpeed);
        }

        @Override
        public void moveTo(Combatant target, double pathSpeed) {
            entity.getPathfinder().moveTo(((Creature) target).entity(), pathSpeed);
        }

        @Override
        public void stopNavigation() {
            entity.getPathfinder().stopPathfinding();
        }

        @Override
        public void stopMoving() {
            entity.setVelocity(new Vector());
        }

        @Override
        public void lookAt(Combatant target, float horizontalSpeed, float verticalSpeed) {
            LivingEntity living = target instanceof Creature creature ? creature.entity() : null;
            if (living != null) {
                entity.lookAt(living, horizontalSpeed, verticalSpeed);
            } else if (target != null) {
                Vec at = target.eyePosition();
                entity.lookAt(at.x(), at.y(), at.z(), horizontalSpeed, verticalSpeed);
            }
        }

        @Override
        public void movementSpeed(double speed) {
            setBase(entity, Attribute.MOVEMENT_SPEED, speed);
        }

        @Override
        public void aggressive(boolean aggressive) {
            entity.setAggressive(aggressive);
        }

        @Override
        public boolean blockedOnGround() {
            Vector now = entity.getLocation().toVector();
            return entity.isOnGround() && entity.getPathfinder().hasPath() && now.distanceSquared(previous) < 0.0025;
        }

        @Override
        public void jump() {
            AttributeInstance strength = entity.getAttribute(Attribute.JUMP_STRENGTH);
            Vector velocity = entity.getVelocity();
            entity.setVelocity(velocity.setY(Math.max(velocity.getY(), strength == null ? 0.42 : strength.getValue())));
        }

        @Override
        public boolean attack(Combatant target) {
            LivingEntity living = ((Creature) target).entity();
            AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
            DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK)
                .withCausingEntity(entity).withDirectEntity(entity).build();
            double before = living.getHealth();
            living.damage(damage == null ? ATTACK_DAMAGE : damage.getValue(), source);
            boolean hurt = living.getHealth() < before || living.isDead();
            AttributeInstance knockback = entity.getAttribute(Attribute.ATTACK_KNOCKBACK);
            double strength = (knockback == null ? ATTACK_KNOCKBACK : knockback.getValue()) * 0.5;
            if (hurt && strength > 0.0) {
                double yaw = Math.toRadians(entity.getLocation().getYaw());
                living.knockback(strength, Math.sin(yaw) * -1.0, Math.cos(yaw));
                entity.setVelocity(entity.getVelocity().multiply(new Vector(0.6, 1.0, 0.6)));
            }
            return hurt;
        }

        @Override
        public void pose(Pose pose) {
            Sniffer.State wanted = pose == Pose.SNIFFING ? Sniffer.State.SNIFFING : Sniffer.State.IDLING;
            if (entity.getState() != wanted) {
                entity.setState(wanted);
            }
        }

        @Override
        public void sonicLaunched(Vec start, Vec direction, int flightTicks) {
            World world = entity.getWorld();
            world.playSound(entity.getLocation(), SNIFF_SOUND, SoundCategory.HOSTILE, 2.5F, 0.78F);
            sonicMoved(start);
        }

        @Override
        public void sonicMoved(Vec head) {
            World world = entity.getWorld();
            Location at = new Location(world, head.x(), head.y(), head.z());
            world.spawnParticle(Particle.SCULK_CHARGE_POP, at, 2, 0.05, 0.05, 0.05, 0.0);
            world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(WAVE_COLOR, 1.6F));
        }

        @Override
        public List<Combatant> livingIn(Aabb area) {
            BoundingBox box = new BoundingBox(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ());
            List<Combatant> found = new ArrayList<>();
            for (Entity other : entity.getWorld().getNearbyEntities(box)) {
                if (other instanceof LivingEntity living && !living.equals(entity)) {
                    found.add(new Creature(living));
                }
            }
            return found;
        }

        @Override
        public void pull(Combatant victim, Vec push) {
            LivingEntity living = ((Creature) victim).entity();
            living.setVelocity(living.getVelocity().add(new Vector(push.x(), push.y(), push.z())));
        }

        @Override
        public void homeSet(Vec home) {
            entity.getPersistentDataContainer().set(homeKey, PersistentDataType.STRING, formatHome(home));
        }

        @Override
        public boolean availableForPetting() {
            return brain.availableForWardenPetting(this, alive());
        }

        @Override
        public void beginPetting(Combatant warden, int ticks) {
            brain.beginWardenPetting(this, warden, ticks);
        }

        @Override
        public void endPetting(Combatant warden) {
            brain.endWardenPetting(warden);
        }
    }

    /** An ordinary sniffer: its infection and its flight. */
    private final class Ordinary {

        private final Sniffer entity;
        private final SnifferInfection infection;
        private Sniffer.State lastState;
        private int fleeCooldown;

        private Ordinary(Sniffer entity, SnifferInfection infection) {
            this.entity = entity;
            this.infection = infection;
        }

        private void save() {
            if (infection.infected()) {
                entity.getPersistentDataContainer().set(infectionKey, PersistentDataType.INTEGER, infection.ticks());
            }
        }
    }

    private final class WardenState implements WardenPetting.Keeper {

        private final Warden entity;
        private final WardenPetting petting = new WardenPetting(random);

        private WardenState(Warden entity) {
            this.entity = entity;
        }

        @Override
        public UUID id() {
            return entity.getUniqueId();
        }

        @Override
        public boolean alive() {
            return entity.isValid() && !entity.isDead();
        }

        @Override
        public Vec position() {
            return vec(entity.getLocation());
        }

        @Override
        public Vec eyePosition() {
            return vec(entity.getEyeLocation());
        }

        @Override
        public Vec velocity() {
            return vec(entity.getVelocity());
        }

        @Override
        public Aabb box() {
            return aabb(entity.getBoundingBox());
        }

        /** {@code isCompletelyCalm}, as far as the API shows it (see the class documentation). */
        @Override
        public boolean calm() {
            return alive() && entity.getPose() == org.bukkit.entity.Pose.STANDING
                && entity.getAngerLevel() == Warden.AngerLevel.CALM && entity.getTarget() == null
                && entity.getEntityAngryAt() == null;
        }

        @Override
        public List<WardenPetting.Pettable> availableSniffersNearby() {
            BoundingBox area = entity.getBoundingBox().expand(WardenPetting.SEARCH_RANGE);
            List<WardenPetting.Pettable> found = new ArrayList<>();
            for (DarkSniffer dark : darks.values()) {
                if (dark.entity.isValid() && dark.entity.getWorld().equals(entity.getWorld())
                    && area.overlaps(dark.entity.getBoundingBox()) && dark.availableForPetting()) {
                    found.add(dark);
                }
            }
            return found;
        }

        @Override
        public void lookAt(Combatant target, float speed) {
            Vec at = target.eyePosition();
            entity.lookAt(at.x(), at.y(), at.z(), speed, speed);
        }

        @Override
        public void moveTo(Combatant target, double speed) {
            entity.getPathfinder().moveTo(darks.get(target.id()).entity, speed);
        }

        @Override
        public void stopNavigation() {
            entity.getPathfinder().stopPathfinding();
        }

        @Override
        public void heart(WardenPetting.Pettable sniffer) {
            Sniffer body = darks.get(sniffer.id()).entity;
            Location at = body.getLocation();
            body.getWorld().spawnParticle(Particle.HEART, at.getX(), at.getY() + body.getHeight() + 0.35, at.getZ(), 1,
                0.18, 0.08, 0.18, 0.0);
        }
    }
}
