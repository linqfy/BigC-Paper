package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.platform.ClientPlatform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Solo cuenta lo confirmado como Bedrock. */
class BedrockTest {

    @Test
    void onlyBedrockCountsAsBedrock() {
        assertTrue(Bedrock.isBedrock(ClientPlatform.BEDROCK));
        assertFalse(Bedrock.isBedrock(ClientPlatform.JAVA));
        assertFalse(Bedrock.isBedrock(ClientPlatform.UNKNOWN));
    }
}
