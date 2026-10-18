package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.DarkSnifferBrain.Combatant;
import dev.linqfy.bigCasares.modules.sift.DarkSnifferBrain.Pose;
import dev.linqfy.bigCasares.modules.sift.SiftGeometry.Aabb;
import dev.linqfy.bigCasares.modules.sift.SiftGeometry.Vec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DarkSnifferBrainTest {

    /** Every bounded draw is {@code value} (capped below the bound). */
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

    private record Mob(UUID id, boolean alive, Vec position, Vec eyePosition, Vec velocity, Aabb box) implements Combatant {
    }

    private static Mob mob(double x, double y, double z) {
        return new Mob(UUID.randomUUID(), true, new Vec(x, y, z), new Vec(x, y + 1.62, z), Vec.ZERO,
            new Aabb(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3));
    }

    private static Mob moving(Mob mob, Vec velocity) {
        return new Mob(mob.id(), mob.alive(), mob.position(), mob.eyePosition(), velocity, mob.box());
    }

    private static Mob dead(Mob mob) {
        return new Mob(mob.id(), false, mob.position(), mob.eyePosition(), mob.velocity(), mob.box());
    }

    private static final class FakeBody implements DarkSnifferBrain.Body {
        boolean peaceful;
        Vec position = new Vec(0, 64, 0);
        Combatant target;
        Combatant nearestPlayer;
        Combatant nearestSniffer;
        final Set<UUID> attackable = new HashSet<>();
        boolean lineOfSight = true;
        boolean navigationDone = true;
        boolean blocked;
        boolean attackHurts = true;
        Pose pose = Pose.IDLING;
        double speed;
        boolean aggressive;
        List<Combatant> living = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        final Map<UUID, Vec> pulls = new HashMap<>();
        Vec home;
        Vec launchStart;
        Vec launchDirection;
        int launchTicks;
        final List<Vec> waveHeads = new ArrayList<>();
        int attacks;
        int stops;

        @Override
        public boolean peaceful() {
            return peaceful;
        }

        @Override
        public Vec position() {
            return position;
        }

        @Override
        public Vec eyePosition() {
            return position.plus(new Vec(0, 1.05, 0));
        }

        @Override
        public Combatant target() {
            return target;
        }

        @Override
        public void target(Combatant target) {
            this.target = target;
        }

        @Override
        public Combatant nearestPlayer() {
            return nearestPlayer;
        }

        @Override
        public Combatant nearestOrdinarySniffer() {
            return nearestSniffer;
        }

        @Override
        public boolean canAttack(Combatant candidate) {
            return !peaceful && attackable.contains(candidate.id());
        }

        @Override
        public boolean hasLineOfSight(Combatant candidate) {
            return lineOfSight;
        }

        @Override
        public boolean navigationDone() {
            return navigationDone;
        }

        @Override
        public void moveTo(Vec destination, double pathSpeed) {
            log.add("moveTo " + destination.x() + "," + destination.z() + " @" + pathSpeed);
        }

        @Override
        public void moveTo(Combatant target, double pathSpeed) {
            log.add("chase @" + pathSpeed);
        }

        @Override
        public void stopNavigation() {
            stops++;
        }

        @Override
        public void stopMoving() {
            log.add("stopMoving");
        }

        @Override
        public void lookAt(Combatant target, float horizontalSpeed, float verticalSpeed) {
            log.add("look " + horizontalSpeed);
        }

        @Override
        public void movementSpeed(double speed) {
            this.speed = speed;
        }

        @Override
        public void aggressive(boolean aggressive) {
            this.aggressive = aggressive;
        }

        @Override
        public boolean blockedOnGround() {
            return blocked;
        }

        @Override
        public void jump() {
            log.add("jump");
        }

        @Override
        public boolean attack(Combatant target) {
            attacks++;
            return attackHurts;
        }

        @Override
        public void pose(Pose pose) {
            this.pose = pose;
        }

        @Override
        public void sonicLaunched(Vec start, Vec direction, int flightTicks) {
            launchStart = start;
            launchDirection = direction;
            launchTicks = flightTicks;
        }

        @Override
        public void sonicMoved(Vec head) {
            waveHeads.add(head);
        }

        @Override
        public List<Combatant> livingIn(Aabb area) {
            return living.stream().filter(mob -> mob.box().overlaps(area)).toList();
        }

        @Override
        public void pull(Combatant victim, Vec push) {
            pulls.put(victim.id(), push);
        }

        @Override
        public void homeSet(Vec home) {
            this.home = home;
        }

        long count(String prefix) {
            return log.stream().filter(line -> line.startsWith(prefix)).count();
        }
    }

    private static DarkSnifferBrain brain() {
        return new DarkSnifferBrain(new FixedRandom(0));
    }

    private static Mob prey(FakeBody body, double x, double y, double z) {
        Mob mob = mob(x, y, z);
        body.attackable.add(mob.id());
        body.nearestPlayer = mob;
        return mob;
    }

    private static void ticks(DarkSnifferBrain brain, FakeBody body, int count) {
        for (int tick = 0; tick < count; tick++) {
            brain.tick(body);
        }
    }

    @Test
    void theFirstTickRemembersTheHomeAndItIsReportedOnce() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        body.position = new Vec(10, 70, -4);
        brain.tick(body);
        assertEquals(new Vec(10, 70, -4), body.home);
        body.home = null;
        body.position = new Vec(20, 70, 0);
        brain.tick(body);
        assertNull(body.home, "the home is only set once");
        assertEquals(new Vec(10, 70, -4), brain.home());
    }

    @Test
    void aRestoredHomeIsKept() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        brain.home(new Vec(1, 2, 3));
        brain.tick(body);
        assertNull(body.home);
        assertEquals(new Vec(1, 2, 3), brain.home());
    }

    @Test
    void withoutATargetItWandersSlowlyWithinTwelveBlocksOfItself() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = new DarkSnifferBrain(new FixedRandom(0));
        body.aggressive = true;
        body.speed = 0.3;
        brain.tick(body);
        assertEquals(DarkSnifferBrain.NORMAL_SPEED, body.speed);
        assertFalse(body.aggressive);
        // A draw of 0 out of 30 starts a walk; each coordinate is nextInt(25) - 12 from where it stands.
        assertEquals(List.of("moveTo -12.0,-12.0 @0.1"), body.log);
    }

    @Test
    void itOnlyWandersWhenItsPathIsDoneAndOnOneTickInThirty() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain moving = brain();
        body.navigationDone = false;
        ticks(moving, body, 5);
        assertTrue(body.log.isEmpty());
        DarkSnifferBrain idle = new DarkSnifferBrain(new FixedRandom(7));
        body.navigationDone = true;
        ticks(idle, body, 5);
        assertTrue(body.log.isEmpty(), "a draw other than 0 does not start a walk");
    }

    @Test
    void farFromHomeItWalksBackAtTheWanderSpeed() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        brain.home(new Vec(0, 64, 0));
        body.position = new Vec(101, 64, 0);
        brain.tick(body);
        assertEquals(List.of("moveTo 0.0,0.0 @0.1"), body.log);
        body.log.clear();
        body.position = new Vec(99, 64, 0);
        brain.tick(body);
        assertEquals(List.of("moveTo " + (99.0 - 12.0) + "," + -12.0 + " @0.1"), body.log, "inside 100 blocks it wanders again");
    }

    @Test
    void thePlayerAndTheNearerOrdinarySnifferAreChosenEveryTenTicks() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        Mob player = prey(body, 10, 64, 0);
        Mob sniffer = mob(4, 64, 0);
        body.attackable.add(sniffer.id());
        body.nearestSniffer = sniffer;
        brain.tick(body);
        assertEquals(sniffer.id(), body.target.id(), "the sniffer is nearer");
        body.nearestSniffer = mob(40, 64, 0);
        body.attackable.add(body.nearestSniffer.id());
        ticks(brain, body, 9);
        assertEquals(sniffer.id(), body.target.id(), "no new choice within ten ticks");
        brain.tick(body);
        assertEquals(player.id(), body.target.id(), "the farther sniffer loses to the player");
    }

    @Test
    void aTargetThatIsNoLongerValidIsDroppedAtTheStartOfTheTick() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        Mob player = prey(body, 6, 64, 0);
        brain.tick(body);
        assertEquals(player.id(), body.target.id());
        body.attackable.clear();
        body.nearestPlayer = null;
        brain.tick(body);
        assertNull(body.target, "not attackable any more");
        body.attackable.add(player.id());
        body.target = dead(player);
        brain.tick(body);
        assertNull(body.target, "dead");
        body.target = mob(50, 64, 0);
        body.attackable.add(body.target.id());
        brain.tick(body);
        assertNull(body.target, "beyond 32 blocks");
    }

    @Test
    void huntingRunsAtTheCombatSpeedAndRefreshesItsPathEverySixTicks() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        body.navigationDone = false;
        // Out of sight, so that the sonic sniff does not interrupt the chase.
        body.lineOfSight = false;
        prey(body, 20, 64, 0);
        ticks(brain, body, 1);
        assertEquals(DarkSnifferBrain.COMBAT_SPEED, body.speed);
        assertTrue(body.aggressive);
        assertEquals(Pose.IDLING, body.pose);
        assertEquals(1, body.count("chase @1.2"));
        ticks(brain, body, 12);
        assertEquals(3, body.count("chase @1.2"), "ticks 1, 7 and 13");
        body.navigationDone = true;
        brain.tick(body);
        assertEquals(4, body.count("chase @1.2"), "a finished path is renewed at once");
    }

    @Test
    void itBitesEveryTwentyFourTicksFromThreePointOneFiveBlocks() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        prey(body, 3.1, 64, 0);
        brain.tick(body);
        assertEquals(1, body.attacks);
        assertEquals(DarkSnifferBrain.ATTACK_COOLDOWN_TICKS, brain.attackCooldown());
        ticks(brain, body, 23);
        assertEquals(1, body.attacks, "still cooling down");
        brain.tick(body);
        assertEquals(2, body.attacks);
    }

    @Test
    void aBiteThatDoesNotHurtDoesNotStartTheCooldown() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        body.attackHurts = false;
        prey(body, 2, 64, 0);
        ticks(brain, body, 3);
        assertEquals(3, body.attacks);
        assertEquals(0, brain.attackCooldown());
    }

    @Test
    void outOfReachItDoesNotBite() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        prey(body, 3.2, 64, 0);
        ticks(brain, body, 5);
        assertEquals(0, body.attacks);
    }

    @Test
    void aBlockedSnifferJumpsOnlyForATargetWithinThreeBlocksUpOrDown() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        body.blocked = true;
        prey(body, 10, 67, 0);
        brain.tick(body);
        assertEquals(1, body.count("jump"));

        FakeBody high = new FakeBody();
        DarkSnifferBrain other = brain();
        high.blocked = true;
        prey(high, 10, 67.5, 0);
        ticks(other, high, 5);
        assertEquals(0, high.count("jump"));
    }

    @Test
    void aTargetBeyondFiveBlocksInSightTriggersTheSonicSniffAfterEightTicksOutOfReach() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        prey(body, 12, 64, 0);
        ticks(brain, body, 7);
        assertEquals(Pose.IDLING, body.pose, "seven ticks out of reach is not enough");
        assertFalse(brain.sonicActive());
        brain.tick(body);
        assertTrue(brain.sonicActive());
        assertEquals(Pose.SNIFFING, body.pose);
        assertEquals(DarkSnifferBrain.SONIC_COOLDOWN_TICKS, brain.sonicCooldown());
    }

    @Test
    void noSonicSniffWithoutLineOfSightUnderFiveBlocksOrDuringTheCooldown() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        body.lineOfSight = false;
        prey(body, 12, 64, 0);
        ticks(brain, body, 20);
        assertFalse(brain.sonicActive(), "needs line of sight");
        FakeBody near = new FakeBody();
        DarkSnifferBrain closeBrain = brain();
        prey(near, 4.9, 64, 0);
        ticks(closeBrain, near, 20);
        assertFalse(closeBrain.sonicActive(), "needs five blocks");

        FakeBody again = new FakeBody();
        DarkSnifferBrain repeating = brain();
        prey(again, 12, 64, 0);
        ticks(repeating, again, 8);
        assertTrue(repeating.sonicActive());
        // Wind-up of 10, flight of ceil((distance + 7.5) / 1.5), then the 100-tick cooldown is still running.
        ticks(repeating, again, 10);
        ticks(repeating, again, again.launchTicks);
        assertFalse(repeating.sonicActive());
        ticks(repeating, again, 10);
        assertFalse(repeating.sonicActive(), "the cooldown has not run out");
    }

    @Test
    void theSonicSniffStandsStillWindsUpForTenTicksAndLaunchesTheWave() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        Mob victim = prey(body, 12, 64, 0);
        ticks(brain, body, 8);
        body.log.clear();
        ticks(brain, body, 9);
        assertNull(body.launchStart, "still winding up");
        assertEquals(9, body.count("stopMoving"));
        brain.tick(body);
        Vec eye = body.eyePosition();
        assertEquals(eye, body.launchStart);
        Vec toVictim = victim.eyePosition().minus(eye);
        double distance = toVictim.length();
        assertEquals(toVictim.x() / distance, body.launchDirection.x(), 1.0E-9);
        assertEquals(toVictim.y() / distance, body.launchDirection.y(), 1.0E-9);
        assertEquals((int) Math.ceil((distance + 7.5) / 1.5), body.launchTicks);
    }

    @Test
    void theWaveLeadsAMovingVictimByItsHorizontalVelocity() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        prey(body, 12, 64, 0);
        ticks(brain, body, 18);
        Vec straight = body.launchDirection;
        assertEquals(0.0, straight.z(), 1.0E-9);

        FakeBody walking = new FakeBody();
        DarkSnifferBrain other = brain();
        Mob runner = moving(prey(walking, 12, 64, 0), new Vec(0, 5, 0.1));
        walking.nearestPlayer = runner;
        ticks(other, walking, 18);
        // The lead is velocity * min(20, distance / 1.5) ticks, horizontal only.
        double lead = Math.min(20.0, runner.eyePosition().minus(walking.eyePosition()).length() / 1.5);
        Vec predicted = runner.eyePosition().plus(new Vec(0, 0, 0.1 * lead));
        Vec expected = predicted.minus(walking.eyePosition());
        assertEquals(expected.z() / expected.length(), walking.launchDirection.z(), 1.0E-9);
        assertEquals(expected.y() / expected.length(), walking.launchDirection.y(), 1.0E-9);
        assertTrue(walking.launchDirection.z() > 0.0);
    }

    @Test
    void noWaveWhenTheVictimLeavesSightDuringTheWindUp() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        prey(body, 12, 64, 0);
        ticks(brain, body, 8 + 9);
        body.lineOfSight = false;
        brain.tick(body);
        assertNull(body.launchStart);
        assertFalse(brain.sonicActive());
        assertEquals(Pose.IDLING, body.pose);
        // Started with 100 on the eighth tick, and it has run for ten more.
        assertEquals(90, brain.sonicCooldown(), "the cooldown keeps running");
    }

    @Test
    void theWavePullsEachCreatureItPassesOnceByThreeTenthsOfTheWayToTheSniffer() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        Mob victim = prey(body, 12, 64, 0);
        Mob bystander = mob(6, 64, 0.5);
        Mob aside = mob(6, 64, 8);
        body.living = List.of(victim, bystander, aside);
        ticks(brain, body, 18);
        assertTrue(brain.sonicActive());
        ticks(brain, body, body.launchTicks);
        assertFalse(brain.sonicActive());
        assertEquals(body.launchTicks, body.waveHeads.size());
        assertEquals(new Vec(-12 * 0.3, 0, 0), body.pulls.get(victim.id()));
        assertEquals(new Vec(-6 * 0.3, 0, -0.5 * 0.3), body.pulls.get(bystander.id()));
        assertFalse(body.pulls.containsKey(aside.id()), "outside the 0.8 block radius");
        assertEquals(Pose.IDLING, body.pose);
    }

    @Test
    void theWaveStepsAtOneAndAHalfBlocksPerTick() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        prey(body, 12, 64, 0);
        ticks(brain, body, 18);
        ticks(brain, body, 3);
        Vec start = body.launchStart;
        for (int step = 0; step < 3; step++) {
            Vec head = body.waveHeads.get(step);
            assertEquals(1.5 * (step + 1), head.minus(start).length(), 1.0E-9);
        }
    }

    @Test
    void theSonicSniffEndsWhenTheVictimDiesOrCannotBeAttacked() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        Mob victim = prey(body, 12, 64, 0);
        ticks(brain, body, 8 + 3);
        assertTrue(brain.sonicActive());
        body.attackable.remove(victim.id());
        brain.tick(body);
        assertFalse(brain.sonicActive());
        assertEquals(Pose.IDLING, body.pose);
    }

    @Test
    void peacefulMakesItDropEverything() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        prey(body, 12, 64, 0);
        ticks(brain, body, 8 + 3);
        body.peaceful = true;
        body.aggressive = true;
        brain.tick(body);
        assertFalse(brain.sonicActive());
        assertNull(body.target);
        assertFalse(body.aggressive);
        long chases = body.count("chase");
        ticks(brain, body, 5);
        assertEquals(chases, body.count("chase"));
    }

    @Test
    void aWardenPettingKeepsItFromHuntingUntilItEnds() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        Mob warden = mob(3, 64, 0);
        prey(body, 12, 64, 0);
        brain.tick(body);
        assertFalse(brain.availableForWardenPetting(body, true), "it is hunting");
        brain.beginWardenPetting(body, warden, 260);
        assertNull(body.target);
        assertFalse(brain.availableForWardenPetting(body, true));
        body.log.clear();
        ticks(brain, body, 30);
        assertNull(body.target, "players are ignored while it is petted");
        assertEquals(30, body.count("look 20.0"));
        assertEquals(Pose.IDLING, body.pose);
        brain.endWardenPetting(warden);
        assertEquals(0, brain.wardenPettingTicks());
        ticks(brain, body, 12);
        assertTrue(body.target != null, "hunting again");
    }

    @Test
    void aWardenThatLeavesEndsThePettingByItself() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        Mob far = mob(30, 64, 0);
        brain.beginWardenPetting(body, far, 100);
        brain.tick(body);
        assertEquals(0, brain.wardenPettingTicks(), "more than 24 blocks away");
        Mob warden = mob(5, 64, 0);
        brain.beginWardenPetting(body, warden, 3);
        ticks(brain, body, 3);
        assertEquals(0, brain.wardenPettingTicks());
        brain.beginWardenPetting(body, warden, 3);
        brain.beginWardenPetting(body, warden, 2);
        assertEquals(3, brain.wardenPettingTicks(), "a shorter hold does not shorten it");
    }

    @Test
    void anotherWardenCannotEndThePetting() {
        FakeBody body = new FakeBody();
        DarkSnifferBrain brain = brain();
        brain.beginWardenPetting(body, mob(3, 64, 0), 50);
        brain.endWardenPetting(mob(3, 64, 0));
        assertEquals(50, brain.wardenPettingTicks());
    }
}
