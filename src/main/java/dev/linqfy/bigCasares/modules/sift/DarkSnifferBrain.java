package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.SiftGeometry.Aabb;
import dev.linqfy.bigCasares.modules.sift.SiftGeometry.Vec;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The decisions of the mod's {@code DarkSnifferEntity#tick}, written against a small {@link Body}
 * view so they run without a server; {@code SiftDarkSniffers} adapts a Bukkit sniffer to {@link Body}.
 * One brain belongs to one Dark Sniffer and keeps the mod's counters.
 *
 * <p>Order of a tick, as in the mod: cooldowns count down and the home is remembered; while a Warden
 * pets it, it drops its target and only looks at the Warden; on Peaceful it idles; during the sonic
 * sniff nothing else happens; otherwise a target that stopped being valid is dropped, a new target
 * (the nearest player not in Creative or Spectator, or the nearer ordinary sniffer within 32 blocks)
 * is picked every 10 ticks, and without one the sniffer wanders around its home at speed 0.1 (back to
 * it when more than 100 blocks away). With a target it runs at 0.3 (path speed 1.2, path refreshed
 * every 6 ticks), starts the sonic sniff when the target has been out of melee reach for 8 ticks at
 * 5 or more blocks in sight and the 100-tick cooldown is over, and bites every 24 ticks within 3.15
 * blocks.</p>
 *
 * <p>The sonic sniff: a 10-tick wind-up standing still in the sniffing pose, then a wave from the eye
 * towards where the victim will be (its horizontal velocity times the travel time, at most 20 ticks), at 1.5
 * blocks per tick for the distance plus 7.5 blocks. Every living creature the wave passes (its box
 * inflated by 0.8 against the box of the last step) is pulled once by 0.3 of the way to the sniffer.</p>
 */
final class DarkSnifferBrain {

    static final double AGGRO_RANGE = 32.0;
    static final double ATTACK_REACH_SQUARED = 3.15 * 3.15;
    static final int ATTACK_COOLDOWN_TICKS = 24;
    static final int SONIC_WINDUP_TICKS = 10;
    static final int SONIC_COOLDOWN_TICKS = 100;
    static final int SONIC_MIN_OUT_OF_REACH_TICKS = 8;
    static final double SONIC_MIN_DISTANCE_SQUARED = 25.0;
    static final double SONIC_WAVE_SPEED = 1.5;
    static final double SONIC_HIT_RADIUS = 0.8;
    static final double SONIC_OVERSHOOT_DISTANCE = 7.5;
    static final double SONIC_MAX_LEAD_TICKS = 20.0;
    static final double SONIC_PULL_STRENGTH = 0.3;
    /** Movement speed attribute without and with a target, and the path speed modifiers. */
    static final double NORMAL_SPEED = 0.1;
    static final double COMBAT_SPEED = 0.3;
    static final double CHASE_PATH_SPEED = 1.2;
    static final double WANDER_PATH_SPEED = 0.1;
    static final double HOME_RADIUS_SQUARED = 10000.0;
    static final int WANDER_RANGE = 25;
    static final int WANDER_ONE_IN = 30;
    static final double PETTING_LOOK_RANGE_SQUARED = 576.0;
    private static final int TARGET_REFRESH_TICKS = 10;
    private static final int PATH_REFRESH_TICKS = 6;
    private static final double MAX_CLIMB = 3.0;

    /** A creature the sniffer can target, pull or be petted by. */
    interface Combatant {
        UUID id();

        boolean alive();

        /** Feet position. */
        Vec position();

        Vec eyePosition();

        Vec velocity();

        Aabb box();
    }

    /** The two poses the brain forces; the sniffer's other states belong to its vanilla brain. */
    enum Pose {
        IDLING,
        SNIFFING
    }

    /** What the brain senses of its sniffer and the world, and how it commands them. */
    interface Body {
        boolean peaceful();

        Vec position();

        Vec eyePosition();

        /** The current target, or null. */
        Combatant target();

        void target(Combatant target);

        /** The nearest player within {@link #AGGRO_RANGE} that is alive, not in Creative and not a spectator. */
        Combatant nearestPlayer();

        /** The nearest ordinary (not dark) sniffer within {@link #AGGRO_RANGE}. */
        Combatant nearestOrdinarySniffer();

        /** {@code canAttack}: not on Peaceful, a player in survival or adventure or an ordinary sniffer. */
        boolean canAttack(Combatant candidate);

        boolean hasLineOfSight(Combatant candidate);

        boolean navigationDone();

        void moveTo(Vec destination, double pathSpeed);

        void moveTo(Combatant target, double pathSpeed);

        void stopNavigation();

        /** Zeroes the sniffer's own velocity. */
        void stopMoving();

        void lookAt(Combatant target, float horizontalSpeed, float verticalSpeed);

        void movementSpeed(double speed);

        void aggressive(boolean aggressive);

        /** {@code horizontalCollision && onGround}. */
        boolean blockedOnGround();

        void jump();

        /** {@code doHurtTarget}; whether the target was hurt. */
        boolean attack(Combatant target);

        void pose(Pose pose);

        /** The wave leaves the eye: sound and first particles. */
        void sonicLaunched(Vec start, Vec direction, int flightTicks);

        /** One step of the wave in flight. */
        void sonicMoved(Vec head);

        /** Living creatures whose box overlaps the area, other than the sniffer itself. */
        List<Combatant> livingIn(Aabb area);

        void pull(Combatant victim, Vec push);

        /** The home was set for the first time (so it can be saved). */
        void homeSet(Vec home);
    }

    private final Random random;
    private int targetRefreshCooldown;
    private int pathRefreshCooldown;
    private int attackCooldown;
    private int sonicCooldown;
    private int sonicWindupTicks;
    private int sonicFlightTicks;
    private int sonicFlightAge;
    private int ticksWithoutMeleeContact;
    private Combatant sonicVictim;
    private Combatant observedCombatTarget;
    private Vec sonicOrigin = Vec.ZERO;
    private Vec sonicDirection = Vec.ZERO;
    private final Set<UUID> sonicPulled = new HashSet<>();
    private Vec home;
    private boolean aggressive;
    private Combatant wardenPetter;
    private int wardenPettingTicks;

    DarkSnifferBrain(Random random) {
        this.random = random;
        this.targetRefreshCooldown = random.nextInt(10);
        this.pathRefreshCooldown = random.nextInt(6);
    }

    // --- the mod's tick ------------------------------------------------------------------

    void tick(Body body) {
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (sonicCooldown > 0) {
            sonicCooldown--;
        }
        if (home == null) {
            home = body.position();
            body.homeSet(home);
        }
        if (wardenPettingTicks > 0) {
            cancelSonicSniff();
            wardenPettingTicks--;
            clearCombatTarget(body);
            body.pose(Pose.IDLING);
            if (wardenPetter != null && wardenPetter.alive()
                && body.position().distanceSquared(wardenPetter.position()) <= PETTING_LOOK_RANGE_SQUARED) {
                body.lookAt(wardenPetter, 20.0F, 20.0F);
            } else {
                wardenPetter = null;
                wardenPettingTicks = 0;
            }
            return;
        }
        if (body.peaceful()) {
            cancelSonicSniff();
            clearCombatTarget(body);
            return;
        }
        if (sonicWindupTicks > 0 || sonicFlightTicks > 0) {
            tickSonicSniff(body);
            return;
        }
        Combatant target = body.target();
        if (!isValidTarget(body, target)) {
            target = null;
            body.target(null);
        }
        if (--targetRefreshCooldown <= 0) {
            targetRefreshCooldown = TARGET_REFRESH_TICKS;
            Combatant nearestPlayer = body.nearestPlayer();
            Combatant nearestSniffer = body.nearestOrdinarySniffer();
            target = nearestPlayer;
            if (nearestSniffer != null && (target == null || distanceSquared(body, nearestSniffer) < distanceSquared(body, target))) {
                target = nearestSniffer;
            }
            body.target(target);
        }
        if (target == null) {
            wander(body);
            return;
        }
        body.movementSpeed(COMBAT_SPEED);
        setAggressive(body, true);
        if (observedCombatTarget == null || !observedCombatTarget.id().equals(target.id())) {
            observedCombatTarget = target;
            ticksWithoutMeleeContact = 0;
        }
        double targetDistanceSquared = distanceSquared(body, target);
        ticksWithoutMeleeContact = targetDistanceSquared > ATTACK_REACH_SQUARED
            ? Math.min(SONIC_MIN_OUT_OF_REACH_TICKS, ticksWithoutMeleeContact + 1) : 0;
        boolean validSonicRange = targetDistanceSquared >= SONIC_MIN_DISTANCE_SQUARED && body.hasLineOfSight(target);
        if (sonicCooldown <= 0 && ticksWithoutMeleeContact >= SONIC_MIN_OUT_OF_REACH_TICKS && validSonicRange) {
            beginSonicSniff(body, target);
            return;
        }
        body.pose(Pose.IDLING);
        body.lookAt(target, 30.0F, 30.0F);
        if (--pathRefreshCooldown <= 0 || body.navigationDone()) {
            pathRefreshCooldown = PATH_REFRESH_TICKS;
            body.moveTo(target, CHASE_PATH_SPEED);
        }
        if (body.blockedOnGround() && Math.abs(target.position().y() - body.position().y()) <= MAX_CLIMB) {
            body.jump();
        }
        if (attackCooldown <= 0 && distanceSquared(body, target) <= ATTACK_REACH_SQUARED && body.attack(target)) {
            attackCooldown = ATTACK_COOLDOWN_TICKS;
            ticksWithoutMeleeContact = 0;
        }
    }

    private void wander(Body body) {
        observedCombatTarget = null;
        ticksWithoutMeleeContact = 0;
        body.movementSpeed(NORMAL_SPEED);
        setAggressive(body, false);
        Vec here = body.position();
        if (here.distanceSquared(home) > HOME_RADIUS_SQUARED) {
            if (body.navigationDone()) {
                body.moveTo(home, WANDER_PATH_SPEED);
            }
        } else if (body.navigationDone() && random.nextInt(WANDER_ONE_IN) == 0) {
            body.moveTo(new Vec(here.x() + random.nextInt(WANDER_RANGE) - 12.0, here.y(),
                here.z() + random.nextInt(WANDER_RANGE) - 12.0), WANDER_PATH_SPEED);
        }
    }

    // --- sonic sniff ----------------------------------------------------------------------

    private void beginSonicSniff(Body body, Combatant target) {
        sonicVictim = target;
        sonicWindupTicks = SONIC_WINDUP_TICKS;
        sonicFlightTicks = 0;
        sonicFlightAge = 0;
        sonicCooldown = SONIC_COOLDOWN_TICKS;
        ticksWithoutMeleeContact = 0;
        body.stopNavigation();
        body.stopMoving();
        body.lookAt(target, 30.0F, 30.0F);
        body.pose(Pose.SNIFFING);
    }

    private void tickSonicSniff(Body body) {
        Combatant victim = sonicVictim;
        if (victim == null || !victim.alive() || !body.canAttack(victim)) {
            cancelSonicSniff();
            body.pose(Pose.IDLING);
            return;
        }
        body.stopNavigation();
        body.stopMoving();
        body.lookAt(victim, 30.0F, 30.0F);
        if (sonicWindupTicks > 0) {
            sonicWindupTicks--;
            body.pose(Pose.SNIFFING);
            if (sonicWindupTicks == 0) {
                launchSonicWave(body, victim);
            }
            return;
        }
        body.pose(Pose.SNIFFING);
        Vec previous = sonicOrigin.plus(sonicDirection.scale(SONIC_WAVE_SPEED * sonicFlightAge));
        sonicFlightAge++;
        Vec wave = sonicOrigin.plus(sonicDirection.scale(SONIC_WAVE_SPEED * sonicFlightAge));
        body.sonicMoved(wave);
        Aabb searchBox = Aabb.between(previous, wave).inflate(SONIC_HIT_RADIUS);
        for (Combatant mob : body.livingIn(searchBox)) {
            if (mob.alive() && !sonicPulled.contains(mob.id())
                && mob.box().inflate(SONIC_HIT_RADIUS).intersects(previous, wave)) {
                pullOnce(body, mob);
            }
        }
        if (victim.box().inflate(SONIC_HIT_RADIUS).intersects(previous, wave)) {
            pullOnce(body, victim);
        }
        if (sonicFlightAge >= sonicFlightTicks) {
            cancelSonicSniff();
            body.pose(Pose.IDLING);
        }
    }

    private void launchSonicWave(Body body, Combatant victim) {
        Vec start = body.eyePosition();
        Vec targetPosition = victim.eyePosition();
        Vec directDelta = targetPosition.minus(start);
        double directDistance = directDelta.length();
        if (directDistance < 0.001 || !body.hasLineOfSight(victim)) {
            cancelSonicSniff();
            body.pose(Pose.IDLING);
            return;
        }
        double leadTicks = Math.min(SONIC_MAX_LEAD_TICKS, directDistance / SONIC_WAVE_SPEED);
        Vec velocity = victim.velocity();
        Vec predicted = new Vec(targetPosition.x() + velocity.x() * leadTicks, targetPosition.y(),
            targetPosition.z() + velocity.z() * leadTicks);
        Vec delta = predicted.minus(start);
        double predictedDistance = delta.length();
        if (predictedDistance < 0.001) {
            delta = directDelta;
            predictedDistance = directDistance;
        }
        sonicOrigin = start;
        sonicDirection = delta.scale(1.0 / predictedDistance);
        double travelDistance = predictedDistance + SONIC_OVERSHOOT_DISTANCE;
        sonicFlightTicks = Math.max(1, (int) Math.ceil(travelDistance / SONIC_WAVE_SPEED));
        sonicFlightAge = 0;
        body.pose(Pose.SNIFFING);
        body.sonicLaunched(start, sonicDirection, sonicFlightTicks);
    }

    private void pullOnce(Body body, Combatant victim) {
        if (!victim.alive() || !sonicPulled.add(victim.id())) {
            return;
        }
        Vec toSniffer = body.position().minus(victim.position());
        body.pull(victim, toSniffer.scale(SONIC_PULL_STRENGTH));
    }

    private void cancelSonicSniff() {
        sonicWindupTicks = 0;
        sonicFlightTicks = 0;
        sonicFlightAge = 0;
        sonicVictim = null;
        sonicOrigin = Vec.ZERO;
        sonicDirection = Vec.ZERO;
        sonicPulled.clear();
        ticksWithoutMeleeContact = 0;
    }

    // --- targets --------------------------------------------------------------------------

    private boolean isValidTarget(Body body, Combatant target) {
        return target != null && target.alive() && body.canAttack(target)
            && distanceSquared(body, target) <= AGGRO_RANGE * AGGRO_RANGE;
    }

    private static double distanceSquared(Body body, Combatant other) {
        return body.position().distanceSquared(other.position());
    }

    private void clearCombatTarget(Body body) {
        body.target(null);
        setAggressive(body, false);
        body.stopNavigation();
    }

    private void setAggressive(Body body, boolean value) {
        aggressive = value;
        body.aggressive(value);
    }

    // --- Warden petting (the mod's DarkSnifferEntity side) ---------------------------------

    /** {@code isAvailableForWardenPetting}. */
    boolean availableForWardenPetting(Body body, boolean alive) {
        return alive && !aggressive && !isValidTarget(body, body.target()) && wardenPettingTicks <= 0;
    }

    /** {@code beginWardenPetting}: the petting lasts at least {@code durationTicks} more ticks. */
    void beginWardenPetting(Body body, Combatant warden, int durationTicks) {
        wardenPetter = warden;
        wardenPettingTicks = Math.max(wardenPettingTicks, durationTicks);
        clearCombatTarget(body);
    }

    void endWardenPetting(Combatant warden) {
        if (wardenPetter != null && wardenPetter.id().equals(warden.id())) {
            wardenPetter = null;
            wardenPettingTicks = 0;
        }
    }

    // --- state ----------------------------------------------------------------------------

    void home(Vec home) {
        this.home = home;
    }

    Vec home() {
        return home;
    }

    boolean sonicActive() {
        return sonicWindupTicks > 0 || sonicFlightTicks > 0;
    }

    int sonicCooldown() {
        return sonicCooldown;
    }

    int attackCooldown() {
        return attackCooldown;
    }

    int wardenPettingTicks() {
        return wardenPettingTicks;
    }
}
