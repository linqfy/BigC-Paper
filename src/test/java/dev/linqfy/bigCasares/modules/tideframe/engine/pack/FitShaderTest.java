package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.GlyphColor;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewportFit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class FitShaderTest {
    @Test void theScaleKeepsTheWholeSafeAreaOnScreenAtEveryCommonSizeAndGuiScale() {
        // {window width, window height} / GUI scale, as the client's projection reports them.
        double[][] guis = {{640, 360}, {480, 270}, {960, 540}, {683, 384}, {512, 288}, {320, 240}, {427, 240}, {800, 600}, {640, 480}, {1280, 360}};
        // The safe rectangle where the pack really draws it (2x frame, HudAssets.canvasTopGuiQuarters), scaled about the
        // screen centre like the shader: every edge on screen, and one edge exactly at the screen's edge.
        int top = HudAssets.canvasTopGuiQuarters(FitShader.REFERENCE_QUARTERS);
        for (double[] gui : guis) {
            double f = FitShader.scale(gui[0], gui[1]), cx = gui[0] / 2, cy = gui[1] / 2;
            double left = cx + (-256 + 2 * ViewportFit.SAFE_LEFT) * f, right = cx + (-256 + 2 * ViewportFit.SAFE_RIGHT) * f;
            double up = cy + (top + 2 * ViewportFit.SAFE_TOP) * f, down = cy + (top + 2 * ViewportFit.SAFE_BOTTOM) * f;
            String at = " at " + gui[0] + "x" + gui[1];
            assertTrue(left >= -1e-9 && right <= gui[0] + 1e-9, "safe area across on screen" + at);
            assertTrue(up >= -1e-9 && down <= gui[1] + 1e-9, "safe area down on screen" + at);
            assertTrue(Math.abs(left) < 1e-9 || Math.abs(down - gui[1]) < 1e-9, "and no smaller than needed" + at);
        }
        assertEquals(464, FitShader.SAFE_WIDTH); assertEquals(252, FitShader.SAFE_HEIGHT, "2 x 126: the frame sits 4 GUI units low");
        assertEquals(480 / 464.0, FitShader.scale(480, 270), 1e-12, "1080p at GUI scale 4 (Auto): the safe width just fits");
        assertEquals(640 / 464.0, FitShader.scale(640, 480), 1e-12, "4:3: width decides, the art leaves bars above and below");
        assertEquals(360 / 252.0, FitShader.scale(1280, 360), 1e-12, "ultrawide: height decides, the art overflows the sides");
        // 16:9 windows: the canvas covers the whole window (only its outer edge falls off).
        for (double[] gui : new double[][]{{480, 270}, {640, 360}, {960, 540}, {683, 384}}) {
            double f = FitShader.scale(gui[0], gui[1]);
            assertTrue(FitShader.REFERENCE_WIDTH * f >= gui[0] && FitShader.REFERENCE_HEIGHT * f >= gui[1], "covers " + gui[0] + "x" + gui[1]);
        }
        assertThrows(IllegalArgumentException.class, () -> FitShader.scale(0, 100));
        assertEquals(512, FitShader.REFERENCE_WIDTH); assertEquals(288, FitShader.REFERENCE_HEIGHT); assertEquals(8, FitShader.REFERENCE_QUARTERS);
    }

    @Test void theStageCarriesTheBlockAndTheMarkerTheJavaSideWrites() {
        String s = FitShader.source();
        assertTrue(s.contains("(tideframeRgb.r & 7) == " + GlyphColor.RED + " && (tideframeRgb.g & 7) == " + GlyphColor.GREEN + " && (tideframeRgb.b & 7) == " + GlyphColor.BLUE), s);
        assertTrue(s.contains("min(tideframeGui.x / 464.0, tideframeGui.y / 252.0)"));
        assertTrue(s.contains("gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);"));
        assertTrue(s.contains("#ifdef IS_GUI"), "only GUI text is ever scaled");
        assertEquals(count(s, '{'), count(s, '}'), "balanced braces");
        assertEquals(count(s, '('), count(s, ')'), "balanced parentheses");
        assertEquals(s.split("#if").length - 1, s.split("#endif").length - 1, "every #if is closed");
        assertTrue(s.startsWith("#version 330\n"));
        assertFalse(s.contains("%d") || s.contains("%s"), "no unformatted placeholder");
        assertTrue(s.contains("vertexColor = Color;") && s.contains("texCoord0 = UV0;"), "the varyings the game's fragment stage reads");
        // Minecraft 26.2's dialect: #moj_import and unlocated inputs. 26.3's (#include, layout locations) is not shipped.
        assertTrue(s.contains("#moj_import <minecraft:projection.glsl>") && !s.contains("#include") && !s.contains("layout(location") && !s.contains("#extension"));
    }

    @Test void theOverlayIsForMinecraft262OnlyAndUsesMinimumAndMaximumFormats() {
        String json = FitShader.overlaysJson();
        assertTrue(json.contains("\"min_format\": 88") && json.contains("\"max_format\": 88"), json);
        assertFalse(json.contains("\"formats\""), "the old key is rejected above pack format 64");
        assertFalse(json.contains("97") || json.contains("26_3"), "no shader for 26.3");
        assertEquals(java.util.Set.of("tideframe_fit_26_2/assets/minecraft/shaders/core/text.vsh"), FitShader.entries().keySet());
    }

    /**
     * On a PC with Minecraft 26.2 installed: our stage equals the game's own, line for line, except the replaced
     * position, the added block and the comment. Read for comparison only; nothing of the game is kept.
     */
    @Test void ourStageDiffersFromTheGamesOnlyByTheMarkedBlock() throws IOException {
        String appData = System.getenv("APPDATA");
        Assumptions.assumeTrue(appData != null, "no %APPDATA%");
        Path jar = Path.of(appData, ".minecraft", "versions", "26.2", "26.2.jar");
        Assumptions.assumeTrue(Files.isRegularFile(jar), "Minecraft 26.2 is not installed here");
        String vanilla;
        try (var zip = new ZipFile(jar.toFile()); var in = zip.getInputStream(zip.getEntry("assets/minecraft/shaders/core/text.vsh"))) {
            vanilla = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> ours = lines(FitShader.source());
        List<String> theirs = lines(vanilla);
        // Every line of the game's stage appears in ours, in order, except the one position line we replace.
        int at = 0;
        for (String line : theirs) {
            if (line.equals("gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);")) continue;
            int found = -1;
            for (int i = at; i < ours.size(); i++) if (ours.get(i).equals(line)) { found = i; break; }
            assertTrue(found >= 0, "missing or reordered: " + line);
            at = found + 1;
        }
        // And what we add is the comment, the marked block and the replaced position line: nothing else.
        List<String> added = new ArrayList<>(ours);
        for (String line : theirs) added.remove(line);
        for (String line : added)
            assertTrue(line.startsWith("//") || line.contains("tideframe") || line.equals("vec3 position = Position;") || line.equals("#ifdef IS_GUI") || line.equals("#endif")
                    || line.equals("}") || line.equals("gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);") || line.contains("position.xy"),
                    "unexpected added line: " + line);
    }

    private static List<String> lines(String source) {
        var out = new ArrayList<String>();
        for (String line : source.split("\n")) { String t = line.strip(); if (!t.isEmpty()) out.add(t); }
        return out;
    }
    private static int count(String text, char c) { int n = 0; for (char x : text.toCharArray()) if (x == c) n++; return n; }
}
