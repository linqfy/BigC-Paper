package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class BossPortraitTest {
    @TempDir Path directory;

    @Test void absentPortraitKeepsBuiltInFallbackWithoutCreatingFiles() throws Exception {
        assertNull(BossPortrait.load(directory));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void validPortraitPreservesTransparentAndSemitransparentPixels() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x00334455);
        image.setRGB(1, 0, 0x7f123456);
        image.setRGB(0, 1, 0xffaabbcc);
        image.setRGB(1, 1, 0xffffffff);
        var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", bytes));
        Files.write(directory.resolve("boss-face.png"), bytes.toByteArray());
        ImageAsset actual = BossPortrait.load(directory);
        assertNotNull(actual);
        assertEquals(2, actual.width()); assertEquals(2, actual.height());
        var decoded = ImageIO.read(new ByteArrayInputStream(actual.png()));
        assertEquals(0, decoded.getRGB(0, 0) >>> 24);
        assertEquals(0x7f123456, decoded.getRGB(1, 0));
        assertEquals(0xffaabbcc, decoded.getRGB(0, 1));
    }

    @Test void corruptPortraitFailsWithPathAndActionablePngMessage() throws Exception {
        Files.write(directory.resolve("boss-face.png"), new byte[]{1, 2, 3, 4});
        var error = assertThrows(IOException.class, () -> BossPortrait.load(directory));
        assertTrue(error.getMessage().contains("boss-face.png"));
        assertTrue(error.getMessage().contains("PNG"));
        assertNotNull(error.getCause());
    }

    @Test void oversizedPortraitIsRejectedBeforeDecoding() throws Exception {
        Path file = directory.resolve("boss-face.png");
        try (var channel = Files.newByteChannel(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.position(8L * 1024 * 1024);
            channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
        }
        var error = assertThrows(IOException.class, () -> BossPortrait.load(directory));
        assertTrue(error.getMessage().contains("8 MiB"));
        assertTrue(error.getMessage().contains("boss-face.png"));
    }

    @Test void directoryCannotMasqueradeAsPortrait() throws Exception {
        Files.createDirectory(directory.resolve("boss-face.png"));
        var error = assertThrows(IOException.class, () -> BossPortrait.load(directory));
        assertTrue(error.getMessage().contains("regular file"));
    }

    @Test void symbolicLinkCannotReadPortraitOutsideConfiguredDirectory() throws Exception {
        Path target = directory.resolve("actual.png");
        Files.write(target, portrait().png());
        try { Files.createSymbolicLink(directory.resolve("boss-face.png"), target); }
        catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable: " + unsupported.getMessage());
        }
        var error = assertThrows(IOException.class, () -> BossPortrait.load(directory));
        assertTrue(error.getMessage().contains("symbolic link"));
    }

    @Test void pngWithDamagedChunkChecksumIsRejected() throws Exception {
        byte[] damaged = portrait().png();
        damaged[damaged.length - 1] ^= 1;
        Files.write(directory.resolve("boss-face.png"), damaged);
        assertThrows(IOException.class, () -> BossPortrait.load(directory));
    }

    @Test void explicitNullPortraitKeepsLegacyPackBytes() throws Exception {
        var hud = hud();
        var legacy = PackGenerator.generate(directory.resolve("old"), ImageGlyphs.empty(), hud);
        var explicit = PackGenerator.generate(directory.resolve("new"), ImageGlyphs.empty(), hud, null);
        assertArrayEquals(Files.readAllBytes(legacy.path()), Files.readAllBytes(explicit.path()));
    }

    @Test void portraitIsIgnoredWithoutHudAndDoesNotAddBossResources() throws Exception {
        var portrait = portrait();
        var legacy = PackGenerator.generate(directory.resolve("old"));
        var explicit = PackGenerator.generate(directory.resolve("new"), ImageGlyphs.empty(), HudAssets.empty(), portrait);
        assertArrayEquals(Files.readAllBytes(legacy.path()), Files.readAllBytes(explicit.path()));
        try (var zip = new ZipFile(explicit.path().toFile())) {
            assertFalse(zip.stream().anyMatch(entry -> entry.getName().contains("boss")));
        }
    }

    @Test void suppliedPortraitChangesActualBossPixelsAndRecordsOriginalRights() throws Exception {
        var hud = hud();
        var portrait = portrait();
        var original = PackGenerator.generate(directory.resolve("old"), ImageGlyphs.empty(), hud);
        var changed = PackGenerator.generate(directory.resolve("new"), ImageGlyphs.empty(), hud, portrait);
        try (var oldZip = new ZipFile(original.path().toFile()); var zip = new ZipFile(changed.path().toFile())) {
            boolean pixelsChanged = false;
            for (var entry : zip.stream().filter(e -> e.getName().contains("boss") && e.getName().endsWith(".png")).toList()) {
                var actual = ImageIO.read(zip.getInputStream(entry));
                var baseline = ImageIO.read(oldZip.getInputStream(oldZip.getEntry(entry.getName())));
                assertNotNull(actual);
                assertEquals(baseline.getWidth(), actual.getWidth());
                for (int x = 0; x < actual.getWidth(); x++) for (int y = 0; y < actual.getHeight(); y++)
                    pixelsChanged |= actual.getRGB(x, y) != baseline.getRGB(x, y);
            }
            assertTrue(pixelsChanged, "Supplied portrait must change rendered boss PNG pixels");
            String provenance = new String(zip.getInputStream(zip.getEntry("ASSET_PROVENANCE.txt")).readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(provenance.contains(portrait.sha256()));
            assertTrue(provenance.contains("2x2"));
            assertTrue(provenance.contains("portrait retains"));
            assertFalse(provenance.contains("All included resources are available under the included MIT License."));
        }
    }

    private static HudAssets hud() {
        return HudAssets.create(List.of(new Screen("portrait-test", "BOSS", List.of())));
    }

    private static ImageAsset portrait() throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) image.setRGB(x, y, 0xff14da41);
        var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", bytes));
        return ImageAsset.fromPng(bytes.toByteArray());
    }
}
