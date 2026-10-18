package dev.linqfy.bigCasares.modules.mutango;

import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.ModelRotation;
import kr.toxicity.model.api.tracker.ModelScaler;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Segundo ataque de Neguechen: levanta las manos, se le sueltan y las tira en parabola a 5 bloques de cada lado tuyo.
 * Van acostadas, con la palma para abajo (el modelo de la mano suelta ya es asi), y se agrandan en el vuelo. Cuando
 * caen, cada una larga una onda expansiva por el piso: un anillo de particulas que se agranda y arranca los bloques del
 * piso por donde pasa (vuelven a su lugar a los 3 segundos). Si la onda te agarra parado en el piso te
 * levanta muy alto: para esquivarla hay que saltarla. Despues vuelven achicandose y se le pegan de nuevo.
 */
final class MutangoHandsAttack {

    static final String HAND_LEFT = MutangoPhaseThree.NEGUECHEN + "_mano_izq";
    static final String HAND_RIGHT = MutangoPhaseThree.NEGUECHEN + "_mano_der";

    /** Cuando termina "levantar_manos" (0.6 s): ahi se sueltan las manos. */
    private static final int RAISE_TICKS = 11;
    private static final int RIGHT_HAND_DELAY = 4;
    private static final int FLIGHT_TICKS = 26;
    private static final double APEX = 8.5;
    private static final double SIDE = 5.0;
    /** Del tamano de las manos de Neguechen (que esta agrandado) a bastante mas grandes cuando caen. */
    private static final float SCALE_SMALL = MutangoPhaseThree.NEGUECHEN_SCALE;
    private static final float SCALE_BIG = 3.6f;
    /** En el piso, cuanto se quedan antes de volver. */
    private static final int REST_TICKS = 30;
    private static final int RETURN_TICKS = 24;
    /** Centro de la palma levantada, en pixeles del modelo de Neguechen (+X es su izquierda, -Z su frente). */
    private static final double RAISED_X = 20;
    private static final double RAISED_Y = 58;
    private static final double RAISED_Z = -10;

    // la onda expansiva
    private static final double WAVE_SPEED = 0.45;
    /** Hasta que altura (arriba y abajo) se buscan jugadores alrededor de la onda; el limite fino es MutangoWaveRules. */
    private static final double WAVE_SEARCH_HEIGHT = 16.0;
    private static final double WAVE_MAX = 9.0;
    /** Separacion entre las particulas del anillo (antes 0.6: un tercio mas de llamadas por tick). */
    private static final double WAVE_POINT_SPACING = 0.9;
    private static final double WAVE_BAND = 0.6;
    /** Si tenes los pies a mas de esto del piso (saltando), la onda te pasa por abajo. */
    private static final double WAVE_DAMAGE = 3.0;
    private static final double WAVE_PUSH = 0.4;
    private static final double WAVE_LIFT = 1.15;
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 200, 70),
        1.5f);

    private enum State { WAITING, FLYING, LANDED, RETURNING, DONE }

    /** Un bloque del piso que arranco la onda, lo que era y cuando vuelve. */
    private record Removed(Block block, BlockData data, int restoreAt) {
    }

    /** Hasta donde busca un lugar permitido (bloques en cada eje) si donde iba a caer esta protegido. */
    private static final int PROTECTION_SEARCH = 10;
    private static final int DEBRIS_PER_TICK = 3;
    private static final int RESTORE_TICKS = 60;
    private final List<Removed> removed = new ArrayList<>();
    private int clock;

    private static final class Hand {
        final String model;
        /** 1 la izquierda de Neguechen (+X del modelo), -1 la derecha. */
        final int side;
        final int delay;
        UUID id;
        State state = State.WAITING;
        Location from;
        Location to;
        Location position;
        int ticks;
        float scale = SCALE_SMALL;
        float yaw;
        float pitch;
        Location wave;
        double radius = -1;
        final Set<UUID> launched = new HashSet<>();

        Hand(String model, int side, int delay) {
            this.model = model;
            this.side = side;
            this.delay = delay;
        }
    }

    private final MutangoPhaseThree.Host host;
    private final MutangoDifficulty difficulty;
    /** Dice si se puede tocar un bloque (zonas protegidas): ahi las ondas no arrancan nada y las manos no caen. */
    private final Predicate<Block> allowed;
    private final Hand left = new Hand(HAND_LEFT, 1, 0);
    private final Hand right = new Hand(HAND_RIGHT, -1, RIGHT_HAND_DELAY);
    private UUID neguechen;
    private int ticks = -1;

    MutangoHandsAttack(MutangoPhaseThree.Host host, MutangoDifficulty difficulty,
                       Predicate<Block> allowed) {
        this.allowed = allowed;
        this.host = host;
        this.difficulty = difficulty;
    }

    boolean active() {
        return ticks >= 0;
    }

    boolean owns(UUID id) {
        return id.equals(left.id) || id.equals(right.id);
    }

    void start(UUID neguechen, Location home) {
        stop();
        this.neguechen = neguechen;
        ticks = 0;
        for (Hand hand : List.of(left, right)) {
            hand.state = State.WAITING;
            hand.radius = -1;
            hand.launched.clear();
        }
        host.animations().once(neguechen, "levantar_manos");
        home.getWorld().playSound(home, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.5f);
    }

    /** Saca las manos sueltas y le devuelve las suyas a Neguechen. */
    void stop() {
        for (Hand hand : List.of(left, right)) {
            if (hand.id != null && Bukkit.getEntity(hand.id) instanceof Mob mob) {
                mob.remove();
            }
            hand.id = null;
            hand.state = State.DONE;
        }
        if (neguechen != null) {
            host.animations().stop(neguechen, "sin_manos");
        }
        ticks = -1;
    }

    /** Un tick. home y yaw: donde esta Neguechen y hacia donde mira. Devuelve true cuando termino el ataque. */
    boolean tick(Location home, float yaw, Player target) {
        if (ticks < 0) {
            return false;
        }
        ticks++;
        if (ticks == RAISE_TICKS) {
            host.animations().loop(neguechen, "sin_manos");
        }
        if (ticks < RAISE_TICKS) {
            // se cargan: polvo dorado y destellos juntandose en las manos levantadas
            for (int side : new int[] {1, -1}) {
                Location at = raised(home, yaw, side);
                home.getWorld().spawnParticle(Particle.DUST, at, 6, 0.7, 0.7, 0.7, 0, GOLD);
                home.getWorld().spawnParticle(Particle.END_ROD, at, 1, 0.5, 0.5, 0.5, 0.02);
            }
        }
        for (Hand hand : List.of(left, right)) {
            tickHand(hand, home, yaw, target);
        }
        if (left.state == State.DONE && right.state == State.DONE) {
            stop();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- cada mano

    private void tickHand(Hand hand, Location home, float yaw, Player target) {
        switch (hand.state) {
            case WAITING -> {
                if (ticks >= RAISE_TICKS + hand.delay) {
                    release(hand, home, yaw, target);
                }
            }
            case FLYING -> {
                hand.ticks++;
                double s = Math.min(1.0, hand.ticks / (double) FLIGHT_TICKS);
                hand.position = arc(hand.from, hand.to, s, APEX);
                hand.scale = (float) (SCALE_SMALL + (SCALE_BIG - SCALE_SMALL) * s);
                World world = hand.position.getWorld();
                world.spawnParticle(Particle.DUST, hand.position, 4, 0.5, 0.5, 0.5, 0, GOLD);
                world.spawnParticle(Particle.CLOUD, hand.position, 1, 0.3, 0.3, 0.3, 0.01);
                world.spawnParticle(Particle.END_ROD, hand.position, 1, 0.4, 0.4, 0.4, 0.02);
                if (s >= 1.0) {
                    land(hand);
                }
            }
            case LANDED -> {
                hand.ticks++;
                if (hand.ticks >= REST_TICKS) {
                    hand.state = State.RETURNING;
                    hand.ticks = 0;
                    hand.from = hand.position.clone();
                }
            }
            case RETURNING -> {
                hand.ticks++;
                double s = Math.min(1.0, hand.ticks / (double) RETURN_TICKS);
                hand.position = arc(hand.from, raised(home, yaw, hand.side), s, 4.0);
                hand.scale = (float) (SCALE_BIG + (SCALE_SMALL - SCALE_BIG) * s);
                hand.yaw = yaw;
                hand.position.getWorld().spawnParticle(Particle.END_ROD, hand.position, 2, 0.4, 0.4, 0.4, 0.02);
                if (s >= 1.0) {
                    if (Bukkit.getEntity(hand.id) instanceof Mob mob) {
                        mob.remove();
                    }
                    hand.id = null;
                    hand.state = State.DONE;
                }
            }
            case DONE -> {
            }
        }
        if (hand.radius >= 0) {
            wave(hand);
        }
        if (hand.id != null && Bukkit.getEntity(hand.id) instanceof Mob entity) {
            entity.setAware(false);
            entity.setGravity(false);
            entity.setCollidable(false);
            EntityTracker tracker = host.tracker(hand.id);
            if (tracker != null) {
                tracker.scaler(ModelScaler.value(hand.scale));
            }
            Location at = hand.position.clone();
            at.setYaw(hand.yaw);
            entity.teleport(at);
        }
    }

    /** Si donde iba a caer la mano es zona protegida, cae en el lugar permitido mas cercano. */
    private Location nearestAllowed(Location wanted) {
        World world = wanted.getWorld();
        for (int[] offset : MutangoProtection.offsetsByDistance(PROTECTION_SEARCH)) {
            Location candidate = wanted.clone().add(offset[0], 0, offset[1]);
            if (allowed.test(MutangoGround.block(candidate))) {
                return candidate;
            }
        }
        return wanted; // todo alrededor esta protegido: cae igual, pero las ondas no arrancan nada
    }

    /** Se suelta de arriba de la cabeza y sale hacia el piso, a SIDE bloques de su lado del jugador. */
    private void release(Hand hand, Location home, float yaw, Player target) {
        hand.from = raised(home, yaw, hand.side);
        Location aim = target == null ? home.clone().add(forward(yaw).multiply(10)) : target.getLocation();
        Location to = aim.clone().add(leftOf(yaw).multiply(hand.side * SIDE));
        to = nearestAllowed(to);
        to.setY(MutangoGround.y(to) + 1 + 2.0 / 16.0 * SCALE_BIG); // media palma de grosor
        hand.to = to;
        hand.position = hand.from.clone();
        hand.yaw = yaw;
        hand.pitch = 0;
        hand.scale = SCALE_SMALL;
        hand.ticks = 0;
        hand.state = State.FLYING;
        Mob entity = host.spawn(hand.model, hand.position, 0);
        hand.id = entity.getUniqueId();
        EntityTracker tracker = host.tracker(hand.id);
        if (tracker != null) {
            tracker.scaler(ModelScaler.value(hand.scale));
            tracker.rotation(() -> new ModelRotation(hand.pitch, hand.yaw));
        }
        home.getWorld().playSound(hand.from, Sound.ENTITY_WIND_CHARGE_THROW, 1.5f, 0.5f);
    }

    private void land(Hand hand) {
        hand.state = State.LANDED;
        hand.ticks = 0;
        Location ground = hand.to.clone();
        ground.setY(MutangoGround.y(ground) + 1);
        hand.wave = ground;
        hand.radius = 0;
        World world = ground.getWorld();
        world.playSound(ground, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
        world.playSound(ground, Sound.ENTITY_IRON_GOLEM_ATTACK, 2f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION, ground, 5, 1.0, 0.2, 1.0, 0);
        world.spawnParticle(Particle.CLOUD, ground, 40, 1.5, 0.2, 1.5, 0.2);
        Block top = MutangoGround.block(ground);
        world.spawnParticle(Particle.BLOCK, ground, 60, 1.5, 0.3, 1.5, 0.2, groundData(top));
        world.spawnParticle(Particle.DUST, ground.clone().add(0, 0.5, 0), 40, 1.8, 0.4, 1.8, 0, GOLD);
    }

    /** La onda: un anillo de polvo y bloques que se agranda por el piso y levanta a quien agarre parado. */
    private void wave(Hand hand) {
        double before = hand.radius;
        hand.radius += WAVE_SPEED;
        double r = hand.radius;
        World world = hand.wave.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int points = Math.max(8, (int) (2 * Math.PI * r / WAVE_POINT_SPACING));
        for (int k = 0; k < points; k++) {
            double angle = 2 * Math.PI * k / points;
            Location point = hand.wave.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
            Block top = MutangoGround.block(point);
            point.setY(top.getY() + 1.1);
            world.spawnParticle(Particle.BLOCK, point, 2, 0.15, 0.05, 0.15, 0, groundData(top));
            if (k % 2 == 0) {
                world.spawnParticle(Particle.CLOUD, point, 1, 0.05, 0.02, 0.05, 0.01);
            }
        }
        // arranca los bloques del piso por donde pasa: el bloque sale volando desde su lugar y deja el hueco; a los
        // RESTORE_TICKS vuelve a estar como antes (no se rompe el mapa)
        NamespacedKey debrisKey = host.debrisKey();
        // primero se eligen los bloques de este tick (sin tocar el mundo): se anotan todos juntos en el diario y se
        // guardan, y recien si quedaron guardados se arrancan
        Map<Block, BlockData> lifted = new LinkedHashMap<>();
        Map<Block, Double> angles = new LinkedHashMap<>();
        for (int k = 0; k < DEBRIS_PER_TICK; k++) {
            double angle = rng.nextDouble(Math.PI * 2);
            Location point = hand.wave.clone().add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
            Block top = MutangoGround.block(point);
            if (!liftable(top) || !allowed.test(top) || lifted.containsKey(top)) {
                continue;
            }
            lifted.put(top, top.getBlockData());
            angles.put(top, angle);
        }
        List<MutangoBlockJournal.Entry> batch = new ArrayList<>();
        lifted.forEach((top, data) -> batch.add(new MutangoBlockJournal.Entry(world.getUID(), top.getX(), top.getY(),
            top.getZ(), data.getAsString())));
        if (!host.journal().commit(batch)) {
            lifted.clear(); // no se pudo guardar el diario: este tick no arranca ningun bloque
        }
        for (Map.Entry<Block, BlockData> pick : lifted.entrySet()) {
            Block top = pick.getKey();
            BlockData data = pick.getValue();
            double angle = angles.get(top);
            removed.add(new Removed(top, data, clock + RESTORE_TICKS));
            top.setBlockData(Material.AIR.createBlockData(), false);
            FallingBlock debris = world.spawn(top.getLocation().add(0.5, 0.05, 0.5), FallingBlock.class,
                block -> {
                    block.setBlockData(data);
                    block.setDropItem(false);
                    block.setCancelDrop(true);
                    block.setHurtEntities(false);
                    block.getPersistentDataContainer().set(debrisKey, PersistentDataType.BYTE, (byte) 1);
                });
            Vector out = new Vector(Math.cos(angle), 0, Math.sin(angle)).multiply(0.15 + rng.nextDouble(0.15));
            debris.setVelocity(out.setY(0.5 + rng.nextDouble(0.3)));
        }
        if ((int) (r / WAVE_SPEED) % 4 == 0) {
            world.playSound(hand.wave, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.5f, 0.6f);
        }
        // te agarra si estas parado en el piso justo donde pasa el anillo
        double outer = r + WAVE_BAND;
        for (Player player : world.getNearbyPlayers(hand.wave, outer, WAVE_SEARCH_HEIGHT, outer)) {
            if (!MutangoTargets.targetable(player) || hand.launched.contains(player.getUniqueId())) {
                continue;
            }
            Location feet = player.getLocation();
            double d = Math.hypot(feet.getX() - hand.wave.getX(), feet.getZ() - hand.wave.getZ());
            if (d < before - WAVE_BAND || d > outer) {
                continue; // fuera del anillo: ni se calcula el piso
            }
            double floor = MutangoGround.y(feet) + 1;
            if (MutangoWaveRules.reaches(feet.getY() - floor)) {
                hand.launched.add(player.getUniqueId());
                Entity attacker = hand.id == null ? null : Bukkit.getEntity(hand.id);
                if (attacker == null && neguechen != null) {
                    attacker = Bukkit.getEntity(neguechen);
                }
                MutangoDamage.hurt(player, difficulty.damage(WAVE_DAMAGE), attacker);
                Vector out = feet.toVector().subtract(hand.wave.toVector()).setY(0);
                out = out.lengthSquared() > 1e-4 ? out.normalize().multiply(WAVE_PUSH) : new Vector();
                MutangoDamage.push(player, out.setY(WAVE_LIFT));
                world.playSound(feet, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.5f, 0.7f);
            }
        }
        if (r >= WAVE_MAX) {
            hand.radius = -1;
        }
    }

    /** Bloques del piso que se pueden arrancar: solidos, sin contenido (cofres, carteles...) y no irrompibles. */
    private static boolean liftable(Block top) {
        Material type = top.getType();
        return type.isSolid() && !(top.getState() instanceof TileState)
            && type.getHardness() >= 0 && type.getHardness() < 50;
    }

    /** Cada tick (aunque no este atacando): vuelve a poner los bloques arrancados que ya cumplieron su tiempo. */
    void tickRestore() {
        clock++;
        removed.removeIf(entry -> {
            if (clock < entry.restoreAt()) {
                return false;
            }
            restore(entry);
            return true;
        });
    }

    /** Vuelve a poner todos los bloques arrancados ya (al terminar la pelea o apagar el servidor). */
    void restoreAll() {
        removed.forEach(this::restore);
        removed.clear();
    }

    private void restore(Removed entry) {
        // solo si sigue el hueco (si alguien puso algo ahi, se respeta)
        if (entry.block().getType().isAir()) {
            entry.block().setBlockData(entry.data(), false);
        }
        Block block = entry.block();
        host.journal().restored(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private static BlockData groundData(Block top) {
        return top.getType().isSolid() ? top.getBlockData() : Material.DIRT.createBlockData();
    }

    /** Donde queda el centro de la palma con las manos levantadas (animacion "levantar_manos"). */
    private static Location raised(Location home, float yaw, int side) {
        double scale = MutangoPhaseThree.NEGUECHEN_SCALE / 16.0; // Neguechen esta agrandado
        return home.clone().add(leftOf(yaw).multiply(side * RAISED_X * scale))
            .add(forward(yaw).multiply(-RAISED_Z * scale))
            .add(0, RAISED_Y * scale, 0);
    }

    /** Hacia donde mira Neguechen (su frente, el -Z del modelo). */
    private static Vector forward(float yaw) {
        return MutangoDirections.forward(yaw);
    }

    /** Su izquierda (el +X del modelo): mirando de frente al modelo, queda a la izquierda de quien mira. */
    private static Vector leftOf(float yaw) {
        return MutangoDirections.leftOf(yaw);
    }

    private static Location arc(Location from, Location to, double s, double apex) {
        Location point = from.clone().add(to.clone().subtract(from).toVector().multiply(s));
        point.add(0, 4 * apex * s * (1 - s), 0);
        return point;
    }
}
