package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

/** The colour of one painted-floor round and what it does to whoever stands on it. */
public enum PachamamaColour {
    /** Random cells: water fills them and lightning falls on the players standing there. */
    BLUE("§9", "azul", "¡Agua y rayo!"),
    /** Chessboard: the ground shakes (damage, nausea, a small jolt). */
    BROWN("§6", "marrón", "¡La tierra tiembla!"),
    /** Chessboard: an extreme hunger. */
    YELLOW("§e", "amarillo", "¡Hambre de la tierra!");

    private final String code;
    private final String spanish;
    private final String strike;

    PachamamaColour(String code, String spanish, String strike) {
        this.code = code;
        this.spanish = spanish;
        this.strike = strike;
    }

    /** Legacy colour code for messages. */
    public String code() {
        return code;
    }

    public String spanish() {
        return spanish;
    }

    /** What the action bar says when the effect lands. */
    public String strike() {
        return strike;
    }

    /** Blue picks scattered cells; the others paint one colour of the chessboard. */
    public boolean chessboard() {
        return this != BLUE;
    }
}
