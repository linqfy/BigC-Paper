package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * El director de la fase 2, sin Bukkit: elige cada ataque coordinado de los dos señores y dice, tick a tick, cuando
 * empieza el aviso de cada golpe y cuando cae. Solo ataca con los dos en pie: si uno cae se corta el patron y el otro
 * se dedica a revivirlo (ver {@link CameDuoHealth}).
 *
 * <ul>
 *   <li>{@link Pattern#PINCER}: se ponen a los dos lados del jugador y lo embisten a la vez (dos carriles).</li>
 *   <li>{@link Pattern#PIN_AND_STRIKE}: Hun golpea el piso (huesos que frenan) y Vucub cae de un salto en el mismo
 *   lugar.</li>
 *   <li>{@link Pattern#ALTERNATING}: cuatro golpes alternados, Hun - Vucub - Hun - Vucub, cada uno sobre donde esta el
 *   jugador al empezar su aviso.</li>
 *   <li>{@link Pattern#FLANK_SWEEP}: uno adelante y otro atras, barren dos conos a la vez; solo sirve correrse de
 *   costado.</li>
 * </ul>
 */
public final class CameDuoDirector {

    public enum Pattern { PINCER, PIN_AND_STRIKE, ALTERNATING, FLANK_SWEEP }

    public enum Move { DASH, ROOT_SLAM, LEAP_STRIKE, STRIKE, SWEEP }

    public enum Cue { TELEGRAPH, LAND }

    /** Un golpe de un patron; los tiempos son ticks desde que empieza el patron. */
    public record Beat(CameLord lord, Move move, int telegraphAt, int landAt) {
        public Beat {
            if (landAt - telegraphAt < MIN_TELEGRAPH) {
                throw new IllegalArgumentException("every attack needs at least " + MIN_TELEGRAPH + " ticks of warning");
            }
        }
    }

    /** Lo que hay que hacer en este tick. */
    public record Due(Beat beat, Cue cue) {
    }

    /** 0.8 s de aviso como minimo. */
    public static final int MIN_TELEGRAPH = 16;
    /** Ticks que dura una embestida despues de caer. */
    public static final int DASH_TICKS = 9;
    public static final long FIRST_GAP = 50L;
    public static final long GAP = 50L;
    public static final long AFTER_REVIVE_GAP = 40L;

    private Pattern current;
    private Pattern last;
    private long startedAt;
    private long nextAt;

    public CameDuoDirector(long now) {
        this.nextAt = now + FIRST_GAP;
    }

    public static List<Beat> beats(Pattern pattern) {
        return switch (pattern) {
            case PINCER -> List.of(
                new Beat(CameLord.HUN, Move.DASH, 0, 22),
                new Beat(CameLord.VUCUB, Move.DASH, 0, 22));
            case PIN_AND_STRIKE -> List.of(
                new Beat(CameLord.HUN, Move.ROOT_SLAM, 0, 20),
                new Beat(CameLord.VUCUB, Move.LEAP_STRIKE, 20, 38));
            case ALTERNATING -> List.of(
                new Beat(CameLord.HUN, Move.STRIKE, 0, 18),
                new Beat(CameLord.VUCUB, Move.STRIKE, 12, 30),
                new Beat(CameLord.HUN, Move.STRIKE, 24, 42),
                new Beat(CameLord.VUCUB, Move.STRIKE, 36, 54));
            case FLANK_SWEEP -> List.of(
                new Beat(CameLord.HUN, Move.SWEEP, 0, 22),
                new Beat(CameLord.VUCUB, Move.SWEEP, 0, 22));
        };
    }

    /** Ticks que dura el patron entero (el ultimo golpe y lo que tarda en reponerse). */
    public static int length(Pattern pattern) {
        int end = 0;
        for (Beat beat : beats(pattern)) {
            end = Math.max(end, beat.landAt() + (beat.move() == Move.DASH ? DASH_TICKS : 0));
        }
        return end + 12;
    }

    public Optional<Pattern> current() {
        return Optional.ofNullable(current);
    }

    public long startedAt() {
        return startedAt;
    }

    /** {@code true} mientras corre un patron (los señores no persiguen ni pegan por su cuenta). */
    public boolean running(long now) {
        return current != null && now - startedAt < length(current);
    }

    /**
     * Empieza el siguiente patron si toca y estan los dos en pie. No repite el anterior.
     *
     * @param random devuelve un entero en {@code [0, bound)}
     */
    public Optional<Pattern> maybeStart(long now, boolean bothStanding, IntUnaryOperator random) {
        if (current != null && !running(now)) {
            last = current;
            current = null;
            nextAt = now + GAP;
        }
        if (current != null || !bothStanding || now < nextAt) {
            return Optional.empty();
        }
        List<Pattern> options = new ArrayList<>(List.of(Pattern.values()));
        options.remove(last);
        current = options.get(Math.floorMod(random.applyAsInt(options.size()), options.size()));
        startedAt = now;
        return Optional.of(current);
    }

    /** Los avisos y golpes del patron actual que tocan en {@code now}. */
    public List<Due> due(long now) {
        if (current == null) {
            return List.of();
        }
        long offset = now - startedAt;
        List<Due> due = new ArrayList<>(2);
        for (Beat beat : beats(current)) {
            if (beat.telegraphAt() == offset) {
                due.add(new Due(beat, Cue.TELEGRAPH));
            }
            if (beat.landAt() == offset) {
                due.add(new Due(beat, Cue.LAND));
            }
        }
        return due;
    }

    /** Uno de los dos cayo: se corta el patron y no empieza otro hasta {@link #resume}. */
    public void abort() {
        if (current != null) {
            last = current;
        }
        current = null;
        nextAt = Long.MAX_VALUE;
    }

    /** Volvieron a estar los dos: el proximo patron, despues de una pausa. */
    public void resume(long now) {
        current = null;
        nextAt = now + AFTER_REVIVE_GAP;
    }
}
