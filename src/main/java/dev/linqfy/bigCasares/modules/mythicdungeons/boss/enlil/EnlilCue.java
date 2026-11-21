package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

/** Something the server glue must show or do on a given tick of the fight. */
public enum EnlilCue {
    /** Gust telegraph: the inhale; the gust lands {@link EnlilTuning#GUST_WARN_TICKS} later. */
    GUST_WARN,
    GUST,
    /** Tornado telegraph: rings on the ground; the columns rise {@link EnlilTuning#TORNADO_WARN_TICKS} later. */
    TORNADO_WARN,
    TORNADO,
    /** Landing ring under Enlil; he starts to come down {@link EnlilTuning#DESCEND_WARN_TICKS} later. */
    DESCEND_WARN,
    DESCEND,
    /** Touchdown: the vulnerable window of phase 1 opens. */
    LANDED,
    ASCEND,
    AIRBORNE,
    /** Phase 2: the three tablets (re)appear around him. */
    TABLETS_FORM,
    /** Phase 2: the last tablet broke and he drops into the water. */
    FALL,
    /** Phase 2: he is down; the vulnerable window opens. */
    FALLEN,
    /** Phase 2: the window closes and he rises; the tablets re-form when he is up. */
    RISE
}
