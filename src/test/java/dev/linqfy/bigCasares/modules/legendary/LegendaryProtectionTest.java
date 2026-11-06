package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryProtectionTest {
    @Test
    void teamDamageHonorsPvpAndFriendlyFireWithoutAffectingUnrelatedPlayers() {
        UUID attacker = UUID.randomUUID(), ally = UUID.randomUUID(), enemy = UUID.randomUUID();
        Optional<UUID> team = Optional.of(UUID.randomUUID());
        assertFalse(LegendaryTeamRules.canDamage(attacker, ally, true, false, team, team));
        assertTrue(LegendaryTeamRules.canDamage(attacker, ally, true, true, team, team));
        assertFalse(LegendaryTeamRules.canDamage(attacker, ally, false, true, team, team));
        assertFalse(LegendaryTeamRules.canDamage(attacker, attacker, true, true, team, team));
        assertTrue(LegendaryTeamRules.canDamage(attacker, enemy, true, false, team, Optional.of(UUID.randomUUID())));
        assertTrue(LegendaryTeamRules.canDamage(attacker, enemy, true, false, Optional.empty(), Optional.empty()));
    }

    @Test
    void eachPulseSelectsNearestUnprotectedEnemiesOnceAndHonorsItsTargetLimit() {
        record Target(UUID id, double distance, boolean protectedPlayer) {}
        UUID repeated = UUID.randomUUID();
        var first = new Target(repeated, 1, false);
        var second = new Target(UUID.randomUUID(), 2, false);
        var third = new Target(UUID.randomUUID(), 3, false);
        var protectedPlayer = new Target(UUID.randomUUID(), 0.1, true);
        var selected = LegendaryTargets.select(List.of(third, protectedPlayer, second, first, first),
            Target::id, Target::distance, target -> !target.protectedPlayer(), 2);
        assertEquals(List.of(first, second), selected);
        assertThrows(IllegalArgumentException.class, () -> LegendaryTargets.select(List.of(first),
            Target::id, Target::distance, target -> true, 0));
    }
}
