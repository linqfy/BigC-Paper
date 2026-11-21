package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * El combate de Zeus en el servidor (capa fina sobre {@link ZeusFightRules}, {@link ZeusStorm},
 * {@link ZeusSpearCycle}, {@link ZeusShockLedger} y {@link ZeusCloudRoster}). Corre en una tarea propia de
 * {@link MythicBossSupport#scheduler()} cada tick; todo lo que crea pasa por el support y se borra en {@link #stop()}.
 * No cambia ningun bloque: los rayos son solo el efecto visual vanilla mas dano propio en las baldosas.
 *
 * <p>Bedrock: ve un Evoker grande en lugar del modelo, un soporte con tridente en lugar de la lanza y Vex en lugar
 * de las nubes; los avisos son particulas en el piso, sonidos, titulos y action bar, que Geyser muestra igual.</p>
 */
final class ZeusController implements BossEncounterController {

    static final String SPEAR_MODEL = "bigcasares_myth_zeus_spear";
    /** Dano de un rayo de patron (tipo rayo: la armadura lo reduce). */
    static final double STRIKE_DAMAGE = 6.0;
    /** Dano del impacto de la lanza, en {@link #SPEAR_RADIUS} bloques. */
    static final double SPEAR_DAMAGE = 7.0;
    static final double SPEAR_RADIUS = 2.5;
    /** Rayos vanilla (solo efecto) por tanda: el resto de las baldosas son chispas, para no ensordecer a nadie. */
    private static final int BOLTS_PER_WAVE = 3;
    private static final double WALK_PER_TICK = 0.12;
    private static final double KEEP_DISTANCE = 3.0;
    private static final int FEEDBACK_COOLDOWN = 30;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Particle.DustOptions WARN_EARLY = new Particle.DustOptions(Color.fromRGB(255, 214, 64), 1.6F);
    private static final Particle.DustOptions WARN_LATE = new Particle.DustOptions(Color.fromRGB(190, 236, 255), 1.9F);
    private static final Particle.DustOptions SPEAR_WARN = new Particle.DustOptions(Color.fromRGB(255, 120, 40), 1.7F);

    private final MythicBossSupport support;
    private final BossEncounterContext context;
    private final UUID bossId;
    private final RandomGenerator random = new SplittableRandom();
    private final ZeusFightRules rules = new ZeusFightRules();
    private final ZeusShockLedger shocks = new ZeusShockLedger();
    private final Map<UUID, Location> shockAnchors = new HashMap<>();
    private final Map<UUID, Integer> feedbackAt = new HashMap<>();
    private final NamespacedKey shockSpeedKey;
    private final NamespacedKey shockJumpKey;

    private ZeusArena arena;
    private ZeusStorm storm;
    private ZeusSpearCycle spears;
    private ZeusCloudRoster roster;
    private ZeusClouds clouds;
    private BukkitTask task;
    private int tick;
    private boolean started;
    /** {@code true} solo cuando {@link #start()} termino bien; antes (o si fallo) el controlador queda inerte. */
    private boolean ready;
    private boolean stopped;
    private String baseLoop = "idle";

    private Location spearFrom;
    private Location spearTarget;
    private ArmorStand spear;
    private JavaModelHandle spearModel;
    private boolean warnedSpearModel;

    ZeusController(MythicBossSupport support, BossEncounterContext context) {
        this.support = support;
        this.context = context;
        this.bossId = context.bossInstanceId();
        this.shockSpeedKey = new NamespacedKey(support.plugin(), "zeus_shock_speed");
        this.shockJumpKey = new NamespacedKey(support.plugin(), "zeus_shock_jump");
    }

    // ------------------------------------------------------------------ BossEncounterController

    @Override
    public void start() {
        if (started || stopped) {
            return;
        }
        started = true;
        Warden boss = context.boss();
        Location spawn = boss.getLocation();
        arena = new ZeusArena(spawn, support.locator().dungeonBounds(spawn));
        clouds = new ZeusClouds(support, bossId, random);
        storm = new ZeusStorm((kind, telegraph) -> ZeusPatterns.build(kind, ZeusArena.RADIUS, telegraph,
            arena.cellAt(context.boss().getLocation()), arena.cellsOf(vulnerablePlayers()), random), random, 0);
        try {
            support.spawnBedrockStandIn(bossId, boss, Evoker.class, 1.8);
        } catch (RuntimeException unavailable) {
            // sin dobles (modulo sin Geyser): nadie de Bedrock que atender
        }
        task = support.scheduler().repeating("zeus-" + bossId, 1L, 1L, this::step);
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
        if (storm != null) {
            storm.stop();
        }
        for (UUID id : shocks.releaseAll()) {
            Player player = support.plugin().getServer().getPlayer(id);
            if (player != null) {
                unfreeze(player);
            }
        }
        shockAnchors.clear();
        if (clouds != null) {
            clouds.close();
        }
        removeSpear();
        support.cleanup(bossId);
    }

    @Override
    public boolean blocksProjectile(Projectile projectile) {
        if (projectile.getShooter() instanceof Player shooter) {
            feedback(shooter, "§c¡Las flechas no le hacen nada a Zeus! §fPegale cuerpo a cuerpo.");
        }
        Location at = projectile.getLocation();
        at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 10, 0.2, 0.2, 0.2, 0.2);
        return true;
    }

    @Override
    public double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
        if (!ready) {
            // inerte: sin arena no hay fases ni ventanas; el golpe pasa como en un jefe comun
            return damage;
        }
        ZeusFightRules.HitKind kind = ZeusFightRules.HitKind.OTHER;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Projectile) {
                kind = ZeusFightRules.HitKind.PROJECTILE;
            } else if (byEntity.getDamager() instanceof Player
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
                kind = ZeusFightRules.HitKind.MELEE;
            }
        }
        Warden boss = context.boss();
        double distance = attacker != null && attacker.getWorld().equals(boss.getWorld())
            ? attacker.getLocation().distance(boss.getLocation()) : Double.POSITIVE_INFINITY;
        boolean stuck = spears != null && spears.vulnerable(tick);
        ZeusFightRules.Decision decision = rules.admit(kind, damage, distance, context.health(),
            context.maximumHealth(), stuck);
        Location chest = boss.getLocation().add(0, 2.0, 0);
        switch (decision.verdict()) {
            case ADMITTED -> {
                if (rules.stage() == ZeusFightRules.Stage.SPEARS) {
                    chest.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, chest, 18, 0.5, 0.8, 0.5, 0.3);
                }
            }
            case PROJECTILE_IMMUNE -> {
                if (attacker != null) {
                    feedback(attacker, "§c¡Las flechas no le hacen nada a Zeus! §fPegale cuerpo a cuerpo.");
                }
            }
            case NOT_MELEE -> {
                if (attacker != null) {
                    feedback(attacker, "§7Zeus solo siente golpes cuerpo a cuerpo.");
                }
            }
            case SHIELDED -> {
                chest.getWorld().spawnParticle(Particle.WAX_OFF, chest, 8, 0.6, 0.9, 0.6, 0.5);
                chest.getWorld().playSound(chest, Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 0.8F, 1.4F);
                if (attacker != null) {
                    feedback(attacker, rules.stage() == ZeusFightRules.Stage.SPEARS
                        ? "§7Zeus es intocable: §eesperá a que clave la lanza§7 y pegale."
                        : "§7Zeus está desatando su furia...");
                }
            }
        }
        return decision.damage();
    }

    @Override
    public OptionalInt phaseOverride() {
        return ready ? OptionalInt.of(rules.phase()) : OptionalInt.empty();
    }

    @Override
    public boolean suspendsAbilities() {
        // el golpe de la YAML es solo de la fase 1; en la 2 Zeus esta tirando lanzas
        return ready && rules.stage() != ZeusFightRules.Stage.STORM;
    }

    // ------------------------------------------------------------------ the loop

    private void step() {
        if (stopped || !ready) {
            return;
        }
        tick++;
        Warden boss = context.boss();
        if (!boss.isValid() || boss.isDead()) {
            return;
        }
        double fraction = context.maximumHealth() <= 0.0 ? 1.0 : context.health() / context.maximumHealth();
        ZeusFightRules.Stage changed = rules.update(tick, fraction);
        if (changed == ZeusFightRules.Stage.TRANSITION) {
            beginTransition(boss);
        } else if (changed == ZeusFightRules.Stage.SPEARS) {
            beginSpears(boss);
        }
        List<Player> players = vulnerablePlayers();
        switch (rules.stage()) {
            case STORM -> stormStep(boss, players, fraction);
            case TRANSITION -> transitionStep(boss);
            case SPEARS -> spearStep(boss, players);
        }
        shockStep(players);
    }

    // ------------------------------------------------------------------ phase 1: the storm

    private void stormStep(Warden boss, List<Player> players, double fraction) {
        ZeusStorm.Step step = storm.advance(tick, fraction);
        if (step.started() != null) {
            actionBar(players, step.started().kind().announcement());
            playOnce("cast");
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE,
                2.0F, 0.7F);
        }
        if (!step.warned().isEmpty()) {
            for (Player player : players) {
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE,
                    0.7F, 1.7F);
            }
        }
        for (ZeusStrikeWave wave : step.struck()) {
            strike(boss, wave, players);
        }
        if (tick % 4 == 0) {
            drawTelegraphs(boss.getWorld());
        }
        if (storm.casting()) {
            setBase("idle");
            face(boss, nearest(players, boss.getLocation()));
        } else {
            chase(boss, nearest(players, boss.getLocation()));
        }
        if (tick % 5 == 0) {
            Location hand = boss.getLocation().add(0, 3.0, 0);
            boss.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, hand, 2, 0.6, 0.6, 0.6, 0.05);
        }
    }

    private void drawTelegraphs(World world) {
        for (ZeusStorm.Telegraph telegraph : storm.telegraphs(tick)) {
            boolean late = telegraph.progress() >= 0.6;
            Particle.DustOptions dust = late ? WARN_LATE : WARN_EARLY;
            for (ZeusCell cell : telegraph.wave().cells()) {
                if (!arena.has(cell)) {
                    continue;
                }
                Location center = arena.cellCenter(cell, 0.1);
                world.spawnParticle(Particle.DUST, center, 1, 0, 0, 0, 0, dust);
                for (int corner = 0; corner < 4; corner++) {
                    double dx = (corner & 1) == 0 ? -1.1 : 1.1;
                    double dz = (corner & 2) == 0 ? -1.1 : 1.1;
                    world.spawnParticle(Particle.DUST, center.clone().add(dx, 0, dz), 1, 0, 0, 0, 0, dust);
                }
                if (late) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 0.2, 0), 2, 0.5, 0.1, 0.5,
                        0.02);
                }
            }
        }
    }

    private void strike(Warden boss, ZeusStrikeWave wave, List<Player> players) {
        World world = boss.getWorld();
        List<ZeusCell> live = new ArrayList<>();
        for (ZeusCell cell : wave.cells()) {
            if (arena.has(cell)) {
                live.add(cell);
            }
        }
        if (live.isEmpty()) {
            return;
        }
        for (ZeusCell cell : live) {
            Location center = arena.cellCenter(cell, 0.2);
            world.spawnParticle(Particle.ELECTRIC_SPARK, center, 14, 1.0, 0.2, 1.0, 0.25);
            world.spawnParticle(Particle.END_ROD, center.clone().add(0, 2.0, 0), 4, 0.1, 1.6, 0.1, 0.0);
        }
        for (int i = 0; i < Math.min(BOLTS_PER_WAVE, live.size()); i++) {
            // solo el efecto: sin fuego, sin dano vanilla (el dano es el de abajo)
            world.strikeLightningEffect(arena.cellCenter(live.get(random.nextInt(live.size())), -1.0));
        }
        world.playSound(arena.cellCenter(live.get(random.nextInt(live.size())), 0.0),
            Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 1.2F, 1.1F);
        DamageSource source = DamageSource.builder(DamageType.LIGHTNING_BOLT)
            .withCausingEntity(boss).withDirectEntity(boss).build();
        for (Player player : players) {
            ZeusCell cell = arena.cellAt(player.getLocation());
            OptionalInt floor = arena.cellFloor(cell);
            if (!wave.cells().contains(cell) || floor.isEmpty()) {
                continue;
            }
            double y = player.getLocation().getY();
            if (y < floor.getAsInt() - 1.0 || y > floor.getAsInt() + 5.5) {
                continue;
            }
            player.damage(STRIKE_DAMAGE, source);
            world.spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.2);
        }
    }

    private void chase(Warden boss, Player target) {
        if (target == null) {
            setBase("idle");
            return;
        }
        Location from = boss.getLocation();
        Vector delta = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        double distance = delta.length();
        if (distance <= KEEP_DISTANCE) {
            setBase("idle");
            face(boss, target);
            return;
        }
        Vector move = delta.multiply(Math.min(WALK_PER_TICK, distance - KEEP_DISTANCE) / distance);
        double x = from.getX() + move.getX();
        double z = from.getZ() + move.getZ();
        OptionalInt floor = arena.floorAt(x, z);
        if (!arena.walkable(x, z) || floor.isEmpty()
            || Math.abs(floor.getAsInt() + 1.0 - from.getY()) > 1.6) {
            setBase("idle");
            face(boss, target);
            return;
        }
        Location to = new Location(from.getWorld(), x, floor.getAsInt() + 1.0, z, yawTo(from, target.getLocation()), 0F);
        boss.teleport(to);
        boss.setBodyYaw(to.getYaw());
        setBase("walk");
    }

    // ------------------------------------------------------------------ transition

    private void beginTransition(Warden boss) {
        storm.stop();
        setBase("idle");
        playOnce("rage");
        Location home = arena.home();
        home.setYaw(boss.getLocation().getYaw());
        boss.teleport(home);
        World world = boss.getWorld();
        for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI / 2.0 + Math.PI / 4.0;
            world.strikeLightningEffect(home.clone().add(Math.cos(angle) * 3.0, 0, Math.sin(angle) * 3.0));
        }
        world.playSound(home, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2.0F, 0.6F);
        // el titulo "¡ZEUS SE ENFURECE!" lo pone el runtime (dialogo phase-2); aca va la pista de la fase
        for (Player player : vulnerablePlayers()) {
            player.sendMessage(LEGACY.deserialize("§6⚡ §eZeus llama a las nubes y toma sus lanzas. "
                + "§fSolo podés herirlo mientras una lanza está clavada en el piso. "
                + "§7Cuidado: las nubes te electrocutan."));
        }
    }

    private void transitionStep(Warden boss) {
        if (tick % 3 == 0) {
            Location at = boss.getLocation().add(0, 2.0, 0);
            boss.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 16, 1.2, 1.5, 1.2, 0.3);
            boss.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 2.0, 0), 6, 2.0, 0.3, 2.0, 0.02);
        }
    }

    // ------------------------------------------------------------------ phase 2: clouds and spears

    private void beginSpears(Warden boss) {
        spears = new ZeusSpearCycle(tick);
        roster = new ZeusCloudRoster(tick);
        int count = roster.toSpawn(tick, clouds.alive(), vulnerablePlayers().size(), true);
        for (int i = 0; i < count; i++) {
            clouds.spawn(arena, vulnerablePlayers(), boss.getLocation());
        }
    }

    private void spearStep(Warden boss, List<Player> players) {
        int count = roster.toSpawn(tick, clouds.alive(), players.size(), false);
        for (int i = 0; i < count; i++) {
            clouds.spawn(arena, players, boss.getLocation());
        }
        clouds.tick(tick, players, this::electrocute);

        for (ZeusSpearCycle.Event event : spears.advance(tick)) {
            switch (event) {
                case WINDUP -> windUp(boss, players);
                case THROW -> launchSpear(boss);
                case IMPACT -> impact(boss, players);
                case WINDOW_CLOSE -> closeWindow(boss, players);
            }
        }
        if (spearTarget != null && spears.windingUp(tick)) {
            if (tick % 2 == 0) {
                drawSpearWarning();
            }
            flySpear();
        }
        if (spears.vulnerable(tick)) {
            if (tick % 4 == 0) {
                Location at = boss.getLocation().add(0, 1.5, 0);
                boss.getWorld().spawnParticle(Particle.WAX_OFF, at, 6, 0.7, 1.2, 0.7, 0.4);
                if (spear != null && spear.isValid()) {
                    boss.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, spear.getLocation().add(0, 0.8, 0), 3,
                        0.2, 0.6, 0.2, 0.05);
                }
            }
            if (tick % 10 == 0) {
                int seconds = (spears.windowRemaining(tick) + 19) / 20;
                actionBar(players, "§a⚡ ¡Zeus es vulnerable! §f" + seconds + " s");
            }
        } else {
            face(boss, nearest(players, boss.getLocation()));
        }
    }

    private void windUp(Warden boss, List<Player> players) {
        removeSpear();
        if (players.isEmpty()) {
            spearTarget = null;
            return;
        }
        Player target = players.get(random.nextInt(players.size()));
        Location at = target.getLocation();
        OptionalInt floor = arena.floorAt(at.getX(), at.getZ());
        spearTarget = new Location(at.getWorld(), at.getX(), floor.isPresent() ? floor.getAsInt() + 1.0 : at.getY(),
            at.getZ());
        face(boss, target);
        setBase("idle");
        playOnce("lanza");
        boss.getWorld().playSound(boss.getLocation(), Sound.ITEM_TRIDENT_RETURN, SoundCategory.HOSTILE, 2.0F, 0.6F);
        target.sendActionBar(LEGACY.deserialize("§c¡Zeus te apunta con su lanza! §f¡Salí de la marca!"));
        target.playSound(target.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 1.0F, 1.6F);
    }

    private void drawSpearWarning() {
        World world = spearTarget.getWorld();
        Location center = spearTarget.clone().add(0, 0.1, 0);
        int points = 18;
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points + tick * 0.05;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(angle) * SPEAR_RADIUS, 0,
                Math.sin(angle) * SPEAR_RADIUS), 1, 0, 0, 0, 0, SPEAR_WARN);
        }
        world.spawnParticle(Particle.DUST, center, 3, 0.4, 0, 0.4, 0, SPEAR_WARN);
    }

    private void launchSpear(Warden boss) {
        if (spearTarget == null) {
            return;
        }
        spearFrom = boss.getLocation().add(0, 3.4, 0);
        float yaw = yawTo(spearFrom, spearTarget);
        Location at = spearFrom.clone();
        at.setYaw(yaw);
        spear = support.spawnOwned(bossId, at, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
        });
        try {
            spearModel = support.models().attach(spear, SPEAR_MODEL);
            support.models().animate(spearModel, "idle");
        } catch (RuntimeException | LinkageError missing) {
            spearModel = null;
            if (!warnedSpearModel) {
                warnedSpearModel = true;
                support.logger().warning("[Zeus] Sin modelo " + SPEAR_MODEL + " (" + missing.getMessage()
                    + "): la lanza se ve solo como particulas en Java.");
            }
        }
        try {
            // Bedrock no ve el modelo: un soporte invisible con un tridente en la mano marca la lanza
            ArmorStand standIn = support.spawnBedrockStandIn(bossId, spear, ArmorStand.class, 1.0);
            standIn.setInvisible(true);
            standIn.setArms(true);
            standIn.setBasePlate(false);
            EntityEquipment equipment = standIn.getEquipment();
            equipment.setItemInMainHand(new ItemStack(Material.TRIDENT));
        } catch (RuntimeException unavailable) {
            // sin dobles de Bedrock
        }
        boss.getWorld().playSound(spearFrom, Sound.ITEM_TRIDENT_THROW, SoundCategory.HOSTILE, 2.0F, 0.7F);
    }

    /** Lleva la lanza por un arco desde la mano de Zeus hasta la marca (cosmetico; el impacto es por tiempo). */
    private void flySpear() {
        if (spear == null || !spear.isValid() || spearFrom == null) {
            return;
        }
        double t = spears.flight(tick);
        Vector path = spearTarget.toVector().subtract(spearFrom.toVector());
        Location at = spearFrom.clone().add(path.multiply(t)).add(0, Math.sin(Math.PI * t) * 3.0, 0);
        at.setYaw(yawTo(spearFrom, spearTarget));
        spear.teleport(at);
        spear.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 2, 0.1, 0.1, 0.1, 0.02);
    }

    private void impact(Warden boss, List<Player> players) {
        if (spearTarget == null) {
            return;
        }
        World world = spearTarget.getWorld();
        if (spear != null && spear.isValid()) {
            Location stuck = spearTarget.clone();
            stuck.setYaw(spear.getLocation().getYaw());
            spear.teleport(stuck);
            if (spearModel != null) {
                support.models().animate(spearModel, "stuck");
            }
        }
        world.strikeLightningEffect(spearTarget);
        world.spawnParticle(Particle.ELECTRIC_SPARK, spearTarget.clone().add(0, 0.3, 0), 60, 1.4, 0.3, 1.4, 0.4);
        world.spawnParticle(Particle.EXPLOSION, spearTarget, 1);
        world.playSound(spearTarget, Sound.ITEM_TRIDENT_HIT_GROUND, SoundCategory.HOSTILE, 2.0F, 0.6F);
        world.playSound(spearTarget, Sound.ITEM_TRIDENT_THUNDER, SoundCategory.HOSTILE, 1.5F, 1.2F);
        for (Player player : players) {
            Location at = player.getLocation();
            double dx = at.getX() - spearTarget.getX();
            double dz = at.getZ() - spearTarget.getZ();
            if (dx * dx + dz * dz <= SPEAR_RADIUS * SPEAR_RADIUS && Math.abs(at.getY() - spearTarget.getY()) < 3.0) {
                player.damage(SPEAR_DAMAGE, boss);
                Vector push = new Vector(dx, 0, dz);
                push = push.lengthSquared() < 1.0e-4 ? new Vector(0, 0.5, 0) : push.normalize().multiply(0.8);
                player.setVelocity(push.setY(0.45));
            }
        }
        setBase("vulnerable");
        Title title = Title.title(LEGACY.deserialize("§e⚡"), LEGACY.deserialize("§e¡La lanza se clavó! §f¡Pegale a Zeus!"),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(400)));
        for (Player player : players) {
            player.showTitle(title);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 0.8F, 1.6F);
        }
    }

    private void closeWindow(Warden boss, List<Player> players) {
        if (spear != null && spear.isValid()) {
            spear.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, spear.getLocation().add(0, 0.6, 0), 30, 0.3, 0.8,
                0.3, 0.2);
        }
        removeSpear();
        spearTarget = null;
        setBase("idle");
        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 1.5F, 0.8F);
        actionBar(players, "§7Zeus arranca la lanza y se recompone...");
    }

    private void removeSpear() {
        if (spearModel != null) {
            try {
                support.models().close(spearModel);
            } catch (RuntimeException ignored) {
                // cosmetico
            }
            spearModel = null;
        }
        if (spear != null && spear.isValid()) {
            // su doble de Bedrock se va solo cuando el ancla deja de ser valida
            spear.remove();
        }
        spear = null;
        spearFrom = null;
    }

    // ------------------------------------------------------------------ electrocution

    private void electrocute(Player player) {
        if (!shocks.shock(player.getUniqueId(), tick)) {
            return;
        }
        shockAnchors.put(player.getUniqueId(), player.getLocation());
        addFreeze(player, Attribute.MOVEMENT_SPEED, shockSpeedKey);
        addFreeze(player, Attribute.JUMP_STRENGTH, shockJumpKey);
        player.setSprinting(false);
        player.showTitle(Title.title(Component.empty(), LEGACY.deserialize("§e⚡ §l¡ELECTROCUTADO! §e⚡"),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(600), Duration.ofMillis(300))));
        Location at = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 40, 0.4, 0.8, 0.4, 0.4);
        player.getWorld().spawnParticle(Particle.END_ROD, at, 8, 0.3, 0.7, 0.3, 0.05);
        player.getWorld().playSound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 1.0F, 1.8F);
        player.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 1.0F, 2.0F);
    }

    /**
     * {@code players}: los vulnerables de la sala este tick; al paralizado que ya no esta (se fue, murio, cambio de
     * mundo o de modo) se lo suelta en vez de devolverlo a la baldosa.
     */
    private void shockStep(List<Player> players) {
        List<UUID> inRoom = new ArrayList<>(players.size());
        for (Player player : players) {
            inRoom.add(player.getUniqueId());
        }
        List<UUID> released = new ArrayList<>(shocks.release(tick));
        released.addAll(shocks.releaseAbsent(inRoom, tick));
        for (UUID id : released) {
            shockAnchors.remove(id);
            Player player = support.plugin().getServer().getPlayer(id);
            if (player != null) {
                unfreeze(player);
            }
        }
        for (Map.Entry<UUID, Location> entry : shockAnchors.entrySet()) {
            Player player = support.plugin().getServer().getPlayer(entry.getKey());
            if (player == null || !player.isValid() || player.isDead()) {
                continue;
            }
            Location lock = entry.getValue();
            Location now = player.getLocation();
            player.setSprinting(false);
            // ancla de posicion: tambien frena a Bedrock y a un cliente que ignore los atributos
            double dx = now.getX() - lock.getX();
            double dz = now.getZ() - lock.getZ();
            if (now.getWorld().equals(lock.getWorld()) && (dx * dx + dz * dz > 0.04 || now.getY() > lock.getY() + 0.3)) {
                Location back = lock.clone();
                back.setYaw(now.getYaw());
                back.setPitch(now.getPitch());
                player.teleport(back);
                player.setVelocity(new Vector(0, Math.min(0.0, player.getVelocity().getY()), 0));
            }
            if (tick % 2 == 0) {
                player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, now.clone().add(0, 1, 0), 5, 0.35, 0.7, 0.35,
                    0.15);
            }
        }
    }

    private static void addFreeze(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null || instance.getModifier(key) != null) {
            return;
        }
        // transitorio: no se guarda con el jugador, asi un reinicio a mitad de paralisis no lo deja trabado
        instance.addTransientModifier(new AttributeModifier(key, -1.0,
            AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void unfreeze(Player player) {
        for (Attribute attribute : new Attribute[] {Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH}) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(attribute == Attribute.MOVEMENT_SPEED ? shockSpeedKey : shockJumpKey);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Jugadores en modo de supervivencia/aventura y parados adentro de la sala (no en pasillos detras de paredes). */
    private List<Player> vulnerablePlayers() {
        List<Player> list = new ArrayList<>();
        if (arena == null) {
            return list;
        }
        for (Player player : context.players()) {
            GameMode mode = player.getGameMode();
            if ((mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && arena.contains(player.getLocation())) {
                list.add(player);
            }
        }
        return list;
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

    private void face(Warden boss, Player target) {
        if (target == null) {
            return;
        }
        float yaw = yawTo(boss.getLocation(), target.getLocation());
        boss.setRotation(yaw, 0F);
        boss.setBodyYaw(yaw);
    }

    private static float yawTo(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
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

    private void actionBar(List<Player> players, String text) {
        Component component = LEGACY.deserialize(text);
        for (Player player : players) {
            player.sendActionBar(component);
        }
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
