package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * La fase 2 (el juicio), sin Bukkit: que ataque hace Enma y cuando. Cada ataque tiene un aviso
 * ({@link Attack#windupTicks()}, siempre 0,8 s o mas) y una parte activa. Prioridades:
 * <ol>
 *     <li>{@link Attack#SLAM}: golpe de shaku al piso que saca oni, si hay menos oni que el tope (cada
 *     {@link #SLAM_COOLDOWN} como mucho). Es siempre el primero de la fase.</li>
 *     <li>{@link Attack#SPIN}: si alguien esta pegado a Enma (cada {@link #SPIN_COOLDOWN} como mucho).</li>
 *     <li>Si no, un patron de balas distinto del anterior.</li>
 * </ol>
 * Por debajo de {@link #FRENZY_BELOW} de vida la pausa entre ataques se acorta.
 */
public final class EnmaJudgement {

    public static final int FIRST_ATTACK_DELAY = 10;
    public static final int GAP = 30;
    public static final int FRENZY_GAP = 18;
    public static final double FRENZY_BELOW = 0.35;
    public static final int SLAM_COOLDOWN = 400;
    public static final int SPIN_COOLDOWN = 200;

    public enum Attack {
        SLAM(24, 16, null),
        SPIN(20, 40, null),
        RING(16, EnmaPattern.RING.activeTicks(), EnmaPattern.RING),
        SPIRAL(16, EnmaPattern.SPIRAL.activeTicks(), EnmaPattern.SPIRAL),
        AIMED(16, EnmaPattern.AIMED.activeTicks(), EnmaPattern.AIMED),
        CROSS(16, EnmaPattern.CROSS.activeTicks(), EnmaPattern.CROSS);

        private final int windupTicks;
        private final int activeTicks;
        private final EnmaPattern pattern;

        Attack(int windupTicks, int activeTicks, EnmaPattern pattern) {
            this.windupTicks = windupTicks;
            this.activeTicks = activeTicks;
            this.pattern = pattern;
        }

        public int windupTicks() {
            return windupTicks;
        }

        public int activeTicks() {
            return activeTicks;
        }

        /** El patron de balas, o {@code null} para el golpe y el giro. */
        public EnmaPattern pattern() {
            return pattern;
        }
    }

    /** Lo que ve el planificador en este tick. */
    public record Situation(int oniAlive, int oniCap, boolean playerClose, double healthFraction) {
    }

    public enum Kind {
        WINDUP, RELEASE, END
    }

    public record Event(Kind kind, Attack attack) {
    }

    private static final List<Attack> PATTERNS = List.of(Attack.RING, Attack.SPIRAL, Attack.AIMED, Attack.CROSS);

    private final RandomGenerator random;
    private Attack current;
    private int startedAt;
    private int nextAt;
    private int slamReadyAt;
    private int spinReadyAt;
    private Attack lastPattern;

    public EnmaJudgement(int tick, RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
        this.nextAt = tick + FIRST_ATTACK_DELAY;
        this.slamReadyAt = tick;
        this.spinReadyAt = tick;
    }

    /** Tope de oni vivos: 1 solo, 2 con dos o tres jugadores, 3 con cuatro. */
    public static int oniCap(int players) {
        return Math.min(3, 1 + Math.max(0, players) / 2);
    }

    public List<Event> advance(int tick, Situation situation) {
        List<Event> events = new ArrayList<>();
        if (current != null) {
            int elapsed = tick - startedAt;
            if (elapsed == current.windupTicks()) {
                events.add(new Event(Kind.RELEASE, current));
            }
            if (elapsed >= current.windupTicks() + current.activeTicks()) {
                events.add(new Event(Kind.END, current));
                current = null;
                nextAt = tick + (situation.healthFraction() < FRENZY_BELOW ? FRENZY_GAP : GAP);
            }
            return events;
        }
        if (tick < nextAt) {
            return events;
        }
        current = choose(tick, situation);
        startedAt = tick;
        events.add(new Event(Kind.WINDUP, current));
        return events;
    }

    private Attack choose(int tick, Situation situation) {
        if (situation.oniAlive() < situation.oniCap() && tick >= slamReadyAt) {
            slamReadyAt = tick + SLAM_COOLDOWN;
            return Attack.SLAM;
        }
        if (situation.playerClose() && tick >= spinReadyAt) {
            spinReadyAt = tick + SPIN_COOLDOWN;
            return Attack.SPIN;
        }
        List<Attack> options = new ArrayList<>(PATTERNS);
        options.remove(lastPattern);
        Attack pick = options.get(random.nextInt(options.size()));
        lastPattern = pick;
        return pick;
    }

    public Attack current() {
        return current;
    }

    /** El ataque esta en su aviso. */
    public boolean windingUp(int tick) {
        return current != null && tick - startedAt < current.windupTicks();
    }

    /** Ticks desde que se solto el ataque actual (negativo durante el aviso, -1 sin ataque). */
    public int elapsedActive(int tick) {
        return current == null ? -1 : tick - startedAt - current.windupTicks();
    }

    /** Avance del aviso, de 0 a 1. */
    public double windupProgress(int tick) {
        if (current == null) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, (tick - startedAt) / (double) current.windupTicks()));
    }
}
