package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * El Espejo de Johari en el mundo: un Husk invisible, sin IA ni gravedad, que es el blanco que se golpea, con el
 * modelo {@link #MODEL} encima. El encuentro no puede registrar listeners, asi que los golpes se leen por sondeo: la
 * vida del Husk se mantiene en {@link #POOL}; lo que le falta en cada tick es el dano del golpe y
 * {@code getLastDamageCause()} dice quien fue. Solo cuentan golpes de jugadores (directos o con proyectil).
 *
 * <p>Bedrock no ve el modelo: ve un soporte invisible con un bloque de vidrio como casco (y el nombre), con todos los
 * huecos trabados, que le pasa los golpes al Husk como cualquier doble de {@code BedrockStandIns}. Si el Husk deja de
 * ser valido, el encuentro lo vuelve a crear ({@link #respawn()}).</p>
 */
final class EnmaMirror {

    static final String MODEL = "bigcasares_myth_enma_mirror";
    private static final double POOL = 1000.0;
    private static final double HITBOX_SCALE = 1.5;

    /** Un golpe de un jugador al espejo. */
    record Strike(Player attacker, double damage) {
    }

    private final MythicBossSupport support;
    private final UUID bossId;
    private Husk anchor;
    private Location home;
    private JavaModelHandle model;
    private boolean warnedModel;

    EnmaMirror(MythicBossSupport support, UUID bossId) {
        this.support = Objects.requireNonNull(support, "support");
        this.bossId = Objects.requireNonNull(bossId, "bossId");
    }

    void spawn(Location at) {
        home = at.clone();
        anchor = support.spawnOwned(bossId, home, Husk.class, husk -> {
            husk.setAI(false);
            husk.setSilent(true);
            husk.setInvisible(true);
            husk.setGravity(false);
            husk.setCollidable(false);
            husk.setPersistent(false);
            husk.setRemoveWhenFarAway(false);
            husk.setCanPickupItems(false);
            husk.setAdult();
            husk.setShouldBurnInDay(false);
            attribute(husk, Attribute.MAX_HEALTH, POOL);
            attribute(husk, Attribute.KNOCKBACK_RESISTANCE, 1.0);
            attribute(husk, Attribute.SCALE, HITBOX_SCALE);
            husk.setHealth(POOL);
            EntityEquipment equipment = husk.getEquipment();
            if (equipment != null) {
                equipment.clear();
            }
            LootTable empty = Bukkit.getLootTable(NamespacedKey.minecraft("empty"));
            if (empty != null) {
                husk.setLootTable(empty);
            }
        });
        anchor.setRotation(home.getYaw(), 0F);
        anchor.setBodyYaw(home.getYaw());
        try {
            model = support.models().attach(anchor, MODEL);
            support.models().animate(model, "idle");
        } catch (RuntimeException | LinkageError missing) {
            model = null;
            if (!warnedModel) {
                warnedModel = true;
                support.logger().warning("[Enma] Sin modelo " + MODEL + " (" + missing.getMessage()
                    + "): el espejo se ve solo como particulas en Java.");
            }
        }
        try {
            ArmorStand standIn = support.spawnBedrockStandIn(bossId, anchor, ArmorStand.class, 1.4);
            standIn.setInvisible(true);
            standIn.setBasePlate(false);
            standIn.customName(Component.text("Espejo de Johari", NamedTextColor.GOLD));
            standIn.setCustomNameVisible(true);
            EntityEquipment equipment = standIn.getEquipment();
            equipment.setHelmet(new ItemStack(Material.GLASS));
            lockEquipment(standIn);
        } catch (RuntimeException unavailable) {
            // sin dobles de Bedrock (modulo sin Geyser)
        }
    }

    /** Huecos que un jugador puede tocar en un soporte (los de un jugador: armadura y manos). */
    static final EquipmentSlot[] STAND_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND,
        EquipmentSlot.OFF_HAND
    };

    /**
     * Traba todos los huecos del soporte de Bedrock: nadie le saca el vidrio ni le pone nada. No es marcador porque los
     * golpes tienen que llegarle (los pasa {@code BedrockStandIns} al Husk).
     */
    static void lockEquipment(ArmorStand stand) {
        for (EquipmentSlot slot : STAND_SLOTS) {
            for (ArmorStand.LockType lock : ArmorStand.LockType.values()) {
                stand.addEquipmentLock(slot, lock);
            }
        }
    }

    /**
     * Vuelve a crear el espejo en su lugar si el Husk dejo de ser valido ({@code /kill}, chunk descargado); devuelve si
     * quedo presente. El doble de Bedrock viejo se va solo (su ancla ya no es valida).
     */
    boolean respawn() {
        if (present()) {
            return true;
        }
        if (home == null) {
            return false;
        }
        remove();
        try {
            spawn(home);
        } catch (RuntimeException failure) {
            support.logger().warning("[Enma] No se pudo volver a crear el espejo: " + failure.getMessage());
            return false;
        }
        return present();
    }

    boolean present() {
        return anchor != null && anchor.isValid();
    }

    /** Centro del espejo (para particulas). */
    Location center() {
        return home.clone().add(0, 1.6, 0);
    }

    Location home() {
        return home.clone();
    }

    /** El golpe de este tick, si hubo uno de un jugador; restituye la vida del Husk y lo vuelve a su lugar. */
    Optional<Strike> poll() {
        if (!present()) {
            return Optional.empty();
        }
        if (anchor.getLocation().distanceSquared(home) > 0.01) {
            anchor.teleport(home);
        }
        double lost = POOL - anchor.getHealth();
        if (lost <= 0.01) {
            return Optional.empty();
        }
        anchor.setHealth(POOL);
        Player attacker = attacker(anchor.getLastDamageCause());
        return attacker == null ? Optional.empty() : Optional.of(new Strike(attacker, lost));
    }

    void animate(String animation) {
        if (model == null) {
            return;
        }
        try {
            support.models().animate(model, animation);
        } catch (RuntimeException ignored) {
            // cosmetico
        }
    }

    void animateOnce(String animation, String then) {
        if (model == null) {
            return;
        }
        try {
            JavaModelHandle handle = model;
            support.models().animateOnce(handle, animation, () -> {
                if (model == handle) {
                    support.models().animate(handle, then);
                }
            });
        } catch (RuntimeException ignored) {
            // cosmetico
        }
    }

    /** Cierra el modelo y borra el Husk (su doble de Bedrock se va solo cuando el ancla deja de ser valida). */
    void remove() {
        if (model != null) {
            try {
                support.models().close(model);
            } catch (RuntimeException ignored) {
                // cosmetico
            }
            model = null;
        }
        if (anchor != null && anchor.isValid()) {
            anchor.remove();
        }
        anchor = null;
    }

    private static Player attacker(EntityDamageEvent cause) {
        if (!(cause instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private static void attribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
