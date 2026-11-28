package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Los dobles de la dimension (ver {@link Doppelganger}). Corre cada tick:
 * <ul>
 *   <li>cada tick: el maniqui sigue a su gallina;</li>
 *   <li>cada segundo: los que no tienen ningun jugador a menos de {@link #DESPAWN_RADIUS} desaparecen;</li>
 *   <li>cada 10 segundos: aparecen nuevos cerca de cada jugador, hasta {@link #MAX_NEAR_PLAYER}. Cada uno copia el
 *       cuerpo de un jugador al azar de los que estan en la dimension.</li>
 * </ul>
 */
final class ChickenSpawner implements Runnable {

    private static final int SPAWN_EVERY_TICKS = 20 * 10;
    private static final int DESPAWN_CHECK_TICKS = 20;
    /** Dobles como maximo alrededor de cada jugador. */
    private static final int MAX_NEAR_PLAYER = 4;
    private static final double NEAR_RADIUS = 48.0;
    /** Si nadie esta mas cerca que esto, el doble desaparece. */
    static final double DESPAWN_RADIUS = 48.0;
    /** Tope total en la dimension, por si hay muchos jugadores. */
    private static final int MAX_IN_WORLD = 40;

    private int tick;

    @Override
    public void run() {
        tick++;
        for (Doppelganger doppel : Doppelganger.all()) {
            if (!doppel.isValid()) {
                doppel.remove(false);
                continue;
            }
            doppel.sync();
            if (tick % 10 == 0) {
                for (Player player : doppel.brain.getLocation().getNearbyPlayers(NEAR_RADIUS)) {
                    doppel.showTo(player);
                }
            }
            if (tick % DESPAWN_CHECK_TICKS == 0 && !doppel.hasPlayerWithin(DESPAWN_RADIUS)) {
                doppel.remove(false);
            }
        }
        if (tick % SPAWN_EVERY_TICKS == 0) {
            spawnAround();
        }
    }

    private void spawnAround() {
        World world = PortalListener.dimension();
        if (world == null) {
            return;
        }
        List<Player> players = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR) {
                players.add(player);
            }
        }
        if (players.isEmpty()) {
            return;
        }
        Collection<Doppelganger> existing = Doppelganger.all();
        int total = existing.size();
        for (Player player : players) {
            if (total >= MAX_IN_WORLD) {
                return;
            }
            long near = existing.stream()
                    .filter(d -> d.brain.getWorld().equals(world)
                            && d.brain.getLocation().distanceSquared(player.getLocation()) < NEAR_RADIUS * NEAR_RADIUS)
                    .count();
            int toSpawn = (int) Math.min(2, MAX_NEAR_PLAYER - near);
            for (int i = 0; i < toSpawn; i++) {
                Player copy = players.get(ThreadLocalRandom.current().nextInt(players.size()));
                Location at = spotNear(world, player.getLocation());
                if (at != null) {
                    Doppelganger.spawn(at, copy);
                    total++;
                }
            }
        }
    }

    /** Un lugar a 16-32 bloques del jugador, parado sobre suelo firme (no sobre los liquidos flotantes). */
    private Location spotNear(World world, Location center) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2);
        double distance = 16 + random.nextDouble(16);
        int x = (int) Math.floor(center.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(center.getZ() + Math.sin(angle) * distance);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        Block ground = world.getHighestBlockAt(x, z);
        if (!ground.getType().isSolid() || ground.isLiquid()
                || !ground.getRelative(BlockFace.UP).isPassable() || !ground.getRelative(BlockFace.UP, 2).isPassable()) {
            return null;
        }
        return ground.getLocation().add(0.5, 1, 0.5);
    }
}
