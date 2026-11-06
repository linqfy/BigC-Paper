package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryParalysisTest {
    private final UUID world = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID target = UUID.randomUUID();
    private final LegendaryParalysis locks = new LegendaryParalysis();

    private LegendaryParalysis.Point point(double x, double y, double z) {
        return new LegendaryParalysis.Point(world, x, y, z);
    }

    @Test
    void refreshKeepsInitialPositionUntilTargetLeavesTheField() {
        locks.sync(first, Map.of(target, point(1, 2, 3)));
        locks.sync(first, Map.of(target, point(4, 5, 6)));
        assertEquals(point(1, 2, 3), locks.anchor(target).orElseThrow());
        locks.sync(first, Map.of());
        assertTrue(locks.anchor(target).isEmpty());
        locks.sync(first, Map.of(target, point(4, 5, 6)));
        assertEquals(point(4, 5, 6), locks.anchor(target).orElseThrow());
    }

    @Test
    void overlappingFieldsKeepTheFirstAnchorUntilTheLastFieldEnds() {
        locks.acquire(first, target, point(1, 2, 3));
        locks.acquire(second, target, point(9, 8, 7));
        locks.release(first);
        assertEquals(point(1, 2, 3), locks.anchor(target).orElseThrow());
        locks.release(second);
        locks.release(second);
        assertTrue(locks.targets().isEmpty());
    }

    @Test
    void refreshRemovesOnlyItsOwnLeases() {
        UUID other = UUID.randomUUID();
        locks.sync(first, Map.of(target, point(0, 0, 0), other, point(1, 0, 0)));
        locks.acquire(second, target, point(2, 0, 0));
        locks.sync(first, Map.of());
        assertTrue(locks.anchor(target).isPresent());
        assertTrue(locks.anchor(other).isEmpty());
        locks.sync(second, Map.of());
        assertTrue(locks.targets().isEmpty());
    }

    @Test
    void deathDisconnectOrAdministrativeEscapeForgetsEveryLease() {
        locks.acquire(first, target, point(0, 0, 0));
        locks.acquire(second, target, point(1, 0, 0));
        var snapshot = locks.targets();
        locks.forget(target);
        locks.release(first);
        assertTrue(locks.targets().isEmpty());
        assertTrue(snapshot.contains(target));
        locks.acquire(second, target, point(2, 0, 0));
        locks.clear();
        assertTrue(locks.targets().isEmpty());
    }

    @Test
    void cylinderIncludesItsEdgeAndFourVerticalBlocksButNeverAnotherWorld() {
        var center = point(0, 10, 0);
        assertTrue(LegendaryParalysis.inside(center, point(7, 14, 0), 7));
        assertTrue(LegendaryParalysis.inside(center, point(0, 6, -7), 7));
        assertFalse(LegendaryParalysis.inside(center, point(7.001, 10, 0), 7));
        assertFalse(LegendaryParalysis.inside(center, point(0, 14.001, 0), 7));
        assertFalse(LegendaryParalysis.inside(center, point(5, 10, 5), 7));
        assertFalse(LegendaryParalysis.inside(center,
            new LegendaryParalysis.Point(UUID.randomUUID(), 0, 10, 0), 7));
        assertFalse(LegendaryParalysis.inside(center, center, Double.NaN));
        assertFalse(LegendaryParalysis.inside(center, center, -1));
        assertThrows(IllegalArgumentException.class, () -> point(Double.NaN, 0, 0));
    }
}
