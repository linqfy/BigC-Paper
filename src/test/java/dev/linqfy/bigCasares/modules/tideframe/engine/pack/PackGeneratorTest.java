package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class PackGeneratorTest {
    @TempDir Path folder;

    @Test void deterministicOriginalPackHasOnlyOwnedResourcesAndActualHashes() throws Exception {
        var first = PackGenerator.generate(folder.resolve("one"));
        var second = PackGenerator.generate(folder.resolve("two"));
        byte[] bytes = Files.readAllBytes(first.path());
        assertArrayEquals(bytes, Files.readAllBytes(second.path()));
        assertArrayEquals(MessageDigest.getInstance("SHA-1").digest(bytes), first.sha1());
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(bytes), first.sha256());
        try (var zip = new ZipFile(first.path().toFile())) {
            var names = new TreeSet<String>();
            zip.stream().forEach(entry -> names.add(entry.getName()));
            assertEquals(Set.of("pack.mcmeta", "LICENSE.txt", "ASSET_PROVENANCE.txt",
                    "assets/tideframe/font/ui.json", "assets/tideframe/textures/font/atlas.png",
                    "assets/tideframe/textures/font/cursor.png", "assets/tideframe/textures/font/panel.png",
                    "assets/tideframe/textures/font/rounded.png", "assets/tideframe/textures/font/ellipse.png",
                    "assets/tideframe/textures/font/border_rectangle.png", "assets/tideframe/textures/font/border_rounded.png",
                    "assets/tideframe/textures/font/border_ellipse.png"), names);
            String metadata = new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(metadata.contains("\"pack_format\": 34"));
            assertTrue(metadata.contains("\"supported_formats\": [34, 97]"));
            assertTrue(metadata.contains("\"min_format\": 34"));
            assertTrue(metadata.contains("\"max_format\": [97, 1]"));
            String font = new String(zip.getInputStream(zip.getEntry("assets/tideframe/font/ui.json")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(font.contains("tideframe:font/atlas.png"));
            assertTrue(font.contains("\\ue000"));
            assertFalse(font.contains("minecraft:"));
            var atlas = ImageIO.read(zip.getInputStream(zip.getEntry("assets/tideframe/textures/font/atlas.png")));
            assertEquals(96, atlas.getWidth());
            assertEquals(48, atlas.getHeight());
            // Every printable ASCII glyph except space must contain independently drawn pixels.
            for (int code = 33; code <= 126; code++) {
                int startX = ((code - 32) % 16) * 6, startY = ((code - 32) / 16) * 8;
                boolean drawn = false;
                for (int x = 0; x < 6; x++) for (int y = 0; y < 8; y++)
                    drawn |= (atlas.getRGB(startX + x, startY + y) >>> 24) != 0;
                assertTrue(drawn, "Blank printable glyph " + code);
            }
            var cursor = ImageIO.read(zip.getInputStream(zip.getEntry("assets/tideframe/textures/font/cursor.png")));
            assertEquals(9, cursor.getWidth());
            assertEquals(9, cursor.getHeight());
            assertNotEquals(0, cursor.getRGB(4, 4) >>> 24);
            assertEquals(0, cursor.getRGB(0, 0) >>> 24);
            assertTrue(new String(zip.getInputStream(zip.getEntry("LICENSE.txt")).readAllBytes(), StandardCharsets.UTF_8).contains("MIT License"));
            assertTrue(new String(zip.getInputStream(zip.getEntry("ASSET_PROVENANCE.txt")).readAllBytes(), StandardCharsets.UTF_8).contains("No installed font"));
        }
    }

    @Test void generatedShapeMasksHaveDistinctCornersAndSinglePixelErosionOutlines() throws Exception {
        var artifact = PackGenerator.generate(folder);
        try (var zip = new ZipFile(artifact.path().toFile())) {
            var rectangle = shape(zip, "panel");
            var rounded = shape(zip, "rounded");
            var ellipse = shape(zip, "ellipse");
            assertEquals(0xffffffff, rectangle.getRGB(0, 0));
            assertEquals(0, rounded.getRGB(0, 0)); assertEquals(0, ellipse.getRGB(0, 0));
            assertEquals(0xffffffff, rounded.getRGB(0, 2));
            assertEquals(0, ellipse.getRGB(0, 2));
            for (var fill : new java.awt.image.BufferedImage[]{rectangle, rounded, ellipse}) {
                assertEquals(0xffffffff, fill.getRGB(7, 7));
                assertEquals(0xffffffff, fill.getRGB(7, 0));
                assertEquals(0xffffffff, fill.getRGB(0, 7));
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
                    assertEquals(fill.getRGB(x, y), fill.getRGB(15 - x, y), "Horizontal symmetry");
                    assertEquals(fill.getRGB(x, y), fill.getRGB(x, 15 - y), "Vertical symmetry");
                    assertTrue(fill.getRGB(x, y) == 0 || fill.getRGB(x, y) == 0xffffffff, "Binary white mask");
                }
            }
            String[] names = {"rectangle", "rounded", "ellipse"};
            var fills = new java.awt.image.BufferedImage[]{rectangle, rounded, ellipse};
            for (int i = 0; i < names.length; i++) {
                var outline = shape(zip, "border_" + names[i]);
                assertEquals(0, outline.getRGB(7, 7), "Outline has no interior");
                assertEquals(0xffffffff, outline.getRGB(7, 0), "Top edge is visible");
                int boundaryCount = 0;
                for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
                    boolean inside = fills[i].getRGB(x, y) != 0;
                    boolean eroded = inside;
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                        int nx = x + dx, ny = y + dy;
                        eroded &= nx >= 0 && nx < 16 && ny >= 0 && ny < 16 && fills[i].getRGB(nx, ny) != 0;
                    }
                    boolean expectedBoundary = inside && !eroded;
                    assertEquals(expectedBoundary ? 0xffffffff : 0, outline.getRGB(x, y), names[i] + " boundary at " + x + "," + y);
                    if (expectedBoundary) boundaryCount++;
                }
                assertTrue(boundaryCount >= 40 && boundaryCount <= 100, "Bounded perimeter");
            }
        }
    }

    @Test void fontProvidersMapEveryOwnedShapeToItsExplicitGlyph() throws Exception {
        var artifact = PackGenerator.generate(folder);
        try (var zip = new ZipFile(artifact.path().toFile())) {
            String font = new String(zip.getInputStream(zip.getEntry("assets/tideframe/font/ui.json")).readAllBytes(), StandardCharsets.UTF_8);
            String[] names = {"panel", "rounded", "ellipse", "border_rectangle", "border_rounded", "border_ellipse"};
            for (int i = 0; i < names.length; i++) {
                String glyph = String.format(java.util.Locale.ROOT, "\\u%04x", 0xe001 + i);
                assertTrue(font.contains("{\"type\":\"bitmap\",\"file\":\"tideframe:font/" + names[i] + ".png\",\"ascent\":16,\"height\":16,\"chars\":[\"" + glyph + "\"]}"), names[i]);
            }
        }
    }

    private static java.awt.image.BufferedImage shape(ZipFile zip, String name) throws Exception {
        var entry = zip.getEntry("assets/tideframe/textures/font/" + name + ".png");
        assertNotNull(entry, "Own shape texture missing: " + name);
        var image = ImageIO.read(zip.getInputStream(entry));
        assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
        return image;
    }

    @Test void ownPanelIsAnOpaqueSquareAndExistingAtlasAndCursorStayByteIdentical() throws Exception {
        var artifact = PackGenerator.generate(folder);
        try (var zip = new ZipFile(artifact.path().toFile())) {
            var entry = zip.getEntry("assets/tideframe/textures/font/panel.png");
            assertNotNull(entry, "Original panel texture is missing");
            var panel = ImageIO.read(zip.getInputStream(entry));
            assertEquals(16, panel.getWidth()); assertEquals(16, panel.getHeight());
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++)
                assertEquals(0xffffffff, panel.getRGB(x, y), "Panel must be opaque white at " + x + "," + y);
            String font = new String(zip.getInputStream(zip.getEntry("assets/tideframe/font/ui.json")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(font.contains("{\"type\":\"bitmap\",\"file\":\"tideframe:font/panel.png\",\"ascent\":16,\"height\":16,\"chars\":[\"\\ue001\"]}"));
            String provenance = new String(zip.getInputStream(zip.getEntry("ASSET_PROVENANCE.txt")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(provenance.contains("panel.png: original solid 16-by-16 white square, U+E001"));
            for (String[] original : new String[][] {
                    {"atlas.png", "16618f7fd7891404b68058e88130d230fa403dec60a43d82888f9bdf749f846d"},
                    {"cursor.png", "2485301aee36bd46815387b5e7e20855e07692012e748aa34075e80d942f12b5"}}) {
                byte[] bytes = zip.getInputStream(zip.getEntry("assets/tideframe/textures/font/" + original[0])).readAllBytes();
                assertEquals(original[1], HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            }
        }
    }

    @Test void artifactHashArraysCannotBeMutatedThroughConstructorOrAccessors() throws Exception {
        byte[] one = new byte[20], two = new byte[32];
        var artifact = new PackGenerator.Artifact(folder.resolve("pack.zip"), one, two);
        one[0] = 7; two[0] = 9;
        assertEquals(0, artifact.sha1()[0]); assertEquals(0, artifact.sha256()[0]);
        byte[] returned = artifact.sha1(); returned[0] = 12;
        byte[] returnedTwo = artifact.sha256(); returnedTwo[0] = 12;
        assertEquals(0, artifact.sha1()[0]); assertEquals(0, artifact.sha256()[0]);
        assertThrows(IllegalArgumentException.class, () -> new PackGenerator.Artifact(folder, new byte[1], new byte[32]));
    }
}
