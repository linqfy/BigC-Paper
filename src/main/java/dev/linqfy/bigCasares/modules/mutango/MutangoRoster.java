package dev.linqfy.bigCasares.modules.mutango;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
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

    /** Los que ya murieron (no vuelven aunque aparezca una entidad vieja con su id). */
    Set<UUID> dead() {
        return dead;
    }

    /** Retoma lo guardado de esta pelea (quienes la forman y quienes ya murieron). */
    void restore(Collection<UUID> savedMembers, Collection<UUID> savedDead) {
        dead.addAll(savedDead);
        for (UUID id : savedMembers) {
            add(id);
        }
    }

    /**
     * La fase 1 se cierra solo con los tres caidos Y los tres cargados: un caido que sigue descargado se saltearia su
     * transicion a la fase 2 (volveria con 1 de vida y en fase 1), asi que se espera a tener el roster completo.
     */
    boolean canCloseFirstPhase(Set<UUID> downed, Collection<UUID> loaded) {
        return allDowned(downed) && missing(loaded) == 0;
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

}
