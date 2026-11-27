package dev.linqfy.bigCasares.modules.missions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionEntityKeysTest {
    @Test
    void acceptsNamespacedTags() {
        assertTrue(MissionEntityKeys.isCustomKey("alexscaves:teletor"));
    }

    @Test
    void rejectsMissingOrMalformedTags() {
        assertFalse(MissionEntityKeys.isCustomKey(null));
        assertFalse(MissionEntityKeys.isCustomKey(""));
        assertFalse(MissionEntityKeys.isCustomKey("teletor"));
        assertFalse(MissionEntityKeys.isCustomKey(":x"));
        assertFalse(MissionEntityKeys.isCustomKey("alexscaves:"));
        assertFalse(MissionEntityKeys.isCustomKey("a:" + "x".repeat(200)));
    }
}
