package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.DarkSnifferBrain.Combatant;
import dev.linqfy.bigCasares.modules.sift.SiftGeometry.Aabb;
import dev.linqfy.bigCasares.modules.sift.SiftGeometry.Vec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardenPettingTest {

    /** Bounded draws are {@code value}, capped below the bound. */
    private static final class FixedRandom extends Random {
        private final int value;

        FixedRandom(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            return Math.min(value, bound - 1);
        }
    }

    private static final class FakeSniffer implements WardenPetting.Pettable {
        final UUID id = UUID.randomUUID();
        Vec position;
        boolean alive = true;
        boolean available = true;
        int held;
        int begins;
        int ends;

        FakeSniffer(double x, double z) {
            this.position = new Vec(x, 64, z);
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public boolean alive() {
            return alive;
        }

        @Override
        public Vec position() {
            return position;
        }

        @Override
        public Vec eyePosition() {
            return position.plus(new Vec(0, 1, 0));
        }

        @Override
        public Vec velocity() {
            return Vec.ZERO;
        }

        @Override
        public Aabb box() {
            return new Aabb(position.x() - 1, 64, position.z() - 1, position.x() + 1, 66, position.z() + 1);
        }

        @Override
        public boolean availableForPetting() {
            return available;
        }

        @Override
        public void beginPetting(Combatant warden, int ticks) {
            begins++;
            held = Math.max(held, ticks);
        }

        @Override
        public void endPetting(Combatant warden) {
            ends++;
            held = 0;
        }
    }

    private static final class FakeWarden implements WardenPetting.Keeper {
        final UUID id = UUID.randomUUID();
        Vec position = new Vec(0, 64, 0);
        boolean calm = true;
        List<WardenPetting.Pettable> nearby = new ArrayList<>();
        int moves;
        int stops;
        int hearts;
        int looks;
        final List<Integer> heartTicks = new ArrayList<>();
        int tick;

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public boolean alive() {
            return true;
        }

        @Override
        public Vec position() {
            return position;
        }

        @Override
        public Vec eyePosition() {
            return position.plus(new Vec(0, 2.5, 0));
        }

        @Override
        public Vec velocity() {
            return Vec.ZERO;
        }

        @Override
        public Aabb box() {
            return new Aabb(-0.5, 64, -0.5, 0.5, 66.9, 0.5);
        }

        @Override
        public boolean calm() {
            return calm;
        }

        @Override
        public List<WardenPetting.Pettable> availableSniffersNearby() {
            return nearby.stream().filter(WardenPetting.Pettable::availableForPetting).toList();
        }

        @Override
        public void lookAt(Combatant target, float speed) {
            looks++;
        }

        @Override
        public void moveTo(Combatant target, double speed) {
            assertEquals(0.55, speed);
            moves++;
        }

        @Override
        public void stopNavigation() {
            stops++;
        }

        @Override
        public void heart(WardenPetting.Pettable sniffer) {
            hearts++;
            heartTicks.add(tick);
        }
    }

    private static void run(WardenPetting petting, FakeWarden warden, int ticks) {
        for (int index = 0; index < ticks; index++) {
            warden.tick++;
            petting.tick(warden);
        }
    }

    @Test
    void theFirstSearchComesEightHundredToSixteenHundredTicksAfterTheFirstTick() {
        FakeSniffer sniffer = new FakeSniffer(4, 0);
        FakeWarden warden = new FakeWarden();
        warden.nearby.add(sniffer);
        WardenPetting early = new WardenPetting(new FixedRandom(0));
        run(early, warden, 800);
        assertFalse(early.petting(), "the 800th tick draws 800 and only counts it down");
        run(early, warden, 1);
        assertTrue(early.petting());
        assertEquals(260, sniffer.held, "the sniffer is held for 260 ticks");

        FakeSniffer late = new FakeSniffer(4, 0);
        FakeWarden other = new FakeWarden();
        other.nearby.add(late);
        WardenPetting slow = new WardenPetting(new FixedRandom(10_000));
        run(slow, other, 2400);
        assertFalse(slow.petting(), "the longest first delay is 800 + 1600");
        run(slow, other, 1);
        assertTrue(slow.petting());
    }

    @Test
    void aWardenThatIsNotCalmWhenTheCountdownEndsWaitsAnotherFullDelay() {
        FakeSniffer sniffer = new FakeSniffer(4, 0);
        FakeWarden warden = new FakeWarden();
        warden.nearby.add(sniffer);
        warden.calm = false;
        WardenPetting petting = new WardenPetting(new FixedRandom(0));
        run(petting, warden, 801);
        assertFalse(petting.petting());
        warden.calm = true;
        run(petting, warden, 799);
        assertFalse(petting.petting(), "a new delay of 800 started");
        run(petting, warden, 2);
        assertTrue(petting.petting());
    }

    @Test
    void withoutACandidateTheSearchRepeatsAfterThreeHundredToSixHundredTicks() {
        FakeWarden warden = new FakeWarden();
        WardenPetting petting = new WardenPetting(new FixedRandom(0));
        run(petting, warden, 801);
        assertFalse(petting.petting());
        FakeSniffer sniffer = new FakeSniffer(4, 0);
        warden.nearby.add(sniffer);
        run(petting, warden, 299);
        assertFalse(petting.petting());
        run(petting, warden, 2);
        assertTrue(petting.petting());
    }

    @Test
    void itPicksTheNearestAvailableSniffer() {
        FakeSniffer far = new FakeSniffer(12, 0);
        FakeSniffer near = new FakeSniffer(5, 0);
        FakeSniffer busy = new FakeSniffer(2, 0);
        busy.available = false;
        FakeWarden warden = new FakeWarden();
        warden.nearby.addAll(List.of(far, busy, near));
        WardenPetting petting = new WardenPetting(new FixedRandom(0));
        run(petting, warden, 801);
        assertEquals(1, near.begins);
        assertEquals(0, far.begins);
        assertEquals(0, busy.begins);
    }

    @Test
    void itWalksUpAtHalfSpeedThenPetsForHundredTicksWithAHeartEveryTwentyFromTheEighth() {
        FakeSniffer sniffer = new FakeSniffer(8, 0);
        FakeWarden warden = new FakeWarden();
        warden.nearby.add(sniffer);
        WardenPetting petting = new WardenPetting(new FixedRandom(0));
        run(petting, warden, 801);
        assertTrue(petting.petting());
        run(petting, warden, 10);
        assertEquals(10, warden.moves, "walking: more than 3.5 blocks away");
        assertEquals(0, warden.hearts);
        warden.position = new Vec(6, 64, 0);
        warden.tick = 0;
        run(petting, warden, 100);
        assertEquals(List.of(9, 29, 49, 69, 89), warden.heartTicks, "pettings 8, 28, 48... (zero based) from arriving");
        assertFalse(petting.petting(), "done after a hundred");
        assertEquals(1, sniffer.ends);
        assertTrue(sniffer.begins >= 100, "every tick of the petting refreshes the sniffer's hold");
        // A completed petting waits 2400 to 4800 ticks.
        FakeSniffer next = new FakeSniffer(4, 0);
        warden.nearby.add(next);
        run(petting, warden, 2400);
        assertFalse(petting.petting());
        run(petting, warden, 1);
        assertTrue(petting.petting());
    }

    @Test
    void anApproachThatTakesMoreThanTwoHundredFortyTicksIsGivenUp() {
        FakeSniffer sniffer = new FakeSniffer(10, 0);
        FakeWarden warden = new FakeWarden();
        warden.nearby.add(sniffer);
        WardenPetting petting = new WardenPetting(new FixedRandom(0));
        run(petting, warden, 801);
        run(petting, warden, 240);
        assertTrue(petting.petting());
        run(petting, warden, 1);
        assertFalse(petting.petting());
        assertEquals(1, sniffer.ends);
        // Interrupted: 600 to 1200 ticks.
        run(petting, warden, 600);
        assertFalse(petting.petting());
        run(petting, warden, 1);
        assertTrue(petting.petting());
    }

    @Test
    void itStopsWhenTheSnifferIsFarOrDeadOrTheWardenIsNoLongerCalm() {
        for (int reason = 0; reason < 3; reason++) {
            FakeSniffer sniffer = new FakeSniffer(8, 0);
            FakeWarden warden = new FakeWarden();
            warden.nearby.add(sniffer);
            WardenPetting petting = new WardenPetting(new FixedRandom(0));
            run(petting, warden, 801);
            assertTrue(petting.petting());
            switch (reason) {
                case 0 -> sniffer.position = new Vec(19, 64, 0);
                case 1 -> sniffer.alive = false;
                default -> warden.calm = false;
            }
            run(petting, warden, 1);
            assertFalse(petting.petting(), "reason " + reason);
            assertEquals(1, sniffer.ends);
        }
    }
}
