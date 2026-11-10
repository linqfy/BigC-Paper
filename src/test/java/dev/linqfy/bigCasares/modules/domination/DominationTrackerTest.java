package dev.linqfy.bigCasares.modules.domination;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DominationTrackerTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    @Test
    void exposesStableModuleId() {
        assertEquals("domination-system", new DominationModule(null).getId());
    }

    @Test
    void thirdConsecutiveKillOnTheSamePlayerIsADomination() {
        DominationTracker tracker = new DominationTracker(3);

        assertFalse(tracker.recordKill(alice, bob).domination());
        assertFalse(tracker.recordKill(alice, carol).domination(), "otra víctima no corta la racha");
        assertFalse(tracker.recordKill(alice, bob).domination());
        DominationOutcome third = tracker.recordKill(alice, bob);

        assertTrue(third.domination());
        assertEquals(3, third.streak());
        assertTrue(tracker.isDominating(alice, bob));
        assertFalse(tracker.recordKill(alice, bob).domination(), "se anuncia una sola vez");
    }

    @Test
    void killingYourDominatorIsRevengeAndEndsTheDomination() {
        DominationTracker tracker = new DominationTracker(3);
        tracker.recordKill(alice, bob);
        tracker.recordKill(alice, bob);
        tracker.recordKill(alice, bob);

        DominationOutcome revenge = tracker.recordKill(bob, alice);

        assertTrue(revenge.revenge());
        assertFalse(revenge.domination());
        assertFalse(tracker.isDominating(alice, bob));
        assertEquals(0, tracker.streak(alice, bob));
    }

    @Test
    void killingBackBeforeTheDominationOnlyResetsTheStreak() {
        DominationTracker tracker = new DominationTracker(3);
        tracker.recordKill(alice, bob);
        tracker.recordKill(alice, bob);

        assertFalse(tracker.recordKill(bob, alice).revenge());
        assertEquals(0, tracker.streak(alice, bob));
        assertFalse(tracker.recordKill(alice, bob).domination());
    }

    @Test
    void someoneElseKillingTheDominatorIsNotRevenge() {
        DominationTracker tracker = new DominationTracker(3);
        tracker.recordKill(alice, bob);
        tracker.recordKill(alice, bob);
        tracker.recordKill(alice, bob);

        assertFalse(tracker.recordKill(carol, alice).revenge());
        assertTrue(tracker.isDominating(alice, bob));
    }

    @Test
    void selfKillsAndBadThresholdsAreIgnoredOrRejected() {
        DominationTracker tracker = new DominationTracker(1);
        assertEquals(new DominationOutcome(0, false, false), tracker.recordKill(alice, alice));
        assertThrows(IllegalArgumentException.class, () -> new DominationTracker(0));
    }

    @Test
    void streaksSurviveSnapshotAndRestore() {
        DominationTracker tracker = new DominationTracker(3);
        tracker.recordKill(alice, bob);
        tracker.recordKill(alice, bob);
        DominationTracker restored = new DominationTracker(3);
        tracker.snapshot().forEach((pair, streak) -> restored.restore(pair.killerId(), pair.victimId(), streak));

        assertTrue(restored.recordKill(alice, bob).domination());
    }

    @Test
    void settingsDefaultToThreeKills() {
        assertEquals(3, DominationSettings.load(new YamlConfiguration()).threshold());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("domination-system.kills-to-dominate", 0);
        assertEquals(3, DominationSettings.load(yaml).threshold());
    }
}
