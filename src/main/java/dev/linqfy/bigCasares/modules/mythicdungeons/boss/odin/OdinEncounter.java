package dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin;

import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterControllerFactory;

import java.util.List;

/**
 * El encuentro de Odín. Fase 1: invoca a Hugin, Munin, Geri, Freki y a los einherjar y es invulnerable mientras
 * vivan. Fase 2: con el casco puesto embiste en linea recta; cada dos embestidas, o al chocar contra una pared, queda
 * aturdido y solo entonces recibe daño. Las reglas estan en {@link OdinFight} y {@link OdinCharge}; el servidor en
 * {@link OdinController}.
 */
public final class OdinEncounter {

    private OdinEncounter() {
    }

    /** Claves extra de BetterModel que usa el encuentro (ademas del modelo del jefe); se instalan si vienen en el jar. */
    public static List<String> extraModelKeys() {
        return List.of(OdinGuardianSquad.RAVEN_MODEL, OdinGuardianSquad.WOLF_MODEL,
            OdinGuardianSquad.EINHERJAR_MODEL);
    }

    public static BossEncounterControllerFactory factory(MythicBossSupport support) {
        return context -> new OdinController(support, context);
    }
}
