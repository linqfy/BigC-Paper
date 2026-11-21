package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterControllerFactory;

import java.util.List;

/**
 * El encuentro de Hun Camé y Vucub Camé, los señores de Xibalbá. Fase 1: el juego de pelota; le tiran una pelota
 * gigante a un jugador, que la devuelve de un golpe; cada gol en el aro de piedra les saca vida a los dos (y el aro se
 * achica), el pelotazo no devuelto saca la mitad de la vida. Tres goles terminan la fase. Fase 2: atacan juntos con
 * patrones coordinados; cada uno tiene su vida y si uno cae el otro lo revive a los 10 s: hay que voltear a los dos a
 * la vez. Reglas en {@link CameBallGame}, {@link CameHoop}, {@link CameBall}, {@link CameDuoHealth} y
 * {@link CameDuoDirector}; el servidor en {@link CameController}.
 */
public final class CameEncounter {

    /** Id del jefe (el del YAML); la cultura MESOAMERICAN lo cablea el coordinador. */
    public static final String BOSS_ID = "myth-came";
    public static final String MODEL_KEY = "bigcasares_myth_came";

    private CameEncounter() {
    }

    /** Claves extra de BetterModel que usa el encuentro (ademas del modelo de Hun Camé). */
    public static List<String> extraModelKeys() {
        return List.of(CameVucub.MODEL, CameCourt.BALL_MODEL, CameCourt.HOOP_MODEL);
    }

    public static BossEncounterControllerFactory factory(MythicBossSupport support) {
        return context -> new CameController(support, context);
    }
}
