package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.Optional;
import java.util.UUID;

/**
 * La cancha en el servidor: el aro de piedra (un soporte invisible con el modelo {@link #HOOP_MODEL}) y la pelota de
 * hule, que es una entidad de verdad para poder pegarle: un slime grande invisible y sin IA con el modelo
 * {@link #BALL_MODEL}. Los golpes a la pelota se encuentran mirando su vida (se mantiene al maximo); el encuentro no
 * puede registrar listeners. Nunca cambia bloques.
 *
 * <p>Bedrock: no ve los modelos. La pelota tiene un doble vanilla (un cubo de magma del mismo tamaño) que le pasa los
 * golpes al slime; el aro se ve como un anillo de particulas del radio real de la abertura (que tambien ven los de
 * Java, para que la punteria sea justa en las dos versiones).</p>
 */
final class CameCourt {

    static final String HOOP_MODEL = "bigcasares_myth_came_hoop";
    static final String BALL_MODEL = "bigcasares_myth_came_ball";
    /** Altura del centro del aro sobre el soporte (centro del anillo del modelo: 48 px). */
    static final double HOOP_MODEL_CENTER = 3.0;
    private static final int BALL_SIZE = 3;
    /** Del pie del slime al centro de la pelota (hitbox 0.52 * 3 = 1.56). */
    static final double BALL_CENTER = 0.78;
    private static final double BALL_HEALTH = 1024.0;
    private static final Particle.DustOptions RING = new Particle.DustOptions(Color.fromRGB(60, 220, 160), 1.3F);
    private static final Particle.DustOptions RING_STONE = new Particle.DustOptions(Color.fromRGB(150, 140, 120), 1.6F);
    private static final Particle.DustOptions TRAIL = new Particle.DustOptions(Color.fromRGB(40, 30, 26), 1.4F);
    private static final Particle.DustOptions TRAIL_RETURN = new Particle.DustOptions(Color.fromRGB(255, 210, 70), 1.4F);

    /** Un golpe a la pelota: quien y hacia donde. */
    record BallHit(Player striker, CameVec look) {
    }

    private final MythicBossSupport support;
    private final UUID bossId;
    private ArmorStand hoopAnchor;
    private JavaModelHandle hoopModel;
    private CameHoop hoop;
    private Slime ball;
    private JavaModelHandle ballModel;
    private CameBall flight;
    private String hoopLoop;

    CameCourt(MythicBossSupport support, UUID bossId) {
        this.support = support;
        this.bossId = bossId;
    }

    // ------------------------------------------------------------------ hoop

    void raiseHoop(Location center, Vector normal) {
        hoop = new CameHoop(vec(center), new CameVec(normal.getX(), 0.0, normal.getZ()));
        Location base = center.clone().subtract(0.0, HOOP_MODEL_CENTER, 0.0);
        base.setDirection(new Vector(normal.getX(), 0.0, normal.getZ()));
        hoopAnchor = support.spawnOwned(bossId, base, ArmorStand.class, CameCourt::marker);
        try {
            hoopModel = support.models().attach(hoopAnchor, HOOP_MODEL);
            hoopLoop("idle");
        } catch (RuntimeException | LinkageError missing) {
            support.logger().warning("[Camé] Sin modelo " + HOOP_MODEL + ": " + missing.getMessage());
        }
        World world = center.getWorld();
        world.spawnParticle(Particle.BLOCK, center, 60, 1.5, 1.5, 0.4, 0.1, Material.MOSSY_STONE_BRICKS.createBlockData());
        world.playSound(center, Sound.BLOCK_STONE_PLACE, SoundCategory.HOSTILE, 2.0F, 0.5F);
    }

    CameHoop hoop() {
        return hoop;
    }

    /** El aro se achica (modelo) despues de {@code goals} goles. */
    void narrow(int goals) {
        if (hoopModel == null) {
            return;
        }
        try {
            support.models().animateOnce(hoopModel, "goal", () -> {
            });
        } catch (RuntimeException ignored) {
            // cosmetico
        }
        hoopLoop(goals >= 2 ? "narrow_2" : goals == 1 ? "narrow_1" : "idle");
    }

    /** El anillo de particulas: el radio de verdad de la abertura (lo que cuenta para el gol). */
    void drawRing(int goals) {
        if (hoop == null || hoopAnchor == null || !hoopAnchor.isValid()) {
            return;
        }
        World world = hoopAnchor.getWorld();
        CameVec normal = hoop.normal();
        CameVec right = normal.perpendicular();
        CameVec up = new CameVec(0.0, 1.0, 0.0);
        double radius = CameHoop.opening(goals);
        int points = 28;
        for (int index = 0; index < points; index++) {
            double angle = index * Math.PI * 2.0 / points;
            CameVec at = hoop.center().add(right.multiply(Math.cos(angle) * radius))
                .add(up.multiply(Math.sin(angle) * radius));
            world.spawnParticle(Particle.DUST, at.x(), at.y(), at.z(), 1, 0, 0, 0, 0, RING);
            if (index % 4 == 0) {
                CameVec outer = hoop.center().add(right.multiply(Math.cos(angle) * 2.9))
                    .add(up.multiply(Math.sin(angle) * 2.9));
                world.spawnParticle(Particle.DUST, outer.x(), outer.y(), outer.z(), 1, 0, 0, 0, 0, RING_STONE);
            }
        }
    }

    void goalBurst() {
        if (hoop == null) {
            return;
        }
        World world = hoopAnchor.getWorld();
        CameVec center = hoop.center();
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center.x(), center.y(), center.z(), 80, 1.0, 1.0, 1.0, 0.4);
        world.spawnParticle(Particle.FIREWORK, center.x(), center.y(), center.z(), 40, 0.6, 0.6, 0.6, 0.15);
        Location at = new Location(world, center.x(), center.y(), center.z());
        world.playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 3.0F, 0.6F);
        world.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.HOSTILE, 1.6F, 1.2F);
    }

    /** Fin de la fase 1: el aro se rompe y desaparece. */
    void shatterHoop() {
        if (hoopAnchor != null && hoopAnchor.isValid()) {
            Location center = hoopAnchor.getLocation().add(0.0, HOOP_MODEL_CENTER, 0.0);
            World world = center.getWorld();
            world.spawnParticle(Particle.BLOCK, center, 120, 2.0, 2.0, 0.6, 0.2,
                Material.MOSSY_STONE_BRICKS.createBlockData());
            world.spawnParticle(Particle.EXPLOSION, center, 3, 1.0, 1.0, 0.4, 0.0);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0F, 0.6F);
            world.playSound(center, Sound.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 2.0F, 0.5F);
        }
        closeHoop();
    }

    // ------------------------------------------------------------------ ball

    /** Aparece la pelota (en la mano del que la tira). */
    void spawnBall(CameVec at, World world) {
        removeBall(false);
        flight = new CameBall(at);
        Location feet = new Location(world, at.x(), at.y() - BALL_CENTER, at.z());
        ball = support.spawnOwned(bossId, feet, Slime.class, slime -> {
            slime.setSize(BALL_SIZE);
            slime.setAI(false);
            slime.setGravity(false);
            slime.setSilent(true);
            slime.setInvisible(true);
            slime.setCollidable(false);
            slime.setPersistent(false);
            slime.setRemoveWhenFarAway(false);
            slime.setLootTable(null);
            slime.customName(Component.text("Pelota de hule"));
            slime.setCustomNameVisible(false);
            AttributeInstance max = slime.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(BALL_HEALTH);
            }
            AttributeInstance knockback = slime.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (knockback != null) {
                knockback.setBaseValue(1.0);
            }
            slime.setHealth(BALL_HEALTH);
        });
        try {
            ballModel = support.models().attach(ball, BALL_MODEL);
            support.models().animate(ballModel, "idle");
        } catch (RuntimeException | LinkageError missing) {
            ball.setInvisible(false);
            support.logger().warning("[Camé] Sin modelo " + BALL_MODEL + ": " + missing.getMessage());
        }
        try {
            MagmaCube standIn = support.spawnBedrockStandIn(bossId, ball, MagmaCube.class, 1.0);
            standIn.setSize(BALL_SIZE);
        } catch (RuntimeException unavailable) {
            // sin doble: los de Bedrock ven el slime invisible (solo las particulas de la estela)
        }
    }

    Optional<CameBall> flight() {
        return Optional.ofNullable(ballAlive() ? flight : null);
    }

    boolean ballAlive() {
        return ball != null && ball.isValid() && flight != null;
    }

    void spin() {
        if (ballModel != null) {
            try {
                support.models().stopAnimation(ballModel, "idle");
                support.models().animate(ballModel, "spin");
            } catch (RuntimeException ignored) {
                // cosmetico
            }
        }
    }

    /** Lleva el slime adonde dicen las reglas y deja la estela. */
    void syncBall() {
        if (!ballAlive()) {
            return;
        }
        CameVec at = flight.position();
        Location feet = new Location(ball.getWorld(), at.x(), at.y() - BALL_CENTER, at.z(),
            ball.getLocation().getYaw() + 18.0F, 0.0F);
        ball.teleport(feet);
        ball.setVelocity(new Vector());
        if (flight.mode() != CameBall.Mode.HELD) {
            ball.getWorld().spawnParticle(Particle.DUST, at.x(), at.y(), at.z(), 2, 0.2, 0.2, 0.2, 0.0,
                flight.mode() == CameBall.Mode.RETURNING ? TRAIL_RETURN : TRAIL);
        }
    }

    /** Un golpe de un jugador a la pelota desde el ultimo llamado (deja la vida al maximo). */
    Optional<BallHit> pollBallHit() {
        if (!ballAlive()) {
            return Optional.empty();
        }
        if (ball.getHealth() >= BALL_HEALTH - 0.01) {
            return Optional.empty();
        }
        EntityDamageEvent cause = ball.getLastDamageCause();
        ball.setHealth(BALL_HEALTH);
        if (!(cause instanceof EntityDamageByEntityEvent byEntity)) {
            return Optional.empty();
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Player player) {
            Vector look = player.getEyeLocation().getDirection();
            return Optional.of(new BallHit(player, new CameVec(look.getX(), look.getY(), look.getZ())));
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            // una flecha tambien la devuelve, en la direccion en que venia
            Vector velocity = projectile.getVelocity();
            return Optional.of(new BallHit(shooter, new CameVec(velocity.getX(), velocity.getY(), velocity.getZ())));
        }
        return Optional.empty();
    }

    void removeBall(boolean pop) {
        if (ballModel != null) {
            try {
                support.models().close(ballModel);
            } catch (RuntimeException ignored) {
                // ya cerrado
            }
            ballModel = null;
        }
        if (ball != null && ball.isValid()) {
            if (pop) {
                Location at = ball.getLocation().add(0.0, BALL_CENTER, 0.0);
                at.getWorld().spawnParticle(Particle.BLOCK, at, 30, 0.5, 0.5, 0.5, 0.1,
                    Material.BLACK_CONCRETE_POWDER.createBlockData());
                at.getWorld().playSound(at, Sound.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 1.4F, 0.5F);
            }
            ball.remove();
        }
        ball = null;
        flight = null;
    }

    void close() {
        removeBall(false);
        closeHoop();
    }

    private void closeHoop() {
        if (hoopModel != null) {
            try {
                support.models().close(hoopModel);
            } catch (RuntimeException ignored) {
                // ya cerrado
            }
            hoopModel = null;
        }
        if (hoopAnchor != null && hoopAnchor.isValid()) {
            hoopAnchor.remove();
        }
        hoopAnchor = null;
    }

    private void hoopLoop(String animation) {
        if (hoopModel == null || animation.equals(hoopLoop)) {
            return;
        }
        try {
            if (hoopLoop != null) {
                support.models().stopAnimation(hoopModel, hoopLoop);
            }
            support.models().animate(hoopModel, animation);
        } catch (RuntimeException ignored) {
            // cosmetico
        }
        hoopLoop = animation;
    }

    private static void marker(ArmorStand stand) {
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setPersistent(false);
        stand.setBasePlate(false);
    }

    static CameVec vec(Location at) {
        return new CameVec(at.getX(), at.getY(), at.getZ());
    }
}
