package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlockInteractionCheckTest {
    private final BlockInteractionCheck check = new BlockInteractionCheck();
    private final UUID player = UUID.randomUUID();

    @Test
    void instamineAtOneBlockPerTickIsFine() {
        for (long tick = 0; tick < 400; tick++) {
            assertTrue(check.breakBlock(player, tick).isEmpty());
        }
    }

    @Test
    void aSmallLagBurstIsFine() {
        for (int i = 0; i < 3; i++) {
            assertTrue(check.breakBlock(player, 10).isEmpty());
        }
    }

    @Test
    void nukingTwiceIsFlagged() {
        Optional<Detection> detection = Optional.empty();
        for (long tick : new long[] {10, 50}) {
            for (int i = 0; i < 8; i++) {
                Optional<Detection> next = check.breakBlock(player, tick);
                if (next.isPresent()) {
                    detection = next;
                }
            }
        }
        assertEquals("block-break-rate", detection.orElseThrow().rule());
    }

    @Test
    void faceVisibilityFollowsTheEye() {
        // ojos arriba del bloque: la cara de arriba se ve, la de abajo no
        assertTrue(BlockInteractionCheck.faceVisible(0.5, 2.6, 0.5, 0, 0, 0, 0, 1, 0));
        assertFalse(BlockInteractionCheck.faceVisible(0.5, 2.6, 0.5, 0, 0, 0, 0, -1, 0));
        // puenteando agachado al borde: los ojos apenas pasan el borde oeste
        assertTrue(BlockInteractionCheck.faceVisible(-0.2, 1.6, 0.5, 0, 0, 0, -1, 0, 0));
        // parado sobre la mitad oeste del bloque tambien vale por el margen de latencia
        assertTrue(BlockInteractionCheck.faceVisible(0.45, 1.6, 0.5, 0, 0, 0, -1, 0, 0));
        assertFalse(BlockInteractionCheck.faceVisible(2.0, 1.6, 0.5, 0, 0, 0, -1, 0, 0));
    }

    @Test
    void placingOnHiddenFacesRepeatedlyIsFlagged() {
        Optional<Detection> detection = Optional.empty();
        for (int i = 0; i < 5 && detection.isEmpty(); i++) {
            detection = check.place(player, 0.5, 3.0, 0.5, 0, 0, 0, 0, -1, 0);
        }
        assertEquals("block-place-face", detection.orElseThrow().rule());
    }
}
