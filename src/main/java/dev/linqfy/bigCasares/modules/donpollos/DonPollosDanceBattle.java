package dev.linqfy.bigCasares.modules.donpollos;

import java.util.Objects;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Batalla de baile contra el Salsero (sin Bukkit). El Salsero pide un paso, tenes una ventana
 * de tiempo para hacerlo: si acertas le sacas vida, si te equivocas o no llegas perdes una vida.
 * La ventana se achica a medida que el Salsero pierde vida.
 */
public final class DonPollosDanceBattle {

    public enum Outcome { NONE, PROMPT, HIT, MISS, TIMEOUT, WON, LOST }

    private final DonPollosBattleSettings settings;
    private final IntUnaryOperator randomBelow;
    private int hitsLeft;
    private int lives;
    private DonPollosDanceMove current;
    private DonPollosDanceMove last;
    private long deadline;
    private long nextPromptAt;

    public DonPollosDanceBattle(DonPollosBattleSettings settings, IntUnaryOperator randomBelow, long now, long introTicks) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.randomBelow = Objects.requireNonNull(randomBelow, "randomBelow");
        this.hitsLeft = settings.hitsToWin();
        this.lives = settings.lives();
        this.nextPromptAt = now + introTicks;
    }

    /** Avanza el reloj: puede lanzar un paso nuevo o vencer el actual. */
    public Outcome tick(long now) {
        if (isOver()) {
            return Outcome.NONE;
        }
        if (current != null && now >= deadline) {
            current = null;
            return loseLife(now, Outcome.TIMEOUT);
        }
        if (current == null && now >= nextPromptAt) {
            DonPollosDanceMove[] moves = DonPollosDanceMove.values();
            DonPollosDanceMove next = moves[randomBelow.applyAsInt(moves.length)];
            if (next == last) {
                next = moves[(next.ordinal() + 1 + randomBelow.applyAsInt(moves.length - 1)) % moves.length];
            }
            current = next;
            last = next;
            deadline = now + windowTicks();
            return Outcome.PROMPT;
        }
        return Outcome.NONE;
    }

    /** Teclas recien apretadas: si esta la pedida es un acierto; si apretaste otra, error. */
    public Outcome input(long now, Set<DonPollosDanceMove> pressed) {
        if (isOver() || current == null || pressed.isEmpty()) {
            return Outcome.NONE;
        }
        if (pressed.contains(current)) {
            current = null;
            hitsLeft--;
            nextPromptAt = now + settings.pauseTicks();
            return hitsLeft <= 0 ? Outcome.WON : Outcome.HIT;
        }
        current = null;
        return loseLife(now, Outcome.MISS);
    }

    private Outcome loseLife(long now, Outcome reason) {
        lives--;
        nextPromptAt = now + settings.pauseTicks() * 2L;
        return lives <= 0 ? Outcome.LOST : reason;
    }

    /** Ventana para hacer el paso: arranca en window-ticks y baja hasta min-window-ticks. */
    public int windowTicks() {
        double done = 1 - hitsLeft / (double) settings.hitsToWin();
        return (int) Math.round(settings.startWindowTicks()
            - (settings.startWindowTicks() - settings.minWindowTicks()) * done);
    }

    public DonPollosDanceMove current() {
        return current;
    }

    public long ticksLeft(long now) {
        return current == null ? 0 : Math.max(0, deadline - now);
    }

    public float salseroHealth() {
        return Math.max(0, hitsLeft) / (float) settings.hitsToWin();
    }

    public int hitsLeft() {
        return hitsLeft;
    }

    public int lives() {
        return lives;
    }

    public boolean isWon() {
        return hitsLeft <= 0;
    }

    public boolean isLost() {
        return lives <= 0;
    }

    public boolean isOver() {
        return isWon() || isLost();
    }
}
