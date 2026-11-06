package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

/** Original font glyph identities shared by generation and rendering. */
public final class ShapeGlyphs {
    private ShapeGlyphs() { }
    public record Pair(char fill, char outline) { }
    public static Pair forShape(String shape) {
        if (shape == null) throw new IllegalArgumentException("Shape must be specified");
        return switch (shape) {
            case "rectangle" -> new Pair('\uE001', '\uE004');
            case "rounded" -> new Pair('\uE002', '\uE005');
            case "ellipse" -> new Pair('\uE003', '\uE006');
            default -> throw new IllegalArgumentException("Unknown shape: " + shape);
        };
    }
}
