package dev.linqfy.bigCasares.modules.tideframe;

import java.util.List;

/**
 * The look the Tideframe screens share, in pixels of the 256 x 144 canvas: the world dimmed behind, a window of dark
 * stone in a wooden frame (x 14..241, y 36..114), the game's buttons, a close button at the top right, and tabs with
 * items hanging under the window, the chosen one opening into it. The window starts at y 36 and keeps clear of the
 * corners, where players' HUD mods draw (minimap, coordinates).
 */
public final class ScreenArt {
    public static final int WIN_X = 14;
    public static final int WIN_Y = 36;
    public static final int WIN_W = 228;
    public static final int WIN_H = 79;
    public static final int TAB_Y = WIN_Y + WIN_H;
    public static final int TAB_W = 24;
    public static final int TAB_H = 20;
    /** At most this many tabs fit left of the shortcut tab. */
    public static final int MAX_TABS = 7;
    public static final int NAV_X = 212;
    /** Top of a capital letter on the heading line. */
    public static final int HEADER_TOP = 43;
    public static final int[] CLOSE = {227, 41, 238, 52};
    /** The game's chat colours. */
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFFAAAAAA;
    public static final int GOLD = 0xFFFFAA00;
    public static final int YELLOW = 0xFFFFFF55;
    public static final int GREEN = 0xFF55FF55;
    public static final int RED = 0xFFFF5555;
    public static final int AQUA = 0xFF55FFFF;
    private static final int EDGE = PixelImage.rgb(0x150d06);
    private static final int PLANK = PixelImage.rgb(0x6c4521);
    private static final int PLANK_LIGHT = PixelImage.rgb(0x96622e);
    /** The chosen tab's art is cut with a margin of the same pixels around it, so its edges never show a seam. */
    private static final int MARGIN_TOP = 6;
    private static final int MARGIN_SIDE = 1;

    private ScreenArt() {
    }

    public static int tabX(int index) {
        return 20 + 26 * index;
    }

    /** The canvas with the dimmed world, the window, the close button and the given unselected tabs. */
    public static PixelImage window(int seed, List<Integer> tabXs) {
        PixelImage image = new PixelImage(256, 144);
        // The world goes dark behind a screen, as in the game; it steps in from the top edge of the canvas.
        for (int y = 0; y < 144; y++) {
            image.fill(0, y, 256, 1, PixelImage.rgba(0x101010, (int) Math.round(255 * Math.min(0.55, 0.15 + 0.1 * Math.floor(y / 2.0)))));
        }
        image.stone(WIN_X + 4, WIN_Y + 4, WIN_W - 8, WIN_H - 8, seed, 1).wood(WIN_X, WIN_Y, WIN_W, WIN_H, seed);
        for (int x : tabXs) {
            tab(image, x, false, seed);
        }
        image.button(CLOSE[0], CLOSE[1], 11, 11);
        return image;
    }

    /** A tab under the window. Unselected: darker stone below the frame. Chosen: it opens the frame and joins the window. */
    public static void tab(PixelImage image, int x, boolean chosen, int seed) {
        int top = chosen ? TAB_Y - 4 : TAB_Y;
        image.stone(x + 2, top, TAB_W - 4, TAB_Y + TAB_H - 2 - top, seed, chosen ? 1 : 0.7);
        for (int y = top; y < TAB_Y + TAB_H - 1; y++) {
            image.put(x, y, EDGE).put(x + 1, y, chosen ? PLANK_LIGHT : PLANK).put(x + TAB_W - 2, y, PLANK).put(x + TAB_W - 1, y, EDGE);
        }
        image.fill(x + 1, TAB_Y + TAB_H - 2, TAB_W - 2, 1, PLANK).fill(x, TAB_Y + TAB_H - 1, TAB_W, 1, EDGE);
    }

    /** The chosen tab at {@code x}, cut from {@code base} with its margin; place it at {@link #chosenTabBox}. */
    public static PixelImage chosenTab(int x, int seed, PixelImage base) {
        PixelImage image = base.copy();
        tab(image, x, true, seed);
        PixelImage piece = image.crop(x - MARGIN_SIDE, TAB_Y - MARGIN_TOP, TAB_W + 2 * MARGIN_SIDE, TAB_H + MARGIN_TOP);
        // Beside the tab, below the window, the art is the dimmed world: left out, or it would be dimmed twice.
        for (int y = MARGIN_TOP; y < piece.height(); y++) {
            piece.put(0, y, 0).put(piece.width() - 1, y, 0);
        }
        return piece;
    }

    public static int[] chosenTabBox(int x) {
        return new int[] {x - MARGIN_SIDE, TAB_Y - MARGIN_TOP, x + TAB_W + MARGIN_SIDE, TAB_Y + TAB_H};
    }

    public static PixelImage tabHover() {
        return new PixelImage(TAB_W, TAB_H).fill(2, 0, TAB_W - 4, TAB_H - 2, PixelImage.rgba(0xffffff, 56));
    }

    public static PixelImage closeHover() {
        return new PixelImage(11, 11).button(0, 0, 11, 11, 0xa83232, 0xd86a6a, 0x5a1818);
    }

    /** A slot's hover: the game's white wash over the item area. */
    public static PixelImage slotHover() {
        return new PixelImage(18, 18).fill(1, 1, 16, 16, PixelImage.rgba(0xffffff, 128));
    }

    /** A heading line in {@code width} pixels, cut with … when it does not fit. */
    public static PixelImage header(String text, int colour, int width) {
        PixelImage image = new PixelImage(width, 10);
        PixelFont font = PixelFont.get();
        font.draw(image, font.fit(text, width - 1), 0, 2, colour);
        return image;
    }

    /** Text centred in {@code image} (a button face), cut to fit. */
    public static PixelImage fitted(PixelImage image, String text, int colour) {
        PixelFont font = PixelFont.get();
        font.centre(image, font.fit(text, image.width() - 4), image.width() / 2.0, 2, colour);
        return image;
    }

    /** A tooltip like the game's: near-black plate, purple rim, one line of text every 10 px. */
    public static PixelImage tooltip(List<String> texts, List<Integer> colours) {
        PixelFont font = PixelFont.get();
        int textWidth = 0;
        for (String text : texts) {
            textWidth = Math.max(textWidth, font.width(PixelFont.sanitize(text)));
        }
        int w = textWidth + 9, h = texts.size() * 10 + 6;
        PixelImage image = new PixelImage(w, h);
        image.fill(1, 0, w - 2, h, PixelImage.rgba(0x170f1d, 242)).fill(0, 1, w, h - 2, PixelImage.rgba(0x170f1d, 242));
        for (int j = 1; j < h - 1; j++) {
            double t = (j - 1) / (double) (h - 3);
            int red = (int) Math.round(80 - 40 * t), blue = (int) Math.round(255 - 128 * t);
            int rim = PixelImage.rgb((int) Math.round(23 + (red - 23) * 0.42), (int) Math.round(15 * 0.58), (int) Math.round(29 + (blue - 29) * 0.42));
            if (j == 1 || j == h - 2) {
                image.fill(1, j, w - 2, 1, rim);
            } else {
                image.put(1, j, rim).put(w - 2, j, rim);
            }
        }
        for (int k = 0; k < texts.size(); k++) {
            font.draw(image, texts.get(k), 4, 4 + k * 10, colours.get(k));
        }
        return image;
    }

    /** A small triangle pointing left or right (4 x 7 pixels from {@code x, y}) with the game's text shadow. */
    public static void arrow(PixelImage image, int x, int y, boolean left, int colour) {
        int shadow = 0xFF000000 | (colour >> 16 & 255) >> 2 << 16 | (colour >> 8 & 255) >> 2 << 8 | (colour & 255) >> 2;
        for (int pass = 1; pass >= 0; pass--) {
            for (int k = 0; k < 4; k++) {
                int column = left ? x + k : x + 3 - k;
                for (int row = 3 - k; row <= 3 + k; row++) {
                    image.put(column + pass, y + row + pass, pass == 1 ? shadow : colour);
                }
            }
        }
    }

    /** An amount short enough for the screens: 52.5, 5000, 13k, 999k, 1.2M, 45M. */
    public static String amount(double value) {
        double abs = Math.abs(value);
        String compact;
        if (abs < 100 && abs != Math.rint(abs)) {
            compact = number(abs);
        } else if (Math.round(abs) < 10_000) {
            compact = Long.toString(Math.round(abs));
        } else if (Math.round(abs / 1_000) < 1_000) {
            compact = Math.round(abs / 1_000) + "k";
        } else {
            double millions = abs / 1_000_000;
            compact = millions < 9.95 ? number(millions) + "M" : Math.round(millions) + "M";
        }
        return value < 0 ? "-" + compact : compact;
    }

    private static String number(double value) {
        return java.math.BigDecimal.valueOf(value).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
