package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Quien esta electrocutado (paralizado) por una nube y hasta cuando, sin Bukkit. Despues de soltarlo queda un rato
 * inmune: varias nubes juntas no pueden encadenar la paralisis para siempre.
 */
public final class ZeusShockLedger {

    /** Paralisis de un golpe de nube: 1 s. */
    public static final int PARALYSIS_TICKS = 20;
    /** Inmunidad despues de soltarse. */
    public static final int IMMUNITY_TICKS = 30;

    private final Map<UUID, Integer> paralysedUntil = new HashMap<>();
    private final Map<UUID, Integer> immuneUntil = new HashMap<>();

    /** Electrocuta si se puede; {@code true} si la paralisis empieza ahora. */
    public boolean shock(UUID player, int tick) {
        if (paralysedUntil.containsKey(player)) {
            return false;
        }
        Integer immune = immuneUntil.get(player);
        if (immune != null && tick < immune) {
            return false;
        }
        immuneUntil.remove(player);
        paralysedUntil.put(player, tick + PARALYSIS_TICKS);
        return true;
    }

    public boolean paralysed(UUID player) {
        return paralysedUntil.containsKey(player);
    }

    /** Suelta a los que ya cumplieron; devuelve a quienes hay que devolverles el movimiento. */
    public List<UUID> release(int tick) {
        List<UUID> released = new ArrayList<>();
        paralysedUntil.entrySet().removeIf(entry -> {
            if (tick >= entry.getValue()) {
                released.add(entry.getKey());
                immuneUntil.put(entry.getKey(), tick + IMMUNITY_TICKS);
                return true;
            }
            return false;
        });
        return released;
    }

    /**
     * Suelta ya (con la inmunidad de siempre) a los paralizados que no estan en {@code inRoom}: el que salio de la sala
     * ({@code /spawn}, {@code /home}, murio y reaparecio, cambio de mundo) no puede quedar anclado a la baldosa.
     */
    public List<UUID> releaseAbsent(Collection<UUID> inRoom, int tick) {
        List<UUID> released = new ArrayList<>();
        paralysedUntil.keySet().removeIf(player -> {
            if (inRoom.contains(player)) {
                return false;
            }
            released.add(player);
            immuneUntil.put(player, tick + IMMUNITY_TICKS);
            return true;
        });
        return released;
    }

    /** Suelta a todos (fin del combate); devuelve a quienes estaban paralizados. */
    public Set<UUID> releaseAll() {
        Set<UUID> all = Set.copyOf(paralysedUntil.keySet());
        paralysedUntil.clear();
        immuneUntil.clear();
        return all;
    }
}
