package dev.linqfy.bigCasares.modules.alexscaves.core.mob;

import dev.linqfy.bigCasares.modules.alexscaves.core.ModTasks;
import dev.linqfy.bigCasares.items.CustomItemRegistry;
import dev.linqfy.bigCasares.modules.alexscaves.config.AlexsCavesSettings;
import dev.linqfy.bigCasares.modules.alexscaves.core.ability.CooldownLedger;
import dev.linqfy.bigCasares.modules.alexscaves.core.damage.ModDamageTypes;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectService;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.platform.ClientEntityPresentationRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Spawns, tracks and cleans up module mobs. Identity lives in the PDC so mobs
 * that unload and load again (or survive a restart) are re-bound to their
 * behaviour and model by {@link #bind(LivingEntity)}.
 */
public final class ModMobService implements ModMobContext {
    private ModTasks tasks;

    @Override
    public ModTasks tasks() {
        if (tasks == null) {
            tasks = new ModTasks(plugin);
        }
        return tasks;
    }

    private static final int TICK_PERIOD = 2;
    /** One idle sound every ~8 seconds on average (checked every TICK_PERIOD ticks). */
    private static final int IDLE_SOUND_CHANCE = 80;

    private final Plugin plugin;
    private final ModMobCatalog catalog;
    private final ModEffectService effects;
    private final ModDamageTypes damageTypes;
    private final JavaModelGateway models;
    private final CustomItemRegistry items;
    private final CooldownLedger cooldowns;
    private final NamespacedKey customEntityKey;
    private final Map<UUID, Instance> tracked = new HashMap<>();
    /** Models of dead mobs kept while their death animation plays, until the body is removed. */
    private final Map<UUID, JavaModelHandle> dying = new HashMap<>();
    private volatile AlexsCavesSettings settings = AlexsCavesSettings.defaults();
    private long tick;

    public ModMobService(Plugin plugin, ModMobCatalog catalog, ModEffectService effects, ModDamageTypes damageTypes,
                         JavaModelGateway models, CustomItemRegistry items) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.effects = effects;
        this.damageTypes = damageTypes;
        this.models = models;
        this.items = items;
        this.cooldowns = new CooldownLedger(() -> tick);
        this.customEntityKey = new NamespacedKey(plugin, ModMobIdentity.CUSTOM_ENTITY_KEY);
    }

    /** Module settings for content listeners; defaults until the runtime calls {@link #configure}. */
    public AlexsCavesSettings settings() {
        return settings;
    }

    public void configure(AlexsCavesSettings settings) {
        this.settings = settings;
    }

    /** Marker on a mob the module's spawner placed (the mod's {@code MobSpawnType.NATURAL}), for spawn-only extras. */
    public static final String NATURAL_KEY = "ac_natural_spawn";

    /** A natural spawn: like {@link #spawn} but marked, so mobs with natural-only starts (posing, reinforcements) do them. */
    public Optional<LivingEntity> spawnNatural(String mobId, Location location) {
        return spawn(mobId, location, true);
    }

    /** Whether the mob was placed by the natural spawner (the marker stays; read it right after spawn). */
    public static boolean isNatural(Entity entity) {
        return entity.getPersistentDataContainer().has(new NamespacedKey("bigcasares", NATURAL_KEY), PersistentDataType.BYTE);
    }

    public Optional<LivingEntity> spawn(String mobId, Location location) {
        return spawn(mobId, location, false);
    }

    private Optional<LivingEntity> spawn(String mobId, Location location, boolean natural) {
        ModMobType type = catalog.find(mobId).orElse(null);
        if (type == null || location.getWorld() == null) {
            return Optional.empty();
        }
        EntityType base = EntityType.valueOf(type.baseEntity().toUpperCase(Locale.ROOT));
        Class<? extends Entity> entityClass = base.getEntityClass();
        if (entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass)) {
            throw new IllegalStateException("Base inválida para " + mobId + ": " + base);
        }
        @SuppressWarnings("unchecked")
        Class<LivingEntity> livingClass = (Class<LivingEntity>) entityClass;
        // Tag inside the spawn consumer so CreatureSpawnEvent listeners (Mob Scaling) already see the identity.
        Entity spawned = location.getWorld().spawn(location, livingClass, CreatureSpawnEvent.SpawnReason.CUSTOM,
            false, entity -> {
                configure(entity, type);
                if (natural) {
                    entity.getPersistentDataContainer().set(new NamespacedKey("bigcasares", NATURAL_KEY),
                        PersistentDataType.BYTE, (byte) 1);
                }
            });
        if (!(spawned instanceof LivingEntity living) || !living.isValid()) {
            return Optional.empty();
        }
        bind(living);
        return Optional.of(living);
    }

    private void configure(LivingEntity entity, ModMobType type) {
        ModMobIdentity.tag(new PdcTagStore(entity.getPersistentDataContainer(), customEntityKey), type);
        for (Map.Entry<String, Double> attribute : type.attributes().entrySet()) {
            Attribute key = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attribute.getKey()));
            AttributeInstance instance = key == null ? null : entity.getAttribute(key);
            if (instance != null) {
                instance.setBaseValue(attribute.getValue());
            }
        }
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            entity.setHealth(health.getValue());
        }
        entity.setCustomName(null);
        entity.setCustomNameVisible(false);
        if (entity instanceof Mob mob) {
            mob.setPersistent(type.persistent());
            mob.setRemoveWhenFarAway(!type.persistent());
        }
        // Zombie-like bases would otherwise pick up and keep loot.
        entity.setCanPickupItems(false);
        if (type.modelKey() != null) {
            entity.setSilent(true);
        }
    }

    /** Attaches behaviour, model and Bedrock presentation to a tagged entity. Idempotent. */
    public void bind(LivingEntity entity) {
        if (tracked.containsKey(entity.getUniqueId())) {
            return;
        }
        Optional<String> id = ModMobIdentity.mobId(new PdcTagStore(entity.getPersistentDataContainer(), customEntityKey));
        if (id.isEmpty()) {
            return;
        }
        ModMobType type = catalog.find(id.get()).orElse(null);
        if (type == null) {
            return;
        }
        JavaModelHandle handle = null;
        if (type.modelKey() != null && models != null) {
            try {
                handle = models.attach(entity, type.modelKey());
                models.animate(handle, "idle");
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "[AlexsCaves] No se pudo adjuntar el modelo " + type.modelKey(), failure);
            }
        }
        ModMobBehaviour behaviour;
        try {
            behaviour = catalog.behaviour(type.id()).map(factory -> factory.create(entity, type))
                .orElse(ModMobBehaviour.NONE);
        } catch (RuntimeException failure) {
            if (handle != null) {
                models.close(handle);
            }
            plugin.getLogger().log(Level.WARNING, "[AlexsCaves] No se pudo crear el comportamiento de " + type.id(), failure);
            return;
        }
        ClientEntityPresentationRegistry.register(entity.getUniqueId(), type.baseEntity().toUpperCase(Locale.ROOT), type.geyserMarker());
        tracked.put(entity.getUniqueId(), new Instance(type, behaviour, handle));
    }

    /**
     * The mob died: its behaviour stops now, but its model plays the death animation and is closed when the body is
     * removed (vanilla keeps a dead body about 20 ticks), or after 60 ticks at most.
     */
    public void releaseDead(LivingEntity entity) {
        Instance instance = tracked.get(entity.getUniqueId());
        String animation = null;
        if (instance != null && instance.model() != null) {
            try {
                animation = instance.behaviour().deathAnimation(entity);
            } catch (RuntimeException failure) {
                animation = null;
            }
        }
        if (animation == null || models == null) {
            release(entity);
            return;
        }
        tracked.remove(entity.getUniqueId());
        UUID id = entity.getUniqueId();
        try {
            instance.behaviour().close(entity, this);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[AlexsCaves] Falló la limpieza de " + instance.type().id(), failure);
        }
        dying.put(id, instance.model());
        try {
            models.animateOnce(instance.model(), animation, () -> { });
        } catch (RuntimeException failure) {
            closeDying(id);
            return;
        }
        tasks().later(60L, () -> closeDying(id));
    }

    private void closeDying(UUID id) {
        JavaModelHandle handle = dying.remove(id);
        if (handle != null) {
            try {
                models.close(handle);
            } finally {
                ClientEntityPresentationRegistry.unregister(id);
            }
        }
    }

    public void release(LivingEntity entity) {
        closeDying(entity.getUniqueId());
        Instance instance = tracked.remove(entity.getUniqueId());
        if (instance == null) {
            return;
        }
        try {
            instance.behaviour().close(entity, this);
        } finally {
            if (instance.model() != null) {
                models.close(instance.model());
            }
            ClientEntityPresentationRegistry.unregister(entity.getUniqueId());
        }
    }

    /** Sound events the mod does not have (playing them would only log client warnings). */
    private static final Set<String> SILENT_SOUNDS = Set.of("ferrouslime_idle");

    public void tick() {
        tick += 1;
        if (tick % TICK_PERIOD != 0) {
            return;
        }
        for (UUID id : List.copyOf(tracked.keySet())) {
            Instance instance = tracked.get(id);
            Entity entity = plugin.getServer().getEntity(id);
            if (!(entity instanceof LivingEntity living) || !living.isValid()) {
                discard(id, entity instanceof LivingEntity gone ? gone : null);
                continue;
            }
            if (instance.model() != null && ThreadLocalRandom.current().nextInt(IDLE_SOUND_CHANCE) == 0) {
                playSound(living, instance.type(), "idle");
            }
            try {
                instance.behaviour().tick(living, this, TICK_PERIOD);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "[AlexsCaves] Falló el comportamiento de " + instance.type().id(), failure);
            }
        }
    }

    public Optional<ModMobType> typeOf(Entity entity) {
        Instance instance = tracked.get(entity.getUniqueId());
        if (instance != null) {
            return Optional.of(instance.type());
        }
        return ModMobIdentity.mobId(new PdcTagStore(entity.getPersistentDataContainer(), customEntityKey))
            .flatMap(catalog::find);
    }

    public Optional<ModMobBehaviour> behaviourOf(Entity entity) {
        Instance instance = tracked.get(entity.getUniqueId());
        return instance == null ? Optional.empty() : Optional.of(instance.behaviour());
    }

    /**
     * Replaces vanilla drops with the mod's loot table.
     *
     * @param burning the mob died on fire: raw drops with a module cooking recipe come out cooked (vanilla
     *                {@code furnace_smelt} with {@code on_fire})
     */
    public List<ItemStack> rollDrops(ModMobType type, Player killer, boolean burning) {
        int looting = 0;
        if (killer != null) {
            looting = killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        }
        List<ItemStack> drops = new ArrayList<>();
        for (MobDrop drop : type.drops()) {
            int amount = drop.roll(ThreadLocalRandom.current(), looting, killer != null);
            if (amount > 0) {
                String item = burning ? dev.linqfy.bigCasares.modules.alexscaves.core.item.AlexsCavesCraftGuard.COOKING
                    .getOrDefault(drop.item(), drop.item()) : drop.item();
                createItem(item, amount).ifPresent(drops::add);
            }
        }
        return drops;
    }

    /** Module item id ({@code ac_...}) of a stack, for content listeners that react to held items. */
    public Optional<String> itemId(ItemStack stack) {
        return stack == null || stack.getType().isAir() ? Optional.empty() : items.resolveItemId(stack);
    }

    /** A catalog item ({@code ac_...}) or a vanilla material by name. */
    public Optional<ItemStack> createItem(String id, int amount) {
        if (id.startsWith("ac_")) {
            try {
                return Optional.of(items.createItemStack(id, amount));
            } catch (RuntimeException missing) {
                plugin.getLogger().warning("[AlexsCaves] Drop sin ítem en el catálogo: " + id);
                return Optional.empty();
            }
        }
        Material material = Material.matchMaterial(id);
        return material == null ? Optional.empty() : Optional.of(new ItemStack(material, amount));
    }

    /** Forgets an instance whose entity is gone; the behaviour cleans up with a possibly null entity. */
    private void discard(UUID id, LivingEntity entity) {
        Instance instance = tracked.remove(id);
        if (instance == null) {
            return;
        }
        try {
            instance.behaviour().close(entity, this);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[AlexsCaves] Falló la limpieza de " + instance.type().id(), failure);
        } finally {
            if (instance.model() != null) {
                models.close(instance.model());
            }
            ClientEntityPresentationRegistry.unregister(id);
        }
    }

    /**
     * Plays the mod's sound event {@code alexscaves:<mob>_<suffix>} for mobs whose vanilla base is silenced
     * (those with a model). Missing events simply play nothing on the client.
     */
    public void playSound(LivingEntity entity, ModMobType type, String suffix) {
        ModMobBehaviour behaviour = behaviourOf(entity).orElse(ModMobBehaviour.NONE);
        String variant = behaviour.soundSuffix(entity, suffix);
        if (variant != null && type.modelKey() != null && !SILENT_SOUNDS.contains(type.id() + "_" + variant)) {
            entity.getWorld().playSound(entity.getLocation(), "alexscaves:" + type.id() + "_" + variant,
                behaviour.soundVolume(), 0.9f + ThreadLocalRandom.current().nextFloat() * 0.2f);
        }
    }

    public List<String> knownIds() {
        return catalog.types().stream().map(ModMobType::id).sorted().toList();
    }

    public int trackedCount() {
        return tracked.size();
    }

    public int countNear(Location center, double radius, Predicate<ModMobType> filter) {
        int count = 0;
        double radiusSquared = radius * radius;
        for (Map.Entry<UUID, Instance> entry : tracked.entrySet()) {
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (entity != null && entity.getWorld().equals(center.getWorld())
                && entity.getLocation().distanceSquared(center) <= radiusSquared && filter.test(entry.getValue().type())) {
                count++;
            }
        }
        return count;
    }

    public void shutdown() {
        for (UUID id : List.copyOf(tracked.keySet())) {
            Entity entity = plugin.getServer().getEntity(id);
            if (entity instanceof LivingEntity living) {
                release(living);
            } else {
                discard(id, null);
            }
        }
        for (UUID id : List.copyOf(dying.keySet())) {
            closeDying(id);
        }
    }

    @Override
    public Plugin plugin() {
        return plugin;
    }

    @Override
    public ModEffectService effects() {
        return effects;
    }

    @Override
    public ModDamageTypes damageTypes() {
        return damageTypes;
    }

    @Override
    public CooldownLedger cooldowns() {
        return cooldowns;
    }

    @Override
    public JavaModelGateway models() {
        return models;
    }

    @Override
    public Optional<JavaModelHandle> model(UUID entity) {
        Instance instance = tracked.get(entity);
        return instance == null ? Optional.empty() : Optional.ofNullable(instance.model());
    }

    @Override
    public ModMobService mobs() {
        return this;
    }

    private record Instance(ModMobType type, ModMobBehaviour behaviour, JavaModelHandle model) {
    }

    private record PdcTagStore(PersistentDataContainer container, NamespacedKey key) implements ModMobIdentity.TagStore {
        @Override
        public String getString(String ignored) {
            return container.get(key, PersistentDataType.STRING);
        }

        @Override
        public void setString(String ignored, String value) {
            container.set(key, PersistentDataType.STRING, value);
        }
    }
}
