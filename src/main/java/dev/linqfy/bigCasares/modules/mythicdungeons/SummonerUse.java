package dev.linqfy.bigCasares.modules.mythicdungeons;

import java.util.Objects;
import java.util.Optional;

/**
 * Si un Invocador se puede usar donde lo uso el jugador (sin servidor). Se usa con click derecho sobre el altar de la
 * sala del jefe, adentro de la mazmorra de su cultura; cada rechazo trae su explicacion en castellano.
 */
public final class SummonerUse {

    public enum Result {
        SUCCESS,
        NOT_IN_DUNGEON,
        WRONG_DUNGEON,
        NOT_AT_ALTAR,
        BOSS_ALREADY_ALIVE,
        BOSS_SYSTEM_OFF
    }

    /**
     * Lo que se sabe del intento.
     *
     * @param summoner        la cultura del invocador que tiene en la mano
     * @param dungeon         la cultura de la mazmorra donde esta (el bloque clickeado o el jugador), si hay una
     * @param altarClicked    el bloque clickeado es el bloque de altar configurado
     * @param altarInDungeon  ese altar esta adentro de la caja de la mazmorra
     * @param bossAlive       ya hay un jefe vivo invocado en esta mazmorra
     * @param bossSystem      el sistema de jefes (pve-boss) esta prendido y el jefe registrado
     */
    public record Request(MythicCulture summoner, Optional<MythicCulture> dungeon, boolean altarClicked,
                          boolean altarInDungeon, boolean bossAlive, boolean bossSystem) {

        public Request {
            Objects.requireNonNull(summoner, "summoner");
            Objects.requireNonNull(dungeon, "dungeon");
        }
    }

    public record Outcome(Result result, String message) {

        public boolean success() {
            return result == Result.SUCCESS;
        }
    }

    private SummonerUse() {
    }

    public static Outcome decide(Request request) {
        MythicCulture culture = request.summoner();
        if (request.dungeon().isEmpty()) {
            return new Outcome(Result.NOT_IN_DUNGEON, "§cSolo podés usar el invocador dentro de "
                + culture.dungeonName() + ", sobre el altar de la sala de " + culture.requireBoss().displayName() + ".");
        }
        MythicCulture found = request.dungeon().get();
        if (found != culture) {
            return new Outcome(Result.WRONG_DUNGEON, "§cEstás en " + found.dungeonName() + ". Este invocador llama a "
                + culture.requireBoss().displayName() + " y solo funciona en " + culture.dungeonName() + ".");
        }
        if (!request.altarClicked() || !request.altarInDungeon()) {
            return new Outcome(Result.NOT_AT_ALTAR, "§eEstás en " + culture.dungeonName()
                + ", pero el invocador se usa con click derecho sobre el altar de la sala del jefe.");
        }
        if (!request.bossSystem()) {
            return new Outcome(Result.BOSS_SYSTEM_OFF,
                "§cEl sistema de jefes está apagado: el altar no responde. Avisale a un admin.");
        }
        if (request.bossAlive()) {
            return new Outcome(Result.BOSS_ALREADY_ALIVE, "§c" + culture.requireBoss().displayName()
                + " ya está despierto en esta mazmorra. Derrotalo antes de invocarlo de nuevo.");
        }
        return new Outcome(Result.SUCCESS, "§5§l¡" + culture.requireBoss().displayName() + " responde al llamado!");
    }
}
