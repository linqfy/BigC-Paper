package dev.linqfy.bigCasares.modules.donpollos;

import java.util.List;
import java.util.Objects;

/**
 * FASE 3 del Don Pollo Boss: se eleva, te hunde en un cubo y empiezan las Batallas del Cubo.
 *
 * @param levitateSeconds    cuanto tarda en elevarse al empezar la fase
 * @param levitateHeight     cuantos bloques sube
 * @param pitDepth           cuantos bloques de profundidad se vacia el chunk donde esta cada jugador
 * @param maxAlive           tope de Labubus vivos a la vez durante las Batallas del Cubo
 * @param roundPauseSeconds  pausa entre ronda y ronda
 * @param rounds             las rondas de las Batallas del Cubo
 */
public record DonPollosBossPhase3Settings(
    double levitateSeconds,
    double levitateHeight,
    int pitDepth,
    int maxAlive,
    double roundPauseSeconds,
    List<DonPollosCubeRound> rounds
) {

    public DonPollosBossPhase3Settings {
        Objects.requireNonNull(rounds, "rounds");
        rounds = List.copyOf(rounds);
        if (levitateSeconds < 1 || levitateHeight < 0 || levitateHeight > 64 || pitDepth < 0 || pitDepth > 32) {
            throw new IllegalArgumentException("boss.fase-3: levitate-seconds >= 1, levitate-height 0-64 y pit-depth 0-32");
        }
        if (maxAlive < 1 || roundPauseSeconds < 0 || rounds.isEmpty()) {
            throw new IllegalArgumentException("boss.fase-3: max-alive >= 1, round-pause-seconds >= 0 y al menos una ronda");
        }
    }

    /** 3 rondas: 10, 30 y 50 Labubus, cada vez mas rapido, y un Coronel cada vez mas rapido y fuerte. */
    public static DonPollosBossPhase3Settings defaults() {
        return new DonPollosBossPhase3Settings(7, 20, 6, 20, 3, List.of(
            new DonPollosCubeRound(10, 1.2, 1.0, 0),
            new DonPollosCubeRound(30, 0.6, 1.25, 3),
            new DonPollosCubeRound(50, 0.3, 1.5, 6)));
    }

    long roundPauseTicks() {
        return Math.round(roundPauseSeconds * 20);
    }
}
