package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Generates original, font-independent bitmap resources. Never call from a region tick. */
public final class PackGenerator {
    private PackGenerator() { }

    public record Artifact(Path path, byte[] sha1, byte[] sha256) {
        public Artifact {
            path = Objects.requireNonNull(path).toAbsolutePath().normalize();
            if (sha1 == null || sha1.length != 20 || sha256 == null || sha256.length != 32)
                throw new IllegalArgumentException("Expected SHA-1 and SHA-256 digests");
            sha1 = sha1.clone(); sha256 = sha256.clone();
        }
        @Override public byte[] sha1() { return sha1.clone(); }
        @Override public byte[] sha256() { return sha256.clone(); }
    }

    // Handwritten 5-column, 7-row strokes. Data authored for Tideframe;
    // neither a system font nor another resource pack is read by this generator.
    private static final String[] LETTERS = {
            "00100/01010/10001/11111/10001/10001/10001", // A
            "11110/10001/10001/11110/10001/10001/11110", // B
            "01111/10000/10000/10000/10000/10000/01111", // C
            "11100/10010/10001/10001/10001/10010/11100", // D
            "11111/10000/10000/11110/10000/10000/11111", // E
            "11111/10000/10000/11110/10000/10000/10000", // F
            "01111/10000/10000/10111/10001/10001/01110", // G
            "10001/10001/10001/11111/10001/10001/10001", // H
            "11111/00100/00100/00100/00100/00100/11111", // I
            "00111/00010/00010/00010/10010/10010/01100", // J
            "10001/10010/10100/11000/10100/10010/10001", // K
            "10000/10000/10000/10000/10000/10000/11111", // L
            "10001/11011/10101/10001/10001/10001/10001", // M
            "10001/11001/11001/10101/10011/10011/10001", // N
            "01110/10001/10001/10001/10001/10001/01110", // O
            "11110/10001/10001/11110/10000/10000/10000", // P
            "01110/10001/10001/10001/10101/10010/01101", // Q
            "11110/10001/10001/11110/10100/10010/10001", // R
            "01111/10000/10000/01110/00001/00001/11110", // S
            "11111/00100/00100/00100/00100/00100/00100", // T
            "10001/10001/10001/10001/10001/10001/01110", // U
            "10001/10001/10001/10001/01010/01010/00100", // V
            "10001/10001/10001/10101/10101/11011/10001", // W
            "10001/01010/01010/00100/01010/01010/10001", // X
            "10001/10001/01010/00100/00100/00100/00100", // Y
            "11111/00001/00010/00100/01000/10000/11111"  // Z
    };
    private static final String[] DIGITS = {
            "01110/10001/10011/10101/11001/10001/01110",
            "00100/01100/00100/00100/00100/00100/01110",
            "01110/10001/00001/00010/00100/01000/11111",
            "11110/00001/00001/01110/00001/00001/11110",
            "00010/00110/01010/10010/11111/00010/00010",
            "11111/10000/10000/11110/00001/00001/11110",
            "01110/10000/10000/11110/10001/10001/01110",
            "11111/00001/00010/00100/00100/00100/00100",
            "01110/10001/10001/01110/10001/10001/01110",
            "01110/10001/10001/01111/00001/00001/01110"
    };

    public static Artifact generate(Path folder) throws IOException {
        return generate(folder, ImageGlyphs.empty());
    }

    /** Includes a fixed image registry; custom bytes keep the supplying operator's rights. */
    public static Artifact generate(Path folder, ImageGlyphs images) throws IOException {
        return generate(folder, images, HudAssets.empty());
    }

    /** Adds the worker-prebaked vanilla title HUD; legacy overloads retain their exact bytes. */
    public static Artifact generate(Path folder, ImageGlyphs images, HudAssets hud) throws IOException {
        return generate(folder, images, hud, null);
    }

    /** A supplied portrait is baked only into the boss artwork; its original rights remain intact. */
    public static Artifact generate(Path folder, ImageGlyphs images, HudAssets hud, ImageAsset bossPortrait) throws IOException {
        return generate(folder, images, hud, bossPortrait, List.of());
    }
    /** {@code sounds}: the operator's own sounds (plugins/BigCasares/tideframe/engine/sounds), as tideframe:NAME. */
    public static Artifact generate(Path folder, ImageGlyphs images, HudAssets hud, ImageAsset bossPortrait, List<CustomSounds.Sound> sounds) throws IOException {
        return generate(folder, images, hud, bossPortrait, sounds, "rose");
    }
    /** {@code bossStyle}: the boss bar frame design, one of {@link BossBarAssets#STYLES} (config.yml boss-bar.style). */
    public static Artifact generate(Path folder, ImageGlyphs images, HudAssets hud, ImageAsset bossPortrait, List<CustomSounds.Sound> sounds, String bossStyle) throws IOException {
        Objects.requireNonNull(images, "Image registry must be specified");
        Objects.requireNonNull(hud, "HUD registry must be specified");
        Files.createDirectories(folder);
        var entries = new LinkedHashMap<String, byte[]>();
        // With auto-fit the pack also carries the text shader, as an overlay per game version; otherwise these bytes never change.
        String overlays = hud.autoFit() ? ",\n                  " + FitShader.overlaysJson() : "";
        entries.put("pack.mcmeta", utf8("""
                {
                  "pack": {
                    "description": "Tideframe original UI resources",
                    "pack_format": 34,
                    "supported_formats": [34, 97],
                    "min_format": 34,
                    "max_format": [97, 1]
                  }%s
                }
                """.formatted(overlays)));
        if (hud.autoFit()) FitShader.entries().forEach(entries::put);
        entries.put("LICENSE.txt", utf8(LICENSE));
        String provenance = """
                Tideframe original asset inventory — copyright 2026 Tideframe contributors.
                All included resources are available under the included MIT License.
                atlas.png: handwritten 5-by-7 English ASCII bitmap strokes in PackGenerator.java.
                Lowercase slots repeat our own uppercase strokes. Space is a font advance.
                cursor.png: original 9-by-9 white cross with a cyan center, U+E000.
                panel.png: original solid 16-by-16 white square, U+E001.
                rounded.png: original 16-by-16 rounded geometric fill, radius 4, U+E002.
                ellipse.png: original 16-by-16 ellipse geometric fill, U+E003.
                border_rectangle.png, border_rounded.png, border_ellipse.png: original
                one-pixel outlines from fill minus eight-neighbor erosion, U+E004 through U+E006.
                ui.json: original bitmap font providers in namespace tideframe.
                No installed font, commercial plugin, vendor resource pack, copied icon,
                downloaded texture, core shader or Minecraft font was used to generate these assets.
                Metadata declares formats 34 through 97.1; client acceptance across that range
                has NOT been verified. A metadata range is not a compatibility test.
                """;
        var hudEntries = hud.entries();
        if (images.size() > 0) {
            provenance = provenance.replace("All included resources are available under the included MIT License.",
                    "Built-in resources are available under the included MIT License.\n"
                            + "Custom images retain their operator's or original rights holder's rights;\n"
                            + "the included MIT License does not automatically license custom image bytes.")
                    .replace("was used to generate these assets.", "was used to generate the built-in assets.");
            var inventory = new StringBuilder(provenance).append("\nOperator-supplied source image inventory (canonical RGBA PNG):\n");
            for (var image : images.images()) {
                inventory.append(image.sha256()).append(": ").append(image.width()).append("x").append(image.height());
                if(hudEntries.isEmpty())inventory.append(", U+").append(String.format(java.util.Locale.ROOT,"%04X",(int)images.glyph(image)));
                else inventory.append(", pixels baked into HUD tiles; no standalone image glyph");
                inventory.append('\n');
            }
            provenance = inventory.toString();
        }
        if (!hudEntries.isEmpty()) provenance = provenance.replace("All included resources are available under the included MIT License.",
                "Built-in resources are available under the included MIT License.\n"
                        + "Operator-supplied image pixels retain their original rights; the included MIT\n"
                        + "License does not automatically license custom pixels embedded in HUD canvases.")
                + "\nHUD canvases: original handwritten 5-by-7 strokes (ASCII, lower case, accented letters, symbols;\n"
                + "PackGenerator.java and Glyphs.java) and geometric masks rasterized\n"
                + "on per-screen transparent rasters (256-by-144 logical): Low512x288, Medium1920x1080, High2560x1440, Custom up to3840x2160; legacy1024x576.\n"
                + "These are source-raster dimensions, not a claim of physical on-screen pixel resolution; custom pixels retain supplying rights.\n"
                + "HUD tiles fit the vanilla 256-pixel font atlas, with transparent padding and per-row ascent.\n"
                + "HUD cursor: original white cross and cyan center, tiled at the same visual scale.\n"
                + "hud.json: own quarter-pixel space advances and zero-advance tiled title providers.\n"
                + "No world entity, installed font, copied shader or proprietary asset is required for this HUD.\n";
        if (!hudEntries.isEmpty() && bossPortrait != null)
            provenance = provenance.replace("was used to generate these assets.", "was used to generate the built-in assets.");
        if (hud.autoFit()) provenance += "\nAuto-fit (display.auto-fit): the overlay tideframe_fit_26_2 (Minecraft 26.2 only) holds Tideframe's own text vertex stage\n"
                + "(core/text.vsh), written against the game's shader interface. It includes the game's fog, lightmap, transform and projection\n"
                + "files by reference, copies none of them, and only adds a block that scales marked Tideframe glyphs to the window.\n";
        entries.put("ASSET_PROVENANCE.txt", utf8(provenance));
        entries.put("assets/tideframe/font/ui.json", utf8(fontJson(hudEntries.isEmpty()?images:ImageGlyphs.empty())));
        var atlas = new BufferedImage(96, 48, BufferedImage.TYPE_INT_ARGB);
        for (char code = 32; code <= 126; code++) {
            String[] rows = pattern(code).split("/");
            int baseX = ((code - 32) % 16) * 6, baseY = ((code - 32) / 16) * 8;
            for (int y = 0; y < 7; y++) for (int x = 0; x < 5; x++)
                if (rows[y].charAt(x) == '1') atlas.setRGB(baseX + x, baseY + y, 0xffffffff);
        }
        entries.put("assets/tideframe/textures/font/atlas.png", png(atlas));
        var cursor = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
        for (int i = 1; i < 8; i++) {
            cursor.setRGB(4, i, 0xffffffff); cursor.setRGB(i, 4, 0xffffffff);
        }
        cursor.setRGB(4, 4, 0xff61dfd1);
        entries.put("assets/tideframe/textures/font/cursor.png", png(cursor));
        var panel = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) panel.setRGB(x, y, 0xffffffff);
        entries.put("assets/tideframe/textures/font/panel.png", png(panel));
        entries.put("assets/tideframe/textures/font/rounded.png", png(maskImage(shapeMask("rounded"))));
        entries.put("assets/tideframe/textures/font/ellipse.png", png(maskImage(shapeMask("ellipse"))));
        for (String shape : new String[]{"rectangle", "rounded", "ellipse"})
            entries.put("assets/tideframe/textures/font/border_" + shape + ".png", png(maskImage(outline(shapeMask(shape)))));
        if(hudEntries.isEmpty())for (var image : images.images())
            entries.put("assets/tideframe/textures/font/images/" + image.sha256() + ".png", image.png());
        entries.putAll(hudEntries);
        if(!hudEntries.isEmpty()) {
            entries.putAll(BossBarAssets.create(bossPortrait, bossStyle).entries());
            String path="ASSET_PROVENANCE.txt";
            // The ornate bar is authored from geometry; no reference image pixels are included.
            if(entries.containsKey(path))entries.put(path,utf8(new String(entries.get(path),StandardCharsets.UTF_8)
                + (bossPortrait == null ? "\nBoss bar: original rose crystal fill, angular gold vines and crest. MIT.\n"
                : "\nBoss bar geometry: original rose crystal fill and angular gold vines. MIT.\n"
                  + "Operator-supplied boss portrait retains its operator's or original rights holder's rights;\n"
                  + "the MIT License does not automatically license the imported portrait.\n"
                  + "Boss portrait source (canonical RGBA PNG): " + bossPortrait.sha256() + ": "
                  + bossPortrait.width() + "x" + bossPortrait.height() + "; pixels baked into boss tiles.\n")));
        }
        if (!sounds.isEmpty()) {
            entries.putAll(CustomSounds.entries(sounds));
            var listed = new StringBuilder("\nCustom sounds (operator supplied; they retain their operator's or original rights holder's rights):\n");
            for (var sound : sounds) listed.append(sound.id()).append(": ").append(sound.ogg().length).append(" bytes, ").append(sound.channels() == 1 ? "mono" : sound.channels() + " channels").append('\n');
            String path = "ASSET_PROVENANCE.txt";
            entries.put(path, utf8(new String(entries.get(path), StandardCharsets.UTF_8) + listed));
        }
        if (storedZipBytes(entries) > HudAssets.MAX_PACK_BYTES)
            throw new IllegalArgumentException("Resource pack exceeds the 200 MiB limit");
        Path output = folder.resolve("tideframe.zip"), temporary = Files.createTempFile(folder, "tideframe-", ".tmp");
        try {
            try (var zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
                for (var entry : entries.entrySet()) {
                    var stored = new ZipEntry(entry.getKey());
                    stored.setTimeLocal(LocalDateTime.of(1980, 1, 1, 0, 0));
                    stored.setMethod(ZipEntry.STORED);
                    stored.setSize(entry.getValue().length);
                    var crc = new CRC32(); crc.update(entry.getValue()); stored.setCrc(crc.getValue());
                    zip.putNextEntry(stored); zip.write(entry.getValue()); zip.closeEntry();
                }
            }
            // Validate the actual archive before publication, independently of JDK ZIP header accounting.
            if (Files.size(temporary) > HudAssets.MAX_PACK_BYTES)
                throw new IOException("Resource pack exceeds the 200 MiB limit");
            try { Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        byte[] bytes = Files.readAllBytes(output);
        return new Artifact(output, digest("SHA-1", bytes), digest("SHA-256", bytes));
    }

    static long storedZipBytes(Map<String, byte[]> entries) {
        long bytes = 22;
        for (var entry : entries.entrySet())
            // 30 local + 46 central header bytes, plus the writer's two nine-byte timestamp extras.
            bytes += entry.getValue().length + 94L + entry.getKey().getBytes(StandardCharsets.UTF_8).length * 2L;
        return bytes;
    }

    /** 5x7 bitmap rows ("0"/"1", separated by "/") of the project font. */
    public static String pattern(char raw) {
        char code = Character.toUpperCase(raw);
        if (code >= 'A' && code <= 'Z') return LETTERS[code - 'A'];
        if (code >= '0' && code <= '9') return DIGITS[code - '0'];
        return switch (code) {
            case ' ' -> "00000/00000/00000/00000/00000/00000/00000";
            case '!' -> "00100/00100/00100/00100/00100/00000/00100";
            case '"' -> "01010/01010/01010/00000/00000/00000/00000";
            case '#' -> "01010/01010/11111/01010/11111/01010/01010";
            case '$' -> "00100/01111/10100/01110/00101/11110/00100";
            case '%' -> "11001/11010/00100/00100/01000/10110/00110";
            case '&' -> "01100/10010/10100/01000/10101/10010/01101";
            case '\'' -> "00100/00100/01000/00000/00000/00000/00000";
            case '(' -> "00010/00100/01000/01000/01000/00100/00010";
            case ')' -> "01000/00100/00010/00010/00010/00100/01000";
            case '*' -> "00000/10101/01110/11111/01110/10101/00000";
            case '+' -> "00000/00100/00100/11111/00100/00100/00000";
            case ',' -> "00000/00000/00000/00000/00000/00100/01000";
            case '-' -> "00000/00000/00000/11111/00000/00000/00000";
            case '.' -> "00000/00000/00000/00000/00000/00000/00100";
            case '/' -> "00001/00010/00010/00100/01000/01000/10000";
            case ':' -> "00000/00100/00100/00000/00100/00100/00000";
            case ';' -> "00000/00100/00100/00000/00100/00100/01000";
            case '<' -> "00001/00010/00100/01000/00100/00010/00001";
            case '=' -> "00000/00000/11111/00000/11111/00000/00000";
            case '>' -> "10000/01000/00100/00010/00100/01000/10000";
            case '?' -> "01110/10001/00001/00010/00100/00000/00100";
            case '@' -> "01110/10001/10111/10101/10111/10000/01110";
            case '[' -> "01110/01000/01000/01000/01000/01000/01110";
            case '\\' -> "10000/01000/01000/00100/00010/00010/00001";
            case ']' -> "01110/00010/00010/00010/00010/00010/01110";
            case '^' -> "00100/01010/10001/00000/00000/00000/00000";
            case '_' -> "00000/00000/00000/00000/00000/00000/11111";
            case '`' -> "01000/00100/00000/00000/00000/00000/00000";
            case '{' -> "00011/00100/00100/01000/00100/00100/00011";
            case '|' -> "00100/00100/00100/00100/00100/00100/00100";
            case '}' -> "11000/00100/00100/00010/00100/00100/11000";
            case '~' -> "00000/00000/01001/10110/00000/00000/00000";
            default -> throw new IllegalArgumentException("Unsupported ASCII glyph: " + (int) code);
        };
    }

    private static String fontJson(ImageGlyphs images) {
        var chars = new StringBuilder();
        for (int row = 0; row < 6; row++) {
            if (row != 0) chars.append(',');
            chars.append('"');
            for (int col = 0; col < 16; col++) {
                int code = 32 + row * 16 + col;
                chars.append(String.format(java.util.Locale.ROOT, "\\u%04x", code <= 126 ? code : 0));
            }
            chars.append('"');
        }
        String baseline = "{\"providers\":[{\"type\":\"space\",\"advances\":{\" \":4}},"
                + "{\"type\":\"bitmap\",\"file\":\"tideframe:font/atlas.png\",\"ascent\":7,\"height\":8,\"chars\":[" + chars + "]},"
                + "{\"type\":\"bitmap\",\"file\":\"tideframe:font/cursor.png\",\"ascent\":8,\"height\":9,\"chars\":[\"\\ue000\"]},"
                + shapeProvider("panel", ShapeGlyphs.forShape("rectangle").fill()) + ","
                + shapeProvider("rounded", ShapeGlyphs.forShape("rounded").fill()) + ","
                + shapeProvider("ellipse", ShapeGlyphs.forShape("ellipse").fill()) + ","
                + shapeProvider("border_rectangle", ShapeGlyphs.forShape("rectangle").outline()) + ","
                + shapeProvider("border_rounded", ShapeGlyphs.forShape("rounded").outline()) + ","
                + shapeProvider("border_ellipse", ShapeGlyphs.forShape("ellipse").outline());
        var providers = new StringBuilder(baseline);
        for (var image : images.images()) providers.append(',').append(shapeProvider("images/" + image.sha256(), images.glyph(image)));
        return providers.append("]}\n").toString();
    }

    private static String shapeProvider(String name, char glyph) {
        return "{\"type\":\"bitmap\",\"file\":\"tideframe:font/" + name + ".png\",\"ascent\":16,\"height\":16,\"chars\":[\""
                + String.format(java.util.Locale.ROOT, "\\u%04x", (int) glyph) + "\"]}";
    }

    /** Pixel centers define these original geometric masks; no font or path rasterizer is used. */
    private static boolean[][] shapeMask(String shape) {
        boolean[][] mask = new boolean[16][16];
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
            double px = x + .5, py = y + .5;
            mask[x][y] = switch (shape) {
                case "rectangle" -> true;
                case "rounded" -> {
                    double dx = Math.max(4 - px, Math.max(0, px - 12));
                    double dy = Math.max(4 - py, Math.max(0, py - 12));
                    yield dx * dx + dy * dy <= 16;
                }
                case "ellipse" -> (px - 8) * (px - 8) + (py - 8) * (py - 8) <= 64;
                default -> throw new IllegalArgumentException("Unsupported mask shape");
            };
        }
        return mask;
    }

    private static boolean[][] outline(boolean[][] fill) {
        boolean[][] border = new boolean[16][16];
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
            if (!fill[x][y]) continue;
            boolean eroded = true;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                int nx = x + dx, ny = y + dy;
                if (nx < 0 || nx >= 16 || ny < 0 || ny >= 16 || !fill[nx][ny]) eroded = false;
            }
            border[x][y] = !eroded;
        }
        return border;
    }

    private static BufferedImage maskImage(boolean[][] mask) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++)
            if (mask[x][y]) image.setRGB(x, y, 0xffffffff);
        return image;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        var bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", bytes)) throw new IOException("PNG encoder unavailable");
        return bytes.toByteArray();
    }
    private static byte[] utf8(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    static byte[] digest(String algorithm, byte[] bytes) {
        try { return MessageDigest.getInstance(algorithm).digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Usage: PackGenerator <output folder>");
        var artifact = generate(Path.of(args[0]));
        System.out.println("Generated " + artifact.path() + " SHA-256=" + java.util.HexFormat.of().formatHex(artifact.sha256()));
    }

    private static final String LICENSE = """
            MIT License

            Copyright (c) 2026 Tideframe contributors

            Permission is hereby granted, free of charge, to any person obtaining a copy
            of this software and associated documentation files (the "Software"), to deal
            in the Software without restriction, including without limitation the rights
            to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
            copies of the Software, and to permit persons to whom the Software is
            furnished to do so, subject to the following conditions:

            The above copyright notice and this permission notice shall be included in all
            copies or substantial portions of the Software.

            THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
            IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
            FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
            AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
            LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
            OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
            SOFTWARE.
            """;
}
