package dev.linqfy.bigCasares.modules.auctionhouse;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionHouseConfigTest {

    @Test
    void readsShippedSettings() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        assertTrue(config.getBoolean("modules.auction-house.enabled"));
        AuctionHouseSettings settings = AuctionHouseSettings.load(config);
        assertEquals(10, settings.maxListingsPerPlayer());
        assertEquals(5, settings.taxPercent());
        assertEquals(Duration.ofHours(48), settings.listingDuration());
        assertEquals(50, settings.taxOf(1_000));
        assertEquals(0, settings.taxOf(19));
    }

    @Test
    void clampsInvalidSettings() {
        AuctionHouseSettings settings = new AuctionHouseSettings(500, 0, -5, 150, Duration.ZERO);
        assertEquals(45, settings.maxListingsPerPlayer());
        assertEquals(1, settings.minPrice());
        assertEquals(1, settings.maxPrice());
        assertEquals(100, settings.taxPercent());
        assertEquals(Duration.ofHours(48), settings.listingDuration());
        assertEquals(AuctionHouseSettings.defaults(), AuctionHouseSettings.load(new YamlConfiguration()));
    }

    @Test
    void parsesAndFormatsPrices() {
        assertEquals(1_500, AuctionFormat.parsePrice("1,500"));
        assertEquals(2_000, AuctionFormat.parsePrice("2k"));
        assertEquals(1_500_000, AuctionFormat.parsePrice("1.5M"));
        assertThrows(NumberFormatException.class, () -> AuctionFormat.parsePrice("0"));
        assertThrows(NumberFormatException.class, () -> AuctionFormat.parsePrice("1.5"));
        assertThrows(NumberFormatException.class, () -> AuctionFormat.parsePrice("abc"));
        assertEquals("$1,234,567", AuctionFormat.money(1_234_567));
        assertEquals("1d 2h", AuctionFormat.remaining(Duration.ofHours(26)));
        assertEquals("3h 5m", AuctionFormat.remaining(Duration.ofMinutes(185)));
        assertEquals("1m", AuctionFormat.remaining(Duration.ofSeconds(10)));
    }

    @Test
    void moduleIsRegisteredAndCommandDeclared() throws Exception {
        assertEquals("auction-house", new AuctionHouseModule(null).getId());
        AuctionHouseModule module = new AuctionHouseModule(null);
        assertDoesNotThrow(() -> module.onEnable(new RuntimeRegistrationScope()));
        assertDoesNotThrow(module::onDisable);

        CompiledClass main = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.BigCasares");
        assertTrue(main.calls("dev/linqfy/bigCasares/modules/auctionhouse/AuctionHouseModule", "<init>"));
        assertEquals(AuctionHouseModule.class, BigCasares.class.getMethod("getAuctionHouseModule").getReturnType());

        YamlConfiguration plugin = new YamlConfiguration();
        plugin.options().pathSeparator('/');
        plugin.load(new File("src/main/resources/plugin.yml"));
        assertTrue(plugin.contains("commands/ah/usage"));
        assertEquals("true", plugin.getString("permissions/bigcasares.auctionhouse.use/default"));
        assertEquals("op", plugin.getString("permissions/bigcasares.auctionhouse.admin/default"));
        assertTrue(plugin.getBoolean("permissions/bigcasares.*/children/bigcasares.auctionhouse.admin"));
    }
}
