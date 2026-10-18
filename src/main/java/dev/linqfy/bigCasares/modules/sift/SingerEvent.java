package dev.linqfy.bigCasares.modules.sift;

import java.util.Optional;
import java.util.UUID;

/**
 * The life of a Singer that comes for a soul, the Bukkit-free part of the mod's {@code SingerEntity}: she
 * rises ({@link Phase#APPEAR}), roams, walks to an Echo Golem that offers a Soul Block and receives it, takes
 * Soul Blocks from players for a gift ({@link Phase#BARTER}) and, unless she is a permanent roamer, sinks
 * back into the ground after 30 seconds of roaming. {@link #tick} advances one server tick and asks the
 * {@link Env} for everything that touches the world.
 *
 * <p>Phase ordinals and the tick numbers inside each phase are the mod's: receiving takes 48 ticks, the
 * Soul Block changes hands on tick 22 and leaves the Singer's hands on tick 32; a barter takes 40 ticks.</p>
 */
final class SingerEvent {

    /** The mod's {@code SoulEventPhase}; the ordinal is what the mod saves. */
    enum Phase {
        APPEAR,
        APPROACH,
        EXCHANGE,
        ROAMING,
        DISAPPEAR,
        BARTER
    }

    static final int ROAM_TICKS = 600;
    static final int BARTER_TICKS = 40;
    static final int SOUL_TRANSFER_TICK = 22;
    static final int SOUL_RELEASE_TICK = 32;
    static final int EXCHANGE_TICKS = 48;
    /** A golem farther than this (squared, 10 blocks) is not worth approaching. */
    static final double APPROACH_RANGE_SQUARED = 100.0;
    static final double GIVE_UP_RANGE_SQUARED = 144.0;
    static final double ARRIVED_RANGE_SQUARED = 6.25;
    static final double BARTER_PARTNER_RANGE_SQUARED = 256.0;
    static final int GOLEM_SCAN_INTERVAL = 40;
    static final int APPROACH_REPATH_INTERVAL = 15;

    static final double APPROACH_SPEED = 0.92;
    static final double ROAM_SPEED = 0.78;

    /** Everything the Singer reads or does in the world. Positions are those of the Singer. */
    interface Env {
        /** The golem with this id when it is loaded, alive and not bonded; else null ({@code resolveSoulGolem}). */
        SoulExchange.Golem golem(UUID id);

        /** Whether an entity with this id is loaded at all, in any state. */
        boolean isLoaded(UUID id);

        /** The nearest golem within 48x28x48 blocks that carries a soul and is not bonded; else null. */
        SoulExchange.Golem nearestOfferingGolem();

        double distanceSqrTo(SoulExchange.Golem golem);

        SiftPoint position();

        /** The player of a barter when online, in this world and within 16 blocks. */
        Optional<SiftPoint> barterPartner(UUID player);

        /** Whether the Singer carries a custom name: named Singers never leave. */
        boolean isNamed();

        void playAppearEffects();

        void playDisappearEffects();

        /** Ends the intangible phase: collisions, gravity and AI on. */
        void becomeSolid();

        /** Starts the intangible phase: no AI, no gravity, standing still. */
        void becomeIntangible();

        void stopNavigation();

        boolean navigationDone();

        void moveToGolem(SoulExchange.Golem golem, double speed);

        void faceToward(SiftPoint point);

        void lookAt(SoulExchange.Golem golem);

        void playHappy(float pitch);

        /** The Soul Block in the Singer's hands appears or goes. */
        void holdSoulBlock(boolean holding);

        /** Walks to and takes a dropped Soul Block when one is near; true while that is going on. */
        boolean seekPayment();

        /** Keeps away from a Soul Canyon that has Soul Blocks; true while it does. */
        boolean avoidSoulCanyon();

        /** Picks a random spot that does not lead into a canyon and walks to it. */
        void roam(double speed);

        /** Gives the gift at the point (where the player stood, or in front of the Singer if none). */
        void reward(UUID player, SiftPoint target);

        void discard();

        /** The phase or progress changed in a way worth saving and showing. */
        void phaseChanged();
    }

    private final SingerTimings timings;
    private final java.util.Random random;
    private Phase phase = Phase.APPEAR;
    private int phaseTick;
    private UUID golemId;
    private boolean permanent;
    private boolean holdingSoul;
    private int roamCooldown;
    private UUID barterPlayer;
    private SiftPoint barterTarget;

    SingerEvent(SingerTimings timings, java.util.Random random) {
        this.timings = timings;
        this.random = random;
    }

    // --- state ------------------------------------------------------------------------------------

    Phase phase() {
        return phase;
    }

    int phaseTick() {
        return phaseTick;
    }

    UUID golemId() {
        return golemId;
    }

    boolean isPermanent() {
        return permanent;
    }

    boolean isHoldingSoul() {
        return holdingSoul;
    }

    UUID barterPlayer() {
        return barterPlayer;
    }

    SiftPoint barterTarget() {
        return barterTarget;
    }

    /** Whether she is on the ground and listening: the only time she takes a Soul Block. */
    boolean canBarter(boolean alive) {
        return alive && phase == Phase.ROAMING;
    }

    /** {@code beginSoulEvent}: rises for this golem (or for nobody, when {@code golemId} is null). */
    void begin(UUID golemId, Env env) {
        permanent = false;
        this.golemId = golemId;
        roamCooldown = 0;
        env.becomeIntangible();
        setPhase(Phase.APPEAR, env);
    }

    /** A spawn egg or command: a roamer that never leaves. */
    void beginPermanent(Env env) {
        begin(null, env);
        permanent = true;
    }

    /** {@code enterPersistentRoaming}: a named Singer in the middle of her exit stays. */
    void stay(Env env) {
        permanent = true;
        env.becomeSolid();
        setPhase(Phase.ROAMING, env);
    }

    /** Takes the saved state back after a restart; the held block is derived as the mod does. */
    void restore(Phase phase, int phaseTick, UUID golemId, boolean permanent, UUID barterPlayer, SiftPoint barterTarget) {
        this.phase = phase;
        this.phaseTick = Math.max(0, phaseTick);
        this.golemId = golemId;
        this.permanent = permanent;
        this.barterPlayer = barterPlayer;
        this.barterTarget = barterTarget;
        this.holdingSoul = phase == Phase.BARTER && this.phaseTick < BARTER_TICKS
            || phase == Phase.EXCHANGE && this.phaseTick >= SOUL_TRANSFER_TICK && this.phaseTick < SOUL_RELEASE_TICK;
    }

    /** Whether she is in a phase in which she is not part of the world (no body to hit). */
    boolean isIntangible() {
        return phase == Phase.APPEAR || phase == Phase.DISAPPEAR;
    }

    // --- golem offers -------------------------------------------------------------------------------

    /** {@code offerSoulGolem}: a golem with a soul asks to be received. */
    void offer(SoulExchange.Golem golem, Env env) {
        if (!(golem.hasSoulBlock() && golem.canInteractWithSinger())) {
            return;
        }
        if (phase == Phase.ROAMING || golemId != null && !env.isLoaded(golemId)) {
            golemId = golem.id();
        }
    }

    // --- barter -------------------------------------------------------------------------------------

    /** {@code beginBarter}: takes a Soul Block from this player (or off the ground when {@code player} is null). */
    void beginBarter(UUID player, SiftPoint target, Env env) {
        barterPlayer = player;
        barterTarget = target;
        env.stopNavigation();
        setPhase(Phase.BARTER, env);
        holdingSoul = true;
        env.holdSoulBlock(true);
        if (player != null && target != null) {
            env.faceToward(target);
        }
        env.playHappy(1.0F);
    }

    // --- the tick -----------------------------------------------------------------------------------

    /** {@code tickSoulEvent}: one server tick. */
    void tick(Env env) {
        phaseTick++;
        switch (phase) {
            case APPEAR -> {
                if (phaseTick == 1) {
                    env.playAppearEffects();
                }
                if (phaseTick >= timings.appearTicks()) {
                    env.becomeSolid();
                    setPhase(Phase.ROAMING, env);
                }
            }
            case APPROACH -> tickApproach(env);
            case EXCHANGE -> tickExchange(env);
            case ROAMING -> tickRoaming(env);
            case BARTER -> tickBarter(env);
            case DISAPPEAR -> {
                env.stopNavigation();
                env.becomeIntangible();
                if (phaseTick == 1) {
                    env.playDisappearEffects();
                }
                if (phaseTick >= timings.disappearTicks()) {
                    env.discard();
                }
            }
        }
    }

    private void tickApproach(Env env) {
        SoulExchange.Golem golem = golem(env);
        if (golem == null || !golem.hasSoulBlock() || !golem.canInteractWithSinger()) {
            golemId = null;
            setPhase(Phase.ROAMING, env);
            return;
        }
        env.faceToward(golem.position());
        if (phaseTick % APPROACH_REPATH_INTERVAL == 1 || env.navigationDone()) {
            env.moveToGolem(golem, APPROACH_SPEED);
        }
        double distance = env.distanceSqrTo(golem);
        if (distance > GIVE_UP_RANGE_SQUARED) {
            setPhase(Phase.ROAMING, env);
            return;
        }
        if (distance <= ARRIVED_RANGE_SQUARED) {
            env.stopNavigation();
            golem.beginBow();
            setPhase(Phase.EXCHANGE, env);
        }
    }

    private void tickExchange(Env env) {
        SoulExchange.Golem golem = golem(env);
        env.stopNavigation();
        if (golem == null) {
            golemId = null;
            setPhase(Phase.ROAMING, env);
            return;
        }
        env.faceToward(golem.position());
        golem.faceToward(env.position());
        if (phaseTick < SOUL_TRANSFER_TICK && !golem.hasSoulBlock()) {
            golem.finishBow();
            golemId = null;
            setPhase(Phase.ROAMING, env);
            return;
        }
        env.lookAt(golem);
        if (phaseTick == SOUL_TRANSFER_TICK) {
            if (!golem.transferSoulToSinger()) {
                golem.finishBow();
                golemId = null;
                setPhase(Phase.ROAMING, env);
                return;
            }
            holdingSoul = true;
            env.holdSoulBlock(true);
            env.playHappy(0.96F + random.nextFloat() * 0.08F);
        }
        if (phaseTick >= SOUL_RELEASE_TICK && holdingSoul) {
            holdingSoul = false;
            env.holdSoulBlock(false);
        }
        if (phaseTick >= EXCHANGE_TICKS) {
            golem.finishBow();
            golemId = null;
            setPhase(Phase.ROAMING, env);
        }
    }

    private void tickRoaming(Env env) {
        if (env.seekPayment()) {
            return;
        }
        if (env.avoidSoulCanyon()) {
            return;
        }
        SoulExchange.Golem assigned = golem(env);
        if (assigned != null && assigned.hasSoulBlock() && assigned.canInteractWithSinger()
            && env.distanceSqrTo(assigned) <= APPROACH_RANGE_SQUARED) {
            setPhase(Phase.APPROACH, env);
            return;
        }
        if (phaseTick % GOLEM_SCAN_INTERVAL == 1) {
            SoulExchange.Golem nearby = env.nearestOfferingGolem();
            if (nearby != null) {
                golemId = nearby.id();
                if (env.distanceSqrTo(nearby) <= APPROACH_RANGE_SQUARED) {
                    setPhase(Phase.APPROACH, env);
                    return;
                }
            }
        }
        if (--roamCooldown <= 0 || env.navigationDone()) {
            roamCooldown = 60 + random.nextInt(61);
            env.roam(ROAM_SPEED);
        }
        if (!permanent && !env.isNamed() && phaseTick >= ROAM_TICKS) {
            setPhase(Phase.DISAPPEAR, env);
        }
    }

    private void tickBarter(Env env) {
        env.stopNavigation();
        if (barterPlayer != null) {
            Optional<SiftPoint> partner = env.barterPartner(barterPlayer);
            if (partner.isPresent()) {
                barterTarget = partner.get();
                env.faceToward(partner.get());
            }
        }
        if (phaseTick >= BARTER_TICKS) {
            holdingSoul = false;
            env.holdSoulBlock(false);
            UUID player = barterPlayer;
            SiftPoint target = barterTarget;
            barterPlayer = null;
            barterTarget = null;
            env.reward(player, target);
            setPhase(Phase.ROAMING, env);
        }
    }

    private SoulExchange.Golem golem(Env env) {
        return golemId == null ? null : env.golem(golemId);
    }

    /** {@code setSoulEventPhase}: a new phase starts at tick 0, and only the exchange and the barter hold the block. */
    private void setPhase(Phase next, Env env) {
        if (next != Phase.EXCHANGE && next != Phase.BARTER && holdingSoul) {
            holdingSoul = false;
            env.holdSoulBlock(false);
        }
        phase = next;
        phaseTick = 0;
        env.phaseChanged();
    }
}
