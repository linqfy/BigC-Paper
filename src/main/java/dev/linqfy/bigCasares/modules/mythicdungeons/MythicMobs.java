package dev.linqfy.bigCasares.modules.mythicdungeons;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Los mobs miticos en el servidor: los crea con su marca y atributos, les pone el modelo de BetterModel (si existe:
 * sin modelo quedan como el mob vanilla de base y se avisa una sola vez por clave), corre sus conductas y resuelve lo
 * que sueltan al morir. Los mobs persisten en el mundo con sus marcas: al volver a cargarse (reinicio, chunk) se les
 * vuelve a poner el modelo. Todo en el hilo del servidor.
 */
public final class MythicMobs implements Listener {

    /** Cada cuantos ticks corren las conductas. */
    public static final long TICK_PERIOD = 5L;
    private static final double TARGET_RADIUS = 16.0;

    private final MythicKeys keys;
    private final MythicMobCatalog catalog;
    private final JavaModelGateway models;
    private final MythicScheduler scheduler;
    private final Logger logger;
    private final FragmentDropRoll fragments;
    private final Function<String, Optional<ItemStack>> catalogItems;
    private final Predicate<UUID> bossActive;
    private final MythicMobBehaviours behaviours;
    private final Map<UUID, Tracked> tracked = new LinkedHashMap<>();
    private final Map<UUID, JavaModelHandle> handles = new HashMap<>();
    private final Set<String> missingModels = new HashSet<>();
    private final Map<String, PotionEffectType> effects = new HashMap<>();
    private long runs;
    private boolean closed;

    /** Lo que se sabe de un mob vivo y cargado. */
    static final class Tracked {
        final LivingEntity entity;
        final MythicMobType type;
        final List<LivingEntity> summons = new ArrayList<>();
        long nextAbilityRun;
        /** Para los voladores dirigidos, que pegan por contacto (su IA vanilla esta apagada). */
        long nextContactRun;

        Tracked(LivingEntity entity, MythicMobType type) {
            this.entity = entity;
            this.type = type;
        }
    }

    /**
     * @param catalogItems crea un item del catalogo por id (vacio si el catalogo no esta activo)
     * @param bossActive   si una instancia de jefe sigue viva (para limpiar minions huerfanos tras un reinicio)
     */
    public MythicMobs(MythicKeys keys, MythicMobCatalog catalog, JavaModelGateway models, MythicScheduler scheduler,
                      Logger logger, FragmentDropRoll fragments, Function<String, Optional<ItemStack>> catalogItems,
                      Predicate<UUID> bossActive) {
        this.keys = Objects.requireNonNull(keys, "keys");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.models = Objects.requireNonNull(models, "models");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.fragments = Objects.requireNonNull(fragments, "fragments");
        this.catalogItems = Objects.requireNonNull(catalogItems, "catalogItems");
        this.bossActive = Objects.requireNonNull(bossActive, "bossActive");
        this.behaviours = new MythicMobBehaviours(this);
    }

    public MythicMobCatalog catalog() {
        return catalog;
    }

    public MythicKeys keys() {
        return keys;
    }

    // ------------------------------------------------------------------ spawn API

    /**
     * Crea un mob mitico.
     *
     * @param dungeonSpawn {@code true} solo para los que nacen en la mazmorra (spawners, spawns naturales o un admin
     *                     que lo pide): son los unicos que pueden soltar fragmentos y desaparecen lejos de jugadores
     * @return el mob, o vacio si el mundo no lo dejo nacer
     */
    public Optional<LivingEntity> spawn(MythicMobType type, Location location, boolean dungeonSpawn) {
        return spawn(type, location, dungeonSpawn, ignored -> {
        });
    }

    /**
     * Igual que {@link #spawn(MythicMobType, Location, boolean)} con un paso extra adentro del consumer de spawn (antes
     * del {@code CreatureSpawnEvent}), por ejemplo para marcar al minion de un jefe.
     */
    public Optional<LivingEntity> spawn(MythicMobType type, Location location, boolean dungeonSpawn,
                                        Consumer<LivingEntity> extraSetup) {
        Objects.requireNonNull(type, "type");
        World world = location == null ? null : location.getWorld();
        if (closed || world == null) {
            return Optional.empty();
        }
        Class<? extends Entity> entityClass = type.baseEntity().getEntityClass();
        if (entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass)) {
            logger.warning("[Mazmorras] La base de " + type.id() + " no es un mob: " + type.baseEntity());
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        Class<LivingEntity> livingClass = (Class<LivingEntity>) entityClass;
        LivingEntity entity = world.spawn(location, livingClass, CreatureSpawnEvent.SpawnReason.CUSTOM, created -> {
            configure(created, type, dungeonSpawn);
            extraSetup.accept(created);
        });
        if (entity == null || !entity.isValid()) {
            return Optional.empty();
        }
        adopt(entity, type);
        return Optional.of(entity);
    }

    public Optional<MythicMobType> typeOf(Entity entity) {
        return keys.mobId(entity).flatMap(catalog::find);
    }

    /** Saca los mobs y entidades del modulo cerca de un lugar; devuelve cuantas saco. */
    public int removeNearby(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return 0;
        }
        int removed = 0;
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player) && keys.isOwnEntity(entity)) {
                forget(entity.getUniqueId());
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Vivos y cargados (para los topes de spawn). */
    public int countNearby(Location center, double radius, boolean dungeonOnly) {
        double squared = radius * radius;
        int count = 0;
        for (Tracked mob : tracked.values()) {
            LivingEntity entity = mob.entity;
            if (entity.isValid() && entity.getWorld().equals(center.getWorld())
                && entity.getLocation().distanceSquared(center) <= squared
                && (!dungeonOnly || keys.isDungeonSpawn(entity))) {
                count++;
            }
        }
        return count;
    }

    /** Toma los mobs marcados que ya estan cargados (al prender el modulo o tras /bc reload). */
    public void adoptLoaded(Iterable<World> worlds) {
        for (World world : worlds) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (keys.bossOwner(entity).filter(owner -> !bossActive.test(owner)).isPresent()) {
                    entity.remove();
                    continue;
                }
                typeOf(entity).ifPresent(type -> adopt(entity, type));
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /** Cuerpo de la tarea repetida del modulo (cada {@link #TICK_PERIOD} ticks). */
    public void tick() {
        if (closed) {
            return;
        }
        runs++;
        Iterator<Map.Entry<UUID, Tracked>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            Tracked mob = iterator.next().getValue();
            if (!mob.entity.isValid() || mob.entity.isDead()) {
                iterator.remove();
                continue;
            }
            try {
                behaviours.tick(mob, runs);
            } catch (RuntimeException failure) {
                logger.warning("[Mazmorras] Fallo la conducta de " + mob.type.id() + ": " + failure);
            }
        }
    }

    /** Idempotente: cierra todos los modelos y olvida los mobs (las entidades quedan en el mundo). */
    public void shutdown() {
        closed = true;
        for (JavaModelHandle handle : List.copyOf(handles.values())) {
            closeQuietly(handle);
        }
        handles.clear();
        tracked.clear();
    }

    // ------------------------------------------------------------------ listeners

    @EventHandler
    public void onAddToWorld(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        Optional<UUID> owner = keys.bossOwner(entity);
        if (owner.isPresent()) {
            UUID bossId = owner.get();
            // los jefes no se recuperan tras un reinicio: sus minions y decorados quedan huerfanos
            scheduler.next("orphan-" + entity.getUniqueId(), () -> {
                if (entity.isValid() && !bossActive.test(bossId)) {
                    forget(entity.getUniqueId());
                    entity.remove();
                }
            });
        }
        if (!(entity instanceof LivingEntity living) || tracked.containsKey(entity.getUniqueId())) {
            return;
        }
        typeOf(living).ifPresent(type -> scheduler.next("adopt-" + entity.getUniqueId(), () -> {
            if (living.isValid() && !living.isDead()) {
                adopt(living, type);
            }
        }));
    }

    @EventHandler
    public void onRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        forget(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Optional<MythicMobType> type = typeOf(entity);
        if (type.isEmpty()) {
            if (keys.bossOwner(entity).isPresent()) {
                // apoyo vanilla de un jefe (ojos, lobos, nubes...): no deja botin ni experiencia
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
            return;
        }
        animateOnce(entity, "death");
        event.getDrops().clear();
        if (keys.bossOwner(entity).isPresent()) {
            event.setDroppedExp(0);
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (MythicDrop drop : type.get().drops()) {
            int amount = drop.roll(random.nextDouble(), random.nextDouble());
            if (amount > 0) {
                event.getDrops().add(new ItemStack(drop.material(), amount));
            }
        }
        event.setDroppedExp(type.get().xp());
        Player killer = entity.getKiller();
        int looting = killer == null ? 0
            : killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        fragments.roll(type.get(), keys.isDungeonSpawn(entity), killer != null, looting)
            .flatMap(catalogItems)
            .ifPresent(event.getDrops()::add);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        LivingEntity attacker = attackerOf(event.getDamager());
        if (attacker == null) {
            return;
        }
        Optional<MythicMobType> type = typeOf(attacker);
        if (type.isEmpty()) {
            return;
        }
        if (event.getDamager() == attacker) {
            animateOnce(attacker, "attack");
        }
        if (event.getEntity() instanceof LivingEntity victim && !keys.isOwnEntity(victim)) {
            applyEffects(victim, type.get().onHit());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        // los mobs nunca prenden fuego la mazmorra (el Dragon del tesoro es un blaze por debajo)
        if (event.getEntity() instanceof Explosive explosive
            && event.getEntity().getShooter() instanceof LivingEntity shooter && typeOf(shooter).isPresent()) {
            explosive.setIsIncendiary(false);
            explosive.setYield(0.0F);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (!(event instanceof EntityCombustByEntityEvent) && !(event instanceof EntityCombustByBlockEvent)
            && keys.mobId(event.getEntity()).isPresent()) {
            event.setCancelled(true); // sin quemarse al sol
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (keys.isOwnEntity(event.getEntity())) {
            event.setCancelled(true); // ni ahogados, ni zombificados, ni curados
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (keys.isOwnEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        LivingEntity target = event.getTarget();
        if (target != null && keys.isOwnEntity(event.getEntity()) && keys.isOwnEntity(target)) {
            event.setCancelled(true); // los de la mazmorra no se pelean entre ellos
        }
    }

    // ------------------------------------------------------------------ helpers used by behaviours

    MythicScheduler schedulerFor() {
        return scheduler;
    }

    Optional<LivingEntity> spawnSummon(MythicMobType type, Location location) {
        return fitNear(type, location, 2, 1).flatMap(spot -> spawn(type, spot, false));
    }

    /**
     * El lugar libre mas cercano donde entra la hitbox del mob ({@link MobPlacement}), en una caja de {@code range}
     * bloques horizontales y {@code vertical} verticales; vacio si no entra en ningun lado.
     */
    static Optional<Location> fitNear(MythicMobType type, Location around, int range, int vertical) {
        World world = around.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        MobPlacement.Spot center = new MobPlacement.Spot(around.getBlockX(), around.getBlockY(), around.getBlockZ());
        return MobPlacement.nearestFit(new WorldBlockGrid(world), center, center, range, vertical,
                type.hitboxWidth(), type.hitboxHeight())
            .map(spot -> new Location(world, spot.x() + 0.5, spot.y(), spot.z() + 0.5, around.getYaw(),
                around.getPitch()));
    }

    void applyEffects(LivingEntity target, List<MythicEffect> list) {
        for (MythicEffect effect : list) {
            PotionEffectType potion = effects.computeIfAbsent(effect.effectKey(),
                key -> Registry.EFFECT.get(NamespacedKey.minecraft(key)));
            if (potion != null) {
                target.addPotionEffect(new PotionEffect(potion, effect.durationTicks(), effect.amplifier()));
            }
        }
    }

    void animateOnce(LivingEntity entity, String animation) {
        JavaModelHandle handle = handles.get(entity.getUniqueId());
        if (handle == null) {
            return;
        }
        try {
            models.animateOnce(handle, animation, () -> {
            });
        } catch (RuntimeException ignored) {
            // cosmetic only
        }
    }

    static boolean isVulnerable(Player player) {
        return player.isValid() && !player.isDead()
            && (player.getGameMode() == org.bukkit.GameMode.SURVIVAL
            || player.getGameMode() == org.bukkit.GameMode.ADVENTURE);
    }

    static Player nearestVulnerablePlayer(Entity entity, double radius) {
        Player nearest = null;
        double best = radius * radius;
        for (Player player : entity.getWorld().getPlayers()) {
            if (!isVulnerable(player)) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(entity.getLocation());
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    static double targetRadius() {
        return TARGET_RADIUS;
    }

    // ------------------------------------------------------------------ internals

    private void configure(LivingEntity entity, MythicMobType type, boolean dungeonSpawn) {
        keys.markSystem(entity);
        entity.getPersistentDataContainer().set(keys.mob, PersistentDataType.STRING, type.id());
        entity.getPersistentDataContainer().set(keys.dungeonSpawn, PersistentDataType.BYTE,
            (byte) (dungeonSpawn ? 1 : 0));
        boolean flyer = type.baseEntity() == EntityType.VEX || type.steeredFlyer();
        setBase(entity, Attribute.MAX_HEALTH, type.health());
        setBase(entity, Attribute.ATTACK_DAMAGE, type.damage());
        if (!flyer) {
            setBase(entity, Attribute.MOVEMENT_SPEED, type.speed());
        }
        setBase(entity, Attribute.SCALE, type.scale());
        setBase(entity, Attribute.ARMOR, type.armor());
        setBase(entity, Attribute.KNOCKBACK_RESISTANCE, type.knockbackResistance());
        setBase(entity, Attribute.FOLLOW_RANGE, 32.0);
        setBase(entity, Attribute.SPAWN_REINFORCEMENTS, 0.0);
        entity.setHealth(type.health());
        // el nombre es el que leen los mensajes de muerte vanilla ("... fue asesinado por Minotauro")
        entity.customName(Component.text(type.displayName()));
        entity.setCustomNameVisible(false);
        entity.setCanPickupItems(false);
        if (entity instanceof Mob mob) {
            mob.setRemoveWhenFarAway(dungeonSpawn);
        }
        if (type.floating()) {
            entity.setGravity(false);
        }
        if (type.steeredFlyer() && entity instanceof Mob mob) {
            // sin IA vanilla (el phantom daria vueltas en el cielo, el murcielago revolotea al azar): lo dirige
            // MythicMobBehaviours con velocidad, asi que choca con las paredes como cualquier entidad
            mob.setAware(false);
        }
        if (entity instanceof Bat bat) {
            bat.setAwake(true);
        }
        configureEquipment(entity, type);
        if (entity instanceof Zombie zombie) {
            zombie.setAdult();
            zombie.setShouldBurnInDay(false);
            zombie.setCanBreakDoors(false);
        }
        if (entity instanceof AbstractSkeleton skeleton) {
            skeleton.setShouldBurnInDay(false);
        }
        if (entity instanceof Phantom phantom) {
            phantom.setShouldBurnInDay(false);
        }
        if (entity instanceof PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true);
        }
        if (entity instanceof Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }
        if (entity instanceof Wolf wolf) {
            wolf.setAngry(true);
        }
        if (entity instanceof Vex vex) {
            vex.setLimitedLifetime(false);
        }
    }

    private static void configureEquipment(LivingEntity entity, MythicMobType type) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET}) {
            equipment.setItem(slot, null);
        }
        // las bases que pelean con su arma la conservan (y Bedrock sin modelo las ve armadas); el resto va a mano
        boolean keepsWeapon = switch (type.baseEntity()) {
            case SKELETON, VINDICATOR, WITHER_SKELETON, PIGLIN_BRUTE -> true;
            default -> false;
        };
        if (!keepsWeapon) {
            equipment.setItemInMainHand(null);
        }
        equipment.setItemInOffHand(null);
        equipment.setHelmetDropChance(0.0F);
        equipment.setChestplateDropChance(0.0F);
        equipment.setLeggingsDropChance(0.0F);
        equipment.setBootsDropChance(0.0F);
        equipment.setItemInMainHandDropChance(0.0F);
        equipment.setItemInOffHandDropChance(0.0F);
    }

    private void adopt(LivingEntity entity, MythicMobType type) {
        if (closed) {
            return;
        }
        tracked.computeIfAbsent(entity.getUniqueId(), ignored -> new Tracked(entity, type));
        if (handles.containsKey(entity.getUniqueId())) {
            return;
        }
        try {
            handles.put(entity.getUniqueId(), models.attach(entity, type.modelKey()));
        } catch (RuntimeException | LinkageError missing) {
            if (missingModels.add(type.modelKey())) {
                logger.warning("[Mazmorras] Sin modelo " + type.modelKey() + " (" + missing.getMessage()
                    + "): " + type.displayName() + " se ve como " + type.baseEntity() + " hasta que se agregue.");
            }
        }
    }

    private void forget(UUID entityId) {
        tracked.remove(entityId);
        JavaModelHandle handle = handles.remove(entityId);
        if (handle != null) {
            closeQuietly(handle);
        }
    }

    private void closeQuietly(JavaModelHandle handle) {
        try {
            models.close(handle);
        } catch (RuntimeException failure) {
            logger.warning("[Mazmorras] No se pudo cerrar el modelo " + handle.modelKey() + ": " + failure);
        }
    }

    private static LivingEntity attackerOf(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return null;
    }

    private static void setBase(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
