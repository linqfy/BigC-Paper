package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

/** Numbers of the Enlil fight (ticks are server ticks, damage in half-hearts), scaled by the audience size. */
public final class EnlilTuning {

    /** Health fraction at which the flood (phase 2) starts. */
    public static final double PHASE_TWO_AT = 0.5;
    /** Height above the walking level at which Enlil hovers in phase 1 (out of melee reach). */
    public static final double HOVER_HEIGHT = 5.0;
    /** Height above the walking level at which Enlil hovers in phase 2 (above the water, inside his tablets). */
    public static final double SHIELD_HEIGHT = 4.5;
    /** Tablets float just above the 2-block flood so swimmers reach them. */
    public static final double TABLET_HEIGHT = 2.3;
    public static final double TABLET_RADIUS = 7.0;
    public static final int TABLETS = 3;

    public static final double GUST_DAMAGE = 3.0;
    public static final double GUST_BRACED_DAMAGE = 1.0;
    public static final double GUST_PUSH = 1.6;
    public static final double GUST_RADIUS = 20.0;
    public static final double LANDING_DAMAGE = 3.0;
    public static final double LANDING_RADIUS = 3.5;
    public static final double TORNADO_DAMAGE = 2.0;
    public static final int TORNADO_HIT_TICKS = 10;
    public static final int TORNADO_LIFE_TICKS = 160;
    public static final double TORNADO_RADIUS = 1.5;
    public static final double TORNADO_HEIGHT = 7.0;
    /** Blocks per tick: slower than walking (0.21) so a tornado can always be outrun. */
    public static final double TORNADO_SPEED = 0.09;

    /** Telegraphs: at least 0.8 s before anything dangerous lands. */
    public static final int GUST_WARN_TICKS = 20;
    public static final int TORNADO_WARN_TICKS = 24;
    public static final int DESCEND_WARN_TICKS = 24;

    public static final int MOVE_TICKS = 20;
    public static final int GROUNDED_TICKS = 100;
    public static final int RAGE_TICKS = 60;
    public static final int FALL_TICKS = 15;
    public static final int FALLEN_TICKS = 160;
    public static final int RISE_TICKS = 40;

    /** Ticks after the start of phase 2 at which each flood layer starts to fill. */
    public static final int FLOOD_LAYER_ONE_AT = 40;
    public static final int FLOOD_LAYER_TWO_AT = 400;
    public static final int FLOOD_LAYERS = 2;

    private EnlilTuning() {
    }

    /** Hits each tablet takes: 3 alone, one more per extra player, at most 6. */
    public static int tabletHits(int players) {
        return Math.min(6, 3 + Math.max(0, players - 1));
    }

    /** Tornadoes per cast: 2, one more for every two extra players, at most 4. */
    public static int tornadoCount(int players) {
        return Math.min(4, 2 + Math.max(0, players - 1) / 2);
    }

    /** How many flood layers should be full {@code ticks} after phase 2 started. */
    public static int floodLayersAt(long ticks) {
        if (ticks >= FLOOD_LAYER_TWO_AT) {
            return 2;
        }
        return ticks >= FLOOD_LAYER_ONE_AT ? 1 : 0;
    }
}
