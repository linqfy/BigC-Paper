package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.EchoGolemBrain.Mission;
import dev.linqfy.bigCasares.modules.sift.EchoGolemBrain.Route;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EchoGolemBrainTest {

    private static final int SURFACE = 64;

    private static final class FakePlayer implements EchoGolemBrain.Player {
        final UUID id = UUID.randomUUID();
        String name = "Alex";
        SiftPoint at = new SiftPoint(0, SURFACE, 0);
        boolean inWorld = true;

        @Override public UUID id() { return id; }
        @Override public String name() { return name; }
        @Override public SiftPoint position() { return at; }
        @Override public boolean inWorld() { return inWorld; }
    }

    private static final class FakeSinger implements SoulExchange.Singer {
        final UUID id = UUID.randomUUID();
        SiftPos at = new SiftPos(30, SURFACE, 0);
        final List<SoulExchange.Golem> offers = new ArrayList<>();

        @Override public UUID id() { return id; }
        @Override public boolean isAlive() { return true; }
        @Override public boolean isSoulEvent() { return true; }
        @Override public SiftPos blockPosition() { return at; }
        @Override public SiftPoint position() { return at.bottomCenter(); }
        @Override public void offerSoulGolem(SoulExchange.Golem golem) { offers.add(golem); }
    }

    private static final class FakeNavigation implements EchoGolemBrain.Navigation {
        final List<SiftPos> goals = new ArrayList<>();
        boolean inProgress;
        int stops;

        private record Found(SiftPos end) implements EchoGolemBrain.Path {
            @Override public boolean canReach() { return true; }
        }

        @Override public EchoGolemBrain.Path createPath(SiftPos target, int reach) { return new Found(target); }

        @Override
        public EchoGolemBrain.Path createPath(Collection<SiftPos> targets, int reach) {
            return targets.isEmpty() ? null : new Found(targets.iterator().next());
        }

        @Override
        public boolean moveTo(EchoGolemBrain.Path path, double speed) {
            goals.add(path.end());
            inProgress = true;
            return true;
        }

        @Override public boolean isInProgress() { return inProgress; }
        @Override public boolean isDone() { return !inProgress; }
        @Override public Integer nextNodeY() { return null; }
        @Override public void stop() { inProgress = false; stops++; }
    }

    private static final class FakeEnv implements EchoGolemBrain.Env {
        final UUID uuid = UUID.randomUUID();
        final int entityId;
        final GolemShared shared;
        final SiftLandmarks landmarks;
        final FakeNavigation navigation = new FakeNavigation();
        final Set<SiftPos> soulBlocks = new HashSet<>();
        final List<String> log = new ArrayList<>();
        long time = 1000;
        double x;
        double y = SURFACE;
        double z;
        boolean onGround = true;
        FakeSinger singer;
        int singerRequests;
        FakePlayer player;
        EchoGolemBrain other;

        FakeEnv(int entityId, GolemShared shared, SiftLandmarks landmarks, double x, double z) {
            this.entityId = entityId;
            this.shared = shared;
            this.landmarks = landmarks;
            this.x = x;
            this.z = z;
        }

        void moveTo(SiftPos pos) {
            x = pos.x() + 0.5;
            y = pos.y();
            z = pos.z() + 0.5;
        }

        @Override public long gameTime() { return time; }
        @Override public int entityId() { return entityId; }
        @Override public UUID uuid() { return uuid; }
        @Override public double x() { return x; }
        @Override public double y() { return y; }
        @Override public double z() { return z; }
        @Override public boolean onGround() { return onGround; }
        @Override public boolean horizontalCollision() { return false; }
        @Override public EchoGolemBrain.Navigation navigation() { return navigation; }
        @Override public GolemShared shared() { return shared; }
        @Override public SiftLandmarks landmarks() { return landmarks; }
        @Override public boolean isLoaded(SiftPos pos) { return true; }
        @Override public boolean isSoulBlock(SiftPos pos) { return soulBlocks.contains(pos); }
        @Override public boolean canSeeSky(SiftPos pos) { return pos.y() > SURFACE; }
        @Override public int surfaceY(int bx, int bz) { return SURFACE; }
        @Override public boolean isChunkLoaded(int bx, int bz) { return true; }

        @Override
        public boolean handsCanReach(SiftPos soul) {
            return soulBlocks.contains(soul) && new SiftPoint(x, y, z).distanceSqr(soul.center()) <= 3.0625;
        }

        @Override public Optional<SiftPoint> randomPosTowards(SiftPoint target, int h, int v, double angle) { return Optional.empty(); }

        @Override
        public void removeSoulBlock(SiftPos pos) {
            soulBlocks.remove(pos);
            log.add("removed " + pos);
        }

        @Override public Optional<SiftPos> soulTargetOf(UUID golem) { return other == null ? Optional.empty() : Optional.ofNullable(other.soulTarget()); }
        @Override public SoulExchange.Singer singer(UUID id) { return singer != null && singer.id.equals(id) ? singer : null; }

        @Override
        public SoulExchange.Singer requestSinger(EchoGolemBrain golem) {
            singerRequests++;
            return singer;
        }

        @Override public EchoGolemBrain.Player player(UUID id) { return player != null && player.id.equals(id) ? player : null; }
        @Override public EchoGolemBrain.Player playerNamed(String name) { return player != null && player.name.equalsIgnoreCase(name) ? player : null; }
        @Override public void faceToward(SiftPoint point) { log.add("face"); }
        @Override public void hop(double velocity) { log.add("hop"); }
        @Override public void playItemGet(float pitch) { log.add("get " + pitch); }
        @Override public void playItemDrop(float pitch) { log.add("drop " + pitch); }
        @Override public void dropSoulBlock() { log.add("dropped"); }
        @Override public void giveSoulBlock(EchoGolemBrain.Player p) { log.add("gave " + p.name()); }
        @Override public void awardFriendOfTheSift(EchoGolemBrain.Player p) { log.add("friend " + p.name()); }
        @Override public void awardLikeFatherAndSon() { log.add("father and son"); }
        @Override public void keepLoaded() { log.add("keep"); }
    }

    private final GolemShared shared = new GolemShared();
    private final SiftLandmarks landmarks = new SiftLandmarks();

    private FakeEnv env(int id, double x, double z) {
        return new FakeEnv(id, shared, landmarks, x, z);
    }

    private static void run(EchoGolemBrain brain, FakeEnv env, int ticks) {
        for (int index = 0; index < ticks; index++) {
            env.time++;
            brain.tick();
        }
    }

    private static void runUntil(EchoGolemBrain brain, FakeEnv env, java.util.function.BooleanSupplier done, int limit) {
        for (int index = 0; index < limit && !done.getAsBoolean(); index++) {
            run(brain, env, 1);
        }
        assertTrue(done.getAsBoolean(), "not reached within " + limit + " ticks");
    }

    /** A golem standing at the surface beside a Soul Block, with the block recorded. */
    private FakeEnv golemBesideASoulBlock(SiftPos soul) {
        FakeEnv env = env(1, soul.x() - 0.5, soul.z() + 0.5);
        env.soulBlocks.add(soul);
        landmarks.record(SiftLandmarks.Kind.SOUL_BLOCK, soul);
        return env;
    }

    @Test
    void aGolemNextToASoulBlockPicksItUpAndCarriesItOut() {
        SiftPos soul = new SiftPos(11, SURFACE, 10);
        FakeEnv env = golemBesideASoulBlock(soul);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        runUntil(brain, env, () -> brain.mission() == Mission.PICKUP, 40);
        assertEquals(soul, brain.soulTarget());
        assertEquals(soul.asLong(), shared.reservations().keySet().iterator().next());
        assertFalse(brain.hasSoulBlock());

        run(brain, env, EchoGolemBrain.PICKUP_GRAB_TICK - brain.stateTicks() - 1);
        assertTrue(env.soulBlocks.contains(soul), "the block is still there on tick 17");
        run(brain, env, 1);
        assertTrue(brain.hasSoulBlock());
        assertFalse(env.soulBlocks.contains(soul), "taken on tick 18");
        assertTrue(env.log.contains("get 0.96"));
        assertTrue(env.log.contains("keep"));
        assertTrue(shared.reservations().isEmpty(), "the claim is released with the block");

        run(brain, env, EchoGolemBrain.PICKUP_TICKS - EchoGolemBrain.PICKUP_GRAB_TICK);
        assertEquals(Mission.CARRY, brain.mission());
        assertEquals(Route.DELIVER, brain.route(), "it is already on the surface");
    }

    @Test
    void aGolemThatCarriesAsksForASingerEveryThirtyTicksAndWalksToHer() {
        FakeEnv env = env(1, 10.5, 10.5);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        brain.restore(new EchoGolemBrain.Snapshot(Mission.CARRY.ordinal(), true, 0, Route.DELIVER.ordinal(), 0, null, null, null, null,
            null, null, 0, false, true, 0.0, null, null, null, null));
        run(brain, env, 5);
        assertEquals(1, env.singerRequests);
        env.singer = new FakeSinger();
        run(brain, env, EchoGolemBrain.SINGER_REQUEST_INTERVAL);
        assertTrue(env.singerRequests >= 2);
        assertFalse(env.singer.offers.isEmpty(), "it offers its soul to her");
        assertTrue(env.navigation.goals.stream().anyMatch(goal -> goal.distSqr(env.singer.at) <= 4.0 || goal.x() > 11),
            "and heads toward her: " + env.navigation.goals);
    }

    @Test
    void handingTheSoulToASingerAwardsFatherAndSonAndFreesTheGolem() {
        FakeEnv env = env(1, 10.5, 10.5);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        brain.restore(new EchoGolemBrain.Snapshot(Mission.CARRY.ordinal(), true, 0, Route.DELIVER.ordinal(), 0, null, null, null, null,
            null, null, 0, false, true, 0.0, null, null, null, null));
        brain.beginBow();
        assertEquals(Mission.BOW, brain.mission());
        assertTrue(brain.transferSoulToSinger());
        assertFalse(brain.hasSoulBlock());
        assertTrue(env.log.contains("father and son"));
        assertTrue(env.log.contains("drop 0.92"));
        assertEquals(Mission.BOW, brain.mission(), "it keeps bowing until the Singer finishes");
        brain.finishBow();
        assertEquals(Mission.SEARCH, brain.mission());
        assertFalse(brain.transferSoulToSinger(), "nothing left to give");
    }

    @Test
    void aBowToASingerEndsByItselfAfterFiveSeconds() {
        FakeEnv env = env(1, 10.5, 10.5);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        brain.restore(new EchoGolemBrain.Snapshot(Mission.CARRY.ordinal(), true, 0, Route.DELIVER.ordinal(), 0, null, null, null, null,
            null, null, 0, false, true, 0.0, null, null, null, null));
        brain.beginBow();
        run(brain, env, EchoGolemBrain.SINGER_BOW_TICKS - 1);
        assertEquals(Mission.BOW, brain.mission());
        run(brain, env, 1);
        assertEquals(Mission.CARRY, brain.mission(), "still carrying: back to delivering");
    }

    @Test
    void aGolemThatLosesItsSoulDropsItAndSitsForThirtySeconds() {
        FakeEnv env = env(1, 10.5, 10.5);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        brain.restore(new EchoGolemBrain.Snapshot(Mission.CARRY.ordinal(), true, 0, Route.DELIVER.ordinal(), 0, null, null, null, null,
            null, null, 0, false, true, 0.0, null, null, null, null));
        assertTrue(brain.releaseCarriedSoul());
        assertFalse(brain.releaseCarriedSoul());
        assertTrue(env.log.contains("dropped"));
        assertTrue(env.log.contains("drop 1.0"));
        assertEquals(Mission.SIT, brain.mission());
        run(brain, env, EchoGolemBrain.SIT_TICKS - 1);
        assertEquals(Mission.SIT, brain.mission());
        run(brain, env, 1);
        assertEquals(Mission.SEARCH, brain.mission());
    }

    @Test
    void twoGolemsDoNotGoForTheSameSoulBlock() {
        SiftPos soul = new SiftPos(11, SURFACE, 10);
        FakeEnv first = golemBesideASoulBlock(soul);
        FakeEnv second = env(2, 10.5, 10.5);
        second.soulBlocks.add(soul);
        EchoGolemBrain firstBrain = new EchoGolemBrain(first, new Random(3));
        EchoGolemBrain secondBrain = new EchoGolemBrain(second, new Random(4));
        second.other = firstBrain;
        runUntil(firstBrain, first, () -> firstBrain.soulTarget() != null, 40);
        assertEquals(soul, firstBrain.soulTarget());
        run(secondBrain, second, 40);
        assertNull(secondBrain.soulTarget(), "the block is claimed by the first golem");
        assertEquals(Mission.SEARCH, secondBrain.mission());

        firstBrain.release();
        runUntil(secondBrain, second, () -> secondBrain.soulTarget() != null, 40);
        assertEquals(soul, secondBrain.soulTarget(), "once the claim is released the second golem takes it");
    }

    @Test
    void aGolemThatSeesTheBlockGoneChoosesAgain() {
        SiftPos soul = new SiftPos(11, SURFACE, 10);
        FakeEnv env = golemBesideASoulBlock(soul);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        runUntil(brain, env, () -> brain.mission() == Mission.PICKUP, 40);
        env.soulBlocks.clear();
        run(brain, env, EchoGolemBrain.PICKUP_GRAB_TICK);
        assertEquals(Mission.SEARCH, brain.mission());
        assertFalse(brain.hasSoulBlock());
        assertNull(brain.soulTarget());
    }

    @Test
    void anEchoShardBondsTheGolemToOnePlayer() {
        FakeEnv env = env(1, 10.5, 10.5);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        assertTrue(brain.canInteractWithSinger());
        assertTrue(brain.isBondedTo(UUID.randomUUID(), "Anyone"), "an unbonded golem answers to everybody");
        UUID owner = UUID.randomUUID();
        assertTrue(brain.bondTo(owner, "Alex"));
        assertFalse(brain.bondTo(UUID.randomUUID(), "Sam"));
        assertTrue(brain.isBonded());
        assertFalse(brain.canInteractWithSinger());
        assertTrue(brain.isBondedTo(owner, "Alex"));
        assertFalse(brain.isBondedTo(UUID.randomUUID(), "Sam"));
        assertTrue(env.log.contains("keep"));
        // A renamed account is found by name and bound to the new id.
        UUID renamed = UUID.randomUUID();
        assertTrue(brain.isBondedTo(renamed, "ALEX"));
        assertTrue(brain.isBondedTo(renamed, "Alex"));
    }

    @Test
    void aBondedGolemBringsTheSoulToItsPlayerBowsAndHandsItOver() {
        FakeEnv env = env(1, 10.5, 10.5);
        FakePlayer alex = new FakePlayer();
        alex.at = new SiftPoint(12.5, SURFACE, 10.5);
        env.player = alex;
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        brain.restore(new EchoGolemBrain.Snapshot(Mission.CARRY.ordinal(), true, 0, Route.DELIVER.ordinal(), 0, null, null, null, null,
            null, null, 0, false, true, 0.0, null, alex.id, "Alex", null));
        run(brain, env, 2);
        assertEquals(Mission.BOW, brain.mission(), "the player is within three blocks");
        assertTrue(brain.hasSoulBlock());
        run(brain, env, EchoGolemBrain.PLAYER_HANDOVER_TICK - 1 - brain.stateTicks());
        assertTrue(brain.hasSoulBlock());
        run(brain, env, 1);
        assertFalse(brain.hasSoulBlock());
        assertTrue(env.log.contains("gave Alex"));
        assertTrue(env.log.contains("friend Alex"));
        assertTrue(env.log.contains("drop 1.04"));
        run(brain, env, EchoGolemBrain.PLAYER_BOW_TICKS - EchoGolemBrain.PLAYER_HANDOVER_TICK);
        assertEquals(Mission.SEARCH, brain.mission());
    }

    @Test
    void aBondedGolemWalksToAPlayerWhoIsFarAndWaitsNearItsCanyonWhenTheyAreOffline() {
        FakeEnv env = env(1, 10.5, 10.5);
        FakePlayer alex = new FakePlayer();
        alex.at = new SiftPoint(50.5, SURFACE, 10.5);
        env.player = alex;
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        brain.restore(new EchoGolemBrain.Snapshot(Mission.CARRY.ordinal(), true, 0, Route.DELIVER.ordinal(), 0, null, null, null, null,
            null, null, 0, false, true, 0.0, null, alex.id, "Alex", null));
        run(brain, env, 40);
        assertEquals(Mission.CARRY, brain.mission());
        assertFalse(env.navigation.goals.isEmpty());
        assertTrue(env.navigation.goals.get(0).x() >= 45, "toward the player: " + env.navigation.goals);

        env.player = null;
        env.navigation.goals.clear();
        env.navigation.inProgress = false;
        run(brain, env, 120);
        assertEquals(Mission.CARRY, brain.mission(), "it keeps the soul and patrols");
        assertFalse(env.navigation.goals.isEmpty());
        assertEquals(0, env.singerRequests, "a bonded golem never asks for a Singer");
    }

    @Test
    void aBondedGolemLoadedWithASoulGoesBackToCarrying() {
        FakeEnv env = env(1, 10.5, 10.5);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        brain.restore(new EchoGolemBrain.Snapshot(Mission.SEARCH.ordinal(), true, 55, Route.EXPLORE.ordinal(), 0, null, null,
            new SiftPos(0, 60, 0).asLong(), null, null, null, 0, true, true, 0.0, UUID.randomUUID(), UUID.randomUUID(), "Alex", null));
        assertEquals(Mission.CARRY, brain.mission());
        assertEquals(Route.EXIT_CANYON, brain.route());
        assertNull(brain.snapshot().singerTarget(), "a bonded golem has forgotten its Singer");
        assertTrue(brain.isBonded());
    }

    @Test
    void theSavedStateComesBackAsItWas() {
        SiftPos soul = new SiftPos(11, SURFACE, 10);
        FakeEnv env = golemBesideASoulBlock(soul);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        runUntil(brain, env, () -> brain.mission() == Mission.PICKUP, 40);
        EchoGolemBrain.Snapshot saved = brain.snapshot();
        assertEquals(Mission.PICKUP.ordinal(), saved.mission());
        assertEquals(soul.asLong(), saved.soulTarget());

        FakeEnv otherEnv = env(2, 10.5, 10.5);
        EchoGolemBrain restored = new EchoGolemBrain(otherEnv, new Random(8));
        restored.restore(saved);
        EchoGolemBrain.Snapshot again = restored.snapshot();
        assertEquals(saved.mission(), again.mission());
        assertEquals(saved.soulTarget(), again.soulTarget());
        assertEquals(saved.route(), again.route());
        assertEquals(saved.stateTicks(), again.stateTicks());
        assertEquals(saved.hasSoul(), again.hasSoul());
    }

    @Test
    void aSavedGolemWithoutARouteGuessesFromItsCanyon() {
        FakeEnv env = env(1, 10.5, 10.5);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        SiftPos cluster = new SiftPos(40, 40, 0);
        SiftPos entrance = new SiftPos(20, 62, 0);
        brain.restore(new EchoGolemBrain.Snapshot(Mission.SEARCH.ordinal(), false, 0, null, 0, null, null, entrance.asLong(), null,
            cluster.asLong(), null, 0, false, null, 0.0, null, null, null, null));
        assertEquals(Route.APPROACH_CANYON, brain.route());
        brain.restore(new EchoGolemBrain.Snapshot(Mission.SEARCH.ordinal(), false, 0, null, 0, null, null, entrance.asLong(), null,
            cluster.asLong(), null, 0, true, null, 0.0, null, null, null, null));
        assertEquals(Route.SEEK_SOUL, brain.route());
    }

    @Test
    void aGolemFindsACanyonAndWalksItsRouteDownToTheSouls() {
        SiftPos entrance = new SiftPos(60, 60, 0);
        SiftPos midpoint = new SiftPos(75, 50, 0);
        SiftPos cluster = new SiftPos(90, 45, 0);
        SiftPos soul = new SiftPos(90, 44, 1);
        landmarks.record(SiftLandmarks.Kind.CANYON_ENTRANCE, entrance);
        landmarks.record(SiftLandmarks.Kind.CANYON_MIDPOINT, midpoint);
        landmarks.record(SiftLandmarks.Kind.CANYON_CLUSTER, cluster);
        landmarks.record(SiftLandmarks.Kind.SOUL_BLOCK, soul);
        FakeEnv env = env(1, 20.5, 0.5);
        env.soulBlocks.add(soul);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));

        run(brain, env, 60);
        assertEquals(Route.APPROACH_CANYON, brain.route());
        assertEquals(cluster, brain.canyonCluster());
        assertEquals(entrance, brain.canyonEntrance());

        // The approach point is seven blocks outside the entrance, away from the cluster; the golem walks there first
        // and, once within four blocks of it, straight on to the entrance.
        env.navigation.goals.clear();
        env.moveTo(new SiftPos(52, SURFACE, 0));
        env.navigation.inProgress = false;
        run(brain, env, 30);
        assertTrue(env.navigation.goals.contains(entrance), "heads for the entrance: " + env.navigation.goals);
        env.moveTo(new SiftPos(57, SURFACE, 0));
        run(brain, env, 1);
        assertEquals(Route.ENTER_CANYON, brain.route());
        run(brain, env, 1);
        assertEquals(Route.DESCEND_CANYON, brain.route(), "within 3.5 blocks of the entrance and 4 of its height");
        env.moveTo(midpoint);
        run(brain, env, 2);
        assertEquals(Route.SEEK_SOUL, brain.route());
        runUntil(brain, env, () -> brain.soulTarget() != null, 30);
        assertEquals(soul, brain.soulTarget(), "the Soul Block is within the scan range from the canyon");
    }

    @Test
    void aCanyonWithoutSoulBlocksIsNotAGoal() {
        landmarks.record(SiftLandmarks.Kind.CANYON_ENTRANCE, new SiftPos(60, 60, 0));
        landmarks.record(SiftLandmarks.Kind.CANYON_CLUSTER, new SiftPos(90, 45, 0));
        landmarks.record(SiftLandmarks.Kind.SOUL_BLOCK, new SiftPos(90, 44, 1));
        FakeEnv env = env(1, 20.5, 0.5);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        run(brain, env, 120);
        assertEquals(Route.EXPLORE, brain.route(), "the recorded block is no longer in the world");
        assertNull(brain.canyonCluster());
    }

    @Test
    void aCanyonFarAwayOnlyTurnsTheExplorationTowardIt() {
        landmarks.record(SiftLandmarks.Kind.CANYON_ENTRANCE, new SiftPos(500, 60, 0));
        landmarks.record(SiftLandmarks.Kind.CANYON_CLUSTER, new SiftPos(530, 45, 0));
        landmarks.record(SiftLandmarks.Kind.SOUL_BLOCK, new SiftPos(530, 44, 1));
        FakeEnv env = env(1, 20.5, 0.5);
        env.soulBlocks.add(new SiftPos(530, 44, 1));
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        for (int index = 0; index < 60; index++) {
            env.x += 0.3;
            run(brain, env, 1);
        }
        assertEquals(Route.EXPLORE, brain.route(), "beyond 96 blocks it explores toward the canyon first");
        assertNotNull(brain.snapshot());
        assertTrue(Math.cos(brain.snapshot().explorationHeading()) > 0.9, "heading " + brain.snapshot().explorationHeading());
    }

    @Test
    void theSearchRingsOutwardAndFindsBlocksNobodyRecorded() {
        SiftPos soul = new SiftPos(14, SURFACE, 10);
        FakeEnv env = env(1, 10.5, 10.5);
        env.soulBlocks.add(soul);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        runUntil(brain, env, () -> brain.soulTarget() != null, 100);
        assertEquals(soul, brain.soulTarget(), "found by the physical scan within 12 blocks");
        assertTrue(brain.route() == Route.SEEK_SOUL);
        assertNotEquals(Mission.CARRY, brain.mission());
    }

    @Test
    void theSharedPathBudgetAllowsOneSearchPerTick() {
        PathBudget budget = new PathBudget();
        assertTrue(budget.tryAcquire(5));
        assertFalse(budget.tryAcquire(5));
        assertTrue(budget.tryAcquireCriticalExit(5), "exits have a budget of their own");
        assertFalse(budget.tryAcquireCriticalExit(5));
        assertTrue(budget.tryAcquire(6));
        budget.clear();
        assertTrue(budget.tryAcquire(6));
    }

    @Test
    void aGolemGivesWayWhenAnotherTookTheTicksSearch() {
        FakeEnv env = env(3, 10.5, 10.5);
        EchoGolemBrain brain = new EchoGolemBrain(env, new Random(3));
        env.time = 5000;
        assertTrue(shared.budget().tryAcquire(env.time));
        env.time += 0;
        // Its own pause may still run; once past it the shared budget refuses and sets a short retry.
        long start = env.time;
        boolean acquired = false;
        for (int index = 0; index < 8 && !acquired; index++) {
            env.time = start + index;
            if (index > 0) {
                shared.budget().tryAcquire(env.time);
            }
            acquired = brain.tryAcquirePathBudget(4);
        }
        assertFalse(acquired, "the budget is spent every one of those ticks");
    }
}
