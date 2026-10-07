package dev.linqfy.bigCasares.modules.donpollos;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonPollosRewardTest {

    @Test
    void donPolloSaysTheFivePhrasesInOrder() {
        assertEquals(List.of(
            "Ya no hay video mi gente...",
            "Ni salsa...",
            "Ni picante...",
            "Mi gente triste...",
            "Elegi lo que quieras..."), DonPollosReward.DEFEAT_PHRASES);
    }

    @Test
    void gorditoSaysHisSecretPhrasesInOrder() {
        assertEquals(List.of("Yo se cosas...", "Queres saberlas...", "Dame Salsa y Picante"),
            DonPollosReward.SECRET_PHRASES);
    }

    @Test
    void secretBlockTellsTheStoryBeforeTurningEvil() {
        assertEquals(6, DonPollosReward.BLOCK_STORY.size());
        assertEquals("Era una vez...", DonPollosReward.BLOCK_STORY.getFirst());
        assertEquals("\"No hay salsa, ni picante, ni nada mi gente\" dijo", DonPollosReward.BLOCK_STORY.get(4));
        assertEquals("Desde ese dia algo cambio en Don Pollo...", DonPollosReward.BLOCK_STORY.getLast());
    }

    @Test
    void eachDonPolloSaysGoodbyeExceptTheCommonOne() {
        assertEquals("Lastima que alguien no tiene salsa y picante...", DonPollosReward.FAREWELLS.get(DonPolloVariant.AURA_67));
        assertEquals("Como estan los precios...", DonPollosReward.FAREWELLS.get(DonPolloVariant.FINO));
        assertEquals("A alguien le falta moverse...", DonPollosReward.FAREWELLS.get(DonPolloVariant.SALSERO));
        assertEquals("Tene cuidado...", DonPollosReward.FAREWELLS.get(DonPolloVariant.GORDITO));
        assertTrue(!DonPollosReward.FAREWELLS.containsKey(DonPolloVariant.COMUN));
        assertEquals("En esta ciudad pasaron cosas...", DonPollosReward.CITY_WELCOME);
    }

    @Test
    void rewardIsTheBucketPlusFourChoices() {
        assertEquals(4, DonPollosReward.CHOICES);
    }

    @Test
    void itemsArePagedFortyFiveAtATime() {
        List<Integer> items = IntStream.range(0, 100).boxed().toList();
        assertEquals(3, DonPollosReward.pageCount(items.size(), DonPollosReward.ITEMS_PER_PAGE));
        assertEquals(1, DonPollosReward.pageCount(0, DonPollosReward.ITEMS_PER_PAGE));
        assertEquals(45, DonPollosReward.page(items, 0, 45).size());
        assertEquals(10, DonPollosReward.page(items, 2, 45).size());
        assertEquals(90, DonPollosReward.page(items, 2, 45).getFirst());
        assertTrue(DonPollosReward.page(items, 3, 45).isEmpty());
    }

    @Test
    void itemsGoToTheirCategory() {
        assertEquals(DonPollosReward.Category.COMBATE, DonPollosReward.category("DIAMOND_SWORD", false, false, false, false));
        assertEquals(DonPollosReward.Category.COMBATE, DonPollosReward.category("NETHERITE_CHESTPLATE", false, false, false, false));
        assertEquals(DonPollosReward.Category.CONSTRUCCION, DonPollosReward.category("STONE", true, true, true, false));
        assertEquals(DonPollosReward.Category.CONSTRUCCION, DonPollosReward.category("OAK_STAIRS", true, true, false, false));
        assertEquals(DonPollosReward.Category.DECORACION, DonPollosReward.category("POPPY", true, false, false, false));
        assertEquals(DonPollosReward.Category.COMIDA, DonPollosReward.category("APPLE", false, false, false, true));
        assertEquals(DonPollosReward.Category.POCIONES, DonPollosReward.category("SPLASH_POTION", false, false, false, false));
        assertEquals(DonPollosReward.Category.TRANSPORTE, DonPollosReward.category("OAK_BOAT", false, false, false, false));
        assertEquals(DonPollosReward.Category.TRANSPORTE, DonPollosReward.category("POWERED_RAIL", true, false, false, false));
        assertEquals(DonPollosReward.Category.REDSTONE, DonPollosReward.category("REPEATER", true, false, false, false));
        assertEquals(DonPollosReward.Category.HERRAMIENTAS, DonPollosReward.category("DIAMOND_PICKAXE", false, false, false, false));
        assertEquals(DonPollosReward.Category.HERRAMIENTAS, DonPollosReward.category("WATER_BUCKET", false, false, false, false));
        assertEquals(DonPollosReward.Category.VARIOS, DonPollosReward.category("STICK", false, false, false, false));
        assertEquals(9, DonPollosReward.Category.values().length);
    }

    @Test
    void itemNamesAreReadable() {
        assertEquals("Diamond Sword", DonPollosReward.pretty(Material.DIAMOND_SWORD.name()));
    }

    @Test
    void bucketHelmetGivesDepthStriderTwo() {
        assertEquals(2.0 / 3.0, DonPollosKfcBucket.DEPTH_STRIDER_II, 1e-9);
    }
}
