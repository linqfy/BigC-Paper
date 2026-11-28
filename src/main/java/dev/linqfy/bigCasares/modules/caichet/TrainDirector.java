package dev.linqfy.bigCasares.modules.caichet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Cada minuto sortea un orden con todos los jugadores que estan en la dimension y manda el tren a buscar a uno
 * por uno. Se llama una vez por tick.
 */
final class TrainDirector implements Runnable {

    static final int INTERVAL_TICKS = 20 * 60;
    /** Pausa entre un tren y el siguiente. */
    private static final int GAP_TICKS = 30;

    private final Deque<UUID> queue = new ArrayDeque<>();
    private Train current;
    private int idle;
    private int gap;

    @Override
    public void run() {
        World world = PortalListener.dimension();
        if (world == null) {
            return;
        }
        if (current != null) {
            boolean alive;
            try {
                alive = current.tick();
            } catch (RuntimeException e) {
                // un tren roto se saca y sigue el siguiente: no se repite el error en cada tick
                CaiChetRuntime.logger().log(Level.WARNING, "[CaiChet] Tren: fallo y se retira", e);
                try {
                    current.remove();
                } catch (RuntimeException ignored) {
                    // ya se intento
                }
                alive = false;
            }
            if (!alive) {
                current = null;
                gap = GAP_TICKS;
            }
            return;
        }
        if (!queue.isEmpty()) {
            if (gap > 0) {
                gap--;
                return;
            }
            launchNext(world);
            return;
        }
        if (activePlayers(world).isEmpty()) {
            // nadie adentro: el minuto empieza a contar cuando entre alguien
            idle = 0;
            return;
        }
        if (idle < INTERVAL_TICKS) {
            idle++;
        }
        if (idle >= INTERVAL_TICKS) {
            startCycle(world);
            idle = 0;
        }
    }

    /** Arranca un ciclo ahora (comando /caichet tren). */
    void startNow() {
        World world = PortalListener.dimension();
        if (world != null && current == null && queue.isEmpty()) {
            startCycle(world);
        }
    }

    /** Manda un tren a este jugador desde el lado al que mira (para verlo llegar de frente). */
    void startInFront(Player player) {
        World world = PortalListener.dimension();
        if (world == null || !player.getWorld().equals(world)) {
            player.sendMessage("Esto solo funciona dentro de la dimensión.");
            return;
        }
        if (current != null) {
            current.remove();
        }
        queue.clear();
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0E-6) {
            look = new Vector(0, 0, 1);
        }
        look.normalize();
        Location at = player.getLocation();
        double sx = at.getX() + look.getX() * Train.SPAWN_DISTANCE;
        double sz = at.getZ() + look.getZ() * Train.SPAWN_DISTANCE;
        current = new Train(world, sx, sz, at.getX() - sx, at.getZ() - sz, () -> player.isOnline() ? player.getLocation() : null);
    }

    void shutdown() {
        queue.clear();
        if (current != null) {
            current.remove();
            current = null;
        }
    }

    private List<Player> activePlayers(World world) {
        List<Player> players = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR && !player.isDead()) {
                players.add(player);
            }
        }
        return players;
    }

    private void startCycle(World world) {
        List<Player> players = activePlayers(world);
        if (players.isEmpty()) {
            return;
        }
        Collections.shuffle(players, ThreadLocalRandom.current());
        queue.clear();
        StringBuilder names = new StringBuilder();
        for (Player player : players) {
            queue.add(player.getUniqueId());
            if (!names.isEmpty()) {
                names.append(" → ");
            }
            names.append(player.getName());
        }
        gap = 0;
        CaiChetRuntime.logger().info("[CaiChet] Tren: ciclo con " + names);
        Component message = Component.text("El tren sale a buscar: " + names, NamedTextColor.RED);
        for (Player player : world.getPlayers()) {
            player.sendMessage(message);
        }
    }

    private void launchNext(World world) {
        while (!queue.isEmpty()) {
            Player player = Bukkit.getPlayer(queue.poll());
            if (player == null || !player.isOnline() || !player.getWorld().equals(world)
                    || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location at = player.getLocation();
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double sx = at.getX() + Math.cos(angle) * Train.SPAWN_DISTANCE;
            double sz = at.getZ() + Math.sin(angle) * Train.SPAWN_DISTANCE;
            current = new Train(world, sx, sz, at.getX() - sx, at.getZ() - sz, () -> {
                if (!player.isOnline() || player.isDead() || !player.getWorld().equals(world)) {
                    return null;
                }
                return player.getLocation();
            });
            CaiChetRuntime.logger().info("[CaiChet] Tren: sale a buscar a " + player.getName() + " desde ("
                    + (int) sx + ", " + (int) sz + ")");
            player.sendActionBar(Component.text("¡El tren va por vos!", NamedTextColor.DARK_RED));
            player.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.5f);
            return;
        }
    }
}
