package dev.linqfy.bigCasares.modules.legendary;

import java.util.Optional;
import java.util.UUID;

public final class LegendaryTeamRules {
    private LegendaryTeamRules() {}

    public static boolean canDamage(UUID attacker, UUID target, boolean pvp, boolean friendlyFire,
                                    Optional<?> attackerTeam, Optional<?> targetTeam) {
        return !attacker.equals(target) && pvp
            && (friendlyFire || attackerTeam.isEmpty() || !attackerTeam.equals(targetTeam));
    }
}
