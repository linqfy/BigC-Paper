package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForlornItemRulesTest {
    @Test
    void dreadbowLoadTimes() {
        assertEquals(40, ForlornItemRules.maxLoadTime(false, 0));
        assertEquals(24, ForlornItemRules.maxLoadTime(false, 2));
        assertEquals(16, ForlornItemRules.maxLoadTime(false, 3));
        assertEquals(5, ForlornItemRules.maxLoadTime(true, 0));
    }

    @Test
    void dreadbowPowerFollowsTheBowCurve() {
        assertEquals(1.0F, ForlornItemRules.power(40, 40));
        assertEquals(1.0F, ForlornItemRules.power(100, 40));
        assertEquals((0.25F * 0.25F + 0.5F) / 3.0F, ForlornItemRules.power(10, 40), 1.0e-6F);
        assertFalse(ForlornItemRules.firesAt(ForlornItemRules.power(2, 40)));
        assertTrue(ForlornItemRules.firesAt(ForlornItemRules.power(8, 40)));
    }

    @Test
    void volleySizeAndReach() {
        assertEquals(30, ForlornItemRules.volleySize(true, 1.0F));
        assertEquals(8, ForlornItemRules.volleySize(false, 1.0F));
        assertEquals(15, ForlornItemRules.volleySize(true, 0.5F));
        assertEquals(1, ForlornItemRules.volleySize(false, 0.1F));
        assertEquals(128.0, ForlornItemRules.volleyReach(1.0F, false));
        assertEquals(21.0, ForlornItemRules.volleyReach(0.5F, true));
    }

    @Test
    void shadowArrowDamage() {
        assertEquals(3.0F, ForlornItemRules.shadowArrowDamage(false));
        assertEquals(2.0F, ForlornItemRules.shadowArrowDamage(true));
        assertEquals(6.0F, ForlornItemRules.darkArrowHit(3.0F, true));
        assertEquals(3.0F, ForlornItemRules.darkArrowHit(3.0F, false));
    }

    @Test
    void volleySpread() {
        assertEquals(0.0F, ForlornItemRules.volleySpread(true, true, 0.9F, 0.0F, 0.5F));
        assertEquals(25.0F, ForlornItemRules.volleySpread(false, true, 0.5F, 0.9F, 0.5F));
        assertEquals(10.0F, ForlornItemRules.volleySpread(false, false, 0.5F, 0.9F, 0.5F));
        // One arrow in four flies almost straight.
        assertEquals(0.3F, ForlornItemRules.volleySpread(false, true, 0.5F, 0.2F, 0.3F));
    }

    @Test
    void twilightPerfectionWindow() {
        assertEquals(4, ForlornItemRules.perfectWindow(1));
        assertEquals(7, ForlornItemRules.perfectWindow(2));
        assertEquals(10, ForlornItemRules.perfectWindow(3));
        assertFalse(ForlornItemRules.perfectShot(0, 40, 40));
        assertFalse(ForlornItemRules.perfectShot(1, 39, 40));
        assertTrue(ForlornItemRules.perfectShot(1, 40, 40));
        assertTrue(ForlornItemRules.perfectShot(1, 43, 40));
        assertFalse(ForlornItemRules.perfectShot(1, 44, 40));
        assertTrue(ForlornItemRules.perfectShot(3, 49, 40));
        assertFalse(ForlornItemRules.perfectShot(3, 50, 40));
    }

    @Test
    void relentlessBurstsEveryThirdTick() {
        assertFalse(ForlornItemRules.relentlessBurst(0));
        assertFalse(ForlornItemRules.relentlessBurst(2));
        assertTrue(ForlornItemRules.relentlessBurst(3));
        assertTrue(ForlornItemRules.relentlessBurst(9));
        assertEquals(2, ForlornItemRules.relentlessBurstSize(true, 1));
        assertEquals(1, ForlornItemRules.relentlessBurstSize(false, 1));
    }

    @Test
    void totemNumbers() {
        assertEquals(0.0F, ForlornItemRules.totemRamp(0));
        assertEquals(0.5F, ForlornItemRules.totemRamp(7) + 1.0F / 30.0F, 1.0e-6F);
        assertEquals(1.0F, ForlornItemRules.totemRamp(15));
        assertEquals(32.0F, ForlornItemRules.totemReach(1.0F));
        assertEquals(1.25F, ForlornItemRules.totemSpeed(0));
        assertEquals(2.3F, ForlornItemRules.totemSpeed(3), 1.0e-6F);
        assertTrue(ForlornItemRules.totemWears(4));
        assertFalse(ForlornItemRules.totemWears(5));
        assertEquals(3.0F, ForlornItemRules.totemExplosion(1.2));
        assertEquals(4.0F, ForlornItemRules.totemExplosion(2.0));
    }

    @Test
    void totemSightCone() {
        // Looking dead on is always inside; 5 degrees stretched by 10 % a block means a wide cone up close.
        assertTrue(ForlornItemRules.looksAt(1.0, 10.0, 5.0));
        assertTrue(ForlornItemRules.looksAt(0.9, 10.0, 5.0));
        assertFalse(ForlornItemRules.looksAt(0.0, 10.0, 5.0));
    }

    @Test
    void daggerNumbers() {
        assertEquals(1, ForlornItemRules.daggerCount(0));
        assertEquals(2, ForlornItemRules.daggerCount(1));
        assertEquals(20, ForlornItemRules.orbitTicks(0, 0));
        assertEquals(49, ForlornItemRules.orbitTicks(1, 9));
        assertEquals(2.0F, ForlornItemRules.stabDamage(0));
        assertEquals(8.0F, ForlornItemRules.stabDamage(3));
        assertEquals(4.2F, ForlornItemRules.satedSaturation(4.0F, 2), 1.0e-6F);
        assertEquals(5.0F, ForlornItemRules.satedSaturation(5.0F, 2));
        assertEquals(4.0F, ForlornItemRules.satedSaturation(4.0F, 0));
    }

    @Test
    void cloakMeter() {
        assertEquals(0.5F, ForlornItemRules.meterProgress(500));
        assertEquals(1000, ForlornItemRules.charged(995, 10));
        assertEquals(30, ForlornItemRules.charged(20, 10));
    }

    @Test
    void burrowingArrowTimes() {
        assertEquals(3, ForlornItems.hardnessTicks(0.0F));
        assertEquals(22, ForlornItems.hardnessTicks(1.5F));
        assertEquals(45, ForlornItems.hardnessTicks(3.0F));
    }

    @Test
    void burrowingArrowUsesTheModHardnessOfBlocksOnTripwireAndChorusStates() throws Exception {
        var registry = dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockRegistry.parse(
            java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/alexscaves/blocks.yml")));
        for (String id : registry.blockIds()) {
            boolean shaped = registry.variants(id).stream().map(v -> registry.state(id, v).orElseThrow().base())
                .anyMatch(base -> base != dev.linqfy.bigCasares.modules.alexscaves.core.block.BlockBase.NOTE_BLOCK);
            if (shaped) {
                assertTrue(ForlornItems.MOD_HARDNESS.containsKey(id), id + " lives on a tripwire or chorus plant state");
            }
        }
        assertEquals(2.0F, ForlornItems.MOD_HARDNESS.get("tube_worm"));
        assertEquals(30, ForlornItems.hardnessTicks(ForlornItems.MOD_HARDNESS.get("tube_worm")));
        assertEquals(15, ForlornItems.hardnessTicks(ForlornItems.MOD_HARDNESS.get("mussel")));
    }

    @Test
    void volleyAimKeepsTheSpeedWithoutSpread() {
        Vector velocity = Dreadbow.aim(new Vector(0, -4, 3), 2.0, 0.0, new Random(1));
        assertEquals(2.0, velocity.length(), 1.0e-9);
        assertEquals(-1.6, velocity.getY(), 1.0e-9);
    }

    @Test
    void volleyAimScattersWithSpread() {
        Vector straight = Dreadbow.aim(new Vector(0, -1, 0), 1.0, 0.0, new Random(3));
        Vector loose = Dreadbow.aim(new Vector(0, -1, 0), 1.0, 30.0, new Random(3));
        assertTrue(straight.angle(loose) > 0.0);
        assertTrue(Math.toDegrees(straight.angle(loose)) < 40.0);
    }
}
