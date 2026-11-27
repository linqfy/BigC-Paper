package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwoBlockPlantsTest {
    private static final ModBlockRegistry REGISTRY = ModBlockRegistry.parse("""
        curly_fern:
          default: tripwire 118 legacy 463
          upper: tripwire 119 legacy 464
        underweed:
          default: tripwire 122 legacy 467
        """);
    private static final ModBlockRegistry.Placed BOTTOM = new ModBlockRegistry.Placed("curly_fern", "default");
    private static final ModBlockRegistry.Placed TOP = new ModBlockRegistry.Placed("curly_fern", "upper");

    /** One column: height -> module block. */
    private final Map<Integer, ModBlockRegistry.Placed> column = new HashMap<>();

    private OptionalInt other(ModBlockRegistry.Placed placed, int y) {
        return TwoBlockPlants.otherHalf(REGISTRY, placed, y, h -> Optional.ofNullable(column.get(h)));
    }

    @Test
    void onlyTheCurlyFernIsTall() {
        assertTrue(TwoBlockPlants.isTall(REGISTRY, "curly_fern"));
        assertFalse(TwoBlockPlants.isTall(REGISTRY, "underweed"));
    }

    @Test
    void placementNeedsRoomForTheTop() {
        assertTrue(TwoBlockPlants.roomForTop(64, 320, true));
        assertFalse(TwoBlockPlants.roomForTop(64, 320, false), "block above taken");
        assertFalse(TwoBlockPlants.roomForTop(319, 320, true), "build limit");
    }

    @Test
    void eachHalfFindsTheOther() {
        column.put(10, BOTTOM);
        column.put(11, TOP);
        assertEquals(OptionalInt.of(11), other(BOTTOM, 10));
        assertEquals(OptionalInt.of(10), other(TOP, 11));
        assertEquals(OptionalInt.empty(), other(new ModBlockRegistry.Placed("underweed", "default"), 10));
    }

    @Test
    void aLoneHalfOrAnotherPlantIsNotTheOtherHalf() {
        column.put(10, BOTTOM);
        column.put(11, BOTTOM);
        assertEquals(OptionalInt.empty(), other(BOTTOM, 10), "a bottom above is another plant");
        column.clear();
        column.put(11, TOP);
        assertEquals(OptionalInt.empty(), other(TOP, 11));
    }

    /** As NoteBlockListener.explode: each listed block drops once and takes its other half with it. */
    @Test
    void explodingBothHalvesDropsOnce() {
        column.put(10, BOTTOM);
        column.put(11, TOP);
        int drops = 0;
        for (int y : new int[] {11, 10}) {
            ModBlockRegistry.Placed placed = column.get(y);
            if (placed == null) {
                continue;
            }
            other(placed, y).ifPresent(column::remove);
            column.remove(y);
            drops++;
        }
        assertEquals(1, drops);
        assertTrue(column.isEmpty(), "no floating half");
    }

    @Test
    void explodingOneHalfClearsTheOther() {
        column.put(10, BOTTOM);
        column.put(11, TOP);
        other(BOTTOM, 10).ifPresent(column::remove);
        column.remove(10);
        assertTrue(column.isEmpty());
    }
}
