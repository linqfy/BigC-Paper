package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterContext;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterController;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Breeze;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

/**
 * Bukkit glue of the Enlil encounter. Decisions live in {@link EnlilSkyCycle} (phase 1), {@link EnlilTabletCycle}
 * (phase 2), {@link EnlilRules}, {@link EnlilWind} and {@link EnlilFloodPlanner}; this class reads the world, moves the
 * invisible Warden by teleport every tick (gravity off) and shows the effects. Everything runs in one 1-tick task of
 * {@link MythicBossSupport#scheduler()} that {@link #stop()} cancels.
 *
 * <p>Bedrock: a scaled Breeze stand-in shows Enlil and brown shulkers show the tablets; tornadoes, gusts, rings and
 * the water are vanilla, so Bedrock sees the same fight. Cues go through titles, the action bar, particles and
 * sounds. A Bedrock arrow that reaches the Breeze stand-in is forwarded to the Warden as a projectile, so
 * {@link #blocksProjectile} stops it like a Java arrow; a shulker forwards arrows to its tablet, where they count.
 * Only players inside the measured arena are targeted ({@link #fighters}): the audience radius reaches corridors
 * behind the walls.</p>
 */
final class EnlilFight implements BossEncounterController {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Particle.DustOptions LANDING = new Particle.DustOptions(Color.fromRGB(240, 240, 255), 1.6f);
    private static final double STAND_IN_SCALE = 2.2;
    private static final double DEFLECT_RADIUS = 4.0;
    private static final double DRIFT_SPEED = 0.08;
    private static final double SHIELD_GLIDE = 0.15;
    private static final int HINT_COOLDOWN_TICKS = 40;

    private final MythicBossSupport support;
    private final BossEncounterContext context;
    private final UUID bossId;
    private final RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
    private final EnlilSkyCycle sky = new EnlilSkyCycle();
    private final Set<UUID> deflected = new HashSet<>();
    private final Map<UUID, Long> hints = new HashMap<>();

    private EnlilArena arena;
    private EnlilTabletCycle tablets;
    private EnlilTablets tabletEntities;
    private EnlilTornadoes tornadoes;
    private EnlilFlood flood;
    private BukkitTask task;
    private int phase = 1;
    private long tick;
    private long phaseTwoTick;
    private double x;
    private double z;
    private double targetX;
    private double targetZ;
    private boolean walking;
    private boolean started;
    private boolean stopped;

    EnlilFight(MythicBossSupport support, BossEncounterContext context) {
        this.support = support;
        this.context = context;
        this.bossId = context.bossInstanceId();
    }

    // ------------------------------------------------------------------------------------------------ lifecycle

    @Override
    public void start() {
        if (started || stopped) {
            return;
        }
        started = true;
        Warden boss = context.boss();
        boss.setGravity(false);
        Location at = boss.getLocation();
        World world = at.getWorld();
        arena = measure(world, at);
        x = at.getX();
        z = at.getZ();
        pickHoverTarget();
        tornadoes = new EnlilTornadoes(world, arena, random);
        tabletEntities = new EnlilTablets(support, bossId);
        flood = new EnlilFlood(support.plugin(), bossId, world, arena, at.getBlockX(), at.getBlockZ());
        try {
            EnlilFlood.recoverOrphans(support.plugin());
        } catch (RuntimeException failure) {
            support.logger().log(Level.WARNING, "[Mazmorras] Enlil: no se pudo limpiar una inundacion vieja", failure);
        }
        try {
            support.spawnBedrockStandIn(bossId, boss, Breeze.class, STAND_IN_SCALE);
        } catch (RuntimeException failure) {
            support.logger().log(Level.WARNING, "[Mazmorras] Enlil: sin doble de Bedrock", failure);
        }
        task = support.scheduler().repeating("enlil-" + bossId, 1L, 1L, this::loop);
        for (Player player : fighters()) {
            player.sendActionBar(LEGACY.deserialize(
                "§bEl viento desvía las flechas. §7Esperá a que Enlil baje a tierra y pegale de cerca."));
        }
    }

    @Override
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (tornadoes != null) {
            tornadoes.clear();
        }
        if (tabletEntities != null) {
            try {
                tabletEntities.clear();
            } catch (RuntimeException failure) {
                support.logger().log(Level.WARNING, "[Mazmorras] Enlil: fallo al sacar las tablillas", failure);
            }
        }
        if (flood != null) {
            try {
                flood.restore();
            } catch (RuntimeException failure) {
                support.logger().log(Level.WARNING, "[Mazmorras] Enlil: fallo al secar la arena", failure);
            }
        }
        support.cleanup(bossId);
    }

    @Override
    public void tick(Instant now) {
        // everything runs in the 1-tick task of start(); the runtime tick (every ui.update-ticks) has nothing to do
    }

    // ------------------------------------------------------------------------------------------------ rules

    @Override
    public boolean blocksProjectile(Projectile projectile) {
        // the wind: no projectile ever reaches Enlil (most are already bounced by deflect())
        Location at = projectile.getLocation();
        at.getWorld().spawnParticle(Particle.SMALL_GUST, at, 3, 0.2, 0.2, 0.2, 0.0);
        at.getWorld().playSound(at, Sound.ENTITY_BREEZE_DEFLECT, SoundCategory.HOSTILE, 1.0f, 1.0f);
        return true;
    }

    @Override
    public double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
        boolean projectile = event instanceof EntityDamageByEntityEvent byEntity
            && byEntity.getDamager() instanceof Projectile;
        double admitted = EnlilRules.admit(phase == 1, vulnerable(), projectile, context.health(),
            context.maximumHealth(), damage);
        if (admitted <= 0.0 && attacker != null) {
            Location at = context.boss().getLocation().add(0.0, 1.5, 0.0);
            at.getWorld().spawnParticle(Particle.SMALL_GUST, at, 4, 0.6, 0.8, 0.6, 0.0);
            hint(attacker, phase == 1
                ? "§7El viento te frena: §eEnlil es intocable en el aire. §7Esperá a que baje."
                : "§6Las Tablillas del Destino lo protegen. §7¡Rompelas primero!");
        }
        return admitted;
    }

    @Override
    public OptionalInt phaseOverride() {
        return OptionalInt.of(phase);
    }

    /** The YAML swat ({@code soplo}) only runs while he stands on the ground in phase 1. */
    @Override
    public boolean suspendsAbilities() {
        return !(phase == 1 && sky.state() == EnlilSkyCycle.State.GROUNDED);
    }

    private boolean vulnerable() {
        return phase == 1 ? sky.vulnerable() : tablets != null && tablets.vulnerable();
    }

    // ------------------------------------------------------------------------------------------------ loop

    private void loop() {
        if (stopped) {
            return;
        }
        Warden boss = context.boss();
        if (!boss.isValid() || boss.isDead()) {
            return;
        }
        try {
            tick++;
            List<Player> players = fighters();
            if (phase == 1) {
                for (EnlilCue cue : sky.advance()) {
                    phaseOneCue(cue, players);
                }
                if (EnlilRules.entersPhaseTwo(context.health(), context.maximumHealth())) {
                    beginPhaseTwo(players);
                }
            } else {
                phaseTwo(players);
            }
            tornadoes.tick(tick, players, boss);
            deflect(boss);
            move(boss, players);
            windowCountdown(players);
        } catch (RuntimeException failure) {
            support.logger().log(Level.WARNING, "[Mazmorras] Enlil: fallo en el tick del encuentro", failure);
        }
    }

    private void phaseOneCue(EnlilCue cue, List<Player> players) {
        switch (cue) {
            case GUST_WARN -> gustWarn(players);
            case GUST -> gust(players);
            case TORNADO_WARN -> tornadoWarn(players);
            case TORNADO -> tornadoes.release();
            case DESCEND_WARN -> {
                stopWalking();
                actionBar(players, "§e¡Enlil va a bajar! §7Salí del círculo blanco... y preparate para pegarle.");
                sound(Sound.ENTITY_BREEZE_INHALE, 2.5f, 0.5f);
            }
            case DESCEND -> context.animate("descend");
            case LANDED -> landed(players);
            case ASCEND -> {
                context.animate("ascend");
                actionBar(players, "§7Enlil vuelve a elevarse con el viento...");
                sound(Sound.ENTITY_BREEZE_JUMP, 2.0f, 0.5f);
            }
            case AIRBORNE -> pickHoverTarget();
            default -> {
            }
        }
    }

    private void beginPhaseTwo(List<Player> players) {
        phase = 2;
        phaseTwoTick = tick;
        stopWalking();
        tablets = new EnlilTabletCycle(EnlilTuning.TABLETS, EnlilTuning.tabletHits(players.size()));
        context.animate("rage");
        sound(Sound.ENTITY_ELDER_GUARDIAN_CURSE, 3.0f, 0.6f);
        sound(Sound.ENTITY_BREEZE_WIND_BURST, 3.0f, 0.5f);
        Location at = context.boss().getLocation().add(0.0, 2.0, 0.0);
        at.getWorld().spawnParticle(Particle.GUST_EMITTER_LARGE, at, 2, 1.0, 1.0, 1.0, 0.0);
        // the runtime shows the phase-2 title from the YAML dialogue; this adds the "what now"
        actionBar(players, "§9¡La arena se inunda! §7Nadá hasta la superficie si te falta el aire.");
    }

    private void phaseTwo(List<Player> players) {
        if (flood.raiseTo(EnlilTuning.floodLayersAt(tick - phaseTwoTick))) {
            sound(Sound.ITEM_BUCKET_EMPTY, 3.0f, 0.5f);
            sound(Sound.BLOCK_WATER_AMBIENT, 3.0f, 0.7f);
            actionBar(players, "§9El agua sube...");
        }
        flood.tick();
        for (EnlilCue cue : tablets.advance()) {
            switch (cue) {
                case GUST_WARN -> gustWarn(players);
                case GUST -> gust(players);
                case TORNADO_WARN -> tornadoWarn(players);
                case TORNADO -> tornadoes.release();
                case TABLETS_FORM -> formTablets(players);
                case FALLEN -> {
                    Location at = context.boss().getLocation();
                    at.getWorld().spawnParticle(Particle.SPLASH, at.clone().add(0.0, 1.0, 0.0), 80, 1.5, 0.5, 1.5, 0.3);
                    sound(Sound.ENTITY_GENERIC_SPLASH, 2.5f, 0.6f);
                }
                case RISE -> {
                    support.models().stopAnimation(context.model(), "fall");
                    context.animate("ascend");
                    actionBar(players, "§7Enlil se levanta... §6las tablillas se vuelven a formar.");
                }
                default -> {
                }
            }
        }
        for (EnlilTablets.Hit hit : tabletEntities.poll()) {
            tabletHit(hit.index(), hit.attacker(), players);
        }
        for (int lost : tabletEntities.lost()) {
            while (tablets.standing(lost)) {
                tabletHit(lost, null, players);
            }
        }
        tabletEntities.tick();
        keepAfloat(players);
    }

    // ------------------------------------------------------------------------------------------------ attacks

    private void gustWarn(List<Player> players) {
        stopWalking();
        context.animate("cast");
        sound(Sound.ENTITY_BREEZE_INHALE, 3.0f, 0.6f);
        Location at = context.boss().getLocation().add(0.0, 2.0, 0.0);
        at.getWorld().spawnParticle(Particle.GUST_EMITTER_SMALL, at, 1, 0.0, 0.0, 0.0, 0.0);
        at.getWorld().spawnParticle(Particle.CLOUD, at, 40, 3.0, 1.5, 3.0, -0.15);
        actionBar(players, "§b¡Enlil junta aire! §7Agachate (shift) para aguantar la ráfaga.");
    }

    private void gust(List<Player> players) {
        Warden boss = context.boss();
        Location from = boss.getLocation();
        World world = from.getWorld();
        world.spawnParticle(Particle.GUST_EMITTER_LARGE, from.clone().add(0.0, 1.5, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
        sound(Sound.ENTITY_BREEZE_WIND_BURST, 3.0f, 0.5f);
        for (Player player : players) {
            Location at = player.getLocation();
            if (at.distanceSquared(from) > EnlilTuning.GUST_RADIUS * EnlilTuning.GUST_RADIUS
                || !arena.contains(at.getX(), at.getZ())) {
                continue;
            }
            boolean braced = player.isSneaking();
            EnlilWind.Push push = EnlilWind.gust(from.getX(), from.getZ(), at.getX(), at.getZ(), braced);
            EnlilTornadoes.hitThenPush(player, braced ? EnlilTuning.GUST_BRACED_DAMAGE : EnlilTuning.GUST_DAMAGE, boss,
                new Vector(push.x(), push.y(), push.z()));
            world.spawnParticle(Particle.GUST, at.clone().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
            Vector step = new Vector(push.x(), 0.0, push.z()).normalize();
            Location trail = at.clone().add(0.0, 1.0, 0.0);
            for (int index = 0; index < 5; index++) {
                trail.add(step);
                world.spawnParticle(Particle.CLOUD, trail, 2, 0.1, 0.2, 0.1, 0.02);
            }
        }
    }

    private void tornadoWarn(List<Player> players) {
        stopWalking();
        context.animate("tornado");
        tornadoes.warn(players, EnlilTuning.tornadoCount(players.size()));
        actionBar(players, "§7¡Tornados! §fAlejate de los círculos en el suelo y no te quedes quieto.");
    }

    private void landed(List<Player> players) {
        context.animate("land");
        Warden boss = context.boss();
        Location at = boss.getLocation();
        World world = at.getWorld();
        world.spawnParticle(Particle.DUST_PLUME, at.clone().add(0.0, 0.2, 0.0), 40, 2.0, 0.2, 2.0, 0.05);
        world.spawnParticle(Particle.CLOUD, at.clone().add(0.0, 0.3, 0.0), 40, 2.5, 0.2, 2.5, 0.1);
        sound(Sound.ENTITY_BREEZE_LAND, 3.0f, 0.5f);
        sound(Sound.BLOCK_ANVIL_LAND, 1.5f, 0.5f);
        for (Player player : players) {
            Location p = player.getLocation();
            if (p.distanceSquared(at) <= EnlilTuning.LANDING_RADIUS * EnlilTuning.LANDING_RADIUS) {
                EnlilWind.Push push = EnlilWind.gust(at.getX(), at.getZ(), p.getX(), p.getZ(), false);
                EnlilTornadoes.hitThenPush(player, EnlilTuning.LANDING_DAMAGE, boss,
                    new Vector(push.x() * 0.55, 0.35, push.z() * 0.55));
            }
            player.showTitle(Title.title(LEGACY.deserialize(""),
                LEGACY.deserialize("§e¡Enlil tocó tierra! §f¡Pegale de cerca!"),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1500), Duration.ofMillis(300))));
        }
    }

    private void formTablets(List<Player> players) {
        double angle = tablets.cycles() * Math.PI / 3.0;
        tabletEntities.form(context.boss().getWorld(), arena.ring(EnlilTuning.TABLETS, EnlilTuning.TABLET_RADIUS, angle),
            arena.floorY() + EnlilTuning.TABLET_HEIGHT);
        actionBar(players, tablets.cycles() == 1
            ? "§6Las Tablillas del Destino lo protegen. §7¡Rompelas! (a golpes o con flechas)"
            : "§6Las tablillas se vuelven a formar...");
    }

    private void tabletHit(int index, Player attacker, List<Player> players) {
        EnlilTabletCycle.Hit result = tablets.hit(index);
        switch (result) {
            case CRACKED -> {
                tabletEntities.crack(index, tablets.hitsLeft(index));
                if (attacker != null) {
                    hint(attacker, "§6Tablilla: §f" + tablets.hitsLeft(index) + " §7golpes más.");
                }
            }
            case BROKEN -> {
                tabletEntities.shatter(index);
                actionBar(players, "§6¡Una tablilla se rompió! §7Quedan " + tablets.standing() + ".");
            }
            case ALL_BROKEN -> {
                tabletEntities.shatter(index);
                context.animate("fall");
                sound(Sound.ENTITY_BREEZE_DEATH, 3.0f, 0.5f);
                for (Player player : players) {
                    player.showTitle(Title.title(LEGACY.deserialize("§6¡Las tablillas se rompieron!"),
                        LEGACY.deserialize("§e¡Enlil cae al agua! §fAtacalo ahora."),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2000), Duration.ofMillis(400))));
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ world

    /** Bounces incoming projectiles off the wind around Enlil (tablets are left alone: arrows are for them). */
    private void deflect(Warden boss) {
        Location center = boss.getLocation().add(0.0, 1.5, 0.0);
        World world = center.getWorld();
        List<Location> tabletSpots = tabletEntities.standing();
        for (Projectile projectile : world.getNearbyEntitiesByType(Projectile.class, center, DEFLECT_RADIUS)) {
            if (deflected.contains(projectile.getUniqueId()) || projectile.isOnGround()) {
                continue;
            }
            ProjectileSource shooter = projectile.getShooter();
            if (shooter == boss) {
                continue;
            }
            Location at = projectile.getLocation();
            double toBoss = at.distanceSquared(center);
            boolean aimedAtTablet = tabletSpots.stream().anyMatch(spot -> spot.distanceSquared(at) < toBoss);
            Vector velocity = projectile.getVelocity();
            double dx = at.getX() - center.getX();
            double dy = at.getY() - center.getY();
            double dz = at.getZ() - center.getZ();
            if (aimedAtTablet || !EnlilWind.incoming(dx, dy, dz, velocity.getX(), velocity.getY(), velocity.getZ())) {
                continue;
            }
            EnlilWind.Push bounce = EnlilWind.deflect(dx, dy, dz, velocity.getX(), velocity.getY(), velocity.getZ(),
                (random.nextDouble() - 0.5) * 1.2);
            projectile.setVelocity(new Vector(bounce.x(), bounce.y(), bounce.z()));
            deflected.add(projectile.getUniqueId());
            world.spawnParticle(Particle.SMALL_GUST, at, 2, 0.1, 0.1, 0.1, 0.0);
            world.playSound(at, Sound.ENTITY_BREEZE_DEFLECT, SoundCategory.HOSTILE, 1.2f, 1.0f);
        }
        if (tick % 100 == 0) {
            deflected.removeIf(id -> {
                Entity entity = world.getEntity(id);
                return entity == null || !entity.isValid();
            });
        }
    }

    /** Teleports the Warden: drifting while hovering, straight down/up while landing, to the centre in phase 2. */
    private void move(Warden boss, List<Player> players) {
        double height;
        if (phase == 1) {
            if (sky.state() == EnlilSkyCycle.State.AIRBORNE && !sky.landingWarned()) {
                glide(DRIFT_SPEED);
            }
            height = sky.height();
        } else {
            targetX = arena.centerX();
            targetZ = arena.centerZ();
            if (tablets.state() != EnlilTabletCycle.State.FALLEN) {
                glide(SHIELD_GLIDE);
            }
            height = tablets.height();
        }
        double ground = groundY(boss.getWorld(), x, z);
        Location to = new Location(boss.getWorld(), x, ground + height, z);
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player player : players) {
            double distance = player.getLocation().distanceSquared(to);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null) {
            Vector look = nearest.getLocation().toVector().subtract(to.toVector());
            to.setDirection(look.lengthSquared() < 1.0e-6 ? new Vector(0, 0, 1) : look);
            to.setPitch(0.0f);
        } else {
            to.setYaw(boss.getLocation().getYaw());
        }
        boss.teleport(to);
        boss.setVelocity(new Vector());
        if (phase == 1 && tick % 4 == 0
            && (sky.state() == EnlilSkyCycle.State.DESCENDING || sky.landingWarned())) {
            landingRing(ground);
        }
    }

    private void glide(double speed) {
        double dx = targetX - x;
        double dz = targetZ - z;
        double length = Math.hypot(dx, dz);
        if (length <= speed) {
            x = targetX;
            z = targetZ;
            stopWalking();
            return;
        }
        if (length > 2.0 && !walking) {
            walking = true;
            context.animate("walk");
        }
        x += dx / length * speed;
        z += dz / length * speed;
    }

    private void stopWalking() {
        if (walking) {
            walking = false;
            support.models().stopAnimation(context.model(), "walk");
        }
    }

    private void pickHoverTarget() {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double radius = 2.0 + random.nextDouble() * 4.0;
        targetX = arena.clampX(arena.centerX() + Math.cos(angle) * radius, 3.0);
        targetZ = arena.clampZ(arena.centerZ() + Math.sin(angle) * radius, 3.0);
    }

    /** Ring on the ground where Enlil is about to land. */
    private void landingRing(double ground) {
        World world = context.boss().getWorld();
        for (int index = 0; index < 24; index++) {
            double angle = index * Math.PI / 12.0;
            world.spawnParticle(Particle.DUST, new Location(world, x + Math.cos(angle) * EnlilTuning.LANDING_RADIUS,
                ground + 0.15, z + Math.sin(angle) * EnlilTuning.LANDING_RADIUS), 1, 0.0, 0.0, 0.0, 0.0, LANDING);
        }
    }

    /** Top of the first solid block under (x, z) near the floor: the altar or the floor itself (water is not solid). */
    private double groundY(World world, double atX, double atZ) {
        int bx = (int) Math.floor(atX);
        int bz = (int) Math.floor(atZ);
        for (int y = arena.floorY() + 2; y >= arena.floorY() - 1; y--) {
            Block block = world.getBlockAt(bx, y, bz);
            if (!block.isPassable()) {
                return y + 1.0;
            }
        }
        return arena.floorY();
    }

    /** The action-bar countdown of the vulnerable windows, and sparks around him so everyone sees he is open. */
    private void windowCountdown(List<Player> players) {
        int left = phase == 1 ? sky.groundedTicksLeft() : tablets.fallenTicksLeft();
        if (left <= 0) {
            return;
        }
        if (tick % 5 == 0) {
            Location at = context.boss().getLocation().add(0.0, 1.6, 0.0);
            at.getWorld().spawnParticle(Particle.CRIT, at, 6, 0.6, 1.0, 0.6, 0.1);
        }
        if (left % 20 == 0) {
            actionBar(players, "§e¡Enlil está expuesto! §f" + (left / 20) + "s");
        }
    }

    /** The water is two blocks deep: whoever runs out of air gets a little back, so the flood never drowns. */
    private void keepAfloat(List<Player> players) {
        for (Player player : players) {
            if (player.isInWater() && player.getRemainingAir() < 60) {
                player.setRemainingAir(60);
                hint(player, "§9¡Subí a respirar! §7Nadá hacia arriba (saltar).");
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static EnlilArena measure(World world, Location at) {
        EnlilArena.Passable passable = (bx, by, bz) -> world.getBlockAt(bx, by, bz).isPassable();
        int floor = EnlilArena.floorUnder(at.getBlockX(), at.getBlockY(), at.getBlockZ(), passable);
        return EnlilArena.probe(at.getBlockX(), at.getBlockZ(), floor, passable);
    }

    /**
     * The fighters inside the room in survival/adventure (the walls bound the fight; people in the corridors and
     * creative/spectator staff are left alone).
     */
    private List<Player> fighters() {
        List<Player> inside = new ArrayList<>();
        for (Player player : context.players()) {
            Location at = player.getLocation();
            if (EnlilRules.targetable(player.getGameMode(), player.isDead()) && arena.contains(at.getX(), at.getZ()) && at.getY() >= arena.floorY() - 2.0
                && at.getY() <= arena.floorY() + EnlilArena.HEADROOM) {
                inside.add(player);
            }
        }
        return inside;
    }

    private void actionBar(List<Player> players, String text) {
        for (Player player : players) {
            player.sendActionBar(LEGACY.deserialize(text));
        }
    }

    private void hint(Player player, String text) {
        Long last = hints.get(player.getUniqueId());
        if (last == null || tick - last >= HINT_COOLDOWN_TICKS) {
            hints.put(player.getUniqueId(), tick);
            player.sendActionBar(LEGACY.deserialize(text));
        }
    }

    private void sound(Sound sound, float volume, float pitch) {
        Location at = context.boss().getLocation();
        at.getWorld().playSound(at, sound, SoundCategory.HOSTILE, volume, pitch);
    }
}
