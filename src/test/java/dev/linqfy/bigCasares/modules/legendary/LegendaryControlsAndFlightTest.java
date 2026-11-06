package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.Location;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryControlsAndFlightTest {
    @TempDir Path directory;

    @Test
    void rightClickAirDoesNotMistakeVanillaBlockDenialForProtection() {
        var event = new org.bukkit.event.player.PlayerInteractEvent(null,
            org.bukkit.event.block.Action.RIGHT_CLICK_AIR, null, null,
            org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND);
        assertEquals(org.bukkit.event.Event.Result.DENY, event.useInteractedBlock());
        assertFalse(LegendaryControls.denied(event));
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        assertTrue(LegendaryControls.denied(event));
    }

    @Test
    void normalLungeAttackIsNativeEvenWhenThrowCooldownIsActive() throws Exception {
        var store = new LegendaryStateStore(directory.resolve("state.properties"));
        UUID player = UUID.randomUUID();
        assertTrue(store.beginCooldown(player, LegendaryWeapon.THUNDER_SPEAR, 0));
        assertEquals(LegendaryControls.Decision.NATIVE_ATTACK,
            LegendaryControls.decide(LegendaryWeapon.THUNDER_SPEAR, false, true, false));
        assertEquals(40_000, store.remaining(player, LegendaryWeapon.THUNDER_SPEAR, 0));
        assertEquals(LegendaryControls.Decision.NATIVE_ATTACK, LegendaryControls.decide(null, true, true, false));
    }

    @Test
    void oneRightClickActivatesEachSpecialAndOffhandOrProtectionNeverLaunches() {
        for (LegendaryWeapon weapon : LegendaryWeapon.available()) {
            assertEquals(LegendaryControls.Decision.ACTIVATE_SPECIAL, LegendaryControls.decide(weapon, true, true, false));
            assertEquals(LegendaryControls.Decision.BLOCK_SPECIAL, LegendaryControls.decide(weapon, true, false, false));
            assertEquals(LegendaryControls.Decision.BLOCK_SPECIAL, LegendaryControls.decide(weapon, true, true, true));
            assertEquals(LegendaryControls.Decision.NATIVE_ATTACK, LegendaryControls.decide(weapon, false, true, false));
        }
    }

    @Test
    void collisionStopsAtFirstHitInsideStepWithoutTunnellingOrMutatingInput() {
        Location start = new Location(null, 0, 10, 0);
        Vector velocity = new Vector(0, 0, 4);
        var step = LegendaryProjectileSweep.advance(start, velocity, (direction, distance) -> {
            assertEquals(new Vector(0, 0, 1), direction);
            assertEquals(4, distance);
            return new RayTraceResult(new Vector(0, 10, 0.75));
        });
        assertTrue(step.impact());
        assertEquals(0.75, step.destination().getZ());
        assertEquals(0, start.getZ());
        assertEquals(new Vector(0, 0, 4), velocity);
    }

    @Test
    void directCollisionRetainsTheHitEntityWhileGroundAndMissesHaveNoTarget() {
        var entity = (org.bukkit.entity.Entity) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{org.bukkit.entity.Entity.class}, (proxy, method, args) -> {
                throw new UnsupportedOperationException(method.getName());
            });
        Location start = new Location(null, 0, 10, 0);
        Vector velocity = new Vector(0, 0, 4);
        var direct = LegendaryProjectileSweep.advance(start, velocity,
            (direction, distance) -> new RayTraceResult(new Vector(0, 10, 2), entity));
        assertSame(entity, direct.hitEntity());
        assertTrue(direct.impact());
        assertNull(LegendaryProjectileSweep.advance(start, velocity,
            (direction, distance) -> new RayTraceResult(new Vector(0, 10, 2))).hitEntity());
        assertNull(LegendaryProjectileSweep.advance(start, velocity, (direction, distance) -> null).hitEntity());
    }

    @Test
    void missedStepAdvancesPreciselyAndInvalidPhysicsFailsClosed() {
        Location start = new Location(null, 2, 10, 3);
        var step = LegendaryProjectileSweep.advance(start, new Vector(1, -0.2, 2), (direction, distance) -> null);
        assertFalse(step.impact());
        assertEquals(new Vector(3, 9.8, 5), step.destination().toVector());
        assertThrows(IllegalArgumentException.class, () -> LegendaryProjectileSweep.advance(start, new Vector(), (d, r) -> null));
        assertThrows(IllegalArgumentException.class, () -> LegendaryProjectileSweep.advance(start, new Vector(Double.NaN, 0, 0), (d, r) -> null));
    }
}
