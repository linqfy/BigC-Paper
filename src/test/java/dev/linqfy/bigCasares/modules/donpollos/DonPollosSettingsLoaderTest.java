package dev.linqfy.bigCasares.modules.donpollos;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonPollosSettingsLoaderTest {

    @Test
    void bundledFileDefinesEveryVariant() {
        DonPollosSettings settings = new DonPollosSettingsLoader().load(bundled());

        for (DonPolloVariant variant : DonPolloVariant.values()) {
            DonPollosVariantSettings variantSettings = settings.variant(variant);
            assertNotNull(variantSettings);
            assertTrue(variantSettings.modelKey().startsWith("bigcasares_don_pollo_"));
        }
    }

    @Test
    void bundledGorditoTradesUseOnlyBreadAndAuraOnlyHasThePolleria() {
        DonPollosSettings settings = new DonPollosSettingsLoader().load(bundled());
        List<DonPollosTrade> gordito = settings.variant(DonPolloVariant.GORDITO).trades();

        assertFalse(gordito.isEmpty());
        for (DonPollosTrade trade : gordito) {
            assertEquals("BREAD", trade.cost().material());
            assertEquals("BREAD", trade.result().material());
        }
        assertTrue(settings.variant(DonPolloVariant.AURA_67).trades().isEmpty());
        assertFalse(settings.variant(DonPolloVariant.AURA_67).offers().isEmpty());
    }

    @Test
    void bundledFinoRouletteIsTwentyFourTwentyFourTwoAndDoubles() {
        DonPollosRoulette roulette = new DonPollosSettingsLoader().load(bundled()).roulette();

        assertEquals(24, roulette.weight(DonPollosRouletteColor.RED));
        assertEquals(24, roulette.weight(DonPollosRouletteColor.BLACK));
        assertEquals(2, roulette.weight(DonPollosRouletteColor.GREEN));
        for (DonPollosRouletteColor color : DonPollosRouletteColor.values()) {
            assertEquals(2, roulette.multiplier(color));
        }
    }

    @Test
    void salseroHasFiveDances() {
        DonPollosSettings settings = new DonPollosSettingsLoader().load(bundled());

        assertEquals(List.of("dance1", "dance2", "dance3", "dance4", "dance5"), settings.salseroDances());
    }

    @Test
    void salseroBattleRewardsFiveDiamondsAndHasThreeLives() {
        DonPollosBattleSettings battle = new DonPollosSettingsLoader().load(bundled()).battle();

        assertEquals(new DonPollosItem("DIAMOND", 5), battle.reward());
        assertEquals(3, battle.lives());
        assertTrue(battle.hitsToWin() > 0);
    }

    @Test
    void citiesSpawnSeveralOfEveryDonPollo() {
        DonPollosCitySettings cities = new DonPollosSettingsLoader().load(bundled()).cities();

        assertTrue(cities.enabled());
        assertTrue(cities.spacing() >= 1000);
        for (DonPolloVariant variant : DonPolloVariant.values()) {
            assertTrue(cities.count(variant) >= 2, variant.id());
        }
    }

    @Test
    void rejectsZeroRouletteWeight() {
        YamlConfiguration config = bundled();
        config.set("fino.roulette.green.weight", 0);

        assertThrows(IllegalArgumentException.class, () -> new DonPollosSettingsLoader().load(config));
    }

    @Test
    void bundledWanderingMatchesVariantBehaviour() {
        DonPollosSettings settings = new DonPollosSettingsLoader().load(bundled());

        assertTrue(settings.variant(DonPolloVariant.COMUN).wander());
        assertTrue(settings.variant(DonPolloVariant.SALSERO).wander());
        assertTrue(settings.variant(DonPolloVariant.GORDITO).wander());
        assertTrue(settings.variant(DonPolloVariant.GORDITO).speed() < settings.variant(DonPolloVariant.COMUN).speed());
        assertFalse(settings.variant(DonPolloVariant.FINO).wander());
    }

    @Test
    void auraSellsFriedChickenSauceAndSpicyForBread() {
        DonPollosSettings settings = new DonPollosSettingsLoader().load(bundled());

        assertEquals(List.of(
            new DonPollosOffer(DonPollosProduct.POLLO_FRITO, 4, 5),
            new DonPollosOffer(DonPollosProduct.SALSA, 1, 2),
            new DonPollosOffer(DonPollosProduct.PICANTE, 1, 3)
        ), settings.variant(DonPolloVariant.AURA_67).offers());
    }

    @Test
    void rejectsUnknownProductOffer() {
        YamlConfiguration config = bundled();
        config.set("variants.aura-67.offers", List.of(Map.of("item", "hamburguesa", "amount", 1, "price", 1)));

        assertThrows(IllegalArgumentException.class, () -> new DonPollosSettingsLoader().load(config));
    }

    @Test
    void rejectsUnknownMaterial() {
        YamlConfiguration config = bundled();
        config.set("variants.gordito.trades", List.of(Map.of(
            "cost", Map.of("material", "NOT_A_MATERIAL", "amount", 1),
            "result", Map.of("material", "BREAD", "amount", 1))));

        assertThrows(IllegalArgumentException.class, () -> new DonPollosSettingsLoader().load(config));
    }

    @Test
    void rejectsMissingVariant() {
        YamlConfiguration config = bundled();
        config.set("variants.fino", null);

        assertThrows(IllegalArgumentException.class, () -> new DonPollosSettingsLoader().load(config));
    }

    @Test
    void rejectsUnknownVariantKey() {
        YamlConfiguration config = bundled();
        config.set("variants.pollo-loco.display-name", "x");

        assertThrows(IllegalArgumentException.class, () -> new DonPollosSettingsLoader().load(config));
    }

    private static YamlConfiguration bundled() {
        try (InputStream stream = DonPollosSettingsLoaderTest.class.getClassLoader().getResourceAsStream("don-pollos.yml")) {
            assertNotNull(stream, "don-pollos.yml must be bundled");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
