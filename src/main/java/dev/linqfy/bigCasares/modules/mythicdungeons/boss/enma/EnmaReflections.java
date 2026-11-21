package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.loot.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Los reflejos de la fase 1: por cada jugador de la sala sale del espejo un zombi (no se quema al sol) con su
 * nombre y una copia de su armadura y de lo que tiene en las manos (sin encantamientos ni atributos agregados, sin
 * totems, probabilidad de soltarlos 0).
 * Si el jugador no lleva casco, el reflejo lleva su cabeza. Cada reflejo persigue a su original mientras siga en la
 * sala; si no, al jugador mas cercano. Al ser mobs vanilla, Java y Bedrock los ven igual.
 */
final class EnmaReflections {

    static final double HEALTH = 24.0;
    /** Ataque base; el arma copiada suma su propio dano encima. */
    static final double ATTACK = 2.0;
    private static final double SPEED = 0.24;

    private final MythicBossSupport support;
    private final UUID bossId;
    private final List<Reflection> reflections = new ArrayList<>();

    EnmaReflections(MythicBossSupport support, UUID bossId) {
        this.support = Objects.requireNonNull(support, "support");
        this.bossId = Objects.requireNonNull(bossId, "bossId");
    }

    /** Hace salir una copia por jugador en {@code spots} (en el mismo orden); devuelve cuantas nacieron. */
    int summon(List<Player> players, List<Location> spots) {
        int created = 0;
        for (int i = 0; i < players.size() && i < spots.size(); i++) {
            Player original = players.get(i);
            Location at = spots.get(i);
            try {
                Zombie copy = support.spawnOwned(bossId, at, Zombie.class, zombie -> dress(zombie, original));
                reflections.add(new Reflection(copy, original.getUniqueId()));
                created++;
            } catch (RuntimeException failure) {
                support.logger().warning("[Enma] No pudo salir el reflejo de " + original.getName() + ": "
                    + failure.getMessage());
                continue;
            }
            at.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 1, 0), 40, 0.3, 0.9, 0.3, 0.05);
            at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 8, 0.3, 0.8, 0.3, 0.02);
            at.getWorld().playSound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 1.5F, 0.8F);
        }
        return created;
    }

    int alive() {
        int count = 0;
        for (Reflection reflection : reflections) {
            if (reflection.entity.isValid() && !reflection.entity.isDead()) {
                count++;
            }
        }
        return count;
    }

    /** Objetivos y correa a la sala (un reflejo que se va de la sala vuelve al frente del espejo). */
    void tick(List<Player> targets, Predicate<Location> inside, Location fallback) {
        reflections.removeIf(reflection -> !reflection.entity.isValid() || reflection.entity.isDead());
        for (Reflection reflection : reflections) {
            Zombie zombie = reflection.entity;
            if (!inside.test(zombie.getLocation())) {
                zombie.teleport(fallback);
            }
            Player wanted = null;
            for (Player player : targets) {
                if (player.getUniqueId().equals(reflection.original)) {
                    wanted = player;
                    break;
                }
            }
            if (wanted == null) {
                wanted = EnmaController.nearest(targets, zombie.getLocation());
            }
            if (zombie.getTarget() != wanted) {
                zombie.setTarget(wanted);
            }
        }
    }

    /** Borra los reflejos que quedan (al quebrarse el espejo); el resto lo limpia {@link MythicBossSupport#cleanup}. */
    void clear() {
        for (Reflection reflection : reflections) {
            if (reflection.entity.isValid()) {
                Location at = reflection.entity.getLocation().add(0, 1, 0);
                at.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at, 25, 0.3, 0.8, 0.3, 0.05);
                reflection.entity.remove();
            }
        }
        reflections.clear();
    }

    private static void dress(Zombie zombie, Player original) {
        zombie.setAdult();
        zombie.setShouldBurnInDay(false);
        zombie.setCanBreakDoors(false);
        zombie.setCanPickupItems(false);
        zombie.setPersistent(false);
        zombie.setRemoveWhenFarAway(false);
        attribute(zombie, Attribute.MAX_HEALTH, HEALTH);
        attribute(zombie, Attribute.ATTACK_DAMAGE, ATTACK);
        attribute(zombie, Attribute.MOVEMENT_SPEED, SPEED);
        attribute(zombie, Attribute.FOLLOW_RANGE, 40.0);
        attribute(zombie, Attribute.SPAWN_REINFORCEMENTS, 0.0);
        zombie.setHealth(HEALTH);
        zombie.customName(Component.text("Reflejo de " + original.getName(), NamedTextColor.RED));
        zombie.setCustomNameVisible(true);
        LootTable empty = Bukkit.getLootTable(NamespacedKey.minecraft("empty"));
        if (empty != null) {
            zombie.setLootTable(empty);
        }
        EntityEquipment equipment = zombie.getEquipment();
        if (equipment == null) {
            return;
        }
        PlayerInventory inventory = original.getInventory();
        ItemStack helmet = copy(inventory.getHelmet());
        equipment.setHelmet(helmet != null ? helmet : head(original));
        equipment.setChestplate(copy(inventory.getChestplate()));
        equipment.setLeggings(copy(inventory.getLeggings()));
        equipment.setBoots(copy(inventory.getBoots()));
        equipment.setItemInMainHand(copy(inventory.getItemInMainHand()));
        equipment.setItemInOffHand(copy(inventory.getItemInOffHand()));
        equipment.setHelmetDropChance(0.0F);
        equipment.setChestplateDropChance(0.0F);
        equipment.setLeggingsDropChance(0.0F);
        equipment.setBootsDropChance(0.0F);
        equipment.setItemInMainHandDropChance(0.0F);
        equipment.setItemInOffHandDropChance(0.0F);
    }

    /**
     * Copia de un objeto sin encantamientos ni modificadores de atributos agregados (el reflejo se ve igual pero no
     * pega ni aguanta como el equipo del jugador; un arma conserva su dano normal). Un totem u otro objeto que
     * salva de la muerte no se copia: el reflejo no puede revivir.
     */
    private static ItemStack copy(ItemStack item) {
        if (item == null || item.getType().isAir()
            || !EnmaFightRules.mirrorable(item.getType(), item.hasData(DataComponentTypes.DEATH_PROTECTION))) {
            return null;
        }
        ItemStack copy = item.clone();
        copy.setAmount(1);
        copy.removeEnchantments();
        copy.resetData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        return copy;
    }

    private static ItemStack head(Player original) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setPlayerProfile(original.getPlayerProfile());
            head.setItemMeta(meta);
        }
        return head;
    }

    private static void attribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private record Reflection(Zombie entity, UUID original) {
    }
}
