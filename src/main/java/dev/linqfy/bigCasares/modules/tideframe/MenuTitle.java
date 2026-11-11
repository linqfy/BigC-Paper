package dev.linqfy.bigCasares.modules.tideframe;

import java.util.Objects;

/**
 * An inventory title: the menu's own text, and optionally the background glyph drawn behind the whole window.
 *
 * @param background glyph string in the {@link #FONT} font, or {@code null} for the plain title
 * @param text       the title text with legacy colour codes, as the menu always wrote it
 */
public record MenuTitle(String background, String text) {
    public static final String FONT = "bigcasares:tideframe_ui";
    /** Space glyphs of {@link #FONT}: back 8 pixels to the window's left edge, and from its right edge to the title. */
    static final char BACK_TO_EDGE = '';
    static final char BACK_TO_TITLE = '';

    public MenuTitle {
        Objects.requireNonNull(text, "text");
    }

    public static MenuTitle plain(String text) {
        return new MenuTitle(null, text);
    }

    /** The background of {@code screen}, drawn from the window's top-left corner, then the title where it always was. */
    public static MenuTitle drawn(MenuScreen screen, String text) {
        return new MenuTitle("" + BACK_TO_EDGE + screen.glyph() + BACK_TO_TITLE, text);
    }

    public boolean drawsBackground() {
        return background != null;
    }
}
