package dev.linqfy.bigCasares.modules.pveboss;

public final class BossHealthPool {
    private final double maximum;
    private double current;

    public BossHealthPool(double maximum) {
        if (!Double.isFinite(maximum) || maximum <= 0.0) {
            throw new IllegalArgumentException("maximum health must be finite and positive");
        }
        this.maximum = maximum;
        this.current = maximum;
    }

    public double damage(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("damage must be finite and non-negative");
        }
        current = Math.max(0.0, current - amount);
        return current;
    }

    public double heal(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("heal must be finite and non-negative");
        }
        current = Math.min(maximum, current + amount);
        return current;
    }

    public double current() {
        return current;
    }

    public double maximum() {
        return maximum;
    }

    public double fraction() {
        return current / maximum;
    }

    public boolean destroyed() {
        return current <= 0.0;
    }
}
