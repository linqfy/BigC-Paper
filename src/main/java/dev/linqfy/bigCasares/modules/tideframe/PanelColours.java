package dev.linqfy.bigCasares.modules.tideframe;

import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/** Reversible tints allow the Java pack to fit only menu glyphs to the viewport. */
final class PanelColours {
    private PanelColours() {}

    static int marker(int rgb) {
        return (rgb & 0xffffff) ^ 0x010204;
    }

    static int iconMarker(int offset) {
        return 0xfdfaf0 + offset;
    }

    /** A block face: the shader projects the glyph onto face 0 (top), 1 (left) or 2 (right) of the cube. */
    static int cubeMarker(int face, int offset) {
        return 0xfdfaf4 + face * 4 + offset;
    }

    static String shaderCases() {
        var colours = new TreeSet<>(List.of(0, 0x7d7d79, 0x1b1b19, 0x0c0c0b, 0x1e2a16,
                0x1d1d1b, 0x56544f, 0x9dff22, 0x5ed10f, 0x3a8209, 0x4f9a41, 0x3c7a31,
                0x8fdc7c, 0x6dbd5b, 0x1e4718, 0x77777a, 0xa0a09c, 0x3a3a38));
        for (int text : new int[]{ScreenArt.WHITE, ScreenArt.GRAY, ScreenArt.GOLD, ScreenArt.GREEN,
                ScreenArt.RED, ScreenArt.YELLOW, ScreenArt.AQUA, 0x808080, 0x555555}) {
            colours.add(text & 0xffffff);
            colours.add((text & 0xfcfcfc) >>> 2);
        }
        collect(colours, ScreenArt.closeHover());
        collect(colours, new PixelImage(11, 11).button(0, 0, 11, 11));
        for (int lines = 1; lines <= 3; lines++) {
            collect(colours, ScreenArt.tooltip(Collections.nCopies(lines, "X"),
                    Collections.nCopies(lines, ScreenArt.WHITE)));
        }
        var cases = new StringBuilder();
        for (int rgb : colours) cases.append("        case ").append(marker(rgb)).append(":\n");
        return cases.toString();
    }

    private static void collect(TreeSet<Integer> colours, PixelImage image) {
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                int argb = image.get(x, y);
                if ((argb >>> 24) != 0) colours.add(argb & 0xffffff);
            }
        }
    }
}
