package dev.linqfy.bigCasares.modules.moderation.anticheat;

import dev.linqfy.bigCasares.platform.ClientPlatform;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuplicateScanPolicyTest {
    @Test void honorsBypassPlatformAndGameModeBeforeScanning() {
        var settings = AnticheatSettings.defaults();
        assertTrue(DuplicateItemScanner.shouldScan(settings, GameMode.SURVIVAL, false, ClientPlatform.JAVA));
        assertFalse(DuplicateItemScanner.shouldScan(settings, GameMode.SURVIVAL, true, ClientPlatform.JAVA));
        assertFalse(DuplicateItemScanner.shouldScan(settings, GameMode.SURVIVAL, false, ClientPlatform.BEDROCK));
        assertFalse(DuplicateItemScanner.shouldScan(settings, GameMode.CREATIVE, false, ClientPlatform.JAVA));
        assertFalse(DuplicateItemScanner.shouldScan(settings, GameMode.SPECTATOR, false, ClientPlatform.JAVA));
    }

    @Test void bedrockExemptionCanBeDisabled() {
        var base = AnticheatSettings.defaults();
        var settings = new AnticheatSettings(base.enabled(), base.bypassPermission(), false,
            base.minimumTps(), base.maxAttacksPerSecond(), base.disabledChecks(), base.dupeTrackedItems(),
            base.trackShulkerBoxes());
        assertTrue(DuplicateItemScanner.shouldScan(settings, GameMode.SURVIVAL, false, ClientPlatform.BEDROCK));
    }
}
