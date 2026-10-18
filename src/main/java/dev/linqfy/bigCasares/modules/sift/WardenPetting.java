package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.DarkSnifferBrain.Combatant;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The mod's {@code WardenDarkSnifferMixin}: a calm Warden now and then walks to a Dark Sniffer
 * within 18 blocks and pets it for 100 ticks (a heart every 20 ticks, from the 8th), which keeps the
 * sniffer from hunting for that time. One instance belongs to one Warden and is ticked every tick.
 *
 * <p>Timing as in the mod: the first search comes 800 to 1600 ticks after the first tick; it only
 * happens if the Warden is completely calm on the tick the countdown ends (otherwise the countdown
 * restarts); a search without a candidate waits 300 to 600 ticks, a petting that was interrupted 600
 * to 1200 and a completed one 2400 to 4800. The Warden approaches at speed 0.55 for at most 240
 * ticks and pets from within 3.5 blocks.</p>
 */
final class WardenPetting {

    static final double SEARCH_RANGE = 18.0;
    static final double ABANDON_DISTANCE_SQUARED = 324.0;
    static final double PETTING_DISTANCE_SQUARED = 12.25;
    static final int PETTING_DURATION = 100;
    static final int SNIFFER_HOLD_TICKS = 260;
    static final int SNIFFER_REFRESH_TICKS = 12;
    static final int MAX_APPROACH_TICKS = 240;
    static final double APPROACH_SPEED = 0.55;
    static final float LOOK_SPEED = 22.0F;
    private static final int HEART_PHASE = 8;
    private static final int HEART_INTERVAL = 20;

    /** A Dark Sniffer that can be petted. */
    interface Pettable extends Combatant {
        boolean availableForPetting();

        void beginPetting(Combatant warden, int ticks);

        void endPetting(Combatant warden);
    }

    /** The Warden and the world around it. */
    interface Keeper extends Combatant {
        /** {@code isCompletelyCalm}. */
        boolean calm();

        /** The Dark Sniffers within {@link #SEARCH_RANGE} blocks that are available. */
        List<Pettable> availableSniffersNearby();

        void lookAt(Combatant target, float speed);

        void moveTo(Combatant target, double speed);

        void stopNavigation();

        void heart(Pettable sniffer);
    }

    private final Random random;
    private Pettable target;
    private int pettingTicks;
    private int approachTicks;
    private int searchCooldown = -1;

    WardenPetting(Random random) {
        this.random = random;
    }

    void tick(Keeper warden) {
        if (target != null) {
            tickActive(warden);
            return;
        }
        if (searchCooldown < 0) {
            searchCooldown = randomDelay(800, 1600);
        }
        if (searchCooldown-- > 0 || !warden.calm()) {
            return;
        }
        Pettable candidate = warden.availableSniffersNearby().stream()
            .min(Comparator.comparingDouble(sniffer -> warden.position().distanceSquared(sniffer.position())))
            .orElse(null);
        if (candidate == null) {
            searchCooldown = randomDelay(300, 300);
            return;
        }
        target = candidate;
        approachTicks = 0;
        pettingTicks = 0;
        candidate.beginPetting(warden, SNIFFER_HOLD_TICKS);
    }

    private void tickActive(Keeper warden) {
        Pettable sniffer = target;
        double distanceSquared = warden.position().distanceSquared(sniffer.position());
        if (!sniffer.alive() || distanceSquared > ABANDON_DISTANCE_SQUARED || !warden.calm()) {
            stop(warden, false);
            return;
        }
        warden.lookAt(sniffer, LOOK_SPEED);
        sniffer.beginPetting(warden, SNIFFER_REFRESH_TICKS);
        if (distanceSquared > PETTING_DISTANCE_SQUARED) {
            if (++approachTicks > MAX_APPROACH_TICKS) {
                stop(warden, false);
                return;
            }
            warden.moveTo(sniffer, APPROACH_SPEED);
            return;
        }
        warden.stopNavigation();
        if (pettingTicks % HEART_INTERVAL == HEART_PHASE) {
            warden.heart(sniffer);
        }
        if (++pettingTicks >= PETTING_DURATION) {
            stop(warden, true);
        }
    }

    private void stop(Keeper warden, boolean completed) {
        if (target != null) {
            target.endPetting(warden);
        }
        target = null;
        pettingTicks = 0;
        approachTicks = 0;
        searchCooldown = completed ? randomDelay(2400, 2400) : randomDelay(600, 600);
    }

    private int randomDelay(int minimum, int variation) {
        return minimum + random.nextInt(variation + 1);
    }

    boolean petting() {
        return target != null;
    }

    int searchCooldown() {
        return searchCooldown;
    }
}
