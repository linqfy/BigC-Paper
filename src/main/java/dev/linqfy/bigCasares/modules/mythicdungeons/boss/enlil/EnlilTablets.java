package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The three Tablets of Destiny of phase 2. Each one is an invisible, AI-less small slime (a real hitbox that melee
 * and arrows both hit) carrying the {@code bigcasares_myth_enlil_tablet} model. The encounter may not register
 * listeners, so hits are found by polling: the slime's health is kept at its maximum and a drop is one hit when the
 * last damage came from a player (melee or projectile; fire and poison do not count, and fire is put out every poll;
 * vanilla invulnerability ticks keep one hit per half second). Bedrock players get a brown shulker stand-in
 * (smaller than the slime, so Java arrows still hit the slime first) that forwards its hits to the slime.
 */
final class EnlilTablets {

    static final String MODEL = "bigcasares_myth_enlil_tablet";
    private static final double HEALTH = 1024.0;
    private static final int BREAK_TICKS = 16;

    /** One player hit found by {@link #poll()}. */
    record Hit(int index, Player attacker, Location at) {
    }

    private static final class Tablet {
        private final int index;
        private final Slime anchor;
        private final Location spot;
        private final JavaModelHandle model;
        private int breaking = -1;

        private Tablet(int index, Slime anchor, Location spot, JavaModelHandle model) {
            this.index = index;
            this.anchor = anchor;
            this.spot = spot;
            this.model = model;
        }
    }

    private final MythicBossSupport support;
    private final UUID bossId;
    private final List<Tablet> tablets = new ArrayList<>();
    private boolean missingModelLogged;

    EnlilTablets(MythicBossSupport support, UUID bossId) {
        this.support = support;
        this.bossId = bossId;
    }

    void form(World world, List<double[]> spots, double y) {
        clear();
        for (int index = 0; index < spots.size(); index++) {
            Location spot = new Location(world, spots.get(index)[0], y, spots.get(index)[1]);
            Slime anchor = support.spawnOwned(bossId, spot, Slime.class, slime -> {
                slime.setSize(2);
                slime.setAI(false);
                slime.setGravity(false);
                slime.setSilent(true);
                slime.setInvisible(true);
                slime.setCollidable(false);
                slime.setPersistent(false);
                slime.setRemoveWhenFarAway(false);
                AttributeInstance max = slime.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) {
                    max.setBaseValue(HEALTH);
                }
                AttributeInstance knockback = slime.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
                if (knockback != null) {
                    knockback.setBaseValue(1.0);
                }
                slime.setHealth(HEALTH);
            });
            JavaModelHandle model = null;
            try {
                model = support.models().attach(anchor, MODEL);
                support.models().animateOnce(model, "reform", () -> {
                });
            } catch (RuntimeException | LinkageError failure) {
                // without the model Java players would see nothing: show the slime itself instead
                anchor.setInvisible(false);
                if (!missingModelLogged) {
                    missingModelLogged = true;
                    support.logger().warning("[Mazmorras] Enlil: sin modelo " + MODEL + " (" + failure.getMessage() + ")");
                }
            }
            try {
                Shulker standIn = support.spawnBedrockStandIn(bossId, anchor, Shulker.class, 0.7);
                standIn.setColor(DyeColor.BROWN);
            } catch (RuntimeException failure) {
                support.logger().log(Level.WARNING, "[Mazmorras] Enlil: sin doble de Bedrock para la tablilla", failure);
            }
            tablets.add(new Tablet(index, anchor, spot, model));
            world.spawnParticle(Particle.ENCHANT, spot.clone().add(0.0, 0.6, 0.0), 40, 0.5, 0.6, 0.5, 0.6);
            world.playSound(spot, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.0f, 1.4f);
        }
    }

    /** Hits since the last poll; keeps every tablet at its spot and at full health. */
    List<Hit> poll() {
        List<Hit> hits = new ArrayList<>(1);
        for (Tablet tablet : tablets) {
            if (tablet.breaking >= 0 || !tablet.anchor.isValid()) {
                continue;
            }
            tablet.anchor.setFireTicks(0);
            Optional<Player> attacker = struck(tablet.anchor);
            attacker.ifPresent(player -> hits.add(new Hit(tablet.index, player, tablet.spot)));
            if (tablet.anchor.getLocation().distanceSquared(tablet.spot) > 0.01) {
                tablet.anchor.teleport(tablet.spot);
                tablet.anchor.setVelocity(new org.bukkit.util.Vector());
            }
        }
        return hits;
    }

    /** A tablet that vanished (killed by a command, unloaded) counts as broken so the fight never locks. */
    List<Integer> lost() {
        List<Integer> lost = new ArrayList<>(0);
        for (Tablet tablet : tablets) {
            if (tablet.breaking < 0 && !tablet.anchor.isValid()) {
                tablet.breaking = BREAK_TICKS;
                lost.add(tablet.index);
            }
        }
        return lost;
    }

    void crack(int index, int hitsLeft) {
        Tablet tablet = find(index);
        if (tablet == null) {
            return;
        }
        World world = tablet.spot.getWorld();
        world.spawnParticle(Particle.BLOCK, tablet.spot.clone().add(0.0, 0.7, 0.0), 14, 0.3, 0.4, 0.3, 0.0,
            org.bukkit.Material.MUD_BRICKS.createBlockData());
        world.playSound(tablet.spot, Sound.BLOCK_DECORATED_POT_HIT, SoundCategory.HOSTILE, 1.2f, 0.7f + hitsLeft * 0.08f);
    }

    void shatter(int index) {
        Tablet tablet = find(index);
        if (tablet == null || tablet.breaking >= 0) {
            return;
        }
        tablet.breaking = BREAK_TICKS;
        if (tablet.model != null) {
            try {
                support.models().animate(tablet.model, "break");
            } catch (RuntimeException ignored) {
                // cosmetic
            }
        }
        World world = tablet.spot.getWorld();
        world.spawnParticle(Particle.BLOCK, tablet.spot.clone().add(0.0, 0.7, 0.0), 40, 0.4, 0.5, 0.4, 0.0,
            org.bukkit.Material.MUD_BRICKS.createBlockData());
        world.spawnParticle(Particle.ENCHANT, tablet.spot.clone().add(0.0, 0.7, 0.0), 30, 0.4, 0.5, 0.4, 1.0);
        world.playSound(tablet.spot, Sound.BLOCK_DECORATED_POT_SHATTER, SoundCategory.HOSTILE, 1.6f, 0.8f);
    }

    /** Removes the tablets whose break animation is over; call every tick. */
    void tick() {
        tablets.removeIf(tablet -> {
            if (tablet.breaking < 0) {
                return false;
            }
            if (tablet.breaking-- > 0) {
                return false;
            }
            remove(tablet);
            return true;
        });
    }

    /** Positions of the standing tablets (for the arrow deflection, which must leave them alone). */
    List<Location> standing() {
        List<Location> spots = new ArrayList<>(tablets.size());
        for (Tablet tablet : tablets) {
            if (tablet.breaking < 0) {
                spots.add(tablet.spot);
            }
        }
        return spots;
    }

    void clear() {
        tablets.forEach(this::remove);
        tablets.clear();
    }

    private void remove(Tablet tablet) {
        if (tablet.model != null) {
            try {
                support.models().close(tablet.model);
            } catch (RuntimeException ignored) {
                // already gone
            }
        }
        if (tablet.anchor.isValid()) {
            tablet.anchor.remove();
        }
        // the Bedrock stand-in removes itself when its anchor is gone (BedrockStandIns#tick)
    }

    private Tablet find(int index) {
        for (Tablet tablet : tablets) {
            if (tablet.index == index) {
                return tablet;
            }
        }
        return null;
    }

    /**
     * The player whose attack took health off {@code anchor} since the last poll, if any; the health always goes back
     * to the maximum. Only a direct player attack or a player's projectile counts: fire, poison, explosions or a
     * mob do not break a tablet.
     */
    static Optional<Player> struck(LivingEntity anchor) {
        if (anchor.getHealth() >= HEALTH - 0.01) {
            return Optional.empty();
        }
        anchor.setHealth(HEALTH);
        return Optional.ofNullable(attacker(anchor.getLastDamageCause()));
    }

    private static Player attacker(EntityDamageEvent cause) {
        if (!(cause instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player ? player : null;
        }
        return damager instanceof Player player ? player : null;
    }
}
