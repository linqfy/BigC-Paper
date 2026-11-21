package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterControllerFactory;

import java.util.List;

/**
 * El encuentro de Iblís y el rey Salomón (mazmorra árabe).
 *
 * <p>Fase 1: Iblís es invisible, solo se ve su humo; tira bolas y lineas de fuego (avisadas en el piso) y se le pega
 * golpeando donde esta el humo. A la mitad de la vida aparece el rey Salomón y le da su anillo a cada jugador.</p>
 *
 * <p>Fase 2: Iblís se revela y cruza la sala en embestidas de fuego. Con el anillo en la mano, agachandose junto a
 * una vasija sellada, se la arma unos segundos: si Iblís pasa cerca queda atrapado, y solo entonces se le pega. El
 * guion esta en {@link IblisFight}.</p>
 */
public final class IblisEncounter {

    /** Id del jefe (la cultura y el registro los conecta el modulo). */
    public static final String BOSS_ID = "myth-iblis";
    public static final String SHORT_ID = "iblis";
    public static final String MODEL = "bigcasares_myth_iblis";

    private IblisEncounter() {
    }

    /** Claves extra de BetterModel que usa el encuentro (ademas del modelo del jefe); se instalan si vienen en el jar. */
    public static List<String> extraModelKeys() {
        return List.of(IblisFight.SOLOMON_MODEL, IblisVesselProps.MODEL);
    }

    public static BossEncounterControllerFactory factory(MythicBossSupport support) {
        return context -> new IblisFight(support, context);
    }
}
