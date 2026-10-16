package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Mob;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fase 2, los tres juntos:
 * <ol>
 *   <li><b>A las pinas:</b> te pegan como bebes zombies (rapidos, dano de zombie).</li>
 *   <li>Cada vez que les sacas el <b>10% de la vida maxima de los tres juntos</b> (15 de 150) <b>se van corriendo</b>
 *   unos bloques lejos tuyo y <b>se apilan</b> en un orden al azar: el del medio salta arriba del de abajo y el de
 *   arriba arriba del del medio. El de abajo agarra al del medio de los pies y el del medio al de arriba. Mientras se
 *   apilan los rodea una esfera de particulas grises y no reciben dano.</li>
 *   <li>El del medio tira para atras al de arriba y <b>lo lanza de cabeza</b> contra vos; despues el de abajo hace lo
 *   mismo con el del medio. Cada golpe es dano de golem de hierro y salis volando. Cuando caen vuelven a las pinas.</li>
 * </ol>
 * Con dos vivos se apilan los dos y el de abajo lanza al de arriba; con uno solo no se apila.
 * Los tiempos coinciden con las animaciones de los .bbmodel de los Mutangos.
 */
final class MutangoPhaseTwo {

    // a las pinas, como bebe zombie
    private static final double MELEE_SPEED = 0.8;
    private static final double MELEE_REACH = 1.5;
    private static final int MELEE_COOLDOWN_TICKS = 20;
    private static final double MELEE_DAMAGE = 3.0;
    private static final double MELEE_KNOCKBACK = 0.4;
    private static final double MELEE_LIFT = 0.36;
    /** Se apilan cada vez que pierden esta parte de la vida maxima de los tres juntos. */
    private static final double STACK_EVERY = 0.10;

    // irse corriendo y juntarse
    /** Se arman a esta distancia fija de donde estaba el jugador cuando arrancan a irse. */
    private static final double GATHER_DISTANCE = 20.0;
    private static final double GATHER_SPEED = 1.6;
    private static final double GATHER_ARRIVED = 1.2;
    /** Si en este tiempo ninguno se acerco a su lugar (se trabo), recien ahi se los pone en su lugar. */
    private static final int GATHER_STUCK_TICKS = 60;
    private static final int GATHER_TIMEOUT_TICKS = 300;
    private static final double SIDE = 1.6;

    // apilarse (ticks desde que empieza la pila)
    /** Alto del modelo: el de arriba se para justo en la cabeza del de abajo (25 pixeles). */
    private static final double BODY_TOP = 25.0 / 16.0;
    private static final int JUMP_TICKS = 16;
    private static final int JUMP_MOVE_START = 4;
    private static final int JUMP_MOVE_END = 14;
    private static final double JUMP_ARC = 1.2;
    private static final int JUMP_GAP = 20;
    /** Despues de que aterriza el ultimo, cuanto falta para que empiece el primer lanzamiento. */
    private static final int HOLD_TICKS = 12;
    /** Tira para atras durante la animacion "lanzar" y suelta a los 0.55 s. */
    private static final int RELEASE_AFTER_THROW = 11;
    /** Entre que suelta a uno y el de abajo empieza a tirar para atras al siguiente. */
    private static final int THROW_GAP_TICKS = 14;
    /** El ultimo lanzador termina la animacion "lanzar" (1 s) y vuelve a las pinas. */
    private static final int END_AFTER_LAST_THROW = 20;
    private static final double PULL_BACK = 0.6;
    private static final double PULL_UP = 0.2;

    // esfera de particulas mientras se apilan
    private static final int SPHERE_POINTS = 48;
    private static final int SPHERE_PER_TICK = 3;
    private static final double SPHERE_MARGIN = 1.2;
    private static final Particle.DustOptions SPHERE_DUST = new Particle.DustOptions(Color.fromRGB(150, 150, 152), 1.3f);

    // los lanzados
    /** Bloques por tick (2.6 = 52 bloques por segundo). */
    private static final double FLIGHT_SPEED = 2.6;
    /** Largo maximo de cada paso al chequear el golpe durante el vuelo. */
    private static final double FLIGHT_STEP = 0.4;
    private static final double FLIGHT_GRAVITY = 0.03;
    private static final double FALL_GRAVITY = 0.08;
    private static final int FLIGHT_MAX_TICKS = 60;
    private static final double FLIGHT_HIT_HALF = 0.75;
    private static final double THROWN_KNOCKBACK = 0.6;
    private static final double THROWN_LIFT = 0.8;

    private enum Mode { MELEE, GATHER, STACK }

    /** Un Mutango volando de cabeza hacia el jugador. */
    private static final class Flight {
        final Mob mutango;
        Location position;
        Vector velocity;
        int ticks;
        boolean hit;

        Flight(Mob mutango, Location position, Vector velocity) {
            this.mutango = mutango;
            this.position = position;
            this.velocity = velocity;
        }
    }

    private final MutangoAnimations animations;
    /** La dificultad de esta pelea (cada pelea la suya). */
    private final MutangoDifficulty difficulty;
    private Mode mode = Mode.MELEE;
    private int ticks;
    private double damageTaken;
    private double stackThreshold = Double.MAX_VALUE;
    private final Map<UUID, Integer> meleeCooldown = new HashMap<>();
    private final Set<UUID> protectedIds = new HashSet<>();
    private final Map<UUID, Flight> flights = new LinkedHashMap<>();

    // la pila: order.get(0) abajo, el ultimo arriba
    private final List<Mob> order = new ArrayList<>();
    private Location ground;
    private Vector facing;
    private final Map<UUID, Location> spots = new HashMap<>();
    /** Lo mas cerca que llego cada uno a su lugar mientras corren, y el ultimo tick en que alguno avanzo. */
    private final Map<UUID, Double> closest = new HashMap<>();
    private int lastProgress;
    /** throwStarts[level]: cuando empiezan a tirar para atras al del nivel level (el de abajo lo lanza). */
    private int[] throwStarts = new int[0];
    private int stackEnd;

    MutangoPhaseTwo(MutangoAnimations animations, MutangoDifficulty difficulty) {
        this.animations = animations;
        this.difficulty = difficulty;
    }

    boolean isProtected(UUID id) {
        return protectedIds.contains(id);
    }

    void onDamaged(UUID id, double damage) {
        if (mode == Mode.MELEE && !protectedIds.contains(id)) {
            damageTaken += damage;
        }
    }

    /** Deja a todos como al principio (al cambiar de fase o volver a spawnearlos). */
    void reset(List<Mob> all) {
        for (Mob mutango : all) {
            restore(mutango);
            for (String animation : List.of("sostener", "lanzado")) {
                animations.stop(mutango.getUniqueId(), animation);
            }
        }
        mode = Mode.MELEE;
        ticks = 0;
        damageTaken = 0;
        meleeCooldown.clear();
        protectedIds.clear();
        flights.clear();
        order.clear();
        spots.clear();
    }

    void tick(List<Mob> alive) {
        ticks++;
        stackThreshold = STACK_EVERY * maxHealthEach(alive) * MutangoModels.MODELS.size();
        for (Flight flight : new ArrayList<>(flights.values())) {
            if (flight.mutango.isValid()) {
                tickFlight(flight);
            } else {
                flights.remove(flight.mutango.getUniqueId());
            }
        }
        switch (mode) {
            case MELEE -> {
                List<Mob> grounded = alive.stream()
                    .filter(mutango -> !flights.containsKey(mutango.getUniqueId())).toList();
                grounded.forEach(this::melee);
                if (damageTaken >= stackThreshold && flights.isEmpty() && grounded.size() >= 2) {
                    startGather(grounded);
                }
            }
            case GATHER -> {
                if (!allValid()) {
                    abort();
                } else {
                    gather();
                }
            }
            case STACK -> {
                if (!allValid()) {
                    abort();
                } else {
                    stack();
                }
            }
        }
    }

    // ---------------------------------------------------------------- a las pinas

    private void melee(Mob mutango) {
        Player target = MutangoTargets.nearest(mutango);
        if (target == null) {
            return;
        }
        UUID id = mutango.getUniqueId();
        int cooldown = Math.max(0, meleeCooldown.getOrDefault(id, 0) - 1);
        meleeCooldown.put(id, cooldown);
        Location from = mutango.getLocation();
        Location to = target.getLocation();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        boolean inReach = dx * dx + dz * dz <= MELEE_REACH * MELEE_REACH && Math.abs(to.getY() - from.getY()) < 1.6;
        if (inReach && cooldown == 0) {
            MutangoTargets.face(mutango, to);
            animations.once(id, "golpe");
            MutangoDamage.hurt(target, difficulty.damage(MELEE_DAMAGE), mutango);
            Vector push = MutangoTargets.directionTo(mutango, target).multiply(MELEE_KNOCKBACK).setY(MELEE_LIFT);
            MutangoDamage.push(target, push);
            mutango.getWorld().playSound(from, Sound.BLOCK_STONE_HIT, 1.0f, 1.2f);
            meleeCooldown.put(id, difficulty.cooldownTicks(MELEE_COOLDOWN_TICKS));
        }
        if (ticks % 10 == 0) {
            mutango.getPathfinder().moveTo(target, MELEE_SPEED);
        }
    }

    // ---------------------------------------------------------------- irse corriendo y juntarse

    private void startGather(List<Mob> group) {
        Location centroid = centroid(group);
        Player player = MutangoTargets.nearest(centroid);
        Vector away = player == null ? new Vector(1, 0, 0)
            : centroid.toVector().subtract(player.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 1e-4) {
            away = new Vector(1, 0, 0);
        }
        away.normalize();
        Location base = player == null ? centroid : player.getLocation();
        ground = base.clone().add(away.clone().multiply(GATHER_DISTANCE));
        ground.setY(MutangoGround.y(ground) + 1);
        facing = away.clone().multiply(-1);
        Vector right = new Vector(-facing.getZ(), 0, facing.getX());

        order.clear();
        order.addAll(group);
        Collections.shuffle(order);
        spots.clear();
        spots.put(order.get(0).getUniqueId(), ground.clone());
        for (int i = 1; i < order.size(); i++) {
            // los que van a saltar esperan a los costados del de abajo
            Vector side = right.clone().multiply(i % 2 == 1 ? SIDE : -SIDE);
            Location spot = ground.clone().add(side);
            spot.setY(MutangoGround.y(spot) + 1);
            spots.put(order.get(i).getUniqueId(), spot);
        }
        for (Mob mutango : order) {
            protectedIds.add(mutango.getUniqueId());
            mutango.setAware(true);
        }
        mode = Mode.GATHER;
        ticks = 0;
        closest.clear();
        lastProgress = 0;
        ground.getWorld().playSound(ground, Sound.ENTITY_GOAT_SCREAMING_AMBIENT, 1.5f, 0.6f);
    }

    /** Corren hasta su lugar; solo si se traban (no avanzan durante 3 s) se los pone ahi. */
    private void gather() {
        boolean arrived = true;
        for (Mob mutango : order) {
            UUID id = mutango.getUniqueId();
            Location spot = spots.get(id);
            double distance = mutango.getLocation().distance(spot);
            if (distance > GATHER_ARRIVED) {
                arrived = false;
                if (distance < closest.getOrDefault(id, Double.MAX_VALUE) - 0.3) {
                    closest.put(id, distance);
                    lastProgress = ticks;
                }
                if (ticks % 10 == 1) {
                    mutango.getPathfinder().moveTo(spot, GATHER_SPEED);
                }
            } else {
                mutango.getPathfinder().stopPathfinding();
            }
        }
        boolean stuck = ticks - lastProgress >= GATHER_STUCK_TICKS;
        if (arrived || stuck || ticks >= GATHER_TIMEOUT_TICKS) {
            startStack();
        }
    }

    // ---------------------------------------------------------------- apilarse y lanzar

    private void startStack() {
        for (Mob mutango : order) {
            mutango.getPathfinder().stopPathfinding();
            mutango.setAware(false);
            mutango.setGravity(false);
            mutango.setCollidable(false);
            mutango.setVelocity(new Vector());
            place(mutango, spots.get(mutango.getUniqueId()));
        }
        // se lanzan de arriba para abajo: primero el de arriba (lo lanza el del medio), despues el del medio
        int top = order.size() - 1;
        throwStarts = new int[order.size()];
        int start = (top - 1) * JUMP_GAP + JUMP_MOVE_END + HOLD_TICKS;
        for (int level = top; level >= 1; level--) {
            throwStarts[level] = start;
            start += RELEASE_AFTER_THROW + THROW_GAP_TICKS;
        }
        stackEnd = throwStarts[1] + END_AFTER_LAST_THROW;
        mode = Mode.STACK;
        ticks = 0;
    }

    private void stack() {
        int t = ticks;
        int top = order.size() - 1;

        for (int level = 1; level <= top; level++) {
            Mob climber = order.get(level);
            Mob holder = order.get(level - 1);
            int jump = (level - 1) * JUMP_GAP;
            if (t == jump) {
                animations.once(climber.getUniqueId(), "saltar");
            }
            if (t == jump + JUMP_MOVE_END) {
                animations.loop(holder.getUniqueId(), "sostener");
                climber.getWorld().playSound(climber.getLocation(), Sound.BLOCK_STONE_PLACE, 1.2f, 0.7f);
            }
            if (t == throwStarts[level]) {
                // el de abajo deja de sostener, tira para atras al de arriba y lo lanza
                animations.stop(holder.getUniqueId(), "sostener");
                animations.once(holder.getUniqueId(), "lanzar");
                animations.once(climber.getUniqueId(), "tomar_impulso");
                holder.getWorld().playSound(holder.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 0.5f);
            }
        }

        for (int level = 0; level <= top; level++) {
            if (level >= 1 && t >= throwStarts[level] + RELEASE_AFTER_THROW) {
                continue; // ya lo lanzaron: lo mueve tickFlight y, cuando cae, vuelve a las pinas
            }
            place(order.get(level), positionAt(level, t));
        }
        for (int level = 1; level <= top; level++) {
            if (t == throwStarts[level] + RELEASE_AFTER_THROW) {
                launch(order.get(level));
            }
        }
        // mientras se apilan (hasta que sale volando el primero) los rodea una esfera de particulas grises
        if (t < throwStarts[top] + RELEASE_AFTER_THROW) {
            sphere(t, top);
        }
        if (t >= stackEnd) {
            Mob bottom = order.get(0);
            animations.stop(bottom.getUniqueId(), "sostener");
            restore(bottom);
            order.clear();
            spots.clear();
            mode = Mode.MELEE;
            ticks = 0;
            damageTaken = 0;
        }
    }

    /** Donde esta el del nivel level (0 = abajo) en el tick t de la pila. */
    private Location positionAt(int level, int t) {
        Location onStack = ground.clone().add(0, level * BODY_TOP, 0);
        if (level == 0) {
            return onStack;
        }
        Location spot = spots.get(order.get(level).getUniqueId());
        int jump = (level - 1) * JUMP_GAP;
        if (t < jump) {
            return spot.clone();
        }
        if (t < jump + JUMP_TICKS) {
            return arc(spot, onStack, (t - jump - JUMP_MOVE_START) / (double) (JUMP_MOVE_END - JUMP_MOVE_START));
        }
        if (t >= throwStarts[level]) {
            // lo tiran para atras hasta que lo sueltan
            double s = Math.min(1.0, (t - throwStarts[level]) / (double) (RELEASE_AFTER_THROW - 1));
            return onStack.add(facing.clone().multiply(-PULL_BACK * s)).add(0, PULL_UP * s, 0);
        }
        return onStack;
    }

    /** Unos pocos puntos de una esfera que gira, centrada en la pila y apenas mas grande que ella. */
    private void sphere(int t, int top) {
        double height = (top + 1) * BODY_TOP;
        Location center = ground.clone().add(0, height / 2, 0);
        double radius = height / 2 + SPHERE_MARGIN;
        double spin = t * 0.06;
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int k = 0; k < SPHERE_PER_TICK; k++) {
            int i = (t * SPHERE_PER_TICK + k) * 7 % SPHERE_POINTS;
            double y = 1 - 2 * (i + 0.5) / SPHERE_POINTS;
            double ring = Math.sqrt(1 - y * y);
            double angle = golden * i + spin;
            Location point = center.clone().add(Math.cos(angle) * ring * radius, y * radius,
                Math.sin(angle) * ring * radius);
            center.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, SPHERE_DUST);
        }
    }

    private void launch(Mob flyer) {
        protectedIds.remove(flyer.getUniqueId());
        animations.loop(flyer.getUniqueId(), "lanzado");
        Player target = MutangoTargets.nearest(flyer);
        Location start = flyer.getLocation();
        Vector aim;
        if (target == null) {
            aim = facing.clone().multiply(8);
        } else {
            // apunta al piso debajo del jugador: si esta volando por el golpe anterior, adonde va a caer
            Location feet = target.getLocation();
            feet.setY(Math.min(feet.getY(), MutangoGround.y(feet) + 1) + 0.1);
            aim = feet.toVector().subtract(start.toVector());
        }
        double distance = Math.max(1.0, aim.length());
        Vector velocity = aim.normalize().multiply(FLIGHT_SPEED);
        // apunta compensando la caida para llegar a la cintura del jugador
        velocity.setY(velocity.getY() + FLIGHT_GRAVITY * (distance / FLIGHT_SPEED) / 2);
        flights.put(flyer.getUniqueId(), new Flight(flyer, start, velocity));
        flyer.getWorld().playSound(start, Sound.ENTITY_GOAT_LONG_JUMP, 1.5f, 0.5f);
        flyer.getWorld().playSound(start, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.7f);
    }

    /**
     * Un tick de vuelo. Va tan rapido (mas de 2 bloques por tick) que se mueve en pasos chicos, chequeando bloques y
     * jugadores en cada uno, para no atravesarte entre un tick y el siguiente.
     */
    private void tickFlight(Flight flight) {
        flight.ticks++;
        if (flight.ticks > FLIGHT_MAX_TICKS) {
            land(flight);
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(flight.velocity.length() / FLIGHT_STEP));
        Vector step = flight.velocity.clone().multiply(1.0 / steps);
        Vector horizontal = flight.velocity.clone().setY(0);
        Vector forward = horizontal.lengthSquared() > 1e-4 ? horizontal.normalize() : new Vector();
        for (int i = 0; i < steps; i++) {
            Location next = flight.position.clone().add(step);
            if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) {
                if (!next.getBlock().isPassable()) {
                    // aterriza arriba del bloque
                    flight.position.setY(next.getBlock().getY() + 1);
                }
                land(flight);
                return;
            }
            flight.position = next;
            if (!flight.hit) {
                Vector center = next.toVector().add(forward.clone().multiply(0.3)).add(new Vector(0, 0.6, 0));
                BoundingBox body = BoundingBox.of(center, FLIGHT_HIT_HALF, FLIGHT_HIT_HALF, FLIGHT_HIT_HALF);
                Player victim = null;
                for (Player player : flight.mutango.getWorld().getPlayers()) {
                    if (MutangoTargets.targetable(player) && player.getBoundingBox().overlaps(body)) {
                        victim = player;
                        break;
                    }
                }
                if (victim != null) {
                    hit(flight, victim, forward);
                    break; // el resto del tick ya va con la velocidad del rebote
                }
            }
        }
        if (horizontal.lengthSquared() > 1e-4) {
            flight.position.setYaw(MutangoTargets.yaw(horizontal));
        }
        flight.mutango.teleport(flight.position);
        flight.velocity.setY(flight.velocity.getY() - (flight.hit ? FALL_GRAVITY : FLIGHT_GRAVITY));
    }

    /** Dano de golem de hierro (7.5 a 21.5) y salis volando. */
    private void hit(Flight flight, Player player, Vector forward) {
        flight.hit = true;
        double damage = difficulty.damage(7.5 + ThreadLocalRandom.current().nextInt(15));
        MutangoDamage.hurt(player, damage, flight.mutango);
        MutangoDamage.push(player, forward.clone().multiply(THROWN_KNOCKBACK).setY(THROWN_LIFT));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.8f);
        world.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.5f, 0.6f);
        // rebota un poco para atras (para no caerte encima) y cae
        flight.velocity.setX(flight.velocity.getX() * -0.25);
        flight.velocity.setZ(flight.velocity.getZ() * -0.25);
        flight.velocity.setY(Math.min(flight.velocity.getY(), 0.2));
    }

    private void land(Flight flight) {
        flights.remove(flight.mutango.getUniqueId());
        flight.mutango.teleport(flight.position);
        animations.stop(flight.mutango.getUniqueId(), "lanzado");
        restore(flight.mutango);
        flight.mutango.getWorld().playSound(flight.position, Sound.BLOCK_STONE_FALL, 1.5f, 0.6f);
    }

    // ---------------------------------------------------------------- ayudas

    private void place(Mob mutango, Location location) {
        Player target = MutangoTargets.nearest(ground);
        Location placed = location.clone();
        Vector look = target == null ? facing : target.getLocation().toVector().subtract(ground.toVector()).setY(0);
        placed.setYaw(look.lengthSquared() > 1e-4 ? MutangoTargets.yaw(look) : MutangoTargets.yaw(facing));
        placed.setPitch(0);
        mutango.teleport(placed);
        mutango.setBodyYaw(placed.getYaw());
    }

    private void restore(Mob mutango) {
        protectedIds.remove(mutango.getUniqueId());
        mutango.setGravity(true);
        mutango.setCollidable(true);
        mutango.setAware(true);
    }

    private void abort() {
        for (Mob mutango : order) {
            if (mutango.isValid() && !flights.containsKey(mutango.getUniqueId())) {
                animations.stop(mutango.getUniqueId(), "sostener");
                restore(mutango);
            }
        }
        order.clear();
        spots.clear();
        mode = Mode.MELEE;
        ticks = 0;
        damageTaken = 0;
    }

    private boolean allValid() {
        return order.stream().allMatch(Mob::isValid);
    }

    private static double maxHealthEach(List<Mob> alive) {
        double max = 0;
        for (Mob mutango : alive) {
            var attribute = mutango.getAttribute(Attribute.MAX_HEALTH);
            max = Math.max(max, attribute == null ? 20.0 : attribute.getValue());
        }
        return max;
    }

    private static Location centroid(List<Mob> group) {
        Vector sum = new Vector();
        for (Mob mutango : group) {
            sum.add(mutango.getLocation().toVector());
        }
        sum.multiply(1.0 / group.size());
        return sum.toLocation(group.get(0).getWorld());
    }

    private static Location arc(Location from, Location to, double s) {
        s = Math.max(0.0, Math.min(1.0, s));
        Location point = from.clone().add(to.clone().subtract(from).toVector().multiply(s));
        point.add(0, 4 * JUMP_ARC * s * (1 - s), 0);
        return point;
    }
}
