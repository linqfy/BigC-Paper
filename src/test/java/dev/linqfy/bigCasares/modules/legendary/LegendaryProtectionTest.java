package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;
import dev.linqfy.bigCasares.modules.teams.Team;
import dev.linqfy.bigCasares.modules.teams.TeamId;
import dev.linqfy.bigCasares.modules.teams.TeamColor;
import dev.linqfy.bigCasares.modules.teams.TeamRole;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryProtectionTest {
    @Test
    void friendlyFireUsesDomainMembershipRatherThanPresentationScoreboardColors() {
        UUID owner = UUID.randomUUID(), member = UUID.randomUUID(), enemy = UUID.randomUUID();
        var team = new Team(TeamId.random(), "Test", "ABC", TeamColor.RED, owner,
                Map.of(member, TeamRole.MEMBER), Instant.EPOCH);
        assertFalse(LegendaryCombatPolicy.teamDamageAllowed(false, Optional.of(team), member));
        assertTrue(LegendaryCombatPolicy.teamDamageAllowed(true, Optional.of(team), member));
        assertTrue(LegendaryCombatPolicy.teamDamageAllowed(false, Optional.of(team), enemy));
        assertTrue(LegendaryCombatPolicy.teamDamageAllowed(false, Optional.empty(), enemy));
    }

    @Test
    void selectionStopsProtectionChecksAfterFillingTheNearestTargetBudget() {
        var candidates = new ArrayList<Integer>();
        for (int index = 255; index >= 0; index--) candidates.add(index);
        var checks = new AtomicInteger();
        var selected = LegendaryTargets.select(candidates, index -> new UUID(0, index),
                Integer::doubleValue, index -> { checks.incrementAndGet(); return index >= 2; }, 12);
        assertEquals(List.of(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13), selected);
        assertEquals(14, checks.get(), "distant entities must not trigger more protection/ray checks");
    }

    @Test
    void duplicateEntitiesAreCheckedOnlyOnce() {
        var checks = new AtomicInteger();
        assertEquals(List.of(1, 2), LegendaryTargets.select(List.of(1, 1, 2),
                index -> new UUID(0, index), Integer::doubleValue,
                index -> { checks.incrementAndGet(); return true; }, 12));
        assertEquals(2, checks.get());
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
