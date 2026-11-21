package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterContext;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterController;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterRules;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

/**
 * El combate de Iblís en el servidor: capa fina sobre {@link IblisRules}, {@link IblisSmokeCycle},
 * {@link IblisTelegraphs}, {@link IblisVessels}, {@link IblisRingUse} e {@link IblisDash}. Corre en una tarea propia de
 * {@link MythicBossSupport#scheduler()} cada tick; lo que crea pasa por el support y se borra en {@link #stop()}.
 * No cambia ningun bloque: el fuego es de particulas con dano propio y las vasijas son modelos (Java) y un bloque falso
 * solo del lado del cliente (Bedrock).
 *
 * <p>Bedrock: en la fase 1 ve lo mismo que Java (solo humo y fuego de particulas: Iblís no tiene doble todavia). Desde
 * la aparicion de Salomón ve un aldeano del desierto en lugar de Salomón, un Blaze grande en lugar de Iblís y una
 * vasija decorada en cada vasija. El anillo se usa agachandose (boton de agacharse en pantalla tactil o mando).</p>
 */
final class IblisFight implements BossEncounterController {

    static final String SOLOMON_MODEL = "bigcasares_myth_iblis_solomon";

    static final double FIREBALL_RADIUS = 2.0;
    static final double FIREBALL_DAMAGE = 6.0;
    static final double LINE_LENGTH = 16.0;
    static final double LINE_HALF_WIDTH = 1.2;
    static final double LINE_DAMAGE = 7.0;
    static final double EMBER_RADIUS = 4.5;
    static final double EMBER_DAMAGE = 5.0;
    static final double DASH_DAMAGE = 7.0;
    static final double DASH_HIT_RADIUS = 1.4;
    static final double TRAIL_DAMAGE = 2.0;
    static final int TRAIL_TICKS = 80;
    static final double BREAKOUT_RADIUS = 3.5;
    static final double BREAKOUT_DAMAGE = 4.0;
    private static final int FIREBALL_WARNING = 22;
    private static final int LINE_WARNING = 26;
    private static final int EMBER_WARNING = 20;
    private static final double GLIDE_PER_TICK = 0.14;
    private static final float HIDDEN_SCALE = 0.001F;
    private static final int VESSEL_COUNT = 4;
    private static final int FEEDBACK_COOLDOWN = 30;
    private static final int SUCTION_TICKS = 8;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Particle.DustOptions WARN_EARLY = new Particle.DustOptions(Color.fromRGB(255, 150, 40), 1.5F);
    private static final Particle.DustOptions WARN_LATE = new Particle.DustOptions(Color.fromRGB(230, 40, 20), 1.8F);
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 214, 90), 1.3F);
    private static final Particle.DustOptions ARMED = new Particle.DustOptions(Color.fromRGB(120, 230, 255), 1.4F);

    /** One announced fire attack: a ball on {@code a}, a line {@code a}-{@code b}, or a ring around {@code a}. */
    record Strike(Kind kind, IblisPoint a, IblisPoint b, double radius) {
        enum Kind { FIREBALL, LINE, RING, BREAKOUT }
    }

    private record Trail(IblisPoint point, int until) {
    }

    private final MythicBossSupport support;
    private final BossEncounterContext context;
    private final UUID bossId;
    private final RandomGenerator random = new SplittableRandom();
    private final IblisRules rules = new IblisRules();
    private final IblisTelegraphs<Strike> strikes = new IblisTelegraphs<>();
    private final IblisRingUse ringUse = new IblisRingUse();
    private final List<Trail> trails = new ArrayList<>();
    private final Map<UUID, Integer> feedbackAt = new HashMap<>();
    private final Set<UUID> ringHolders = new HashSet<>();
    private final Set<UUID> dashHit = new HashSet<>();
    private final IblisRingItems rings;

    private World world;
    private IblisRoom room;
    private Location home;
    private IblisSmokeCycle smoke;
    private IblisVessels vessels;
    private IblisVesselProps props;
    private IblisDash dash;
    private BukkitTask task;
    private int tick;
    private boolean started;
    private boolean ready;
    private boolean stopped;
    private boolean modelHidden;
    private String baseLoop = "idle";
    private IblisPoint wander;
    private int wanderUntil;
    private IblisPoint lastDashPoint;
    private Location suctionFrom;
    private int suctionStart;
    private ArmorStand solomon;
    private ArmorStand solomonLabel;
    private JavaModelHandle solomonModel;
    private boolean revealed;

    IblisFight(MythicBossSupport support, BossEncounterContext context) {
        this.support = support;
        this.context = context;
        this.bossId = context.bossInstanceId();
        this.rings = new IblisRingItems(support.plugin(), bossId);
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
        world = spawn.getWorld();
        home = spawn.clone();
        IblisRoom.Passable passable = (x, y, z) -> world.getBlockAt(x, y, z).isPassable();
        int floor = IblisRoom.floorUnder(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ(), passable);
        room = IblisRoom.probe(spawn.getBlockX(), spawn.getBlockZ(), floor, passable);
        smoke = new IblisSmokeCycle(0);
        stepOffAltar(boss);
        rings.open();
        hideModel(true);
        hideRuntimeText(true);
        for (Player player : vulnerablePlayers()) {
            rings.stripStale(player);
            player.sendMessage(LEGACY.deserialize("§8☁ §7Iblís es humo: §fno se lo ve, pero se lo puede golpear. "
                + "§7Seguí el humo y pegale; esquivá el fuego marcado en el piso."));
        }
        task = support.scheduler().repeating("iblis-" + bossId, 1L, 1L, this::step);
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
        strikes.clear();
        trails.clear();
        if (vessels != null) {
            vessels.reset();
        }
        if (props != null) {
            props.close(support.plugin());
        }
        rings.close();
        if (world != null) {
            for (Player player : world.getPlayers()) {
                rings.stripOurs(player);
            }
            rings.sweepDropped(world, home, 48.0);
        }
        for (UUID id : ringHolders) {
            Player player = support.plugin().getServer().getPlayer(id);
            if (player != null) {
                rings.stripOurs(player);
            }
        }
        ringHolders.clear();
        closeSolomonModel();
        if (modelHidden) {
            hideModel(false); // the runtime plays death on the model after this
        }
        hideRuntimeText(false);
        support.cleanup(bossId);
    }

    @Override
    public boolean blocksProjectile(Projectile projectile) {
        if (!ready || (rules.stage() == IblisRules.Stage.VESSELS && vessels != null && vessels.trapped())) {
            return false;
        }
        if (projectile.getShooter() instanceof Player shooter) {
            feedback(shooter, rules.stage() == IblisRules.Stage.SMOKE
                ? "§7La flecha atraviesa el humo. §fPegale cuerpo a cuerpo."
                : "§7Iblís quema la flecha. §eAtrapalo en una vasija y pegale.");
        }
        Location at = projectile.getLocation();
        at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 6, 0.2, 0.2, 0.2, 0.02);
        return true;
    }

    @Override
    public double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
        if (!ready) {
            return damage;
        }
        IblisRules.HitKind kind = kindOf(event);
        boolean trapped = vessels != null && vessels.trapped();
        IblisRules.Decision decision = rules.admit(kind, damage, context.health(), context.maximumHealth(), trapped);
        Warden boss = context.boss();
        Location chest = boss.getLocation().add(0, 1.6, 0);
        switch (decision.verdict()) {
            case ADMITTED -> {
                if (rules.stage() == IblisRules.Stage.SMOKE) {
                    chest.getWorld().spawnParticle(Particle.LARGE_SMOKE, chest, 14, 0.4, 0.8, 0.4, 0.05);
                    chest.getWorld().spawnParticle(Particle.FLAME, chest, 6, 0.3, 0.6, 0.3, 0.03);
                } else {
                    chest.getWorld().spawnParticle(Particle.LAVA, chest, 4, 0.4, 0.6, 0.4, 0.0);
                }
            }
            case THROUGH_SMOKE -> {
                if (attacker != null) {
                    feedback(attacker, "§7La flecha atraviesa el humo. §fPegale cuerpo a cuerpo.");
                }
            }
            case FIRE_IMMUNE -> boss.setFireTicks(0);
            case ENVIRONMENT -> {
            }
            case SCENE -> {
                if (attacker != null) {
                    feedback(attacker, "§6El rey Salomón habla... §7esperá.");
                }
            }
            case UNTOUCHABLE -> {
                chest.getWorld().spawnParticle(Particle.FLAME, chest, 10, 0.5, 0.8, 0.5, 0.05);
                chest.getWorld().playSound(chest, Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 0.8F, 0.7F);
                if (attacker != null) {
                    feedback(attacker, "§7Iblís es intocable: §eagachate con el anillo junto a una vasija "
                        + "§7y esperá que pase cerca.");
                }
            }
        }
        return decision.damage();
    }

    private static IblisRules.HitKind kindOf(EntityDamageEvent event) {
        switch (event.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR, CAMPFIRE -> {
                return IblisRules.HitKind.FIRE;
            }
            default -> {
            }
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Projectile) {
                return IblisRules.HitKind.PROJECTILE;
            }
            if (byEntity.getDamager() instanceof Player) {
                return event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                    ? IblisRules.HitKind.MELEE : IblisRules.HitKind.OTHER;
            }
        }
        // suffocation, falls, explosions, mobs: not a player's hit
        return IblisRules.HitKind.ENVIRONMENT;
    }

    @Override
    public OptionalInt phaseOverride() {
        return ready ? OptionalInt.of(rules.phase()) : OptionalInt.empty();
    }

    @Override
    public boolean suspendsAbilities() {
        // todos los ataques son del guion; la habilidad de la YAML solo existe para el cargador
        return ready;
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
        boss.setFireTicks(0);
        double fraction = context.maximumHealth() <= 0.0 ? 1.0 : context.health() / context.maximumHealth();
        IblisRules.Stage changed = rules.update(tick, fraction);
        if (changed == IblisRules.Stage.SOLOMON) {
            beginSolomon(boss);
        } else if (changed == IblisRules.Stage.VESSELS) {
            beginVessels(boss);
        }
        List<Player> players = vulnerablePlayers();
        switch (rules.stage()) {
            case SMOKE -> smokeStep(boss, players, fraction);
            case SOLOMON -> solomonStep(boss, players);
            case VESSELS -> vesselStep(boss, players);
        }
        strikeStep(boss, players);
        trailStep(boss, players);
        if (tick % 20 == 0) {
            housekeeping(players);
        }
    }

    // ------------------------------------------------------------------ phase 1: smoke

    private void smokeStep(Warden boss, List<Player> players, double fraction) {
        Location at = boss.getLocation();
        // the smoke is the only sign of him: a column the size of the hitbox plus a trail
        world.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 1.4, 0), 3, 0.25, 0.8, 0.25, 0.005);
        if (tick % 2 == 0) {
            world.spawnParticle(Particle.SMOKE, at.clone().add(0, 0.3, 0), 4, 0.3, 0.1, 0.3, 0.01);
        }
        if (tick % 6 == 0) {
            world.spawnParticle(Particle.FLAME, at.clone().add(0, 2.4, 0), 1, 0.15, 0.15, 0.15, 0.005);
        }
        boolean close = false;
        for (Player player : players) {
            if (horizontal(player.getLocation(), at) <= EMBER_RADIUS) {
                close = true;
                break;
            }
        }
        IblisSmokeCycle.Attack attack = smoke.advance(tick, fraction, close);
        if (attack != null) {
            cast(boss, attack, players);
        }
        if (smoke.casting(tick) || players.isEmpty()) {
            face(boss, nearest(players, at));
            return;
        }
        if (wander == null || tick >= wanderUntil || point(at).distance(wander) < 0.4) {
            wander = pickWander(players);
            wanderUntil = tick + 100;
        }
        glide(boss, wander, GLIDE_PER_TICK);
    }

    private IblisPoint pickWander(List<Player> players) {
        Player target = players.get(random.nextInt(players.size()));
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 3.0 + random.nextDouble() * 3.0;
        IblisPoint around = point(target.getLocation());
        return room.clamp(new IblisPoint(around.x() + Math.cos(angle) * distance,
            around.z() + Math.sin(angle) * distance), 2.0);
    }

    private void cast(Warden boss, IblisSmokeCycle.Attack attack, List<Player> players) {
        if (players.isEmpty()) {
            return;
        }
        Location at = boss.getLocation();
        playOnce("attack");
        world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.6F, 0.6F);
        IblisPoint origin = point(at);
        switch (attack) {
            case FIREBALLS -> {
                List<Player> shuffled = new ArrayList<>(players);
                shuffle(shuffled);
                int count = IblisSmokeCycle.fireballs(shuffled.size());
                for (int index = 0; index < count; index++) {
                    fireball(shuffled.get(index));
                }
            }
            case FLAME_LINE -> {
                Player target = players.get(random.nextInt(players.size()));
                IblisPoint aim = point(target.getLocation());
                double dx = aim.x() - origin.x();
                double dz = aim.z() - origin.z();
                double length = Math.hypot(dx, dz);
                if (length < 0.5) {
                    dx = 1.0;
                    dz = 0.0;
                    length = 1.0;
                }
                IblisPoint end = IblisDash.clip(room.clamp(origin, IblisDash.MARGIN), new IblisPoint(
                    origin.x() + dx / length * LINE_LENGTH, origin.z() + dz / length * LINE_LENGTH), room);
                strikes.add(new Strike(Strike.Kind.LINE, origin, end, LINE_HALF_WIDTH), tick, LINE_WARNING);
                actionBar(players, "§6Iblís traza una línea de fuego... §f¡salí de la marca!");
            }
            case EMBER_RING -> {
                strikes.add(new Strike(Strike.Kind.RING, origin, origin, EMBER_RADIUS), tick, EMBER_WARNING);
                world.playSound(at, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 2.0F, 0.6F);
                actionBar(players, "§c¡El humo se enciende! §falejate de Iblís.");
            }
        }
    }

    private void shuffle(List<Player> list) {
        for (int index = list.size() - 1; index > 0; index--) {
            Collections.swap(list, index, random.nextInt(index + 1));
        }
    }

    private void fireball(Player target) {
        IblisPoint center = point(target.getLocation());
        strikes.add(new Strike(Strike.Kind.FIREBALL, center, center, FIREBALL_RADIUS), tick, FIREBALL_WARNING);
        target.playSound(target.getLocation(), Sound.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 0.8F, 0.8F);
    }

    // ------------------------------------------------------------------ transition: Solomon

    private void beginSolomon(Warden boss) {
        strikes.clear();
        wander = null;
        setBase("idle");
        Location at = boss.getLocation();
        world.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 1.5, 0), 80, 1.2, 1.5, 1.2, 0.05);
        world.playSound(at, Sound.ENTITY_BLAZE_DEATH, SoundCategory.HOSTILE, 2.0F, 0.5F);
        spawnSolomon();
        for (Player player : vulnerablePlayers()) {
            player.sendMessage(LEGACY.deserialize("§6✦ §eEl rey Salomón aparece: §f«Con mi anillo encerré a los genios. "
                + "Tomalo, y sellá a Iblís en una vasija.»"));
        }
    }

    private void spawnSolomon() {
        // Solomon stands on the altar Iblis came from
        Location at = home.clone();
        at.setYaw(yawTo(at, new Location(world, room.centerX(), room.floorY(), room.centerZ())));
        solomon = support.spawnOwned(bossId, at, ArmorStand.class, IblisFight::marker);
        solomonLabel = support.spawnOwned(bossId, at.clone().add(0, 2.6, 0), ArmorStand.class, stand -> {
            marker(stand);
            stand.customName(LEGACY.deserialize("§6§lRey Salomón"));
            stand.setCustomNameVisible(true);
        });
        try {
            solomonModel = support.models().attach(solomon, SOLOMON_MODEL);
            support.models().animate(solomonModel, "idle");
        } catch (RuntimeException | LinkageError missing) {
            solomonModel = null;
            support.logger().log(Level.WARNING, "[Iblis] Sin modelo " + SOLOMON_MODEL + ": Salomón no se ve en Java.",
                missing);
        }
        try {
            Villager standIn = support.spawnBedrockStandIn(bossId, solomon, Villager.class, 1.0);
            standIn.setVillagerType(Villager.Type.DESERT);
            standIn.setProfession(Villager.Profession.LIBRARIAN);
            standIn.setInvulnerable(true);
        } catch (RuntimeException unavailable) {
            // sin dobles de Bedrock (modulo sin Geyser)
        }
        world.strikeLightningEffect(at);
        world.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.2, 0), 60, 0.6, 1.2, 0.6, 0.05);
        world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 2.0F, 0.8F);
    }

    private void solomonStep(Warden boss, List<Player> players) {
        int since = rules.inStage(tick);
        Location at = boss.getLocation();
        if (!revealed && tick % 2 == 0) {
            world.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 1.4, 0), 4, 0.4, 0.9, 0.4, 0.01);
        }
        if (solomon != null && solomon.isValid()) {
            face(boss, solomon.getLocation());
        }
        if (since == 20) {
            giveRings(players, true);
        } else if (since == 45) {
            raiseVessels();
        } else if (since == 60) {
            reveal(boss, players);
        }
        if (solomon != null && since >= 20 && since < 40 && tick % 2 == 0) {
            Location hand = solomon.getLocation().add(0, 1.3, 0);
            for (Player player : players) {
                beam(hand, player.getLocation().add(0, 1.0, 0), GOLD);
            }
        }
    }

    private void giveRings(List<Player> players, boolean ceremony) {
        if (ceremony && solomonModel != null) {
            try {
                support.models().animateOnce(solomonModel, "give", () -> {
                    if (!stopped && solomonModel != null) {
                        support.models().animate(solomonModel, "idle");
                    }
                });
            } catch (RuntimeException ignored) {
                // cosmetico
            }
        }
        Title title = Title.title(LEGACY.deserialize("§6§lAnillo de Salomón"),
            LEGACY.deserialize("§eAgachate con el anillo en la mano junto a una vasija"),
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3000), Duration.ofMillis(600)));
        for (Player player : players) {
            handRing(player, title);
        }
    }

    /** Gives this player a ring if they have none; the title only for the ceremony or a late joiner. */
    private void handRing(Player player, Title title) {
        if (rings.has(player)) {
            return;
        }
        if (!rings.give(player)) {
            feedback(player, "§c¡Tenés el inventario lleno! §fLiberá un lugar para recibir el anillo de Salomón.");
            return;
        }
        ringHolders.add(player.getUniqueId());
        player.showTitle(title);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, SoundCategory.PLAYERS, 1.0F, 1.2F);
        player.sendMessage(LEGACY.deserialize("§6✦ §fRecibiste el §6Anillo de Salomón§f. §7Tenelo en la mano y "
            + "§eagachate (Shift / botón de agacharse) §7junto a una vasija para armarla unos segundos. "
            + "§7Si Iblís pasa cerca de una vasija armada, queda atrapado: §fsolo ahí se le puede pegar."));
    }

    private void raiseVessels() {
        List<Location> spots = new ArrayList<>();
        List<IblisPoint> points = new ArrayList<>();
        for (IblisPoint candidate : room.vesselCandidates()) {
            if (points.size() >= VESSEL_COUNT) {
                break;
            }
            Block block = world.getBlockAt((int) Math.floor(candidate.x()), room.floorY(),
                (int) Math.floor(candidate.z()));
            boolean free = block.getType().isAir() && block.getRelative(0, 1, 0).isPassable()
                && !block.getRelative(0, -1, 0).isPassable();
            boolean apart = points.stream().allMatch(other -> other.distance(candidate) >= 5.0);
            if (free && apart) {
                points.add(new IblisPoint(block.getX() + 0.5, block.getZ() + 0.5));
                spots.add(block.getLocation());
            }
        }
        if (points.size() < 2) {
            // a cluttered room: put them on the floor around the centre anyway (props only, no block changes)
            points.clear();
            spots.clear();
            for (IblisPoint candidate : room.ring(VESSEL_COUNT, 5.0, 0.0)) {
                Block block = world.getBlockAt((int) Math.floor(candidate.x()), room.floorY(),
                    (int) Math.floor(candidate.z()));
                points.add(new IblisPoint(block.getX() + 0.5, block.getZ() + 0.5));
                spots.add(block.getLocation());
            }
        }
        vessels = new IblisVessels(points);
        props = new IblisVesselProps(support, bossId);
        props.spawn(spots);
        for (int index = 0; index < vessels.size(); index++) {
            Location center = props.center(index);
            world.spawnParticle(Particle.END_ROD, center.clone().add(0, 0.6, 0), 20, 0.3, 0.6, 0.3, 0.03);
            world.playSound(center, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 0.6F, 1.6F);
            updateLabel(index);
        }
        props.showBedrock(bedrock(vulnerablePlayers()));
    }

    private void reveal(Warden boss, List<Player> players) {
        revealed = true;
        hideModel(false);
        hideRuntimeText(false);
        playOnce("rage");
        Location at = boss.getLocation();
        world.spawnParticle(Particle.FLAME, at.clone().add(0, 1.5, 0), 120, 1.2, 1.6, 1.2, 0.08);
        world.spawnParticle(Particle.LAVA, at.clone().add(0, 1.5, 0), 20, 1.0, 1.0, 1.0, 0.0);
        world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.4F, 0.7F);
        try {
            // Bedrock no ve el modelo: un Blaze grande marca a Iblís desde que se revela (en la fase 1 no hay doble)
            support.spawnBedrockStandIn(bossId, boss, Blaze.class, 2.0);
        } catch (RuntimeException unavailable) {
            // sin dobles de Bedrock
        }
        Title title = Title.title(LEGACY.deserialize("§c§l¡IBLÍS SE REVELA!"),
            LEGACY.deserialize("§fSolo herido dentro de una vasija sellada"),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(2200), Duration.ofMillis(500)));
        for (Player player : players) {
            player.showTitle(title);
        }
    }

    // ------------------------------------------------------------------ phase 2: vessels

    private void beginVessels(Warden boss) {
        if (vessels == null) {
            raiseVessels();
        }
        if (!revealed) {
            reveal(boss, vulnerablePlayers());
        }
        dash = new IblisDash(tick);
        setBase("idle");
        actionBar(vulnerablePlayers(), "§6Iblís cruza la sala hacia las vasijas: §earmá la que va a pisar.");
    }

    private void vesselStep(Warden boss, List<Player> players) {
        for (Player player : players) {
            useRing(player, players);
        }
        for (IblisVessels.Event event : vessels.advance(tick)) {
            onVesselEvent(boss, event, players);
        }
        drawVessels();
        OptionalInt sealed = vessels.sealedVessel();
        if (sealed.isPresent()) {
            holdTrapped(boss, sealed.getAsInt(), players);
            return;
        }
        IblisDash.Phase entered = dash.advance(tick, () -> IblisDash.plan(point(boss.getLocation()), vessels.points(),
            points(players), room, random));
        if (entered == IblisDash.Phase.AIM) {
            aim(boss, players);
        } else if (entered == IblisDash.Phase.DASH) {
            setBase("dash");
            dashHit.clear();
            lastDashPoint = point(boss.getLocation());
            world.playSound(boss.getLocation(), Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 2.0F, 0.5F);
        } else if (entered == IblisDash.Phase.REST) {
            setBase("idle");
            volley(boss, players);
        }
        switch (dash.phase()) {
            case AIM -> {
                if (tick % 2 == 0) {
                    drawPath(dash.plan());
                }
                face(boss, toLocation(dash.plan().to()));
            }
            case DASH -> dashMove(boss, players);
            case REST -> face(boss, nearest(players, boss.getLocation()));
        }
        OptionalInt caught = vessels.capture(point(boss.getLocation()), tick);
        if (caught.isPresent()) {
            onCapture(boss, caught.getAsInt(), players);
        }
        if (tick % 4 == 0) {
            Location at = boss.getLocation().add(0, 1.2, 0);
            world.spawnParticle(Particle.FLAME, at, 3, 0.4, 0.9, 0.4, 0.01);
            world.spawnParticle(Particle.SMOKE, at.clone().add(0, -1.0, 0), 2, 0.3, 0.1, 0.3, 0.01);
        }
    }

    private void useRing(Player player, List<Player> players) {
        boolean holding = rings.holding(player);
        if (!holding) {
            return;
        }
        boolean sneaking = player.isSneaking() || player.getCurrentInput().isSneak();
        IblisRingUse.Result result = ringUse.attempt(player.getUniqueId(), true, sneaking,
            point(player.getLocation()), vessels, tick);
        switch (result.outcome()) {
            case ARMED -> {
                int vessel = result.vessel();
                Location center = props.center(vessel);
                props.loop(vessel, "armed");
                updateLabel(vessel);
                beam(player.getLocation().add(0, 1.0, 0), center.clone().add(0, 0.8, 0), ARMED);
                world.spawnParticle(Particle.ENCHANT, center.clone().add(0, 1.0, 0), 60, 0.5, 0.8, 0.5, 0.6);
                world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.2F, 1.4F);
                actionBar(players, "§b" + player.getName() + " §farmó una vasija: §b"
                    + IblisVessels.ARMED_TICKS / 20 + " s §fpara que Iblís pase cerca.");
            }
            case NO_VESSEL -> feedback(player, "§7No hay ninguna vasija cerca. §fAcercate a una y agachate.");
            case VESSEL_BUSY -> feedback(player, "§7Esa vasija no está lista todavía.");
            case RECHARGING -> feedback(player, "§7El anillo se recarga... §f"
                + (ringUse.recharge(player.getUniqueId(), tick) + 19) / 20 + " s");
            case NONE -> {
            }
        }
    }

    private void onVesselEvent(Warden boss, IblisVessels.Event event, List<Player> players) {
        int vessel = event.vessel();
        Location center = props.center(vessel);
        switch (event.change()) {
            case EXPIRED -> {
                props.loop(vessel, "idle");
                world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8F, 1.4F);
            }
            case RECHARGED -> world.spawnParticle(Particle.WAX_OFF, center.clone().add(0, 0.8, 0), 8, 0.3, 0.3, 0.3,
                0.2);
            case BREAKOUT_WARNING -> {
                IblisPoint at = vessels.point(vessel);
                strikes.add(new Strike(Strike.Kind.BREAKOUT, at, at, BREAKOUT_RADIUS), tick,
                    IblisVessels.BREAKOUT_WARNING);
                world.playSound(center, Sound.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE, 2.0F, 0.5F);
                actionBar(players, "§c¡La vasija tiembla! §fIblís está por liberarse: alejate.");
            }
            case RELEASED -> {
                props.loop(vessel, "idle");
                setBase("idle");
                dash.rest(tick);
                suctionFrom = null;
                world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.4F, 0.7F);
                actionBar(players, "§cIblís rompió el sello. §fArmá otra vasija.");
            }
        }
        updateLabel(vessel);
    }

    private void onCapture(Warden boss, int vessel, List<Player> players) {
        suctionFrom = boss.getLocation();
        suctionStart = tick;
        setBase("trapped");
        props.loop(vessel, "capture");
        updateLabel(vessel);
        Location center = props.center(vessel);
        world.playSound(center, Sound.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.HOSTILE, 2.0F, 0.6F);
        world.playSound(center, Sound.ENTITY_BLAZE_HURT, SoundCategory.HOSTILE, 2.0F, 0.5F);
        Title title = Title.title(LEGACY.deserialize("§a§l¡ATRAPADO!"),
            LEGACY.deserialize("§fIblís quedó sellado en la vasija: §e¡pegale!"),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(1400), Duration.ofMillis(400)));
        for (Player player : players) {
            player.showTitle(title);
        }
    }

    /** Keeps Iblis inside the sealed vessel: sucked in over a few ticks, then held still. */
    private void holdTrapped(Warden boss, int vessel, List<Player> players) {
        Location center = props.center(vessel);
        center.setYaw(boss.getLocation().getYaw());
        double t = suctionFrom == null ? 1.0 : Math.min(1.0, (tick - suctionStart) / (double) SUCTION_TICKS);
        Location to = suctionFrom == null || t >= 1.0 ? center
            : suctionFrom.clone().add(center.toVector().subtract(suctionFrom.toVector()).multiply(t));
        to.setYaw(center.getYaw());
        boss.teleport(to);
        if (tick % 2 == 0) {
            for (int arm = 0; arm < 3; arm++) {
                double angle = tick * 0.35 + arm * Math.PI * 2.0 / 3.0;
                world.spawnParticle(Particle.LARGE_SMOKE, center.clone().add(Math.cos(angle) * 1.2, 1.6,
                    Math.sin(angle) * 1.2), 1, 0, 0, 0, 0.0);
            }
            world.spawnParticle(Particle.ENCHANT, center.clone().add(0, 1.2, 0), 6, 0.4, 0.6, 0.4, 0.4);
        }
        if (tick % 10 == 0) {
            int seconds = (vessels.remaining(vessel, tick) + 19) / 20;
            actionBar(players, "§a✦ ¡Iblís atrapado! §fPegale: §a" + seconds + " s");
        }
    }

    private void aim(Warden boss, List<Player> players) {
        IblisDash.Plan plan = dash.plan();
        setBase("idle");
        playOnce("attack");
        world.playSound(boss.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE, 2.0F, 0.6F);
        if (plan.vessel() >= 0) {
            Location target = props.center(plan.vessel());
            world.spawnParticle(Particle.FLAME, target.clone().add(0, 1.4, 0), 20, 0.3, 0.3, 0.3, 0.05);
            actionBar(players, "§6¡Iblís embiste hacia una vasija! §e¡Armala con el anillo!");
        } else {
            actionBar(players, "§c¡Iblís embiste! §fSalí de la marca de fuego.");
        }
    }

    private void dashMove(Warden boss, List<Player> players) {
        IblisPoint now = dash.position(tick);
        if (now == null) {
            return;
        }
        Location from = boss.getLocation();
        Location to = new Location(world, now.x(), room.floorY(), now.z(), yawTo(from, toLocation(dash.plan().to())),
            0F);
        if (!standable(to)) {
            // something blocks the way: end the dash here
            dash.rest(tick);
            setBase("idle");
            return;
        }
        boss.teleport(to);
        boss.setBodyYaw(to.getYaw());
        IblisPoint previous = lastDashPoint == null ? now : lastDashPoint;
        DamageSource source = DamageSource.builder(DamageType.MOB_ATTACK)
            .withCausingEntity(boss).withDirectEntity(boss).build();
        for (Player player : players) {
            IblisPoint at = point(player.getLocation());
            if (dashHit.contains(player.getUniqueId()) || at.distanceToSegment(previous, now) > DASH_HIT_RADIUS
                || !nearFloor(player.getLocation())) {
                continue;
            }
            dashHit.add(player.getUniqueId());
            player.damage(DASH_DAMAGE, source);
            player.setFireTicks(Math.max(player.getFireTicks(), 40));
            Vector push = new Vector(at.x() - now.x(), 0, at.z() - now.z());
            push = push.lengthSquared() < 1.0e-4 ? new Vector(0, 0, 0) : push.normalize().multiply(0.9);
            player.setVelocity(push.setY(0.45));
        }
        if (lastDashPoint == null || lastDashPoint.distance(now) >= 1.0) {
            trails.add(new Trail(now, tick + TRAIL_TICKS));
            lastDashPoint = now;
        }
        world.spawnParticle(Particle.FLAME, to.clone().add(0, 1.0, 0), 8, 0.4, 0.8, 0.4, 0.02);
    }

    private void volley(Warden boss, List<Player> players) {
        if (players.isEmpty()) {
            return;
        }
        playOnce("attack");
        world.playSound(boss.getLocation(), Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.6F, 0.7F);
        List<Player> shuffled = new ArrayList<>(players);
        shuffle(shuffled);
        int count = Math.max(1, shuffled.size() / 2);
        for (int index = 0; index < count; index++) {
            fireball(shuffled.get(index));
        }
    }

    private void drawPath(IblisDash.Plan plan) {
        double length = plan.length();
        int points = (int) Math.max(2, length * 1.5);
        for (int index = 0; index <= points; index++) {
            IblisPoint at = plan.from().toward(plan.to(), index / (double) points);
            world.spawnParticle(Particle.DUST, new Location(world, at.x(), room.floorY() + 0.1, at.z()), 1, 0.1, 0,
                0.1, 0, WARN_LATE);
        }
    }

    private void drawVessels() {
        if (tick % 5 != 0) {
            return;
        }
        for (int index = 0; index < vessels.size(); index++) {
            Location center = props.center(index);
            switch (vessels.state(index)) {
                case ARMED -> {
                    for (int point = 0; point < 12; point++) {
                        double angle = point * Math.PI / 6.0 + tick * 0.1;
                        world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(angle)
                            * IblisVessels.CAPTURE_RADIUS, 0.15, Math.sin(angle) * IblisVessels.CAPTURE_RADIUS), 1,
                            0, 0, 0, 0, ARMED);
                    }
                    world.spawnParticle(Particle.END_ROD, center.clone().add(0, 1.3, 0), 2, 0.1, 0.4, 0.1, 0.01);
                }
                case IDLE -> world.spawnParticle(Particle.DUST, center.clone().add(0, 1.3, 0), 1, 0.15, 0.1, 0.15, 0,
                    GOLD);
                default -> {
                }
            }
            if (tick % 10 == 0) {
                updateLabel(index);
            }
        }
    }

    private void updateLabel(int vessel) {
        String text = switch (vessels.state(vessel)) {
            case IDLE -> "§6⚱ Vasija §7(agachate con el anillo)";
            case ARMED -> "§b⚱ ¡ARMADA! §f" + (vessels.remaining(vessel, tick) + 19) / 20 + " s";
            case SEALED -> "§a⚱ ¡IBLÍS SELLADO!";
            case COOLDOWN -> "§8⚱ Recargando... " + (vessels.remaining(vessel, tick) + 19) / 20 + " s";
        };
        props.label(vessel, text);
    }

    // ------------------------------------------------------------------ fire: telegraphs, landings and trails

    private void strikeStep(Warden boss, List<Player> players) {
        if (tick % 2 == 0) {
            for (IblisTelegraphs.Pending<Strike> pending : strikes.pending()) {
                drawStrike(boss, pending.payload(), pending.progress(tick));
            }
        }
        for (Strike strike : strikes.land(tick)) {
            land(boss, strike, players);
        }
    }

    private void drawStrike(Warden boss, Strike strike, double progress) {
        Particle.DustOptions dust = progress >= 0.6 ? WARN_LATE : WARN_EARLY;
        double y = room.floorY() + 0.1;
        switch (strike.kind()) {
            case FIREBALL, RING, BREAKOUT -> {
                int points = strike.kind() == Strike.Kind.FIREBALL ? 14 : 24;
                for (int index = 0; index < points; index++) {
                    double angle = index * Math.PI * 2.0 / points;
                    world.spawnParticle(Particle.DUST, new Location(world, strike.a().x() + Math.cos(angle)
                        * strike.radius(), y, strike.a().z() + Math.sin(angle) * strike.radius()), 1, 0, 0, 0, 0, dust);
                }
                if (strike.kind() == Strike.Kind.FIREBALL && progress >= 0.5) {
                    // the ball flying from the smoke to the mark
                    Location from = boss.getLocation().add(0, 2.2, 0);
                    Location to = new Location(world, strike.a().x(), y, strike.a().z());
                    double t = (progress - 0.5) / 0.5;
                    Location ball = from.clone().add(to.toVector().subtract(from.toVector()).multiply(t))
                        .add(0, Math.sin(Math.PI * t) * 2.0, 0);
                    world.spawnParticle(Particle.FLAME, ball, 6, 0.15, 0.15, 0.15, 0.01);
                    world.spawnParticle(Particle.LAVA, ball, 1, 0, 0, 0, 0);
                }
            }
            case LINE -> {
                double length = strike.a().distance(strike.b());
                int points = (int) Math.max(2, length * 1.2);
                for (int index = 0; index <= points; index++) {
                    IblisPoint at = strike.a().toward(strike.b(), index / (double) points);
                    world.spawnParticle(Particle.DUST, new Location(world, at.x(), y, at.z()), 1,
                        strike.radius() * 0.4, 0, strike.radius() * 0.4, 0, dust);
                }
            }
        }
    }

    private void land(Warden boss, Strike strike, List<Player> players) {
        double y = room.floorY() + 0.2;
        Location center = new Location(world, strike.a().x(), y, strike.a().z());
        DamageSource fire = DamageSource.builder(strike.kind() == Strike.Kind.FIREBALL ? DamageType.FIREBALL
            : DamageType.IN_FIRE).withCausingEntity(boss).withDirectEntity(boss).build();
        switch (strike.kind()) {
            case FIREBALL -> {
                world.spawnParticle(Particle.EXPLOSION, center, 1);
                world.spawnParticle(Particle.FLAME, center, 40, strike.radius() * 0.5, 0.3, strike.radius() * 0.5,
                    0.06);
                world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.0F, 1.3F);
                for (Player player : players) {
                    if (point(player.getLocation()).distance(strike.a()) <= strike.radius()
                        && nearFloor(player.getLocation())) {
                        burn(player, FIREBALL_DAMAGE, fire);
                    }
                }
            }
            case LINE -> {
                double length = strike.a().distance(strike.b());
                int points = (int) Math.max(2, length * 1.5);
                for (int index = 0; index <= points; index++) {
                    IblisPoint at = strike.a().toward(strike.b(), index / (double) points);
                    world.spawnParticle(Particle.FLAME, new Location(world, at.x(), y + 0.4, at.z()), 4, 0.3, 0.5,
                        0.3, 0.02);
                }
                world.playSound(center, Sound.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1.6F, 0.6F);
                for (Player player : players) {
                    if (point(player.getLocation()).distanceToSegment(strike.a(), strike.b()) <= strike.radius()
                        && nearFloor(player.getLocation())) {
                        burn(player, LINE_DAMAGE, fire);
                    }
                }
            }
            case RING, BREAKOUT -> {
                double damage = strike.kind() == Strike.Kind.RING ? EMBER_DAMAGE : BREAKOUT_DAMAGE;
                world.spawnParticle(Particle.FLAME, center.clone().add(0, 0.6, 0), 80, strike.radius() * 0.5, 0.5,
                    strike.radius() * 0.5, 0.08);
                world.spawnParticle(Particle.LARGE_SMOKE, center.clone().add(0, 1.0, 0), 30, 1.0, 0.8, 1.0, 0.05);
                world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 2.0F, 0.4F);
                for (Player player : players) {
                    IblisPoint at = point(player.getLocation());
                    if (at.distance(strike.a()) > strike.radius() || !nearFloor(player.getLocation())) {
                        continue;
                    }
                    burn(player, damage, fire);
                    Vector push = new Vector(at.x() - strike.a().x(), 0, at.z() - strike.a().z());
                    push = push.lengthSquared() < 1.0e-4 ? new Vector(1, 0, 0) : push.normalize();
                    player.setVelocity(push.multiply(0.9).setY(0.4));
                }
            }
        }
    }

    private void trailStep(Warden boss, List<Player> players) {
        if (trails.isEmpty()) {
            return;
        }
        Iterator<Trail> iterator = trails.iterator();
        while (iterator.hasNext()) {
            if (tick >= iterator.next().until()) {
                iterator.remove();
            }
        }
        if (tick % 4 == 0) {
            for (Trail trail : trails) {
                world.spawnParticle(Particle.FLAME, new Location(world, trail.point().x(), room.floorY() + 0.15,
                    trail.point().z()), 2, 0.3, 0.05, 0.3, 0.005);
            }
        }
        if (tick % 10 != 0) {
            return;
        }
        DamageSource source = DamageSource.builder(DamageType.IN_FIRE)
            .withCausingEntity(boss).withDirectEntity(boss).build();
        for (Player player : players) {
            IblisPoint at = point(player.getLocation());
            if (!nearFloor(player.getLocation()) || player.getLocation().getY() > room.floorY() + 1.2) {
                continue;
            }
            for (Trail trail : trails) {
                if (trail.point().distance(at) <= 0.9) {
                    burn(player, TRAIL_DAMAGE, source);
                    break;
                }
            }
        }
    }

    private static void burn(Player player, double damage, DamageSource source) {
        player.damage(damage, source);
        player.setFireTicks(Math.max(player.getFireTicks(), 40));
    }

    // ------------------------------------------------------------------ housekeeping

    /** Once a second: rings in and out of the room, dropped rings, the hidden name, the Bedrock pots. */
    private void housekeeping(List<Player> players) {
        if (!revealed) {
            hideRuntimeText(true);
        }
        Set<UUID> inside = new HashSet<>();
        for (Player player : players) {
            inside.add(player.getUniqueId());
            rings.stripStale(player);
            if (rings.has(player)) {
                ringHolders.add(player.getUniqueId()); // also a ring picked up from someone else
            }
        }
        boolean ringsOut = rules.stage() != IblisRules.Stage.SMOKE && rules.inStage(tick) >= 20;
        if (ringsOut) {
            Title late = Title.title(LEGACY.deserialize("§6§lAnillo de Salomón"),
                LEGACY.deserialize("§eAgachate con el anillo en la mano junto a una vasija"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(500)));
            for (Player player : players) {
                handRing(player, late);
            }
        }
        // a ring outside the room (or of a holder who left) is taken back; it comes back on re-entry
        for (UUID id : List.copyOf(ringHolders)) {
            Player player = support.plugin().getServer().getPlayer(id);
            if (player == null) {
                continue;
            }
            if (!inside.contains(id)) {
                if (rings.stripOurs(player) > 0) {
                    player.sendActionBar(LEGACY.deserialize("§7El anillo de Salomón vuelve a su dueño."));
                }
                ringHolders.remove(id);
            }
        }
        rings.sweepDropped(world, new Location(world, room.centerX(), room.floorY(), room.centerZ()), 40.0);
        if (props != null) {
            props.showBedrock(bedrock(players));
        }
    }

    // ------------------------------------------------------------------ presentation helpers

    /** Phase 1 hides the boss model (shrunk to nothing); the runtime has no way to hide it. */
    private void hideModel(boolean hide) {
        try {
            if (context.models().scale(context.model(), hide ? HIDDEN_SCALE : 1.0F)) {
                modelHidden = hide;
            }
        } catch (RuntimeException ignored) {
            // cosmetico
        }
    }

    /** The runtime floats a name and HP over the boss; in phase 1 it would give the invisible Iblis away. */
    private void hideRuntimeText(boolean hide) {
        Warden boss = context.boss();
        if (world == null || !boss.isValid()) {
            return;
        }
        NamespacedKey key = new NamespacedKey(context.plugin(),
            BossEncounterRules.markerKeyName(context.definition().id(), true));
        String id = bossId.toString();
        for (TextDisplay display : world.getNearbyEntitiesByType(TextDisplay.class,
            boss.getLocation().add(0, 4, 0), 6.0)) {
            if (!id.equals(display.getPersistentDataContainer().get(key, PersistentDataType.STRING))) {
                continue;
            }
            for (Player player : world.getPlayers()) {
                if (hide) {
                    player.hideEntity(support.plugin(), display);
                } else {
                    player.showEntity(support.plugin(), display);
                }
            }
        }
    }

    private void closeSolomonModel() {
        if (solomonModel != null) {
            try {
                support.models().close(solomonModel);
            } catch (RuntimeException ignored) {
                // cosmetico
            }
            solomonModel = null;
        }
        if (solomon != null && solomon.isValid()) {
            solomon.remove();
        }
        if (solomonLabel != null && solomonLabel.isValid()) {
            solomonLabel.remove();
        }
    }

    private void setBase(String loop) {
        if (loop.equals(baseLoop)) {
            return;
        }
        try {
            if (!baseLoop.equals("idle")) {
                context.models().stopAnimation(context.model(), baseLoop);
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

    private void beam(Location from, Location to, Particle.DustOptions dust) {
        if (!from.getWorld().equals(to.getWorld())) {
            return;
        }
        Vector step = to.toVector().subtract(from.toVector());
        int points = (int) Math.min(30, Math.max(2, step.length() * 2.0));
        step.multiply(1.0 / points);
        Location at = from.clone();
        for (int index = 0; index <= points; index++) {
            world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
            at.add(step);
        }
    }

    // ------------------------------------------------------------------ movement helpers

    private void glide(Warden boss, IblisPoint target, double speed) {
        Location from = boss.getLocation();
        IblisPoint now = point(from);
        double distance = now.distance(target);
        if (distance < 1.0e-3) {
            return;
        }
        // stuck on something (the altar): drift straight to the target as smoke
        IblisPoint next = standable(from) ? now.toward(target, Math.min(1.0, speed / distance)) : target;
        Location to = new Location(world, next.x(), room.floorY(), next.z(), yawTo(from, toLocation(target)), 0F);
        if (!standable(to)) {
            wander = null;
            return;
        }
        boss.teleport(to);
        boss.setBodyYaw(to.getYaw());
        setBase("walk");
    }

    /** The boss spawns on the altar block; he moves on the floor, so he starts next to it. */
    private void stepOffAltar(Warden boss) {
        Location from = boss.getLocation();
        if (standable(from)) {
            return;
        }
        int[][] offsets = {{0, -2}, {2, 0}, {-2, 0}, {0, 2}, {0, -3}, {3, 0}, {-3, 0}, {0, 3}};
        for (int[] offset : offsets) {
            Location to = new Location(world, from.getX() + offset[0], room.floorY(), from.getZ() + offset[1],
                from.getYaw(), 0F);
            if (standable(to)) {
                boss.teleport(to);
                return;
            }
        }
    }

    private boolean standable(Location at) {
        if (!room.containsColumn(at.getX(), at.getZ())) {
            return false;
        }
        Block feet = world.getBlockAt(at.getBlockX(), room.floorY(), at.getBlockZ());
        return feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, 2, 0).isPassable();
    }

    private boolean nearFloor(Location at) {
        return at.getY() >= room.floorY() - 1.0 && at.getY() <= room.floorY() + 3.0;
    }

    private Location toLocation(IblisPoint point) {
        return new Location(world, point.x(), room.floorY(), point.z());
    }

    private static IblisPoint point(Location location) {
        return new IblisPoint(location.getX(), location.getZ());
    }

    private static List<IblisPoint> points(List<Player> players) {
        List<IblisPoint> list = new ArrayList<>(players.size());
        for (Player player : players) {
            list.add(point(player.getLocation()));
        }
        return list;
    }

    private static double horizontal(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    private void face(Warden boss, Player target) {
        if (target != null) {
            face(boss, target.getLocation());
        }
    }

    private void face(Warden boss, Location target) {
        float yaw = yawTo(boss.getLocation(), target);
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
            double squared = player.getLocation().distanceSquared(at);
            if (squared < distance) {
                distance = squared;
                best = player;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ audience helpers

    /** Survival/adventure players standing inside the boss room (not in corridors behind the walls). */
    private List<Player> vulnerablePlayers() {
        List<Player> list = new ArrayList<>();
        if (room == null) {
            return list;
        }
        for (Player player : context.players()) {
            GameMode mode = player.getGameMode();
            Location at = player.getLocation();
            if ((mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && at.getWorld().equals(world)
                && room.contains(at.getX(), at.getY(), at.getZ())) {
                list.add(player);
            }
        }
        return list;
    }

    private List<Player> bedrock(List<Player> players) {
        List<Player> list = new ArrayList<>();
        for (Player player : players) {
            if (support.isBedrock(player.getUniqueId())) {
                list.add(player);
            }
        }
        return list;
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

    private static void marker(ArmorStand stand) {
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        stand.setPersistent(false);
    }
}
