package dev.linqfy.bigCasares.modules.mutango;

import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.ModelRotation;
import kr.toxicity.model.api.tracker.ModelScaler;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * El Sol y la Luna de Neguechen: dos modelos achicados que giran arriba de su cabeza, salen volando hacia un jugador
 * (cada uno calcula su trayectoria en el momento en que sale y la sigue sin corregir) y se clavan en el piso. Se manejan
 * por la posicion de su centro. Cada pelea tiene el par suyo.
 */
final class MutangoSunMoon {

    private static final double ORBIT_RADIUS = 5.0;
    /** Altura del centro del Sol y la Luna sobre los pies de Neguechen (un poco arriba del gorro). */
    private static final double ORBIT_HEIGHT = 8.6;
    /** Cuanto suben y bajan el Sol y la Luna mientras giran. */
    private static final double ORBIT_WAVE = 0.7;
    private static final int ORBIT_PERIOD_TICKS = 100;
    private static final double FLIGHT_SPEED = 1.3;
    private static final double FLIGHT_STEP = 0.4;
    private static final int FLIGHT_MAX_TICKS = 80;
    private static final int RETURN_TICKS = 20;
    private static final double ORB_DAMAGE = 10.0;
    private static final double ORB_KNOCKBACK = 1.0;
    private static final double ORB_LIFT = 0.6;
    private static final Particle.DustOptions MOON_DUST = new Particle.DustOptions(Color.fromRGB(190, 210, 255), 1.3f);

    private enum OrbState { ORBIT, FLYING, STUCK, RETURNING }

    /** El Sol o la Luna. */
    private static final class Orb {
        final String model;
        final float scale;
        /** Del piso del modelo a su centro, en bloques, ya achicado. */
        final double centerHeight;
        final double hitRadius;
        final double angleOffset;
        UUID id;
        OrbState state = OrbState.ORBIT;
        Location center;
        Location returnFrom;
        Vector velocity;
        int ticks;
        boolean hit;
        float yaw;
        float pitch;

        Orb(String model, float scale, double centerPixels, double hitRadius, double angleOffset) {
            this.model = model;
            this.scale = scale;
            this.centerHeight = centerPixels / 16.0 * scale;
            this.hitRadius = hitRadius;
            this.angleOffset = angleOffset;
        }
    }

    private final MutangoPhaseThree.Host host;
    private final MutangoDifficulty difficulty;
    private final Orb sun = new Orb(MutangoPhaseThree.SUN, 0.6f, 48, 1.5, 0);
    private final Orb moon = new Orb(MutangoPhaseThree.MOON, 0.7f, 32, 1.2, Math.PI);
    private final List<Orb> orbs = List.of(sun, moon);
    /** Lo que se sabe de Neguechen en este tick: donde flota (el centro de las vueltas), el tick y quien es. */
    private Location home;
    private int ticks;
    private UUID boss;

    MutangoSunMoon(MutangoPhaseThree.Host host, MutangoDifficulty difficulty) {
        this.host = host;
        this.difficulty = difficulty;
    }

    boolean owns(UUID id) {
        return id.equals(sun.id) || id.equals(moon.id);
    }

    /** Los dos estan girando (listos para otro ataque). */
    boolean orbiting() {
        return sun.state == OrbState.ORBIT && moon.state == OrbState.ORBIT;
    }

    /** Aparecen girando arriba de Neguechen. */
    void spawn(Location home, int ticks, UUID boss) {
        sync(home, ticks, boss);
        for (Orb orb : orbs) {
            orb.state = OrbState.ORBIT;
            orb.center = orbitCenter(orb, ticks);
            Mob entity = host.spawn(orb.model, feet(orb, orb.center), 0);
            orb.id = entity.getUniqueId();
            EntityTracker tracker = host.tracker(orb.id);
            if (tracker != null) {
                tracker.scaler(ModelScaler.value(orb.scale));
                tracker.rotation(() -> new ModelRotation(orb.pitch, orb.yaw));
            }
        }
    }

    /** Primer ataque: sale el Sol hacia ese jugador. */
    void launchSun(Player target) {
        launch(sun, target);
    }

    /** La Luna sale un rato despues; sin nadie a quien tirarle se queda quieta hasta que vuelvan. */
    void launchMoon(Player target) {
        if (target != null) {
            launch(moon, target);
        } else {
            moon.state = OrbState.STUCK;
        }
    }

    /** Se desclavan y vuelven a girar. */
    void recall() {
        for (Orb orb : orbs) {
            if (orb.state != OrbState.ORBIT) {
                orb.state = OrbState.RETURNING;
                orb.returnFrom = orb.center.clone();
                orb.ticks = 0;
            }
        }
    }

    /** Se van el Sol y la Luna (en un destello). */
    void vanish() {
        for (Orb orb : orbs) {
            if (orb.id != null && Bukkit.getEntity(orb.id) instanceof Mob mob) {
                mob.getWorld().spawnParticle(Particle.END_ROD, mob.getLocation().add(0, orb.centerHeight, 0), 40,
                    0.6, 0.6, 0.6, 0.08);
                mob.remove();
            }
            orb.id = null;
        }
    }

    /** Se sacan sin destello (se corto la pelea). */
    void discard() {
        for (Orb orb : orbs) {
            if (orb.id != null && Bukkit.getEntity(orb.id) instanceof Mob mob) {
                mob.remove();
            }
            orb.id = null;
        }
    }

    /** Un tick de cada uno. onBack corre cuando uno termina de volver a girar. */
    void tick(Location home, int ticks, UUID boss, Player target, Runnable onBack) {
        sync(home, ticks, boss);
        for (Orb orb : orbs) {
            tickOrb(orb, target, onBack);
        }
    }

    private void sync(Location home, int ticks, UUID boss) {
        this.home = home;
        this.ticks = ticks;
        this.boss = boss;
    }

    private Location orbitCenter(Orb orb, int tick) {
        double angle = tick * 2 * Math.PI / ORBIT_PERIOD_TICKS + orb.angleOffset;
        return home.clone().add(Math.cos(angle) * ORBIT_RADIUS, ORBIT_HEIGHT + Math.sin(angle * 2) * ORBIT_WAVE,
            Math.sin(angle) * ORBIT_RADIUS);
    }

    private static Location feet(Orb orb, Location center) {
        return center.clone().subtract(0, orb.centerHeight, 0);
    }

    /** Sale hacia donde esta el jugador ahora (a la altura de su pecho) y sigue esa trayectoria sin corregir. */
    private void launch(Orb orb, Player target) {
        Vector aim = target.getLocation().add(0, 1.0, 0).toVector().subtract(orb.center.toVector());
        if (aim.lengthSquared() < 1e-4) {
            aim = new Vector(0, -1, 0);
        }
        orb.velocity = aim.normalize().multiply(FLIGHT_SPEED);
        orb.state = OrbState.FLYING;
        orb.ticks = 0;
        orb.hit = false;
        World world = orb.center.getWorld();
        world.playSound(orb.center, orb == sun ? Sound.ENTITY_BLAZE_SHOOT : Sound.ENTITY_PHANTOM_SWOOP, 2f, 0.6f);
        if (orb == sun) {
            world.spawnParticle(Particle.FLAME, orb.center, 40, 0.8, 0.8, 0.8, 0.12);
        } else {
            world.spawnParticle(Particle.END_ROD, orb.center, 30, 0.6, 0.6, 0.6, 0.1);
            world.spawnParticle(Particle.SNOWFLAKE, orb.center, 25, 0.6, 0.6, 0.6, 0.08);
        }
    }

    private void tickOrb(Orb orb, Player target, Runnable onBack) {
        if (orb.id == null || !(Bukkit.getEntity(orb.id) instanceof Mob entity)) {
            return;
        }
        entity.setAware(false);
        entity.setGravity(false);
        entity.setCollidable(false);
        switch (orb.state) {
            case ORBIT -> {
                orb.center = orbitCenter(orb, ticks);
                face(orb, target == null ? null : target.getEyeLocation().toVector().subtract(orb.center.toVector()));
            }
            case FLYING -> fly(orb);
            case STUCK -> {
            }
            case RETURNING -> {
                orb.ticks++;
                double s = Math.min(1.0, orb.ticks / (double) RETURN_TICKS);
                Location to = orbitCenter(orb, ticks);
                orb.center = orb.returnFrom.clone().add(to.clone().subtract(orb.returnFrom).toVector().multiply(s))
                    .add(0, 4 * 2.0 * s * (1 - s), 0);
                orb.pitch = 0;
                if (s >= 1.0) {
                    orb.state = OrbState.ORBIT;
                    onBack.run();
                }
            }
        }
        trail(orb);
        Location at = feet(orb, orb.center);
        at.setYaw(orb.yaw);
        entity.teleport(at);
    }

    /** Un tick de vuelo en pasos chicos: te puede pegar (una vez) y se clava cuando llega al piso o a un bloque. */
    private void fly(Orb orb) {
        orb.ticks++;
        face(orb, orb.velocity);
        int steps = Math.max(1, (int) Math.ceil(orb.velocity.length() / FLIGHT_STEP));
        Vector step = orb.velocity.clone().multiply(1.0 / steps);
        for (int i = 0; i < steps; i++) {
            Location next = orb.center.clone().add(step);
            if (!next.getBlock().isPassable()) {
                stick(orb, next);
                return;
            }
            orb.center = next;
            if (!orb.hit) {
                BoundingBox body = BoundingBox.of(next, orb.hitRadius, orb.hitRadius, orb.hitRadius);
                for (Player player : next.getWorld().getPlayers()) {
                    if (MutangoTargets.targetable(player) && player.getBoundingBox().overlaps(body)) {
                        orb.hit = true;
                        hit(orb, player);
                        break;
                    }
                }
            }
        }
        if (orb.ticks >= FLIGHT_MAX_TICKS) {
            orb.state = OrbState.STUCK;
        }
    }

    private void hit(Orb orb, Player player) {
        // pega el Sol o la Luna (si ya no estan, Neguechen): asi el mensaje de muerte sabe quien fue
        Entity attacker = orb.id == null ? null : Bukkit.getEntity(orb.id);
        if (attacker == null && boss != null) {
            attacker = Bukkit.getEntity(boss);
        }
        MutangoDamage.hurt(player, difficulty.damage(ORB_DAMAGE), attacker);
        Vector push = orb.velocity.clone().setY(0);
        push = push.lengthSquared() > 1e-4 ? push.normalize().multiply(ORB_KNOCKBACK) : new Vector();
        MutangoDamage.push(player, push.setY(ORB_LIFT));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.4f);
        world.spawnParticle(orb == sun ? Particle.FLAME : Particle.END_ROD, player.getLocation().add(0, 1, 0), 30,
            0.4, 0.6, 0.4, 0.08);
    }

    /** Queda clavado: con el centro justo en la superficie, medio enterrado, y una explosion chiquita. */
    private void stick(Orb orb, Location into) {
        orb.state = OrbState.STUCK;
        orb.center = into;
        World world = into.getWorld();
        world.playSound(into, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.7f);
        world.playSound(into, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION, into, 2, 0.3, 0.1, 0.3, 0);
        world.spawnParticle(Particle.BLOCK, into, 40, 0.8, 0.2, 0.8, 0.1, into.getBlock().getBlockData());
    }

    /** Hacia donde mira el modelo: la cara (adelante) en esa direccion, inclinandose si va para arriba o abajo. */
    private static void face(Orb orb, Vector direction) {
        if (direction == null || direction.lengthSquared() < 1e-6) {
            return;
        }
        Vector horizontal = direction.clone().setY(0);
        if (horizontal.lengthSquared() > 1e-6) {
            orb.yaw = MutangoTargets.yaw(horizontal);
        }
        double flat = Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ());
        orb.pitch = (float) Math.toDegrees(Math.atan2(-direction.getY(), flat));
    }

    /** Estelas del Sol (fuego) y la Luna (nieve y destellos), mas fuertes volando; clavados, se queman o escarchan. */
    private void trail(Orb orb) {
        World world = orb.center.getWorld();
        boolean isSun = orb == sun;
        switch (orb.state) {
            case ORBIT, RETURNING -> {
                if (isSun) {
                    world.spawnParticle(Particle.FLAME, orb.center, 2, 0.6, 0.6, 0.2, 0.01);
                } else {
                    world.spawnParticle(Particle.DUST, orb.center, 2, 0.6, 0.6, 0.2, 0, MOON_DUST);
                    if (ticks % 4 == 0) {
                        world.spawnParticle(Particle.END_ROD, orb.center, 1, 0.5, 0.5, 0.2, 0.01);
                    }
                }
            }
            case FLYING -> {
                if (isSun) {
                    world.spawnParticle(Particle.FLAME, orb.center, 8, 0.5, 0.5, 0.5, 0.03);
                    world.spawnParticle(Particle.LARGE_SMOKE, orb.center, 2, 0.4, 0.4, 0.4, 0.01);
                } else {
                    world.spawnParticle(Particle.SNOWFLAKE, orb.center, 6, 0.4, 0.4, 0.4, 0.02);
                    world.spawnParticle(Particle.END_ROD, orb.center, 2, 0.3, 0.3, 0.3, 0.02);
                    world.spawnParticle(Particle.DUST, orb.center, 3, 0.4, 0.4, 0.4, 0, MOON_DUST);
                }
            }
            case STUCK -> {
                if (ticks % 2 == 0) {
                    if (isSun) {
                        world.spawnParticle(Particle.FLAME, orb.center, 3, 0.7, 0.3, 0.7, 0.03);
                        world.spawnParticle(Particle.SMOKE, orb.center.clone().add(0, 0.5, 0), 2, 0.5, 0.2, 0.5,
                            0.02);
                    } else {
                        world.spawnParticle(Particle.SNOWFLAKE, orb.center, 3, 0.6, 0.3, 0.6, 0.02);
                        world.spawnParticle(Particle.DUST, orb.center, 2, 0.6, 0.3, 0.6, 0, MOON_DUST);
                    }
                }
            }
        }
    }
}
