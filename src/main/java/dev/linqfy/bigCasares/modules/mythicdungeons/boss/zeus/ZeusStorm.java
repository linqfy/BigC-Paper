package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * La fase 1 en el tiempo, sin Bukkit: elige el proximo patron (nunca el mismo dos veces seguidas), lo corre y
 * descansa entre patrones. Con menos vida descansa menos y avisa un poco menos (nunca menos de
 * {@link #MINIMUM_TELEGRAPH} ticks). Los ticks son de juego y los pone el que llama.
 */
public final class ZeusStorm {

    /** Aviso minimo en el piso antes de un rayo (1 s; el brief pide al menos 0,8 s). */
    public static final int MINIMUM_TELEGRAPH = 20;
    static final int FULL_HEALTH_TELEGRAPH = 26;
    static final int FULL_HEALTH_REST = 60;
    static final int LOW_HEALTH_REST = 30;
    static final int FIRST_PATTERN_DELAY = 40;

    /** Una tanda que se esta marcando en el piso, con cuanto le falta (0 recien empieza, 1 cae ya). */
    public record Telegraph(ZeusStrikeWave wave, double progress) {
    }

    /** Lo que paso en un avance: tandas que empezaron a marcarse, tandas que cayeron y patron nuevo (si hubo). */
    public record Step(ZeusPattern started, List<ZeusStrikeWave> warned, List<ZeusStrikeWave> struck) {
    }

    /** Lo que necesita el storm para armar un patron nuevo en el momento en que arranca. */
    @FunctionalInterface
    public interface PatternFactory {
        ZeusPattern create(ZeusPatternKind kind, int telegraph);
    }

    private final PatternFactory factory;
    private final RandomGenerator random;
    private ZeusPattern current;
    private ZeusPatternKind last;
    private int patternStart;
    private int nextPatternAt;
    private int lastTick;
    private boolean stopped;

    public ZeusStorm(PatternFactory factory, RandomGenerator random, int startTick) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.random = Objects.requireNonNull(random, "random");
        this.nextPatternAt = startTick + FIRST_PATTERN_DELAY;
        this.lastTick = startTick;
    }

    /** Aviso para la vida que le queda (fraccion 1 a 0,5 en esta fase). */
    public static int telegraphFor(double healthFraction) {
        double t = progress(healthFraction);
        return (int) Math.round(FULL_HEALTH_TELEGRAPH - t * (FULL_HEALTH_TELEGRAPH - MINIMUM_TELEGRAPH));
    }

    public static int restFor(double healthFraction) {
        double t = progress(healthFraction);
        return (int) Math.round(FULL_HEALTH_REST - t * (FULL_HEALTH_REST - LOW_HEALTH_REST));
    }

    private static double progress(double healthFraction) {
        // 1.0 de vida -> 0, 0.5 (cambio de fase) -> 1
        double clamped = Math.max(0.5, Math.min(1.0, healthFraction));
        return (1.0 - clamped) / 0.5;
    }

    /** Elige un patron distinto del anterior. */
    ZeusPatternKind nextKind() {
        ZeusPatternKind[] kinds = ZeusPatternKind.values();
        ZeusPatternKind kind;
        do {
            kind = kinds[random.nextInt(kinds.length)];
        } while (kind == last && kinds.length > 1);
        return kind;
    }

    /** Avanza hasta {@code nowTick} (incluido) y cuenta lo que paso desde el avance anterior. */
    public Step advance(int nowTick, double healthFraction) {
        if (stopped || nowTick <= lastTick) {
            return new Step(null, List.of(), List.of());
        }
        int previous = lastTick;
        lastTick = nowTick;
        ZeusPattern started = null;
        if (current == null && nowTick >= nextPatternAt) {
            ZeusPatternKind kind = nextKind();
            current = factory.create(kind, telegraphFor(healthFraction));
            last = kind;
            patternStart = nowTick;
            previous = nowTick - 1;
            started = current;
        }
        if (current == null) {
            return new Step(null, List.of(), List.of());
        }
        List<ZeusStrikeWave> warned = new ArrayList<>();
        List<ZeusStrikeWave> struck = new ArrayList<>();
        for (ZeusStrikeWave wave : current.waves()) {
            int start = patternStart + wave.startTick();
            int strike = patternStart + wave.strikeTick();
            if (start > previous && start <= nowTick) {
                warned.add(wave);
            }
            if (strike > previous && strike <= nowTick) {
                struck.add(wave);
            }
        }
        if (nowTick >= patternStart + current.lastStrikeTick()) {
            current = null;
            nextPatternAt = nowTick + restFor(healthFraction);
        }
        return new Step(started, warned, struck);
    }

    /** Las tandas marcadas en el piso en este momento. */
    public List<Telegraph> telegraphs(int nowTick) {
        if (current == null || stopped) {
            return List.of();
        }
        List<Telegraph> list = new ArrayList<>();
        for (ZeusStrikeWave wave : current.waves()) {
            int start = patternStart + wave.startTick();
            int strike = patternStart + wave.strikeTick();
            if (nowTick >= start && nowTick < strike) {
                list.add(new Telegraph(wave, (double) (nowTick - start) / wave.telegraphTicks()));
            }
        }
        return list;
    }

    public boolean casting() {
        return current != null && !stopped;
    }

    /** Corta todo (cambio de fase): nada mas cae ni se marca. */
    public void stop() {
        stopped = true;
        current = null;
    }

    public boolean stopped() {
        return stopped;
    }
}
