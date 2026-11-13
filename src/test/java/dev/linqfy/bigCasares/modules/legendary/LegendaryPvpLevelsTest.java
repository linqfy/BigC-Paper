package dev.linqfy.bigCasares.modules.legendary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

class LegendaryPvpLevelsTest {
    @TempDir Path directory;

    @Test
    void repeatedlyKillingTheSamePlayerCannotFarmUnlocksOrSwitchWeaponsToBypassTheLimit() throws Exception {
        var file = directory.resolve("farm.properties");
        var levels = new LegendaryPvpLevels(new LegendaryStateStore(file));
        var killer = UUID.randomUUID();
        var victim = UUID.randomUUID();
        assertEquals(1, levels.kill(killer, victim, LegendaryWeapon.BLOODLUST));
        assertEquals(1, levels.kill(killer, victim, LegendaryWeapon.BLOODLUST));
        assertEquals(0, levels.kill(killer, victim, LegendaryWeapon.SHADOW_BLADE));
        var restored = new LegendaryPvpLevels(new LegendaryStateStore(file));
        assertEquals(1, restored.kill(killer, victim, LegendaryWeapon.BLOODLUST));
        assertEquals(2, restored.kill(killer, UUID.randomUUID(), LegendaryWeapon.BLOODLUST));
    }

    @Test
    void repeatVictimWindowExpiresAtTenMinutesAndClockRollbackDoesNotClearIt() throws Exception {
        var now = new java.util.concurrent.atomic.AtomicLong(1_000_000);
        var levels = new LegendaryPvpLevels(new LegendaryStateStore(directory.resolve("clock.properties")), now::get);
        var killer = UUID.randomUUID();
        var victim = UUID.randomUUID();
        assertEquals(1, levels.kill(killer, victim, LegendaryWeapon.BLOODLUST));
        now.set(999_000);
        assertEquals(1, levels.kill(killer, victim, LegendaryWeapon.BLOODLUST));
        now.set(1_599_999);
        assertEquals(1, levels.kill(killer, victim, LegendaryWeapon.BLOODLUST));
        now.set(1_600_000);
        assertEquals(2, levels.kill(killer, victim, LegendaryWeapon.BLOODLUST));
    }

    @Test
    void failedProgressWriteCannotCommitTheLevelOrConsumeTheVictimReward() throws Exception {
        var path = directory.resolve("failed.properties");
        var store = new LegendaryStateStore(path);
        java.nio.file.Files.createDirectory(path);
        var levels = new LegendaryPvpLevels(store);
        var killer = UUID.randomUUID();
        assertThrows(java.io.IOException.class,
                () -> levels.kill(killer, UUID.randomUUID(), LegendaryWeapon.BLOODLUST));
        assertEquals(0, levels.level(killer, LegendaryWeapon.BLOODLUST));
        assertTrue(store.snapshot().isEmpty());
    }

    @Test
    void onePlayerKillAddsOneLevelUpToFiveAndSurvivesRestartPerPlayerAndWeapon() throws Exception {
        var file = directory.resolve("state.properties");
        var store = new LegendaryStateStore(file);
        var levels = new LegendaryPvpLevels(store);
        var owner = UUID.randomUUID();
        var victim = UUID.randomUUID();
        store.put("unrelated.journal", "keep");
        assertEquals(0, levels.level(owner, LegendaryWeapon.BLOODLUST));
        for (int kill = 1; kill <= 8; kill++)
            assertEquals(Math.min(5, kill), levels.kill(owner, UUID.randomUUID(), LegendaryWeapon.BLOODLUST));
        var restored = new LegendaryPvpLevels(new LegendaryStateStore(file));
        assertEquals(5, restored.level(owner, LegendaryWeapon.BLOODLUST));
        assertEquals(0, restored.level(victim, LegendaryWeapon.BLOODLUST));
        assertEquals(0, restored.level(owner, LegendaryWeapon.SHADOW_BLADE));
        restored.kill(victim, owner, LegendaryWeapon.SHADOW_BLADE);
        assertEquals(5, restored.level(owner, LegendaryWeapon.BLOODLUST));
        assertEquals("keep", new LegendaryStateStore(file).get("unrelated.journal"));
    }

    @Test
    void selfKillsAndRetiredWeaponsCannotUnlockAnythingAndAdminLevelsAreBounded() throws Exception {
        var levels =
                new LegendaryPvpLevels(
                        new LegendaryStateStore(directory.resolve("state.properties")));
        var owner = UUID.randomUUID();
        assertEquals(0, levels.kill(owner, owner, LegendaryWeapon.BLOODLUST));
        assertEquals(0, levels.kill(owner, UUID.randomUUID(), LegendaryWeapon.FROST_SCYTHE));
        assertThrows(
                IllegalArgumentException.class,
                () -> levels.set(owner, LegendaryWeapon.BLOODLUST, 6));
        levels.set(owner, LegendaryWeapon.BLOODLUST, 3);
        assertEquals(3, levels.level(owner, LegendaryWeapon.BLOODLUST));
    }

    @Test
    void levelImprovementsStayWithinTheRequestedDamageLimits() {
        for (int level = 1; level <= 5; level++) {
            var spec = LegendaryAbilityLevels.at(level);
            assertEquals((level + 1) / 2, spec.daggers());
            assertEquals(4, spec.earthHealthDamage());
            assertTrue(spec.shadowDamage() <= 4);
            assertEquals(6 + 2 * level, spec.bloodRange());
            assertEquals(level >= 2, spec.bloodTracker());
            assertEquals(level >= 3, spec.bloodTrail());
            assertEquals(level >= 4, spec.bloodStrength());
            assertEquals(level >= 5, spec.bloodHook());
        }
        assertThrows(IllegalArgumentException.class, () -> LegendaryAbilityLevels.at(0));
    }

    @Test
    void killCreditUsesTheWeaponAtTheHitAndDoesNotLeakAcrossDeathsOrExpireLate() {
        var credits = new LegendaryKillCredits();
        var killer = UUID.randomUUID();
        var victim = UUID.randomUUID();
        credits.record(victim, killer, LegendaryWeapon.SHADOW_BLADE, 1000);
        assertEquals(
                LegendaryWeapon.SHADOW_BLADE,
                credits.consume(victim, killer, 2000).orElseThrow().weapon());
        assertTrue(credits.consume(victim, killer, 2001).isEmpty());
        credits.record(victim, killer, LegendaryWeapon.BLOODLUST, 3000);
        assertTrue(credits.consume(victim, killer, 14001).isEmpty());
        credits.record(victim, killer, LegendaryWeapon.BLOODLUST, 15000);
        assertTrue(credits.consume(victim, UUID.randomUUID(), 15001).isEmpty());
    }

    @Test
    void equipmentSwappingCannotChangeCapturedCreditOrAwardTwoWeapons() throws Exception {
        var credits = new LegendaryKillCredits();
        var killer = UUID.randomUUID();
        var victim = UUID.randomUUID();
        var levels =
                new LegendaryPvpLevels(
                        new LegendaryStateStore(directory.resolve("swap.properties")));
        LegendaryWeapon captured = LegendaryWeapon.SHADOW_BLADE;
        LegendaryWeapon heldAfterSwap = LegendaryWeapon.BLOODLUST;
        credits.record(victim, killer, captured, 1000);
        var earned = credits.consume(victim, killer, 1001).orElseThrow();
        levels.kill(earned.killer(), victim, earned.weapon());
        assertEquals(1, levels.level(killer, captured));
        assertEquals(0, levels.level(killer, heldAfterSwap));
        assertTrue(credits.consume(victim, killer, 1001).isEmpty());
        assertEquals(1, levels.level(killer, captured));
    }

    @Test
    void lastAcceptedHitWinsDeterministicallyEvenWithEqualTimestamps() {
        var credits = new LegendaryKillCredits();
        var killer = UUID.randomUUID();
        var victim = UUID.randomUUID();
        credits.record(victim, killer, LegendaryWeapon.BLOODLUST, 1000);
        credits.record(victim, killer, LegendaryWeapon.THUNDER_SPEAR, 1000);
        assertEquals(
                LegendaryWeapon.THUNDER_SPEAR,
                credits.consume(victim, killer, 1000).orElseThrow().weapon());
        assertTrue(credits.consume(victim, killer, 1000).isEmpty());
        // A non-legendary last hit cannot inherit a previous legendary credit.
        credits.record(victim, killer, LegendaryWeapon.BLOODLUST, 2000);
        credits.record(victim, killer, null, 2000);
        assertTrue(credits.consume(victim, killer, 2000).isEmpty());
    }

    @Test
    void delayedAbilityKeepsItsOriginAndNestedPostDeathEventsCannotAwardNextLife() {
        var credits = new LegendaryKillCredits();
        var killer = UUID.randomUUID();
        var victim = UUID.randomUUID();
        long previousLife = credits.generation(victim);
        credits.record(victim, killer, LegendaryWeapon.BLOODLUST, 1000, previousLife);
        // The delayed storm lands after the owner switches away from the spear.
        credits.record(victim, killer, LegendaryWeapon.THUNDER_SPEAR, 2000, previousLife);
        assertEquals(
                LegendaryWeapon.THUNDER_SPEAR,
                credits.consume(victim, killer, 2000).orElseThrow().weapon());
        // A nested outer damage callback from the dead body cannot credit its respawn.
        credits.record(victim, killer, LegendaryWeapon.SHADOW_BLADE, 2001, previousLife);
        assertTrue(credits.consume(victim, killer, 2001).isEmpty());
    }
}
