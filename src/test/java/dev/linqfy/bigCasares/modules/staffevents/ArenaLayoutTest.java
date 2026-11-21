package dev.linqfy.bigCasares.modules.staffevents;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaLayoutTest {

    private final StaffEventSettings settings = StaffEventSettings.load(new YamlConfiguration());

    @Test
    void arenasDoNotOverlap() {
        ArenaLayout koth = ArenaLayout.of(StaffEventType.KOTH, settings);
        ArenaLayout tnt = ArenaLayout.of(StaffEventType.TNT_TAG, settings);
        ArenaLayout spleef = ArenaLayout.of(StaffEventType.SPLEEF, settings);
        assertTrue(tnt.centerX() - koth.centerX() > koth.clearRadius() + tnt.clearRadius());
        assertTrue(spleef.centerX() - tnt.centerX() > tnt.clearRadius() + spleef.clearRadius());
        assertThrows(IllegalArgumentException.class, () -> ArenaLayout.of(StaffEventType.LIFESTEAL, settings));
    }

    @Test
    void spleefLayersStackDownwardsAndTheVoidIsBelowTheLast() {
        ArenaLayout spleef = ArenaLayout.of(StaffEventType.SPLEEF, settings);
        assertEquals(List.of(99, 93), spleef.layerFloors());
        assertEquals(93, spleef.lowestFloor());
        assertTrue(spleef.fallOutY() < 93);
        assertTrue(spleef.clearMinY() < spleef.lowestFloor());
    }

    @Test
    void theHillIsTheRaisedGoldCircle() {
        ArenaLayout koth = ArenaLayout.of(StaffEventType.KOTH, settings);
        double x = koth.centerX() + 0.5;
        double z = koth.centerZ() + 0.5;
        assertTrue(koth.onHill(x, koth.hillTopY(), z));
        assertTrue(koth.onHill(x + 3, koth.hillTopY(), z));
        assertFalse(koth.onHill(x + 5, koth.hillTopY(), z));
        assertFalse(koth.onHill(x, koth.floorY() - 1, z));
    }

    @Test
    void discsIncludeTheirEdge() {
        assertTrue(ArenaLayout.inDisc(3, 0, 3));
        assertTrue(ArenaLayout.inDisc(2, 2, 3));
        assertFalse(ArenaLayout.inDisc(3, 3, 3));
    }
}
