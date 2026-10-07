package dev.linqfy.bigCasares.modules.donpollos;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Los bichos que invoca el Don Pollo Boss. Todos son zombies por dentro (IA de pegar cuerpo a cuerpo)
 * con un modelo de BetterModel encima: no se queman con el sol, solo atacan jugadores, no se convierten
 * en ahogados, no se guardan en el mundo y al morir desaparecen con una nube (asi no se ve al zombie).
 */
final class DonPollosBossMinions implements Listener {

    enum Kind {
        GALLINA("bigcasares_gallina_wsp", "§aGallina WhatsApp", true, "entity.chicken", true),
        LABUBU_VERDE("bigcasares_labubu_verde", "§2Labubu", false, "entity.silverfish", true),
        LABUBU_ROJO("bigcasares_labubu_rojo", "§cLabubu", false, "entity.silverfish", true),
        LABUBU_ROSA("bigcasares_labubu_rosa", "§dLabubu", false, "entity.silverfish", true),
        LABUBU_AZUL("bigcasares_labubu_azul", "§9Labubu", false, "entity.silverfish", true),
        CORONEL("bigcasares_coronel", "§fEl Coronel", true, "entity.player", false),
        CORONEL_FINAL("bigcasares_coronel", "§4Sanders Fase Final", true, "entity.player", false);

        static final Kind[] LABUBUS = {LABUBU_VERDE, LABUBU_ROJO, LABUBU_ROSA, LABUBU_AZUL};

        final String model;
        final String displayName;
        /** El modelo crece con la escala de la entidad (los Labubus no: achican la hitbox y el modelo ya es chico). */
        final boolean modelFollowsScale;
        /** Sonidos de vanilla (como texto: asi el enum no necesita el servidor para cargarse). */
        final String ambient;
        final String hurt;
        final String death;

        Kind(String model, String displayName, boolean modelFollowsScale, String sounds, boolean hasAmbient) {
            this.model = model;
            this.displayName = displayName;
            this.modelFollowsScale = modelFollowsScale;
            this.ambient = hasAmbient ? sounds + ".ambient" : null;
            this.hurt = sounds + ".hurt";
            this.death = sounds + ".death";
        }

        boolean isLabubu() {
            return name().startsWith("LABUBU");
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Silverfish: 8 de vida, 1 de dano, 0.25 de velocidad. */
    static final double SILVERFISH_HEALTH = 8;
    static final double SILVERFISH_DAMAGE = 1;
    static final double SILVERFISH_SPEED = 0.25;
    /** Los Labubus miden ~1.2 bloques: el zombie (1.95) se achica a esa altura. */
    static final double LABUBU_SCALE = 0.62;
    private static final double TARGET_RANGE = 40;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final DonPollosModelBinder binder;
    private final DonPollosBossSettings settings;
    private final NamespacedKey key;

    DonPollosBossMinions(JavaPlugin plugin, DonPollosModelBinder binder, DonPollosBossSettings settings) {
        this.plugin = plugin;
        this.binder = binder;
        this.settings = settings;
        this.key = new NamespacedKey(plugin, "boss_minion");
    }

    Optional<Kind> kind(Entity entity) {
        if (!(entity instanceof Zombie)) {
            return Optional.empty();
        }
        String id = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Kind.valueOf(id.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    Optional<Kind> kind(UUID id) {
        Entity entity = Bukkit.getEntity(id);
        return entity == null || !entity.isValid() ? Optional.empty() : kind(entity);
    }

    boolean isMinion(Entity entity) {
        return kind(entity).isPresent();
    }

    /** Spawnea un bicho; si {@code target} no es null va derecho a el. */
    Zombie spawn(Kind kind, Location location, Player target) {
        return spawn(kind, location, target, zombie -> { });
    }

    /** Igual, con un retoque extra antes de que aparezca (por ejemplo el Coronel mas fuerte en cada ronda). */
    Zombie spawn(Kind kind, Location location, Player target, java.util.function.Consumer<Zombie> tweak) {
        World world = location.getWorld();
        Zombie minion = world.spawn(location, Zombie.class, false, zombie -> {
            zombie.getPersistentDataContainer().set(key, PersistentDataType.STRING, kind.id());
            zombie.customName(LEGACY.deserialize(kind.displayName));
            zombie.setCustomNameVisible(false);
            zombie.setAdult();
            zombie.setShouldBurnInDay(false);
            zombie.setCanPickupItems(false);
            zombie.setCanBreakDoors(false);
            zombie.setSilent(true);
            zombie.setPersistent(false);
            zombie.getEquipment().clear();
            setAttribute(zombie, Attribute.SPAWN_REINFORCEMENTS, 0);
            dressForBedrock(zombie, kind);
            switch (kind) {
                case GALLINA -> {
                    setAttribute(zombie, Attribute.MAX_HEALTH, settings.chickenHealth());
                    setAttribute(zombie, Attribute.ATTACK_DAMAGE, settings.chickenDamage());
                    zombie.setHealth(settings.chickenHealth());
                }
                case CORONEL, CORONEL_FINAL -> armWithNetherite(zombie);
                default -> {
                    // Labubus: vida, dano y velocidad de silverfish, del tamano del modelo
                    setAttribute(zombie, Attribute.MAX_HEALTH, SILVERFISH_HEALTH);
                    setAttribute(zombie, Attribute.ATTACK_DAMAGE, SILVERFISH_DAMAGE);
                    setAttribute(zombie, Attribute.MOVEMENT_SPEED, SILVERFISH_SPEED);
                    setAttribute(zombie, Attribute.SCALE, LABUBU_SCALE);
                    zombie.setHealth(SILVERFISH_HEALTH);
                }
            }
            tweak.accept(zombie);
        });
        binder.bindModel(minion, kind.model, kind.modelFollowsScale);
        if (target != null) {
            minion.setTarget(target);
        }
        world.spawnParticle(Particle.CLOUD, location.clone().add(0, 0.8, 0), 12, 0.4, 0.4, 0.4, 0.02);
        world.playSound(location, kind == Kind.GALLINA ? Sound.ENTITY_CHICKEN_EGG : Sound.ENTITY_EVOKER_PREPARE_SUMMON,
            1.2f, kind == Kind.CORONEL ? 0.7f : 1.4f);
        return minion;
    }

    /**
     * En Java BetterModel esconde la armadura (se ve el modelo); en Bedrock, que ve el zombie, la armadura de cuero
     * del color de cada bicho ayuda a reconocerlo: gallinas verde WhatsApp y cada Labubu de su color.
     */
    private static void dressForBedrock(Zombie zombie, Kind kind) {
        org.bukkit.Color color = switch (kind) {
            case GALLINA -> org.bukkit.Color.fromRGB(37, 211, 102);
            case LABUBU_VERDE -> org.bukkit.Color.fromRGB(40, 206, 30);
            case LABUBU_ROJO -> org.bukkit.Color.fromRGB(232, 18, 18);
            case LABUBU_ROSA -> org.bukkit.Color.fromRGB(232, 18, 112);
            case LABUBU_AZUL -> org.bukkit.Color.fromRGB(70, 24, 240);
            default -> null;
        };
        if (color == null) {
            return;
        }
        EntityEquipment equipment = zombie.getEquipment();
        equipment.setHelmet(leather(Material.LEATHER_HELMET, color));
        equipment.setChestplate(leather(Material.LEATHER_CHESTPLATE, color));
        equipment.setHelmetDropChance(0);
        equipment.setChestplateDropChance(0);
    }

    private static ItemStack leather(Material material, org.bukkit.Color color) {
        ItemStack piece = new ItemStack(material);
        if (piece.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta meta) {
            meta.setColor(color);
            piece.setItemMeta(meta);
        }
        return piece;
    }

    /** El Coronel pega como un zombie con armadura completa de netherite y espada de netherite. */
    private static void armWithNetherite(Zombie zombie) {
        EntityEquipment equipment = zombie.getEquipment();
        equipment.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
        equipment.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        equipment.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        equipment.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        equipment.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        equipment.setHelmetDropChance(0);
        equipment.setChestplateDropChance(0);
        equipment.setLeggingsDropChance(0);
        equipment.setBootsDropChance(0);
        equipment.setItemInMainHandDropChance(0);
    }

    static void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Lugar libre al lado de {@code center} (a {@code radius} bloques); si no hay, el mismo centro. */
    static Location around(Location center, double radius) {
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        Location location = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        if (!location.getBlock().isPassable() || !location.clone().add(0, 1, 0).getBlock().isPassable()) {
            return center.clone();
        }
        return location;
    }

    long count(Collection<UUID> minions, java.util.function.Predicate<Kind> filter) {
        return minions.stream().map(this::kind).flatMap(Optional::stream).filter(filter).count();
    }

    /** Cada segundo: los que se quedaron sin objetivo van al jugador mas cercano, y hacen ruido de vez en cuando. */
    void tick(Collection<UUID> minions) {
        minions.removeIf(id -> kind(id).isEmpty());
        for (UUID id : minions) {
            if (!(Bukkit.getEntity(id) instanceof Zombie minion)) {
                continue;
            }
            if (!(minion.getTarget() instanceof Player current) || !attackable(current)) {
                minion.setTarget(nearestPlayer(minion.getLocation()));
            }
            Kind kind = kind(minion).orElseThrow();
            if (kind.ambient != null && ThreadLocalRandom.current().nextInt(4) == 0) {
                minion.getWorld().playSound(minion.getLocation(), kind.ambient, 1f, kind.isLabubu() ? 1.5f : 0.9f);
            }
        }
    }

    void removeAll(Collection<UUID> minions) {
        for (UUID id : minions) {
            Entity entity = Bukkit.getEntity(id);
            binder.release(id);
            if (entity != null) {
                entity.remove();
            }
        }
        minions.clear();
    }

    private static boolean attackable(Player player) {
        return !player.isDead() && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
    }

    private static Player nearestPlayer(Location from) {
        Player nearest = null;
        double best = TARGET_RANGE * TARGET_RANGE;
        for (Player player : from.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(from);
            if (attackable(player) && distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    // ------------------------------------------------------------ eventos

    /** Solo atacan jugadores (no a los Don Pollos aldeanos ni a otros mobs). */
    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && !(event.getTarget() instanceof Player) && isMinion(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** No se queman con el sol (el fuego y la lava si los queman). */
    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (!(event instanceof EntityCombustByBlockEvent) && !(event instanceof EntityCombustByEntityEvent)
            && isMinion(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Que no se conviertan en ahogados (perderian el modelo). */
    @EventHandler
    public void onTransform(EntityTransformEvent event) {
        if (isMinion(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        kind(event.getEntity()).ifPresent(kind ->
            event.getEntity().getWorld().playSound(event.getEntity().getLocation(), kind.hurt, 1.2f,
                kind.isLabubu() ? 1.5f : 0.9f));
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        Optional<Kind> kind = kind(event.getEntity());
        if (kind.isEmpty()) {
            return;
        }
        LivingEntity minion = event.getEntity();
        event.getDrops().clear();
        event.setDroppedExp(0);
        World world = minion.getWorld();
        world.playSound(minion.getLocation(), kind.get().death, 1.2f, kind.get().isLabubu() ? 1.5f : 0.9f);
        world.spawnParticle(Particle.CLOUD, minion.getLocation().add(0, 0.8, 0), 16, 0.4, 0.4, 0.4, 0.03);
        if (kind.get() == Kind.GALLINA) {
            world.spawnParticle(Particle.ITEM, minion.getLocation().add(0, 0.8, 0), 12, 0.3, 0.3, 0.3, 0.05,
                new ItemStack(Material.FEATHER));
        }
        // el modelo se suelta recien cuando el zombie ya no existe: si no, se ve al zombie muriendo
        Bukkit.getScheduler().runTask(plugin, () -> {
            minion.remove();
            binder.release(minion.getUniqueId());
        });
    }

    @EventHandler
    public void onUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isMinion(entity)) {
                binder.release(entity.getUniqueId());
                entity.remove();
            }
        }
    }
}
