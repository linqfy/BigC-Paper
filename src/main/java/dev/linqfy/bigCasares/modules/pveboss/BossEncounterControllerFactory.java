package dev.linqfy.bigCasares.modules.pveboss;

/** Creates the scripted controller of one boss instance. */
@FunctionalInterface
public interface BossEncounterControllerFactory {

    BossEncounterController create(BossEncounterContext context);
}
