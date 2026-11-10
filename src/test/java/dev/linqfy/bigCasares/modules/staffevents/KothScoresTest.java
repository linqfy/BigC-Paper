package dev.linqfy.bigCasares.modules.staffevents;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KothScoresTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void onlyASoleOccupantScores() {
        KothScores scores = new KothScores(3);
        assertEquals(KothScores.HillState.EMPTY, scores.tick(List.of()));
        assertEquals(KothScores.HillState.HELD, scores.tick(List.of(alice)));
        assertEquals(KothScores.HillState.CONTESTED, scores.tick(List.of(alice, bob)));
        assertEquals(1, scores.points(alice));
        assertEquals(0, scores.points(bob));
    }

    @Test
    void reachingTheTargetCapturesTheHill() {
        KothScores scores = new KothScores(2);
        scores.tick(List.of(alice));
        assertEquals(Optional.empty(), scores.capturedBy());
        scores.tick(List.of(alice));
        assertEquals(Optional.of(alice), scores.capturedBy());
    }

    @Test
    void aTiedLeadHasNoLeader() {
        KothScores scores = new KothScores(10);
        scores.tick(List.of(alice));
        scores.tick(List.of(bob));
        assertEquals(Optional.empty(), scores.leader());
        scores.tick(List.of(bob));
        assertEquals(Optional.of(bob), scores.leader());
    }
}
