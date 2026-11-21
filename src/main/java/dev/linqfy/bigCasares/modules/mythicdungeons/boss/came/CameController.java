package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import dev.linqfy.bigCasares.modules.mythicdungeons.DungeonBounds;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterContext;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterController;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Warden;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * El encuentro de Hun Camé y Vucub Camé en el servidor: una tarea por tick (via {@link MythicBossSupport#scheduler()})
 * que mueve a los dos señores (teletransportes), la pelota, dibuja los avisos y aplica los golpes. Las decisiones las
 * toman las clases puras ({@link CameBallGame}, {@link CameBall}, {@link CameHoop}, {@link CameDuoHealth},
 * {@link CameDuoDirector}, {@link CameStrikes}). Hun Camé es el Warden autoritativo; Vucub Camé es {@link CameVucub}.
 * No cambia bloques.
 *
 * <p>La barra del jefe es la suma de las dos vidas (ver {@link CameDuoHealth}): los golpes a Hun pasan por
 * {@link #filterDamage}, los de Vucub, los goles y las revividas por {@code context.damage/heal}; cada medio segundo se
 * corrige cualquier diferencia. Con un señor en pie la barra nunca llega a cero.</p>
 *
 * <p>Bedrock: Hun se ve como un esqueleto wither grande y Vucub como un esqueleto con casco de jade; la pelota como un
 * cubo de magma; el aro como un anillo de particulas. Los avisos son particulas, sonidos, titulos y la barra de
 * accion; los caidos no se ven tirados en el piso (los dobles no tienen animaciones), lo dicen los titulos y las
 * almas sobre el cuerpo.</p>
 */
final class CameController implements BossEncounterController {

    /** Lo que el controlador necesita de cada señor (Hun = Warden del runtime, Vucub = su ancla propia). */
    interface LordBody {
        boolean valid();

        Location location();

        void moveTo(Location to);

        void loop(String animation);

        void once(String animation);
    }

    private enum Stage { BALL_GAME, AWAKENING, DUO }

    private enum Shape { CIRCLE, LANE, CONE }

    private record Telegraph(CameLord lord, CameDuoDirector.Move move, Shape shape, CameVec a, CameVec b,
                             double size, long startedAt, long landAt) {
    }

    private static final class Dash {
        private final CameVec direction;
        private final Set<UUID> hit = new HashSet<>();
        private int remaining;

        private Dash(CameVec direction, int remaining) {
            this.direction = direction;
            this.remaining = remaining;
        }
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final double POST_OFFSET = 3.5;
    private static final double HOOP_FORWARD = 4.5;
    private static final double HOOP_HEIGHT = 3.0;
    private static final double HUN_HAND = 4.1;
    private static final double VUCUB_HAND = 3.7;
    private static final long AWAKENING_TICKS = 60L;
    private static final double WALK_SPEED = 0.17;
    private static final double CHASE_STOP = 3.0;
    private static final double HUN_STAND_IN_SCALE = 1.5;
    private static final long NOTICE_GAP = 20L;
    private static final Particle.DustOptions DANGER = new Particle.DustOptions(Color.fromRGB(220, 30, 30), 1.5F);
    private static final Particle.DustOptions DANGER_FILL = new Particle.DustOptions(Color.fromRGB(255, 120, 60), 1.1F);
    private static final Particle.DustOptions THROW_LINE = new Particle.DustOptions(Color.fromRGB(255, 170, 40), 1.0F);
    private static final Particle.DustOptions REVIVE = new Particle.DustOptions(Color.fromRGB(120, 60, 200), 1.3F);

    private final MythicBossSupport support;
    private final BossEncounterContext context;
    private final UUID bossId;
    private final CameCourt court;
    private final CameVucub vucub;
    private final LordBody hun;
    private final List<Telegraph> telegraphs = new ArrayList<>();
    private final Map<CameLord, Dash> dashes = new EnumMap<>(CameLord.class);
    private final Map<UUID, Long> lastNotice = new HashMap<>();
    private CameBallGame game;
    private CameDuoHealth health;
    private CameDuoDirector director;
    private BukkitTask task;
    private Location origin;
    private CameVec forward = new CameVec(0.0, 0.0, 1.0);
    private CameArena arena;
    private DungeonBounds room;
    private Stage stage = Stage.BALL_GAME;
    private long stageStart;
    private long tick;
    private boolean stopped;
    private boolean finished;
    private boolean pendingDefeat;
    private CameLord pendingDown;
    private UUID ballTarget;
    private UUID lastBallTarget;
    private UUID lastStriker;
    private UUID patternTarget;
    private UUID lastPatternTarget;
    private CameVec axis = new CameVec(1.0, 0.0, 0.0);
    private String hunLoop;

    CameController(MythicBossSupport support, BossEncounterContext context) {
        this.support = Objects.requireNonNull(support, "support");
        this.context = Objects.requireNonNull(context, "context");
        this.bossId = context.bossInstanceId();
        this.court = new CameCourt(support, bossId);
        this.vucub = new CameVucub(support, bossId);
        this.hun = new HunBody();
    }

    // ------------------------------------------------------------------ BossEncounterController

    @Override
    public void start() {
        Warden boss = context.boss();
        origin = boss.getLocation().clone();
        arena = CameArena.around(origin.getX(), origin.getY(), origin.getZ());
        room = findRoom(origin);
        health = new CameDuoHealth(context.maximumHealth());
        game = new CameBallGame(0L);
        Player nearest = nearest(vec(origin), targets());
        forward = nearest != null
            ? vec(nearest.getLocation()).subtract(vec(origin)).horizontal()
            : vec(origin.getDirection()).horizontal();
        CameVec side = forward.perpendicular();
        CameVec facing = vec(origin).add(forward.multiply(10.0));
        moveLord(CameLord.HUN, vec(origin).add(side.multiply(POST_OFFSET)), facing);
        spawnHunStandIn();
        Location vucubPost = grounded(vec(origin).subtract(side.multiply(POST_OFFSET)));
        if (vucubPost == null) {
            vucubPost = origin.clone();
        }
        vucubPost.setDirection(toVector(facing.subtract(vec(vucubPost))));
        vucub.spawn(vucubPost);
        CameVec hoopCenter = vec(origin).add(forward.multiply(HOOP_FORWARD));
        court.raiseHoop(new Location(origin.getWorld(), hoopCenter.x(), origin.getY() + HOOP_HEIGHT, hoopCenter.z()),
            toVector(forward));
        hun.loop("idle");
        World world = origin.getWorld();
        world.playSound(origin, Sound.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.HOSTILE, 3.0F, 0.5F);
        world.playSound(origin, Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 2.0F, 0.6F);
        for (Player player : context.players()) {
            title(player, "§2§lHUN CAMÉ Y VUCUB CAMÉ",
                "§7El juego de pelota: §fdevolvé la pelota de un golpe §7y metela en el aro", 70);
        }
        task = support.scheduler().repeating("came-" + bossId, 1L, 1L, this::step);
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
        telegraphs.clear();
        dashes.clear();
        court.close();
        vucub.close();
        support.cleanup(bossId);
    }

    @Override
    public boolean blocksProjectile(Projectile projectile) {
        if (stage == Stage.DUO && health != null && !health.down(CameLord.HUN)) {
            return false;
        }
        if (projectile.getShooter() instanceof Player shooter) {
            noticeInvulnerable(shooter, CameLord.HUN);
        }
        return true;
    }

    @Override
    public double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
        // sin atacante (fuego, caida, asfixia) nunca cuenta
        if (attacker == null || health == null || finished) {
            return 0.0;
        }
        if (stage != Stage.DUO || health.down(CameLord.HUN) || pendingDefeat) {
            noticeInvulnerable(attacker, CameLord.HUN);
            return 0.0;
        }
        CameDuoHealth.Hit hit = health.hit(CameLord.HUN, damage, tick);
        if (hit.bothDown()) {
            pendingDefeat = true;
        } else if (hit.downed()) {
            pendingDown = CameLord.HUN;
        }
        return hit.removed();
    }

    @Override
    public OptionalInt phaseOverride() {
        return OptionalInt.of(stage == Stage.BALL_GAME ? 1 : 2);
    }

    /** Toda la pelea es guionada: la habilidad del YAML queda solo por el esquema. */
    @Override
    public boolean suspendsAbilities() {
        return true;
    }

    // ------------------------------------------------------------------ main loop

    private void step() {
        if (stopped) {
            return;
        }
        Warden boss = context.boss();
        if (!boss.isValid() || boss.isDead()) {
            return;
        }
        tick++;
        List<Player> targets = targets();
        switch (stage) {
            case BALL_GAME -> ballGame(targets);
            case AWAKENING -> awakening();
            case DUO -> duo(targets);
        }
        if (stopped) {
            return;
        }
        if (tick % 10L == 0L) {
            syncBar();
            hud();
        }
    }

    /** La barra del runtime = suma de las dos vidas; corrige cualquier diferencia (nunca la deja en cero). */
    private void syncBar() {
        if (pendingDefeat || finished || health.bothDown() || health.total() <= 0.0) {
            return;
        }
        double difference = context.health() - health.total();
        if (difference > 0.5) {
            context.damage(difference, null);
        } else if (difference < -0.5) {
            context.heal(-difference);
        }
    }

    // ------------------------------------------------------------------ phase 1: the ball game

    private void ballGame(List<Player> targets) {
        game.update(tick, !targets.isEmpty()).ifPresent(next -> enterBallStage(next, targets));
        switch (game.stage()) {
            case INTRO, COOLDOWN -> faceAudience(targets);
            case WINDUP -> windupTick(targets);
            case FLIGHT -> flightTick(targets);
            case DONE -> {
            }
        }
        if (stage == Stage.BALL_GAME && tick % 4L == 0L) {
            court.drawRing(game.goals());
        }
    }

    private void enterBallStage(CameBallGame.Stage next, List<Player> targets) {
        switch (next) {
            case WINDUP -> {
                Player target = pick(targets, lastBallTarget);
                if (target == null) {
                    game.resolve(tick, CameBall.Event.DROPPED);
                    return;
                }
                ballTarget = target.getUniqueId();
                lastBallTarget = ballTarget;
                CameLord thrower = game.thrower();
                LordBody body = body(thrower);
                face(thrower, vec(target.getLocation()));
                court.spawnBall(hand(thrower), origin.getWorld());
                body.once("throw");
                Location at = body.location();
                at.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, SoundCategory.HOSTILE, 2.0F, 0.5F);
                at.getWorld().playSound(at, Sound.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.6F, 0.6F);
                title(target, "§6§l¡PELOTA!", "§fCuando llegue, §epegale mirando al aro §fpara devolverla", 30);
                for (Player player : context.players()) {
                    if (!player.getUniqueId().equals(ballTarget)) {
                        actionBar(player, "§6" + thrower.displayName() + " §7le tira la pelota a §f" + target.getName());
                    }
                }
            }
            case FLIGHT -> court.flight().ifPresentOrElse(ball -> {
                ball.launch();
                court.spin();
                Location at = body(game.thrower()).location();
                at.getWorld().playSound(at, Sound.ENTITY_WITCH_THROW, SoundCategory.HOSTILE, 2.0F, 0.5F);
            }, () -> game.resolve(tick, CameBall.Event.DROPPED));
            case INTRO, COOLDOWN, DONE -> {
            }
        }
    }

    private void windupTick(List<Player> targets) {
        Player target = find(targets, ballTarget);
        if (target == null) {
            target = pick(targets, null);
            if (target == null) {
                court.removeBall(false);
                game.resolve(tick, CameBall.Event.DROPPED);
                return;
            }
            ballTarget = target.getUniqueId();
        }
        CameLord thrower = game.thrower();
        if (tick % 5L == 0L) {
            face(thrower, vec(target.getLocation()));
        }
        CameVec at = hand(thrower);
        court.flight().ifPresent(ball -> ball.hold(at));
        court.pollBallHit(); // un golpe con la pelota en la mano no cuenta (ni queda guardado)
        court.syncBall();
        if (tick % 3L == 0L) {
            drawLine(at, chest(target), THROW_LINE, 1.2);
        }
        if (tick % 5L == 0L) {
            actionBar(target, "§6§l¡Te tiran la pelota! §fMirá al aro y pegale cuando llegue");
        }
        if (game.ticksInStage(tick) % 10L == 0L) {
            Location sound = body(thrower).location();
            sound.getWorld().playSound(sound, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.HOSTILE, 1.6F, 0.6F);
        }
    }

    private void flightTick(List<Player> targets) {
        CameBall ball = court.flight().orElse(null);
        if (ball == null) {
            game.resolve(tick, CameBall.Event.DROPPED);
            return;
        }
        court.pollBallHit().ifPresent(hit -> {
            if (!ball.returnable() || !targets.contains(hit.striker())) {
                return;
            }
            CameVec direction = court.hoop().assist(ball.position(), hit.look(), game.goals());
            if (ball.strike(direction)) {
                lastStriker = hit.striker().getUniqueId();
                Location at = location(ball.position());
                at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.6F, 0.6F);
                at.getWorld().playSound(at, Sound.ENTITY_SLIME_JUMP, SoundCategory.HOSTILE, 1.6F, 0.5F);
                at.getWorld().spawnParticle(Particle.CRIT, at, 20, 0.5, 0.5, 0.5, 0.3);
                actionBar(hit.striker(), "§a§l¡Devuelta! §7a ver si entra...");
            }
        });
        CameBall.Event event = CameBall.Event.NONE;
        Set<Player> struck = new LinkedHashSet<>();
        World world = origin.getWorld();
        CameBall.Terrain terrain = terrain(world);
        if (ball.mode() == CameBall.Mode.INBOUND) {
            Player target = find(targets, ballTarget);
            if (target == null) {
                target = nearest(ball.position(), targets);
            }
            if (target == null) {
                event = CameBall.Event.DROPPED;
            } else {
                ballTarget = target.getUniqueId();
                event = ball.advanceInbound(chest(target), terrain);
                if (event == CameBall.Event.IMPACT) {
                    struck.add(target);
                }
                for (Player player : targets) {
                    if (event != CameBall.Event.DROPPED && ball.touches(chest(player))) {
                        struck.add(player);
                        event = CameBall.Event.IMPACT;
                    }
                }
            }
        } else if (ball.mode() == CameBall.Mode.RETURNING) {
            event = ball.advanceReturn(court.hoop(), game.goals(), terrain);
        }
        court.syncBall();
        switch (event) {
            case IMPACT -> {
                struck.forEach(player -> ballImpact(player, ball.position()));
                court.removeBall(true);
                game.resolve(tick, event);
            }
            case DROPPED -> {
                court.removeBall(true);
                game.resolve(tick, event);
            }
            case MISSED -> {
                court.removeBall(true);
                Player striker = lastStriker == null ? null : find(targets, lastStriker);
                if (striker != null) {
                    actionBar(striker, "§7¡Afuera! §fLa pelota no entró en el aro");
                }
                game.resolve(tick, event);
            }
            case GOAL -> goal(targets);
            case NONE -> {
            }
        }
    }

    private void goal(List<Player> targets) {
        double removed = health.goal();
        Player scorer = lastStriker == null ? null : find(targets, lastStriker);
        if (scorer == null && lastStriker != null) {
            scorer = support.plugin().getServer().getPlayer(lastStriker);
        }
        court.goalBurst();
        court.removeBall(false);
        if (removed > 0.0) {
            context.damage(removed, scorer);
        }
        if (stopped) {
            return;
        }
        CameBallGame.Stage next = game.resolve(tick, CameBall.Event.GOAL);
        int goals = game.goals();
        court.narrow(goals);
        for (CameLord lord : CameLord.values()) {
            Location at = body(lord).location();
            at.getWorld().playSound(at, Sound.ENTITY_WITHER_SKELETON_HURT, SoundCategory.HOSTILE, 2.0F, 0.6F);
            at.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, at.clone().add(0, 2.0, 0), 10, 0.5, 0.8, 0.5, 0.1);
        }
        String who = scorer == null ? "" : "§f" + scorer.getName() + " §7";
        for (Player player : context.players()) {
            if (next == CameBallGame.Stage.DONE) {
                title(player, "§a§l¡GOL! §f" + goals + "/" + CameHoop.GOALS_TO_WIN, who + "§7¡El aro se rompe!", 40);
            } else {
                title(player, "§a§l¡GOL! §f" + goals + "/" + CameHoop.GOALS_TO_WIN,
                    who + "§7Los dos señores sufren. §eEl aro se achica", 40);
            }
        }
        if (next == CameBallGame.Stage.DONE) {
            enterAwakening();
        }
    }

    /** El pelotazo: la mitad de la vida maxima, sin armadura; si no le alcanza la vida, lo mata por la via normal. */
    private void ballImpact(Player player, CameVec ballAt) {
        AttributeInstance maximum = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maximum == null ? 20.0 : maximum.getValue();
        CameBallImpact impact = CameBallImpact.against(player.getHealth(), maxHealth);
        CameVec push = vec(player.getLocation()).subtract(ballAt).horizontal();
        if (impact.lethal()) {
            player.damage(1000.0, context.boss());
        } else {
            player.setHealth(Math.min(maxHealth, impact.remainingHealth()));
            player.playHurtAnimation(0.0F);
            player.setVelocity(new Vector(push.x() * 1.2, 0.45, push.z() * 1.2));
            actionBar(player, "§c§l¡Pelotazo! §7Perdiste la mitad de tu vida");
        }
        Location at = player.getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.6F, 0.5F);
        at.getWorld().playSound(at, Sound.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 1.6F, 0.4F);
        at.getWorld().spawnParticle(Particle.EXPLOSION, at.clone().add(0, 1.0, 0), 1, 0, 0, 0, 0);
    }

    private void enterAwakening() {
        stage = Stage.AWAKENING;
        stageStart = tick;
        court.removeBall(false);
        court.shatterHoop();
        hun.loop("idle");
        vucub.loop("idle");
        hun.once("rage");
        vucub.once("rage");
        World world = origin.getWorld();
        world.playSound(origin, Sound.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 3.0F, 0.6F);
        world.playSound(origin, Sound.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.HOSTILE, 3.0F, 0.4F);
        for (Player player : context.players()) {
            title(player, "§4§lLOS SEÑORES DE XIBALBÁ",
                "§7Cada uno tiene su vida. §fSi cae uno, el otro lo revive en 10 s: §cvoltealos juntos", 80);
        }
    }

    private void awakening() {
        if (tick % 4L == 0L) {
            for (CameLord lord : CameLord.values()) {
                Location at = body(lord).location();
                at.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 2.2, 0), 6, 0.6, 1.0, 0.6, 0.02);
            }
        }
        if (tick - stageStart >= AWAKENING_TICKS) {
            stage = Stage.DUO;
            stageStart = tick;
            director = new CameDuoDirector(tick);
        }
    }

    // ------------------------------------------------------------------ phase 2: the duo

    private void duo(List<Player> targets) {
        health.tick(tick).ifPresent(this::revived);
        for (CameVucub.Hit hit : vucub.pollHits()) {
            vucubHit(hit);
            if (stopped) {
                return;
            }
        }
        if (pendingDown != null) {
            CameLord fallen = pendingDown;
            pendingDown = null;
            lordDown(fallen);
        }
        if (pendingDefeat || health.bothDown()) {
            finish();
            return;
        }
        CameLord fallen = health.awaitingRevive().orElse(null);
        if (fallen != null) {
            reviveTick(fallen);
        } else {
            director.maybeStart(tick, !targets.isEmpty(), bound -> ThreadLocalRandom.current().nextInt(bound))
                .ifPresent(pattern -> startPattern(pattern, targets));
            for (CameDuoDirector.Due due : director.due(tick)) {
                handle(due, targets);
            }
            dashTick(targets);
            if (!director.running(tick)) {
                chase(targets);
            }
        }
        if (tick % 2L == 0L) {
            drawTelegraphs();
        }
    }

    private void vucubHit(CameVucub.Hit hit) {
        if (stage != Stage.DUO || health.down(CameLord.VUCUB) || pendingDefeat || finished) {
            if (hit.attacker() != null) {
                noticeInvulnerable(hit.attacker(), CameLord.VUCUB);
            }
            return;
        }
        if (hit.attacker() == null) {
            return; // como con Hun: sin atacante no cuenta
        }
        CameDuoHealth.Hit result = health.hit(CameLord.VUCUB, hit.damage(), tick);
        if (result.removed() > 0.0) {
            Location at = vucub.location().add(0, 1.8, 0);
            at.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, at, 6, 0.5, 0.8, 0.5, 0.05);
            at.getWorld().playSound(at, Sound.ENTITY_SKELETON_HURT, SoundCategory.HOSTILE, 1.4F, 0.6F);
            if (result.bothDown()) {
                pendingDefeat = true;
            }
            context.damage(result.removed(), hit.attacker());
            if (stopped) {
                return;
            }
        }
        if (result.downed() && !result.bothDown()) {
            lordDown(CameLord.VUCUB);
        }
    }

    private void lordDown(CameLord lord) {
        director.abort();
        telegraphs.clear();
        dashes.clear();
        LordBody body = body(lord);
        body.loop("death");
        Location at = body.location();
        World world = at.getWorld();
        world.playSound(at, Sound.ENTITY_SKELETON_DEATH, SoundCategory.HOSTILE, 2.0F, 0.5F);
        world.spawnParticle(Particle.SOUL, at.clone().add(0, 1.0, 0), 30, 0.6, 0.6, 0.6, 0.05);
        if (health.bothDown()) {
            pendingDefeat = true;
            return;
        }
        CameLord reviver = lord.other();
        CameVec bodyAt = vec(at);
        moveLord(reviver, CameStrikes.besides(vec(body(reviver).location()), bodyAt, 1.6), bodyAt);
        body(reviver).loop("revive");
        world.playSound(at, Sound.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 2.0F, 0.5F);
        for (Player player : context.players()) {
            title(player, "§c§l¡CAYÓ " + lord.displayName().toUpperCase(Locale.ROOT) + "!",
                "§f" + reviver.displayName() + " lo revive en 10 s: §e¡matalo mientras canaliza!", 50);
        }
    }

    private void reviveTick(CameLord fallen) {
        CameLord reviver = fallen.other();
        Location bodyAt = body(fallen).location();
        face(reviver, vec(bodyAt));
        World world = bodyAt.getWorld();
        long left = health.reviveTicksLeft(tick);
        if (tick % 3L == 0L) {
            double radius = 0.6 + 1.6 * left / (double) CameDuoHealth.REVIVE_TICKS;
            for (int index = 0; index < 12; index++) {
                double angle = index * Math.PI / 6.0 + tick * 0.1;
                world.spawnParticle(Particle.DUST, bodyAt.clone().add(Math.cos(angle) * radius, 0.2,
                    Math.sin(angle) * radius), 1, 0, 0, 0, 0, REVIVE);
            }
            world.spawnParticle(Particle.SOUL, bodyAt.clone().add(0, 0.6, 0), 2, 0.4, 0.2, 0.4, 0.02);
            Location hand = body(reviver).location().add(0, 1.0, 0);
            world.spawnParticle(Particle.SCULK_SOUL, hand, 1, 0.2, 0.2, 0.2, 0.01);
        }
        if (tick % 20L == 0L) {
            world.playSound(bodyAt, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 2.0F, 0.7F);
        }
        if (tick % 5L == 0L) {
            Component line = LEGACY.deserialize("§5" + reviver.displayName() + " revive a " + fallen.displayName()
                + ": §f" + seconds(left) + " s §7| §e¡pegale ahora, recibe +50% de daño!");
            for (Player player : context.players()) {
                player.sendActionBar(line);
            }
        }
    }

    private void revived(CameDuoHealth.Revival revival) {
        CameLord lord = revival.lord();
        LordBody body = body(lord);
        body.loop("idle");
        body.once("rage");
        body(lord.other()).loop("idle");
        context.heal(revival.restored());
        Location at = body.location();
        at.getWorld().playSound(at, Sound.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.HOSTILE, 2.5F, 0.4F);
        at.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0, 1.6, 0), 40, 0.6, 1.0, 0.6, 0.04);
        for (Player player : context.players()) {
            title(player, "§4§l" + lord.displayName().toUpperCase(Locale.ROOT) + " VUELVE",
                "§7Voltealos juntos: §fsi queda uno en pie, revive al otro", 50);
        }
        director.resume(tick);
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        telegraphs.clear();
        dashes.clear();
        hun.loop("death");
        vucub.loop("death");
        context.defeat();
    }

    // ------------------------------------------------------------------ coordinated patterns

    private void startPattern(CameDuoDirector.Pattern pattern, List<Player> targets) {
        Player target = pick(targets, lastPatternTarget);
        if (target == null) {
            director.abort();
            director.resume(tick);
            return;
        }
        patternTarget = target.getUniqueId();
        lastPatternTarget = patternTarget;
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        axis = new CameVec(Math.cos(angle), 0.0, Math.sin(angle));
        telegraphs.clear();
        String warning = switch (pattern) {
            case PINCER -> "§c§l¡Pinza de Xibalbá! §fSalí de los dos carriles";
            case PIN_AND_STRIKE -> "§c§l¡Hun te clava al piso y Vucub salta! §fSalí del círculo";
            case ALTERNATING -> "§c§l¡Combo de los señores! §fSeguí moviéndote";
            case FLANK_SWEEP -> "§c§l¡Barrida doble! §fCorrete de costado";
        };
        actionBar(target, warning);
    }

    private void handle(CameDuoDirector.Due due, List<Player> targets) {
        CameDuoDirector.Beat beat = due.beat();
        CameLord lord = beat.lord();
        LordBody body = body(lord);
        if (due.cue() == CameDuoDirector.Cue.TELEGRAPH) {
            Player target = find(targets, patternTarget);
            if (target == null) {
                target = nearest(vec(body.location()), targets);
            }
            if (target == null) {
                return;
            }
            CameVec feet = vec(target.getLocation());
            long landAt = tick + (beat.landAt() - beat.telegraphAt());
            switch (beat.move()) {
                case DASH -> {
                    CameVec start = moveLord(lord, CameStrikes.pincerStart(feet, axis, lord), feet);
                    CameVec end = arena.clamp(CameStrikes.dashEnd(start, feet), CameArena.MOVE_RADIUS);
                    telegraphs.add(new Telegraph(lord, beat.move(), Shape.LANE, start, end, CameStrikes.DASH_WIDTH,
                        tick, landAt));
                    body.loop("idle");
                    sound(body.location(), Sound.ENTITY_RAVAGER_ROAR, 1.4F, 1.2F);
                }
                case ROOT_SLAM -> {
                    moveLord(lord, CameStrikes.besides(vec(body.location()), feet, 2.0), feet);
                    telegraphs.add(new Telegraph(lord, beat.move(), Shape.CIRCLE, feet, feet, CameStrikes.ROOT_RADIUS,
                        tick, landAt));
                    body.once("slam");
                    sound(body.location(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.4F, 0.6F);
                }
                case LEAP_STRIKE -> {
                    face(lord, feet);
                    telegraphs.add(new Telegraph(lord, beat.move(), Shape.CIRCLE, feet, feet, CameStrikes.LEAP_RADIUS,
                        tick, landAt));
                    body.once("slam");
                    Location at = body.location();
                    at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 0.2, 0), 12, 0.6, 0.1, 0.6, 0.05);
                    sound(at, Sound.ENTITY_GOAT_LONG_JUMP, 1.6F, 0.5F);
                }
                case STRIKE -> {
                    moveLord(lord, CameStrikes.besides(vec(body.location()), feet, CameStrikes.STRIKE_STAND_OFF), feet);
                    telegraphs.add(new Telegraph(lord, beat.move(), Shape.CIRCLE, feet, feet,
                        CameStrikes.STRIKE_RADIUS, tick, landAt));
                    body.once("attack");
                    sound(body.location(), Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.4F, 0.8F);
                }
                case SWEEP -> {
                    CameVec from = moveLord(lord, CameStrikes.flankStart(feet, axis, lord), feet);
                    CameVec facing = new CameVec(feet.x() - from.x(), 0.0, feet.z() - from.z()).horizontal();
                    telegraphs.add(new Telegraph(lord, beat.move(), Shape.CONE, from, facing, CameStrikes.SWEEP_RANGE,
                        tick, landAt));
                    body.once("attack");
                    sound(body.location(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1.4F, 0.6F);
                }
            }
            return;
        }
        Telegraph telegraph = takeTelegraph(lord, beat.move());
        if (telegraph == null) {
            return;
        }
        LivingEntity source = source(lord);
        switch (beat.move()) {
            case DASH -> {
                CameVec direction = telegraph.b().subtract(telegraph.a()).horizontal();
                dashes.put(lord, new Dash(direction, CameDuoDirector.DASH_TICKS));
                body.loop("dash");
                sound(body.location(), Sound.ENTITY_RAVAGER_ATTACK, 2.0F, 0.6F);
            }
            case ROOT_SLAM -> {
                hitPlayers(targets, feet -> CameStrikes.inCircle(telegraph.a(), telegraph.size(), feet),
                    CameStrikes.ROOT_SLAM_DAMAGE, source, player -> {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, true, true));
                        actionBar(player, "§f¡Los huesos de Xibalbá te agarran los pies!");
                    });
                impactEffects(telegraph.a(), telegraph.size(), Material.BONE_BLOCK);
            }
            case LEAP_STRIKE -> {
                moveLord(lord, telegraph.a(), vec(origin));
                hitPlayers(targets, feet -> CameStrikes.inCircle(telegraph.a(), telegraph.size(), feet),
                    CameStrikes.LEAP_DAMAGE, source, null);
                impactEffects(telegraph.a(), telegraph.size(), Material.DEEPSLATE);
            }
            case STRIKE -> {
                hitPlayers(targets, feet -> CameStrikes.inCircle(telegraph.a(), telegraph.size(), feet),
                    CameStrikes.STRIKE_DAMAGE, source, null);
                Location at = location(telegraph.a());
                at.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(0, 1.0, 0), 3, 0.8, 0.3, 0.8, 0.0);
                sound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6F, 0.6F);
            }
            case SWEEP -> {
                hitPlayers(targets, feet -> CameStrikes.inCone(telegraph.a(), telegraph.b(),
                    CameStrikes.SWEEP_HALF_ANGLE, telegraph.size(), feet), CameStrikes.SWEEP_DAMAGE, source, null);
                for (double d = 1.5; d <= telegraph.size(); d += 1.5) {
                    Location at = location(telegraph.a().add(telegraph.b().multiply(d)));
                    at.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(0, 1.0, 0), 1, 0.4, 0.2, 0.4, 0.0);
                }
                sound(location(telegraph.a()), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2.0F, 0.5F);
            }
        }
    }

    private void dashTick(List<Player> targets) {
        Iterator<Map.Entry<CameLord, Dash>> iterator = dashes.entrySet().iterator();
        double stepLength = CameStrikes.DASH_LENGTH / CameDuoDirector.DASH_TICKS;
        while (iterator.hasNext()) {
            Map.Entry<CameLord, Dash> entry = iterator.next();
            CameLord lord = entry.getKey();
            Dash dash = entry.getValue();
            LordBody body = body(lord);
            CameVec from = vec(body.location());
            CameVec want = from.add(dash.direction.multiply(stepLength));
            CameVec clamped = arena.clamp(want, CameArena.MOVE_RADIUS);
            Location next = grounded(clamped);
            boolean blocked = next == null || clamped.horizontalDistance(want) > 1.0e-6
                || Math.abs(next.getY() - from.y()) > 1.1;
            if (!blocked) {
                next.setDirection(toVector(dash.direction));
                body.moveTo(next);
                CameVec at = vec(next);
                for (Player player : targets) {
                    CameVec feet = vec(player.getLocation());
                    if (!dash.hit.contains(player.getUniqueId()) && CameStrikes.sameHeight(at.y(), feet.y())
                        && at.horizontalDistance(feet) <= CameStrikes.DASH_WIDTH + 0.4) {
                        dash.hit.add(player.getUniqueId());
                        player.damage(CameStrikes.DASH_DAMAGE, source(lord));
                        player.setVelocity(new Vector(dash.direction.x() * 1.2, 0.4, dash.direction.z() * 1.2));
                    }
                }
                if (tick % 2L == 0L) {
                    next.getWorld().spawnParticle(Particle.CLOUD, next.clone().add(0, 0.2, 0), 4, 0.4, 0.1, 0.4, 0.02);
                }
            }
            dash.remaining--;
            if (blocked || dash.remaining <= 0) {
                body.loop("idle");
                iterator.remove();
            }
        }
    }

    /** Entre patrones cada señor camina hacia el jugador mas cercano a el. */
    private void chase(List<Player> targets) {
        for (CameLord lord : CameLord.values()) {
            if (dashes.containsKey(lord)) {
                continue;
            }
            LordBody body = body(lord);
            CameVec at = vec(body.location());
            Player nearest = nearest(at, targets);
            if (nearest == null) {
                body.loop("idle");
                continue;
            }
            CameVec feet = vec(nearest.getLocation());
            if (at.horizontalDistance(feet) <= CHASE_STOP) {
                body.loop("idle");
                if (tick % 10L == 0L) {
                    face(lord, feet);
                }
                continue;
            }
            CameVec direction = new CameVec(feet.x() - at.x(), 0.0, feet.z() - at.z()).horizontal();
            Location next = grounded(arena.clamp(at.add(direction.multiply(WALK_SPEED)), CameArena.MOVE_RADIUS));
            if (next == null || Math.abs(next.getY() - at.y()) > 1.1) {
                body.loop("idle");
                continue;
            }
            next.setDirection(toVector(direction));
            body.moveTo(next);
            body.loop("walk");
        }
    }

    private Telegraph takeTelegraph(CameLord lord, CameDuoDirector.Move move) {
        Iterator<Telegraph> iterator = telegraphs.iterator();
        while (iterator.hasNext()) {
            Telegraph telegraph = iterator.next();
            if (telegraph.lord() == lord && telegraph.move() == move) {
                iterator.remove();
                return telegraph;
            }
        }
        return null;
    }

    private void drawTelegraphs() {
        World world = origin.getWorld();
        for (Telegraph telegraph : telegraphs) {
            double span = Math.max(1.0, telegraph.landAt() - telegraph.startedAt());
            double progress = Math.max(0.0, Math.min(1.0, (tick - telegraph.startedAt()) / span));
            switch (telegraph.shape()) {
                case CIRCLE -> {
                    CameVec center = telegraph.a();
                    int points = (int) Math.max(14, telegraph.size() * 8);
                    for (int index = 0; index < points; index++) {
                        double angle = index * Math.PI * 2.0 / points;
                        world.spawnParticle(Particle.DUST, center.x() + Math.cos(angle) * telegraph.size(),
                            center.y() + 0.15, center.z() + Math.sin(angle) * telegraph.size(), 1, 0, 0, 0, 0, DANGER);
                        if (index % 2 == 0) {
                            double inner = telegraph.size() * progress;
                            world.spawnParticle(Particle.DUST, center.x() + Math.cos(angle) * inner, center.y() + 0.15,
                                center.z() + Math.sin(angle) * inner, 1, 0, 0, 0, 0, DANGER_FILL);
                        }
                    }
                }
                case LANE -> {
                    CameVec from = telegraph.a();
                    CameVec to = telegraph.b();
                    CameVec direction = to.subtract(from).horizontal();
                    CameVec side = direction.perpendicular().multiply(telegraph.size());
                    double length = from.horizontalDistance(to);
                    for (double d = 0.0; d <= length; d += 0.9) {
                        CameVec centre = from.add(direction.multiply(d));
                        world.spawnParticle(Particle.DUST, centre.x() + side.x(), centre.y() + 0.15,
                            centre.z() + side.z(), 1, 0, 0, 0, 0, DANGER);
                        world.spawnParticle(Particle.DUST, centre.x() - side.x(), centre.y() + 0.15,
                            centre.z() - side.z(), 1, 0, 0, 0, 0, DANGER);
                        if (d <= length * progress) {
                            world.spawnParticle(Particle.DUST, centre.x(), centre.y() + 0.15, centre.z(), 1, 0, 0, 0,
                                0, DANGER_FILL);
                        }
                    }
                }
                case CONE -> {
                    CameVec from = telegraph.a();
                    double base = Math.atan2(telegraph.b().z(), telegraph.b().x());
                    double half = Math.toRadians(CameStrikes.SWEEP_HALF_ANGLE);
                    for (double d = 1.0; d <= telegraph.size(); d += 0.9) {
                        for (double sign : new double[]{-1.0, 1.0}) {
                            double angle = base + sign * half;
                            world.spawnParticle(Particle.DUST, from.x() + Math.cos(angle) * d, from.y() + 0.15,
                                from.z() + Math.sin(angle) * d, 1, 0, 0, 0, 0, DANGER);
                        }
                    }
                    for (int index = 0; index <= 10; index++) {
                        double angle = base - half + 2.0 * half * index / 10.0;
                        world.spawnParticle(Particle.DUST, from.x() + Math.cos(angle) * telegraph.size(),
                            from.y() + 0.15, from.z() + Math.sin(angle) * telegraph.size(), 1, 0, 0, 0, 0, DANGER);
                        double inner = telegraph.size() * progress;
                        world.spawnParticle(Particle.DUST, from.x() + Math.cos(angle) * inner, from.y() + 0.15,
                            from.z() + Math.sin(angle) * inner, 1, 0, 0, 0, 0, DANGER_FILL);
                    }
                }
            }
        }
    }

    private void hitPlayers(List<Player> targets, Predicate<CameVec> inside, double damage, LivingEntity source,
                            Consumer<Player> extra) {
        for (Player player : targets) {
            if (inside.test(vec(player.getLocation()))) {
                player.damage(damage, source);
                if (extra != null && player.isValid() && !player.isDead()) {
                    extra.accept(player);
                }
            }
        }
    }

    private void impactEffects(CameVec center, double radius, Material material) {
        Location at = location(center).add(0, 0.2, 0);
        World world = at.getWorld();
        world.spawnParticle(Particle.BLOCK, at, (int) (radius * 20), radius * 0.6, 0.2, radius * 0.6, 0.1,
            material.createBlockData());
        world.spawnParticle(Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2F, 0.7F);
        world.playSound(at, Sound.BLOCK_BONE_BLOCK_BREAK, SoundCategory.HOSTILE, 2.0F, 0.5F);
    }

    // ------------------------------------------------------------------ hud and notices

    private void hud() {
        String line;
        switch (stage) {
            case BALL_GAME -> {
                if (game.stage() == CameBallGame.Stage.WINDUP) {
                    return; // el blanco ya tiene su aviso; los demas el de quien tira
                }
                line = "§6Goles en el aro: §f" + game.goals() + "/" + CameHoop.GOALS_TO_WIN
                    + " §7| §eSolo los goles les hacen daño";
            }
            case AWAKENING -> line = "§4Los señores de Xibalbá se levantan...";
            default -> {
                if (health.awaitingRevive().isPresent()) {
                    return; // la cuenta de la revivida tiene su propia linea
                }
                line = "§c" + CameLord.HUN.displayName() + ": §f" + Math.round(health.health(CameLord.HUN))
                    + " §7| §c" + CameLord.VUCUB.displayName() + ": §f" + Math.round(health.health(CameLord.VUCUB))
                    + " §7| §evoltealos juntos";
            }
        }
        Component component = LEGACY.deserialize(line);
        for (Player player : context.players()) {
            player.sendActionBar(component);
        }
    }

    private void noticeInvulnerable(Player player, CameLord lord) {
        Long last = lastNotice.get(player.getUniqueId());
        if (last != null && tick - last < NOTICE_GAP) {
            return;
        }
        lastNotice.put(player.getUniqueId(), tick);
        Location at = body(lord).location().add(0, 1.8, 0);
        at.getWorld().playSound(at, Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.0F, 0.6F);
        at.getWorld().spawnParticle(Particle.ENCHANTED_HIT, at, 8, 0.5, 0.8, 0.5, 0.1);
        String message;
        if (stage == Stage.BALL_GAME) {
            message = "§7Los señores no reciben golpes: §esolo los goles en el aro les hacen daño";
        } else if (stage == Stage.AWAKENING) {
            message = "§7Los señores de Xibalbá se están levantando...";
        } else {
            message = "§7" + lord.displayName() + " está caído: §fpegale a " + lord.other().displayName();
        }
        actionBar(player, message);
    }

    // ------------------------------------------------------------------ bodies and movement

    private LordBody body(CameLord lord) {
        return lord == CameLord.HUN ? hun : vucub;
    }

    private LivingEntity source(CameLord lord) {
        return lord == CameLord.HUN || !vucub.valid() ? context.boss() : vucub.entity();
    }

    /** Donde tiene la pelota en alto el que la tira. */
    private CameVec hand(CameLord lord) {
        Location at = body(lord).location();
        CameVec facing = vec(at.getDirection()).horizontal();
        return vec(at).add(facing.multiply(0.4)).add(new CameVec(0.0, lord == CameLord.HUN ? HUN_HAND : VUCUB_HAND, 0.0));
    }

    /** Lleva al señor a {@code want} (adentro de la sala y con piso), mirando a {@code facing}; devuelve donde quedo. */
    private CameVec moveLord(CameLord lord, CameVec want, CameVec facing) {
        LordBody body = body(lord);
        Location next = grounded(arena.clamp(want, CameArena.MOVE_RADIUS));
        if (next == null) {
            face(lord, facing);
            return vec(body.location());
        }
        Vector direction = toVector(new CameVec(facing.x() - next.getX(), 0.0, facing.z() - next.getZ()).horizontal());
        next.setDirection(direction);
        Location from = body.location();
        if (from.distanceSquared(next) > 4.0) {
            from.getWorld().spawnParticle(Particle.LARGE_SMOKE, from.clone().add(0, 1.2, 0), 10, 0.4, 0.8, 0.4, 0.02);
            next.getWorld().spawnParticle(Particle.LARGE_SMOKE, next.clone().add(0, 1.2, 0), 10, 0.4, 0.8, 0.4, 0.02);
        }
        body.moveTo(next);
        return vec(next);
    }

    private void face(CameLord lord, CameVec target) {
        LordBody body = body(lord);
        Location at = body.location();
        CameVec direction = new CameVec(target.x() - at.getX(), 0.0, target.z() - at.getZ());
        if (direction.length() < 1.0e-3) {
            return;
        }
        at.setDirection(toVector(direction.horizontal()));
        body.moveTo(at);
    }

    private void faceAudience(List<Player> targets) {
        if (tick % 10L != 0L) {
            return;
        }
        for (CameLord lord : CameLord.values()) {
            Player nearest = nearest(vec(body(lord).location()), targets);
            if (nearest != null) {
                face(lord, vec(nearest.getLocation()));
            }
        }
    }

    /** Un lugar con piso y tres bloques libres en la columna de {@code at}, cerca de la altura del origen. */
    private Location grounded(CameVec at) {
        World world = origin.getWorld();
        CameBall.Terrain terrain = terrain(world);
        int x = (int) Math.floor(at.x());
        int z = (int) Math.floor(at.z());
        int top = (int) Math.floor(Math.max(at.y(), origin.getY())) + 2;
        for (int y = top; y >= top - 7; y--) {
            if (terrain.solid(x, y - 1, z) && !terrain.solid(x, y, z) && !terrain.solid(x, y + 1, z)
                && !terrain.solid(x, y + 2, z)) {
                return new Location(world, at.x(), y, at.z());
            }
        }
        return null;
    }

    private void spawnHunStandIn() {
        try {
            WitherSkeleton standIn = support.spawnBedrockStandIn(bossId, context.boss(), WitherSkeleton.class,
                HUN_STAND_IN_SCALE);
            standIn.customName(Component.text(CameLord.HUN.displayName()));
            EntityEquipment equipment = standIn.getEquipment();
            if (equipment != null) {
                equipment.clear();
                equipment.setHelmet(CameVucub.jadeHelmet());
                equipment.setHelmetDropChance(0.0F);
            }
        } catch (RuntimeException failure) {
            support.logger().warning("[Camé] Sin doble para Bedrock: " + failure.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Los jugadores que pueden ser blanco: sin creativo y adentro de la cancha (la audiencia de 48 bloques llega a
     * pasillos detras de las paredes). Sin nadie adentro los señores esperan.
     */
    private List<Player> targets() {
        List<Player> list = new ArrayList<>();
        for (Player player : context.players()) {
            GameMode mode = player.getGameMode();
            Location at = player.getLocation();
            if ((mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && at.getWorld().equals(origin.getWorld())
                && arena.contains(at.getX(), at.getY(), at.getZ())
                && (room == null || room.contains(at.getWorld().getName(), at.getX(), at.getY(), at.getZ()))) {
                list.add(player);
            }
        }
        return list;
    }

    /** La caja de la mazmorra; sin mazmorra (por ejemplo /boss spawn a mano) vale solo el radio. */
    private DungeonBounds findRoom(Location at) {
        try {
            return support.locator().dungeonBounds(at).orElse(null);
        } catch (RuntimeException failure) {
            support.logger().warning("[Camé] No se pudo leer la mazmorra: " + failure.getMessage());
            return null;
        }
    }

    private static Player pick(List<Player> players, UUID avoid) {
        if (players.isEmpty()) {
            return null;
        }
        List<Player> options = new ArrayList<>(players);
        if (options.size() > 1) {
            options.removeIf(player -> player.getUniqueId().equals(avoid));
        }
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }

    private static Player find(List<Player> players, UUID id) {
        if (id == null) {
            return null;
        }
        for (Player player : players) {
            if (player.getUniqueId().equals(id)) {
                return player;
            }
        }
        return null;
    }

    private static Player nearest(CameVec from, List<Player> players) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : players) {
            double distance = vec(player.getLocation()).distance(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static CameBall.Terrain terrain(World world) {
        return (x, y, z) -> !world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))
            .isPassable();
    }

    private void drawLine(CameVec from, CameVec to, Particle.DustOptions dust, double every) {
        World world = origin.getWorld();
        double length = from.distance(to);
        CameVec direction = to.subtract(from).normalize(new CameVec(0.0, 0.0, 1.0));
        for (double d = 0.0; d <= length; d += every) {
            CameVec at = from.add(direction.multiply(d));
            world.spawnParticle(Particle.DUST, at.x(), at.y(), at.z(), 1, 0, 0, 0, 0, dust);
        }
    }

    private void sound(Location at, Sound sound, float volume, float pitch) {
        at.getWorld().playSound(at, sound, SoundCategory.HOSTILE, volume, pitch);
    }

    private Location location(CameVec at) {
        return new Location(origin.getWorld(), at.x(), at.y(), at.z());
    }

    private static CameVec chest(Player player) {
        Location at = player.getLocation();
        return new CameVec(at.getX(), at.getY() + 1.1, at.getZ());
    }

    static CameVec vec(Location at) {
        return new CameVec(at.getX(), at.getY(), at.getZ());
    }

    private static CameVec vec(Vector vector) {
        return new CameVec(vector.getX(), vector.getY(), vector.getZ());
    }

    private static Vector toVector(CameVec vector) {
        return new Vector(vector.x(), vector.y(), vector.z());
    }

    private static String seconds(long ticks) {
        return String.valueOf(Math.round(ticks / 2.0) / 10.0);
    }

    private static void actionBar(Player player, String legacy) {
        player.sendActionBar(LEGACY.deserialize(legacy));
    }

    private static void title(Player player, String title, String subtitle, int stayTicks) {
        player.showTitle(Title.title(LEGACY.deserialize(title), LEGACY.deserialize(subtitle),
            Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(stayTicks * 50L), Duration.ofMillis(500))));
    }

    /** Hun Camé: el Warden del runtime con el modelo del jefe. */
    private final class HunBody implements LordBody {

        @Override
        public boolean valid() {
            return context.boss().isValid();
        }

        @Override
        public Location location() {
            return context.boss().getLocation();
        }

        @Override
        public void moveTo(Location to) {
            context.boss().teleport(to);
        }

        @Override
        public void loop(String animation) {
            if (animation.equals(hunLoop)) {
                return;
            }
            try {
                if (hunLoop != null) {
                    context.models().stopAnimation(context.model(), hunLoop);
                }
                context.animate(animation);
            } catch (RuntimeException ignored) {
                // cosmetico: la pelea no depende del modelo
            }
            hunLoop = animation;
        }

        @Override
        public void once(String animation) {
            try {
                context.models().animateOnce(context.model(), animation, () -> {
                });
            } catch (RuntimeException ignored) {
                // cosmetico
            }
        }
    }
}
