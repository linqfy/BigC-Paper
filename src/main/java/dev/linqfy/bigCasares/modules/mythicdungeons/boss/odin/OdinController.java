package dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin;

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
import org.bukkit.block.Block;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * El encuentro de Odín en el servidor: una tarea por tick (via {@link MythicBossSupport#scheduler()}) que mueve al
 * Warden, dibuja los avisos y aplica los golpes; las decisiones las toma {@link OdinFight}. No cambia bloques.
 *
 * <p>Bedrock: ve un Evocador escalado en la fase 1 y un Vindicador (con hacha) en la fase 2 en lugar del modelo;
 * los avisos son particulas, sonidos, titulos y la barra de accion, que Geyser muestra igual.</p>
 */
final class OdinController implements BossEncounterController {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final long SUMMON_AT = 30L;
    private static final double ARENA_RADIUS = 18.0;
    private static final double WALK_SPEED = 0.2;
    private static final double HOME_RADIUS = 6.0;
    private static final double STAND_IN_SCALE = 1.7;
    private static final long INVULNERABLE_NOTICE_GAP = 15L;
    private static final Particle.DustOptions LOCKED_LINE = new Particle.DustOptions(Color.fromRGB(225, 30, 30), 1.7F);
    private static final Particle.DustOptions AIM_LINE = new Particle.DustOptions(Color.fromRGB(240, 210, 200), 1.0F);
    private static final Particle.DustOptions STARS = new Particle.DustOptions(Color.fromRGB(255, 225, 60), 1.3F);

    private final MythicBossSupport support;
    private final BossEncounterContext context;
    private final OdinFight fight = new OdinFight();
    private final OdinGuardianSquad squad;
    private final Map<UUID, Long> lastNotice = new HashMap<>();
    private final Set<UUID> hitThisCharge = new HashSet<>();
    private BukkitTask task;
    private Location origin;
    private OdinArena arena;
    private DungeonBounds room;
    private Vector forward = new Vector(0, 0, 1);
    private List<Location> summonSpots = List.of();
    private LivingEntity standIn;
    private String currentLoop = "idle";
    private long tick;
    private boolean stopped;
    private UUID target;
    private UUID lastTarget;
    private double chargeX;
    private double chargeZ = 1.0;
    private double travelled;

    OdinController(MythicBossSupport support, BossEncounterContext context) {
        this.support = Objects.requireNonNull(support, "support");
        this.context = Objects.requireNonNull(context, "context");
        this.squad = new OdinGuardianSquad(support, context.bossInstanceId());
    }

    // ------------------------------------------------------------------ BossEncounterController

    @Override
    public void start() {
        Warden boss = context.boss();
        origin = boss.getLocation().clone();
        arena = OdinArena.around(origin.getX(), origin.getY(), origin.getZ());
        room = findRoom(origin);
        Player nearest = nearest(origin, targets());
        if (nearest != null) {
            forward = horizontal(nearest.getLocation().toVector().subtract(origin.toVector()));
            face(forward);
        }
        summonSpots = OdinGuardianSquad.placements(origin, forward, Math.max(1, targets().size()));
        spawnStandIn(Evoker.class);
        oneShot("cast");
        World world = origin.getWorld();
        world.playSound(origin, Sound.ITEM_GOAT_HORN_SOUND_0, SoundCategory.HOSTILE, 3.0F, 0.7F);
        world.playSound(origin, Sound.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 2.0F, 0.6F);
        for (Player player : context.players()) {
            title(player, "§9§lODÍN, PADRE DE TODOS",
                "§7Matá a Hugin, Munin, Geri, Freki y los einherjar: mientras vivan, Odín es invulnerable", 60);
        }
        task = support.scheduler().repeating("odin-" + context.bossInstanceId(), 1L, 1L, this::step);
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
        squad.close();
        support.cleanup(context.bossInstanceId());
    }

    @Override
    public boolean blocksProjectile(Projectile projectile) {
        if (fight.vulnerable()) {
            return false;
        }
        if (projectile.getShooter() instanceof Player shooter) {
            noticeInvulnerable(shooter);
        }
        return true;
    }

    @Override
    public double filterDamage(EntityDamageEvent event, Player attacker, double damage) {
        // sin atacante (fuego, asfixia contra una pared, caida) nunca cuenta
        if (attacker == null) {
            return 0.0;
        }
        if (fight.vulnerable()) {
            return damage;
        }
        noticeInvulnerable(attacker);
        return 0.0;
    }

    @Override
    public OptionalInt phaseOverride() {
        return OptionalInt.of(fight.phase());
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
        if (tick == SUMMON_AT && !fight.guardiansSummoned()) {
            int created = squad.summon(origin, forward, Math.max(1, targets.size()));
            fight.markGuardiansSummoned();
            origin.getWorld().playSound(origin, Sound.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 2.0F, 0.7F);
            if (created == 0) {
                support.logger().warning("[Odín] No nacio ningun guardian: la fase 2 empieza directo.");
            }
        }
        squad.tick(tick, targets, origin);
        fight.update(tick, squad.alive(), !targets.isEmpty()).ifPresent(this::enter);
        switch (fight.stage()) {
            case GUARDIANS -> guardiansTick(targets);
            case AWAKENING -> awakeningTick();
            case WINDUP -> windupTick(targets);
            case CHARGING -> chargingTick(targets);
            case RECOVERING -> recoveringTick();
            case STUNNED -> stunnedTick();
        }
        if (tick % 10L == 0L) {
            hud();
        }
    }

    private void enter(OdinFight.Stage stage) {
        World world = context.boss().getWorld();
        Location at = context.boss().getLocation();
        switch (stage) {
            case AWAKENING -> {
                spawnStandIn(Vindicator.class);
                loop("rage");
                world.playSound(at, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 3.0F, 0.6F);
                world.playSound(at, Sound.ITEM_GOAT_HORN_SOUND_1, SoundCategory.HOSTILE, 3.0F, 0.8F);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0, 1.6, 0), 60, 0.8, 1.2, 0.8, 0.05);
                for (Player player : context.players()) {
                    title(player, "§c§lODÍN SE PONE EL CASCO",
                        "§7Esquivá sus embestidas: cada 2, o si choca contra una pared, queda aturdido", 70);
                }
            }
            case WINDUP -> {
                List<Player> targets = targets();
                if (targets.isEmpty()) {
                    return;
                }
                target = OdinCharge.pickTarget(targets.stream().map(Player::getUniqueId).toList(), lastTarget,
                    bound -> ThreadLocalRandom.current().nextInt(bound));
                lastTarget = target;
                loop("carga");
                world.playSound(at, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.0F, 0.9F);
            }
            case CHARGING -> {
                hitThisCharge.clear();
                travelled = 0.0;
                loop("embestida");
                world.playSound(at, Sound.ENTITY_RAVAGER_ATTACK, SoundCategory.HOSTILE, 2.5F, 0.6F);
            }
            case RECOVERING -> loop(far() ? "walk" : "idle_casco");
            case STUNNED -> stun();
            case GUARDIANS -> {
            }
        }
    }

    // ------------------------------------------------------------------ stages

    private void guardiansTick(List<Player> targets) {
        if (tick <= SUMMON_AT && tick % 3L == 0L) {
            for (Location spot : summonSpots) {
                spot.getWorld().spawnParticle(Particle.SOUL, spot.clone().add(0, 0.2, 0), 2, 0.3, 0.05, 0.3, 0.01);
            }
        }
        if (tick % 10L == 0L) {
            Player nearest = nearest(context.boss().getLocation(), targets);
            if (nearest != null) {
                face(horizontal(nearest.getLocation().toVector().subtract(context.boss().getLocation().toVector())));
            }
        }
    }

    private void awakeningTick() {
        if (tick % 4L == 0L) {
            Location at = context.boss().getLocation();
            at.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 2.8, 0), 6, 0.6, 0.6, 0.6, 0.02);
        }
    }

    private void windupTick(List<Player> targets) {
        Warden boss = context.boss();
        Player victim = targets.stream().filter(player -> player.getUniqueId().equals(target)).findFirst().orElse(null);
        if (!fight.aimLocked(tick)) {
            if (victim != null) {
                Vector direction = horizontal(victim.getLocation().toVector().subtract(boss.getLocation().toVector()));
                chargeX = direction.getX();
                chargeZ = direction.getZ();
                face(direction);
            }
        } else if (fight.ticksInStage(tick) == fight.timings().aimTicks()) {
            Location at = boss.getLocation();
            at.getWorld().playSound(at, Sound.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 1.6F, 1.4F);
        }
        if (victim != null && tick % 5L == 0L) {
            actionBar(victim, "§c§l¡Odín te apunta! §fSalí de la línea roja");
        }
        if (tick % 3L == 0L) {
            drawLane(fight.aimLocked(tick));
        }
        if (tick % 6L == 0L) {
            Location at = boss.getLocation();
            at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(-chargeX * 0.8, 0.1, -chargeZ * 0.8), 4,
                0.3, 0.05, 0.3, 0.02);
        }
    }

    private void chargingTick(List<Player> targets) {
        Warden boss = context.boss();
        Location from = boss.getLocation();
        World world = from.getWorld();
        OdinCharge.Step step = OdinCharge.step(OdinWorld.terrain(world), from.getX(), from.getY(), from.getZ(),
            chargeX, chargeZ, OdinCharge.SPEED, origin.getX(), origin.getZ(), ARENA_RADIUS);
        if (step.outcome() != OdinCharge.Outcome.MOVE || travelled + OdinCharge.SPEED > OdinCharge.MAX_DISTANCE) {
            boolean wall = step.outcome() == OdinCharge.Outcome.WALL;
            if (wall) {
                wallImpact(from);
            }
            enter(fight.chargeEnded(tick, wall));
            return;
        }
        travelled += OdinCharge.SPEED;
        Location next = new Location(world, step.x(), step.y(), step.z());
        next.setDirection(new Vector(chargeX, 0.0, chargeZ));
        boss.teleport(next);
        for (Player player : targets) {
            if (!hitThisCharge.contains(player.getUniqueId()) && player.getWorld().equals(world)) {
                Location feet = player.getLocation();
                if (OdinCharge.hits(next.getX(), next.getY(), next.getZ(), feet.getX(), feet.getY(), feet.getZ())) {
                    hitThisCharge.add(player.getUniqueId());
                    ram(player);
                }
            }
        }
        if (tick % 2L == 0L) {
            world.spawnParticle(Particle.CLOUD, next.clone().add(0, 0.2, 0), 6, 0.6, 0.1, 0.6, 0.03);
            Block below = next.clone().subtract(0, 1, 0).getBlock();
            if (!below.isPassable()) {
                world.spawnParticle(Particle.BLOCK, next.clone().add(0, 0.1, 0), 10, 0.6, 0.1, 0.6, 0.1,
                    below.getBlockData());
            }
        }
        if (tick % 6L == 0L) {
            world.playSound(next, Sound.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 1.6F, 0.7F);
        }
    }

    private void recoveringTick() {
        if (!far()) {
            if ("walk".equals(currentLoop)) {
                loop("idle_casco");
            }
            return;
        }
        Warden boss = context.boss();
        Location from = boss.getLocation();
        Vector home = horizontal(origin.toVector().subtract(from.toVector()));
        OdinCharge.Step step = OdinCharge.step(OdinWorld.terrain(from.getWorld()), from.getX(), from.getY(),
            from.getZ(), home.getX(), home.getZ(), WALK_SPEED, origin.getX(), origin.getZ(), ARENA_RADIUS + 4.0);
        if (step.outcome() != OdinCharge.Outcome.MOVE) {
            loop("idle_casco");
            return;
        }
        loop("walk");
        Location next = new Location(from.getWorld(), step.x(), step.y(), step.z());
        next.setDirection(home);
        boss.teleport(next);
    }

    private void stunnedTick() {
        if (tick % 3L != 0L) {
            return;
        }
        Location head = context.boss().getLocation().add(0, 3.7, 0);
        double spin = tick * 0.35;
        for (int index = 0; index < 5; index++) {
            double angle = spin + index * Math.PI * 2.0 / 5.0;
            head.getWorld().spawnParticle(Particle.DUST,
                head.clone().add(Math.cos(angle) * 0.9, 0.0, Math.sin(angle) * 0.9), 1, 0, 0, 0, 0, STARS);
        }
        head.getWorld().spawnParticle(Particle.CRIT, head, 3, 0.5, 0.2, 0.5, 0.05);
    }

    // ------------------------------------------------------------------ effects

    private void stun() {
        loop("stunned");
        Location at = context.boss().getLocation();
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.6F, 0.6F);
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.HOSTILE, 1.6F, 0.6F);
        String why = fight.lastStunFromWall() ? "§7¡Chocó contra la pared!" : "§7¡Se cansó de embestir!";
        for (Player player : context.players()) {
            title(player, "§e§l¡ATURDIDO!", why + " §f¡Pegale ahora!", 30);
        }
    }

    private void wallImpact(Location at) {
        World world = at.getWorld();
        Location front = at.clone().add(chargeX * 1.4, 1.2, chargeZ * 1.4);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.4F, 0.7F);
        world.playSound(at, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE, 2.0F, 0.5F);
        world.spawnParticle(Particle.EXPLOSION, front, 2, 0.4, 0.4, 0.4, 0.0);
        Block wall = front.getBlock();
        if (!wall.isPassable()) {
            world.spawnParticle(Particle.BLOCK, front, 50, 0.8, 0.8, 0.8, 0.2, wall.getBlockData());
        }
    }

    /** El golpe: la mitad de la vida maxima, sin armadura; si no le alcanza la vida, lo mata por la via normal. */
    private void ram(Player player) {
        Warden boss = context.boss();
        AttributeInstance maximum = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maximum == null ? 20.0 : maximum.getValue();
        OdinCharge.Impact impact = OdinCharge.Impact.against(player.getHealth(), maxHealth);
        Vector push = new Vector(chargeX, 0.0, chargeZ).multiply(1.6).setY(0.55);
        if (impact.lethal()) {
            // via de daño normal: totems, mensaje de muerte "... por ODÍN" y eventos de otros modulos
            player.damage(1000.0, boss);
        } else {
            player.setHealth(Math.min(maxHealth, impact.remainingHealth()));
            player.playHurtAnimation(0.0F);
            player.setVelocity(push);
            actionBar(player, "§c¡Odín te embistió! §7Perdiste la mitad de tu vida");
        }
        Location at = player.getLocation();
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 1.6F, 0.6F);
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0F, 1.0F);
        at.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(0, 1.0, 0), 2, 0.3, 0.3, 0.3, 0.0);
    }

    private void drawLane(boolean locked) {
        Location from = context.boss().getLocation();
        World world = from.getWorld();
        double reach = OdinCharge.reach(OdinWorld.terrain(world), from.getX(), from.getY(), from.getZ(),
            chargeX, chargeZ, origin.getX(), origin.getZ(), ARENA_RADIUS);
        double length = Math.max(2.0, reach + OdinCharge.HIT_RADIUS);
        double sideX = -chargeZ * OdinCharge.HIT_RADIUS;
        double sideZ = chargeX * OdinCharge.HIT_RADIUS;
        for (double d = 1.0; d <= length; d += locked ? 0.8 : 1.6) {
            Location centre = from.clone().add(chargeX * d, 0.15, chargeZ * d);
            if (locked) {
                world.spawnParticle(Particle.DUST, centre, 1, 0, 0, 0, 0, LOCKED_LINE);
                if (Math.round(d / 0.8) % 2 == 0) {
                    world.spawnParticle(Particle.DUST, centre.clone().add(sideX, 0, sideZ), 1, 0, 0, 0, 0, LOCKED_LINE);
                    world.spawnParticle(Particle.DUST, centre.clone().add(-sideX, 0, -sideZ), 1, 0, 0, 0, 0,
                        LOCKED_LINE);
                }
            } else {
                world.spawnParticle(Particle.DUST, centre, 1, 0, 0, 0, 0, AIM_LINE);
            }
        }
    }

    private void hud() {
        String line = switch (fight.stage()) {
            case GUARDIANS -> fight.guardiansSummoned()
                ? "§bGuardianes de Odín: §f" + squad.alive() + " §7| §bOdín es invulnerable mientras vivan"
                : "§bOdín llama a sus guardianes...";
            case AWAKENING -> "§cOdín se pone el casco de guerra...";
            case STUNNED -> "§e§l¡ATURDIDO! §fPegale ahora §7(" + seconds(fight.ticksLeft(tick)) + " s)";
            default -> "§7Odín es invulnerable. §fEsquivá §e" + fight.chargesUntilStun()
                + "§f embestida(s) más §7o hacelo chocar contra una pared";
        };
        Component component = LEGACY.deserialize(line);
        for (Player player : context.players()) {
            if (fight.stage() == OdinFight.Stage.WINDUP && player.getUniqueId().equals(target)) {
                continue; // ese ya recibe el aviso de que lo apuntan
            }
            player.sendActionBar(component);
        }
    }

    private void noticeInvulnerable(Player player) {
        Long last = lastNotice.get(player.getUniqueId());
        if (last != null && tick - last < INVULNERABLE_NOTICE_GAP) {
            return;
        }
        lastNotice.put(player.getUniqueId(), tick);
        Location at = context.boss().getLocation().add(0, 1.6, 0);
        at.getWorld().playSound(at, Sound.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.0F, 0.7F);
        at.getWorld().spawnParticle(Particle.ENCHANTED_HIT, at, 8, 0.6, 0.8, 0.6, 0.1);
        actionBar(player, fight.stage() == OdinFight.Stage.GUARDIANS
            ? "§bOdín es invulnerable: §fprimero matá a sus guardianes (§e" + squad.alive() + "§f)"
            : "§7Odín es invulnerable: §fesperá a que quede aturdido");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Jugadores a los que se puede embestir y a los que apuntan los guardianes: sin creativo y adentro de la sala del
     * trono (la audiencia de 48 bloques llega a pasillos detras de las paredes). Sin nadie adentro, Odín espera.
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

    /** La caja de la mazmorra del trono; sin mazmorra (por ejemplo /boss spawn a mano) vale solo el radio. */
    private DungeonBounds findRoom(Location at) {
        try {
            return support.locator().dungeonBounds(at).orElse(null);
        } catch (RuntimeException failure) {
            support.logger().warning("[Odín] No se pudo leer la mazmorra: " + failure.getMessage());
            return null;
        }
    }

    private boolean far() {
        Location at = context.boss().getLocation();
        double dx = at.getX() - origin.getX();
        double dz = at.getZ() - origin.getZ();
        return dx * dx + dz * dz > HOME_RADIUS * HOME_RADIUS;
    }

    private void face(Vector direction) {
        Warden boss = context.boss();
        Location at = boss.getLocation();
        at.setDirection(direction);
        boss.teleport(at);
    }

    /** Animacion en bucle: corta la anterior (si no, el sombrero y el casco de dos clips se mezclan). */
    private void loop(String animation) {
        if (animation.equals(currentLoop)) {
            return;
        }
        try {
            if (currentLoop != null) {
                context.models().stopAnimation(context.model(), currentLoop);
            }
            context.animate(animation);
        } catch (RuntimeException ignored) {
            // cosmetico: la pelea no depende del modelo
        }
        currentLoop = animation;
    }

    /** Un clip de una vez encima del bucle actual (solo la fase 1 lo usa, con el mismo sombrero). */
    private void oneShot(String animation) {
        try {
            context.models().animateOnce(context.model(), animation, () -> {
            });
        } catch (RuntimeException ignored) {
            // cosmetico
        }
    }

    private void spawnStandIn(Class<? extends LivingEntity> type) {
        if (standIn != null && standIn.isValid()) {
            standIn.remove();
        }
        standIn = null;
        try {
            standIn = support.spawnBedrockStandIn(context.bossInstanceId(), context.boss(), type, STAND_IN_SCALE);
            EntityEquipment equipment = standIn.getEquipment();
            if (equipment != null && standIn instanceof Vindicator) {
                equipment.setItemInMainHand(new ItemStack(Material.IRON_AXE));
                equipment.setItemInMainHandDropChance(0.0F);
            }
            standIn.customName(Component.text("Odín"));
        } catch (RuntimeException failure) {
            support.logger().warning("[Odín] Sin doble para Bedrock: " + failure.getMessage());
        }
    }

    private static Player nearest(Location from, List<Player> players) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : players) {
            if (!player.getWorld().equals(from.getWorld())) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static Vector horizontal(Vector vector) {
        Vector flat = vector.clone().setY(0.0);
        return flat.lengthSquared() < 1.0e-6 ? new Vector(0, 0, 1) : flat.normalize();
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
}
