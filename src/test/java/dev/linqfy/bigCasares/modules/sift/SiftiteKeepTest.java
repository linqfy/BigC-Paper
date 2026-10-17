package dev.linqfy.bigCasares.modules.sift;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftiteKeepTest {

    @Test
    void protectsExactlyTheModsTag() {
        assertEquals(14, SiftiteKeep.PROTECTED_IDS.size());
        for (String id : List.of("siftslate_siftite_ore", "siftite_nugget", "siftite_ingot", "siftite_upgrade_smithing_template",
            "siftite_sword", "siftite_shovel", "siftite_pickaxe", "siftite_axe", "siftite_hoe", "siftite_spear",
            "siftite_helmet", "siftite_chestplate", "siftite_leggings", "siftite_boots")) {
            assertTrue(SiftiteKeep.isProtected("the_sift:" + id), id);
        }
        assertFalse(SiftiteKeep.isProtected("the_sift:charoite"));
        assertFalse(SiftiteKeep.isProtected("minecraft:netherite_sword"));
        assertFalse(SiftiteKeep.isProtected("siftite_ingot"));
        assertFalse(SiftiteKeep.isProtected(null));
    }

    @Test
    void keepsTheSiftiteDropsInOrderAndLeavesTheRestToDrop() {
        List<String> drops = new ArrayList<>(List.of("dirt", "the_sift:siftite_sword", "diamond", "the_sift:siftite_ingot", "the_sift:siftite_ingot"));
        List<String> kept = SiftiteKeep.select(drops, false, id -> id.startsWith("the_sift:siftite"));
        assertEquals(List.of("the_sift:siftite_sword", "the_sift:siftite_ingot", "the_sift:siftite_ingot"), kept);
        assertEquals(5, drops.size(), "selecting does not change the drops");
        SiftiteKeep.remove(drops, id -> id.startsWith("the_sift:siftite"));
        assertEquals(List.of("dirt", "diamond"), drops);
    }

    @Test
    void doesNothingWhenTheInventoryIsKept() {
        List<String> drops = new ArrayList<>(List.of("the_sift:siftite_sword"));
        assertEquals(List.of(), SiftiteKeep.select(drops, true, id -> true));
        assertEquals(List.of("the_sift:siftite_sword"), drops);
    }

    @Test
    void ignoresEmptyEntries() {
        List<String> drops = new ArrayList<>();
        drops.add(null);
        drops.add("a");
        assertEquals(List.of("a"), SiftiteKeep.select(drops, false, id -> true));
        SiftiteKeep.remove(drops, id -> true);
        assertEquals(1, drops.size());
        assertEquals(null, drops.get(0));
    }
}
