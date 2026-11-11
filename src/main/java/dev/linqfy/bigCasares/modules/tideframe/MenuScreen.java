package dev.linqfy.bigCasares.modules.tideframe;

/**
 * The chest menus that get a drawn background. Each one has a glyph in the {@code bigcasares:tideframe_ui} font
 * (resourcepack/java/assets/bigcasares/font/tideframe_ui.json) whose texture covers the whole chest window.
 */
public enum MenuScreen {
    MISSIONS(6, ''),
    SHOP_CATEGORIES(3, ''),
    SHOP_ITEMS(6, '');

    private final int rows;
    private final char glyph;

    MenuScreen(int rows, char glyph) {
        this.rows = rows;
        this.glyph = glyph;
    }

    /** Chest rows of the inventory this background is drawn for (its texture is 176 x (rows * 18 + 114) pixels). */
    public int rows() {
        return rows;
    }

    public int size() {
        return rows * 9;
    }

    public char glyph() {
        return glyph;
    }
}
