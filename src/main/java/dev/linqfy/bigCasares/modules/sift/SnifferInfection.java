package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.SiftGeometry.Vec;

import java.util.Optional;
import java.util.Random;

/**
 * The mod's sculk infection of ordinary sniffers ({@code SnifferInfectionMixin}): a sniffer that
 * starts digging on sculk is infected, shakes for {@value #CONVERSION_TICKS} ticks and then turns into
 * a Dark Sniffer (it keeps its health). An infected sniffer digs up nothing. Every ordinary sniffer
 * within {@value #FLEE_RANGE} blocks of a Dark Sniffer runs away from the nearest one.
 */
final class SnifferInfection {

    static final int CONVERSION_TICKS = 600;
    static final double FLEE_RANGE = 20.0;
    static final int FLEE_HORIZONTAL = 16;
    static final int FLEE_VERTICAL = 8;
    static final double FLEE_PATH_SPEED = 1.25;
    /** How far in front of a sniffer its dug block is: {@code position + forward * 2.25}. */
    static final double HEAD_REACH = 2.25;
    private static final double RIGHT_ANGLE = Math.PI / 2.0;

    private int ticks;

    SnifferInfection(int ticks) {
        this.ticks = Math.max(0, ticks);
    }

    boolean infected() {
        return ticks > 0;
    }

    int ticks() {
        return ticks;
    }

    /** The sniffer entered the digging state; sculk under its head starts the infection unless one runs. */
    void diggingStarted(boolean sculkUnderHead) {
        if (sculkUnderHead && ticks <= 0) {
            ticks = 1;
        }
    }

    /** One tick; true on the tick the infection completes. */
    boolean tick() {
        if (ticks <= 0) {
            return false;
        }
        ticks++;
        return ticks >= CONVERSION_TICKS;
    }

    /** The point in front of a sniffer standing at {@code position} and facing {@code yawDegrees} (Bukkit yaw). */
    static Vec headPoint(Vec position, float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new Vec(position.x() - Math.sin(yaw) * HEAD_REACH, position.y(), position.z() + Math.cos(yaw) * HEAD_REACH);
    }

    /**
     * {@code DefaultRandomPos.getPosAway(sniffer, 16, 8, threat)}: one random offset within 90 degrees of
     * straight away from the threat and within 16 blocks sideways (a root-weighted distance, so the
     * far end is rarer), 8 up or down; empty when the draw falls outside the box.
     */
    static Optional<Vec> awayOffset(Random random, Vec sniffer, Vec threat) {
        double awayX = sniffer.x() - threat.x();
        double awayZ = sniffer.z() - threat.z();
        double angle = Math.atan2(awayZ, awayX) - RIGHT_ANGLE;
        double randomAngle = angle + (2.0F * random.nextFloat() - 1.0F) * RIGHT_ANGLE;
        double distance = Math.sqrt(random.nextDouble()) * Math.sqrt(2.0) * FLEE_HORIZONTAL;
        double x = -distance * Math.sin(randomAngle);
        double z = distance * Math.cos(randomAngle);
        if (Math.abs(x) > FLEE_HORIZONTAL || Math.abs(z) > FLEE_HORIZONTAL) {
            return Optional.empty();
        }
        double y = random.nextInt(2 * FLEE_VERTICAL + 1) - FLEE_VERTICAL;
        return Optional.of(new Vec(x, y, z));
    }
}
