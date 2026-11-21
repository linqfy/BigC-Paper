package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * Las nubes electricas de la fase 2. Cada nube es un zombie invisible (la IA y el golpe son los de un zombie) con
 * el modelo {@code bigcasares_myth_zeus_cloud} encima y un Vex de doble para Bedrock. Su vida es la de
 * {@link ZeusCloudRoster} (colchon vanilla, ver alli). El golpe de una nube se detecta sin listeners: cada tick se
 * mira el {@code getLastDamageCause()} de cada jugador y, si es un golpe nuevo de una nube, se avisa.
 */
final class ZeusClouds {

    static final String MODEL = "bigcasares_myth_zeus_cloud";
    private static final int DEATH_TICKS = 16;

    private static final class Cloud {
        final Zombie body;
        final JavaModelHandle model;
        boolean walking;
        int dyingSince = -1;

        Cloud(Zombie body, JavaModelHandle model) {
            this.body = body;
            this.model = model;
        }
    }

    private final MythicBossSupport support;
    private final UUID bossId;
    private final RandomGenerator random;
    private final Map<UUID, Cloud> clouds = new LinkedHashMap<>();
    /** Ultimo evento de dano visto por jugador (por identidad: cada golpe es un evento nuevo). */
    private final Map<UUID, EntityDamageEvent> lastSeen = new HashMap<>();
    private boolean warnedModel;

    ZeusClouds(MythicBossSupport support, UUID bossId, RandomGenerator random) {
        this.support = support;
        this.bossId = bossId;
        this.random = random;
    }

    int alive() {
        int count = 0;
        for (Cloud cloud : clouds.values()) {
            if (cloud.dyingSince < 0 && cloud.body.isValid()) {
                count++;
            }
        }
        return count;
    }

    /** Crea una nube en una baldosa libre, lejos de los jugadores. */
    void spawn(ZeusArena arena, List<Player> players, Location zeus) {
        List<ZeusCell> cells = arena.cells();
        if (cells.isEmpty()) {
            return;
        }
        Location at = null;
        for (int attempt = 0; attempt < 12 && at == null; attempt++) {
            Location candidate = arena.cellCenter(cells.get(random.nextInt(cells.size())), 0.0);
            boolean clear = candidate.distanceSquared(zeus) >= 9.0;
            for (Player player : players) {
                if (player.getWorld().equals(candidate.getWorld())
                    && player.getLocation().distanceSquared(candidate) < 36.0) {
                    clear = false;
                }
            }
            if (clear || attempt == 11) {
                at = candidate;
            }
        }
        Zombie body = support.spawnOwned(bossId, at, Zombie.class, zombie -> {
            zombie.setAdult();
            zombie.setShouldBurnInDay(false);
            zombie.setCanBreakDoors(false);
            zombie.setCanPickupItems(false);
            zombie.setInvisible(true);
            zombie.setSilent(true);
            zombie.setRemoveWhenFarAway(false);
            zombie.setLootTable(null);
            zombie.customName(Component.text("Nube eléctrica"));
            zombie.setCustomNameVisible(false);
            clearEquipment(zombie);
            base(zombie, Attribute.MAX_HEALTH, ZeusCloudRoster.BUFFER_HEALTH);
            base(zombie, Attribute.ATTACK_DAMAGE, ZeusCloudRoster.CLOUD_ATTACK);
            base(zombie, Attribute.MOVEMENT_SPEED, 0.25);
            base(zombie, Attribute.FOLLOW_RANGE, 40.0);
            base(zombie, Attribute.SPAWN_REINFORCEMENTS, 0.0);
            zombie.setHealth(ZeusCloudRoster.BUFFER_HEALTH);
        });
        JavaModelHandle model = null;
        try {
            model = support.models().attach(body, MODEL);
            support.models().animate(model, "idle");
        } catch (RuntimeException | LinkageError missing) {
            if (!warnedModel) {
                warnedModel = true;
                support.logger().warning("[Zeus] Sin modelo " + MODEL + " (" + missing.getMessage()
                    + "): las nubes se ven solo como particulas en Java.");
            }
        }
        try {
            Vex standIn = support.spawnBedrockStandIn(bossId, body, Vex.class, 1.2);
            clearEquipment(standIn);
        } catch (RuntimeException unavailable) {
            // sin dobles de Bedrock (modulo sin Geyser): Bedrock ve solo las particulas
        }
        Player nearest = nearest(players, at);
        if (nearest != null) {
            body.setTarget(nearest);
        }
        clouds.put(body.getUniqueId(), new Cloud(body, model));
        at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 1.2, 0), 40, 0.6, 0.6, 0.6, 0.2);
        at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 1.4, 0), 14, 0.5, 0.3, 0.5, 0.02);
        at.getWorld().playSound(at, Sound.ENTITY_BREEZE_WIND_BURST, SoundCategory.HOSTILE, 1.0F, 1.4F);
    }

    /**
     * Un tick: chispas, animaciones, muertes por vida virtual y golpes nuevos de nubes a jugadores.
     *
     * @param players solo los jugadores adentro de la sala: a otros no se los persigue ni se los electrocuta
     * @param onShock se llama con cada jugador recien golpeado por una nube
     */
    void tick(int tick, List<Player> players, Consumer<Player> onShock) {
        Iterator<Map.Entry<UUID, Cloud>> iterator = clouds.entrySet().iterator();
        while (iterator.hasNext()) {
            Cloud cloud = iterator.next().getValue();
            Zombie body = cloud.body;
            if (!body.isValid()) {
                close(cloud);
                iterator.remove();
                continue;
            }
            if (cloud.dyingSince >= 0) {
                if (tick - cloud.dyingSince >= DEATH_TICKS) {
                    close(cloud);
                    body.remove();
                    iterator.remove();
                }
                continue;
            }
            if (ZeusCloudRoster.remaining(body.getHealth()) <= 0.0) {
                die(cloud, tick);
                continue;
            }
            Location at = body.getLocation();
            if (tick % 6 == 0) {
                at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 1.3, 0), 3, 0.5, 0.35, 0.5, 0.05);
            }
            if (tick % 10 == 0) {
                boolean walking = body.getVelocity().setY(0).lengthSquared() > 0.0004;
                if (walking != cloud.walking && cloud.model != null) {
                    cloud.walking = walking;
                    if (!walking) {
                        support.models().stopAnimation(cloud.model, "walk");
                    }
                    support.models().animate(cloud.model, walking ? "walk" : "idle");
                }
                // solo persiguen a jugadores adentro de la sala (la IA vanilla podria elegir a uno del pasillo)
                if (!(body.getTarget() instanceof Player target) || !players.contains(target)) {
                    body.setTarget(nearest(players, at));
                }
            }
            if (random.nextInt(80) == 0) {
                at.getWorld().playSound(at, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.HOSTILE, 0.6F, 1.9F);
            }
        }
        for (Player player : players) {
            EntityDamageEvent last = player.getLastDamageCause();
            if (last == null || lastSeen.get(player.getUniqueId()) == last) {
                continue;
            }
            lastSeen.put(player.getUniqueId(), last);
            if (last instanceof EntityDamageByEntityEvent byEntity) {
                Cloud cloud = clouds.get(byEntity.getDamager().getUniqueId());
                if (cloud != null && cloud.dyingSince < 0) {
                    if (cloud.model != null) {
                        support.models().animateOnce(cloud.model, "attack", () -> {
                        });
                    }
                    onShock.accept(player);
                }
            }
        }
    }

    /** Cierra los modelos; las entidades las borra {@link MythicBossSupport#cleanup}. */
    void close() {
        for (Cloud cloud : clouds.values()) {
            close(cloud);
        }
        clouds.clear();
        lastSeen.clear();
    }

    private void die(Cloud cloud, int tick) {
        cloud.dyingSince = tick;
        Zombie body = cloud.body;
        body.setAI(false);
        body.setInvulnerable(true);
        if (cloud.model != null) {
            support.models().animateOnce(cloud.model, "death", () -> {
            });
        }
        Location at = body.getLocation().add(0, 1.2, 0);
        at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 50, 0.7, 0.5, 0.7, 0.3);
        at.getWorld().spawnParticle(Particle.CLOUD, at, 20, 0.6, 0.4, 0.6, 0.05);
        at.getWorld().playSound(at, Sound.ENTITY_BREEZE_WIND_BURST, SoundCategory.HOSTILE, 1.0F, 1.8F);
    }

    private void close(Cloud cloud) {
        if (cloud.model == null) {
            return;
        }
        try {
            support.models().close(cloud.model);
        } catch (RuntimeException ignored) {
            // cosmetico
        }
    }

    private static Player nearest(List<Player> players, Location at) {
        Player best = null;
        double distance = Double.MAX_VALUE;
        for (Player player : players) {
            if (!player.getWorld().equals(at.getWorld())) {
                continue;
            }
            double squared = player.getLocation().distanceSquared(at);
            if (squared < distance) {
                distance = squared;
                best = player;
            }
        }
        return best;
    }

    private static void clearEquipment(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            equipment.clear();
        }
    }

    private static void base(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
