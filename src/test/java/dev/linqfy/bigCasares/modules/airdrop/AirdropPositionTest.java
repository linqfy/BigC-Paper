package dev.linqfy.bigCasares.modules.airdrop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirdropPositionTest {

    @Test
    void matchesBlockReturnsTrueOnlyForExactBlockCoordinates() {
        AirdropPosition position = new AirdropPosition(12, 65, -8);

        assertTrue(position.matchesBlock(12, 65, -8));
        assertFalse(position.matchesBlock(12, 65, -7));
        assertFalse(position.matchesBlock(12, 64, -8));
        assertFalse(position.matchesBlock(11, 65, -8));
    }
}
