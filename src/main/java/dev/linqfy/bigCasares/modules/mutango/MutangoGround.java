package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.function.IntPredicate;

/**
 * Donde esta el piso. En el mundo normal y el End es el bloque mas alto de la columna; en el Nether (y todo mundo con
 * techo) eso devolveria el techo de bedrock, asi que se busca el piso que hay debajo de la altura de referencia (donde
 * esta parada la pelea) en vez de arriba de todo.
 */
final class MutangoGround {

    /** Cuanto sube a buscar aire si la altura de referencia cae adentro de un bloque (en un mundo con techo). */
    private static final int ESCAPE_UP = 6;

    private MutangoGround() {
    }

    /** La altura (Y) del bloque de piso de esa columna, igual que World.getHighestBlockYAt pero sirve con techo. */
    static int y(Location at) {
        World world = at.getWorld();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        if (!world.hasCeiling()) {
            return world.getHighestBlockYAt(x, z);
        }
        int top = Math.min(world.getMaxHeight() - 1, at.getBlockY());
        return floorBelow(world.getMinHeight(), top, ESCAPE_UP, y -> {
            Block block = world.getBlockAt(x, y, z);
            return !block.isPassable();
        });
    }

    /** El bloque de piso de esa columna. */
    static Block block(Location at) {
        return at.getWorld().getBlockAt(at.getBlockX(), y(at), at.getBlockZ());
    }

    /**
     * Baja desde start hasta un bloque macizo que tenga aire encima. Si start ya esta adentro de la roca primero sube
     * (hasta escapeUp bloques) hasta encontrar aire. Si no hay piso, devuelve min.
     */
    static int floorBelow(int min, int start, int escapeUp, IntPredicate solid) {
        int y = start;
        int climbed = 0;
        while (solid.test(y) && climbed < escapeUp) {
            y++;
            climbed++;
        }
        for (; y >= min; y--) {
            if (solid.test(y)) {
                return y;
            }
        }
        return min;
    }
}
