package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** A quien atacan los Mutangos y hacia donde miran (lo comparten todas las fases). */
final class MutangoTargets {

    static final double TARGET_RADIUS = 32.0;

    private MutangoTargets() {
    }

    static Player nearest(LivingEntity mutango) {
        return nearest(mutango.getLocation());
    }

    static Player nearest(Location location) {
        Player nearest = null;
        double best = TARGET_RADIUS * TARGET_RADIUS;
        // solo los jugadores cerca (el servidor los busca por chunks): no se recorre todo el mundo por cada Mutango
        for (Player player : location.getWorld().getNearbyPlayers(location, TARGET_RADIUS)) {
            if (!targetable(player)) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(location);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    /** Los jugadores del mundo de location a menos de radius bloques. */
    static List<Player> near(Location location, double radius) {
        List<Player> out = new ArrayList<>();
        for (Player player : location.getWorld().getNearbyPlayers(location, radius)) {
            if (player.getLocation().distanceSquared(location) <= radius * radius) {
                out.add(player);
            }
        }
        return out;
    }

    /** Solo pelean contra jugadores en supervivencia o aventura (en creativo o espectador no tiene sentido). */
    static boolean targetable(Player player) {
        GameMode mode = player.getGameMode();
        return (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !player.isDead();
    }

    /** Gira el cuerpo (no solo la cabeza) para mirar hacia target, sin inclinarse. */
    static void face(LivingEntity mutango, Location target) {
        Location from = mutango.getLocation();
        Vector look = target.toVector().subtract(from.toVector()).setY(0);
        if (look.lengthSquared() < 1e-4) {
            return;
        }
        from.setDirection(look);
        mutango.setRotation(from.getYaw(), 0);
        mutango.setBodyYaw(from.getYaw());
    }

    /** Direccion horizontal (normalizada) hacia el jugador, o hacia donde mira si no hay jugador. */
    static Vector directionTo(LivingEntity mutango, Player target) {
        Vector direction = target == null
            ? mutango.getLocation().getDirection()
            : target.getLocation().toVector().subtract(mutango.getLocation().toVector());
        direction.setY(0);
        if (direction.lengthSquared() < 1e-4) {
            direction = mutango.getLocation().getDirection().setY(0);
        }
        if (direction.lengthSquared() < 1e-4) {
            direction = new Vector(0, 0, 1);
        }
        return direction.normalize();
    }

    static float yaw(Vector direction) {
        return MutangoDirections.yaw(direction);
    }
}
