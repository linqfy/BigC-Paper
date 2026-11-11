package dev.linqfy.bigCasares.modules.donpollos;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.entity.Ravager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Estado de un Don Pollo Boss en pelea (lo comparten el manager, los ataques, el terreno, la fase 3 y la derrota). */
final class DonPollosBoss {

    final Ravager ravager;
    /** La barra de boss (la pone el manager al empezar a seguirlo; el constructor no la pide para poder probar el
     *  estado sin Adventure). */
    BossBar bar;
    final Set<UUID> viewers = new HashSet<>();
    DonPollosBossPhase phase;
    long nextLaser;
    long nextMeteor;
    long nextChickens;
    /** Bichos invocados por este boss (gallinas, Labubus y Coronel). */
    final Set<UUID> minions = new HashSet<>();
    /** Dano que le hizo cada jugador: los tres que mas le pegaron se llevan su alma al derrotarlo. */
    final Map<UUID, Double> damageByPlayer = new HashMap<>();
    Location levitateFrom;
    long levitateStart;
    /** Tick en que termina de levitar (0: no esta levitando). */
    long levitateEnd;
    /** Donde flota despues de subir, hasta que se ganen las Batallas del Cubo (null: no flota). */
    Location hoverAt;
    /** Batallas del Cubo en curso (null: no hay). */
    DonPollosCubeBattle cube;
    BossBar cubeBar;
    final Set<UUID> cubeViewers = new HashSet<>();
    final List<DonPollosBossPit> pits = new ArrayList<>();
    DonPollosBossDifficulty difficulty = new DonPollosBossDifficulty(0);
    /** Tick desde el que no hay nadie en ningun cubo, o -1 (para cerrar las Batallas del Cubo abandonadas). */
    long cubeEmptySince = -1;
    /** Turno para repartir los bichos entre los jugadores que estan en un cubo. */
    long fighterTurn;
    // FASE FINAL
    long nextBeam;
    long beamStart;
    UUID beamTarget;
    Location beamPoint;
    long nextJump;
    long jumpStart;
    /** Tick en que aterriza el mega salto (0: no esta saltando). */
    long jumpEnd;
    Location jumpFrom;
    Location jumpTo;
    double jumpApex;
    UUID jumpTarget;
    long nextHop;
    long nextFinalColonels;
    /** Derrotado: llora y espera que reclamen el premio (null: sigue peleando). */
    Defeat defeat;
    long chargeEnd;
    long firingUntil;
    UUID target;

    DonPollosBoss(Ravager ravager) {
        this.ravager = ravager;
    }

    static final class Defeat {
        final UUID winner;
        final long start;
        int phrase = -1;
        boolean menuOpened;
        boolean claimed;

        Defeat(UUID winner, long start) {
            this.winner = winner;
            this.start = start;
        }

        /** Reclama el premio: solo quien le gano (o cualquiera si no hubo ganador) y una sola vez. */
        boolean tryClaim(UUID player) {
            if (claimed || !DonPollosReward.canClaim(winner, player)) {
                return false;
            }
            claimed = true;
            return true;
        }
    }
}
