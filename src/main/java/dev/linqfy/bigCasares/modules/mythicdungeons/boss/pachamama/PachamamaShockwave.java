package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One chord of Mandinga: a ring that grows along the floor from where it was strummed (no server types). A player
 * on the ground whose distance to the centre is crossed by the front of the ring during a tick is hit once; jumping
 * over it (not on the ground when it passes) is the way out.
 */
public final class PachamamaShockwave {

    private final double centerX;
    private final double centerZ;
    private final Set<UUID> hit = new HashSet<>();
    private int steps;
    private double radius;
    private double previous;

    public PachamamaShockwave(double centerX, double centerZ) {
        this.centerX = centerX;
        this.centerZ = centerZ;
    }

    /** Grows the ring one tick; {@code false} once it is past {@link PachamamaTuning#WAVE_MAX_RADIUS}. */
    public boolean advance() {
        previous = radius;
        steps++;
        radius = steps * PachamamaTuning.WAVE_SPEED;
        return previous < PachamamaTuning.WAVE_MAX_RADIUS - 1.0e-9;
    }

    public double radius() {
        return radius;
    }

    public double centerX() {
        return centerX;
    }

    public double centerZ() {
        return centerZ;
    }

    /** Whether the front swept over the distance {@code distance} during the last {@link #advance()}. */
    public boolean sweeps(double distance) {
        return distance > previous - PachamamaTuning.WAVE_HALF_WIDTH
            && distance <= radius + PachamamaTuning.WAVE_HALF_WIDTH;
    }

    /**
     * Whether the player at (x, z) is hit now: the front passes over them while they are on the ground, once per
     * wave. A player in the air is not marked, but the front has moved on by the time they land.
     */
    public boolean hits(UUID player, double x, double z, boolean onGround) {
        if (!onGround || hit.contains(player)) {
            return false;
        }
        if (sweeps(Math.hypot(x - centerX, z - centerZ))) {
            hit.add(player);
            return true;
        }
        return false;
    }
}
