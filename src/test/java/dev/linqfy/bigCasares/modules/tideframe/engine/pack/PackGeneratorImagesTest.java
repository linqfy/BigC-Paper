package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class PackGeneratorImagesTest {
    @TempDir Path folder;

    @Test void nativeHudBakesImagesIntoAtlasSizedTilesInsteadOfLegacyImageGlyphs() throws Exception {
        var image=ImageAsset.fromPng(PngFixtures.rgba(256,144,0xff176b88));
        var screen=new Screen("photo","PHOTO",List.of(new Screen.Element("photo","",0,0,1.8,1.3,
                "button","close","rectangle",true,null,false,image)));
        var registry=ImageGlyphs.create(List.of(image));
        var artifact=PackGenerator.generate(folder.resolve("hud"),registry,HudAssets.create(List.of(screen)));
        try(var zip=new ZipFile(artifact.path().toFile())) {
            assertFalse(zip.stream().anyMatch(e->e.getName().startsWith("assets/tideframe/textures/font/images/")));
            assertFalse(text(zip,"assets/tideframe/font/ui.json").contains("font/images/"));
            for(var entry:zip.stream().filter(e->e.getName().startsWith("assets/tideframe/textures/font/hud/")).toList()) {
                var tile=ImageIO.read(zip.getInputStream(entry));
                assertTrue(tile.getWidth()<=256 && tile.getHeight()<=256,entry.getName());
            }
            assertTrue(text(zip,"ASSET_PROVENANCE.txt").contains(image.sha256()));
            assertTrue(text(zip,"ASSET_PROVENANCE.txt").contains("rights"));
        }
    }

    @Test void customPngsUseStableGlyphProvidersAndRetainTheirRgbaPixels() throws Exception {
        var red = ImageAsset.fromPng(PngFixtures.rgba(2, 1, 0x00ff0000, 0x7f123456));
        var blue = ImageAsset.fromPng(PngFixtures.rgba(1, 2, 0xff0011ff));
        var glyphs = ImageGlyphs.create(List.of(blue, red, red));
        var first = PackGenerator.generate(folder.resolve("one"), glyphs);
        var second = PackGenerator.generate(folder.resolve("two"), ImageGlyphs.create(List.of(red, blue)));
        assertArrayEquals(Files.readAllBytes(first.path()), Files.readAllBytes(second.path()));
        try (var zip = new ZipFile(first.path().toFile())) {
            String font = text(zip, "assets/tideframe/font/ui.json");
            for (var image : glyphs.images()) {
                String file = "assets/tideframe/textures/font/images/" + image.sha256() + ".png";
                assertNotNull(zip.getEntry(file));
                assertArrayEquals(image.png(), zip.getInputStream(zip.getEntry(file)).readAllBytes());
                String glyph = String.format(java.util.Locale.ROOT, "\\u%04x", (int) glyphs.glyph(image));
                assertTrue(font.contains("{\"type\":\"bitmap\",\"file\":\"tideframe:font/images/" + image.sha256()
                        + ".png\",\"ascent\":16,\"height\":16,\"chars\":[\"" + glyph + "\"]}"));
            }
            var decoded = ImageIO.read(zip.getInputStream(zip.getEntry("assets/tideframe/textures/font/images/" + red.sha256() + ".png")));
            assertEquals(0x00ff0000, decoded.getRGB(0, 0)); assertEquals(0x7f123456, decoded.getRGB(1, 0));
            assertEquals(2, zip.stream().filter(entry -> entry.getName().startsWith("assets/tideframe/textures/font/images/")).count());
            String provenance = text(zip, "ASSET_PROVENANCE.txt");
            assertTrue(provenance.contains(red.sha256())); assertTrue(provenance.contains(blue.sha256()));
            assertTrue(provenance.contains("operator")); assertTrue(provenance.contains("rights"));
            assertFalse(provenance.contains("All included resources are available under the included MIT License."));
            assertTrue(text(zip, "LICENSE.txt").contains("MIT License"));
        }
    }

    @Test void emptyOverloadPreservesEveryBaselinePackEntryAndBytes() throws Exception {
        var baseline = PackGenerator.generate(folder.resolve("baseline"));
        var empty = PackGenerator.generate(folder.resolve("empty"), ImageGlyphs.empty());
        assertArrayEquals(Files.readAllBytes(baseline.path()), Files.readAllBytes(empty.path()));
        try (var base = new ZipFile(baseline.path().toFile());
             var withImages = new ZipFile(PackGenerator.generate(folder.resolve("custom"),
                     ImageGlyphs.create(List.of(ImageAsset.fromPng(PngFixtures.rgba(1, 1))))).path().toFile())) {
            for (var entry : base.stream().toList()) {
                if (entry.getName().equals("assets/tideframe/font/ui.json") || entry.getName().equals("ASSET_PROVENANCE.txt")) continue;
                assertNotNull(withImages.getEntry(entry.getName()));
                assertArrayEquals(base.getInputStream(entry).readAllBytes(), withImages.getInputStream(withImages.getEntry(entry.getName())).readAllBytes());
            }
            String original = text(base, "assets/tideframe/font/ui.json");
            assertTrue(text(withImages, "assets/tideframe/font/ui.json").startsWith(original.substring(0, original.lastIndexOf("]}"))));
        }
    }

    private static String text(ZipFile zip, String path) throws Exception {
        return new String(zip.getInputStream(zip.getEntry(path)).readAllBytes(), StandardCharsets.UTF_8);
    }
}
