package dev.linqfy.bigCasares.modules.sift;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The mind of an Echo Golem, the Bukkit-free part of the mod's {@code EchoGolemEntity}. A golem
 * <em>searches</em> for Soul Blocks, <em>picks one up</em>, <em>carries</em> it out of the canyon and delivers it
 * to a Singer (or, once a player has bonded with it, to that player), <em>bows</em> to the receiver and
 * <em>sits</em> for 30 seconds when it loses its soul. {@link #tick} advances one server tick; everything that
 * touches the world goes through {@link Env}, and walking goes through {@link Navigation}.
 *
 * <p>Two state machines run side by side, as in the mod: the {@link Mission} (what the golem is doing) and,
 * during {@link Mission#SEARCH} and {@link Mission#CARRY}, the {@link Route} (where it is on its way). Searching
 * explores in a slowly turning direction until a canyon with Soul Blocks is within reach of the landmark
 * registry, walks to the canyon's approach point and entrance, descends to its midpoint, looks for a Soul Block
 * it can reach and leaves again when the canyon is empty. Carrying walks back to the entrance, then to a Singer
 * (asking {@link Env#requestSinger} for one every 30 ticks) or, when bonded, to the player. Watchdogs notice a
 * golem that has stopped moving and turn it around.</p>
 *
 * <p>Differences from the mod's navigation, which asks Minecraft's pathfinder for sets of goals and for
 * prepared paths: {@link Navigation} offers Paper's single-goal searches, so a search that the mod runs
 * once over a set of goals ({@link #findReachableSoulApproach}, the three-wide exit ramp, the surface exits)
 * tries the goals one after the other, nearest first, and the prepared exit path is not kept between ticks.
 * Whether a path is stuck is not asked of the pathfinder, only of the watchdogs. Everything else, including
 * the per-tick path budget, the retry timers and the rejection memories, is the mod's.</p>
 */
final class EchoGolemBrain implements SoulExchange.Golem {

    /** The mod's {@code MissionState}; the ordinal is what the mod saves. */
    enum Mission {
        SEARCH,
        PICKUP,
        CARRY,
        BOW,
        SIT
    }

    /** The mod's {@code RoutePhase}; the ordinal is what the mod saves. */
    enum Route {
        EXPLORE,
        APPROACH_CANYON,
        ENTER_CANYON,
        SEEK_SOUL,
        LEAVE_EMPTY_CANYON,
        EXIT_CANYON,
        DELIVER,
        DESCEND_CANYON
    }

    static final int PICKUP_GRAB_TICK = 18;
    static final int PICKUP_TICKS = 34;
    static final int SINGER_BOW_TICKS = 100;
    static final int PLAYER_BOW_TICKS = 48;
    static final int PLAYER_HANDOVER_TICK = 26;
    static final int SIT_TICKS = 600;
    static final int FORGET_CANYON_TICKS = 600;
    static final double BOW_PLAYER_RANGE_SQUARED = 9.0;
    static final double HANDOVER_PLAYER_RANGE_SQUARED = 20.25;
    static final double BONDED_RANGE_SQUARED = 4096.0;
    static final int SINGER_REQUEST_INTERVAL = 30;

    private static final long CANYON_REJECT_SHORT = 2400L;
    private static final long CANYON_REJECT_LONG = 6000L;

    /** A path the pathfinder found. */
    interface Path {
        boolean canReach();

        /** The last node of the path; null when it has none. */
        SiftPos end();
    }

    /** The golem's walking, over Paper's pathfinder. */
    interface Navigation {
        /** A path to within {@code reach} blocks of the target; null when there is none. */
        Path createPath(SiftPos target, int reach);

        /** A path to the first reachable of the targets, nearest first. */
        Path createPath(Collection<SiftPos> targets, int reach);

        boolean moveTo(Path path, double speed);

        /** Walking along a path. */
        boolean isInProgress();

        boolean isDone();

        /** The Y of the next node of the path being walked; null without a path. */
        Integer nextNodeY();

        void stop();
    }

    /** A player the golem may bring a Soul Block to. */
    interface Player {
        UUID id();

        String name();

        SiftPoint position();

        /** Whether the player is in the golem's world. */
        boolean inWorld();
    }

    /** Everything the golem reads or does in the world. */
    interface Env {
        long gameTime();

        /** The entity id, used to spread out retries of golems in the same tick. */
        int entityId();

        UUID uuid();

        double x();

        double y();

        double z();

        boolean onGround();

        boolean horizontalCollision();

        Navigation navigation();

        GolemShared shared();

        SiftLandmarks landmarks();

        boolean isLoaded(SiftPos pos);

        boolean isSoulBlock(SiftPos pos);

        /** {@code level.canSeeSky}. */
        boolean canSeeSky(SiftPos pos);

        /** {@code getHeight(MOTION_BLOCKING_NO_LEAVES)}: the first free Y above the highest blocking, non-leaf block. */
        int surfaceY(int x, int z);

        /** The chunk with this block column is loaded. */
        boolean isChunkLoaded(int x, int z);

        /** Whether the golem's hands reach the Soul Block from where it stands: within 1.75 blocks, with a clear line. */
        boolean handsCanReach(SiftPos soul);

        /** {@code DefaultRandomPos.getPosTowards}: a spot within the ranges, within the angle of the direction to the target. */
        Optional<SiftPoint> randomPosTowards(SiftPoint target, int horizontal, int vertical, double maximumAngle);

        /** Replaces the Soul Block with air. */
        void removeSoulBlock(SiftPos pos);

        /** A golem by id when it is loaded and alive: the Soul Block it has claimed. */
        Optional<SiftPos> soulTargetOf(UUID golem);

        /** The Singer with this id when she is loaded, alive and on a soul event; else null. */
        SoulExchange.Singer singer(UUID id);

        /** {@code SoulExchangeManager.requestSinger}: finds or calls a Singer for this golem; null when none. */
        SoulExchange.Singer requestSinger(EchoGolemBrain golem);

        /** An online player by id, or null. */
        Player player(UUID id);

        /** An online player by name (ignoring case), or null. */
        Player playerNamed(String name);

        void faceToward(SiftPoint point);

        /** Pushes the golem up so it gets over a step it is pressed against. */
        void hop(double velocity);

        void playItemGet(float pitch);

        void playItemDrop(float pitch);

        /** Drops a Soul Block at the golem's chest. */
        void dropSoulBlock();

        /** Puts a Soul Block in the player's inventory, or at their feet when it is full. */
        void giveSoulBlock(Player player);

        void awardFriendOfTheSift(Player player);

        /** Awards {@code like_father_and_son} to the players within 16 blocks. */
        void awardLikeFatherAndSon();

        /** Never despawns again. */
        void keepLoaded();
    }

    /** A Soul Block to go for, with the spot to go to when it is already known. */
    private record SoulCandidate(SiftPos soul, SiftPos approach) {
    }

    /** What the mod saves. */
    record Snapshot(int mission, boolean hasSoul, int stateTicks, Integer route, int routeTicks, Long soulTarget,
                    Long soulApproach, Long canyonEntrance, Long canyonMidpoint, Long canyonCluster, Long ignoredCanyon,
                    int ignoredCanyonTicks, boolean enteredCanyon, Boolean exitMidpointPassed, double explorationHeading,
                    UUID singerTarget, UUID bondedPlayer, String bondedPlayerName, Long deliveryAnchor) {
    }

    private final Env env;
    private final Random random;

    private Mission mission = Mission.SEARCH;
    private Route route = Route.EXPLORE;
    private boolean hasSoul;
    private boolean bonded;
    private SiftPos soulTarget;
    private SiftPos soulApproach;
    private SiftPos canyonEntrance;
    private SiftPos canyonMidpoint;
    private SiftPos canyonCluster;
    private SiftPos ignoredCanyon;
    private UUID singerTarget;
    private UUID bondedPlayer;
    private String bondedPlayerName;
    private SiftPos deliveryAnchor;
    private SiftPos patrolTarget;
    private int patrolCooldown;
    private int stateTicks;
    private int routeTicks;
    private int routeFailures;
    private int targetCooldown;
    private int nearbySoulScanCooldown;
    private long lastSoulScanTick = Long.MIN_VALUE;
    private long nextPhysicalSoulScanTick;
    private long nextAllowedPathfindTick;
    private int repathCooldown;
    private int emptyCanyonChecks;
    private int canyonEntryAttempts;
    private int canyonDepthAttempts;
    private int ignoredCanyonTicks;
    private int landmarkCooldown;
    private int explorationTurnTicks;
    private int explorationTargetTicks;
    private double explorationHeading;
    private SiftPos explorationTarget;
    private boolean enteredCanyon;
    private boolean exitMidpointPassed;
    private SiftPos navigationGoal;
    private int stalledSamples;
    private SiftPoint progressSample;
    private SiftPos distantCanyonHint;
    private SiftPoint carrySample;
    private int carryWatchdogTicks;
    private int carryStallSamples;
    private int carryRecoveryStage;
    private int livenessTicks;
    private int livenessStallSamples;
    private SiftPoint livenessSample;
    private int age;
    private final Map<Long, Long> unavailableCanyons = new HashMap<>();
    private final Map<Long, Long> unreachableSoulBlocks = new HashMap<>();

    EchoGolemBrain(Env env, Random random) {
        this.env = env;
        this.random = random;
        this.explorationHeading = random.nextDouble() * Math.PI * 2.0;
        this.explorationTurnTicks = 900 + random.nextInt(901);
        this.nearbySoulScanCooldown = 1 + random.nextInt(20);
        this.landmarkCooldown = 1 + random.nextInt(45);
        this.nextPhysicalSoulScanTick = env.gameTime() + random.nextInt(80);
        this.nextAllowedPathfindTick = env.gameTime() + random.nextInt(4);
        this.livenessSample = here();
        this.progressSample = here();
        this.carrySample = here();
    }

    // --- state the world reads ------------------------------------------------------------------

    Mission mission() {
        return mission;
    }

    Route route() {
        return route;
    }

    @Override
    public boolean hasSoulBlock() {
        return hasSoul;
    }

    boolean isBonded() {
        return bonded;
    }

    SiftPos soulTarget() {
        return soulTarget;
    }

    SiftPos canyonCluster() {
        return canyonCluster;
    }

    SiftPos canyonEntrance() {
        return canyonEntrance;
    }

    int stateTicks() {
        return stateTicks;
    }

    UUID bondedPlayerId() {
        return bondedPlayer;
    }

    String bondedPlayerName() {
        return bondedPlayerName;
    }

    @Override
    public UUID id() {
        return env.uuid();
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public SiftPoint position() {
        return here();
    }

    @Override
    public boolean canInteractWithSinger() {
        return bondedPlayer == null && bondedPlayerName == null && !bonded;
    }

    /** {@code isWalkingForAnimation}. */
    static boolean isWalking(double horizontalSpeedSquared) {
        return horizontalSpeedSquared > 0.0012;
    }

    // --- the tick -------------------------------------------------------------------------------

    /** One server tick of a living golem. */
    void tick() {
        age++;
        if (ignoredCanyonTicks > 0 && --ignoredCanyonTicks == 0) {
            ignoredCanyon = null;
        }
        stateTicks++;
        routeTicks++;
        switch (mission) {
            case SEARCH -> tickSearch();
            case PICKUP -> tickPickup();
            case CARRY -> tickCarry();
            case BOW -> tickBow();
            case SIT -> tickSit();
        }
        if (mission == Mission.CARRY && hasSoul) {
            tickCarryWatchdog();
        }
        if (mission == Mission.SEARCH) {
            tickLivenessWatchdog();
        }
        assistLocalStep();
    }

    // --- what other creatures and players do to the golem ---------------------------------------

    @Override
    public void beginBow() {
        if (hasSoul) {
            env.navigation().stop();
            setMission(Mission.BOW);
        }
    }

    @Override
    public void faceToward(SiftPoint point) {
        env.faceToward(point);
    }

    @Override
    public boolean transferSoulToSinger() {
        if (!canInteractWithSinger() || !hasSoul) {
            return false;
        }
        env.awardLikeFatherAndSon();
        hasSoul = false;
        env.playItemDrop(0.92F);
        singerTarget = null;
        deliveryAnchor = null;
        patrolTarget = null;
        forgetCurrentCanyon(FORGET_CANYON_TICKS);
        setRoutePhase(Route.EXPLORE);
        if (mission != Mission.BOW) {
            setMission(Mission.SEARCH);
        }
        return true;
    }

    @Override
    public void finishBow() {
        if (mission == Mission.BOW) {
            if (!hasSoul) {
                setRoutePhase(Route.EXPLORE);
            }
            setMission(Mission.SEARCH);
        }
    }

    /** {@code sitAfterLosingSoul}. */
    void sitAfterLosingSoul() {
        env.navigation().stop();
        singerTarget = null;
        deliveryAnchor = null;
        patrolTarget = null;
        forgetCurrentCanyon(FORGET_CANYON_TICKS);
        setRoutePhase(Route.EXPLORE);
        setMission(Mission.SIT);
    }

    /** {@code releaseCarriedSoul}: drops the Soul Block and sits down; false when it carried none. */
    boolean releaseCarriedSoul() {
        if (!hasSoul) {
            return false;
        }
        hasSoul = false;
        env.dropSoulBlock();
        env.playItemDrop(1.0F);
        sitAfterLosingSoul();
        return true;
    }

    /** A player gives an Echo Shard: bonds the golem to them. False when it is bonded already. */
    boolean bondTo(UUID playerId, String playerName) {
        if (bondedPlayer != null || bondedPlayerName != null || bonded) {
            return false;
        }
        bondedPlayer = playerId;
        bondedPlayerName = playerName;
        bonded = true;
        singerTarget = null;
        patrolTarget = null;
        patrolCooldown = 0;
        env.keepLoaded();
        if (mission == Mission.BOW) {
            setRoutePhase(hasSoul ? (hasReachedSurface() ? Route.DELIVER : Route.EXIT_CANYON) : Route.EXPLORE);
            setMission(hasSoul ? Mission.CARRY : Mission.SEARCH);
        }
        return true;
    }

    /** {@code isBondedTo}: everybody, as long as the golem has not been bonded. */
    boolean isBondedTo(UUID playerId, String playerName) {
        if (!bonded) {
            return true;
        }
        if (bondedPlayer != null && bondedPlayer.equals(playerId)) {
            return true;
        }
        if (bondedPlayerName != null && bondedPlayerName.equalsIgnoreCase(playerName)) {
            bondedPlayer = playerId;
            bondedPlayerName = playerName;
            return true;
        }
        return false;
    }

    /** The golem was hurt: stops what it was walking to and starts its liveness count again. */
    void onHurt() {
        env.navigation().stop();
        navigationGoal = null;
        repathCooldown = 0;
        livenessTicks = 0;
        livenessStallSamples = 0;
        livenessSample = here();
        if (mission == Mission.SEARCH && route == Route.SEEK_SOUL) {
            soulApproach = null;
        }
    }

    /** The golem is gone (dead or unloaded): gives up its claim on a Soul Block. */
    void release() {
        clearSoulTargetReservation();
    }

    // --- mission and route changes --------------------------------------------------------------

    private void setMission(Mission next) {
        mission = next;
        stateTicks = 0;
        repathCooldown = 0;
        resetRouteProgressSample();
        resetCarryWatchdog();
    }

    private void setRoutePhase(Route next) {
        route = next;
        routeTicks = 0;
        routeFailures = 0;
        repathCooldown = 0;
        navigationGoal = null;
        explorationTarget = null;
        explorationTargetTicks = 0;
        resetRouteProgressSample();
        env.navigation().stop();
    }

    // --- searching ------------------------------------------------------------------------------

    private void tickSearch() {
        if (hasSoul) {
            setRoutePhase(hasReachedSurface() ? Route.DELIVER : Route.EXIT_CANYON);
            setMission(Mission.CARRY);
            return;
        }
        if (soulTarget != null && !env.isSoulBlock(soulTarget)) {
            forgetSoulFailure(soulTarget);
            clearSoulTargetReservation();
            targetCooldown = 0;
            nearbySoulScanCooldown = 2 + random.nextInt(5);
            repathCooldown = 0;
            navigationGoal = null;
            env.navigation().stop();
        }
        if (--nearbySoulScanCooldown <= 0) {
            nearbySoulScanCooldown = 12;
            lastSoulScanTick = env.gameTime();
            SoulCandidate nearby = findReachableSoulBlock(blockPosition(), soulTarget == null ? 36 : 16, soulTarget == null ? 26 : 12);
            double currentDistance = soulTarget == null ? Double.POSITIVE_INFINITY : soulTarget.distSqr(blockPosition());
            double nearbyDistance = nearby == null ? Double.POSITIVE_INFINITY : nearby.soul().distSqr(blockPosition());
            if (nearby != null && (soulTarget == null || !nearby.soul().equals(soulTarget) && nearbyDistance + 1.0 < currentDistance)) {
                claimSoulTarget(nearby);
                canyonDepthAttempts = 0;
                emptyCanyonChecks = 0;
                setRoutePhase(Route.SEEK_SOUL);
                return;
            }
        }
        if (soulTarget != null && route != Route.SEEK_SOUL) {
            setRoutePhase(Route.SEEK_SOUL);
        }
        switch (route) {
            case EXPLORE -> tickExploreRoute();
            case APPROACH_CANYON -> tickCanyonApproach();
            case ENTER_CANYON -> tickCanyonEntry();
            case DESCEND_CANYON -> tickCanyonDescent();
            case SEEK_SOUL -> tickSoulSearch();
            case LEAVE_EMPTY_CANYON -> tickLeaveEmptyCanyon();
            default -> setRoutePhase(enteredCanyon && canyonCluster != null ? Route.SEEK_SOUL : Route.EXPLORE);
        }
    }

    private void tickExploreRoute() {
        purgeExpiredRejections(env.gameTime());
        if (--landmarkCooldown <= 0) {
            landmarkCooldown = 45;
            if (acquireCanyonRoute()) {
                setRoutePhase(Route.APPROACH_CANYON);
                return;
            }
        }
        tickExploration();
        monitorRouteProgress(true);
    }

    private void tickCanyonApproach() {
        if (!hasValidCanyonRoute()) {
            clearCanyonRoute();
            setRoutePhase(Route.EXPLORE);
            return;
        }
        SiftPos approach = canyonApproach(canyonEntrance, canyonCluster);
        if (approach == null) {
            abandonBlockedCanyon(CANYON_REJECT_SHORT);
            return;
        }
        if (isNear(approach, 3.5, 5.0)) {
            setRoutePhase(Route.ENTER_CANYON);
            return;
        }
        navigateToward(approach, 0.88);
        monitorRouteProgress(false);
        if (routeFailures >= 8) {
            abandonBlockedCanyon(CANYON_REJECT_SHORT);
        }
    }

    private void tickCanyonEntry() {
        if (!hasValidCanyonRoute()) {
            clearCanyonRoute();
            setRoutePhase(Route.EXPLORE);
            return;
        }
        if (isNear(canyonEntrance, 3.5, 4.0)) {
            enteredCanyon = true;
            canyonEntryAttempts = 0;
            setRoutePhase(canyonMidpoint == null ? Route.SEEK_SOUL : Route.DESCEND_CANYON);
            return;
        }
        navigateToward(canyonEntrance, 0.84);
        monitorRouteProgress(false);
        if (routeFailures >= 12) {
            if (++canyonEntryAttempts >= 2) {
                abandonBlockedCanyon(CANYON_REJECT_SHORT);
            } else {
                setRoutePhase(Route.APPROACH_CANYON);
            }
        }
    }

    private void tickCanyonDescent() {
        if (!hasValidCanyonRoute()) {
            clearCanyonRoute();
            setRoutePhase(Route.EXPLORE);
            return;
        }
        if (canyonMidpoint == null || isNear(canyonMidpoint, 3.0, 4.0)) {
            setRoutePhase(Route.SEEK_SOUL);
            return;
        }
        navigateToward(canyonMidpoint, 0.82);
        monitorRouteProgress(false);
        if (routeFailures >= 12) {
            if (++canyonEntryAttempts >= 2) {
                markCurrentCanyonUnavailable(CANYON_REJECT_SHORT);
                setRoutePhase(Route.LEAVE_EMPTY_CANYON);
            } else {
                enteredCanyon = false;
                setRoutePhase(Route.APPROACH_CANYON);
            }
        }
    }

    private void tickSoulSearch() {
        if (soulTarget != null) {
            if (soulApproach == null) {
                if (!tryAcquirePathBudget(4)) {
                    return;
                }
                soulApproach = findReachableSoulApproach(soulTarget);
                if (soulApproach == null) {
                    if (++routeFailures >= 3) {
                        rejectCurrentSoulTarget(100L);
                        nearbySoulScanCooldown = 2 + random.nextInt(5);
                        routeFailures = 0;
                    }
                    return;
                }
                routeFailures = 0;
            }
            if (env.handsCanReach(soulTarget)) {
                env.navigation().stop();
                setMission(Mission.PICKUP);
                return;
            }
            navigateToward(soulApproach, 0.9);
            monitorRouteProgress(false);
            if (routeFailures >= 8) {
                rejectCurrentSoulTarget(200L);
                targetCooldown = 0;
                nearbySoulScanCooldown = 2 + random.nextInt(5);
                routeFailures = 0;
            }
            return;
        }
        if (lastSoulScanTick == env.gameTime() && canyonCluster != null && isNear(canyonCluster.above(), 11.0, 10.0)
            && ++emptyCanyonChecks >= 3) {
            markCurrentCanyonUnavailable(CANYON_REJECT_LONG);
            setRoutePhase(Route.LEAVE_EMPTY_CANYON);
            return;
        }
        if (canyonCluster == null) {
            setRoutePhase(Route.EXPLORE);
            return;
        }
        if (!enteredCanyon) {
            setRoutePhase(Route.APPROACH_CANYON);
            return;
        }
        if (isNear(canyonCluster.above(), 11.0, 10.0)) {
            return;
        }
        navigateToward(canyonCluster.above(), 0.84);
        monitorRouteProgress(false);
        if (routeFailures >= 12) {
            if (++canyonDepthAttempts >= 3) {
                markCurrentCanyonUnavailable(CANYON_REJECT_LONG);
                setRoutePhase(Route.LEAVE_EMPTY_CANYON);
            } else {
                enteredCanyon = false;
                setRoutePhase(Route.APPROACH_CANYON);
            }
        }
    }

    private void tickLeaveEmptyCanyon() {
        if (hasReachedSurface()) {
            clearCanyonRoute();
            setRoutePhase(Route.EXPLORE);
            return;
        }
        SiftPos exit = ensureReachableExit();
        if (exit != null) {
            navigateExitToward(exit, 0.86);
            monitorRouteProgress(false);
        } else {
            tickEmergencyAscent();
        }
    }

    private void tickExploration() {
        if (--explorationTurnTicks <= 0) {
            explorationHeading += (random.nextDouble() - 0.5) * 0.38;
            explorationTurnTicks = 900 + random.nextInt(901);
            explorationTarget = null;
        }
        explorationTargetTicks++;
        boolean reached = explorationTarget != null && isNear(explorationTarget, 4.0, 7.0);
        boolean failed = routeFailures >= 6 || explorationTargetTicks >= 1200;
        if (explorationTarget == null || reached || failed) {
            if (failed) {
                explorationHeading += (random.nextBoolean() ? 1.0 : -1.0) * (0.58 + random.nextDouble() * 0.38);
            }
            explorationTarget = chooseExplorationTarget();
            explorationTargetTicks = 0;
            routeFailures = 0;
            repathCooldown = 0;
            navigationGoal = null;
            env.navigation().stop();
        }
        if (explorationTarget != null) {
            navigateToward(explorationTarget, 0.78);
        }
    }

    private SiftPos chooseExplorationTarget() {
        double[] offsets = {0.0, 0.18, -0.18, 0.38, -0.38, 0.62, -0.62};
        for (int distance : new int[]{42, 34, 26}) {
            for (double offset : offsets) {
                double angle = explorationHeading + offset;
                int x = (int) Math.floor(env.x() + Math.cos(angle) * distance);
                int z = (int) Math.floor(env.z() + Math.sin(angle) * distance);
                if (!env.isChunkLoaded(x, z)) {
                    continue;
                }
                return new SiftPos(x, env.surfaceY(x, z), z);
            }
        }
        SiftPoint desired = new SiftPoint(env.x() + Math.cos(explorationHeading) * 28.0, env.y(),
            env.z() + Math.sin(explorationHeading) * 28.0);
        return env.randomPosTowards(desired, 24, 12, 1.0471975511965976).map(SiftPoint::containing).orElse(null);
    }

    // --- picking up -----------------------------------------------------------------------------

    private void tickPickup() {
        env.navigation().stop();
        if (stateTicks == PICKUP_GRAB_TICK) {
            if (soulTarget == null || !env.isSoulBlock(soulTarget)) {
                forgetSoulFailure(soulTarget);
                clearSoulTargetReservation();
                targetCooldown = 0;
                setRoutePhase(enteredCanyon && canyonCluster != null ? Route.SEEK_SOUL : Route.EXPLORE);
                setMission(Mission.SEARCH);
                return;
            }
            forgetSoulFailure(soulTarget);
            env.removeSoulBlock(soulTarget);
            clearSoulTargetReservation();
            hasSoul = true;
            deliveryAnchor = canyonEntrance != null ? canyonEntrance : blockPosition();
            env.keepLoaded();
            env.playItemGet(0.96F);
        }
        if (stateTicks >= PICKUP_TICKS) {
            clearSoulTargetReservation();
            exitMidpointPassed = !needsCanyonMidpointForExit();
            setRoutePhase(hasReachedSurface() ? Route.DELIVER : Route.EXIT_CANYON);
            setMission(Mission.CARRY);
        }
    }

    // --- carrying -------------------------------------------------------------------------------

    private void tickCarry() {
        if (!hasSoul) {
            setRoutePhase(Route.EXPLORE);
            setMission(Mission.SEARCH);
            return;
        }
        if (route != Route.EXIT_CANYON && route != Route.DELIVER) {
            setRoutePhase(hasReachedSurface() ? Route.DELIVER : Route.EXIT_CANYON);
        }
        if (route == Route.EXIT_CANYON) {
            if (hasReachedSurface()) {
                enteredCanyon = false;
                setRoutePhase(Route.DELIVER);
                return;
            }
            if (!exitMidpointPassed && canyonMidpoint != null) {
                if (isNear(canyonMidpoint, 3.0, 4.0)) {
                    exitMidpointPassed = true;
                    repathCooldown = 0;
                    navigationGoal = null;
                    env.navigation().stop();
                } else {
                    navigateExitToward(canyonMidpoint, 0.84);
                    monitorRouteProgress(false);
                    if (routeFailures < 12) {
                        return;
                    }
                    exitMidpointPassed = true;
                    routeFailures = 0;
                }
            }
            SiftPos exit = ensureReachableExit();
            if (exit != null) {
                navigateExitToward(exit, 0.88);
                monitorRouteProgress(false);
            } else {
                tickEmergencyAscent();
            }
            return;
        }
        if (!canInteractWithSinger()) {
            tickBondedDelivery();
            return;
        }
        SoulExchange.Singer singer = resolveSinger();
        if (singer == null && --targetCooldown <= 0) {
            targetCooldown = SINGER_REQUEST_INTERVAL;
            singer = env.requestSinger(this);
            if (singer != null) {
                singerTarget = singer.id();
                navigationGoal = null;
                repathCooldown = 0;
            }
        }
        if (singer != null) {
            singer.offerSoulGolem(this);
            navigateToward(singer.blockPosition(), 0.86);
            monitorRouteProgress(false);
        } else {
            tickExploration();
            monitorRouteProgress(true);
        }
    }

    private void tickBondedDelivery() {
        Player owner = resolveBondedPlayer();
        if (owner != null && owner.inWorld() && here().distanceSqr(owner.position()) <= BONDED_RANGE_SQUARED) {
            patrolTarget = null;
            patrolCooldown = 0;
            double distance = here().distanceSqr(owner.position());
            if (distance <= BOW_PLAYER_RANGE_SQUARED && env.onGround() && Math.abs(env.y() - owner.position().y()) <= 1.25) {
                env.navigation().stop();
                env.faceToward(owner.position());
                setMission(Mission.BOW);
                return;
            }
            navigateBondedDelivery(owner);
            monitorRouteProgress(false);
            return;
        }
        tickPatrolAroundCanyon();
        monitorRouteProgress(true);
    }

    private void navigateBondedDelivery(Player owner) {
        if (--repathCooldown > 0) {
            return;
        }
        SiftPos target = owner.position().containing();
        if (navigationGoal != null && navigationGoal.distSqr(target) < 4.0 && env.navigation().isInProgress()) {
            return;
        }
        if (!tryAcquirePathBudget(4)) {
            repathCooldown = 2;
            return;
        }
        repathCooldown = 10;
        Path path = env.navigation().createPath(target, 1);
        if (path != null && env.navigation().moveTo(path, 0.82)) {
            navigationGoal = target;
            routeFailures = 0;
        } else {
            routeFailures++;
        }
    }

    private void tickPatrolAroundCanyon() {
        SiftPos anchor = deliveryAnchor != null ? deliveryAnchor : canyonEntrance != null ? canyonEntrance : blockPosition();
        if (horizontalDistanceSqr(anchor) > 4096.0) {
            patrolTarget = anchor;
            navigateToward(anchor, 0.76);
            return;
        }
        if (--patrolCooldown > 0 && patrolTarget != null && !env.navigation().isDone()) {
            return;
        }
        patrolCooldown = 50 + random.nextInt(51);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int radius = 14 + random.nextInt(47);
        int x = anchor.x() + (int) Math.round(Math.cos(angle) * radius);
        int z = anchor.z() + (int) Math.round(Math.sin(angle) * radius);
        SiftPos probe = new SiftPos(x, anchor.y(), z);
        if (!env.isLoaded(probe)) {
            patrolTarget = anchor;
        } else {
            patrolTarget = new SiftPos(x, env.surfaceY(x, z), z);
        }
        navigateToward(patrolTarget, 0.74);
    }

    // --- bowing and sitting ---------------------------------------------------------------------

    private void tickBow() {
        env.navigation().stop();
        if (!canInteractWithSinger()) {
            Player owner = resolveBondedPlayer();
            if (hasSoul) {
                if (owner == null || !owner.inWorld() || here().distanceSqr(owner.position()) > HANDOVER_PLAYER_RANGE_SQUARED) {
                    setRoutePhase(hasReachedSurface() ? Route.DELIVER : Route.EXIT_CANYON);
                    setMission(Mission.CARRY);
                    return;
                }
                env.faceToward(owner.position());
                if (stateTicks == PLAYER_HANDOVER_TICK) {
                    completePlayerSoulDelivery(owner);
                }
            } else if (owner != null && owner.inWorld()) {
                env.faceToward(owner.position());
            }
            if (stateTicks >= PLAYER_BOW_TICKS) {
                setRoutePhase(Route.EXPLORE);
                setMission(Mission.SEARCH);
            }
            return;
        }
        if (stateTicks >= SINGER_BOW_TICKS) {
            if (hasSoul) {
                setRoutePhase(hasReachedSurface() ? Route.DELIVER : Route.EXIT_CANYON);
                setMission(Mission.CARRY);
            } else {
                setRoutePhase(Route.EXPLORE);
                setMission(Mission.SEARCH);
            }
        }
    }

    private void completePlayerSoulDelivery(Player player) {
        if (!hasSoul) {
            return;
        }
        hasSoul = false;
        env.giveSoulBlock(player);
        env.awardFriendOfTheSift(player);
        env.playItemDrop(1.04F);
        forgetCurrentCanyon(FORGET_CANYON_TICKS);
        deliveryAnchor = null;
        patrolTarget = null;
    }

    private void tickSit() {
        env.navigation().stop();
        if (stateTicks >= SIT_TICKS) {
            targetCooldown = 0;
            setRoutePhase(Route.EXPLORE);
            setMission(Mission.SEARCH);
        }
    }

    // --- surface and exits ----------------------------------------------------------------------

    private boolean hasReachedSurface() {
        if (canyonEntrance == null) {
            return env.canSeeSky(blockPosition().above());
        }
        return env.y() >= canyonEntrance.y() - 2.0
            && (env.canSeeSky(blockPosition().above()) || horizontalDistanceSqr(canyonEntrance) <= 144.0);
    }

    private SiftPos ensureReachableExit() {
        if (canyonEntrance == null) {
            SiftPos origin = canyonCluster == null ? blockPosition() : canyonCluster;
            canyonEntrance = env.landmarks().nearestOther(SiftLandmarks.Kind.CANYON_ENTRANCE, origin, 112.0, null).orElse(null);
            navigationGoal = null;
            repathCooldown = 0;
        }
        if (canyonEntrance != null && routeFailures < 6) {
            return canyonEntrance;
        }
        SiftPos emergency = findReachableSurfaceExit();
        if (emergency != null) {
            canyonEntrance = emergency;
            routeFailures = 0;
            navigationGoal = null;
            repathCooldown = 0;
            env.navigation().stop();
            return emergency;
        }
        return canyonEntrance;
    }

    /** The first surface spot, over rings of 8, 14, 20 and 28 blocks, that the golem can walk to. */
    private SiftPos findReachableSurfaceExit() {
        if (!tryAcquireCriticalExitPathBudget(5)) {
            return null;
        }
        List<SiftPos> candidates = new ArrayList<>();
        double phase = explorationHeading;
        int minimumExitY = canyonEntrance == null ? blockPosition().y() + 2 : canyonEntrance.y() - 2;
        for (int radius : new int[]{8, 14, 20, 28}) {
            for (int direction = 0; direction < 8; direction++) {
                double angle = phase + direction * Math.PI * 2.0 / 8.0;
                int x = (int) Math.floor(env.x() + Math.cos(angle) * radius);
                int z = (int) Math.floor(env.z() + Math.sin(angle) * radius);
                int y = env.surfaceY(x, z);
                SiftPos feet = new SiftPos(x, y, z);
                if (y < minimumExitY || !env.isLoaded(feet) || !env.canSeeSky(feet.above())) {
                    continue;
                }
                candidates.add(feet);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        Path path = env.navigation().createPath(candidates, 2);
        if (path == null || path.end() == null || !path.canReach()) {
            return null;
        }
        return path.end();
    }

    private void tickEmergencyAscent() {
        if (env.navigation().isInProgress()) {
            monitorRouteProgress(false);
            return;
        }
        if (--repathCooldown > 0) {
            return;
        }
        repathCooldown = 5;
        if (!tryAcquireCriticalExitPathBudget(5)) {
            repathCooldown = 1 + (env.entityId() & 1);
            return;
        }
        int surfaceY = env.surfaceY(blockPosition().x(), blockPosition().z());
        SiftPoint upward = new SiftPoint(env.x() + Math.cos(explorationHeading) * 18.0, Math.max(env.y() + 8.0, surfaceY),
            env.z() + Math.sin(explorationHeading) * 18.0);
        Optional<SiftPoint> waypoint = env.randomPosTowards(upward, 22, 18, 1.5707963267948966);
        boolean moving = false;
        if (waypoint.isPresent()) {
            Path path = env.navigation().createPath(waypoint.get().containing(), 1);
            moving = path != null && env.navigation().moveTo(path, 0.84);
        }
        if (!moving) {
            explorationHeading += (random.nextBoolean() ? 1.0 : -1.0) * (0.65 + random.nextDouble() * 0.55);
            routeFailures++;
        }
        monitorRouteProgress(false);
    }

    // --- canyon routes --------------------------------------------------------------------------

    private boolean acquireCanyonRoute() {
        Set<Long> excluded = new HashSet<>(unavailableCanyons.keySet());
        if (ignoredCanyon != null && ignoredCanyonTicks > 0) {
            excluded.add(ignoredCanyon.asLong());
        }
        SiftPos cluster = env.landmarks().nearestActiveCluster(blockPosition(), 1024.0, excluded,
            candidate -> !activeSoulBlocksNear(candidate, 36, 28).isEmpty()).orElse(null);
        if (cluster == null) {
            return false;
        }
        if (!hasUnquarantinedSoulAtCanyon(cluster)) {
            rejectCanyon(cluster, CANYON_REJECT_LONG);
            return false;
        }
        SiftPos entrance = env.landmarks().nearestOther(SiftLandmarks.Kind.CANYON_ENTRANCE, cluster, 96.0, null).orElse(null);
        if (entrance == null) {
            return false;
        }
        SiftPos midpoint = env.landmarks().nearestOther(SiftLandmarks.Kind.CANYON_MIDPOINT, cluster, 72.0, null).orElse(null);
        SiftPos approach = canyonApproach(entrance, cluster);
        if (approach == null) {
            rejectCanyon(cluster, CANYON_REJECT_SHORT);
            return false;
        }
        if (horizontalDistanceSqr(approach) > 9216.0) {
            if (!cluster.equals(distantCanyonHint)) {
                distantCanyonHint = cluster;
                explorationHeading = Math.atan2(approach.z() + 0.5 - env.z(), approach.x() + 0.5 - env.x());
                explorationTarget = null;
                explorationTargetTicks = 0;
                navigationGoal = null;
                repathCooldown = 0;
                env.navigation().stop();
            }
            return false;
        }
        distantCanyonHint = null;
        canyonCluster = cluster;
        canyonEntrance = entrance;
        canyonMidpoint = midpoint;
        enteredCanyon = false;
        exitMidpointPassed = false;
        emptyCanyonChecks = 0;
        canyonEntryAttempts = 0;
        canyonDepthAttempts = 0;
        return true;
    }

    private boolean hasUnquarantinedSoulAtCanyon(SiftPos cluster) {
        long gameTime = env.gameTime();
        for (SiftPos soul : activeSoulBlocksNear(cluster, 38, 28)) {
            if (!env.handsCanReach(soul) && isSoulGloballyRejected(soul, gameTime)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean hasValidCanyonRoute() {
        return canyonCluster != null && canyonEntrance != null;
    }

    private void clearCanyonRoute() {
        clearSoulTargetReservation();
        canyonEntrance = null;
        canyonMidpoint = null;
        canyonCluster = null;
        enteredCanyon = false;
        exitMidpointPassed = false;
        emptyCanyonChecks = 0;
        canyonEntryAttempts = 0;
        canyonDepthAttempts = 0;
        landmarkCooldown = 0;
        targetCooldown = 0;
    }

    private void forgetCurrentCanyon(int ignoreTicks) {
        if (canyonCluster != null && ignoreTicks > 0) {
            ignoredCanyon = canyonCluster;
            ignoredCanyonTicks = ignoreTicks;
        }
        clearCanyonRoute();
    }

    private void abandonBlockedCanyon(long duration) {
        SiftPos blocked = canyonCluster;
        if (blocked != null) {
            rejectCanyon(blocked, duration);
            ignoredCanyon = blocked;
            ignoredCanyonTicks = (int) Math.min(Integer.MAX_VALUE, duration);
        }
        clearCanyonRoute();
        setRoutePhase(Route.EXPLORE);
        forceExplorationTurn(false);
    }

    private void markCurrentCanyonUnavailable(long duration) {
        if (canyonCluster == null) {
            return;
        }
        unavailableCanyons.put(canyonCluster.asLong(), env.gameTime() + duration);
        ignoredCanyon = canyonCluster;
        ignoredCanyonTicks = (int) Math.min(Integer.MAX_VALUE, duration);
        clearSoulTargetReservation();
    }

    private void purgeExpiredRejections(long gameTime) {
        unavailableCanyons.values().removeIf(until -> until <= gameTime);
        unreachableSoulBlocks.values().removeIf(until -> until <= gameTime);
    }

    private void rejectCanyon(SiftPos canyon, long duration) {
        if (canyon == null) {
            return;
        }
        unavailableCanyons.put(canyon.asLong(), env.gameTime() + duration);
        if (canyon.equals(distantCanyonHint)) {
            distantCanyonHint = null;
        }
    }

    private void forceExplorationTurn(boolean rejectHint) {
        if (rejectHint && distantCanyonHint != null) {
            rejectCanyon(distantCanyonHint, CANYON_REJECT_SHORT);
        }
        env.navigation().stop();
        navigationGoal = null;
        repathCooldown = 0;
        routeFailures = 0;
        explorationTarget = null;
        explorationTargetTicks = 0;
        double turn = 1.75 + random.nextDouble() * 0.7;
        explorationHeading += random.nextBoolean() ? turn : -turn;
        explorationTurnTicks = 600 + random.nextInt(601);
        explorationTarget = chooseExplorationTarget();
        resetRouteProgressSample();
    }

    /** The spot seven blocks outside the entrance, away from the canyon, where the golem goes before it enters. */
    private SiftPos canyonApproach(SiftPos entrance, SiftPos cluster) {
        if (entrance == null || cluster == null) {
            return entrance;
        }
        double dx = entrance.x() - cluster.x();
        double dz = entrance.z() - cluster.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0) {
            return entrance;
        }
        int x = entrance.x() + (int) Math.round(dx / length * 7.0);
        int z = entrance.z() + (int) Math.round(dz / length * 7.0);
        SiftPos probe = new SiftPos(x, entrance.y(), z);
        int y = env.isLoaded(probe) ? env.surfaceY(x, z) : entrance.y();
        SiftPos approach = new SiftPos(x, y, z);
        return horizontalDistanceSqr(approach) <= 16.0 ? entrance : approach;
    }

    private boolean needsCanyonMidpointForExit() {
        if (canyonMidpoint == null || canyonEntrance == null) {
            return false;
        }
        double midpointFromEntrance = canyonMidpoint.horizontalDistanceSqr(canyonEntrance);
        return horizontalDistanceSqr(canyonEntrance) > midpointFromEntrance + 16.0;
    }

    // --- walking --------------------------------------------------------------------------------

    private void navigateToward(SiftPos target, double speed) {
        navigateToward(target, speed, false);
    }

    private void navigateExitToward(SiftPos target, double speed) {
        navigateToward(target, speed, true);
    }

    /**
     * Walks toward a position, searching for a new path at most every {@code repathCooldown} ticks and only
     * while the path budget allows. Targets farther than 40 blocks are approached through waypoints 12 to 24
     * blocks ahead; every failed search counts in {@code routeFailures}, which the callers turn into decisions.
     */
    private boolean navigateToward(SiftPos target, double speed, boolean criticalExit) {
        if (target == null) {
            return false;
        }
        Navigation navigation = env.navigation();
        if (!target.equals(navigationGoal)) {
            navigationGoal = target;
            repathCooldown = 0;
            navigation.stop();
        }
        if (navigation.isInProgress()) {
            return true;
        }
        if (--repathCooldown > 0) {
            return false;
        }
        boolean acquired = criticalExit ? tryAcquireCriticalExitPathBudget(4) : tryAcquirePathBudget(4);
        if (!acquired) {
            repathCooldown = 1 + (env.entityId() & 1);
            return false;
        }
        repathCooldown = 10 + random.nextInt(7);
        SiftPoint exact = target.bottomCenter();
        SiftPoint destination = exact;
        double exactDx = exact.x() - env.x();
        double exactDz = exact.z() - env.z();
        if (!criticalExit && exactDx * exactDx + exactDz * exactDz > 1600.0) {
            SiftPoint waypoint = forwardWaypoint(exact);
            if (waypoint != null) {
                destination = waypoint;
            } else {
                routeFailures++;
                repathCooldown = 4;
                return false;
            }
        }
        SiftPos pathTarget = destination.containing();
        Path path;
        if (criticalExit) {
            List<SiftPos> rampWidth = new ArrayList<>();
            rampWidth.add(pathTarget);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        rampWidth.add(pathTarget.offset(dx, 0, dz));
                    }
                }
            }
            path = navigation.createPath(rampWidth, 1);
        } else {
            path = navigation.createPath(pathTarget, 1);
        }
        float acceptedDistance = criticalExit ? 10.0F : 4.0F;
        if (path == null || path.end() == null || !path.canReach() && path.end().distSqr(pathTarget) > acceptedDistance) {
            routeFailures++;
            repathCooldown = 4;
            return false;
        }
        if (!navigation.moveTo(path, speed)) {
            routeFailures++;
            repathCooldown = 4;
            return false;
        }
        repathCooldown = 0;
        if (routeFailures > 0) {
            routeFailures--;
        }
        return true;
    }

    /** {@code forwardWaypoint}: a spot 24, 18 or 12 blocks toward a far target, nudged sideways by earlier failures. */
    private SiftPoint forwardWaypoint(SiftPoint exact) {
        double dx = exact.x() - env.x();
        double dz = exact.z() - env.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0) {
            return exact;
        }
        double forwardX = dx / length;
        double forwardZ = dz / length;
        double sideX = -forwardZ;
        double sideZ = forwardX;
        double[] lateral = {0.0, 4.0, -4.0, 8.0, -8.0};
        int first = Math.min(routeFailures, lateral.length - 1);
        for (int distance : new int[]{24, 18, 12}) {
            for (int index = 0; index < lateral.length; index++) {
                double side = lateral[(first + index) % lateral.length];
                int x = (int) Math.floor(env.x() + forwardX * distance + sideX * side);
                int z = (int) Math.floor(env.z() + forwardZ * distance + sideZ * side);
                if (!env.isLoaded(new SiftPos(x, blockPosition().y(), z))) {
                    continue;
                }
                return new SiftPoint(x + 0.5, env.surfaceY(x, z), z + 0.5);
            }
        }
        return null;
    }

    /** {@code tryAcquirePathBudget}: this golem's own pause between searches, then the world's one search per tick. */
    boolean tryAcquirePathBudget(int personalInterval) {
        long now = env.gameTime();
        if (now < nextAllowedPathfindTick) {
            return false;
        }
        if (!env.shared().budget().tryAcquire(now)) {
            nextAllowedPathfindTick = now + 1L + (env.entityId() & 1);
            return false;
        }
        nextAllowedPathfindTick = now + Math.max(1, personalInterval);
        return true;
    }

    private boolean tryAcquireCriticalExitPathBudget(int personalInterval) {
        long now = env.gameTime();
        if (now < nextAllowedPathfindTick) {
            return false;
        }
        if (!env.shared().budget().tryAcquireCriticalExit(now)) {
            nextAllowedPathfindTick = now + 1L + (env.entityId() & 1);
            return false;
        }
        nextAllowedPathfindTick = now + Math.max(1, personalInterval);
        return true;
    }

    /** Samples every 20 ticks how far the golem has come; two stalled samples in a row turn it around. */
    private void monitorRouteProgress(boolean exploring) {
        if (routeTicks % 20 != 0) {
            return;
        }
        double moved = here().distanceSqr(progressSample);
        progressSample = here();
        if (moved >= 0.16) {
            stalledSamples = 0;
            return;
        }
        if (++stalledSamples < 2) {
            return;
        }
        stalledSamples = 0;
        env.navigation().stop();
        navigationGoal = null;
        repathCooldown = 0;
        if (exploring) {
            forceExplorationTurn(true);
            return;
        }
        if (hasSoul || mission == Mission.CARRY) {
            routeFailures += 4;
            return;
        }
        switch (route) {
            case APPROACH_CANYON, ENTER_CANYON -> abandonBlockedCanyon(CANYON_REJECT_SHORT);
            case DESCEND_CANYON -> {
                markCurrentCanyonUnavailable(CANYON_REJECT_SHORT);
                setRoutePhase(Route.LEAVE_EMPTY_CANYON);
            }
            case SEEK_SOUL -> {
                if (soulTarget != null) {
                    soulApproach = null;
                    targetCooldown = 0;
                    nextAllowedPathfindTick = env.gameTime();
                    routeFailures = Math.min(2, routeFailures + 1);
                } else {
                    markCurrentCanyonUnavailable(CANYON_REJECT_SHORT);
                    setRoutePhase(Route.LEAVE_EMPTY_CANYON);
                }
            }
            case LEAVE_EMPTY_CANYON -> routeFailures += 4;
            default -> forceExplorationTurn(false);
        }
    }

    private void resetRouteProgressSample() {
        progressSample = here();
        stalledSamples = 0;
    }

    /** Once a second while searching: a golem that should be walking but has moved a third of a block three times in a row is nudged. */
    private void tickLivenessWatchdog() {
        if (++livenessTicks < 20) {
            return;
        }
        livenessTicks = 0;
        double moved = here().distanceSqr(livenessSample);
        livenessSample = here();
        boolean expectsMovement = soulTarget != null || navigationGoal != null || explorationTarget != null
            || route == Route.APPROACH_CANYON || route == Route.ENTER_CANYON || route == Route.DESCEND_CANYON
            || route == Route.LEAVE_EMPTY_CANYON;
        if (!expectsMovement || moved >= 0.09) {
            livenessStallSamples = 0;
            return;
        }
        if (++livenessStallSamples < 3) {
            return;
        }
        livenessStallSamples = 0;
        env.navigation().stop();
        navigationGoal = null;
        repathCooldown = 0;
        nextAllowedPathfindTick = env.gameTime();
        if (soulTarget != null) {
            soulApproach = null;
            routeFailures = Math.min(2, routeFailures + 1);
            return;
        }
        if (route == Route.EXPLORE) {
            forceExplorationTurn(true);
        } else {
            routeFailures += 3;
        }
    }

    /** Once a second while carrying: a golem that has not moved 0.4 blocks for two or three samples recovers its route. */
    private void tickCarryWatchdog() {
        if (++carryWatchdogTicks < 20) {
            return;
        }
        carryWatchdogTicks = 0;
        double moved = here().distanceSqr(carrySample);
        carrySample = here();
        SoulExchange.Singer singer = resolveSinger();
        Player owner = resolveBondedPlayer();
        boolean waitingForHandshake = route == Route.DELIVER
            && (singer != null && here().distanceSqr(singer.position()) <= 9.0
            || owner != null && here().distanceSqr(owner.position()) <= 9.0);
        if (moved >= 0.16 || waitingForHandshake) {
            carryStallSamples = 0;
            if (moved >= 1.0) {
                carryRecoveryStage = Math.max(0, carryRecoveryStage - 1);
            }
            return;
        }
        int toleratedSamples = route == Route.EXIT_CANYON ? 2 : 3;
        if (++carryStallSamples < toleratedSamples) {
            return;
        }
        carryStallSamples = 0;
        recoverCarryRoute();
    }

    private void recoverCarryRoute() {
        carryRecoveryStage++;
        env.navigation().stop();
        navigationGoal = null;
        repathCooldown = 0;
        routeFailures = Math.max(routeFailures, 7);
        nextAllowedPathfindTick = env.gameTime();
        if (route == Route.EXIT_CANYON) {
            if (!exitMidpointPassed) {
                exitMidpointPassed = true;
            }
            SiftPos emergency = findReachableSurfaceExit();
            if (emergency != null) {
                canyonEntrance = emergency;
                routeFailures = 0;
                navigateExitToward(emergency, 0.86);
                return;
            }
            explorationHeading += (random.nextBoolean() ? 1.0 : -1.0) * (0.48 + Math.min(0.72, carryRecoveryStage * 0.12));
            tickEmergencyAscent();
            return;
        }
        if (route == Route.DELIVER) {
            singerTarget = null;
            targetCooldown = 0;
            patrolTarget = null;
            patrolCooldown = 0;
            explorationTarget = null;
            explorationTargetTicks = 0;
            explorationHeading += (random.nextDouble() - 0.5) * 0.72;
            tickExploration();
        }
    }

    private void resetCarryWatchdog() {
        carrySample = here();
        carryWatchdogTicks = 0;
        carryStallSamples = 0;
        carryRecoveryStage = 0;
    }

    /** {@code assistLocalStep}: a golem pressed against a step it should climb gets a small hop every sixth tick. */
    private void assistLocalStep() {
        if (!(env.horizontalCollision() && env.onGround() && env.navigation().isInProgress()
            && (age + env.entityId()) % 6 == 0)) {
            return;
        }
        Integer next = env.navigation().nextNodeY();
        if (next == null || next < blockPosition().y()) {
            return;
        }
        env.hop(0.34);
    }

    // --- Soul Blocks ----------------------------------------------------------------------------

    /** The recorded Soul Blocks near a position that are still Soul Blocks in a loaded chunk. */
    private List<SiftPos> activeSoulBlocksNear(SiftPos center, int horizontal, int vertical) {
        List<SiftPos> result = new ArrayList<>();
        for (SiftPos candidate : env.landmarks().soulBlocksNear(center, horizontal, vertical)) {
            if (env.isLoaded(candidate) && env.isSoulBlock(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private SoulCandidate findReachableSoulBlock(SiftPos origin, int horizontal, int vertical) {
        long now = env.gameTime();
        purgeExpiredRejections(now);
        List<SiftPos> candidates = new ArrayList<>(activeSoulBlocksNear(origin, horizontal, vertical));
        if (now >= nextPhysicalSoulScanTick) {
            nextPhysicalSoulScanTick = now + 40L;
            collectPhysicalSoulBlocks(blockPosition(), Math.min(horizontal, 12), Math.min(vertical, 8), candidates, 20);
        }
        SiftPos here = blockPosition();
        candidates.sort(Comparator.comparingDouble(candidate -> candidate.distSqr(here)));
        for (SiftPos candidate : candidates) {
            if (!env.isLoaded(candidate) || !env.isSoulBlock(candidate)) {
                forgetSoulFailure(candidate);
                continue;
            }
            if (isSoulReservedByOther(candidate)) {
                continue;
            }
            if (env.handsCanReach(candidate)) {
                forgetSoulFailure(candidate);
                return new SoulCandidate(candidate, blockPosition());
            }
            Long rejectedUntil = unreachableSoulBlocks.get(candidate.asLong());
            if (rejectedUntil != null && rejectedUntil > now || isSoulGloballyRejected(candidate, now)) {
                continue;
            }
            return new SoulCandidate(candidate, null);
        }
        return null;
    }

    /** Soul Blocks around the golem found by looking at the world, in rings outward and column by column from its height. */
    private void collectPhysicalSoulBlocks(SiftPos center, int horizontal, int vertical, List<SiftPos> output, int maximumAdded) {
        int added = 0;
        for (int ring = 0; ring <= horizontal; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || dx * dx + dz * dz > horizontal * horizontal) {
                        continue;
                    }
                    if (!env.isLoaded(new SiftPos(center.x() + dx, center.y(), center.z() + dz))) {
                        continue;
                    }
                    for (int index = 0; index <= vertical * 2; index++) {
                        int magnitude = (index + 1) / 2;
                        int dy = index == 0 ? 0 : index % 2 == 1 ? -magnitude : magnitude;
                        SiftPos found = new SiftPos(center.x() + dx, center.y() + dy, center.z() + dz);
                        if (!env.isSoulBlock(found) || output.contains(found)) {
                            continue;
                        }
                        output.add(found);
                        if (++added >= maximumAdded) {
                            return;
                        }
                    }
                }
            }
        }
    }

    /** A spot next to the Soul Block, up to a block and a half away, that the golem can walk to. */
    private SiftPos findReachableSoulApproach(SiftPos soul) {
        if (env.handsCanReach(soul)) {
            return blockPosition();
        }
        List<SiftPos> approaches = new ArrayList<>();
        for (int yOffset = -1; yOffset <= 2; yOffset++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    approaches.add(soul.offset(dx, yOffset, dz));
                }
            }
        }
        Path path = env.navigation().createPath(approaches, 1);
        if (path == null || path.end() == null || path.end().distSqr(soul.above()) > 3.4F) {
            return null;
        }
        return path.end();
    }

    private boolean isSoulReservedByOther(SiftPos soul) {
        Map<Long, UUID> reservations = env.shared().reservations();
        UUID owner = reservations.get(soul.asLong());
        if (owner == null || owner.equals(env.uuid())) {
            return false;
        }
        Optional<SiftPos> theirTarget = env.soulTargetOf(owner);
        if (theirTarget.isPresent() && theirTarget.get().equals(soul)) {
            return true;
        }
        reservations.remove(soul.asLong(), owner);
        return false;
    }

    private void claimSoulTarget(SoulCandidate candidate) {
        clearSoulTargetReservation();
        soulTarget = candidate.soul();
        soulApproach = candidate.approach();
        env.shared().reservations().put(soulTarget.asLong(), env.uuid());
    }

    private void rejectCurrentSoulTarget(long duration) {
        if (soulTarget != null) {
            rememberSoulFailure(soulTarget, duration);
        }
        clearSoulTargetReservation();
    }

    /** One failure keeps a Soul Block off the list for 5 seconds, two for 20, more for two minutes; all forgotten after 10 minutes. */
    private void rememberSoulFailure(SiftPos soul, long minimumDuration) {
        long now = env.gameTime();
        long packed = soul.asLong();
        GolemShared.SoulFailure previous = env.shared().failures().get(packed);
        int count = previous != null && previous.forgetAfter() > now ? Math.min(4, previous.failures() + 1) : 1;
        long penalty = switch (count) {
            case 1 -> 100L;
            case 2 -> 400L;
            default -> 2400L;
        };
        penalty = Math.max(penalty, minimumDuration);
        long retryAfter = now + penalty;
        env.shared().failures().put(packed, new GolemShared.SoulFailure(count, retryAfter, retryAfter + 12000L));
        unreachableSoulBlocks.put(packed, retryAfter);
    }

    private boolean isSoulGloballyRejected(SiftPos soul, long gameTime) {
        GolemShared.SoulFailure memory = env.shared().failures().get(soul.asLong());
        if (memory == null) {
            return false;
        }
        if (memory.forgetAfter() <= gameTime) {
            env.shared().failures().remove(soul.asLong(), memory);
            return false;
        }
        return memory.retryAfter() > gameTime;
    }

    private void forgetSoulFailure(SiftPos soul) {
        if (soul == null) {
            return;
        }
        long packed = soul.asLong();
        unreachableSoulBlocks.remove(packed);
        env.shared().failures().remove(packed);
    }

    private void clearSoulTargetReservation() {
        if (soulTarget != null) {
            env.shared().reservations().remove(soulTarget.asLong(), env.uuid());
        }
        soulTarget = null;
        soulApproach = null;
    }

    // --- receivers ------------------------------------------------------------------------------

    private SoulExchange.Singer resolveSinger() {
        if (!canInteractWithSinger() || singerTarget == null) {
            singerTarget = null;
            return null;
        }
        SoulExchange.Singer singer = env.singer(singerTarget);
        if (singer != null && singer.isAlive() && singer.isSoulEvent()) {
            return singer;
        }
        singerTarget = null;
        return null;
    }

    /** The bonded player when online; a golem bonded by name only binds to the first player with that name. */
    private Player resolveBondedPlayer() {
        if (bondedPlayer != null) {
            Player byId = env.player(bondedPlayer);
            if (byId != null) {
                bondedPlayerName = byId.name();
                return byId;
            }
        }
        if (bondedPlayerName == null || bondedPlayerName.isBlank()) {
            return null;
        }
        Player byName = env.playerNamed(bondedPlayerName);
        if (byName == null) {
            return null;
        }
        bondedPlayer = byName.id();
        bondedPlayerName = byName.name();
        bonded = true;
        env.keepLoaded();
        return byName;
    }

    // --- helpers --------------------------------------------------------------------------------

    private SiftPoint here() {
        return new SiftPoint(env.x(), env.y(), env.z());
    }

    private SiftPos blockPosition() {
        return SiftPos.containing(env.x(), env.y(), env.z());
    }

    private double horizontalDistanceSqr(SiftPos pos) {
        double dx = pos.x() + 0.5 - env.x();
        double dz = pos.z() + 0.5 - env.z();
        return dx * dx + dz * dz;
    }

    private boolean isNear(SiftPos pos, double horizontal, double vertical) {
        return pos != null && horizontalDistanceSqr(pos) <= horizontal * horizontal && Math.abs(env.y() - pos.y()) <= vertical;
    }

    // --- saving ---------------------------------------------------------------------------------

    Snapshot snapshot() {
        return new Snapshot(mission.ordinal(), hasSoul, stateTicks, route.ordinal(), routeTicks, packed(soulTarget),
            packed(soulApproach), packed(canyonEntrance), packed(canyonMidpoint), packed(canyonCluster), packed(ignoredCanyon),
            ignoredCanyonTicks, enteredCanyon, exitMidpointPassed, explorationHeading, singerTarget, bondedPlayer,
            bondedPlayerName, packed(deliveryAnchor));
    }

    /** {@code readAdditionalSaveData}: a saved golem carries on where it was, minus the walking it was doing. */
    void restore(Snapshot saved) {
        mission = Mission.values()[Math.max(0, Math.min(Mission.values().length - 1, saved.mission()))];
        hasSoul = saved.hasSoul();
        stateTicks = Math.max(0, saved.stateTicks());
        Integer savedRoute = saved.route();
        boolean routeSaved = savedRoute != null && savedRoute >= 0 && savedRoute < Route.values().length;
        route = routeSaved ? Route.values()[savedRoute] : hasSoul ? Route.EXIT_CANYON : Route.EXPLORE;
        routeTicks = Math.max(0, saved.routeTicks());
        soulTarget = position(saved.soulTarget());
        soulApproach = position(saved.soulApproach());
        canyonEntrance = position(saved.canyonEntrance());
        canyonMidpoint = position(saved.canyonMidpoint());
        canyonCluster = position(saved.canyonCluster());
        ignoredCanyon = position(saved.ignoredCanyon());
        ignoredCanyonTicks = Math.max(0, saved.ignoredCanyonTicks());
        enteredCanyon = saved.enteredCanyon();
        exitMidpointPassed = saved.exitMidpointPassed() != null ? saved.exitMidpointPassed() : !needsCanyonMidpointForExit();
        explorationHeading = saved.explorationHeading();
        singerTarget = saved.singerTarget();
        bondedPlayer = saved.bondedPlayer();
        bondedPlayerName = saved.bondedPlayerName() == null || saved.bondedPlayerName().isBlank() ? null : saved.bondedPlayerName();
        bonded = bondedPlayer != null || bondedPlayerName != null;
        deliveryAnchor = position(saved.deliveryAnchor());
        if (bonded) {
            env.keepLoaded();
            singerTarget = null;
            if (hasSoul) {
                mission = Mission.CARRY;
                stateTicks = 0;
                if (route != Route.EXIT_CANYON && route != Route.DELIVER) {
                    route = canyonEntrance == null ? Route.DELIVER : Route.EXIT_CANYON;
                }
            }
        }
        navigationGoal = null;
        repathCooldown = 0;
        routeFailures = 0;
        resetRouteProgressSample();
        resetCarryWatchdog();
        if (!routeSaved && !hasSoul && canyonCluster != null && canyonEntrance != null) {
            route = enteredCanyon ? Route.SEEK_SOUL : Route.APPROACH_CANYON;
        }
    }

    private static Long packed(SiftPos pos) {
        return pos == null ? null : pos.asLong();
    }

    private static SiftPos position(Long packed) {
        return packed == null ? null : SiftPos.of(packed);
    }
}
