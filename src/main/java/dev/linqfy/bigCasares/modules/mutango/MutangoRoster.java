package dev.linqfy.bigCasares.modules.mutango;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Quienes forman una pelea (Lef, Lihuen y Lican), cargados o no (sin Bukkit). Que una entidad no este cargada no quiere
 * decir que murio: al descargarse el chunk sigue en la lista y la pelea no avanza de fase ni se da por terminada mientras
 * falte alguno por cargar. Solo una muerte confirmada lo saca; si despues vuelve a cargarse, se lo vuelve a enlazar pero no
 * se lo revive.
 */
final class MutangoRoster {

    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> dead = new HashSet<>();

    /** La lista viva (en el orden en que aparecieron). */
    Set<UUID> members() {
        return members;
    }

    boolean isEmpty() {
        return members.isEmpty();
    }

    /** Suma a uno (si ya se confirmo su muerte, no). */
    void add(UUID id) {
        if (!dead.contains(id)) {
            members.add(id);
        }
    }

    /** Suma a los que otro miembro tenia anotados en su ficha (los que todavia no cargaron despues de un reinicio). */
    void adopt(Collection<UUID> ids) {
        ids.forEach(this::add);
    }

    /** Murio de verdad: sale de la lista y ya no vuelve aunque aparezca una entidad vieja con su id. */
    void confirmDeath(UUID id) {
        members.remove(id);
        dead.add(id);
    }

    /** Cuantos miembros no estan cargados ahora. */
    int missing(Collection<UUID> loaded) {
        int count = 0;
        for (UUID id : members) {
            if (!loaded.contains(id)) {
                count++;
            }
        }
        return count;
    }

    /** Estan caidos todos los miembros, tambien los que no estan cargados (con uno solo que falte, la fase sigue). */
    boolean allDowned(Set<UUID> downed) {
        return !members.isEmpty() && downed.containsAll(members);
    }

    /** Para guardarlo en la ficha de cada miembro. */
    String serialize() {
        List<String> ids = new ArrayList<>();
        members.forEach(id -> ids.add(id.toString()));
        return String.join(",", ids);
    }

    /** Lo guardado en una ficha; un id que no se entiende no impide leer los demas. */
    static List<UUID> parse(String raw) {
        List<UUID> ids = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return ids;
        }
        for (String part : raw.split(",")) {
            try {
                ids.add(UUID.fromString(part.trim()));
            } catch (IllegalArgumentException invalid) {
                // se ignora
            }
        }
        return ids;
    }
}
