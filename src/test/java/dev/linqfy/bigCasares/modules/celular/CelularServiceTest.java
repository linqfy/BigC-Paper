package dev.linqfy.bigCasares.modules.celular;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CelularServiceTest {

    private static final List<CelularVideo> THREE = List.of(
        new CelularVideo("video_1", "Uno", 10),
        new CelularVideo("video_2", "Dos", 20),
        new CelularVideo("video_3", "Tres", 30));

    private static CelularService service(boolean villageLoot) {
        return new CelularService(THREE, new CelularSettings(true, villageLoot));
    }

    @Test
    void fGoesToTheNextVideoAndWrapsAround() {
        CelularService service = service(true);
        assertEquals(1, service.next(0));
        assertEquals(2, service.next(1));
        assertEquals(0, service.next(2));
        assertEquals(1, service.navigate(0, false));
    }

    @Test
    void shiftFGoesBackAndWrapsAround() {
        CelularService service = service(true);
        assertEquals(1, service.previous(2));
        assertEquals(0, service.previous(1));
        assertEquals(2, service.previous(0));
        assertEquals(2, service.navigate(0, true));
    }

    @Test
    void onBedrockThePhoneDoesNothingAndCraftingSaysTheMessage() {
        assertTrue(CelularService.worksFor(false));
        assertFalse(CelularService.worksFor(true));
        assertEquals("Dale bobi, no tenes java?, bancatela pibe", CelularService.BEDROCK_CRAFT_MESSAGE);
        assertTrue(CelularService.HINT.contains("F siguiente"));
    }

    @Test
    void geyserGetsTheStillModelAndOneModelPerVideo() {
        assertEquals(List.of("celular:celular", "celular:video_1", "celular:video_2", "celular:video_3"),
            service(true).itemModels());
    }

    @Test
    void outOfRangeIndexesFallBackToTheFirstVideo() {
        CelularService service = service(true);
        assertEquals(0, service.clamp(-1));
        assertEquals(0, service.clamp(3));
        assertEquals("Uno", service.video(42).title());
        assertEquals(1, service.next(42));
    }

    @Test
    void actionBarShowsPositionAndTitle() {
        assertEquals("▶ 2/3  Dos", service(true).actionBar(1));
    }

    @Test
    void needsAtLeastOneVideo() {
        assertThrows(IllegalArgumentException.class, () -> new CelularService(List.of(), CelularSettings.defaults()));
    }

    @Test
    void recognisesEveryVillageChestAndNothingElse() {
        assertTrue(CelularService.isVillageChest("minecraft", "chests/village/village_plains_house"));
        assertTrue(CelularService.isVillageChest("minecraft", "chests/village/village_weaponsmith"));
        assertTrue(CelularService.isVillageChest("minecraft", "chests/village/village_temple"));
        assertFalse(CelularService.isVillageChest("minecraft", "chests/simple_dungeon"));
        assertFalse(CelularService.isVillageChest("minecraft", "chests/pillager_outpost"));
        assertFalse(CelularService.isVillageChest("otro", "chests/village/village_plains_house"));
    }

    @Test
    void onlyTheFirstChestOfEachVillageGetsAPhone() {
        CelularService service = service(true);
        assertTrue(service.shouldAddVillagePhone("minecraft", "chests/village/village_desert_house", false));
        assertFalse(service.shouldAddVillagePhone("minecraft", "chests/village/village_desert_house", true));
        assertFalse(service.shouldAddVillagePhone("minecraft", "chests/simple_dungeon", false));
    }

    @Test
    void villageLootCanBeTurnedOff() {
        assertFalse(service(false).shouldAddVillagePhone("minecraft", "chests/village/village_plains_house", false));
    }
}
