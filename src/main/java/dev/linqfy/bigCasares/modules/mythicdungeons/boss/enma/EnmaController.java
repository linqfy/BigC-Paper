package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
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
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * El combate de Enma en el servidor: una tarea por tick (via {@link MythicBossSupport#scheduler()}) que mueve al
 * Warden, dibuja los avisos, simula las balas y aplica los golpes. Las decisiones son de {@link EnmaFightRules},
 * {@link EnmaMirrorTrial}, {@link EnmaJudgement}, {@link EnmaPattern} y {@link EnmaBullets}. No cambia bloques; todo lo
 * que crea pasa por el support y se borra en {@link #stop()}.
 *
 * <p>Bedrock: ve un Evocador grande en lugar del modelo de Enma, un soporte con un bloque de vidrio (con nombre) en
 * lugar del espejo, los reflejos y oni vanilla tal cual y las balas como particulas; los avisos son particulas en el
 * piso, sonidos, titulos y la barra de accion, que Geyser muestra igual. Las animaciones de Java no existen ahi.</p>
 */
final class EnmaController implements BossEncounterController {

    static final String ONI = "oni";
    /** Dano de una bala (tipo proyectil de mob: la armadura lo reduce). */
    static final double BULLET_DAMAGE = 6.0;
    /** Altura de las balas sobre los pies de Enma (a la altura del torso de un jugador). */
    static final double BULLET_HEIGHT = 1.1;
    static final double SLAM_RADIUS = 4.0;
    static final double SLAM_DAMAGE = 7.0;
    static final double SPIN_RADIUS = 4.5;
    static final double SPIN_DAMAGE = 8.0;
    private static final double MIRROR_DISTANCE = 3.5;
    private static final double WALK_PER_TICK = 0.1;
    private static final double SPIN_WALK_PER_TICK = 0.09;
    private static final double KEEP_DISTANCE = 5.0;
    private static final double STAND_IN_SCALE = 1.9;
    private static final int FEEDBACK_COOLDOWN = 30;
    private static final int MIRROR_REMOVE_AFTER = 30;
    /** Ticks seguidos sin poder volver a crear el espejo antes de darlo por quebrado (si no, Enma seria inmune). */
    private static final int MIRROR_RESPAWN_TICKS = 40;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Particle.DustOptions BULLET = new Particle.DustOptions(Color.fromRGB(230, 30, 40), 1.7F);
    private static final Particle.DustOptions WARN = new Particle.DustOptions(Color.fromRGB(255, 70, 30), 1.6F);
    private static final Particle.DustOptions WARN_LATE = new Particle.DustOptions(Color.fromRGB(255, 220, 90), 1.8F);
    private static final Particle.DustOptions MIRROR_GLOW = new Particle.DustOptions(Color.fromRGB(210, 230, 255), 1.4F);
    private static final Particle.DustOptions SHIELD = new Particle.DustOptions(Color.fromRGB(150, 90, 255), 1.3F);

    private final MythicBossSupport support;
    private final BossEncounterContext context;
    private final UUID bossId;
    private final RandomGenerator random;
    private final EnmaFightRules rules = new EnmaFightRules();
    private final Map<UUID, Integer> feedbackAt = new HashMap<>();
    private final Set<UUID> spinHits = new HashSet<>();
    private final List<LivingEntity> oni = new ArrayList<>();

    private EnmaTerrain terrain;
    private EnmaBullets bullets;
    private EnmaMirror mirror;
    private EnmaReflections reflections;
    private EnmaMirrorTrial trial;
    private EnmaJudgement judgement;
    private BukkitTask task;
    private Vector forward = new Vector(0, 0, -1);
    private Location home;
    private UUID volleyTarget;
    private double patternBase;
    private List<Double> aims = List.of();
    private int shatteredAt;
    private int lastCrack;
    private int mirrorMissing;
    private int tick;
    private boolean started;
    private boolean ready;
    private boolean stopped;
    private String baseLoop = "idle";

    EnmaController(MythicBossSupport support, BossEncounterContext context, RandomGenerator random) {
        this.support = support;
        this.context = context;
        this.bossId = context.bossInstanceId();
        this.random = random;
    }

    // ------------------------------------------------------------------ BossEncounterController

    @Override
    public void start() {
        if (started || stopped) {
            return;
        }
        started = true;
        Warden boss = context.boss();
        home = boss.getLocation().clone();
        EnmaArena arena = EnmaArena.around(home.getX(), home.getY(), home.getZ());
        terrain = new EnmaTerrain(home, arena, support.locator().dungeonBounds(home));
        bullets = new EnmaBullets(arena);
        reflections = new EnmaReflections(support, bossId);
        List<Player> players = arenaPlayers();
        Player nearest = nearest(players, home);
        if (nearest != null) {
            forward = horizontal(nearest.getLocation().toVector().subtract(home.toVector()));
        } else {
            forward = horizontal(home.getDirection());
        }
        face(boss, home.clone().add(forward));
        mirror = new EnmaMirror(support, bossId);
        mirror.spawn(mirrorSpot());
        trial = new EnmaMirrorTrial(tick, EnmaMirrorTrial.durabilityFor(players.size()));
        try {
            support.spawnBedrockStandIn(bossId, boss, Evoker.class, STAND_IN_SCALE);
        } catch (RuntimeException unavailable) {
            // sin dobles (modulo sin Geyser): nadie de Bedrock que atender
        }
        World world = home.getWorld();
        world.playSound(home, Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 2.0F, 0.5F);
        world.playSound(home, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.HOSTILE, 2.0F, 0.7F);
        for (Player player : players) {
            title(player, "§4§lENMA, REY DEL JUICIO",
                "§7Matá a tus reflejos y rompé el §fEspejo de Johari§7: mientras esté en pie, Enma es intocable", 70);
        }
        task = support.scheduler().repeating("enma-" + bossId, 1L, 1L, this::step);
        ready = true;
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
        if (bullets != null) {
            bullets.clear();
        }
        if (mirror != null) {
            mirror.remove();
        }
        if (reflections != null) {
            reflections.clear();
        }
        oni.clear();
        support.cleanup(bossId);
    }

    @Override
    public boolean blocksProjectile(Projectile projectile) {
        if (!ready || rules.stage() == EnmaFightRules.Stage.JUDGEMENT) {
            return false;
        }
        if (projectile.getShooter() instanceof Player shooter) {
            shieldedFeedback(shooter);
        }
        return true;
    }

    @Override
    public double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
        if (!ready) {
            return damage;
        }
        EnmaFightRules.Verdict verdict = rules.admit(attacker != null);
        switch (verdict) {
            case ADMITTED -> {
                return damage;
            }
            case MIRROR_SHIELD -> {
                if (attacker != null) {
                    shieldedFeedback(attacker);
                }
                Location chest = context.boss().getLocation().add(0, 2.0, 0);
                chest.getWorld().spawnParticle(Particle.DUST, chest, 10, 0.6, 0.9, 0.6, 0, SHIELD);
                chest.getWorld().playSound(chest, Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 0.8F, 0.7F);
                return 0.0;
            }
            default -> {
                return 0.0;
            }
        }
    }

    @Override
    public OptionalInt phaseOverride() {
        return ready ? OptionalInt.of(rules.phase()) : OptionalInt.empty();
    }

    /** Toda la pelea es guionada: la habilidad del YAML queda solo por el esquema. */
    @Override
    public boolean suspendsAbilities() {
        return true;
    }

    // ------------------------------------------------------------------ the loop

    private void step() {
        if (stopped || !ready) {
            return;
        }
        Warden boss = context.boss();
        if (!boss.isValid() || boss.isDead()) {
            return;
        }
        tick++;
        List<Player> players = arenaPlayers();
        EnmaFightRules.Stage changed = rules.update(tick, trial.broken());
        if (changed == EnmaFightRules.Stage.SHATTER) {
            beginShatter(boss, players);
        } else if (changed == EnmaFightRules.Stage.JUDGEMENT) {
            beginJudgement(players);
        }
        switch (rules.stage()) {
            case MIRROR -> mirrorStep(boss, players);
            case SHATTER -> shatterStep(boss);
            case JUDGEMENT -> judgementStep(boss, players);
        }
        bulletStep(boss, players);
    }

    // ------------------------------------------------------------------ phase 1: the mirror

    private void mirrorStep(Warden boss, List<Player> players) {
        if (!keepMirror()) {
            return;
        }
        mirror.poll().ifPresent(this::strikeMirror);
        reflections.tick(players, terrain::inside, mirrorFront(0.0));
        for (EnmaMirrorTrial.Event event : trial.advance(tick, reflections.alive())) {
            switch (event) {
                case TELEGRAPH -> telegraphWave(boss, players);
                case SUMMON -> summonWave(players);
                case EXPOSED -> exposeMirror(players);
                case VOLLEY_WINDUP -> volleyWindup(boss, players);
                case VOLLEY_FIRE -> volleyFire(boss, players);
            }
        }
        drawMirror();
        setBase("idle");
        Location back = home.clone();
        if (boss.getLocation().distanceSquared(back) > 0.04) {
            back.setYaw(boss.getLocation().getYaw());
            boss.teleport(back);
        }
        Player target = nearest(players, boss.getLocation());
        if (target != null) {
            face(boss, target.getLocation());
        }
        if (tick % 10 == 0) {
            mirrorHud(players);
        }
    }

    /**
     * Si el Husk del espejo dejo de ser valido ({@code /kill}, chunk descargado) lo vuelve a crear en su lugar; si no
     * se puede durante {@link #MIRROR_RESPAWN_TICKS}, el espejo cuenta como quebrado (con su parte de la vida de Enma)
     * para que la fase 1 termine. Devuelve si el espejo sigue en juego.
     */
    private boolean keepMirror() {
        if (mirror.present()) {
            mirrorMissing = 0;
            return true;
        }
        if (mirror.respawn()) {
            mirrorMissing = 0;
            mirror.animate(trial.exposed() ? "exposed" : "idle");
            return true;
        }
        if (++mirrorMissing < MIRROR_RESPAWN_TICKS) {
            return true;
        }
        double rest = trial.forfeit();
        if (rest > 0.0) {
            context.damage(trial.transfer(rest, context.maximumHealth()), null);
        }
        support.logger().warning("[Enma] El espejo desaparecio y no se pudo volver a crear: se da por quebrado.");
        return false;
    }

    private void strikeMirror(EnmaMirror.Strike strike) {
        EnmaMirrorTrial.Hit hit = trial.hit(strike.damage());
        Location center = mirror.center();
        World world = center.getWorld();
        if (hit.shielded()) {
            world.spawnParticle(Particle.DUST, center, 12, 0.6, 1.0, 0.3, 0, SHIELD);
            world.playSound(center, Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 0.9F, 1.3F);
            feedback(strike.attacker(), trial.state() == EnmaMirrorTrial.State.REFLECTIONS
                ? "§7El espejo está protegido: §fmatá a los reflejos primero."
                : "§7El espejo todavía no se puede romper.");
            return;
        }
        if (hit.accepted() <= 0.0) {
            return;
        }
        context.damage(trial.transfer(hit.accepted(), context.maximumHealth()), strike.attacker());
        world.spawnParticle(Particle.BLOCK, center, 14, 0.5, 0.9, 0.2, 0.1, Material.GLASS.createBlockData());
        world.playSound(center, Sound.BLOCK_GLASS_HIT, SoundCategory.HOSTILE, 1.4F, 0.8F);
        int crack = trial.crackStage();
        if (crack > lastCrack && !hit.broke()) {
            lastCrack = crack;
            world.playSound(center, Sound.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.2F, 1.4F);
            mirror.animateOnce("summon", "exposed");
        }
    }

    private void telegraphWave(Warden boss, List<Player> players) {
        mirror.animate("summon");
        playOnce("mirror");
        World world = boss.getWorld();
        world.playSound(mirror.center(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, SoundCategory.HOSTILE, 2.0F, 0.8F);
        world.playSound(home, Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 1.5F, 0.6F);
        actionBar(players, "§d✦ El espejo refleja tus pecados... §f¡van a salir tus reflejos!");
    }

    private void summonWave(List<Player> players) {
        List<Location> spots = new ArrayList<>();
        int count = players.size();
        for (int i = 0; i < count; i++) {
            double side = (i - (count - 1) / 2.0) * 1.4;
            spots.add(mirrorFront(side));
        }
        reflections.summon(players, spots);
        mirror.animate("idle");
        if (trial.waves() == 1) {
            for (Player player : players) {
                player.sendMessage(LEGACY.deserialize("§5✦ §dDel espejo sale tu reflejo, con tu armadura y tu arma. "
                    + "§fMientras quede algún reflejo vivo el espejo no se puede romper."));
            }
        }
    }

    private void exposeMirror(List<Player> players) {
        mirror.animate("exposed");
        Location center = mirror.center();
        center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 1.2F, 1.5F);
        Title title = Title.title(LEGACY.deserialize("§f✦"),
            LEGACY.deserialize("§a¡El espejo está expuesto! §fRompelo antes de que vuelva a brillar"),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(400)));
        for (Player player : players) {
            player.showTitle(title);
        }
    }

    private void volleyWindup(Warden boss, List<Player> players) {
        Player target = players.isEmpty() ? null : players.get(random.nextInt(players.size()));
        volleyTarget = target == null ? null : target.getUniqueId();
        if (target == null) {
            return;
        }
        playOnce("attack");
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE,
            1.6F, 0.6F);
        target.sendActionBar(LEGACY.deserialize("§c✦ Enma te señala con su shaku: §f¡moveté de costado!"));
    }

    private void volleyFire(Warden boss, List<Player> players) {
        Player target = null;
        for (Player player : players) {
            if (player.getUniqueId().equals(volleyTarget)) {
                target = player;
            }
        }
        volleyTarget = null;
        if (target == null) {
            return;
        }
        fire(boss, EnmaPattern.VOLLEY, 0, List.of(angleTo(boss.getLocation(), target.getLocation())), 0.0);
    }

    private void drawMirror() {
        if (!mirror.present() || tick % 3 != 0) {
            return;
        }
        Location center = mirror.center();
        World world = center.getWorld();
        switch (trial.state()) {
            case SUMMONING -> {
                world.spawnParticle(Particle.DUST, center, 10, 0.6, 1.0, 0.25, 0, MIRROR_GLOW);
                world.spawnParticle(Particle.REVERSE_PORTAL, mirrorFront(0.0).add(0, 1, 0), 8, 0.6, 0.8, 0.6, 0.02);
            }
            case REFLECTIONS -> {
                if (tick % 9 == 0) {
                    world.spawnParticle(Particle.DUST, center, 6, 0.7, 1.1, 0.3, 0, SHIELD);
                }
            }
            case EXPOSED -> {
                world.spawnParticle(Particle.END_ROD, center, 2, 0.5, 0.9, 0.2, 0.01);
                if (trial.crackStage() > 0) {
                    world.spawnParticle(Particle.CRIT, center, 2 * trial.crackStage(), 0.5, 0.9, 0.2, 0.05);
                }
            }
            default -> {
            }
        }
    }

    private void mirrorHud(List<Player> players) {
        int integrity = (int) Math.ceil(trial.integrity() * 100.0);
        String text = switch (trial.state()) {
            case REFLECTIONS -> "§5Reflejos vivos: §f" + reflections.alive() + " §8| §7Espejo: §f" + integrity + "%";
            case EXPOSED -> "§a¡Espejo expuesto! §f" + (trial.exposedRemaining(tick) + 19) / 20 + " s §8| §7Espejo: §f"
                + integrity + "%";
            default -> null;
        };
        if (text != null) {
            actionBar(players, text);
        }
    }

    // ------------------------------------------------------------------ transition

    private void beginShatter(Warden boss, List<Player> players) {
        shatteredAt = tick;
        reflections.clear();
        mirror.animate("death");
        Location center = mirror.center();
        World world = center.getWorld();
        world.spawnParticle(Particle.BLOCK, center, 80, 0.8, 1.2, 0.4, 0.2, Material.GLASS.createBlockData());
        world.spawnParticle(Particle.END_ROD, center, 30, 0.6, 1.0, 0.3, 0.15);
        world.playSound(center, Sound.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2.0F, 0.6F);
        world.playSound(center, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.HOSTILE, 2.0F, 0.5F);
        setBase("idle");
        playOnce("rage");
        world.playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.0F, 0.6F);
        for (Player player : players) {
            title(player, "§f§l¡EL ESPEJO SE QUIEBRA!", "§cEnma toma su shaku: §7ahora sí se lo puede herir", 50);
        }
    }

    private void shatterStep(Warden boss) {
        if (tick - shatteredAt == MIRROR_REMOVE_AFTER) {
            mirror.remove();
        }
        if (tick % 3 == 0) {
            Location at = boss.getLocation().add(0, 2.0, 0);
            boss.getWorld().spawnParticle(Particle.FLAME, at, 8, 1.0, 1.4, 1.0, 0.03);
            boss.getWorld().spawnParticle(Particle.SMOKE, at, 6, 1.0, 1.4, 1.0, 0.02);
        }
    }

    private void beginJudgement(List<Player> players) {
        judgement = new EnmaJudgement(tick, random);
        for (Player player : players) {
            player.sendMessage(LEGACY.deserialize("§4✦ §cEl juicio comienza. §fEsquivá sus balas, alejate cuando "
                + "gire con el shaku (círculo rojo) y cuidado con los oni que salen del piso."));
        }
    }

    // ------------------------------------------------------------------ phase 2: the judgement

    private void judgementStep(Warden boss, List<Player> players) {
        trackOni(players, boss.getLocation());
        double fraction = context.maximumHealth() <= 0.0 ? 1.0 : context.health() / context.maximumHealth();
        boolean close = false;
        for (Player player : players) {
            if (horizontalDistance(player.getLocation(), boss.getLocation()) <= SPIN_RADIUS) {
                close = true;
                break;
            }
        }
        EnmaJudgement.Situation situation = new EnmaJudgement.Situation(oni.size(),
            EnmaJudgement.oniCap(players.size()), close, fraction);
        for (EnmaJudgement.Event event : judgement.advance(tick, situation)) {
            switch (event.kind()) {
                case WINDUP -> windup(boss, event.attack(), players);
                case RELEASE -> release(boss, event.attack(), players);
                case END -> setBase("idle");
            }
        }
        EnmaJudgement.Attack current = judgement.current();
        Player target = nearest(players, boss.getLocation());
        if (current == null) {
            chase(boss, target, WALK_PER_TICK, KEEP_DISTANCE);
            return;
        }
        if (judgement.windingUp(tick)) {
            if (target != null && current != EnmaJudgement.Attack.SLAM) {
                face(boss, target.getLocation());
            }
            if (tick % 2 == 0) {
                drawWindup(boss, current);
            }
            return;
        }
        int elapsed = judgement.elapsedActive(tick);
        if (current == EnmaJudgement.Attack.SPIN) {
            spinStep(boss, players, target);
        } else if (current.pattern() != null) {
            EnmaPattern pattern = current.pattern();
            if (pattern == EnmaPattern.AIMED && elapsed % 10 == 0) {
                aims = aimsAt(boss, players);
            }
            fire(boss, pattern, elapsed, aims, patternBase);
            if (target != null) {
                face(boss, target.getLocation());
            }
        }
    }

    private void windup(Warden boss, EnmaJudgement.Attack attack, List<Player> players) {
        setBase("idle");
        World world = boss.getWorld();
        Location at = boss.getLocation();
        switch (attack) {
            case SLAM -> {
                playOnce("slam");
                world.playSound(at, Sound.BLOCK_BELL_USE, SoundCategory.HOSTILE, 2.0F, 0.5F);
                world.playSound(at, Sound.ENTITY_RAVAGER_ATTACK, SoundCategory.HOSTILE, 1.5F, 0.5F);
                actionBar(players, "§c✦ Enma alza el shaku: §f¡alejate del círculo!");
            }
            case SPIN -> {
                playOnce("spin");
                spinHits.clear();
                world.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_1, SoundCategory.HOSTILE, 2.0F, 0.6F);
                actionBar(players, "§c✦ Enma gira su shaku: §f¡salí del círculo rojo!");
            }
            default -> {
                EnmaPattern pattern = attack.pattern();
                playOnce(pattern == EnmaPattern.AIMED ? "attack" : "cast");
                patternBase = random.nextDouble() * 2.0 * Math.PI;
                world.playSound(at, Sound.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 2.0F, 0.5F);
                world.playSound(at, Sound.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 0.6F, 1.6F);
                actionBar(players, "§4✦ " + pattern.announcement());
            }
        }
    }

    private void release(Warden boss, EnmaJudgement.Attack attack, List<Player> players) {
        if (attack == EnmaJudgement.Attack.SLAM) {
            slam(boss, players);
        } else if (attack == EnmaJudgement.Attack.SPIN) {
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE,
                2.0F, 0.5F);
        } else {
            aims = aimsAt(boss, players);
        }
    }

    private void drawWindup(Warden boss, EnmaJudgement.Attack attack) {
        Location center = boss.getLocation().add(0, 0.15, 0);
        World world = center.getWorld();
        boolean late = judgement.windupProgress(tick) >= 0.6;
        Particle.DustOptions dust = late ? WARN_LATE : WARN;
        switch (attack) {
            case SLAM -> ring(world, center, SLAM_RADIUS, 28, dust);
            case SPIN -> ring(world, center, SPIN_RADIUS, 32, dust);
            default -> {
                Location hand = boss.getLocation().add(0, BULLET_HEIGHT + 0.6, 0);
                double radius = 2.2 * (1.0 - judgement.windupProgress(tick)) + 0.4;
                ring(world, hand, radius, 12, BULLET);
            }
        }
        if (late && attack == EnmaJudgement.Attack.SLAM) {
            world.spawnParticle(Particle.BLOCK, center, 6, SLAM_RADIUS * 0.5, 0.05, SLAM_RADIUS * 0.5, 0,
                Material.NETHERRACK.createBlockData());
        }
    }

    private void slam(Warden boss, List<Player> players) {
        Location center = boss.getLocation();
        World world = center.getWorld();
        world.spawnParticle(Particle.EXPLOSION, center, 2, 0.6, 0.1, 0.6, 0);
        world.spawnParticle(Particle.BLOCK, center.clone().add(0, 0.2, 0), 60, SLAM_RADIUS * 0.5, 0.1,
            SLAM_RADIUS * 0.5, 0.2, Material.NETHERRACK.createBlockData());
        world.spawnParticle(Particle.FLAME, center.clone().add(0, 0.2, 0), 30, SLAM_RADIUS * 0.5, 0.1,
            SLAM_RADIUS * 0.5, 0.05);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.4F, 0.6F);
        world.playSound(center, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.2F, 0.5F);
        for (Player player : players) {
            Location at = player.getLocation();
            double distance = horizontalDistance(at, center);
            if (distance <= SLAM_RADIUS && Math.abs(at.getY() - center.getY()) < 2.5) {
                player.damage(SLAM_DAMAGE, boss);
                Vector push = horizontal(at.toVector().subtract(center.toVector())).multiply(0.7);
                player.setVelocity(push.setY(0.55));
            }
        }
        int wanted = Math.min(EnmaJudgement.oniCap(players.size()) - oni.size(), players.size() >= 3 ? 2 : 1);
        for (int i = 0; i < wanted; i++) {
            spawnOni(center, players);
        }
    }

    private void spawnOni(Location center, List<Player> players) {
        Location at = null;
        for (int attempt = 0; attempt < 8 && at == null; attempt++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double radius = 3.0 + random.nextDouble() * 2.5;
            at = terrain.standAt(center.getX() + Math.cos(angle) * radius, center.getZ() + Math.sin(angle) * radius)
                .orElse(null);
        }
        if (at == null) {
            at = center.clone();
        }
        Optional<LivingEntity> spawned = support.spawnMinion(bossId, ONI, at);
        if (spawned.isEmpty()) {
            support.logger().warning("[Enma] No pudo salir un oni (¿falta el mob 'oni' en el catalogo?).");
            return;
        }
        LivingEntity entity = spawned.get();
        oni.add(entity);
        World world = at.getWorld();
        world.spawnParticle(Particle.FLAME, at.clone().add(0, 0.5, 0), 30, 0.5, 0.6, 0.5, 0.05);
        world.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 0.5, 0), 15, 0.5, 0.6, 0.5, 0.02);
        world.playSound(at, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.2F, 1.2F);
        if (entity instanceof Mob mob) {
            mob.setTarget(nearest(players, at));
        }
    }

    private void trackOni(List<Player> players, Location bossAt) {
        oni.removeIf(entity -> !entity.isValid() || entity.isDead());
        if (tick % 20 != 0) {
            return;
        }
        for (LivingEntity entity : oni) {
            if (!terrain.inside(entity.getLocation())) {
                entity.teleport(bossAt);
            }
            if (entity instanceof Mob mob && !(mob.getTarget() instanceof Player current && players.contains(current))) {
                mob.setTarget(nearest(players, entity.getLocation()));
            }
        }
    }

    private void spinStep(Warden boss, List<Player> players, Player target) {
        Location center = boss.getLocation();
        World world = center.getWorld();
        if (tick % 2 == 0) {
            double turn = tick * 0.9;
            for (int i = 0; i < 3; i++) {
                double angle = turn + i * 2.0 * Math.PI / 3.0;
                world.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(Math.cos(angle) * SPIN_RADIUS * 0.7, 1.2,
                    Math.sin(angle) * SPIN_RADIUS * 0.7), 1, 0, 0, 0, 0);
            }
            ring(world, center.clone().add(0, 0.15, 0), SPIN_RADIUS, 20, WARN);
        }
        if (tick % 6 == 0) {
            world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.2F, 0.7F);
        }
        for (Player player : players) {
            Location at = player.getLocation();
            if (spinHits.contains(player.getUniqueId()) || horizontalDistance(at, center) > SPIN_RADIUS
                || Math.abs(at.getY() - center.getY()) > 3.0) {
                continue;
            }
            spinHits.add(player.getUniqueId());
            player.damage(SPIN_DAMAGE, boss);
            Vector push = horizontal(at.toVector().subtract(center.toVector())).multiply(0.9);
            player.setVelocity(push.setY(0.35));
        }
        chase(boss, target, SPIN_WALK_PER_TICK, 0.5);
    }

    // ------------------------------------------------------------------ bullets

    private void fire(Warden boss, EnmaPattern pattern, int elapsed, List<Double> aimAngles, double base) {
        List<EnmaPattern.Shot> shots = pattern.shots(elapsed, aimAngles, base);
        if (shots.isEmpty()) {
            return;
        }
        Location origin = boss.getLocation();
        EnmaBullets.Solid solid = terrain.solid();
        double y = origin.getY() + BULLET_HEIGHT;
        for (EnmaPattern.Shot shot : shots) {
            double x = origin.getX() + Math.cos(shot.angle()) * 1.3;
            double z = origin.getZ() + Math.sin(shot.angle()) * 1.3;
            bullets.fire(x, y, z, shot.angle(), shot.speed(), solid);
        }
        boss.getWorld().playSound(origin, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 0.7F, 1.3F);
    }

    private void bulletStep(Warden boss, List<Player> players) {
        if (bullets.size() == 0) {
            return;
        }
        List<EnmaBullets.Target> targets = new ArrayList<>(players.size());
        Map<UUID, Player> byId = new HashMap<>();
        for (Player player : players) {
            Location at = player.getLocation();
            targets.add(new EnmaBullets.Target(player.getUniqueId(), at.getX(), at.getY(), at.getZ()));
            byId.put(player.getUniqueId(), player);
        }
        EnmaBullets.Step step = bullets.step(terrain.solid(), targets);
        World world = terrain.world();
        DamageSource source = DamageSource.builder(DamageType.MOB_PROJECTILE)
            .withCausingEntity(boss).withDirectEntity(boss).build();
        for (UUID id : step.hits()) {
            Player player = byId.get(id);
            if (player == null) {
                continue;
            }
            player.damage(BULLET_DAMAGE, source);
            world.spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0, BULLET);
        }
        for (EnmaBullets.Position pop : step.pops()) {
            world.spawnParticle(Particle.SMOKE, pop.x(), pop.y(), pop.z(), 3, 0.1, 0.1, 0.1, 0.01);
        }
        for (EnmaBullets.Position bullet : bullets.positions()) {
            world.spawnParticle(Particle.DUST, bullet.x(), bullet.y(), bullet.z(), 1, 0, 0, 0, 0, BULLET);
            if ((bullet.age() + tick) % 4 == 0) {
                world.spawnParticle(Particle.FLAME, bullet.x(), bullet.y(), bullet.z(), 1, 0, 0, 0, 0);
            }
        }
    }

    private List<Double> aimsAt(Warden boss, List<Player> players) {
        List<Double> list = new ArrayList<>();
        for (Player player : players) {
            list.add(angleTo(boss.getLocation(), player.getLocation()));
        }
        return list;
    }

    // ------------------------------------------------------------------ helpers

    /** Jugadores en supervivencia/aventura y parados adentro de la sala (no en pasillos detras de paredes). */
    private List<Player> arenaPlayers() {
        List<Player> list = new ArrayList<>();
        if (terrain == null) {
            return list;
        }
        for (Player player : context.players()) {
            GameMode mode = player.getGameMode();
            if ((mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !player.isDead()
                && terrain.inside(player.getLocation())) {
                list.add(player);
            }
        }
        return list;
    }

    private Location mirrorSpot() {
        double x = home.getX() + forward.getX() * MIRROR_DISTANCE;
        double z = home.getZ() + forward.getZ() * MIRROR_DISTANCE;
        Location at = terrain.standAt(x, z).orElseGet(() -> new Location(home.getWorld(), x, home.getY(), z));
        at.setYaw(yaw(forward));
        at.setPitch(0F);
        return at;
    }

    /** Delante del espejo, corrido {@code side} bloques a su costado. */
    private Location mirrorFront(double side) {
        Location base = mirror.home();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        double x = base.getX() + forward.getX() * 1.8 + right.getX() * side;
        double z = base.getZ() + forward.getZ() * 1.8 + right.getZ() * side;
        return terrain.standAt(x, z).orElseGet(() -> new Location(base.getWorld(), x, base.getY(), z));
    }

    private void chase(Warden boss, Player target, double speed, double keep) {
        if (target == null) {
            setBase("idle");
            return;
        }
        Location from = boss.getLocation();
        Vector delta = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        double distance = delta.length();
        if (distance <= keep) {
            setBase("idle");
            face(boss, target.getLocation());
            return;
        }
        Vector move = delta.multiply(Math.min(speed, distance - keep) / distance);
        double x = from.getX() + move.getX();
        double z = from.getZ() + move.getZ();
        OptionalInt floor = terrain.floorAt(x, z);
        if (floor.isEmpty() || Math.abs(floor.getAsInt() + 1.0 - from.getY()) > 1.6) {
            setBase("idle");
            face(boss, target.getLocation());
            return;
        }
        Location to = new Location(from.getWorld(), x, floor.getAsInt() + 1.0, z, yawTo(from, target.getLocation()), 0F);
        boss.teleport(to);
        boss.setBodyYaw(to.getYaw());
        if (judgement == null || judgement.current() == null) {
            setBase("walk");
        }
    }

    private void setBase(String loop) {
        if (loop.equals(baseLoop)) {
            return;
        }
        JavaModelGateway models = context.models();
        try {
            if (!baseLoop.equals("idle")) {
                models.stopAnimation(context.model(), baseLoop);
            }
            context.animate(loop);
        } catch (RuntimeException ignored) {
            // cosmetico
        }
        baseLoop = loop;
    }

    private void playOnce(String animation) {
        try {
            context.models().animateOnce(context.model(), animation, () -> {
                if (!stopped) {
                    context.animate(baseLoop);
                }
            });
        } catch (RuntimeException ignored) {
            // cosmetico
        }
    }

    private static void ring(World world, Location center, double radius, int points, Particle.DustOptions dust) {
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points;
            world.spawnParticle(Particle.DUST, center.getX() + Math.cos(angle) * radius, center.getY(),
                center.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0, dust);
        }
    }

    private void face(Warden boss, Location to) {
        float yaw = yawTo(boss.getLocation(), to);
        boss.setRotation(yaw, 0F);
        boss.setBodyYaw(yaw);
    }

    private static float yawTo(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static float yaw(Vector direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }

    /** Angulo de las balas ({@code x = cos}, {@code z = sin}) de {@code from} a {@code to}. */
    private static double angleTo(Location from, Location to) {
        return Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX());
    }

    private static Vector horizontal(Vector vector) {
        Vector flat = vector.clone().setY(0);
        return flat.lengthSquared() < 1.0e-6 ? new Vector(0, 0, -1) : flat.normalize();
    }

    private static double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    static Player nearest(List<Player> players, Location at) {
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

    private void title(Player player, String title, String subtitle, int stayTicks) {
        player.showTitle(Title.title(LEGACY.deserialize(title), LEGACY.deserialize(subtitle),
            Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(stayTicks * 50L), Duration.ofMillis(500))));
    }

    private void actionBar(List<Player> players, String text) {
        Component component = LEGACY.deserialize(text);
        for (Player player : players) {
            player.sendActionBar(component);
        }
    }

    private void shieldedFeedback(Player player) {
        feedback(player, "§7Enma está protegido por el §fEspejo de Johari§7: matá a tus reflejos y rompé el espejo.");
    }

    private void feedback(Player player, String text) {
        Integer last = feedbackAt.get(player.getUniqueId());
        if (last != null && tick - last < FEEDBACK_COOLDOWN) {
            return;
        }
        feedbackAt.put(player.getUniqueId(), tick);
        player.sendActionBar(LEGACY.deserialize(text));
    }
}
