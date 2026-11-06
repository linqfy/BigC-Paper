package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.GlyphColor;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewportFit;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The optional auto-fit shader (config {@code display.auto-fit}): the pack's own vertex stage for GUI text, which
 * scales glyphs carrying the {@link GlyphColor} marker about the screen centre so that the menu's safe area fits the
 * window at any size, proportion and GUI scale. The window size comes from the GUI projection matrix, in GUI units.
 *
 * <p><b>Minecraft 26.2 only</b> (pack format 88): it is delivered as a pack overlay for that format, so other game
 * versions (26.3 uses a different shader dialect) never load it. The interface (attributes, varyings, includes) is the
 * game's and cannot differ; the Tideframe block is the only addition. This stage is verified by reading and by a
 * line-by-line comparison with the game's own stage (see FitShaderTest), not by compiling: no OpenGL context is
 * available to the build.
 */
public final class FitShader {
    private FitShader() { }
    /** GUI units of the reference canvas the pack bakes in this mode: quarters 8 = 2x, 64 x 8 by 36 x 8. */
    public static final int REFERENCE_QUARTERS = 8, REFERENCE_WIDTH = 64 * REFERENCE_QUARTERS, REFERENCE_HEIGHT = 36 * REFERENCE_QUARTERS;
    /** The pack overlay directory and the pack format (major) of Minecraft 26.2. */
    static final String DIR_26_2 = "tideframe_fit_26_2";
    static final int FORMAT_26_2 = 88;
    static final String PATH = "/assets/minecraft/shaders/core/text.vsh";

    /**
     * GUI units the shader must keep on screen around its centre, at the reference size: twice the farthest the safe area
     * (where menus keep text and buttons, see {@link ViewportFit#SAFE_LEFT}) reaches from the screen centre. Across it is
     * centred: 232 of 256 canvas pixels, 464 units. Down it is not: the pack's frame puts the 2x canvas 4 units below
     * centre, so the safe rows 8 to 133 reach 124 units up and 126 down, and 2 x 126 = 252 (250, the safe height alone,
     * left its bottom row 1 unit off screen on windows limited by their height).
     */
    public static final int SAFE_WIDTH = REFERENCE_WIDTH * (ViewportFit.SAFE_RIGHT - ViewportFit.SAFE_LEFT) / 256,
            SAFE_HEIGHT = 2 * Math.max(-(HudAssets.canvasTopGuiQuarters(REFERENCE_QUARTERS) + ViewportFit.SAFE_TOP * REFERENCE_QUARTERS / 4),
                    HudAssets.canvasTopGuiQuarters(REFERENCE_QUARTERS) + ViewportFit.SAFE_BOTTOM * REFERENCE_QUARTERS / 4);

    /**
     * The factor the shader applies about the screen centre: the largest one that keeps the whole safe area on screen,
     * {@code min(guiWidth / 464, guiHeight / 252)}. On a 16:9 window the art fills the screen and only its outer edge falls
     * off; on any other proportion the text and buttons are all still visible (the art may leave bars). This is the same
     * rule as picking the largest menu size whose safe area is visible, without the steps. The GLSL block computes
     * exactly this from the GUI projection; this is its reference for tests and tools.
     */
    public static double scale(double guiWidth, double guiHeight) {
        if (!(guiWidth > 0) || !(guiHeight > 0)) throw new IllegalArgumentException("GUI size must be positive");
        return Math.min(guiWidth / SAFE_WIDTH, guiHeight / SAFE_HEIGHT);
    }

    /** The overlays section of pack.mcmeta: format 88 only (minimum and maximum; the pre-26 {@code formats} key is rejected above format 64). */
    static String overlaysJson() {
        return "\"overlays\": {\"entries\": [{\"directory\": \"" + DIR_26_2 + "\", \"min_format\": " + FORMAT_26_2 + ", \"max_format\": " + FORMAT_26_2 + "}]}";
    }

    /** The shader file by pack path. */
    static Map<String, byte[]> entries() {
        var out = new LinkedHashMap<String, byte[]>();
        out.put(DIR_26_2 + PATH, source().getBytes(StandardCharsets.UTF_8));
        return out;
    }

    /** The scaling block. */
    static String block() {
        return """
                    vec3 position = Position;
                #ifdef IS_GUI
                    // Tideframe auto-fit: glyphs carrying the colour marker (the three lowest bits of red, green and blue are %d, %d, %d)
                    // are scaled about the screen centre so the safe area (%d x %d GUI units) of the menu canvas just fits the window.
                    ivec3 tideframeRgb = ivec3(Color.rgb * 255.0 + 0.5);
                    if ((tideframeRgb.r & 7) == %d && (tideframeRgb.g & 7) == %d && (tideframeRgb.b & 7) == %d) {
                        vec2 tideframeGui = vec2(abs(2.0 / ProjMat[0][0]), abs(2.0 / ProjMat[1][1]));
                        float tideframeScale = min(tideframeGui.x / %d.0, tideframeGui.y / %d.0);
                        position.xy = tideframeGui * 0.5 + (position.xy - tideframeGui * 0.5) * tideframeScale;
                    }
                #endif
                """.formatted(GlyphColor.RED, GlyphColor.GREEN, GlyphColor.BLUE, SAFE_WIDTH, SAFE_HEIGHT,
                GlyphColor.RED, GlyphColor.GREEN, GlyphColor.BLUE, SAFE_WIDTH, SAFE_HEIGHT);
    }

    /** The vertex stage for Minecraft 26.2 ({@code #moj_import} dialect). */
    static String source() {
        return "#version 330\n"
                + "\n// Tideframe auto-fit text vertex stage for Minecraft 26.2 (see FitShader.java). Identical to the game's, plus the marked block below.\n\n"
                + "#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)\n"
                + "#moj_import <minecraft:fog.glsl>\n"
                + "#moj_import <minecraft:sample_lightmap.glsl>\n"
                + "#endif\n\n"
                + "#moj_import <minecraft:dynamictransforms.glsl>\n"
                + "#moj_import <minecraft:projection.glsl>\n\n"
                + "in vec3 Position;\n"
                + "in vec4 Color;\n"
                + "in vec2 UV0;\n"
                + "#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)\n"
                + "in ivec2 UV2;\n"
                + "#endif\n\n"
                + "#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)\n"
                + "uniform sampler2D Sampler2;\n"
                + "out float sphericalVertexDistance;\n"
                + "out float cylindricalVertexDistance;\n"
                + "#endif\n\n"
                + "out vec4 vertexColor;\n"
                + "out vec2 texCoord0;\n\n"
                + "void main() {\n" + block()
                + "    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);\n\n"
                + "#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)\n"
                + "    sphericalVertexDistance = fog_spherical_distance(Position);\n"
                + "    cylindricalVertexDistance = fog_cylindrical_distance(Position);\n"
                + "    vertexColor = Color * sample_lightmap(Sampler2, UV2);\n"
                + "#else\n"
                + "    vertexColor = Color;\n"
                + "#endif\n"
                + "    texCoord0 = UV0;\n"
                + "}\n";
    }
}
