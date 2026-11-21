package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import java.util.ArrayList;
import java.util.List;

/**
 * La fase 1 (el Espejo de Johari), sin Bukkit. Regla unica y legible: <b>el espejo solo se puede romper cuando no
 * queda ningun reflejo vivo</b>.
 *
 * <ol>
 *     <li>El espejo brilla ({@link #SUMMON_TELEGRAPH_TICKS}) y salen los reflejos: una copia por jugador.</li>
 *     <li>Mientras viva alguno, el espejo esta protegido (los golpes rebotan).</li>
 *     <li>Muertos todos, el espejo queda expuesto {@link #EXPOSED_TICKS}; lo que se le pegue le baja la integridad.</li>
 *     <li>Si sigue en pie, brilla otra vez y sale otra tanda. Con la integridad en cero se quiebra y termina la fase.</li>
 * </ol>
 * Ademas Enma tira un veredicto (rafaga de balas apuntada) cada {@link #VOLLEY_GAP_TICKS}, avisado
 * {@link #VOLLEY_WINDUP_TICKS} antes.
 */
public final class EnmaMirrorTrial {

    public static final int INTRO_TICKS = 60;
    public static final int SUMMON_TELEGRAPH_TICKS = 30;
    public static final int EXPOSED_TICKS = 200;
    public static final int VOLLEY_GAP_TICKS = 120;
    public static final int VOLLEY_WINDUP_TICKS = 16;
    /** Parte de la vida maxima de Enma que se lleva el espejo al quebrarse (se reparte golpe a golpe). */
    public static final double BOSS_SHARE = 0.15;

    public enum State {
        INTRO, SUMMONING, REFLECTIONS, EXPOSED, BROKEN
    }

    public enum Event {
        /** Empieza el aviso de una tanda (el espejo brilla). */
        TELEGRAPH,
        /** Salen los reflejos ahora. */
        SUMMON,
        /** No queda ningun reflejo: el espejo esta expuesto. */
        EXPOSED,
        /** Aviso del veredicto de Enma. */
        VOLLEY_WINDUP,
        /** Disparar el veredicto. */
        VOLLEY_FIRE
    }

    /**
     * @param accepted dano que se llevo el espejo (0 si estaba protegido)
     * @param shielded el golpe reboto porque hay reflejos vivos (o todavia no salieron)
     * @param broke    este golpe lo quebro
     */
    public record Hit(double accepted, boolean shielded, boolean broke) {
    }

    private final double durability;
    private double remaining;
    private State state = State.INTRO;
    private int changedAt;
    private int waves;
    private int nextVolleyAt;
    private int volleyFireAt = -1;

    public EnmaMirrorTrial(int startTick, double durability) {
        if (!(durability > 0.0)) {
            throw new IllegalArgumentException("durability must be positive");
        }
        this.durability = durability;
        this.remaining = durability;
        this.changedAt = startTick;
        this.nextVolleyAt = startTick + INTRO_TICKS + VOLLEY_GAP_TICKS / 2;
    }

    /** Integridad del espejo segun los jugadores de la sala al empezar (1 a 4). */
    public static double durabilityFor(int players) {
        int count = Math.max(1, Math.min(4, players));
        return 60.0 + 30.0 * (count - 1);
    }

    public List<Event> advance(int tick, int reflectionsAlive) {
        List<Event> events = new ArrayList<>();
        switch (state) {
            case INTRO -> {
                if (tick - changedAt >= INTRO_TICKS) {
                    enter(State.SUMMONING, tick);
                    events.add(Event.TELEGRAPH);
                }
            }
            case SUMMONING -> {
                if (tick - changedAt >= SUMMON_TELEGRAPH_TICKS) {
                    enter(State.REFLECTIONS, tick);
                    waves++;
                    events.add(Event.SUMMON);
                }
            }
            case REFLECTIONS -> {
                // un tick de gracia: los reflejos se cuentan despues de nacer
                if (tick > changedAt && reflectionsAlive <= 0) {
                    enter(State.EXPOSED, tick);
                    events.add(Event.EXPOSED);
                }
            }
            case EXPOSED -> {
                if (tick - changedAt >= EXPOSED_TICKS) {
                    enter(State.SUMMONING, tick);
                    events.add(Event.TELEGRAPH);
                }
            }
            case BROKEN -> {
                return events;
            }
        }
        if (state != State.INTRO) {
            if (volleyFireAt < 0 && tick >= nextVolleyAt) {
                volleyFireAt = tick + VOLLEY_WINDUP_TICKS;
                events.add(Event.VOLLEY_WINDUP);
            } else if (volleyFireAt >= 0 && tick >= volleyFireAt) {
                volleyFireAt = -1;
                nextVolleyAt = tick + VOLLEY_GAP_TICKS;
                events.add(Event.VOLLEY_FIRE);
            }
        }
        return events;
    }

    /** Un golpe de un jugador al espejo. */
    public Hit hit(double damage) {
        if (state == State.BROKEN) {
            return new Hit(0.0, false, false);
        }
        if (state != State.EXPOSED || !(damage > 0.0)) {
            return new Hit(0.0, state != State.EXPOSED, false);
        }
        double accepted = Math.min(damage, remaining);
        remaining -= accepted;
        boolean broke = remaining <= 1.0e-9;
        if (broke) {
            remaining = 0.0;
            state = State.BROKEN;
        }
        return new Hit(accepted, false, broke);
    }

    /**
     * El espejo se perdio (su entidad desaparecio y no se pudo volver a crear): queda quebrado para que la fase
     * termine. Devuelve la integridad que le quedaba (0 si ya estaba quebrado).
     */
    public double forfeit() {
        if (state == State.BROKEN) {
            return 0.0;
        }
        double rest = remaining;
        remaining = 0.0;
        state = State.BROKEN;
        return rest;
    }

    /** Dano que pasa a la vida de Enma por {@code accepted} puntos de espejo. */
    public double transfer(double accepted, double bossMaximumHealth) {
        return accepted / durability * BOSS_SHARE * bossMaximumHealth;
    }

    public State state() {
        return state;
    }

    public boolean broken() {
        return state == State.BROKEN;
    }

    public boolean exposed() {
        return state == State.EXPOSED;
    }

    public int waves() {
        return waves;
    }

    public double integrity() {
        return remaining / durability;
    }

    /** 0 sano, 1 agrietado, 2 casi roto (para los efectos). */
    public int crackStage() {
        double integrity = integrity();
        if (integrity > 2.0 / 3.0) {
            return 0;
        }
        return integrity > 1.0 / 3.0 ? 1 : 2;
    }

    /** Ticks que le quedan a la ventana de exposicion (0 fuera de ella). */
    public int exposedRemaining(int tick) {
        return state == State.EXPOSED ? Math.max(0, EXPOSED_TICKS - (tick - changedAt)) : 0;
    }

    private void enter(State next, int tick) {
        state = next;
        changedAt = tick;
    }
}
