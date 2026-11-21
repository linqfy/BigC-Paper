package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterControllerFactory;

import java.util.List;
import java.util.SplittableRandom;

/**
 * El encuentro de Diablo (Europa medieval). Fase 1: botella de vino que siempre pega, oscuridad y solo el contorno
 * amarillo de Diablo, que se aleja, vuelve y da vueltas. Fase 2 (desde {@code phases.phase-2-at}): se sienta, agarra
 * a todos y cada uno se suelta con un QTE de teclas; cuando no queda nadie agarrado (o se le acaba el tiempo del agarre
 * y revolea a los que quedan) queda aturdido y solo ahi recibe dano. Ver {@link DiabloController}.
 */
public final class DiabloEncounter {

    private DiabloEncounter() {
    }

    /** Claves extra de BetterModel que usa el encuentro (ademas del modelo del jefe); se instalan si vienen en el jar. */
    public static List<String> extraModelKeys() {
        return List.of(DiabloController.BOTTLE_MODEL, DiabloController.HAND_MODEL);
    }

    public static BossEncounterControllerFactory factory(MythicBossSupport support) {
        return context -> new DiabloController(support, context, new SplittableRandom());
    }
}
