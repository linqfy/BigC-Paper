package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShapeGlyphsTest {
    @Test void selectsMatchingIndependentFillAndOutlineForEachSupportedShape() {
        assertEquals(new ShapeGlyphs.Pair('\uE001', '\uE004'), ShapeGlyphs.forShape("rectangle"));
        assertEquals(new ShapeGlyphs.Pair('\uE002', '\uE005'), ShapeGlyphs.forShape("rounded"));
        assertEquals(new ShapeGlyphs.Pair('\uE003', '\uE006'), ShapeGlyphs.forShape("ellipse"));
    }
    @Test void invalidNamesDoNotSilentlyRenderAnotherShape() {
        assertThrows(IllegalArgumentException.class, () -> ShapeGlyphs.forShape("circle"));
        assertThrows(IllegalArgumentException.class, () -> ShapeGlyphs.forShape("RECTANGLE"));
        assertThrows(IllegalArgumentException.class, () -> ShapeGlyphs.forShape(null));
    }
}
