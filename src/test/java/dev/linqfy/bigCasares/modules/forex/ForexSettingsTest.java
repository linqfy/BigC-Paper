package dev.linqfy.bigCasares.modules.forex;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForexSettingsTest {

    @Test
    void shippedConfigMatchesTheBuiltInDefaults() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        assertEquals(ForexSettings.defaults(), ForexSettings.load(config));
    }

    @Test
    void creatorFeesAndRugPullsStayInsideTheAdvertisedRanges() {
        ForexSettings defaults = ForexSettings.defaults();
        assertEquals(0.03, defaults.coins().creatorFeeMin());
        assertEquals(0.05, defaults.coins().creatorFeeMax());
        assertEquals(0.80, defaults.coins().rugPullMin());
        assertEquals(0.90, defaults.coins().rugPullMax());
        assertEquals(0.10, defaults.coins().pumpChance());
        assertEquals(180, defaults.bubble().flipIntervalMinutes());
        assertEquals(List.of(30L, 10L, 1L), defaults.bubble().warningMinutes());
    }

    @Test
    void invalidValuesAreClampedOrRejected() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            forex:
              coins:
                creator-fee-min: 0.04
                creator-fee-max: 0.02
                creator-fee-default: 0.9
              bubble:
                min-holders: 0
            """);
        ForexSettings settings = ForexSettings.load(yaml);
        assertEquals(0.04, settings.coins().creatorFeeMax());
        assertEquals(0.04, settings.coins().creatorFeeDefault());
        assertEquals(2, settings.bubble().minHolders());

        YamlConfiguration broken = new YamlConfiguration();
        broken.loadFromString("forex:\n  emerald:\n    start-price: 0\n");
        assertThrows(IllegalArgumentException.class, () -> ForexSettings.load(broken));
    }

    @Test
    void dailyRatesConvertToCompoundedCycleRates() {
        ForexSettings settings = ForexSettings.defaults();
        double perCycle = settings.perCycle(0.02);
        assertEquals(1.02, Math.pow(1 + perCycle, 144), 1e-9);
    }
}
