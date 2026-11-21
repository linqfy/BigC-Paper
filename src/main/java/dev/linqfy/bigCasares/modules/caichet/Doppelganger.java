package dev.linqfy.bigCasares.modules.caichet;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Una "gallina" de la dimension con el cuerpo de un jugador. Son dos entidades:
 * <ul>
 *   <li>una gallina invisible y muda que pone la IA (camina, persigue, pica, recibe el dano y muere);</li>
 *   <li>un maniqui con la skin del jugador copiado, su armadura de cuando entro y la mejor arma de su inventario, que
 *       sigue a la gallina cada tick. Es lo que se ve; los golpes que recibe pasan a la gallina.</li>
 * </ul>
 * Ninguna de las dos se guarda en el mundo: al reiniciar o al descargarse el chunk desaparecen.
 */
final class Doppelganger {

    /** Vida de un jugador. */
    private static final double HEALTH = 20.0;
    /** Mas lejos que esto de su gallina, el cuerpo se teletransporta en vez de caminar hasta ella (bloques). */
    static final double SNAP_DISTANCE = 3.0;

    private static final Map<UUID, Doppelganger> BY_ENTITY = new HashMap<>();

    final Chicken brain;
    final Mannequin body;
    final double weaponDamage;

    private Doppelganger(Chicken brain, Mannequin body, double weaponDamage) {
        this.brain = brain;
        this.body = body;
        this.weaponDamage = weaponDamage;
    }

    /** El doble al que pertenece esta entidad (la gallina o el maniqui), o null. */
    static Doppelganger of(Entity entity) {
        return entity == null ? null : BY_ENTITY.get(entity.getUniqueId());
    }

    static Collection<Doppelganger> all() {
        if (BY_ENTITY.isEmpty()) {
            return List.of(); // lo comun: sin dobles, sin copiar nada
        }
        return new ArrayList<>(new LinkedHashSet<>(BY_ENTITY.values()));
    }

    static Doppelganger spawn(Location at, Player copy) {
        ItemStack[] armor = ArmorSnapshot.get(copy);
        ItemStack weapon = Weapons.bestOf(copy);

        Chicken brain = at.getWorld().spawn(at, Chicken.class, chicken -> {
            // la gallina la ve solo Bedrock (que no tiene maniquies); Java ve el maniqui. Ver showTo
            chicken.setVisibleByDefault(false);
            Bedrock.markSystem(chicken); // que BIG C no le escale la vida ni lo mute
            chicken.customName(Component.text(copy.getName()));
            chicken.setCustomNameVisible(true);
            chicken.setSilent(true);
            chicken.setPersistent(false);
            chicken.setEggLayTime(Integer.MAX_VALUE);
            AttributeInstance maxHealth = chicken.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(HEALTH);
                chicken.setHealth(HEALTH);
            }
            // la armadura tambien en la gallina: es la que recibe los golpes, asi protege de verdad
            dress(chicken.getEquipment(), armor, null, true);
        });

        Mannequin body;
        try {
            body = at.getWorld().spawn(at, Mannequin.class, mannequin -> {
            mannequin.setProfile(ResolvableProfile.resolvableProfile(copy.getPlayerProfile()));
            mannequin.customName(Component.text(copy.getName()));
            mannequin.setCustomNameVisible(false);
            mannequin.setDescription(null);
            mannequin.setVisibleByDefault(false);
            Bedrock.markSystem(mannequin);
            mannequin.setImmovable(true);
            mannequin.setGravity(false);
            mannequin.setSilent(true);
            mannequin.setPersistent(false);
            dress(mannequin.getEquipment(), armor, weapon, false);
            });
        } catch (RuntimeException e) {
            // sin cuerpo no hay doble: que no quede una gallina invisible suelta
            brain.remove();
            throw e;
        }

        Doppelganger doppel = new Doppelganger(brain, body, Weapons.damage(weapon));
        BY_ENTITY.put(brain.getUniqueId(), doppel);
        BY_ENTITY.put(body.getUniqueId(), doppel);
        return doppel;
    }

    /** @param mob solo a los mobs se les puede decir "no sueltes esto al morir" (el maniqui no es mob) */
    private static void dress(EntityEquipment equipment, ItemStack[] armor, ItemStack weapon, boolean mob) {
        if (equipment == null) {
            return;
        }
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < slots.length; i++) {
            if (armor[i] != null) {
                equipment.setItem(slots[i], armor[i].clone());
            }
            if (mob) {
                equipment.setDropChance(slots[i], 0f);
            }
        }
        if (weapon != null) {
            equipment.setItemInMainHand(weapon);
        }
        if (mob) {
            equipment.setDropChance(EquipmentSlot.HAND, 0f);
        }
    }

    /**
     * El maniqui sigue a la gallina y mira para el mismo lado. Cerca se lo empuja con velocidad, no con un teleport por
     * tick: asi el servidor manda movimiento normal, que Bedrock (via Geyser) interpola igual que a un jugador; con
     * teleports Bedrock lo ve quieto o a los saltos. Si quedo lejos (o en otro mundo) se lo lleva de una.
     */
    void sync() {
        Location to = brain.getLocation();
        Location from = body.getLocation();
        if (!from.getWorld().equals(to.getWorld()) || from.distanceSquared(to) > SNAP_DISTANCE * SNAP_DISTANCE) {
            body.teleport(to);
        } else {
            body.setVelocity(to.toVector().subtract(from.toVector()));
            body.setRotation(to.getYaw(), to.getPitch());
        }
        body.setBodyYaw(brain.getBodyYaw());
    }

    /**
     * Muestra a este jugador el cuerpo que puede ver: el maniqui en Java y, en Bedrock, la gallina; con las entidades de
     * Geyser tambien el maniqui, que Geyser traduce como un jugador con skin.
     */
    void showTo(Player player) {
        Entity shown = Bedrock.is(player) && !CaiChetBedrockEntities.available() ? brain : body;
        if (!player.canSee(shown)) {
            player.showEntity(Bedrock.plugin(), shown);
        }
    }

    boolean isValid() {
        return brain.isValid() && body.isValid();
    }

    /** Hay algun jugador a menos de esta distancia (sin contar espectadores)? */
    boolean hasPlayerWithin(double radius) {
        for (Player player : brain.getLocation().getNearbyPlayers(radius)) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                return true;
            }
        }
        return false;
    }

    void remove(boolean poof) {
        BY_ENTITY.remove(brain.getUniqueId());
        BY_ENTITY.remove(body.getUniqueId());
        if (poof && body.isValid()) {
            body.getWorld().spawnParticle(Particle.POOF, body.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
        }
        body.remove();
        if (brain.isValid() && !brain.isDead()) {
            brain.remove();
        }
    }

    static void removeAll() {
        List<Doppelganger> list = new ArrayList<>(all());
        for (Doppelganger doppel : list) {
            doppel.remove(false);
        }
    }
}
