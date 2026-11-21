package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

/** The numbers of the Pachamama fight (ticks are server ticks, damage is in half hearts). */
public final class PachamamaTuning {

    /** Phase 2 starts at or below this fraction of the maximum health. */
    public static final double PHASE_TWO_AT = 0.5;
    /** A Mandinga guitar note hurts her this many times its damage (12 -> 30). */
    public static final double NOTE_MULTIPLIER = 2.5;

    /** She rises from the earth: no damage and no rounds while it lasts. */
    public static final int RISE_TICKS = 70;
    /** Phase change: rage, Mandinga crawls out of her back; she cannot be hurt meanwhile. */
    public static final int TRANSITION_TICKS = 80;

    /** Floor tiles are squares of this many blocks (a chessboard over the room). */
    public static final int TILE_SIZE = 3;
    /** A tile is used only when at least this many of its columns have a paintable floor. */
    public static final int MIN_TILE_COLUMNS = 5;

    // ----------------------------------------------------------------------- painted-floor rounds

    /** The cells stay painted (the telegraph) this long before the colour's effect lands (>= 0.8 s). */
    public static final int PAINT_TICKS_ONE = 44;
    public static final int PAINT_TICKS_TWO = 52;
    /** How long the effect keeps acting on the painted cells. */
    public static final int EFFECT_TICKS_ONE = 50;
    public static final int EFFECT_TICKS_TWO = 40;
    /** Pause between the end of a round (floor restored) and the next paint. */
    public static final int REST_TICKS_ONE = 60;
    public static final int REST_TICKS_TWO = 110;
    /** The effect re-applies to whoever stands on a painted cell every this many ticks. */
    public static final int PULSE_TICKS = 10;

    /** Blue: share of the tiles picked at random. */
    public static final double BLUE_SHARE_ONE = 0.45;
    public static final double BLUE_SHARE_TWO = 0.32;
    /** Brown/yellow: share of the tiles of the chosen chessboard colour that really get painted. */
    public static final double BOARD_KEEP_ONE = 0.9;
    public static final double BOARD_KEEP_TWO = 0.62;

    /** Blue: the lightning on whoever stands on a painted cell when it lands. */
    public static final double LIGHTNING_DAMAGE = 6.0;
    /** Blue: each pulse inside the water. */
    public static final double WATER_DAMAGE = 2.0;
    /** Brown: each tremor pulse. */
    public static final double QUAKE_DAMAGE = 3.0;
    public static final int NAUSEA_TICKS = 140;
    public static final double QUAKE_LIFT = 0.42;
    /** Yellow: food points taken at once, then a strong Hunger effect while standing on the colour. */
    public static final int HUNGER_FOOD_DRAIN = 6;
    public static final int HUNGER_AMPLIFIER = 49;
    public static final int HUNGER_TICKS = 100;

    // ----------------------------------------------------------------------- phase 2: Mandinga

    /** A chord every this many ticks (the first one {@link #FIRST_CHORD_TICKS} after the transition). */
    public static final int CHORD_PERIOD_TICKS = 150;
    public static final int FIRST_CHORD_TICKS = 100;
    /** The strum (sound + pose) comes this long before the wave leaves (>= 0.8 s). */
    public static final int CHORD_WARN_TICKS = 24;
    /** Blocks per tick the ring of the shockwave grows. */
    public static final double WAVE_SPEED = 0.45;
    public static final double WAVE_MAX_RADIUS = 18.0;
    /** Half the thickness of the ring: a player this close to the front when it passes is caught. */
    public static final double WAVE_HALF_WIDTH = 0.7;
    public static final double WAVE_DAMAGE = 6.0;
    /** Ticks a player must hold sneak on the pedestal to accept the deal (fallback to the click). */
    public static final int DEAL_SNEAK_TICKS = 60;
    /** A click on the pedestal only arms the deal; a second click by the same player within this many ticks pays. */
    public static final int DEAL_CONFIRM_TICKS = 60;
    /**
     * Holding the use key on the pedestal repeats the click every 4 ticks: the confirming click must come after a
     * pause longer than this, so a held right-click (eating, a shield) never pays.
     */
    public static final int DEAL_REPEAT_GAP_TICKS = 5;
    /** The offer is repeated this often while the pedestal stands. */
    public static final int OFFER_REPEAT_TICKS = 400;

    private PachamamaTuning() {
    }

    public static int paintTicks(boolean phaseTwo) {
        return phaseTwo ? PAINT_TICKS_TWO : PAINT_TICKS_ONE;
    }

    public static int effectTicks(boolean phaseTwo) {
        return phaseTwo ? EFFECT_TICKS_TWO : EFFECT_TICKS_ONE;
    }

    public static int restTicks(boolean phaseTwo) {
        return phaseTwo ? REST_TICKS_TWO : REST_TICKS_ONE;
    }

    public static double blueShare(boolean phaseTwo) {
        return phaseTwo ? BLUE_SHARE_TWO : BLUE_SHARE_ONE;
    }

    public static double boardKeep(boolean phaseTwo) {
        return phaseTwo ? BOARD_KEEP_TWO : BOARD_KEEP_ONE;
    }

    /** Salamanca vipers that come out with Mandinga: one per fighter, at most three. */
    public static int vipers(int players) {
        return Math.max(1, Math.min(3, players));
    }
}
