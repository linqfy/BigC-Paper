package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterControllerFactory;

import java.util.List;
import java.util.SplittableRandom;

/**
 * El encuentro de Enma (mazmorra japonesa). Fase 1: Enma pone el Espejo de Johari y de el salen reflejos de cada
 * jugador con su armadura y su arma; mientras quede un reflejo el espejo no se puede romper, y mientras el espejo este
 * en pie Enma es intocable. Fase 2 (al quebrarse el espejo): golpes de shaku que sacan oni, giro con el shaku y
 * patrones de balas lentos. Ver {@link EnmaController}.
 */
public final class EnmaEncounter {

    /** Id del jefe (el coordinador lo registra en la cultura; no depender de {@code MythicCulture}). */
    public static final String BOSS_ID = "myth-enma";
    public static final String SHORT_ID = "enma";
    public static final String MODEL_KEY = "bigcasares_myth_enma";

    private EnmaEncounter() {
    }

    /** Claves extra de BetterModel que usa el encuentro (ademas del modelo del jefe); se instalan si vienen en el jar. */
    public static List<String> extraModelKeys() {
        return List.of(EnmaMirror.MODEL);
    }

    public static BossEncounterControllerFactory factory(MythicBossSupport support) {
        return context -> new EnmaController(support, context, new SplittableRandom());
    }
}
