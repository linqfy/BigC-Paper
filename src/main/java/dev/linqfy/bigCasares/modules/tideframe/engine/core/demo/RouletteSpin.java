package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import java.util.Locale;

/** A code-selected challenge animation. This class does not select, award or simulate random prizes. */
public final class RouletteSpin {
    public enum Color { RED, GREEN, BLUE, GOLD }
    public static final int DURATION_TICKS = 48;
    public static final int ORIENTATIONS = 24;
    private final Color chosenColor;
    private int elapsed;

    public RouletteSpin(Color chosenColor) {
        if (chosenColor == null) throw new IllegalArgumentException("Choose a roulette color");
        this.chosenColor = chosenColor;
    }

    public String tick() {
        if (elapsed < DURATION_TICKS) elapsed++;
        return frameId();
    }

    public String frameId() {
        if (finished()) return "win_" + chosenColor.name().toLowerCase(Locale.ROOT);
        return orientationId((int)Math.round(angle() / 15.0) % ORIENTATIONS);
    }

    public double angle() {
        double t = elapsed / (double)DURATION_TICKS;
        return (1080 + chosenColor.ordinal() * 90) * (1 - Math.pow(1 - t, 3));
    }

    public boolean finished() { return elapsed == DURATION_TICKS; }
    public Color chosenColor() { return chosenColor; }

    static String orientationId(int orientation) { return "spin_" + (orientation < 10 ? "0" : "") + orientation; }
}
