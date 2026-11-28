package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaBuildPlanTest {
    @Test
    void kothKeepsItsFloorHillAndClearedSpace() {
        ArenaLayout layout = new ArenaLayout(ToffyEventType.KOTH, 0, 100, 0, 8, 1, 1, 2);
        ArenaBuildPlan plan = new ArenaBuildPlan(layout);
        assertEquals(Material.SMOOTH_STONE, plan.material(0, 99, 0));
        assertEquals(Material.GOLD_BLOCK, plan.material(0, 100, 0));
        assertEquals(Material.STONE_BRICKS, plan.material(3, 100, 0));
        assertEquals(Material.AIR, plan.material(0, 101, 0));
    }

    @Test
    void spleefBarrierCanExtendAboveTheClearBox() {
        ArenaLayout layout = new ArenaLayout(ToffyEventType.SPLEEF, 0, 100, 0, 6, 2, 20, 0);
        ArenaBuildPlan plan = new ArenaBuildPlan(layout);
        assertEquals(Material.SNOW_BLOCK, plan.material(0, 99, 0));
        assertEquals(Material.BARRIER, plan.material(7, 118, 0));
        assertNull(plan.material(0, 118, 0));
    }

    @Test
    void cursorRespectsBudgetAndResumesWithoutRepeating() {
        ArenaLayout layout = new ArenaLayout(ToffyEventType.TNT_TAG, 0, 100, 0, 6, 1, 1, 0);
        ArenaBuildPlan.Cursor cursor = new ArenaBuildPlan.Cursor(layout, 0, 0);
        long total = 0;
        while (cursor.hasNext()) {
            int count = 0;
            while (count < 7 && cursor.hasNext()) {
                cursor.next();
                count++;
            }
            assertTrue(count <= 7);
            total += count;
        }
        assertEquals(11L * 11 * (layout.clearMaxY() - layout.clearMinY() + 1), total);
    }
}
