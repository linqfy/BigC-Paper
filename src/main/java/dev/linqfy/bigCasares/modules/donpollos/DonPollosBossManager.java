package dev.linqfy.bigCasares.modules.donpollos;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Don Pollo Boss: una estatua de Don Pollo sobre un auto (modelo bigcasares_don_pollo_boss) montada
 * en un Ravager, que embiste como un auto. Cada tanto frena, mira a un jugador y le tira dos rayos
 * laser por los ojos. Tiene barra de boss y suelta premio al morir.
 *
 * <p>Pelea por fases ({@link DonPollosBossPhase}): en la FASE 1 te persigue a velocidad de bebe zombie;
 * en la FASE 2 se aleja y te mantiene a distancia, tira meteoritos del logo de WhatsApp y spawnea
 * gallinas WhatsApp; en la FASE 3 se eleva ("No hay picante para ti..."), vacia el chunk de cada jugador
 * e invoca Labubus y al Coronel. Los bichos invocados los maneja {@link DonPollosBossMinions}.
 */
final class DonPollosBossManager implements Listener {

    static final String MODEL = "bigcasares_don_pollo_boss";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final double BAR_RADIUS = 48;
    private static final double EYE_HEIGHT = 45.5 / 16;   // altura de los ojos del modelo, en bloques
    private static final double EYE_SPREAD = 2.5 / 16;    // separacion de cada ojo al centro
    private static final int LASER_CHARGE_TICKS = 8;
    private static final int LASER_FIRE_TICKS = 28;
    /** Velocidad de un bebe zombie (zombie 0.23 + 50% por ser bebe). */
    private static final double BABY_ZOMBIE_SPEED = 0.345;
    /** El Ravager lleva solo su velocidad base a 0.35 cuando persigue (y a 0.3 sin objetivo). */
    private static final double RAVAGER_CHASE_SPEED = 0.35;
    private static final double CHASE_RANGE = 40;
    private static final List<DonPollosBossPhase> PHASES = DonPollosBossPhase.defaults();
    /** Altura de lanzamiento de los meteoritos (techo del auto) y cuanto adelante del centro salen. */
    private static final double CAR_TOP = 15.0 / 16;
    private static final double CAR_FRONT = 1.0;
    private static final double METEOR_RANGE = 32;
    /** Zombie comun: el Coronel arranca de aca y en cada ronda de las Batallas del Cubo se hace mas rapido y fuerte. */
    private static final double ZOMBIE_SPEED = 0.23;
    private static final double ZOMBIE_DAMAGE = 3;
    /** FASE FINAL: se infla 2 veces (40 ticks), apunta (8 ticks) y tira el rayo de warden. */
    private static final int BEAM_CHARGE_TICKS = 40;
    private static final int BEAM_AIM_TICKS = 8;
    private static final double BEAM_INFLATE = 0.14;
    private static final double BEAM_HIT_RADIUS = 1.3;
    private static final int JUMP_TICKS = 30;
    private static final int HOP_TICKS = 16;
    private static final double EYE_LASER_LENGTH = 8;
    /** Las paredes y el piso que cierran el cubo (se sacan al rellenarlo). */
    private static final Material CUBE_WALL = Material.STONE_BRICKS;
    /** Lo que dice cuando le ganas, una frase cada {@link #PHRASE_TICKS}. */
    private static final List<String> DEFEAT_PHRASES = DonPollosReward.DEFEAT_PHRASES;
    static final int PHRASE_TICKS = 60;

    private final JavaPlugin plugin;
    private final DonPollosModelBinder binder;
    private final DonPollosSounds sounds;
    private final DonPollosBossSettings settings;
    private final NamespacedKey bossKey;
    /** En el boss derrotado: UUID de quien le gano ("" si no se sabe). */
    private final NamespacedKey defeatedKey;
    private final DonPollosKfcBucket bucket;
    private final DonPollosRewardMenu rewardMenu;
    private final DonPollosBossMinions minions;
    private final DonPollosBossMeteors meteors = new DonPollosBossMeteors(player -> blame(player, DeathCause.METEOR));
    private final DonPollosBossShockwaves shockwaves = new DonPollosBossShockwaves(player -> blame(player, DeathCause.SHOCKWAVE));
    /** Ultimo ataque del boss (o de sus gallinas) que recibio cada jugador, para el mensaje de muerte. */
    private final Map<UUID, Blame> lastHits = new HashMap<>();
    private final Map<UUID, Boss> bosses = new HashMap<>();
    private BukkitTask task;

    DonPollosBossManager(JavaPlugin plugin, DonPollosModelBinder binder, DonPollosSounds sounds,
                         DonPollosBossSettings settings) {
        this.plugin = plugin;
        this.binder = binder;
        this.sounds = sounds;
        this.settings = settings;
        this.bossKey = new NamespacedKey(plugin, "don_pollo_boss");
        this.defeatedKey = new NamespacedKey(plugin, "don_pollo_boss_derrotado");
        this.bucket = new DonPollosKfcBucket(plugin);
        this.rewardMenu = new DonPollosRewardMenu(bucket, this::claimReward);
        this.minions = new DonPollosBossMinions(plugin, binder, settings);
    }

    void start() {
        for (World world : Bukkit.getWorlds()) {
            for (Ravager ravager : world.getEntitiesByClass(Ravager.class)) {
                if (isBoss(ravager)) {
                    track(ravager);
                }
            }
        }
        plugin.getServer().getPluginManager().registerEvents(minions, plugin);
        plugin.getServer().getPluginManager().registerEvents(rewardMenu, plugin);
        plugin.getServer().getPluginManager().registerEvents(bucket, plugin);
        bucket.start();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    // ------------------------------------------------------------ spawn

    Ravager spawn(Location location) {
        Ravager ravager = location.getWorld().spawn(location, Ravager.class, boss -> {
            boss.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
            boss.customName(LEGACY.deserialize("§4§lDon Pollo Boss"));
            boss.setCustomNameVisible(false);
            boss.setPersistent(true);
            boss.setRemoveWhenFarAway(false);
            boss.setCanJoinRaid(false);
            setAttribute(boss, Attribute.MAX_HEALTH, settings.health());
            setAttribute(boss, Attribute.SCALE, settings.scale());
            setAttribute(boss, Attribute.ATTACK_DAMAGE, settings.meleeDamage());
            setAttribute(boss, Attribute.KNOCKBACK_RESISTANCE, 1.0);
            setAttribute(boss, Attribute.FOLLOW_RANGE, CHASE_RANGE);
            boss.setHealth(settings.health());
        });
        track(ravager);
        sounds.play(ravager);
        ravager.getWorld().playSound(ravager.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 2f, 0.7f);
        return ravager;
    }

    private static void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** Un Balde de KFC dado vuelta (el premio fijo), para /donpollo give balde. */
    ItemStack kfcBucket() {
        return bucket.create();
    }

    boolean isBoss(Entity entity) {
        return entity instanceof Ravager
            && entity.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE);
    }

    private void track(Ravager ravager) {
        if (bosses.containsKey(ravager.getUniqueId())) {
            return;
        }
        BossBar bar = BossBar.bossBar(barTitle(PHASES.getFirst()), 1f,
            BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        Boss boss = new Boss(ravager, bar);
        ravager.setGravity(true);
        ravager.setAI(true);
        setInflate(ravager, 0);
        boss.nextLaser = Bukkit.getCurrentTick() + settings.laserCooldownSeconds() * 20L;
        bosses.put(ravager.getUniqueId(), boss);
        binder.bindModel(ravager, MODEL);
        String winner = ravager.getPersistentDataContainer().get(defeatedKey, PersistentDataType.STRING);
        if (winner != null) {
            long done = Bukkit.getCurrentTick() - (long) DEFEAT_PHRASES.size() * PHRASE_TICKS - 40;
            boss.defeat = new Defeat(winner.isEmpty() ? null : UUID.fromString(winner), done);
            boss.defeat.phrase = DEFEAT_PHRASES.size();
            boss.defeat.menuOpened = true;
            ravager.setAI(false);
            showDefeatedBar(boss);
        }
    }

    private void untrack(UUID id) {
        Boss boss = bosses.remove(id);
        if (boss != null) {
            for (UUID viewer : boss.viewers) {
                Player player = Bukkit.getPlayer(viewer);
                if (player != null) {
                    player.hideBossBar(boss.bar);
                }
            }
            binder.release(id);
            minions.removeAll(boss.minions);
            endCube(boss);
        }
    }

    // ------------------------------------------------------------ juego

    private void tick() {
        long now = Bukkit.getCurrentTick();
        meteors.tick();
        shockwaves.tick();
        for (Boss boss : new ArrayList<>(bosses.values())) {
            Ravager ravager = boss.ravager;
            if (!ravager.isValid()) {
                continue;
            }
            updateBar(boss);
            if (boss.defeat != null) {
                tickDefeat(boss, now);
                continue;
            }
            updatePhase(boss);
            if (boss.cube != null) {
                tickCube(boss, now);
            }
            if (now % 20 == 0) {
                minions.tick(boss.minions);
            }
            if (boss.levitateEnd > 0) {
                levitate(boss, now);
                continue;
            }
            if (boss.hoverAt != null) {
                hover(boss, now);
                continue;
            }
            if (boss.phase.has(DonPollosBossPhase.Ability.EYE_LASERS) && now % 2 == 0) {
                eyeLasers(boss, now);
            }
            if (boss.jumpEnd > 0) {
                tickJump(boss, now);
                continue;
            }
            if (boss.beamStart > 0) {
                tickBeam(boss, now);
                continue;
            }
            if (boss.firingUntil > 0) {
                fireLaser(boss, now);
                continue;
            }
            if (now % 10 == 0) {
                if (DonPollosBossPhase.KEEP_AWAY.equals(boss.phase.ai())) {
                    keepAway(boss, now);
                } else {
                    chase(boss);
                }
            }
            if (boss.phase.laser() && now >= boss.nextLaser) {
                startLaser(boss, now);
            }
            if (boss.phase.meteors() && now >= boss.nextMeteor) {
                launchMeteor(boss, now);
            }
            if (boss.phase.chickens() && now >= boss.nextChickens) {
                spawnChickens(boss);
                boss.nextChickens = now + Math.round(settings.chickenWaveSeconds() * 20);
            }
            if (boss.phase.has(DonPollosBossPhase.Ability.HOP)) {
                hop(boss, now);
            }
            if (boss.phase.has(DonPollosBossPhase.Ability.WARDEN_BEAM) && now >= boss.nextBeam) {
                startBeam(boss, now);
            } else if (boss.phase.has(DonPollosBossPhase.Ability.MEGA_JUMP) && now >= boss.nextJump) {
                startJump(boss, now);
            }
            if (boss.phase.has(DonPollosBossPhase.Ability.FINAL_COLONELS) && now >= boss.nextFinalColonels) {
                spawnFinalColonels(boss);
                boss.nextFinalColonels = now + Math.round(settings.phase4().colonelSeconds() * 20);
            }
        }
    }

    // ------------------------------------------------------------ fases

    private static Component barTitle(DonPollosBossPhase phase) {
        return LEGACY.deserialize("§4§lDON POLLO BOSS §8| §c§l" + phase.title());
    }

    /** Cambia de fase segun la vida y lo anuncia; la fase define como se mueve y si tira laser. */
    private void updatePhase(Boss boss) {
        Ravager ravager = boss.ravager;
        double fraction = ravager.getHealth() / ravager.getAttribute(Attribute.MAX_HEALTH).getValue();
        DonPollosBossPhase phase = DonPollosBossPhase.forHealth(PHASES, fraction);
        if (phase.equals(boss.phase)) {
            return;
        }
        boss.phase = phase;
        boss.bar.name(barTitle(phase));
        plugin.getLogger().info("Don Pollo Boss en " + phase.title() + " (vida " + Math.round(fraction * 100) + "%)");
        applyPhaseSpeed(ravager, phase);
        applyPhaseScale(ravager, phase);
        long now = Bukkit.getCurrentTick();
        if (boss.levitateEnd > 0 && !phase.levitate()) {
            finishLevitation(boss);
        }
        boss.bar.color(phase.has(DonPollosBossPhase.Ability.GROW) ? BossBar.Color.PURPLE : BossBar.Color.RED);
        boss.nextBeam = now + 60;
        boss.nextJump = now + Math.round(settings.phase4().jumpCooldownSeconds() * 10);
        boss.nextFinalColonels = now + 60;
        if (DonPollosBossPhase.KEEP_AWAY.equals(phase.ai())) {
            ravager.setTarget(null);
        }
        boss.nextMeteor = now + 40;
        boss.nextChickens = now + 40;
        // si la fase tiene mensaje, el texto grande es el mensaje (en la FASE 3 queda mientras levita)
        boolean hasMessage = !phase.message().isEmpty();
        long stayMillis = phase.levitate() ? Math.round(settings.phase3().levitateSeconds() * 1000)
            : hasMessage ? 3000 : 1800;
        // un mensaje largo con coma va en dos renglones (titulo y subtitulo)
        String main = hasMessage ? "§4§o" + phase.message() : "§4§l" + phase.title();
        String sub = hasMessage ? "§c§l" + phase.title() : "§7Don Pollo Boss";
        int comma = phase.message().indexOf(", ");
        if (hasMessage && comma > 0 && phase.message().length() > 30) {
            main = "§4§l" + phase.message().substring(0, comma);
            sub = "§c§o" + phase.message().substring(comma + 2) + " §8| §c§l" + phase.title();
        }
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
            LEGACY.deserialize(main),
            LEGACY.deserialize(sub),
            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300),
                java.time.Duration.ofMillis(stayMillis), java.time.Duration.ofMillis(700)));
        for (UUID viewer : boss.viewers) {
            Player player = Bukkit.getPlayer(viewer);
            if (player != null) {
                player.showTitle(title);
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, hasMessage ? 0.6f : 1.4f);
            }
        }
        if (phase.levitate()) {
            startLevitation(boss, now);
        }
    }

    // ------------------------------------------------------------ FASE 3: levitar, pozo e invocaciones

    /** Se eleva {@code levitate-height} bloques en {@code levitate-seconds} y le vacia el piso a cada jugador. */
    private void startLevitation(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        if (boss.firingUntil > 0) {
            endLaser(boss, now);
        }
        ravager.setTarget(null);
        ravager.setAI(false);
        ravager.setGravity(false);
        ravager.setVelocity(new Vector());
        boss.levitateFrom = ravager.getLocation();
        boss.levitateStart = now;
        boss.levitateEnd = now + Math.round(settings.phase3().levitateSeconds() * 20);
        ravager.getWorld().playSound(ravager.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 2f, 0.6f);
        for (UUID viewer : boss.viewers) {
            Player player = Bukkit.getPlayer(viewer);
            if (player != null && attackable(player) && !inAnyPit(boss, player.getLocation())) {
                Pit pit = digPit(player, settings.phase3().pitDepth());
                if (pit != null) {
                    boss.pits.add(pit);
                }
            }
        }
        if (boss.phase.summons() && boss.cube == null && !boss.pits.isEmpty()) {
            startCube(boss, now);
        }
    }

    private void levitate(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        double total = boss.levitateEnd - boss.levitateStart;
        double t = Math.min(1, (now - boss.levitateStart) / total);
        double eased = t * t * (3 - 2 * t);
        Location at = boss.levitateFrom.clone().add(0, settings.phase3().levitateHeight() * eased, 0);
        at.setYaw(ravager.getLocation().getYaw() + 3);  // gira despacito mientras sube
        at.setPitch(0);
        ravager.teleport(at);
        World world = ravager.getWorld();
        world.spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 0.5, 0), 12, 0.8, 0.3, 0.8, 0.02, null, true);
        world.spawnParticle(Particle.FLAME, at, 4, 0.6, 0.1, 0.6, 0.01, null, true);
        if (now % 20 == 0) {
            world.playSound(at, Sound.BLOCK_BEACON_AMBIENT, 2f, 0.6f);
        }
        if (now % 3 == 0) {
            shieldSphere(ravager, now);
        }
        if (now >= boss.levitateEnd) {
            if (boss.cube != null) {
                // se queda arriba, protegido, hasta que se ganen las Batallas del Cubo
                boss.levitateEnd = 0;
                boss.hoverAt = at;
            } else {
                finishLevitation(boss);
            }
        }
    }

    /** Flota arriba (sube y baja apenas) mirando al jugador mas cercano, dentro de la esfera. */
    private void hover(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        Location at = boss.hoverAt.clone().add(0, Math.sin(now / 20.0) * 0.3, 0);
        Player player = nearestPlayer(ravager.getLocation(), BAR_RADIUS * 2);
        if (player != null && player.getWorld() == at.getWorld()) {
            Vector look = player.getLocation().toVector().subtract(at.toVector());
            at.setYaw((float) Math.toDegrees(Math.atan2(-look.getX(), look.getZ())));
        } else {
            at.setYaw(ravager.getLocation().getYaw());
        }
        at.setPitch(0);
        ravager.teleport(at);
        if (now % 3 == 0) {
            shieldSphere(ravager, now);
        }
        if (now % 40 == 0) {
            ravager.getWorld().playSound(at, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 0.8f);
        }
    }

    /**
     * Esfera de particulas azules alrededor del boss: pocos puntos repartidos parejo (espiral de Fibonacci)
     * que van girando, asi se lo ve adentro pero se nota la forma.
     */
    private void shieldSphere(Ravager ravager, long now) {
        double scale = scale(ravager);
        Location center = ravager.getLocation().add(0, 1.55 * scale, 0);
        double radius = 2.4 * scale;
        int points = 110;
        double golden = Math.PI * (3 - Math.sqrt(5));
        double spin = now * 0.02;
        Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(60, 150, 255), 1.1f);
        World world = ravager.getWorld();
        for (int i = 0; i < points; i++) {
            double y = 1 - 2 * (i + 0.5) / points;
            double ring = Math.sqrt(1 - y * y);
            double angle = golden * i + spin;
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(angle) * ring * radius, y * radius,
                Math.sin(angle) * ring * radius), 1, 0, 0, 0, 0, blue, true);
        }
    }

    /** Termina de subir (o de flotar): baja planeando (no se lastima al caer) y sigue peleando. */
    private void finishLevitation(Boss boss) {
        boss.levitateEnd = 0;
        boss.hoverAt = null;
        boss.ravager.setGravity(true);
        boss.ravager.setAI(true);
        boss.ravager.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 20, 0, false, false));
    }

    // ------------------------------------------------------------ FASE FINAL

    /** En la FASE FINAL se agranda; en las otras vuelve al tamano normal. */
    private void applyPhaseScale(Ravager ravager, DonPollosBossPhase phase) {
        boolean grow = phase.has(DonPollosBossPhase.Ability.GROW);
        setAttribute(ravager, Attribute.SCALE, settings.scale() * (grow ? settings.phase4().growFactor() : 1));
        if (grow) {
            World world = ravager.getWorld();
            world.playSound(ravager.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 3f, 0.5f);
            world.spawnParticle(Particle.WITCH, ravager.getLocation().add(0, 1.5, 0), 80, 1.5, 1.5, 1.5, 0.02,
                null, true);
        }
    }

    private void setInflate(Ravager ravager, double amount) {
        AttributeInstance scale = ravager.getAttribute(Attribute.SCALE);
        if (scale == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, "boss_inflate");
        scale.removeModifier(key);
        if (amount > 0) {
            scale.addModifier(new org.bukkit.attribute.AttributeModifier(key, amount,
                org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                org.bukkit.inventory.EquipmentSlotGroup.ANY));
        }
    }

    /** Laseres violetas finitos de adorno que salen de los ojos (no pegan). */
    private void eyeLasers(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        Vector forward = ravager.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1e-6) {
            return;
        }
        forward.normalize().rotateAroundY(Math.sin(now / 9.0) * 0.25).setY(-0.15).normalize();
        Particle.DustOptions purple = new Particle.DustOptions(Color.fromRGB(176, 64, 255), 0.45f);
        double length = EYE_LASER_LENGTH * scale(ravager) / settings.scale();
        for (int side : new int[]{1, -1}) {
            Location from = eyes(ravager, side);
            for (double d = 0.15; d <= length; d += 0.22) {
                ravager.getWorld().spawnParticle(Particle.DUST, from.clone().add(forward.clone().multiply(d)), 1,
                    0, 0, 0, 0, purple);
            }
        }
    }

    /** Se mueve a los saltos con el auto mientras te persigue. */
    private void hop(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        if (now < boss.nextHop || !ravager.isOnGround() || !(ravager.getTarget() instanceof Player target)) {
            return;
        }
        Vector to = target.getLocation().toVector().subtract(ravager.getLocation().toVector()).setY(0);
        if (to.lengthSquared() < 9) {
            return;
        }
        ravager.setVelocity(to.normalize().multiply(0.38).setY(0.42));
        boss.nextHop = now + HOP_TICKS;
        World world = ravager.getWorld();
        world.playSound(ravager.getLocation(), Sound.ENTITY_RAVAGER_STEP, 1.6f, 0.7f);
        Block ground = ravager.getLocation().add(0, -0.5, 0).getBlock();
        if (ground.getType().isSolid()) {
            world.spawnParticle(Particle.BLOCK, ravager.getLocation(), 14, 0.8, 0.1, 0.8, 0, ground.getBlockData());
        }
    }

    /** Se infla y desinfla 2 veces mirandote; despues fija a donde estabas y tira el rayo ahi. */
    private void startBeam(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        Player target = nearestPlayer(ravager.getLocation(), settings.phase4().beamRange());
        if (target == null) {
            boss.nextBeam = now + 20;
            return;
        }
        boss.beamTarget = target.getUniqueId();
        boss.beamStart = now;
        boss.beamPoint = null;
        ravager.setAI(false);
        ravager.getWorld().playSound(ravager.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 3f, 0.8f);
    }

    private void tickBeam(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        long t = now - boss.beamStart;
        Player target = boss.beamTarget == null ? null : Bukkit.getPlayer(boss.beamTarget);
        if (t < BEAM_CHARGE_TICKS) {
            // dos infladas: |sen| tiene dos picos en 40 ticks
            setInflate(ravager, BEAM_INFLATE * Math.abs(Math.sin(Math.PI * t / 20.0)));
            if (target != null && target.getWorld() == ravager.getWorld()) {
                faceTowards(ravager, target.getLocation());
            }
            return;
        }
        setInflate(ravager, 0);
        if (boss.beamPoint == null) {
            if (target == null || target.getWorld() != ravager.getWorld()) {
                endBeam(boss, now);
                return;
            }
            // apunta a donde estas AHORA; despues ya no te sigue
            boss.beamPoint = target.getLocation().add(0, 1.0, 0);
            faceTowards(ravager, boss.beamPoint);
            ravager.getWorld().playSound(ravager.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.6f);
        }
        Location from = eyes(ravager, 0);
        Vector direction = boss.beamPoint.toVector().subtract(from.toVector());
        if (direction.lengthSquared() < 1e-6) {
            endBeam(boss, now);
            return;
        }
        direction.normalize();
        if (t < BEAM_CHARGE_TICKS + BEAM_AIM_TICKS) {
            Particle.DustOptions aim = new Particle.DustOptions(Color.fromRGB(40, 220, 220), 0.6f);
            for (double d = 0.5; d <= settings.phase4().beamRange(); d += 1.0) {
                ravager.getWorld().spawnParticle(Particle.DUST, from.clone().add(direction.clone().multiply(d)), 1,
                    0, 0, 0, 0, aim, true);
            }
            return;
        }
        fireBeam(boss, from, direction);
        endBeam(boss, now);
    }

    /** Rayo de warden en linea recta (atraviesa paredes, como el del warden). */
    private void fireBeam(Boss boss, Location from, Vector direction) {
        Ravager ravager = boss.ravager;
        World world = ravager.getWorld();
        double range = settings.phase4().beamRange();
        world.playSound(from, Sound.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.9f);
        for (double d = 1; d <= range; d += 1.0) {
            world.spawnParticle(Particle.SONIC_BOOM, from.clone().add(direction.clone().multiply(d)), 1, 0, 0, 0, 0,
                null, true);
        }
        org.bukkit.damage.DamageSource source = org.bukkit.damage.DamageSource.builder(
                org.bukkit.damage.DamageType.SONIC_BOOM)
            .withCausingEntity(ravager).withDirectEntity(ravager).build();
        for (Player player : world.getPlayers()) {
            if (!attackable(player)) {
                continue;
            }
            Vector chest = player.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
            double along = chest.dot(direction);
            if (along < 0 || along > range) {
                continue;
            }
            double off = chest.clone().subtract(direction.clone().multiply(along)).length();
            if (off <= BEAM_HIT_RADIUS) {
                blame(player, DeathCause.BEAM);
                player.damage(settings.phase4().beamDamage(), source);
                player.setVelocity(direction.clone().setY(0).multiply(2.0).setY(0.5));
            }
        }
    }

    private void endBeam(Boss boss, long now) {
        setInflate(boss.ravager, 0);
        boss.beamStart = 0;
        boss.beamPoint = null;
        boss.beamTarget = null;
        boss.ravager.setAI(true);
        boss.nextBeam = now + Math.round(settings.phase4().beamCooldownSeconds() * 20);
        boss.nextJump = Math.max(boss.nextJump, now + 40);
    }

    /** Mega salto: cae a {@code jump-land-distance} bloques del jugador y arma la onda expansiva. */
    private void startJump(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        Player target = nearestPlayer(ravager.getLocation(), CHASE_RANGE);
        if (target == null) {
            boss.nextJump = now + 20;
            return;
        }
        Location from = ravager.getLocation();
        Vector back = from.toVector().subtract(target.getLocation().toVector()).setY(0);
        if (back.lengthSquared() < 0.01) {
            back = new Vector(1, 0, 0).rotateAroundY(ThreadLocalRandom.current().nextDouble(Math.PI * 2));
        }
        Location land = target.getLocation().add(back.normalize().multiply(settings.phase4().jumpLandDistance()));
        land.setY(groundY(land, Math.max(from.getY(), target.getLocation().getY()) + 8, target.getLocation().getY()));
        boss.jumpFrom = from;
        boss.jumpTo = land;
        boss.jumpApex = Math.max(8, from.distance(land) / 2.5);
        boss.jumpStart = now;
        boss.jumpEnd = now + JUMP_TICKS;
        boss.jumpTarget = target.getUniqueId();
        ravager.setAI(false);
        ravager.setGravity(false);
        World world = ravager.getWorld();
        world.playSound(from, Sound.ENTITY_RAVAGER_ROAR, 3f, 0.6f);
        world.playSound(from, Sound.ENTITY_GOAT_LONG_JUMP, 3f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION, from, 4, 1, 0.2, 1, 0, null, true);
    }

    /** Primer piso solido bajando desde {@code fromY} (si no hay, la altura de respaldo). */
    private static double groundY(Location at, double fromY, double fallback) {
        Location probe = at.clone();
        for (int y = (int) Math.floor(fromY); y > fromY - 48 && y > at.getWorld().getMinHeight(); y--) {
            probe.setY(y);
            Block block = probe.getBlock();
            if (block.getType().isSolid() && block.getRelative(0, 1, 0).isPassable()
                && block.getRelative(0, 2, 0).isPassable()) {
                return y + 1;
            }
        }
        return fallback;
    }

    private void tickJump(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        double t = Math.min(1, (now - boss.jumpStart) / (double) JUMP_TICKS);
        Vector path = boss.jumpTo.toVector().subtract(boss.jumpFrom.toVector());
        Location at = boss.jumpFrom.clone().add(path.multiply(t)).add(0, boss.jumpApex * 4 * t * (1 - t), 0);
        Player target = boss.jumpTarget == null ? null : Bukkit.getPlayer(boss.jumpTarget);
        if (target != null && target.getWorld() == at.getWorld()) {
            Vector look = target.getLocation().toVector().subtract(at.toVector());
            at.setYaw((float) Math.toDegrees(Math.atan2(-look.getX(), look.getZ())));
        } else {
            at.setYaw(ravager.getLocation().getYaw());
        }
        at.setPitch(0);
        ravager.teleport(at);
        ravager.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 3, 0.5, 0.2, 0.5, 0.01, null, true);
        if (t >= 1) {
            boss.jumpEnd = 0;
            ravager.setGravity(true);
            ravager.setAI(true);
            ravager.setVelocity(new Vector());
            shockwaves.slam(ravager, boss.jumpTo, settings.phase4().shockwaveRadius(), settings.phase4().shockwaveDamage());
            boss.nextJump = now + Math.round(settings.phase4().jumpCooldownSeconds() * 20);
            boss.nextBeam = Math.max(boss.nextBeam, now + 40);
        }
    }

    /** Invoca Sanders fase final (Coroneles mas rapidos, fuertes y con mas vida) al lado del boss. */
    private void spawnFinalColonels(Boss boss) {
        Ravager ravager = boss.ravager;
        DonPollosBossPhase4Settings phase4 = settings.phase4();
        long alive = minions.count(boss.minions, kind -> kind == DonPollosBossMinions.Kind.CORONEL_FINAL);
        long count = Math.min(phase4.colonelsPerWave(), phase4.maxColonels() - alive);
        for (int i = 0; i < count; i++) {
            Location location = DonPollosBossMinions.around(ravager.getLocation(), 2 * scale(ravager));
            boss.minions.add(minions.spawn(DonPollosBossMinions.Kind.CORONEL_FINAL, location,
                nearestPlayer(location, CHASE_RANGE), colonel -> {
                    DonPollosBossMinions.setAttribute(colonel, Attribute.MOVEMENT_SPEED, ZOMBIE_SPEED * phase4.colonelSpeed());
                    DonPollosBossMinions.setAttribute(colonel, Attribute.ATTACK_DAMAGE, ZOMBIE_DAMAGE + phase4.colonelDamage());
                    DonPollosBossMinions.setAttribute(colonel, Attribute.MAX_HEALTH, phase4.colonelHealth());
                    colonel.setHealth(phase4.colonelHealth());
                }).getUniqueId());
        }
    }

    /** Vacia el chunk donde esta el jugador: {@code depth} capas de bloques debajo de sus pies (nunca la bedrock). */
    static int pitBottom(int feetY, int depth, int minHeight) {
        return Math.max(minHeight, feetY - depth);
    }

    /** El cubo vaciado: guarda como estaba cada bloque que toco (con su contenido) para dejarlo igual despues. */
    private record Pit(World world, int chunkX, int chunkZ, int feet, int bottom, List<BlockState> changed) {
        boolean contains(Location location) {
            return location.getWorld() == world && location.getBlockX() >> 4 == chunkX
                && location.getBlockZ() >> 4 == chunkZ && location.getY() < feet + 1 && location.getY() >= bottom - 1;
        }

        /** El cubo con sus paredes y su piso (el anillo de afuera del chunk incluido). */
        boolean containsBlock(Block block) {
            int x = block.getX() - chunkX * 16;
            int z = block.getZ() - chunkZ * 16;
            return block.getWorld() == world && x >= -1 && x <= 16 && z >= -1 && z <= 16
                && block.getY() >= bottom - 1 && block.getY() <= feet;
        }
    }

    private static boolean inAnyPit(Boss boss, Location location) {
        return boss.pits.stream().anyMatch(pit -> pit.contains(location));
    }

    private Pit digPit(Player player, int depth) {
        if (depth <= 0) {
            return null;
        }
        World world = player.getWorld();
        Chunk chunk = player.getLocation().getChunk();
        int feet = player.getLocation().getBlockY();
        int bottom = pitBottom(feet, depth, world.getMinHeight());
        List<BlockState> changed = new ArrayList<>();
        for (int y = feet - 1; y >= bottom; y--) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (!block.getType().isAir() && block.getType() != Material.BEDROCK) {
                        changed.add(block.getState());
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
        // paredes de todo el alto del cubo alrededor del chunk (aunque afuera el terreno sea mas bajo, como en
        // una pendiente) y piso abajo: no se puede salir caminando ni caerse a una cueva
        int baseX = chunk.getX() * 16;
        int baseZ = chunk.getZ() * 16;
        for (int y = bottom; y <= feet - 1; y++) {
            for (int i = -1; i <= 16; i++) {
                closeGap(world.getBlockAt(baseX + i, y, baseZ - 1), changed);
                closeGap(world.getBlockAt(baseX + i, y, baseZ + 16), changed);
                closeGap(world.getBlockAt(baseX - 1, y, baseZ + i), changed);
                closeGap(world.getBlockAt(baseX + 16, y, baseZ + i), changed);
            }
        }
        if (bottom - 1 >= world.getMinHeight()) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    closeGap(chunk.getBlock(x, bottom - 1, z), changed);
                }
            }
        }
        Location center = new Location(world, chunk.getX() * 16 + 8, feet - depth / 2.0, chunk.getZ() * 16 + 8);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 2, 4, 1, 4, 0, null, true);
        world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
        world.playSound(player.getLocation(), Sound.BLOCK_ROOTED_DIRT_BREAK, 2f, 0.5f);
        return new Pit(world, chunk.getX(), chunk.getZ(), feet, bottom, changed);
    }

    /** Tapa un hueco (aire, agua, pasto...) de la pared o el piso del cubo, guardando como estaba. */
    private static void closeGap(Block block, List<BlockState> changed) {
        if (block.isPassable() || block.isLiquid()) {
            changed.add(block.getState());
            block.setType(CUBE_WALL, false);
        }
    }

    /**
     * Mientras duran las Batallas del Cubo no se tocan bloques si estas adentro del cubo, ni en el cubo o sus
     * paredes aunque estes afuera (los de creativo si pueden).
     */
    private boolean cubeLocked(Player player, Block block) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        for (Boss boss : bosses.values()) {
            if (boss.cube == null) {
                continue;
            }
            for (Pit pit : boss.pits) {
                if (pit.contains(player.getLocation()) || (block != null && pit.containsBlock(block))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void noEscape(org.bukkit.event.Cancellable event, Player player) {
        event.setCancelled(true);
        player.sendActionBar(LEGACY.deserialize("§cNo podes poner ni romper bloques en el cubo hasta ganar las Batallas del Cubo"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onCubePlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (cubeLocked(event.getPlayer(), event.getBlock())) {
            noEscape(event, event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCubeBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (cubeLocked(event.getPlayer(), event.getBlock())) {
            noEscape(event, event.getPlayer());
        }
    }

    /** Tampoco agua ni lava: con una columna de agua se sale nadando. */
    @EventHandler(ignoreCancelled = true)
    public void onCubeBucket(org.bukkit.event.player.PlayerBucketEmptyEvent event) {
        if (cubeLocked(event.getPlayer(), event.getBlock())) {
            noEscape(event, event.getPlayer());
        }
    }

    // ------------------------------------------------------------ Batallas del Cubo

    private static Component cubeTitle(DonPollosCubeBattle cube) {
        return LEGACY.deserialize("§6§lBatallas del Cubo §8| §eRonda " + cube.round() + "/" + cube.rounds());
    }

    private void startCube(Boss boss, long now) {
        DonPollosBossPhase3Settings phase3 = settings.phase3();
        // la primera ronda arranca justo cuando termina de elevarse (la pausa inicial dura hasta ahi)
        boss.cube = new DonPollosCubeBattle(phase3.rounds(), phase3.maxAlive(), phase3.roundPauseTicks(),
            Math.max(now, boss.levitateEnd) - phase3.roundPauseTicks());
        boss.cubeBar = BossBar.bossBar(cubeTitle(boss.cube), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        plugin.getLogger().info("Batallas del Cubo: " + boss.pits.size() + " cubo(s), " + phase3.rounds().size() + " rondas");
    }

    private void tickCube(Boss boss, long now) {
        DonPollosCubeBattle cube = boss.cube;
        int labubus = (int) minions.count(boss.minions, DonPollosBossMinions.Kind::isLabubu);
        int colonels = (int) minions.count(boss.minions, kind -> kind == DonPollosBossMinions.Kind.CORONEL);
        if (now % 10 == 0) {
            digNewcomers(boss);
        }
        Player player = nextCubeFighter(boss);
        DonPollosCubeBattle.Step step = player == null
            ? DonPollosCubeBattle.Step.NONE : cube.tick(now, labubus, colonels);
        if (step.spawnColonel() || step.spawnLabubu()) {
            boss.fighterTurn++;
        }
        if (step.roundStarted() > 0) {
            announceRound(boss, step.roundStarted());
        }
        if (step.spawnColonel()) {
            DonPollosCubeRound round = cube.current();
            boss.minions.add(minions.spawn(DonPollosBossMinions.Kind.CORONEL,
                DonPollosBossMinions.around(player.getLocation(), 4), player, colonel -> {
                    // cada ronda mas rapido y mas fuerte
                    DonPollosBossMinions.setAttribute(colonel, Attribute.MOVEMENT_SPEED, ZOMBIE_SPEED * round.colonelSpeed());
                    DonPollosBossMinions.setAttribute(colonel, Attribute.ATTACK_DAMAGE, ZOMBIE_DAMAGE + round.colonelDamage());
                }).getUniqueId());
            colonels++;
        }
        if (step.spawnLabubu()) {
            DonPollosBossMinions.Kind kind = DonPollosBossMinions.Kind.LABUBUS[(cube.labubuIndex() - 1) % 4];
            boss.minions.add(minions.spawn(kind, DonPollosBossMinions.around(player.getLocation(), 3), player)
                .getUniqueId());
            labubus++;
        }
        boss.cubeBar.name(cubeTitle(cube));
        boss.cubeBar.progress((float) cube.remaining(labubus, colonels));
        for (UUID viewer : boss.viewers) {
            Player p = Bukkit.getPlayer(viewer);
            if (p != null && boss.cubeViewers.add(viewer)) {
                p.showBossBar(boss.cubeBar);
            }
        }
        if (step.finished()) {
            for (UUID viewer : boss.viewers) {
                Player p = Bukkit.getPlayer(viewer);
                if (p != null) {
                    p.showTitle(net.kyori.adventure.title.Title.title(
                        LEGACY.deserialize("§a§l¡Ganaste las Batallas del Cubo!"), LEGACY.deserialize("§7Volves a la superficie"),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300),
                            java.time.Duration.ofMillis(2500), java.time.Duration.ofMillis(700))));
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                }
            }
            endCube(boss);
        }
    }

    /** Quien entra al rango de la barra durante las Batallas del Cubo tambien cae en su propio cubo. */
    private void digNewcomers(Boss boss) {
        for (UUID viewer : boss.viewers) {
            Player player = Bukkit.getPlayer(viewer);
            if (player == null || !attackable(player) || inAnyPit(boss, player.getLocation())) {
                continue;
            }
            Location at = player.getLocation();
            boolean chunkTaken = boss.pits.stream().anyMatch(pit -> pit.world() == at.getWorld()
                && pit.chunkX() == at.getBlockX() >> 4 && pit.chunkZ() == at.getBlockZ() >> 4);
            if (chunkTaken) {
                continue;  // ese chunk ya es un cubo (esta arriba del borde: se cae adentro)
            }
            Pit pit = digPit(player, settings.phase3().pitDepth());
            if (pit != null) {
                boss.pits.add(pit);
                player.showTitle(net.kyori.adventure.title.Title.title(
                    LEGACY.deserialize("§6§lBatallas del Cubo"), LEGACY.deserialize("§eNo hay escape..."),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200),
                        java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(500))));
            }
        }
    }

    /** A quien le salen los bichos: va rotando entre todos los que estan en un cubo (si no hay, el mas cercano). */
    private Player nextCubeFighter(Boss boss) {
        List<Player> fighters = new ArrayList<>();
        for (UUID viewer : boss.viewers) {
            Player player = Bukkit.getPlayer(viewer);
            if (player != null && attackable(player) && inAnyPit(boss, player.getLocation())) {
                fighters.add(player);
            }
        }
        if (fighters.isEmpty()) {
            return nearestPlayer(boss.ravager.getLocation(), BAR_RADIUS * 2);
        }
        fighters.sort(java.util.Comparator.comparing(Player::getUniqueId));
        return fighters.get((int) (boss.fighterTurn % fighters.size()));
    }

    private void announceRound(Boss boss, int round) {
        for (UUID viewer : boss.viewers) {
            Player p = Bukkit.getPlayer(viewer);
            if (p != null) {
                p.showTitle(net.kyori.adventure.title.Title.title(
                    LEGACY.deserialize("§6§lRonda " + round), LEGACY.deserialize("§eBatallas del Cubo"),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200),
                        java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(500))));
                p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.5f, 1f);
            }
        }
    }

    /** Termina las Batallas del Cubo: saca la barra, rellena cada cubo y sube a quien haya quedado adentro. */
    private void endCube(Boss boss) {
        if (boss.cubeBar != null) {
            for (UUID viewer : boss.cubeViewers) {
                Player p = Bukkit.getPlayer(viewer);
                if (p != null) {
                    p.hideBossBar(boss.cubeBar);
                }
            }
        }
        boss.cubeViewers.clear();
        boss.cube = null;
        boss.cubeBar = null;
        for (Pit pit : boss.pits) {
            List<Player> inside = pit.world().getPlayers().stream().filter(p -> pit.contains(p.getLocation())).toList();
            for (BlockState state : pit.changed()) {
                state.update(true, false);
            }
            for (Player p : inside) {
                Location up = surface(p.getLocation(), pit.feet());
                p.teleport(up);
                p.setFallDistance(0);
                p.getWorld().spawnParticle(Particle.CLOUD, up.clone().add(0, 0.5, 0), 30, 0.5, 0.5, 0.5, 0.05);
                p.getWorld().playSound(up, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
            }
        }
        boss.pits.clear();
        if (boss.hoverAt != null) {
            finishLevitation(boss);
        }
    }

    /** Primer lugar libre (2 bloques de alto) desde la altura original del piso para arriba. */
    private static Location surface(Location from, int feet) {
        Location at = from.clone();
        at.setY(feet);
        for (int i = 0; i < 64; i++) {
            if (at.getBlock().isPassable() && at.clone().add(0, 1, 0).getBlock().isPassable()) {
                return at;
            }
            at.add(0, 1, 0);
        }
        return at;
    }

    /**
     * El Ravager reescribe su velocidad base en cada tick, asi que la fase no la pisa: le suma un
     * modificador para que persiguiendo vaya justo a la velocidad que corresponde.
     */
    private void applyPhaseSpeed(Ravager ravager, DonPollosBossPhase phase) {
        AttributeInstance speed = ravager.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, "boss_phase_speed");
        speed.removeModifier(key);
        if (DonPollosBossPhase.BABY_ZOMBIE.equals(phase.ai())) {
            speed.addModifier(new org.bukkit.attribute.AttributeModifier(key,
                BABY_ZOMBIE_SPEED / RAVAGER_CHASE_SPEED - 1,
                org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                org.bukkit.inventory.EquipmentSlotGroup.ANY));
        }
    }

    /** IA de bebe zombie: va derecho al jugador mas cercano y no lo suelta. */
    private void chase(Boss boss) {
        Ravager ravager = boss.ravager;
        if (ravager.getTarget() instanceof Player current && current.isValid() && !current.isDead()
            && current.getGameMode() != GameMode.CREATIVE && current.getGameMode() != GameMode.SPECTATOR
            && current.getWorld() == ravager.getWorld()
            && current.getLocation().distanceSquared(ravager.getLocation()) <= CHASE_RANGE * CHASE_RANGE) {
            return;
        }
        ravager.setTarget(nearestPlayer(ravager.getLocation(), CHASE_RANGE));
    }

    private static boolean attackable(Player player) {
        return !player.isDead() && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
    }

    /** Jugador vivo mas cercano en supervivencia o aventura (a los de creativo y espectador no los ve). */
    private Player nearestPlayer(Location from, double range) {
        Player nearest = null;
        double best = range * range;
        for (Player player : from.getWorld().getPlayers()) {
            if (!attackable(player)) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(from);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    /**
     * FASE 2: no se acerca; camina para quedar a {@code keep-distance} bloques del jugador mas cercano,
     * moviendose de costado de a ratos para no quedarse quieto.
     */
    private void keepAway(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        Player player = nearestPlayer(ravager.getLocation(), CHASE_RANGE);
        if (player == null) {
            return;
        }
        Vector away = ravager.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 0.01) {
            away = new Vector(1, 0, 0);
        }
        away.normalize().rotateAroundY(Math.sin(now / 50.0) * 0.6);
        Location goal = player.getLocation().add(away.multiply(settings.keepDistance()));
        goal.setY(ravager.getLocation().getY());
        ravager.getPathfinder().moveTo(goal, 1.15);
    }

    private void launchMeteor(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        Player player = nearestPlayer(ravager.getLocation(), METEOR_RANGE);
        if (player == null) {
            boss.nextMeteor = now + 20;
            return;
        }
        faceTowards(ravager, player.getLocation());
        double scale = scale(ravager);
        Vector forward = player.getLocation().toVector().subtract(ravager.getLocation().toVector()).setY(0);
        if (forward.lengthSquared() > 1e-6) {
            forward.normalize();
        }
        Location from = ravager.getLocation().add(0, CAR_TOP * scale, 0).add(forward.multiply(CAR_FRONT * scale));
        Location target = player.getLocation().add(0, 0.5, 0);
        meteors.launch(ravager, from, target, settings.meteorDamage());
        boss.nextMeteor = now + Math.round(settings.meteorCooldownSeconds() * 20);
    }

    private static double scale(LivingEntity entity) {
        AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
        return scale == null ? 1.0 : scale.getValue();
    }

    /** FASE 2: gallinas WhatsApp al lado del boss. */
    private void spawnChickens(Boss boss) {
        Ravager ravager = boss.ravager;
        long alive = minions.count(boss.minions, kind -> kind == DonPollosBossMinions.Kind.GALLINA);
        long count = Math.min(settings.chickensPerWave(), settings.maxChickens() - alive);
        for (int i = 0; i < count; i++) {
            Location location = DonPollosBossMinions.around(ravager.getLocation(), 1.6 * scale(ravager));
            boss.minions.add(minions.spawn(DonPollosBossMinions.Kind.GALLINA, location,
                nearestPlayer(location, CHASE_RANGE)).getUniqueId());
        }
    }

    private void updateBar(Boss boss) {
        Ravager ravager = boss.ravager;
        double max = ravager.getAttribute(Attribute.MAX_HEALTH).getValue();
        boss.bar.progress((float) Math.max(0, Math.min(1, ravager.getHealth() / max)));
        Set<UUID> inside = new HashSet<>();
        for (Player player : ravager.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(ravager.getLocation()) <= BAR_RADIUS * BAR_RADIUS) {
                inside.add(player.getUniqueId());
                if (boss.viewers.add(player.getUniqueId())) {
                    player.showBossBar(boss.bar);
                }
            }
        }
        for (UUID viewer : new ArrayList<>(boss.viewers)) {
            if (!inside.contains(viewer)) {
                boss.viewers.remove(viewer);
                Player player = Bukkit.getPlayer(viewer);
                if (player != null) {
                    player.hideBossBar(boss.bar);
                }
            }
        }
    }

    private void startLaser(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        Player target = null;
        double best = settings.laserRange() * settings.laserRange();
        for (Player player : ravager.getWorld().getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE || player.isDead()) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(ravager.getLocation());
            if (distance < best && ravager.hasLineOfSight(player)) {
                best = distance;
                target = player;
            }
        }
        if (target == null) {
            boss.nextLaser = now + 40;
            return;
        }
        boss.target = target.getUniqueId();
        boss.chargeEnd = now + LASER_CHARGE_TICKS;
        boss.firingUntil = now + LASER_CHARGE_TICKS + LASER_FIRE_TICKS;
        ravager.setAI(false);
        binder.playOnce(ravager.getUniqueId(), "laser", () -> { });
        ravager.getWorld().playSound(ravager.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 1.6f);
    }

    private void fireLaser(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        Player target = boss.target == null ? null : Bukkit.getPlayer(boss.target);
        if (now >= boss.firingUntil || target == null || !target.isValid() || target.getWorld() != ravager.getWorld()) {
            endLaser(boss, now);
            return;
        }
        faceTowards(ravager, target.getLocation());
        if (now < boss.chargeEnd) {
            Location eyes = eyes(ravager, 0);
            ravager.getWorld().spawnParticle(Particle.DUST, eyes, 4, 0.15, 0.05, 0.15, 0,
                new Particle.DustOptions(Color.RED, 1.2f));
            return;
        }
        if ((now - boss.chargeEnd) % 6 == 0) {
            ravager.getWorld().playSound(ravager.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 1.5f, 1.8f);
        }
        Location aim = target.getLocation().add(0, 1.1, 0);
        boolean hit = false;
        for (int side : new int[]{1, -1}) {
            Location from = eyes(ravager, side);
            Vector direction = aim.toVector().subtract(from.toVector());
            double length = Math.min(settings.laserRange(), direction.length());
            direction.normalize();
            RayTraceResult wall = ravager.getWorld().rayTraceBlocks(from, direction, length,
                FluidCollisionMode.NEVER, true);
            double reach = wall == null ? length : wall.getHitPosition().distance(from.toVector());
            beam(from, direction, reach);
            RayTraceResult entity = ravager.getWorld().rayTraceEntities(from, direction, reach, 0.4,
                e -> e instanceof Player p && p.getGameMode() != GameMode.SPECTATOR);
            if (entity != null && entity.getHitEntity() instanceof Player) {
                hit = true;
            }
            if (wall != null) {
                ravager.getWorld().spawnParticle(Particle.LAVA, wall.getHitPosition().toLocation(ravager.getWorld()), 1);
            }
        }
        if (hit && (now - boss.chargeEnd) % 4 == 0) {
            blame(target, DeathCause.LASER);
            target.damage(settings.laserDamage(), ravager);
            target.setFireTicks(Math.max(target.getFireTicks(), 40));
        }
    }

    private void endLaser(Boss boss, long now) {
        boss.firingUntil = 0;
        boss.target = null;
        boss.ravager.setAI(true);
        int jitter = ThreadLocalRandom.current().nextInt(-40, 41);
        boss.nextLaser = now + settings.laserCooldownSeconds() * 20L + jitter;
    }

    private Location eyes(Ravager ravager, int side) {
        double scale = scale(ravager);
        Location base = ravager.getLocation();
        Vector forward = base.getDirection().setY(0);
        if (forward.lengthSquared() < 1e-6) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        Vector right = new Vector(-forward.getZ(), 0, forward.getX());
        return base.clone().add(0, EYE_HEIGHT * scale, 0)
            .add(forward.clone().multiply(0.15 * scale))
            .add(right.multiply(EYE_SPREAD * scale * side));
    }

    private static void faceTowards(Ravager ravager, Location target) {
        Vector to = target.toVector().subtract(ravager.getLocation().toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        ravager.setRotation(yaw, 0);
    }

    private static void beam(Location from, Vector direction, double length) {
        World world = from.getWorld();
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(255, 30, 20), 1.1f);
        for (double d = 0.3; d <= length; d += 0.35) {
            world.spawnParticle(Particle.DUST, from.clone().add(direction.clone().multiply(d)), 1, 0, 0, 0, 0, red, true);
        }
    }

    // ------------------------------------------------------------ eventos

    @EventHandler
    public void onMelee(EntityDamageByEntityEvent event) {
        if (isBoss(event.getDamager())) {
            binder.playOnce(event.getDamager().getUniqueId(), "golpe", () -> { });
            if (event.getEntity() instanceof Player player) {
                blame(player, DeathCause.RAM);
            }
        } else if (event.getEntity() instanceof Player player) {
            minions.kind(event.getDamager()).ifPresent(kind -> blame(player, switch (kind) {
                case GALLINA -> DeathCause.CHICKEN;
                case CORONEL -> DeathCause.COLONEL;
                case CORONEL_FINAL -> DeathCause.FINAL_COLONEL;
                default -> DeathCause.LABUBU;
            }));
        }
    }

    /** El boss no se lastima al caer (por ejemplo al bajar despues de levitar). */
    @EventHandler(ignoreCancelled = true)
    public void onBossFall(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL && isBoss(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ mensajes de muerte

    enum DeathCause {
        METEOR(" §7fue aplastado por un §ameteorito de WhatsApp §7del §4Don Pollo Boss"),
        LASER(" §7fue derretido por los §claser de los ojos §7del §4Don Pollo Boss"),
        RAM(" §7fue atropellado por el auto del §4Don Pollo Boss"),
        CHICKEN(" §7fue picoteado hasta la muerte por una §aGallina WhatsApp"),
        LABUBU(" §7fue mordisqueado por los §dLabubus"),
        COLONEL(" §7fue frito por §fEl Coronel"),
        FINAL_COLONEL(" §7fue frito por un §4Sanders Fase Final"),
        BEAM(" §7fue pulverizado por el §3rayo sonico §7del §4Don Pollo Boss"),
        SHOCKWAVE(" §7salio volando por la §6onda expansiva §7del §4Don Pollo Boss");

        private final String message;

        DeathCause(String message) {
            this.message = message;
        }

        String message() {
            return message;
        }
    }

    /** Cuanto despues del golpe se le sigue echando la culpa (cubre el fuego del meteorito o del laser). */
    static final long BLAME_TICKS = 100;

    private record Blame(DeathCause cause, long tick) {
    }

    private void blame(Player player, DeathCause cause) {
        lastHits.put(player.getUniqueId(), new Blame(cause, Bukkit.getCurrentTick()));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Blame blame = lastHits.remove(event.getPlayer().getUniqueId());
        if (blame == null || Bukkit.getCurrentTick() - blame.tick() > BLAME_TICKS) {
            return;
        }
        event.deathMessage(Component.text()
            .append(event.getPlayer().displayName().colorIfAbsent(net.kyori.adventure.text.format.NamedTextColor.RED))
            .append(LEGACY.deserialize(blame.cause().message()))
            .build());
    }

    /** Cuando se mantiene a distancia (FASE 2 y 3) el boss no elige objetivo, asi no embiste. */
    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() == null) {
            return;
        }
        Boss boss = bosses.get(event.getEntity().getUniqueId());
        if (boss != null && boss.phase != null && DonPollosBossPhase.KEEP_AWAY.equals(boss.phase.ai())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!isBoss(event.getEntity())) {
            return;
        }
        // ganarle no lo mata (ver onBossDamage): esto solo pasa con /kill o el vacio
        event.getDrops().clear();
        event.setDroppedExp(0);
        untrack(event.getEntity().getUniqueId());
    }

    // ------------------------------------------------------------ derrota: llora y te deja elegir el premio

    /**
     * El golpe que lo mataria no lo mata: queda con 1 de vida, se te queda mirando y llora. Mientras llora no
     * le pasa nada (salvo /kill y el vacio).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        Boss boss = bosses.get(event.getEntity().getUniqueId());
        if (boss == null) {
            return;
        }
        org.bukkit.event.entity.EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.KILL
            || cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (boss.defeat != null) {
            event.setCancelled(true);
        } else if (boss.levitateEnd > 0 || boss.hoverAt != null) {
            // arriba, adentro de la esfera, no le pasa nada
            event.setCancelled(true);
            boss.ravager.getWorld().spawnParticle(Particle.DUST, boss.ravager.getLocation().add(0, 1.55 * scale(boss.ravager), 0),
                20, 1.5, 1.5, 1.5, 0, new Particle.DustOptions(Color.fromRGB(120, 200, 255), 1.6f), true);
            boss.ravager.getWorld().playSound(boss.ravager.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.5f, 0.8f);
        } else if (event.getFinalDamage() >= boss.ravager.getHealth()) {
            event.setCancelled(true);
            startDefeat(boss, killer(event));
        }
    }

    private static Player killer(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Player player) {
                return player;
            }
            if (byEntity.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
        }
        return null;
    }

    private void startDefeat(Boss boss, Player killer) {
        Ravager ravager = boss.ravager;
        long now = Bukkit.getCurrentTick();
        Player winner = killer != null ? killer : nearestPlayer(ravager.getLocation(), BAR_RADIUS);
        // corta todo lo que estuviera haciendo
        boss.levitateEnd = 0;
        boss.firingUntil = 0;
        boss.target = null;
        boss.beamStart = 0;
        boss.beamPoint = null;
        boss.jumpEnd = 0;
        setInflate(ravager, 0);
        ravager.setGravity(true);
        ravager.setVelocity(new Vector());
        ravager.setTarget(null);
        ravager.setAI(false);
        ravager.setHealth(1);
        minions.removeAll(boss.minions);
        endCube(boss);
        boss.defeat = new Defeat(winner == null ? null : winner.getUniqueId(), now);
        ravager.getPersistentDataContainer().set(defeatedKey, PersistentDataType.STRING,
            winner == null ? "" : winner.getUniqueId().toString());
        showDefeatedBar(boss);
        ravager.getWorld().playSound(ravager.getLocation(), Sound.ENTITY_RAVAGER_HURT, 3f, 0.5f);
        plugin.getLogger().info("Don Pollo Boss derrotado por " + (winner == null ? "nadie" : winner.getName()));
    }

    private void showDefeatedBar(Boss boss) {
        boss.bar.name(LEGACY.deserialize("§b§lDON POLLO BOSS §8| §7derrotado"));
        boss.bar.color(BossBar.Color.BLUE);
    }

    /** Te mira, llora y dice las frases de a una; despues abre el menu del premio. */
    private void tickDefeat(Boss boss, long now) {
        Ravager ravager = boss.ravager;
        Defeat defeat = boss.defeat;
        Player winner = defeat.winner == null ? null : Bukkit.getPlayer(defeat.winner);
        if (winner != null && winner.getWorld() == ravager.getWorld()) {
            faceTowards(ravager, winner.getLocation());
        }
        tears(ravager, now);
        if (defeat.claimed) {
            return;
        }
        int phrase = (int) ((now - defeat.start) / PHRASE_TICKS);
        if (phrase > defeat.phrase && phrase < DEFEAT_PHRASES.size()) {
            defeat.phrase = phrase;
            say(boss, DEFEAT_PHRASES.get(phrase));
        } else if (defeat.phrase < 0 && phrase == 0) {
            defeat.phrase = 0;
            say(boss, DEFEAT_PHRASES.getFirst());
        }
        if (!defeat.menuOpened && now - defeat.start >= (long) DEFEAT_PHRASES.size() * PHRASE_TICKS) {
            defeat.menuOpened = true;
            if (winner != null && winner.getWorld() == ravager.getWorld()
                && winner.getLocation().distanceSquared(ravager.getLocation()) <= 64 * 64) {
                rewardMenu.open(winner, ravager.getUniqueId());
                winner.sendMessage(LEGACY.deserialize(
                    "§7(Si cerras el menu, hacele click derecho al Don Pollo para volver a abrirlo)"));
            }
        }
    }

    private void say(Boss boss, String phrase) {
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
            LEGACY.deserialize("§b§o" + phrase), LEGACY.deserialize("§7Don Pollo"),
            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300),
                java.time.Duration.ofMillis(2200), java.time.Duration.ofMillis(500)));
        for (UUID viewer : boss.viewers) {
            Player player = Bukkit.getPlayer(viewer);
            if (player != null) {
                player.showTitle(title);
                player.sendMessage(LEGACY.deserialize("§6Don Pollo: §b§o" + phrase));
                player.playSound(boss.ravager.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.5f, 0.5f);
            }
        }
    }

    /** Lagrimas que caen de los dos ojos. */
    private void tears(Ravager ravager, long now) {
        World world = ravager.getWorld();
        for (int side : new int[]{1, -1}) {
            Location eye = eyes(ravager, side);
            // una lagrima de a ratos, alternando los ojos
            if ((now + (side > 0 ? 0 : 5)) % 10 == 0) {
                world.spawnParticle(Particle.FALLING_WATER, eye, 1, 0.02, 0.01, 0.02, 0, null, true);
            }
        }
        if (now % 120 == 0) {
            world.playSound(ravager.getLocation(), Sound.ENTITY_PANDA_CANT_BREED, 1f, 0.5f);
        }
    }

    /** Click derecho al Don Pollo derrotado: vuelve a abrir el menu del premio. */
    @EventHandler
    public void onInteractDefeated(PlayerInteractEntityEvent event) {
        Boss boss = bosses.get(event.getRightClicked().getUniqueId());
        if (boss == null || boss.defeat == null) {
            return;
        }
        event.setCancelled(true);
        Defeat defeat = boss.defeat;
        if (!defeat.menuOpened || defeat.claimed) {
            return;
        }
        Player player = event.getPlayer();
        Player winner = defeat.winner == null ? null : Bukkit.getPlayer(defeat.winner);
        if (winner != null && !winner.equals(player)) {
            player.sendMessage(LEGACY.deserialize("§cEl premio es de §f" + winner.getName() + "§c."));
            return;
        }
        rewardMenu.open(player, boss.ravager.getUniqueId());
    }

    /** Al reclamar el premio: el Don Pollo se va llorando. Devuelve false si ya se reclamo. */
    private boolean claimReward(Player player, UUID bossId) {
        Boss boss = bosses.get(bossId);
        if (boss == null || boss.defeat == null || boss.defeat.claimed) {
            return false;
        }
        Player winner = boss.defeat.winner == null ? null : Bukkit.getPlayer(boss.defeat.winner);
        if (winner != null && !winner.equals(player)) {
            return false;
        }
        boss.defeat.claimed = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> farewell(boss, player), 20L);
        return true;
    }

    private void farewell(Boss boss, Player player) {
        Ravager ravager = boss.ravager;
        World world = ravager.getWorld();
        Location at = ravager.getLocation().add(0, 1.5, 0);
        sounds.play(ravager);
        world.spawnParticle(Particle.CLOUD, at, 60, 1.2, 1.2, 1.2, 0.05, null, true);
        world.spawnParticle(Particle.FALLING_WATER, at, 80, 1.2, 1.2, 1.2, 0, null, true);
        world.playSound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 2f, 0.6f);
        Bukkit.broadcast(LEGACY.deserialize("§6§l¡" + player.getName() + " le gano al Don Pollo Boss! §7Se fue llorando..."));
        untrack(ravager.getUniqueId());
        ravager.remove();
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isBoss(entity)) {
                track((Ravager) entity);
            }
        }
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isBoss(entity)) {
                untrack(entity.getUniqueId());
            }
        }
    }

    boolean removeNearest(Location location, double maxDistance) {
        Boss nearest = null;
        double best = maxDistance * maxDistance;
        for (Boss boss : bosses.values()) {
            if (boss.ravager.getWorld() == location.getWorld()) {
                double distance = boss.ravager.getLocation().distanceSquared(location);
                if (distance <= best) {
                    best = distance;
                    nearest = boss;
                }
            }
        }
        if (nearest == null) {
            return false;
        }
        untrack(nearest.ravager.getUniqueId());
        nearest.ravager.remove();
        return true;
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
        }
        meteors.clear();
        shockwaves.clear();
        bucket.stop();
        org.bukkit.event.HandlerList.unregisterAll(minions);
        org.bukkit.event.HandlerList.unregisterAll(rewardMenu);
        org.bukkit.event.HandlerList.unregisterAll(bucket);
        for (UUID id : new ArrayList<>(bosses.keySet())) {
            Boss boss = bosses.get(id);
            if (boss != null && boss.ravager.isValid()) {
                boss.ravager.setAI(true);
                boss.ravager.setGravity(true);
                setInflate(boss.ravager, 0);
            }
            untrack(id);
        }
    }

    private static final class Defeat {
        private final UUID winner;
        private final long start;
        private int phrase = -1;
        private boolean menuOpened;
        private boolean claimed;

        private Defeat(UUID winner, long start) {
            this.winner = winner;
            this.start = start;
        }
    }

    private static final class Boss {
        private final Ravager ravager;
        private final BossBar bar;
        private final Set<UUID> viewers = new HashSet<>();
        private DonPollosBossPhase phase;
        private long nextLaser;
        private long nextMeteor;
        private long nextChickens;
        /** Bichos invocados por este boss (gallinas, Labubus y Coronel). */
        private final Set<UUID> minions = new HashSet<>();
        private Location levitateFrom;
        private long levitateStart;
        /** Tick en que termina de levitar (0: no esta levitando). */
        private long levitateEnd;
        /** Donde flota despues de subir, hasta que se ganen las Batallas del Cubo (null: no flota). */
        private Location hoverAt;
        /** Batallas del Cubo en curso (null: no hay). */
        private DonPollosCubeBattle cube;
        private BossBar cubeBar;
        private final Set<UUID> cubeViewers = new HashSet<>();
        private final List<Pit> pits = new ArrayList<>();
        /** Turno para repartir los bichos entre los jugadores que estan en un cubo. */
        private long fighterTurn;
        // FASE FINAL
        private long nextBeam;
        private long beamStart;
        private UUID beamTarget;
        private Location beamPoint;
        private long nextJump;
        private long jumpStart;
        /** Tick en que aterriza el mega salto (0: no esta saltando). */
        private long jumpEnd;
        private Location jumpFrom;
        private Location jumpTo;
        private double jumpApex;
        private UUID jumpTarget;
        private long nextHop;
        private long nextFinalColonels;
        /** Derrotado: llora y espera que reclamen el premio (null: sigue peleando). */
        private Defeat defeat;
        private long chargeEnd;
        private long firingUntil;
        private UUID target;

        private Boss(Ravager ravager, BossBar bar) {
            this.ravager = ravager;
            this.bar = bar;
        }
    }
}
