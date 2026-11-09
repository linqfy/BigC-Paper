package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MovementCheckTest {
    private static final double SPRINT_ATTRIBUTE = 0.13;
    private static final double[] JUMP_ARC = {0.42, 0.33, 0.25, 0.17, 0.10, 0.02, -0.06, -0.14, -0.22, -0.30, -0.37};

    private final MovementCheck check = new MovementCheck();
    private final UUID player = UUID.randomUUID();

    @Test
    void sprintJumpingForAWhileNeverFlags() {
        for (int lap = 0; lap < 30; lap++) {
            assertClean(sample(1, 0.28, 0, true));
            assertClean(sample(1, 0.62, JUMP_ARC[0], false));
            for (int i = 1; i < JUMP_ARC.length; i++) {
                assertClean(sample(1, 0.36 - i * 0.004, JUMP_ARC[i], false));
            }
            assertClean(sample(1, 0.45, 0, true));
            assertClean(sample(1, 0.36, 0, true));
        }
    }

    @Test
    void slowMovementSpanningSeveralTicksIsMeasuredPerTick() {
        for (int i = 0; i < 40; i++) {
            // Paper junta movimientos chicos: un evento cada 3 ticks con el triple de distancia
            assertClean(sample(3, 0.28 * 3, 0, true));
        }
    }

    @Test
    void sustainedGroundSpeedIsFlagged() {
        Optional<Detection> detection = Optional.empty();
        for (int i = 0; i < 20 && detection.isEmpty(); i++) {
            detection = check.sample(player, sample(1, 0.7, 0, true));
        }
        assertEquals("movement-speed", detection.orElseThrow().rule());
    }

    @Test
    void aSingleSpikeIsNotEnough() {
        for (int i = 0; i < 10; i++) {
            assertClean(sample(1, 0.28, 0, true));
        }
        assertClean(sample(1, 1.4, 0, true));
        for (int i = 0; i < 10; i++) {
            assertClean(sample(1, 0.28, 0, true));
        }
    }

    @Test
    void exemptSamplesClearAccumulatedViolations() {
        for (int i = 0; i < 4; i++) {
            check.sample(player, sample(1, 0.9, 0, true));
        }
        assertClean(new MovementSample(1, 3.0, 2.0, false, SPRINT_ATTRIBUTE, true, 0.42, 0.6, true));
        for (int i = 0; i < 3; i++) {
            assertClean(sample(1, 0.9, 0, true));
        }
    }

    @Test
    void jumpBoostRaisesTheVerticalLimit() {
        double boosted = 0.42 + 0.2;
        for (int lap = 0; lap < 10; lap++) {
            assertClean(new MovementSample(1, 0.2, 0, true, SPRINT_ATTRIBUTE, true, boosted, 0.6, false));
            assertClean(new MovementSample(1, 0.4, 0.62, false, SPRINT_ATTRIBUTE, true, boosted, 0.6, false));
        }
    }

    @Test
    void flyingUpwardIsFlagged() {
        Optional<Detection> detection = Optional.empty();
        for (int i = 0; i < 40 && detection.isEmpty(); i++) {
            detection = check.sample(player, sample(1, 0.1, 0.3, false));
        }
        assertEquals("upward-movement", detection.orElseThrow().rule());
    }

    @Test
    void repeatedImpossibleRisesAreFlagged() {
        assertClean(sample(1, 0.1, 1.5, false));
        assertEquals("upward-movement", check.sample(player, sample(1, 0.1, 1.5, false)).orElseThrow().rule());
    }

    @Test
    void notSprintingServerSideStillAllowsSprintSpeed() {
        for (int i = 0; i < 40; i++) {
            assertClean(new MovementSample(1, 0.28 * 1.2, 0, true, 0.1, false, 0.42, 0.6, false));
        }
    }

    private MovementSample sample(int elapsed, double horizontal, double vertical, boolean onGround) {
        return new MovementSample(elapsed, horizontal, vertical, onGround, SPRINT_ATTRIBUTE, true, 0.42, 0.6, false);
    }

    private void assertClean(MovementSample sample) {
        Optional<Detection> detection = check.sample(player, sample);
        assertTrue(detection.isEmpty(), () -> "unexpected " + detection.orElseThrow());
    }
}
