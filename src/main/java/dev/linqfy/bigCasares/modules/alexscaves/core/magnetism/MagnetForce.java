package dev.linqfy.bigCasares.modules.alexscaves.core.magnetism;

/**
 * Pull of neodymium blocks on magnetic entities, from {@code MagnetUtil}: within
 * 5 blocks, {@code direction * (1 - distance / 5) * 0.25}. Scarlet attracts and
 * azure repels. The direction is not normalised when closer than one block.
 */
public final class MagnetForce {
    public static final double RANGE = 5.0;
    public static final double STRENGTH = 0.25;
    /** Mod's per-tick smoothing of the accumulated magnet delta. */
    public static final double CARRY = 0.9;
    /** Mod's per-tick decay of the stored delta after it is applied ({@code MagnetUtil}). */
    public static final double DECAY = 0.08;
    /** Not in the mod: a safety cap so stacked magnets cannot fling entities across the map. */
    public static final double MAX_SPEED = 1.0;

    private MagnetForce() {
    }

    public static Vec pull(Vec entity, Vec magnet, Polarity polarity) {
        Vec offset = magnet.subtract(entity);
        double distance = offset.length();
        if (distance >= RANGE) {
            return Vec.ZERO;
        }
        Vec direction = distance < 1.0 ? offset : offset.scale(1.0 / distance);
        Vec pull = direction.scale((1.0 - distance / RANGE) * STRENGTH);
        return polarity == Polarity.SCARLET ? pull : pull.scale(-1.0);
    }

    /**
     * Velocity to add this tick, as in {@code delta = delta * 0.9 + pull}, clamped. The caller stores
     * {@code result * DECAY} as the delta for the next tick.
     */
    public static Vec accumulate(Vec previousDelta, Vec pull) {
        return previousDelta.scale(CARRY).add(pull).clampLength(MAX_SPEED);
    }

    public enum Polarity {
        SCARLET,
        AZURE;

        public Polarity opposite() {
            return this == SCARLET ? AZURE : SCARLET;
        }
    }

    public record Vec(double x, double y, double z) {
        public static final Vec ZERO = new Vec(0, 0, 0);

        public Vec add(Vec other) {
            return new Vec(x + other.x, y + other.y, z + other.z);
        }

        public Vec subtract(Vec other) {
            return new Vec(x - other.x, y - other.y, z - other.z);
        }

        public Vec scale(double factor) {
            return new Vec(x * factor, y * factor, z * factor);
        }

        public double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        public Vec clampLength(double max) {
            double length = length();
            return length > max ? scale(max / length) : this;
        }
    }
}
