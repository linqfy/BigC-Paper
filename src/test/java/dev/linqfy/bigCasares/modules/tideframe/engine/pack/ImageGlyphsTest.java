package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImageGlyphsTest {
    @Test void duplicatesAndInputOrderDoNotChangeSortedGlyphs() throws Exception {
        var first = ImageAsset.fromPng(PngFixtures.rgba(1, 1, 0xff123456));
        var second = ImageAsset.fromPng(PngFixtures.rgba(1, 1, 0xffabcdef));
        var duplicate = ImageAsset.fromPng(first.png());
        var glyphs = ImageGlyphs.create(List.of(second, first, duplicate));
        var expected = new ArrayList<>(List.of(first, second)); expected.sort(Comparator.comparing(ImageAsset::sha256));
        assertEquals(expected, glyphs.images()); assertEquals(2, glyphs.size());
        assertEquals('\ue100', glyphs.glyph(expected.get(0))); assertEquals('\ue101', glyphs.glyph(expected.get(1)));
        assertEquals(glyphs.glyph(first), ImageGlyphs.create(List.of(first, second)).glyph(first));
        assertTrue(glyphs.contains(duplicate)); assertFalse(glyphs.contains(null));
        assertThrows(UnsupportedOperationException.class, () -> glyphs.images().clear());
        var unknown = ImageAsset.fromPng(PngFixtures.rgba(1, 1, 0xff654321));
        assertThrows(IllegalArgumentException.class, () -> glyphs.glyph(unknown));
    }

    @Test void limitCountsUniqueImagesAndCopiedCollectionCannotChangeMapping() throws Exception {
        var images = new ArrayList<ImageAsset>();
        for (int i = 0; i < 128; i++) images.add(ImageAsset.fromPng(PngFixtures.rgba(1, 1, 0xff000000 | i)));
        images.add(images.get(0));
        var glyphs = ImageGlyphs.create(images); assertEquals(128, glyphs.size());
        assertEquals('\ue17f', glyphs.glyph(glyphs.images().get(127)));
        images.add(ImageAsset.fromPng(PngFixtures.rgba(1, 1, 0xff000080)));
        assertEquals(128, glyphs.size());
        assertThrows(IllegalArgumentException.class, () -> ImageGlyphs.create(images));
        images.clear(); assertEquals(128, glyphs.size());
    }

    @Test void emptyRegistryHasNoGlyphAndNullAssetsAreRejected() throws Exception {
        var empty = ImageGlyphs.empty(); assertEquals(List.of(), empty.images()); assertEquals(0, empty.size());
        var image = ImageAsset.fromPng(PngFixtures.rgba(1, 1));
        assertFalse(empty.contains(image));
        assertThrows(IllegalArgumentException.class, () -> empty.glyph(image));
        assertThrows(IllegalArgumentException.class, () -> ImageGlyphs.create(null));
        assertThrows(IllegalArgumentException.class, () -> ImageGlyphs.create(java.util.Arrays.asList(image, null)));
    }
}
