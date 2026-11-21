package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.BedrockStandIns;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterContext;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterController;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

/**
 * El pegamento Bukkit del encuentro de Diablo. Las decisiones (fases, movimiento, botella, ciclo de agarre y QTE) estan
 * en clases puras del paquete; esto solo lee el mundo, mueve al Warden por teleport y muestra efectos. Todo corre en
 * una tarea de un tick de {@link MythicBossSupport#scheduler()} que se cancela en {@link #stop()}.
 *
 * <p>Fase 1 (oscuridad): a cada jugador le tira una botella de vino que lo persigue y siempre pega (dano minimo). Al
 * pegar le pone Ceguera + Oscuridad y solo ve el contorno amarillo de Diablo, que parpadea. Paper no tiene glowing por
 * jugador (no hay API de metadata por espectador), asi que el contorno es una entidad propia: un Piglin Bruto
 * invisible con el tamano de Diablo, oculto por defecto y mostrado ({@code showEntity}) solo a quien fue cegado; un
 * cuerpo invisible con glowing se dibuja solo como contorno. El color sale de un equipo amarillo del scoreboard
 * principal (un jugador con un scoreboard propio de otro plugin lo ve blanco). La silueta es la de ese mob, no la del
 * modelo. Ese cuerpo pasa sus golpes al jefe como el doble de Bedrock (los proyectiles llegan como proyectiles, asi que
 * {@link #blocksProjectile} los frena igual que sobre el Warden), asi pegarle al contorno vale.</p>
 *
 * <p>Solo cuentan los jugadores adentro de la sala ({@link DiabloArena}): la audiencia del runtime es mas ancha y
 * atraviesa paredes. Quien sale de la sala pierde la oscuridad, su botella y, si estaba agarrado, queda suelto.</p>
 *
 * <p>Bedrock: Geyser aplica la Ceguera y la Oscuridad, pero Bedrock no tiene contorno de glowing; a esos jugadores se les
 * dibuja en cambio una silueta de particulas amarillas que parpadea (solo para ellos) y ven el doble vanilla de cerca.</p>
 */
final class DiabloController implements BossEncounterController {

    static final String BOTTLE_MODEL = "bigcasares_myth_diablo_bottle";
    static final String HAND_MODEL = "bigcasares_myth_diablo_hand";

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final String GLOW_TEAM = "bc_diablo_glow";
    private static final double STAND_IN_SCALE = 1.75;
    private static final double ARENA_RADIUS = 13.0;

    // fase 1
    private static final int FIRST_THROW_TICK = 40;
    private static final int THROW_WINDUP_TICKS = 16;
    private static final int THROW_SPACING_TICKS = 14;
    private static final double BOTTLE_DAMAGE = 1.0;
    private static final int DARKNESS_REFRESH_TICKS = 10;
    private static final int DARKNESS_TICKS = 60;
    private static final int BLINK_ON = 7;
    private static final int BLINK_OFF = 5;
    private static final double SWIPE_DAMAGE = 5.0;
    private static final double SWIPE_REACH = 4.5;

    // fase 2
    private static final double QTE_FAIL_DAMAGE = 3.0;
    private static final double SLAM_DAMAGE = 6.0;
    private static final double HOLD_SLACK_SQUARED = 0.36;
    private static final int HINT_COOLDOWN_TICKS = 40;

    private static final Particle.DustOptions WINE = new Particle.DustOptions(Color.fromRGB(110, 10, 30), 1.2f);
    private static final Particle.DustOptions EDGE = new Particle.DustOptions(Color.fromRGB(255, 220, 40), 1.4f);
    private static final Particle.DustOptions CLAW = new Particle.DustOptions(Color.fromRGB(200, 20, 20), 1.6f);
    private static final Particle.DustOptions ARM = new Particle.DustOptions(Color.fromRGB(150, 25, 20), 1.3f);

    private record Bottle(ArmorStand anchor, JavaModelHandle model, HomingBottle flight) {
    }

    private record Held(Location anchor, DiabloQte qte, ArmorStand hand, JavaModelHandle handModel) {
    }

    private final MythicBossSupport support;
    private final BossEncounterContext context;
    private final RandomGenerator random;
    private final UUID bossId;
    private final DiabloPhases phases;
    private final DiabloOrbit orbit = new DiabloOrbit(
        new DiabloOrbit.Settings(9.0, 3.5, 40, 30, 110, 16, 0.42, 0.5));
    private final DiabloGrabCycle cycle = new DiabloGrabCycle(
        new DiabloGrabCycle.Settings(60, 20, 600, 160, 60));
    private final NamespacedKey gripKey;
    private final Set<UUID> darkened = new LinkedHashSet<>();
    private final Map<UUID, Long> pendingThrows = new LinkedHashMap<>();
    private final Map<UUID, Bottle> bottles = new LinkedHashMap<>();
    private final Map<UUID, Held> held = new LinkedHashMap<>();
    private final Map<UUID, Long> hints = new HashMap<>();
    private Location center;
    private DiabloArena arena;
    private PiglinBrute outline;
    private Team glowTeam;
    private BukkitTask task;
    private UUID orbitTarget;
    private long tick;
    private long nextThrowTick = FIRST_THROW_TICK;
    private boolean stopped;

    DiabloController(MythicBossSupport support, BossEncounterContext context, RandomGenerator random) {
        this.support = Objects.requireNonNull(support, "support");
        this.context = Objects.requireNonNull(context, "context");
        this.random = Objects.requireNonNull(random, "random");
        this.bossId = context.bossInstanceId();
        this.phases = new DiabloPhases(context.definition().phaseTwoAt());
        this.gripKey = new NamespacedKey(support.plugin(), "diablo_grip");
    }

    // ------------------------------------------------------------------------------------------------ lifecycle

    @Override
    public void start() {
        Warden boss = context.boss();
        center = boss.getLocation().clone();
        arena = arenaAround(center);
        spawnBedrockStandIn(boss);
        spawnOutline(boss);
        task = support.scheduler().repeating("diablo-" + bossId, 1L, 1L, this::loop);
        for (Player player : fighters()) {
            title(player, "§4§lDIABLO", "§6\"Vení, tomate un vinito conmigo...\"", 10, 50, 10);
            player.playSound(boss.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 0.5f);
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
        }
        for (UUID playerId : List.copyOf(held.keySet())) {
            release(playerId);
        }
        for (UUID playerId : List.copyOf(bottles.keySet())) {
            dropBottle(playerId);
        }
        pendingThrows.clear();
        for (UUID playerId : List.copyOf(darkened)) {
            forget(playerId);
        }
        if (glowTeam != null && outline != null) {
            try {
                glowTeam.removeEntry(outline.getUniqueId().toString());
                if (glowTeam.getEntries().isEmpty()) {
                    glowTeam.unregister();
                }
            } catch (IllegalStateException alreadyGone) {
                // otro Diablo ya borro el equipo
            }
        }
        support.cleanup(bossId);
    }

    @Override
    public OptionalInt phaseOverride() {
        return OptionalInt.of(phases.phase());
    }

    /** Todo el combate es guionado: las habilidades del YAML no corren. */
    @Override
    public boolean suspendsAbilities() {
        return true;
    }

    @Override
    public boolean blocksProjectile(Projectile projectile) {
        return phases.phase() == 2 && !cycle.vulnerable();
    }

    @Override
    public double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
        if (phases.phase() == 1 || cycle.vulnerable()) {
            return damage;
        }
        if (attacker != null) {
            Long last = hints.get(attacker.getUniqueId());
            if (last == null || tick - last >= HINT_COOLDOWN_TICKS) {
                hints.put(attacker.getUniqueId(), tick);
                attacker.sendActionBar(LEGACY.deserialize("§7Diablo ni lo siente... §e¡esperá a que quede aturdido!"));
            }
        }
        return 0.0;
    }

    // ------------------------------------------------------------------------------------------------ loop

    private void loop() {
        if (stopped) {
            return;
        }
        tick++;
        Warden boss = context.boss();
        if (!boss.isValid()) {
            return;
        }
        List<Player> players = fighters();
        if (phases.phase() == 1) {
            double maximum = context.maximumHealth();
            double fraction = maximum > 0.0 ? context.health() / maximum : 0.0;
            if (phases.update(fraction)) {
                enterPhaseTwo(boss, players);
            } else {
                phaseOne(boss, players);
            }
        } else {
            phaseTwo(boss, players);
        }
    }

    // ------------------------------------------------------------------------------------------------ fase 1

    private void phaseOne(Warden boss, List<Player> players) {
        Set<UUID> present = ids(players);
        for (UUID playerId : List.copyOf(darkened)) {
            if (!present.contains(playerId)) {
                forget(playerId);
            }
        }
        pendingThrows.keySet().removeIf(playerId -> !present.contains(playerId));
        for (UUID playerId : List.copyOf(bottles.keySet())) {
            if (!present.contains(playerId)) {
                dropBottle(playerId);
            }
        }
        queueThrow(boss, players);
        launchDueBottles(boss);
        flyBottles(boss);
        if (tick % DARKNESS_REFRESH_TICKS == 0) {
            for (Player player : players) {
                if (darkened.contains(player.getUniqueId())) {
                    applyDarkness(player);
                }
            }
        }
        blinkOutline(boss, players);
        List<Player> blind = players.stream().filter(player -> darkened.contains(player.getUniqueId())).toList();
        if (blind.isEmpty()) {
            players.stream().findFirst().ifPresent(player -> face(boss, player.getLocation()));
            return;
        }
        circle(boss, blind);
    }

    /** Uno por vez: el que no fue cegado ni tiene botella encima recibe la proxima, con aviso. */
    private void queueThrow(Warden boss, List<Player> players) {
        if (tick < nextThrowTick) {
            return;
        }
        for (Player player : players) {
            UUID playerId = player.getUniqueId();
            if (darkened.contains(playerId) || pendingThrows.containsKey(playerId) || bottles.containsKey(playerId)) {
                continue;
            }
            pendingThrows.put(playerId, tick + THROW_WINDUP_TICKS);
            nextThrowTick = tick + THROW_SPACING_TICKS;
            context.animate("throw");
            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WANDERING_TRADER_DRINK_POTION, 1.4f, 0.6f);
            player.sendActionBar(LEGACY.deserialize("§5Diablo destapa una botella... §7y te mira."));
            return;
        }
    }

    private void launchDueBottles(Warden boss) {
        for (Map.Entry<UUID, Long> entry : List.copyOf(pendingThrows.entrySet())) {
            if (entry.getValue() > tick) {
                continue;
            }
            pendingThrows.remove(entry.getKey());
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            Location from = boss.getLocation().add(0.0, 3.0, 0.0);
            ArmorStand anchor = support.spawnOwned(bossId, from, ArmorStand.class, DiabloController::marker);
            JavaModelHandle model = attach(anchor, BOTTLE_MODEL);
            HomingBottle flight = new HomingBottle(vec(from), 0.45, 0.04, 0.6, 80);
            bottles.put(entry.getKey(), new Bottle(anchor, model, flight));
            boss.getWorld().playSound(from, Sound.ENTITY_WITCH_THROW, 1.4f, 0.7f);
        }
    }

    private void flyBottles(Warden boss) {
        for (Map.Entry<UUID, Bottle> entry : List.copyOf(bottles.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Bottle bottle = entry.getValue();
            if (player == null || !bottle.anchor().isValid()) {
                dropBottle(entry.getKey());
                continue;
            }
            Location eye = player.getEyeLocation().subtract(0.0, 0.3, 0.0);
            HomingBottle.Step step = bottle.flight().advance(vec(eye));
            Location at = new Location(eye.getWorld(), step.position().x(), step.position().y(), step.position().z(),
                yawTowards(bottle.anchor().getLocation(), eye), 0.0f);
            bottle.anchor().teleport(at);
            at.getWorld().spawnParticle(Particle.DUST, at, 2, 0.05, 0.05, 0.05, 0.0, WINE);
            if (step.hit()) {
                dropBottle(entry.getKey());
                hitByBottle(boss, player);
            }
        }
    }

    private void hitByBottle(Warden boss, Player player) {
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        world.playSound(eye, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.9f);
        world.playSound(eye, Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 0.6f);
        world.spawnParticle(Particle.DUST, eye, 30, 0.4, 0.4, 0.4, 0.0, WINE);
        world.spawnParticle(Particle.ITEM, eye, 12, 0.2, 0.2, 0.2, 0.08, new ItemStack(Material.GLASS_PANE));
        player.damage(BOTTLE_DAMAGE, boss);
        darkened.add(player.getUniqueId());
        applyDarkness(player);
        if (outline != null && outline.isValid() && !context.isBedrock(player.getUniqueId())) {
            player.showEntity(support.plugin(), outline);
        }
        title(player, "§8§l...", "§eSolo ves sus bordes. §6¡Matalo en la oscuridad!", 5, 50, 15);
    }

    private static void applyDarkness(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, DARKNESS_TICKS, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, DARKNESS_TICKS, 0, false, false, true));
    }

    private void blinkOutline(Warden boss, List<Player> players) {
        boolean lit = DiabloPhases.outlineLit(tick, BLINK_ON, BLINK_OFF);
        if (outline != null && outline.isValid() && outline.isGlowing() != lit) {
            outline.setGlowing(lit);
        }
        if (!lit || tick % 2 != 0) {
            return;
        }
        // Bedrock no dibuja el contorno: una silueta de particulas que solo ven ellos
        Location base = boss.getLocation();
        for (Player player : players) {
            if (!darkened.contains(player.getUniqueId()) || !context.isBedrock(player.getUniqueId())) {
                continue;
            }
            for (int index = 0; index < 14; index++) {
                double height = index * 0.25;
                double angle = tick * 0.4 + index * 1.7;
                double radius = height > 2.6 ? 0.5 : 0.75;
                player.spawnParticle(Particle.DUST, base.clone().add(Math.cos(angle) * radius, height,
                    Math.sin(angle) * radius), 1, 0.0, 0.0, 0.0, 0.0, EDGE);
            }
        }
    }

    /** Se aleja, vuelve y le da vueltas a uno de los cegados; al final de la vuelta avisa y tira un zarpazo. */
    private void circle(Warden boss, List<Player> blind) {
        Player target = blind.stream().filter(player -> player.getUniqueId().equals(orbitTarget)).findFirst()
            .orElse(null);
        if (target == null) {
            target = blind.get(random.nextInt(blind.size()));
            orbitTarget = target.getUniqueId();
        }
        Location from = boss.getLocation();
        Location targetAt = target.getLocation();
        DiabloOrbit.Move move = orbit.next(tick, point(from), point(targetAt));
        if (move.legStart()) {
            switch (move.leg()) {
                case RETREAT -> {
                    target = blind.get(random.nextInt(blind.size()));
                    orbitTarget = target.getUniqueId();
                    targetAt = target.getLocation();
                    context.animate("walk");
                    from.getWorld().playSound(from, Sound.ENTITY_WITCH_AMBIENT, 1.2f, 0.5f);
                }
                case APPROACH -> {
                    context.animate("walk");
                    from.getWorld().playSound(from, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1.0f, 0.6f);
                }
                case CIRCLE -> {
                    context.animate("circle");
                    from.getWorld().playSound(from, Sound.ENTITY_EVOKER_AMBIENT, 1.2f, 0.5f);
                }
                case SWIPE -> {
                    context.animate("attack");
                    from.getWorld().playSound(from, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.6f, 0.6f);
                }
            }
        }
        Location destination = ground(from.getWorld(), move.to(), from.getY());
        if (destination == null) {
            orbit.blocked();
            destination = from.clone();
        }
        destination.setYaw(yawTowards(destination, targetAt));
        destination.setPitch(0.0f);
        boss.teleport(destination);
        if (move.leg() == DiabloOrbit.Leg.SWIPE && tick % 3 == 0) {
            ring(targetAt, 1.2, CLAW);
        }
        if (move.strike()) {
            swipe(boss, target);
        }
    }

    private void swipe(Warden boss, Player target) {
        Location at = boss.getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 0.6f);
        if (target.getLocation().distanceSquared(at) > SWIPE_REACH * SWIPE_REACH) {
            return;
        }
        target.damage(SWIPE_DAMAGE, boss);
        Vector away = target.getLocation().toVector().subtract(at.toVector()).setY(0.0);
        if (away.lengthSquared() > 1.0e-6) {
            target.setVelocity(away.normalize().multiply(0.9).setY(0.35));
        }
    }

    // ------------------------------------------------------------------------------------------------ fase 2

    private void enterPhaseTwo(Warden boss, List<Player> players) {
        for (UUID playerId : List.copyOf(bottles.keySet())) {
            dropBottle(playerId);
        }
        pendingThrows.clear();
        for (UUID playerId : List.copyOf(darkened)) {
            forget(playerId);
        }
        if (outline != null && outline.isValid()) {
            outline.setGlowing(false);
        }
        Location destination = ground(boss.getWorld(), point(center), center.getY());
        if (destination != null) {
            boss.getWorld().spawnParticle(Particle.LARGE_SMOKE, boss.getLocation().add(0.0, 1.5, 0.0), 40, 0.6, 1.2,
                0.6, 0.02);
            boss.teleport(destination);
        }
        context.animate("rage");
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2.0f, 0.5f);
        boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation().add(0.0, 1.5, 0.0), 80, 1.0, 1.5, 1.0,
            0.05);
        // el titulo del cambio de fase lo pone el runtime con dialogue.phase-2; aca va el detalle en la barra
        for (Player player : players) {
            player.sendActionBar(LEGACY.deserialize("§4¡Diablo se enfurece! §7La oscuridad se va..."));
        }
        cycle.start(tick);
    }

    private void phaseTwo(Warden boss, List<Player> players) {
        Set<UUID> present = ids(players);
        for (UUID playerId : List.copyOf(held.keySet())) {
            if (!present.contains(playerId)) {
                release(playerId);
            }
        }
        DiabloGrabCycle.Event event = cycle.tick(tick, held.isEmpty());
        switch (event) {
            case REACH -> reach(boss, players);
            case GRAB -> grabAll(boss, players);
            case STUN -> stun(boss, players);
            case SLAM -> slam(boss, players);
            case STAND_UP -> standUp(boss, players);
            case NONE -> {
            }
        }
        switch (cycle.stage()) {
            case REACHING -> {
                if (tick % 2 == 0) {
                    players.forEach(player -> arm(boss, player));
                }
            }
            case HOLDING -> updateHeld(boss);
            case STUNNED -> {
                if (tick % 4 == 0) {
                    boss.getWorld().spawnParticle(Particle.CRIT, boss.getLocation().add(0.0, 3.8, 0.0), 6, 0.5, 0.1,
                        0.5, 0.0);
                }
                if (tick % 10 == 0) {
                    String seconds = String.format(Locale.ROOT, "%.0f", cycle.stunTicksLeft(tick) / 20.0);
                    players.forEach(player -> player.sendActionBar(
                        LEGACY.deserialize("§e¡Aturdido! §f" + seconds + "s §7para pegarle")));
                }
            }
            default -> {
            }
        }
        if (cycle.stage() != DiabloGrabCycle.Stage.STUNNED && tick % 10 == 0 && !players.isEmpty()) {
            Location from = boss.getLocation();
            Player nearest = players.stream().min((left, right) -> Double.compare(
                left.getLocation().distanceSquared(from), right.getLocation().distanceSquared(from))).orElseThrow();
            face(boss, nearest.getLocation());
        }
    }

    private void reach(Warden boss, List<Player> players) {
        context.models().stopAnimation(context.model(), "stunned");
        context.animate("sit");
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2.0f, 0.5f);
        for (Player player : players) {
            title(player, "§c§l¡TE VA A AGARRAR!", "§7Cuando te tenga: §eapretá la tecla que te pida", 0, 30, 5);
        }
    }

    private void grabAll(Warden boss, List<Player> players) {
        context.animate("grab");
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 2.0f, 0.6f);
        // 4 a 6 pulsaciones en 3.5 s: holgado a proposito, porque las teclas se muestrean una vez por tick (DiabloQte)
        DiabloQte.Settings settings = new DiabloQte.Settings(3, Math.min(6, 3 + cycle.grabs()), 70, 6);
        for (Player player : players) {
            UUID playerId = player.getUniqueId();
            Location anchor = player.getLocation().clone();
            grip(player);
            ArmorStand hand = support.spawnOwned(bossId, handLocation(boss, anchor), ArmorStand.class,
                DiabloController::marker);
            JavaModelHandle handModel = attach(hand, HAND_MODEL);
            DiabloQte qte = new DiabloQte(settings, context.isBedrock(playerId), random);
            qte.begin(tick);
            held.put(playerId, new Held(anchor, qte, hand, handModel));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.5f);
            showPrompt(player, qte);
        }
    }

    private void updateHeld(Warden boss) {
        for (Map.Entry<UUID, Held> entry : List.copyOf(held.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Held grip = entry.getValue();
            if (player == null) {
                release(entry.getKey());
                continue;
            }
            Location now = player.getLocation();
            if (!now.getWorld().equals(grip.anchor().getWorld())
                || now.distanceSquared(grip.anchor()) > HOLD_SLACK_SQUARED) {
                Location back = grip.anchor().clone();
                back.setYaw(now.getYaw());
                back.setPitch(now.getPitch());
                player.teleport(back);
            }
            DiabloQte qte = grip.qte();
            switch (qte.tick(tick, keys(player.getCurrentInput()))) {
                case PROGRESS -> player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f,
                    0.8f + 0.15f * qte.count());
                case PROMPT_CLEARED -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    showPrompt(player, qte);
                }
                case FAILED_WRONG, FAILED_TIMEOUT -> {
                    player.damage(QTE_FAIL_DAMAGE, boss);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    showPrompt(player, qte);
                }
                case COMPLETED -> {
                    release(entry.getKey());
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
                    title(player, "§a§l¡TE SOLTASTE!", "§7Esperá a que los demás se suelten", 0, 30, 10);
                    continue;
                }
                case NONE -> {
                }
            }
            if (tick % 2 == 0) {
                progress(player, qte);
            }
            if (tick % 4 == 0) {
                arm(boss, player);
            }
        }
    }

    private void stun(Warden boss, List<Player> players) {
        context.models().stopAnimation(context.model(), "grab");
        context.models().stopAnimation(context.model(), "sit");
        context.animate("stunned");
        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.5f, 0.6f);
        for (Player player : players) {
            title(player, "§e§l¡ATURDIDO!", "§f¡Pegale con todo!", 0, 30, 10);
        }
    }

    /** El agarre llego al tope: revolea a los que siguen agarrados y queda aturdido igual, agotado. */
    private void slam(Warden boss, List<Player> players) {
        Set<UUID> slammed = new HashSet<>();
        for (UUID playerId : List.copyOf(held.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            release(playerId);
            if (player != null) {
                slammed.add(playerId);
                player.damage(SLAM_DAMAGE, boss);
                title(player, "§4§l¡TARDASTE!", "§7Diablo te revoleó... §ey quedó agotado: ¡pegale!", 0, 30, 10);
            }
        }
        context.models().stopAnimation(context.model(), "grab");
        context.models().stopAnimation(context.model(), "sit");
        context.animate("stunned");
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2.0f, 0.7f);
        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.5f, 0.6f);
        for (Player player : players) {
            if (!slammed.contains(player.getUniqueId())) {
                title(player, "§e§l¡AGOTADO!", "§f¡Pegale con todo!", 0, 30, 10);
            }
        }
    }

    private void standUp(Warden boss, List<Player> players) {
        context.models().stopAnimation(context.model(), "stunned");
        context.animate("idle");
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1.6f, 0.5f);
        players.forEach(player -> player.sendActionBar(LEGACY.deserialize("§cDiablo se recupera...")));
    }

    private void showPrompt(Player player, DiabloQte qte) {
        QteKey key = qte.prompt();
        String subtitle = "§7Apretá §f" + key.keyName() + " §7" + qte.settings().presses() + " veces";
        if (qte.alternatives() && key.alternative() != null) {
            subtitle += " §8(o " + (key.alternative() == QteKey.JUMP ? "saltá" : "agachate") + ")";
        }
        title(player, key.title(), subtitle, 0, 40, 5);
    }

    private void progress(Player player, DiabloQte qte) {
        int required = qte.settings().presses();
        StringBuilder bar = new StringBuilder("§6");
        for (int index = 0; index < required; index++) {
            bar.append(index == qte.count() ? "§8" : "").append('■');
        }
        String seconds = String.format(Locale.ROOT, "%.1f", qte.ticksLeft(tick) / 20.0);
        player.sendActionBar(LEGACY.deserialize(bar + " §f" + seconds + "s §8| §e" + qte.cleared() + "/"
            + qte.settings().rounds() + " §7" + qte.prompt().keyName()));
    }

    /** Sujeta al jugador: sin velocidad ni salto (modificadores transitorios, no se guardan) y teleport si se mueve. */
    private void grip(Player player) {
        for (Attribute attribute : List.of(Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH)) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                instance.removeModifier(gripKey);
                instance.addTransientModifier(
                    new AttributeModifier(gripKey, -1.0, AttributeModifier.Operation.ADD_SCALAR));
            }
        }
    }

    private void release(UUID playerId) {
        Held grip = held.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            for (Attribute attribute : List.of(Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH)) {
                AttributeInstance instance = player.getAttribute(attribute);
                if (instance != null) {
                    instance.removeModifier(gripKey);
                }
            }
        }
        if (grip != null) {
            close(grip.handModel());
            if (grip.hand().isValid()) {
                grip.hand().remove();
            }
        }
    }

    /** La mano se pone en el jugador mirando desde Diablo: el antebrazo del modelo sale hacia atras, hacia el. */
    private static Location handLocation(Warden boss, Location player) {
        Location at = player.clone();
        at.setYaw(yawTowards(boss.getLocation(), player));
        at.setPitch(0.0f);
        return at;
    }

    /** El brazo estirado de Diablo hasta el jugador, en particulas (tambien es lo que ve Bedrock). */
    private static void arm(Warden boss, Player player) {
        Location from = boss.getLocation().add(0.0, 2.2, 0.0);
        Location to = player.getLocation().add(0.0, 1.0, 0.0);
        if (!from.getWorld().equals(to.getWorld())) {
            return;
        }
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        int points = (int) Math.min(40, Math.max(2, length * 2.0));
        step.multiply(1.0 / points);
        Location at = from.clone();
        for (int index = 0; index <= points; index++) {
            from.getWorld().spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, ARM);
            at.add(step);
        }
    }

    // ------------------------------------------------------------------------------------------------ entidades

    private void spawnBedrockStandIn(Warden boss) {
        try {
            PiglinBrute standIn = support.spawnBedrockStandIn(bossId, boss, PiglinBrute.class, STAND_IN_SCALE);
            standIn.setImmuneToZombification(true);
            EntityEquipment equipment = standIn.getEquipment();
            equipment.clear();
            equipment.setItemInMainHand(new ItemStack(Material.TRIDENT));
            equipment.setItemInMainHandDropChance(0.0f);
        } catch (RuntimeException failure) {
            support.logger().log(Level.WARNING, "Diablo: no Bedrock stand-in", failure);
        }
    }

    /** El cuerpo invisible del contorno amarillo; solo lo ve quien fue cegado y le pasa los golpes al jefe. */
    private void spawnOutline(Warden boss) {
        try {
            outline = support.spawnOwned(bossId, boss.getLocation(), PiglinBrute.class, body -> {
                body.setVisibleByDefault(false);
                body.setInvisible(true);
                body.setAI(false);
                body.setSilent(true);
                body.setImmuneToZombification(true);
                body.setCanPickupItems(false);
                body.setGlowing(false);
            });
            outline.getEquipment().clear();
            BedrockStandIns.scale(outline, STAND_IN_SCALE);
            support.bedrockStandIns().bind(outline, boss);
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            glowTeam = board.getTeam(GLOW_TEAM);
            if (glowTeam == null) {
                glowTeam = board.registerNewTeam(GLOW_TEAM);
            } else {
                pruneGlowTeam(glowTeam);
            }
            glowTeam.color(NamedTextColor.YELLOW);
            glowTeam.addEntry(outline.getUniqueId().toString());
        } catch (RuntimeException failure) {
            support.logger().log(Level.WARNING, "Diablo: no glowing outline", failure);
        }
    }

    /**
     * El scoreboard principal se guarda en disco: tras un crash el equipo queda con UUIDs de contornos que ya no
     * existen. Se borran las entradas que no son una entidad viva (los contornos no persisten).
     */
    private static void pruneGlowTeam(Team team) {
        for (String entry : Set.copyOf(team.getEntries())) {
            UUID entityId;
            try {
                entityId = UUID.fromString(entry);
            } catch (IllegalArgumentException notAnEntity) {
                continue;
            }
            Entity entity = Bukkit.getEntity(entityId);
            if (entity == null || !entity.isValid()) {
                team.removeEntry(entry);
            }
        }
    }

    private void forget(UUID playerId) {
        darkened.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        if (outline != null) {
            player.hideEntity(support.plugin(), outline);
        }
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.DARKNESS);
    }

    private void dropBottle(UUID playerId) {
        Bottle bottle = bottles.remove(playerId);
        if (bottle == null) {
            return;
        }
        close(bottle.model());
        if (bottle.anchor().isValid()) {
            bottle.anchor().remove();
        }
    }

    private JavaModelHandle attach(Entity anchor, String modelKey) {
        try {
            return support.models().attach(anchor, modelKey);
        } catch (RuntimeException failure) {
            support.logger().log(Level.FINE, "Diablo: model " + modelKey + " unavailable", failure);
            return null;
        }
    }

    private void close(JavaModelHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            support.models().close(handle);
        } catch (RuntimeException failure) {
            support.logger().log(Level.FINE, "Diablo: closing model failed", failure);
        }
    }

    private static void marker(ArmorStand stand) {
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setPersistent(false);
    }

    // ------------------------------------------------------------------------------------------------ ayudas

    /**
     * Los jugadores de la audiencia vivos, en supervivencia o aventura, que estan adentro de la sala. Quien deja de
     * cumplirlo (muere, pasa a creativo o espectador) pierde la oscuridad, su botella y el agarre en el proximo tick.
     */
    private List<Player> fighters() {
        return context.players().stream()
            .filter(player -> DiabloGrabCycle.fights(player.getGameMode(), player.isDead()))
            .filter(player -> inside(player.getLocation()))
            .toList();
    }

    private boolean inside(Location location) {
        return arena == null || (location.getWorld().equals(center.getWorld())
            && arena.contains(location.getX(), location.getY(), location.getZ()));
    }

    /** La sala alrededor del punto de aparicion: paredes sondeadas una vez y la caja de la mazmorra si hay una. */
    private DiabloArena arenaAround(Location spawn) {
        World world = spawn.getWorld();
        double[] walls = DiabloArena.probeWalls(spawn.getX(), spawn.getY(), spawn.getZ(),
            (x, y, z) -> !world.getBlockAt(x, y, z).isPassable());
        DiabloArena.Box box = support.locator().dungeonBounds(spawn)
            .map(bounds -> new DiabloArena.Box(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(),
                bounds.maxY(), bounds.maxZ()))
            .orElse(null);
        return new DiabloArena(spawn.getX(), spawn.getY(), spawn.getZ(), walls, box);
    }

    /**
     * El piso para el Warden en {@code point} (cerca de {@code referenceY}), dentro del radio de la sala; {@code null}
     * si ahi no entra (pared, pozo). No cambia ningun bloque.
     */
    private Location ground(World world, DiabloOrbit.Point point, double referenceY) {
        double x = point.x();
        double z = point.z();
        double dx = x - center.getX();
        double dz = z - center.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance > ARENA_RADIUS) {
            x = center.getX() + dx / distance * ARENA_RADIUS;
            z = center.getZ() + dz / distance * ARENA_RADIUS;
        }
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int base = (int) Math.floor(referenceY + 0.01);
        for (int dy = 1; dy >= -3; dy--) {
            int y = base + dy;
            Block below = world.getBlockAt(blockX, y - 1, blockZ);
            if (below.isPassable()) {
                continue;
            }
            if (world.getBlockAt(blockX, y, blockZ).isPassable() && world.getBlockAt(blockX, y + 1, blockZ).isPassable()
                && world.getBlockAt(blockX, y + 2, blockZ).isPassable()) {
                Location at = new Location(world, x, y, z);
                return inside(at) ? at : null;
            }
        }
        return null;
    }

    private static void face(LivingEntity entity, Location target) {
        Location at = entity.getLocation();
        entity.setRotation(yawTowards(at, target), 0.0f);
    }

    private static float yawTowards(Location from, Location to) {
        return (float) Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
    }

    private static void ring(Location at, double radius, Particle.DustOptions color) {
        for (int index = 0; index < 16; index++) {
            double angle = Math.PI * 2.0 * index / 16;
            at.getWorld().spawnParticle(Particle.DUST, at.clone().add(Math.cos(angle) * radius, 0.1,
                Math.sin(angle) * radius), 1, 0.0, 0.0, 0.0, 0.0, color);
        }
    }

    private static Set<QteKey> keys(Input input) {
        Set<QteKey> keys = EnumSet.noneOf(QteKey.class);
        if (input.isForward()) {
            keys.add(QteKey.FORWARD);
        }
        if (input.isBackward()) {
            keys.add(QteKey.BACKWARD);
        }
        if (input.isLeft()) {
            keys.add(QteKey.LEFT);
        }
        if (input.isRight()) {
            keys.add(QteKey.RIGHT);
        }
        if (input.isJump()) {
            keys.add(QteKey.JUMP);
        }
        if (input.isSneak()) {
            keys.add(QteKey.SNEAK);
        }
        if (input.isSprint()) {
            keys.add(QteKey.SPRINT);
        }
        return keys;
    }

    private static Set<UUID> ids(List<Player> players) {
        Set<UUID> ids = new HashSet<>();
        for (Player player : players) {
            ids.add(player.getUniqueId());
        }
        return ids;
    }

    private static DiabloOrbit.Point point(Location location) {
        return new DiabloOrbit.Point(location.getX(), location.getZ());
    }

    private static HomingBottle.Vec vec(Location location) {
        return new HomingBottle.Vec(location.getX(), location.getY(), location.getZ());
    }

    private static void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.showTitle(Title.title(LEGACY.deserialize(title), LEGACY.deserialize(subtitle), Title.Times.times(
            Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
    }
}
