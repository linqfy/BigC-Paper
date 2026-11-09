package dev.linqfy.bigCasares.modules.customcrossbow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class SniperAimService {

    private final Map<UUID, Aim> aims = new HashMap<>();

    void begin(UUID player, UUID weapon, long tick) {
        aims.putIfAbsent(player, new Aim(weapon, tick));
    }

    int hearts(UUID player, UUID weapon, long tick, double health) {
        return hearts(aims.get(player), weapon, tick, health);
    }

    int fire(UUID player, UUID weapon, long tick, double health) {
        Aim aim = aims.remove(player);
        if (aim == null || !aim.weapon().equals(weapon)) {
            return 0;
        }
        return hearts(aim, weapon, tick, health);
    }

    boolean isLoading(UUID player) {
        return aims.containsKey(player);
    }

    void cancel(UUID player) {
        aims.remove(player);
    }

    void clear() {
        aims.clear();
    }

    private int hearts(Aim aim, UUID weapon, long tick, double health) {
        if (aim == null || !aim.weapon().equals(weapon)) {
            return 0;
        }
        return CrossbowEnchantmentRules.sniperHearts(tick - aim.startedAt(), health);
    }

    private record Aim(UUID weapon, long startedAt) { }
}
