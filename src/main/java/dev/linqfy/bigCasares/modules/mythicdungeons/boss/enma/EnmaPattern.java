package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import java.util.ArrayList;
import java.util.List;

/**
 * Los patrones de balas de Enma (estilo The Binding of Isaac: lentos y esquivables), sin Bukkit. Cada patron dice que
 * balas salen en cada tick desde que se suelta: un angulo horizontal (radianes; {@code x = cos}, {@code z = sin}) y una
 * velocidad en bloques por tick.
 */
public enum EnmaPattern {

    /** Tres anillos de 16 balas, cada uno corrido media separacion: hay que meterse en el hueco. */
    RING("§cAnillo de condena", 30),
    /** Cuatro brazos que giran: se esquiva caminando en circulo con el giro. */
    SPIRAL("§cEspiral del Naraka", 48),
    /** Abanicos de cinco balas apuntados a cada jugador: correrse de costado. */
    AIMED("§cSentencia", 26),
    /** Chorros en cruz (+) y despues en aspa (x): pararse entre las lineas. */
    CROSS("§cCruz de los Diez Reyes", 36),
    /** Fase 1: tres balas apuntadas a un jugador. */
    VOLLEY("§cVeredicto", 1);

    public static final double RING_SPEED = 0.22;
    public static final double SPIRAL_SPEED = 0.2;
    public static final double AIMED_SPEED = 0.28;
    public static final double CROSS_SPEED = 0.24;
    public static final double VOLLEY_SPEED = 0.3;
    public static final int RING_BULLETS = 16;
    public static final int SPIRAL_ARMS = 4;
    public static final double AIMED_SPREAD = 0.18;
    public static final double VOLLEY_SPREAD = 0.2;

    /** Una bala que sale en este tick. */
    public record Shot(double angle, double speed) {
    }

    private final String announcement;
    private final int activeTicks;

    EnmaPattern(String announcement, int activeTicks) {
        this.announcement = announcement;
        this.activeTicks = activeTicks;
    }

    public String announcement() {
        return announcement;
    }

    /** Ticks que dura el patron desde que se suelta. */
    public int activeTicks() {
        return activeTicks;
    }

    /**
     * Las balas del tick {@code elapsed} (0 = el tick en que se suelta).
     *
     * @param aims angulos hacia los jugadores (para los patrones apuntados); {@code base} gira los no apuntados
     */
    public List<Shot> shots(int elapsed, List<Double> aims, double base) {
        List<Shot> shots = new ArrayList<>();
        if (elapsed < 0 || elapsed >= activeTicks) {
            return shots;
        }
        switch (this) {
            case RING -> {
                if (elapsed % 12 == 0) {
                    int volley = elapsed / 12;
                    double step = 2.0 * Math.PI / RING_BULLETS;
                    double offset = base + (volley % 2) * step / 2.0;
                    for (int i = 0; i < RING_BULLETS; i++) {
                        shots.add(new Shot(offset + i * step, RING_SPEED));
                    }
                }
            }
            case SPIRAL -> {
                if (elapsed % 4 == 0) {
                    double turn = base + elapsed * 0.11;
                    for (int arm = 0; arm < SPIRAL_ARMS; arm++) {
                        shots.add(new Shot(turn + arm * 2.0 * Math.PI / SPIRAL_ARMS, SPIRAL_SPEED));
                    }
                }
            }
            case AIMED -> {
                if (elapsed % 10 == 0) {
                    for (double aim : aims) {
                        for (int i = -2; i <= 2; i++) {
                            shots.add(new Shot(aim + i * AIMED_SPREAD, AIMED_SPEED));
                        }
                    }
                }
            }
            case CROSS -> {
                if (elapsed % 4 == 0) {
                    double turn = base + (elapsed < activeTicks / 2 ? 0.0 : Math.PI / 4.0) + elapsed * 0.012;
                    for (int i = 0; i < 4; i++) {
                        shots.add(new Shot(turn + i * Math.PI / 2.0, CROSS_SPEED));
                    }
                }
            }
            case VOLLEY -> {
                for (double aim : aims) {
                    for (int i = -1; i <= 1; i++) {
                        shots.add(new Shot(aim + i * VOLLEY_SPREAD, VOLLEY_SPEED));
                    }
                }
            }
        }
        return shots;
    }
}
