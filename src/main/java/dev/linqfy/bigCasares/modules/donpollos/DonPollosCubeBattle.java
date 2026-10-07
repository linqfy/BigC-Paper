package dev.linqfy.bigCasares.modules.donpollos;

import java.util.List;

/**
 * Reglas de las Batallas del Cubo (FASE 3), sin nada de Bukkit: en cada ronda sale un Coronel al empezar
 * y los Labubus de a uno cada {@code spawnSeconds}, sin pasar de {@code maxAlive} vivos a la vez. La ronda
 * se gana cuando ya salieron todos y no queda ninguno vivo; entre ronda y ronda hay una pausa.
 */
final class DonPollosCubeBattle {

    /** Lo que hay que hacer en este tick. */
    record Step(int roundStarted, boolean spawnColonel, boolean spawnLabubu, boolean finished) {
        static final Step NONE = new Step(0, false, false, false);
    }

    private final List<DonPollosCubeRound> rounds;
    private final int maxAlive;
    private final long pauseTicks;
    private int round;
    private boolean started;
    private int spawned;
    private boolean colonelSpawned;
    private long nextSpawn;
    private long pauseUntil;
    private boolean finished;

    DonPollosCubeBattle(List<DonPollosCubeRound> rounds, int maxAlive, long pauseTicks, long now) {
        if (rounds.isEmpty()) {
            throw new IllegalArgumentException("las Batallas del Cubo necesitan al menos una ronda");
        }
        this.rounds = List.copyOf(rounds);
        this.maxAlive = Math.max(1, maxAlive);
        this.pauseTicks = pauseTicks;
        this.pauseUntil = now + pauseTicks;
    }

    Step tick(long now, int aliveLabubus, int aliveColonels) {
        if (finished || now < pauseUntil) {
            return Step.NONE;
        }
        DonPollosCubeRound current = rounds.get(round);
        int roundStarted = 0;
        boolean colonel = false;
        if (!started) {
            started = true;
            roundStarted = round + 1;
            colonel = true;
            colonelSpawned = true;
            nextSpawn = now;
            aliveColonels++;
        }
        boolean labubu = false;
        if (spawned < current.labubus() && now >= nextSpawn && aliveLabubus < maxAlive) {
            labubu = true;
            spawned++;
            aliveLabubus++;
            nextSpawn = now + current.spawnTicks();
        }
        if (spawned >= current.labubus() && colonelSpawned && aliveLabubus == 0 && aliveColonels == 0) {
            round++;
            started = false;
            spawned = 0;
            colonelSpawned = false;
            if (round >= rounds.size()) {
                finished = true;
                return new Step(roundStarted, colonel, labubu, true);
            }
            pauseUntil = now + pauseTicks;
        }
        return new Step(roundStarted, colonel, labubu, false);
    }

    /** Ronda actual, de 1 a {@link #rounds()}. */
    int round() {
        return Math.min(round + 1, rounds.size());
    }

    int rounds() {
        return rounds.size();
    }

    DonPollosCubeRound current() {
        return rounds.get(Math.min(round, rounds.size() - 1));
    }

    /** Cuanto falta de la ronda (1 al empezar, 0 cuando se gana): los que faltan salir mas los vivos. */
    double remaining(int aliveLabubus, int aliveColonels) {
        if (finished) {
            return 0;
        }
        DonPollosCubeRound current = current();
        int total = current.labubus() + 1;
        int left = (current.labubus() - spawned) + aliveLabubus + (colonelSpawned ? aliveColonels : 1);
        return Math.max(0, Math.min(1, left / (double) total));
    }

    /** Index (0, 1, 2...) del color del proximo Labubu, para ir rotando los 4 colores. */
    int labubuIndex() {
        return spawned;
    }

    boolean finished() {
        return finished;
    }
}
