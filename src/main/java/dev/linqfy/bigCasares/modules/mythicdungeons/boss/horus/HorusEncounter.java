package dev.linqfy.bigCasares.modules.mythicdungeons.boss.horus;

import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterControllerFactory;

import java.util.List;

/**
 * El encuentro de Horus. Fase 1: planea en picada sobre los jugadores (aviso dorado en el piso) y entre vuelos baja
 * a tierra, donde se le puede pegar. A la mitad de la vida se retira al cielo, invulnerable, y del piso salen veinte
 * Ojos de Horus (zombis bebe con el modelo del Ojo). Cuando caen todos, Horus se enfurece y se deshace en polvo: esa
 * es su derrota. El guion esta en {@link HorusFight}.
 */
public final class HorusEncounter {

    private HorusEncounter() {
    }

    /** Claves extra de BetterModel que usa el encuentro (ademas del modelo del jefe); se instalan si vienen en el jar. */
    public static List<String> extraModelKeys() {
        return List.of(HorusFight.EYE_MODEL);
    }

    public static BossEncounterControllerFactory factory(MythicBossSupport support) {
        return context -> new HorusFight(support, context);
    }
}
