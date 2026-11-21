package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.pveboss.BossEncounterControllerFactory;

import java.util.List;

/**
 * El encuentro de Zeus. Fase 1: patrones de rayos en el piso que hay que esquivar mientras se le pega a mano (los
 * proyectiles no le hacen nada). Fase 2 (desde la mitad de la vida): nubes electricas que paralizan al golpear y
 * lanzas que, mientras quedan clavadas, son el unico momento en que Zeus recibe dano. Ver {@link ZeusController}.
 */
public final class ZeusEncounter {

    private ZeusEncounter() {
    }

    /** Claves extra de BetterModel que usa el encuentro (ademas del modelo del jefe); se instalan si vienen en el jar. */
    public static List<String> extraModelKeys() {
        return List.of(ZeusClouds.MODEL, ZeusController.SPEAR_MODEL);
    }

    public static BossEncounterControllerFactory factory(MythicBossSupport support) {
        return context -> new ZeusController(support, context);
    }
}
