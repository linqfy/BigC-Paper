package dev.linqfy.bigCasares.modules.quadrants;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Estado vivo de los cuadrantes: búfer de combate, última zona y última posición válida de cada jugador. */
final class QuadrantRuntime {

    static final String BYPASS_PERMISSION = "bigcasares.quadrants.bypass";

    private final QuadrantMap map;
    private final CombatTagTracker combat;
    private final Map<UUID, QuadrantZone> lastZones = new HashMap<>();
    private final Map<UUID, Location> lastOutsideChill = new HashMap<>();

    QuadrantRuntime(QuadrantMap map, CombatTagTracker combat) {
        this.map = Objects.requireNonNull(map, "map");
        this.combat = Objects.requireNonNull(combat, "combat");
    }

    Optional<QuadrantZone> zoneAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return map.zoneAt(location.getWorld().getName(), location.getX(), location.getZ());
    }

    CombatTagTracker combat() {
        return combat;
    }

    boolean isTagged(Player player) {
        return combat.isTagged(player.getUniqueId());
    }

    void tag(Player player) {
        if (combat.tag(player.getUniqueId())) {
            player.sendMessage("§c⚔ Entraste en combate: no podés entrar a la zona Chill durante "
                + combat.remainingSeconds(player.getUniqueId()) + " segundos.");
        }
    }

    boolean exempt(Player player) {
        return player.hasPermission(BYPASS_PERMISSION);
    }

    /** Registra la posición del jugador y avisa si cambió de zona. */
    void observe(Player player, Location location) {
        Optional<QuadrantZone> zone = zoneAt(location);
        if (zone.filter(QuadrantZone.CHILL::equals).isEmpty()) {
            lastOutsideChill.put(player.getUniqueId(), location.clone());
        }
        QuadrantZone current = zone.orElse(null);
        QuadrantZone previous = current == null
            ? lastZones.remove(player.getUniqueId())
            : lastZones.put(player.getUniqueId(), current);
        if (current != null && previous != current) {
            player.sendActionBar("§7Entraste a la zona " + current.label());
        }
    }

    Optional<Location> lastOutsideChill(UUID playerId) {
        return Optional.ofNullable(lastOutsideChill.get(playerId)).map(Location::clone);
    }

    void forget(UUID playerId) {
        lastZones.remove(playerId);
        lastOutsideChill.remove(playerId);
    }

    void clear() {
        lastZones.clear();
        lastOutsideChill.clear();
    }
}
