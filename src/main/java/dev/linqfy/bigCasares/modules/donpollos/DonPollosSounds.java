package dev.linqfy.bigCasares.modules.donpollos;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sonidos de Don Pollo (assets/donpollos/sounds, en el pack de items). Suena uno al azar desde
 * la posicion del Don Pollo; mientras uno esta sonando, ese Don Pollo no arranca otro.
 */
final class DonPollosSounds {

    private record Clip(String key, int ticks) {
    }

    static final double APPROACH_RADIUS = 15.0;

    private final List<Clip> clips = new ArrayList<>();
    private final Map<UUID, Integer> busyUntil = new HashMap<>();
    private final Map<UUID, Set<UUID>> near = new HashMap<>();

    DonPollosSounds(JavaPlugin plugin) {
        try (InputStream stream = plugin.getResource("donpollos/sonidos.json")) {
            if (stream == null) {
                plugin.getLogger().warning("No hay sonidos.json: los Don Pollos van a estar mudos.");
                return;
            }
            for (JsonElement entry : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonArray()) {
                clips.add(new Clip(entry.getAsJsonObject().get("sound").getAsString(),
                    (int) Math.ceil(entry.getAsJsonObject().get("seconds").getAsDouble() * 20)));
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("No se pudieron leer los sonidos: " + exception.getMessage());
        }
    }

    /** Hace sonar a este Don Pollo (si no esta sonando ya). */
    void play(Entity pollo) {
        if (clips.isEmpty() || !pollo.isValid()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        if (now < busyUntil.getOrDefault(pollo.getUniqueId(), 0)) {
            return;
        }
        Clip clip = clips.get(ThreadLocalRandom.current().nextInt(clips.size()));
        pollo.getWorld().playSound(pollo.getLocation(), clip.key(), SoundCategory.NEUTRAL, 1.3f, 1.0f);
        busyUntil.put(pollo.getUniqueId(), now + clip.ticks());
    }

    /** Comun y Gordito: suenan cuando un jugador entra a menos de 15 bloques. */
    void checkApproach(Entity pollo) {
        Set<UUID> inside = new HashSet<>();
        boolean someoneArrived = false;
        Set<UUID> before = near.getOrDefault(pollo.getUniqueId(), Set.of());
        for (Player player : pollo.getWorld().getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR
                || player.getLocation().distanceSquared(pollo.getLocation()) > APPROACH_RADIUS * APPROACH_RADIUS) {
                continue;
            }
            inside.add(player.getUniqueId());
            if (!before.contains(player.getUniqueId())) {
                someoneArrived = true;
            }
        }
        near.put(pollo.getUniqueId(), inside);
        if (someoneArrived) {
            play(pollo);
        }
    }

    void forget(UUID pollo) {
        busyUntil.remove(pollo);
        near.remove(pollo);
    }
}
