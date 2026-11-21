package dev.linqfy.bigCasares.modules.duels;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class DuelSnapshotRecoveryTest {
    @Test void invalidSlotPreventsDecodingAnySnapshot() {
        var yaml = new YamlConfiguration();
        yaml.set("0", Base64.getEncoder().encodeToString(new byte[]{42}));
        yaml.set("1", "not base64!");
        assertThrows(IllegalArgumentException.class, () -> DuelSnapshotCodec.decode(yaml, 2, bytes -> bytes[0]));
    }

    @Test void onlyMatchingLiveRevisionCanReplaceAnExistingSnapshot() {
        assertTrue(DuelSnapshotCodec.mayReplace("one", "one"));
        assertFalse(DuelSnapshotCodec.mayReplace("one", "old"));
        assertFalse(DuelSnapshotCodec.mayReplace(null, null));
    }

    @Test void retirementRequiresAFreshJoinAndTheMatchingRevision() {
        assertFalse(DuelSnapshotCodec.mayRetire("one", "one", false));
        assertFalse(DuelSnapshotCodec.mayRetire("one", "other", true));
        assertFalse(DuelSnapshotCodec.mayRetire(null, null, true));
        assertTrue(DuelSnapshotCodec.mayRetire("one", "one", true));
    }
}
