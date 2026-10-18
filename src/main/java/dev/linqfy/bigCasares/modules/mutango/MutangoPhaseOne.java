package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Mob;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Fase 1: los Mutangos son practicamente cabras. Cada uno por su cuenta persigue al jugador mas cercano:
 * <ul>
 *   <li>de lejos (3 a 10 bloques) frena, se inclina para atras despacio mirandolo (animacion "embestida") y a los
 *   1.2 s se tira de cabeza en linea recta hacia donde estaba;</li>
 *   <li>de cerca (menos de 3 bloques) le mete un cabezazo rapido (animacion "cabezazo").</li>
 * </ul>
 * Los dos golpes pegan fuerte y te levantan por el aire. El golpe se mide con la caja que ocupa el cuerpo en la pose
 * de cabezazo (no con un radio desde el centro), contra toda la hitbox del jugador.
 */
final class MutangoPhaseOne {

    static final String RAM_ANIMATION = "embestida";
    static final String HEADBUTT_ANIMATION = "cabezazo";

    private static final double CHASE_SPEED = 0.7;

    // embestida (de lejos)
    private static final double RAM_MIN_DISTANCE = 3.0;
    private static final double RAM_MAX_DISTANCE = 10.0;
    /** Coincide con el momento en que la animacion se tira para adelante (1.2 s). */
    private static final int RAM_WINDUP_TICKS = 24;
    private static final int RAM_CHARGE_TICKS = 10;
    private static final double RAM_CHARGE_SPEED = 0.95;
    private static final int RAM_RECOVER_TICKS = 20;
    private static final int RAM_COOLDOWN_MIN_TICKS = 40;
    private static final int RAM_COOLDOWN_MAX_TICKS = 90;
    private static final double RAM_DAMAGE = 10.0;
    private static final double RAM_KNOCKBACK = 1.1;
    private static final double RAM_LIFT = 1.0;

    // cabezazo (de cerca)
    private static final double HEADBUTT_DISTANCE = 3.0;
    /** Coincide con el golpe de la animacion "cabezazo" (0.35 s). */
    private static final int HEADBUTT_WINDUP_TICKS = 7;
    /** Durante estos ticks despues del golpe la cabeza sigue adelante y puede pegar. */
    private static final int HEADBUTT_ACTIVE_TICKS = 4;
    private static final double HEADBUTT_LUNGE = 0.45;
    private static final int HEADBUTT_RECOVER_TICKS = 12;
    private static final int HEADBUTT_COOLDOWN_TICKS = 30;
    private static final double HEADBUTT_DAMAGE = 8.0;
    private static final double HEADBUTT_KNOCKBACK = 0.8;
    private static final double HEADBUTT_LIFT = 0.85;

    /*
     * Caja que ocupa el cuerpo con la cabeza tirada para adelante (cuerpo inclinado 40 grados sobre la cadera y
     * levantado 1.8 pixeles), medida desde los pies del Mutango y en bloques: "adelante" es hacia donde mira.
     * Sale del modelo: adelante -0.34 a 1.02, costados +-0.5, altura 0.33 a 1.71. Se agranda un poco para que el
     * golpe no dependa del lag.
     */
    private static final double HIT_BACK = -0.34;
    private static final double HIT_FRONT = 1.02;
    private static final double HIT_HALF_WIDTH = 0.5;
    private static final double HIT_BOTTOM = 0.33;
    private static final double HIT_TOP = 1.71;
    private static final double HIT_MARGIN = 0.2;
    /** Radio donde se buscan jugadores para el golpe de cabeza (la caja llega a ~1.5 bloques del Mutango). */
    private static final double HEAD_BOX_SEARCH = 4.0;

    private enum State { CHASE, RAM_WINDUP, RAM_CHARGE, HEADBUTT, RECOVER }

    private static final class Ram {
        State state = State.CHASE;
        int ticks;
        int ramCooldown = ThreadLocalRandom.current().nextInt(20, 60);
        int headbuttCooldown;
        int recoverTicks;
        Vector direction;
        boolean hit;
    }

    private final Map<UUID, Ram> rams = new HashMap<>();
    /** Hace una animacion (una vez) sobre el Mutango con ese id. */
    private final BiConsumer<UUID, String> playOnce;
    /** La dificultad de esta pelea (cada pelea la suya). */
    private final MutangoDifficulty difficulty;

    MutangoPhaseOne(BiConsumer<UUID, String> playOnce, MutangoDifficulty difficulty) {
        this.playOnce = playOnce;
        this.difficulty = difficulty;
    }

    void forget(UUID id) {
        rams.remove(id);
    }

    void tick(Mob mutango) {
        Ram ram = rams.computeIfAbsent(mutango.getUniqueId(), id -> new Ram());
        ram.ticks++;
        if (ram.ramCooldown > 0) {
            ram.ramCooldown--;
        }
        if (ram.headbuttCooldown > 0) {
            ram.headbuttCooldown--;
        }
        switch (ram.state) {
            case CHASE -> chase(mutango, ram);
            case RAM_WINDUP -> ramWindup(mutango, ram);
            case RAM_CHARGE -> ramCharge(mutango, ram);
            case HEADBUTT -> headbutt(mutango, ram);
            case RECOVER -> {
                if (ram.ticks >= ram.recoverTicks) {
                    mutango.setAware(true);
                    enter(ram, State.CHASE);
                }
            }
        }
    }

    private void chase(Mob mutango, Ram ram) {
        Player target = MutangoTargets.nearest(mutango);
        if (target == null) {
            return;
        }
        double distance = target.getLocation().distance(mutango.getLocation());
        if (distance < HEADBUTT_DISTANCE && ram.headbuttCooldown <= 0) {
            startAttack(mutango, ram, target, State.HEADBUTT, HEADBUTT_ANIMATION);
            mutango.getWorld().playSound(mutango.getLocation(), Sound.ENTITY_GOAT_PREPARE_RAM, 1.0f, 1.2f);
            return;
        }
        if (distance >= RAM_MIN_DISTANCE && distance <= RAM_MAX_DISTANCE && ram.ramCooldown <= 0
            && mutango.hasLineOfSight(target)) {
            startAttack(mutango, ram, target, State.RAM_WINDUP, RAM_ANIMATION);
            mutango.getWorld().playSound(mutango.getLocation(), Sound.ENTITY_GOAT_PREPARE_RAM, 1.2f, 0.6f);
            return;
        }
        if (ram.ticks % 10 == 0) {
            mutango.getPathfinder().moveTo(target, CHASE_SPEED);
        }
    }

    private void startAttack(Mob mutango, Ram ram, Player target, State state, String animation) {
        mutango.getPathfinder().stopPathfinding();
        mutango.setAware(false);
        enter(ram, state);
        ram.hit = false;
        MutangoTargets.face(mutango, target.getLocation());
        playOnce.accept(mutango.getUniqueId(), animation);
    }

    private void ramWindup(Mob mutango, Ram ram) {
        Player target = MutangoTargets.nearest(mutango);
        if (target != null) {
            MutangoTargets.face(mutango, target.getLocation());
        }
        if (ram.ticks % 8 == 0) {
            mutango.getWorld().playSound(mutango.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.5f, 0.5f);
        }
        if (ram.ticks < RAM_WINDUP_TICKS) {
            return;
        }
        // se tira hacia donde estaba el jugador en este momento, en linea recta: se puede esquivar
        ram.direction = MutangoTargets.directionTo(mutango, target);
        enter(ram, State.RAM_CHARGE);
        mutango.getWorld().playSound(mutango.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1.2f, 0.6f);
    }

    private void ramCharge(Mob mutango, Ram ram) {
        Vector velocity = ram.direction.clone().multiply(RAM_CHARGE_SPEED);
        velocity.setY(Math.min(mutango.getVelocity().getY(), 0.1));
        mutango.setVelocity(velocity);
        MutangoTargets.face(mutango, mutango.getLocation().add(ram.direction));
        if (!ram.hit) {
            Player victim = playerInHeadBox(mutango, ram.direction);
            if (victim != null) {
                ram.hit = true;
                launch(mutango, victim, ram.direction, difficulty.damage(RAM_DAMAGE), RAM_KNOCKBACK, RAM_LIFT);
            }
        }
        if (ram.hit || ram.ticks >= RAM_CHARGE_TICKS) {
            mutango.setVelocity(new Vector(0, mutango.getVelocity().getY(), 0));
            ram.ramCooldown = difficulty.cooldownTicks(
                ThreadLocalRandom.current().nextInt(RAM_COOLDOWN_MIN_TICKS, RAM_COOLDOWN_MAX_TICKS));
            recover(ram, RAM_RECOVER_TICKS);
        }
    }

    private void headbutt(Mob mutango, Ram ram) {
        Player target = MutangoTargets.nearest(mutango);
        if (ram.ticks < HEADBUTT_WINDUP_TICKS) {
            if (target != null) {
                MutangoTargets.face(mutango, target.getLocation());
            }
            return;
        }
        if (ram.ticks == HEADBUTT_WINDUP_TICKS) {
            ram.direction = MutangoTargets.directionTo(mutango, target);
            Vector lunge = ram.direction.clone().multiply(HEADBUTT_LUNGE);
            mutango.setVelocity(lunge.setY(mutango.getVelocity().getY()));
            mutango.getWorld().playSound(mutango.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1.0f, 1.0f);
        }
        if (!ram.hit) {
            Player victim = playerInHeadBox(mutango, ram.direction);
            if (victim != null) {
                ram.hit = true;
                launch(mutango, victim, ram.direction, difficulty.damage(HEADBUTT_DAMAGE), HEADBUTT_KNOCKBACK,
                    HEADBUTT_LIFT);
            }
        }
        if (ram.hit || ram.ticks >= HEADBUTT_WINDUP_TICKS + HEADBUTT_ACTIVE_TICKS) {
            ram.headbuttCooldown = difficulty.cooldownTicks(HEADBUTT_COOLDOWN_TICKS);
            recover(ram, HEADBUTT_RECOVER_TICKS);
        }
    }

    private static void enter(Ram ram, State state) {
        ram.state = state;
        ram.ticks = 0;
    }

    private static void recover(Ram ram, int ticks) {
        ram.recoverTicks = ticks;
        enter(ram, State.RECOVER);
    }

    /** Pega, revolea para adelante y levanta por el aire. */
    private static void launch(Mob mutango, Player player, Vector direction, double damage, double knockback,
                               double lift) {
        MutangoDamage.hurt(player, damage, mutango);
        MutangoDamage.push(player, direction.clone().multiply(knockback).setY(lift));
        mutango.getWorld().playSound(mutango.getLocation(), Sound.ENTITY_GOAT_RAM_IMPACT, 1.5f, 0.7f);
        mutango.getWorld().playSound(mutango.getLocation(), Sound.BLOCK_STONE_BREAK, 1.2f, 0.6f);
    }

    /**
     * El jugador cuya hitbox toca la caja del cuerpo tirado de cabeza. La caja se arma en el sistema del Mutango
     * (adelante = direction) y la del jugador se pasa a ese sistema.
     */
    private static Player playerInHeadBox(Mob mutango, Vector direction) {
        Location origin = mutango.getLocation();
        Vector right = new Vector(-direction.getZ(), 0, direction.getX());
        for (Player player : mutango.getWorld().getNearbyPlayers(origin, HEAD_BOX_SEARCH)) {
            if (!MutangoTargets.targetable(player)) {
                continue;
            }
            BoundingBox box = player.getBoundingBox();
            double halfWidth = box.getWidthX() / 2;
            Vector offset = box.getCenter().setY(0).subtract(origin.toVector().setY(0));
            double forward = offset.dot(direction);
            double side = offset.dot(right);
            double bottom = box.getMinY() - origin.getY();
            double top = box.getMaxY() - origin.getY();
            if (forward >= HIT_BACK - halfWidth - HIT_MARGIN && forward <= HIT_FRONT + halfWidth + HIT_MARGIN
                && Math.abs(side) <= HIT_HALF_WIDTH + halfWidth + HIT_MARGIN
                && top >= HIT_BOTTOM - HIT_MARGIN && bottom <= HIT_TOP + HIT_MARGIN) {
                return player;
            }
        }
        return null;
    }
}
