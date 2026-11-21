package dev.linqfy.bigCasares.modules.staffevents;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardItemTest {

    @Test
    void parsesCatalogAndVanillaIdsWithOptionalAmounts() {
        assertEquals(Optional.of(new RewardItem("nuke_shot", 1)), RewardItem.parse("nuke_shot"));
        assertEquals(Optional.of(new RewardItem("nuke_shot", 2)), RewardItem.parse(" Nuke_Shot:2 "));
        assertEquals(Optional.of(new RewardItem("minecraft:diamond", 1)), RewardItem.parse("minecraft:diamond"));
        assertEquals(Optional.of(new RewardItem("minecraft:diamond", 16)), RewardItem.parse("minecraft:diamond:16"));
        assertTrue(RewardItem.parse("minecraft:diamond").orElseThrow().vanilla());
    }

    @Test
    void rejectsInvalidEntries() {
        assertEquals(Optional.empty(), RewardItem.parse(""));
        assertEquals(Optional.empty(), RewardItem.parse("nuke_shot:0"));
        assertEquals(Optional.empty(), RewardItem.parse("bad item"));
        assertEquals(Optional.empty(), RewardItem.parse("nuke_shot:99999999999"));
    }
}
