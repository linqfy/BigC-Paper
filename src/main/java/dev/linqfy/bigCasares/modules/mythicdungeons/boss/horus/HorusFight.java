package dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus;

import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.mythicdungeons.MythicKeys;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus.HorusDirector.Stage;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterContext;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterController;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * La parte de Bukkit del encuentro de Horus: mueve al Warden (teletransporte cada tick, sin gravedad), dibuja los
 * avisos, pega, saca y vigila a los Ojos. Las decisiones (etapas, ventanas, cuentas) son de {@link HorusDirector},
 * {@link HorusGlidePath}, {@link HorusEyeSwarm} y {@link HorusDamageRules}. Corre en una tarea de cada tick de
 * {@link MythicBossSupport#scheduler()} que se cancela en {@link #stop()}. No cambia ningun bloque del mundo.
 *
 * <p>Bedrock: ve un Vex grande en lugar de Horus y un zombi bebe en lugar de cada Ojo (dobles de
 * {@link MythicBossSupport#spawnBedrockStandIn}); los avisos son particulas, sonidos, titulos y la barra de accion,
 * que si le llegan. Las animaciones del modelo no existen alla.</p>
 */
final class HorusFight implements BossEncounterController {

    static final String EYE_MODEL = "bigcasares_myth_horus_eye";
    /** Radio horizontal en que se mueve Horus y salen los Ojos (la sala es de ~33x33). */
    private static final double ARENA_RADIUS = 13.0;
    /** Mas lejos que esto del centro un Ojo vuelve a la sala. */
    private static final double LEASH_RADIUS = 18.0;
    /** Altura sobre el piso desde la que se lanza en picada. */
    private static final double ALTITUDE = 7.0;
    /** Altura del trono en el aire durante la fase 2. */
    private static final double THRONE_HEIGHT = 8.0;
    private static final int EYE_RISE_TICKS = 20;
    private static final double EYE_HEALTH = 20.0;
    /** El dano base de un zombi bebe (el ataque de zombi; la dificultad lo escala como en vanilla). */
    private static final double EYE_DAMAGE = 3.0;
    private static final double EYE_FOLLOW_RANGE = 40.0;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Particle.DustOptions GOLD_DUST = new Particle.DustOptions(Color.fromRGB(0xE0B13C), 1.4F);
    private static final Particle.DustOptions RED_DUST = new Particle.DustOptions(Color.fromRGB(0xD0402A), 1.6F);
    private static final BlockData SAND = Material.SAND.createBlockData();

    private final MythicBossSupport support;
    private final BossEncounterContext context;
    private final HorusDirector director = new HorusDirector();
    private final HorusEyeSwarm swarm = new HorusEyeSwarm(HorusEyeSwarm.DEFAULT_TOTAL);
    private final List<Eye> eyes = new ArrayList<>();
    private final List<JavaModelHandle> closingModels = new ArrayList<>();
    private final Set<UUID> hitThisGlide = new HashSet<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private BukkitTask loop;
    private World world;
    private HorusPoint center;
    private double floorY;
    private HorusPoint position;
    private float yaw;
    private HorusPoint from;
    private HorusPoint to;
    private HorusGlidePath path;
    private UUID glideTarget;
    private int glideCount;
    private long clock;
    private String loopAnimation = "idle";
    private boolean stopped;
    private boolean defeated;

    /** Un Ojo vivo: su cuerpo (zombi bebe), su modelo y desde que tick pelea. */
    private static final class Eye {
        final Zombie body;
        final JavaModelHandle model;
        final long awakeAt;
        boolean awake;

        Eye(Zombie body, JavaModelHandle model, long awakeAt) {
            this.body = body;
            this.model = model;
            this.awakeAt = awakeAt;
        }
    }

    HorusFight(MythicBossSupport support, BossEncounterContext context) {
        this.support = Objects.requireNonNull(support, "support");
        this.context = Objects.requireNonNull(context, "context");
    }

    // ------------------------------------------------------------------ controller

    @Override
    public void start() {
        Warden boss = context.boss();
        Location spawn = boss.getLocation();
        world = Objects.requireNonNull(spawn.getWorld(), "world");
        center = point(spawn);
        floorY = spawn.getY();
        position = center;
        yaw = spawn.getYaw();
        boss.setGravity(false);
        try {
            Vex standIn = support.spawnBedrockStandIn(context.bossInstanceId(), boss, Vex.class, 4.0);
            standIn.setLimitedLifetime(false);
            standIn.setCharging(true);
        } catch (RuntimeException unavailable) {
            support.logger().warning("[Horus] Sin doble de Bedrock: " + unavailable.getMessage());
        }
        loop = support.scheduler().repeating("horus-" + context.bossInstanceId(), 1L, 1L, this::step);
    }

    @Override
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        if (loop != null) {
            loop.cancel();
            loop = null;
        }
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        for (Eye eye : eyes) {
            closeModel(eye.model);
        }
        eyes.clear();
        for (JavaModelHandle handle : closingModels) {
            closeModel(handle);
        }
        closingModels.clear();
        support.cleanup(context.bossInstanceId());
    }

    @Override
    public boolean blocksProjectile(Projectile projectile) {
        return !director.vulnerable();
    }

    @Override
    public double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
        return HorusDamageRules.admitted(director.phase(), context.health(), context.maximumHealth(), damage);
    }

    @Override
    public OptionalInt phaseOverride() {
        return OptionalInt.of(director.phase());
    }

    @Override
    public boolean suspendsAbilities() {
        // todo el combate es del guion; las habilidades del YAML no corren
        return true;
    }

    // ------------------------------------------------------------------ loop

    private void step() {
        if (stopped || defeated) {
            return;
        }
        Warden boss = context.boss();
        if (!boss.isValid()) {
            return;
        }
        clock++;
        List<Player> players = targets();
        pollEyes(players);
        double fraction = context.maximumHealth() <= 0.0 ? 1.0 : context.health() / context.maximumHealth();
        boolean entered = director.tick(fraction, swarm.allSpawned(), swarm.cleared());
        if (entered) {
            enter(director.stage(), players);
            if (stopped || defeated) {
                return;
            }
        }
        during(director.stage(), director.ticksInStage(), players);
        move(boss);
    }

    /** Los jugadores del publico en supervivencia/aventura: a los de creativo/espectador no se los ataca. */
    private List<Player> targets() {
        List<Player> targets = new ArrayList<>();
        for (Player player : context.players()) {
            if (HorusDamageRules.targetable(player.getGameMode(), player.isDead())) {
                targets.add(player);
            }
        }
        return targets;
    }

    private void enter(Stage stage, List<Player> players) {
        switch (stage) {
            case LANDED -> {
                setLoop("idle");
                position = ground(position);
                burst(Particle.DUST_PLUME, position, 30, 1.2, 0.1);
                sound(position, Sound.BLOCK_SAND_FALL, 1.4F, 0.7F);
            }
            case TAKEOFF -> {
                setLoop("planeo");
                from = position;
                to = position.withY(floorY + ALTITUDE);
                burst(Particle.GUST, position.plus(0, 0.5, 0), 3, 1.0, 0.0);
                sound(position, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.6F, 1.1F);
            }
            case AIM -> aim(players);
            case GLIDE -> {
                setLoop("dive");
                hitThisGlide.clear();
                sound(position, Sound.ENTITY_PHANTOM_SWOOP, 2.0F, 0.6F);
            }
            case DESCEND -> {
                setLoop("planeo");
                from = position;
                to = ground(position);
            }
            case ASCEND -> {
                setLoop("planeo");
                playOnce("rage");
                hitThisGlide.clear();
                from = position;
                to = center.withY(floorY + THRONE_HEIGHT);
                sound(position, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0F, 1.3F);
                for (Player player : players) {
                    player.sendActionBar(text("§6Horus se retira al cielo... §esu Ojo despierta bajo la arena"));
                }
            }
            case SUMMON -> {
                playOnce("cast");
                sound(position, Sound.BLOCK_BEACON_ACTIVATE, 2.0F, 0.6F);
            }
            case GUARD -> {
                // espera a que maten a todos los Ojos
            }
            case WRATH -> {
                setLoop("idle");
                playOnce("rage");
                from = position;
                for (Player player : players) {
                    player.showTitle(Title.title(text("§6¡Horus se enfurece!"), text("§eSus ojos cayeron..."),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))));
                }
                sound(position, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0F, 0.8F);
            }
            case DONE -> crumble(players);
        }
    }

    private void during(Stage stage, int tick, List<Player> players) {
        switch (stage) {
            case LANDED -> {
                faceNearest(players);
                if (director.buffetWarning()) {
                    sound(position, Sound.ENTITY_BREEZE_INHALE, 1.8F, 0.6F);
                }
                if (tick >= HorusDirector.BUFFET_WARN_AT && tick < HorusDirector.BUFFET_HIT_AT && tick % 3 == 0) {
                    ring(position.withY(floorY + 0.15), HorusDamageRules.BUFFET_RADIUS, RED_DUST);
                }
                if (tick == HorusDirector.BUFFET_HIT_AT - 11) {
                    playOnce("attack");
                }
                if (director.buffetStrike()) {
                    buffet(players);
                }
            }
            case TAKEOFF -> position = from.lerp(to, smooth((tick + 1.0) / HorusDirector.TAKEOFF_TICKS));
            case AIM -> {
                if (path != null) {
                    position = from.lerp(path.start(), smooth(Math.min(1.0, (tick + 1.0) / 12.0)));
                    yaw = path.start().yawTowards(path.low(), yaw);
                    if (tick % 4 == 0) {
                        telegraph(path);
                    }
                    if (tick == 0 || tick == HorusDirector.AIM_TICKS - 8) {
                        sound(path.low(), Sound.ENTITY_PHANTOM_AMBIENT, 1.6F, 0.5F);
                    }
                } else {
                    hover(tick);
                }
            }
            case GLIDE -> glide(tick, players);
            case DESCEND -> position = from.lerp(to, smooth((tick + 1.0) / HorusDirector.DESCEND_TICKS));
            case ASCEND -> {
                position = from.lerp(to, smooth((tick + 1.0) / HorusDirector.ASCEND_TICKS));
                faceNearest(players);
                if (tick % 5 == 0) {
                    quake();
                }
            }
            case SUMMON -> {
                hover(tick);
                faceNearest(players);
                if (tick % HorusDirector.WAVE_EVERY == 0) {
                    spawnWave(swarm.nextWave(), players);
                }
            }
            case GUARD -> {
                hover(tick);
                faceNearest(players);
                if (!swarm.allSpawned() && tick % HorusDirector.WAVE_EVERY == 0) {
                    spawnWave(swarm.nextWave(), players);
                }
                if (tick % 20 == 0) {
                    for (Player player : players) {
                        player.sendActionBar(text("§eOjos de Horus: §6" + swarm.remaining()));
                    }
                }
            }
            case WRATH -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                double shake = 0.05 + 0.25 * tick / HorusDirector.WRATH_TICKS;
                position = from.plus(random.nextDouble(-shake, shake), 0.0, random.nextDouble(-shake, shake));
                world.spawnParticle(Particle.FALLING_DUST, location(position.plus(0, 1.8, 0)),
                    4 + tick / 4, 0.8, 1.2, 0.8, 0.0, SAND);
                if (tick == 20) {
                    setLoop("idle");
                    playOnce("vanish");
                    sound(position, Sound.BLOCK_SAND_BREAK, 2.0F, 0.5F);
                }
                if (tick % 10 == 5) {
                    sound(position, Sound.BLOCK_SUSPICIOUS_SAND_BREAK, 1.5F, 0.6F);
                }
            }
            case DONE -> {
            }
        }
    }

    // ------------------------------------------------------------------ phase 1

    private void aim(List<Player> players) {
        setLoop("planeo");
        from = position;
        path = null;
        glideTarget = null;
        if (players.isEmpty()) {
            director.glidePlanned(0);
            return;
        }
        Player target = players.get(Math.floorMod(glideCount++, players.size()));
        glideTarget = target.getUniqueId();
        HorusPoint start = HorusGlidePath.clampInside(position, center, ARENA_RADIUS).withY(floorY + ALTITUDE);
        path = HorusGlidePath.plan(start, point(target.getLocation()), center, ARENA_RADIUS, floorY);
        director.glidePlanned(path.totalTicks());
        target.sendActionBar(text("§c¡Horus se lanza en picada sobre vos! §eSalí de la línea dorada"));
        for (Player player : players) {
            if (!player.getUniqueId().equals(glideTarget)) {
                player.sendActionBar(text("§6Horus planea sobre " + target.getName() + "§6: §esalí de la línea"));
            }
        }
    }

    private void glide(int tick, List<Player> players) {
        if (path == null) {
            return;
        }
        HorusPoint next = path.positionAt(tick + 1);
        yaw = position.yawTowards(next, yaw);
        position = next;
        world.spawnParticle(Particle.CLOUD, location(position.plus(0, 1.5, 0)), 3, 0.6, 0.3, 0.6, 0.01);
        world.spawnParticle(Particle.DUST, location(position.plus(0, 1.5, 0)), 4, 1.4, 0.2, 1.4, 0.0, GOLD_DUST);
        Vector push = new Vector(next.x() - path.start().x(), 0.0, next.z() - path.start().z());
        if (push.lengthSquared() > 1.0E-6) {
            push.normalize();
        }
        for (Player player : players) {
            if (hitThisGlide.contains(player.getUniqueId())
                || !HorusGlidePath.bodyHits(position, point(player.getLocation()))) {
                continue;
            }
            hitThisGlide.add(player.getUniqueId());
            player.damage(HorusDamageRules.GLIDE_DAMAGE, context.boss());
            player.setVelocity(push.clone().multiply(1.1).setY(0.45));
            sound(point(player.getLocation()), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.7F);
        }
    }

    private void buffet(List<Player> players) {
        HorusPoint here = position;
        world.spawnParticle(Particle.GUST, location(here.plus(0, 1.0, 0)), 6, 2.0, 0.5, 2.0, 0.0);
        world.spawnParticle(Particle.SWEEP_ATTACK, location(here.plus(0, 1.2, 0)), 10, 2.5, 0.4, 2.5, 0.0);
        sound(here, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0F, 0.7F);
        double radius = HorusDamageRules.BUFFET_RADIUS;
        for (Player player : players) {
            HorusPoint at = point(player.getLocation());
            if (at.horizontalDistance(here) > radius || Math.abs(at.y() - here.y()) > 3.0) {
                continue;
            }
            player.damage(HorusDamageRules.BUFFET_DAMAGE, context.boss());
            Vector away = new Vector(at.x() - here.x(), 0.0, at.z() - here.z());
            if (away.lengthSquared() < 1.0E-6) {
                away = new Vector(0.0, 0.0, 1.0);
            }
            player.setVelocity(away.normalize().multiply(1.3).setY(0.5));
        }
    }

    private void telegraph(HorusGlidePath glide) {
        for (HorusPoint mark : glide.groundMarks(floorY + 0.15, 0.8)) {
            world.spawnParticle(Particle.DUST, location(mark), 1, 0.05, 0.0, 0.05, 0.0, GOLD_DUST);
        }
        ring(glide.low().withY(floorY + 0.15), HorusGlidePath.HIT_RADIUS, RED_DUST);
    }

    // ------------------------------------------------------------------ phase 2: the Eyes

    private void spawnWave(int count, List<Player> players) {
        for (int i = 0; i < count; i++) {
            spawnEye(players);
        }
    }

    private void spawnEye(List<Player> players) {
        Location at = eyeSpot(players);
        MythicKeys keys = support.mobs().keys();
        UUID bossId = context.bossInstanceId();
        // sin datos al azar: nada de jinetes de gallina ni equipo del spawn natural
        Zombie body = world.spawn(at, Zombie.class, false, zombie -> {
            keys.markSystem(zombie);
            keys.markBossOwner(zombie, bossId);
            zombie.setBaby();
            zombie.setShouldBurnInDay(false);
            zombie.setCanBreakDoors(false);
            zombie.setCanPickupItems(false);
            zombie.setLootTable(null);
            zombie.setSilent(true);
            zombie.setAI(false);
            zombie.setPersistent(false);
            zombie.setRemoveWhenFarAway(false);
            zombie.customName(Component.text("Ojo de Horus"));
            zombie.setCustomNameVisible(false);
            setBase(zombie, Attribute.MAX_HEALTH, EYE_HEALTH);
            setBase(zombie, Attribute.ATTACK_DAMAGE, EYE_DAMAGE);
            setBase(zombie, Attribute.FOLLOW_RANGE, EYE_FOLLOW_RANGE);
            setBase(zombie, Attribute.SPAWN_REINFORCEMENTS, 0.0);
            zombie.setHealth(EYE_HEALTH);
            EntityEquipment equipment = zombie.getEquipment();
            if (equipment != null) {
                equipment.clear();
            }
        });
        support.track(bossId, body);
        JavaModelHandle model = null;
        try {
            model = support.models().attach(body, EYE_MODEL);
            support.models().animateOnce(model, "rise", () -> {
            });
        } catch (RuntimeException | LinkageError missing) {
            support.logger().warning("[Horus] Sin modelo " + EYE_MODEL + ": " + missing.getMessage());
        }
        try {
            Zombie standIn = support.spawnBedrockStandIn(bossId, body, Zombie.class, 1.0);
            standIn.setBaby();
            standIn.setShouldBurnInDay(false);
        } catch (RuntimeException unavailable) {
            // sin dobles de Bedrock: esos jugadores ven el cuerpo invisible del modelo
        }
        eyes.add(new Eye(body, model, clock + EYE_RISE_TICKS));
        swarm.spawned();
        Block floor = at.getBlock().getRelative(0, -1, 0);
        BlockData ground = floor.getType().isAir() ? SAND : floor.getBlockData();
        world.spawnParticle(Particle.BLOCK_CRUMBLE, at.clone().add(0, 0.2, 0), 24, 0.4, 0.1, 0.4, 0.0, ground);
        world.spawnParticle(Particle.DUST_PLUME, at.clone().add(0, 0.3, 0), 8, 0.3, 0.1, 0.3, 0.02);
        world.playSound(at, Sound.BLOCK_SUSPICIOUS_SAND_BREAK, SoundCategory.HOSTILE, 1.4F, 0.7F);
    }

    private void pollEyes(List<Player> players) {
        Iterator<Eye> iterator = eyes.iterator();
        while (iterator.hasNext()) {
            Eye eye = iterator.next();
            Zombie body = eye.body;
            if (body.isDead()) {
                iterator.remove();
                double credit = swarm.killedOne(context.health());
                if (credit > 0.0) {
                    context.damage(credit, body.getKiller());
                }
                Location at = body.getLocation();
                world.spawnParticle(Particle.FALLING_DUST, at.clone().add(0, 0.6, 0), 14, 0.3, 0.3, 0.3, 0.0, SAND);
                world.playSound(at, Sound.ENTITY_ENDER_EYE_DEATH, SoundCategory.HOSTILE, 1.2F, 0.8F);
                retire(eye.model);
                continue;
            }
            if (!body.isValid()) {
                // desaparecio sin morir (descarga o limpieza externa): sale otro en su lugar
                iterator.remove();
                swarm.lost();
                closeModel(eye.model);
                continue;
            }
            if (!eye.awake && clock >= eye.awakeAt) {
                eye.awake = true;
                body.setAI(true);
            }
            if (point(body.getLocation()).horizontalDistance(center) > LEASH_RADIUS) {
                body.teleport(eyeSpot(players));
            }
            if (eye.awake && body.getTarget() == null) {
                Player nearest = nearest(players, point(body.getLocation()));
                if (nearest != null) {
                    body.setTarget(nearest);
                }
            }
        }
    }

    /** El modelo de un Ojo muerto hace su animacion de muerte y se cierra al rato. */
    private void retire(JavaModelHandle model) {
        if (model == null) {
            return;
        }
        try {
            support.models().animateOnce(model, "death", () -> {
            });
        } catch (RuntimeException ignored) {
            // cosmetico
        }
        closingModels.add(model);
        tasks.add(support.scheduler().later("horus-eye-close-" + model.anchorId(), 18L, () -> {
            if (closingModels.remove(model)) {
                closeModel(model);
            }
        }));
    }

    private Location eyeSpot(List<Player> players) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double radius = random.nextDouble(3.0, ARENA_RADIUS - 2.0);
            HorusPoint spot = new HorusPoint(center.x() + Math.cos(angle) * radius, floorY,
                center.z() + Math.sin(angle) * radius);
            Player close = nearest(players, spot);
            boolean crowded = close != null && point(close.getLocation()).horizontalDistance(spot) < 2.0;
            if (!crowded && standable(spot)) {
                return location(spot);
            }
        }
        return location(center);
    }

    // ------------------------------------------------------------------ the end

    private void crumble(List<Player> players) {
        defeated = true;
        Location at = location(position.plus(0, 1.6, 0));
        world.spawnParticle(Particle.FALLING_DUST, at, 260, 1.6, 2.2, 1.6, 0.0, SAND);
        world.spawnParticle(Particle.BLOCK, at, 160, 1.2, 1.8, 1.2, 0.1, SAND);
        world.spawnParticle(Particle.DUST_PLUME, at, 60, 1.0, 1.4, 1.0, 0.05);
        world.spawnParticle(Particle.CLOUD, at, 40, 1.0, 1.5, 1.0, 0.03);
        // polvo que cae despacio sobre toda la arena: FALLING_DUST cae solo, no hace falta otra tarea
        world.spawnParticle(Particle.FALLING_DUST, location(center.withY(floorY + THRONE_HEIGHT + 3.0)), 300,
            ARENA_RADIUS * 0.6, 1.5, ARENA_RADIUS * 0.6, 0.0, SAND);
        world.playSound(at, Sound.BLOCK_SAND_BREAK, SoundCategory.HOSTILE, 2.5F, 0.5F);
        world.playSound(at, Sound.BLOCK_SUSPICIOUS_SAND_BREAK, SoundCategory.HOSTILE, 2.0F, 0.4F);
        world.playSound(at, Sound.ENTITY_PHANTOM_DEATH, SoundCategory.HOSTILE, 2.0F, 0.5F);
        for (Player player : players) {
            player.showTitle(Title.title(text("§eSolo queda polvo"), text("§6Horus se desvanecio"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(800))));
        }
        context.defeat();
    }

    // ------------------------------------------------------------------ helpers

    private void move(Warden boss) {
        Location current = boss.getLocation();
        Location target = new Location(world, position.x(), position.y(), position.z(), yaw, 0.0F);
        if (current.distanceSquared(target) > 1.0E-4 || Math.abs(current.getYaw() - yaw) > 0.5F) {
            boss.teleport(target);
        }
    }

    private void hover(int tick) {
        HorusPoint throne = center.withY(floorY + THRONE_HEIGHT);
        position = throne.plus(0.0, Math.sin(tick / 12.0) * 0.35, 0.0);
    }

    private void faceNearest(List<Player> players) {
        Player nearest = nearest(players, position);
        if (nearest != null) {
            yaw = position.yawTowards(point(nearest.getLocation()), yaw);
        }
    }

    private void quake() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double radius = random.nextDouble(ARENA_RADIUS);
            HorusPoint spot = new HorusPoint(center.x() + Math.cos(angle) * radius, floorY + 0.2,
                center.z() + Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST_PLUME, location(spot), 3, 0.3, 0.05, 0.3, 0.01);
        }
        sound(center, Sound.BLOCK_ROOTED_DIRT_BREAK, 0.8F, 0.5F);
    }

    /** El piso bajo {@code at}; si ahi hay algo solido (una columna), aterriza en el centro. */
    private HorusPoint ground(HorusPoint at) {
        HorusPoint spot = HorusGlidePath.clampInside(at, center, ARENA_RADIUS).withY(floorY);
        return standable(spot) ? spot : center;
    }

    private boolean standable(HorusPoint spot) {
        Block feet = world.getBlockAt((int) Math.floor(spot.x()), (int) Math.floor(floorY), (int) Math.floor(spot.z()));
        return feet.isPassable() && feet.getRelative(0, 1, 0).isPassable()
            && feet.getRelative(0, 2, 0).isPassable() && !feet.getRelative(0, -1, 0).isPassable();
    }

    private void ring(HorusPoint middle, double radius, Particle.DustOptions dust) {
        int points = Math.max(12, (int) Math.round(radius * 7));
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            world.spawnParticle(Particle.DUST,
                location(middle.plus(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius)),
                1, 0.0, 0.0, 0.0, 0.0, dust);
        }
    }

    private void burst(Particle particle, HorusPoint at, int count, double spread, double speed) {
        world.spawnParticle(particle, location(at), count, spread, 0.2, spread, speed);
    }

    private void sound(HorusPoint at, Sound sound, float volume, float pitch) {
        world.playSound(location(at), sound, SoundCategory.HOSTILE, volume, pitch);
    }

    private void setLoop(String animation) {
        if (animation.equals(loopAnimation)) {
            return;
        }
        JavaModelGateway models = context.models();
        try {
            models.stopAnimation(context.model(), loopAnimation);
        } catch (RuntimeException ignored) {
            // cosmetico
        }
        loopAnimation = animation;
        context.animate(animation);
    }

    private void playOnce(String animation) {
        try {
            context.models().animateOnce(context.model(), animation, () -> {
            });
        } catch (RuntimeException ignored) {
            // cosmetico: el servidor nunca espera una animacion
        }
    }

    private void closeModel(JavaModelHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            support.models().close(handle);
        } catch (RuntimeException ignored) {
            // ya estaba cerrado (la entidad se fue)
        }
    }

    private static Player nearest(List<Player> players, HorusPoint from) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : players) {
            double distance = point(player.getLocation()).distance(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static double smooth(double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static void setBase(Zombie zombie, Attribute attribute, double value) {
        AttributeInstance instance = zombie.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private static Component text(String legacy) {
        return LEGACY.deserialize(legacy);
    }

    private static HorusPoint point(Location location) {
        return new HorusPoint(location.getX(), location.getY(), location.getZ());
    }

    private Location location(HorusPoint point) {
        return new Location(world, point.x(), point.y(), point.z());
    }

    @Override
    public void tick(Instant now) {
        // el guion corre en su propia tarea de cada tick (el runtime llama cada ui.update-ticks)
    }
}
