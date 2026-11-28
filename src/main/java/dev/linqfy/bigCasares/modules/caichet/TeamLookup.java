package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.modules.teams.Team;
import dev.linqfy.bigCasares.modules.teams.TeamModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El team de un jugador, tomado del modulo de teams de BigCasares. Si ese modulo esta apagado o el jugador no tiene
 * team, el resultado es "sin team" y no ve pasajeros en el tren. Los nombres no se guardan en el team: salen del
 * jugador.
 */
final class TeamLookup {

    record Member(UUID id, String name) {
    }

    /** @param key identifica al team y a sus integrantes: si cambia la gente, cambia la clave */
    record Roster(String key, List<Member> members) {
    }

    /** Cuanto dura en cache el team de un jugador: los cambios de team se ven en el tren en menos de 5 segundos. */
    private static final int CACHE_TICKS = 100;

    private record Cached(Roster roster, int tick) {
    }

    /** Team de cada jugador conectado (null adentro = sin team), para no consultarlo dos veces por segundo. */
    private static final Map<UUID, Cached> CACHE = new ConcurrentHashMap<>();
    /** Nombres ya resueltos: el de un jugador desconectado sale de su archivo en disco, asi que se lee una sola vez. */
    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();

    private TeamLookup() {
    }

    /** Al desconectarse: se olvida su team en cache. */
    static void forget(UUID player) {
        CACHE.remove(player);
    }

    /** Al apagar el modulo. */
    static void clear() {
        CACHE.clear();
        NAMES.clear();
    }

    /** @return el team del jugador, o null si no tiene (o el modulo de teams esta apagado) */
    static Roster of(Player player) {
        int now = Bukkit.getCurrentTick();
        Cached cached = CACHE.get(player.getUniqueId());
        if (cached != null && now >= cached.tick() && now - cached.tick() < CACHE_TICKS) {
            return cached.roster();
        }
        Roster roster = lookup(player.getUniqueId());
        CACHE.put(player.getUniqueId(), new Cached(roster, now));
        return roster;
    }

    private static Roster lookup(UUID playerId) {
        BigCasares plugin = CaiChetRuntime.plugin();
        TeamModule module = plugin == null ? null : plugin.getTeamModule();
        if (module == null) {
            return null;
        }
        Team team = module.service().flatMap(service -> service.findByMember(playerId)).orElse(null);
        if (team == null) {
            return null;
        }
        List<Member> members = new ArrayList<>();
        for (UUID id : team.members().keySet()) {
            members.add(new Member(id, nameOf(id)));
        }
        return roster(members);
    }

    private static String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            NAMES.put(id, online.getName());
            return online.getName();
        }
        return NAMES.computeIfAbsent(id, key -> {
            String name = Bukkit.getOfflinePlayer(key).getName(); // lee el archivo del jugador: una sola vez
            return name != null ? name : key.toString().substring(0, 8);
        });
    }

    /** Orden fijo (por UUID) para que cada integrante siempre ocupe el mismo lugar del tren. */
    static Roster roster(Collection<Member> members) {
        List<Member> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparing(member -> member.id().toString()));
        StringBuilder key = new StringBuilder();
        for (Member member : sorted) {
            key.append(member.id()).append(';');
        }
        return new Roster(key.toString(), List.copyOf(sorted));
    }
}
