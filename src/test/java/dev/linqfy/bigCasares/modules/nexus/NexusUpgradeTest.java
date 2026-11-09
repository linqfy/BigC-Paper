package dev.linqfy.bigCasares.modules.nexus;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NexusUpgradeTest {

    @Test
    void upgradeIdsAreStableAndParseLeniently() {
        assertEquals("repulsor", NexusUpgrade.MOB_REPULSOR.id());
        assertEquals("saturacion", NexusUpgrade.SATURATION_AURA.id());
        assertEquals(Optional.of(NexusUpgrade.SATURATION_AURA), NexusUpgrade.parse(" Saturación "));
        assertEquals(Optional.of(NexusUpgrade.MOB_REPULSOR), NexusUpgrade.parse("MOB_REPULSOR"));
        assertEquals(Optional.empty(), NexusUpgrade.parse("velocidad"));
        assertEquals(Optional.empty(), NexusUpgrade.parse(null));
    }

    @Test
    void settingsLoadPricesAndFallBackOnNonsense() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("nexus-system.upgrades.repulsor.price", 100.0);
        yaml.set("nexus-system.upgrades.saturacion.radius", 8.0);

        NexusUpgradeSettings settings = NexusUpgradeSettings.fromConfig(yaml);

        assertEquals(100.0, settings.price(NexusUpgrade.MOB_REPULSOR));
        assertEquals(15000.0, settings.price(NexusUpgrade.SATURATION_AURA));
        assertEquals(8.0, settings.saturationRadius());

        yaml.set("nexus-system.upgrades.repulsor.radius", 500.0);
        assertEquals(NexusUpgradeSettings.defaults(), NexusUpgradeSettings.fromConfig(yaml));
    }

    @Test
    void repulsorPushesHorizontallyAwayFromTheNexus() {
        assertArrayEquals(new double[] {0.6, 0.8}, NexusUpgradeSettings.pushAway(3.0, 4.0, 1.0), 1.0E-9);
        assertArrayEquals(new double[] {-0.5, 0.0}, NexusUpgradeSettings.pushAway(-2.0, 0.0, 0.5), 1.0E-9);
        assertArrayEquals(new double[] {0.8, 0.0}, NexusUpgradeSettings.pushAway(0.0, 0.0, 0.8), 1.0E-9);
    }

    @Test
    void saturationAuraFeedsWithoutExceedingVanillaLimits() {
        assertArrayEquals(new float[] {11f, 3f}, NexusUpgradeSettings.feed(10, 2f, 1), 1.0E-6f);
        assertArrayEquals(new float[] {20f, 20f}, NexusUpgradeSettings.feed(20, 20f, 1), 1.0E-6f);
        assertArrayEquals(new float[] {2f, 2f}, NexusUpgradeSettings.feed(0, 5f, 2), 1.0E-6f, "saturación <= hambre");
    }
}
