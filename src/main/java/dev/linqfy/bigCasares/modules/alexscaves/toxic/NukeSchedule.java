package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import java.util.List;

/**
 * Timeline of a primed Nuclear Bomb, as pure logic: a 14 second fuse whose last 8 seconds carry the chat alert and
 * the 5-to-1 countdown, then a white screen for 3.5 seconds while the crater's chunks are loaded, and a quick fade.
 */
public final class NukeSchedule {
    /** The fuse: 14 seconds. */
    public static final int FUSE_TICKS = 280;
    /** The alert goes out when this many ticks of the fuse are left (the last 8 seconds). */
    public static final int ALERT_TICKS_LEFT = 160;
    /** The countdown starts at this many seconds left. */
    public static final int COUNTDOWN_SECONDS = 5;
    /** The screen stays white at least this long (3.5 seconds). */
    public static final int WHITE_HOLD_TICKS = 70;
    /** ...and never longer than this, whatever the chunks are doing. */
    public static final int WHITE_MAX_HOLD_TICKS = 200;
    /** The white goes away in this many ticks. */
    public static final int WHITE_FADE_TICKS = 20;
    /** Chunks are requested over about this many ticks. */
    public static final int PRELOAD_TICKS = 60;
    /** Bombs numbered A1 to A9, B1 to B9... Z9, then A1 again. */
    private static final int LETTERS = 26;
    private static final int PER_LETTER = 9;

    private NukeSchedule() {
    }

    /** The name of the n-th bomb: {@code A1} to {@code A9}, then {@code B1}... */
    public static String label(int serial) {
        int index = Math.floorMod(Math.max(1, serial) - 1, LETTERS * PER_LETTER);
        return (char) ('A' + index / PER_LETTER) + Integer.toString(index % PER_LETTER + 1);
    }

    /** Whether the alert starts now ({@code ticksLeft} of the fuse remain). */
    public static boolean isAlert(int ticksLeft) {
        return ticksLeft == ALERT_TICKS_LEFT;
    }

    /**
     * The chat lines to send when {@code ticksLeft} of the fuse remain: the alert at 8 seconds, then
     * {@code Explosión en 5...} and {@code 4...} to {@code 1...}, one a second; nothing on any other tick.
     */
    public static List<String> linesAt(int ticksLeft, String label) {
        if (isAlert(ticksLeft)) {
            return List.of("", "§c§l¡ALERTA! §r§cLa bomba nuclear §l" + label
                + "§r§c ha sido activada, si se encuentran en su rango de explosión que Dios los ampare, corran...", "");
        }
        if (ticksLeft > 0 && ticksLeft % 20 == 0 && ticksLeft / 20 <= COUNTDOWN_SECONDS) {
            int seconds = ticksLeft / 20;
            return List.of(seconds == COUNTDOWN_SECONDS ? "§eExplosión en " + seconds + "..." : "§e" + seconds + "...");
        }
        return List.of();
    }

    /** Opacity (0 to 255) of the white screen while the chunks load: full in two ticks. */
    public static int whiteRise(int tick) {
        return Math.max(0, Math.min(255, tick * 128));
    }

    /** Opacity of the white screen {@code tick} ticks after it starts to go: fast at first, then slower. */
    public static int whiteFade(int tick) {
        if (tick >= WHITE_FADE_TICKS) {
            return 0;
        }
        double left = 1.0 - Math.max(0, tick) / (double) WHITE_FADE_TICKS;
        return (int) Math.round(255.0 * left * left);
    }

    /** Chunks to request each tick so that all {@code chunks} go out in about {@link #PRELOAD_TICKS}. */
    public static int preloadPerTick(int chunks) {
        return Math.max(1, (int) Math.ceil(chunks / (double) PRELOAD_TICKS));
    }
}
