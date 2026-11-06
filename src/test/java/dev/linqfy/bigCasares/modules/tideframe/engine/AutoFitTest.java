package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.GlyphColor;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewState;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.PackGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** display.auto-fit: the colour marker on every menu glyph, the reference size, the shader in the pack, and nothing changed when it is off. */
class AutoFitTest {
    private static Screen menu(int scale, String mode) {
        var live = new Screen.Element("hello", "Hola {player}", 0, .5, 1, .2, "label", "", "rectangle", false, null, false, null, Screen.Style.defaults(), 0, null);
        var panel = new Screen.Element("panel", "", 0, -.2, .6, .3, "label", "", "rectangle", false, null, true, null,
                new Screen.Style(1, "#335577", "#ffffff", "#ffffff", false), 1, null).withLogic(ActionParser.element(ScreenCodec.parse("{\"hidden\":true}")));
        var bar = new Screen.Element("life", "{bar:health}", 0, -.7, 1, .08, "label", "", "rounded", true, null, true, null,
                new Screen.Style(1, "#223344", "#ffffff", "#ff4455", false), 1, null);
        return new Screen("fit", "FIT", List.of(live, panel, bar), RenderQuality.low(), new Screen.Presentation(mode, scale, "cross"));
    }
    private static void colored(Component component, List<TextColor> out) {
        if (component.style().color() != null) out.add(component.style().color());
        for (var child : component.children()) colored(child, out);
    }

    @Test void menusAndHudPagesAtScaleOneAreFittedAndOnlyWhenAutoFitIsOn() {
        var on = HudAssets.create(List.of(menu(1, "menu")), List.of(4), HudAssets.DEFAULT_CURSOR_SCALE, Map.of(), Map.of(), true);
        var off = HudAssets.create(List.of(menu(1, "menu")), List.of(4), HudAssets.DEFAULT_CURSOR_SCALE, Map.of(), Map.of(), false);
        assertTrue(on.fitted(menu(1, "menu"))); assertTrue(on.fitted(menu(1, "overlay")), "a HUD page fits the window like a menu");
        assertFalse(on.fitted(menu(2, "menu"))); assertFalse(on.fitted(menu(2, "overlay")), "enlarged layouts stay as authored");
        assertFalse(off.fitted(menu(1, "menu"))); assertFalse(off.fitted(menu(1, "overlay"))); assertFalse(off.autoFit()); assertTrue(on.autoFit());
        assertTrue(on.sizes().contains(HudAssets.REFERENCE_QUARTERS), "the reference size is baked: " + on.sizes());
        assertFalse(off.sizes().contains(HudAssets.REFERENCE_QUARTERS));
        assertFalse(HudAssets.empty().autoFit());
    }

    @Test void everyColouredGlyphLayerOfAFittedMenuCarriesTheMarker() {
        var screen = menu(1, "menu");
        var assets = HudAssets.create(List.of(screen), List.of(4), HudAssets.DEFAULT_CURSOR_SCALE, Map.of(), Map.of(), true);
        int quarters = HudAssets.REFERENCE_QUARTERS;
        var view = ViewState.initial(screen, 0).visibility(List.of("panel"), List.of(), List.of());
        var colours = new ArrayList<TextColor>();
        colored(HudText.layer(assets, assets.frame(screen, null, 8, quarters), quarters, screen), colours);
        colored(DynamicLayer.build(assets, screen, view, null, 0, quarters), colours);
        colored(LiveLayer.build(assets, screen, Map.of("hello", "Hola Steve", "life", "0.7000|0.8500"), quarters, view), colours);
        assertTrue(colours.size() >= 4, "canvas, runtime layer, live text and bar: " + colours);
        for (var color : colours) assertTrue(GlyphColor.marked(color.value()), Integer.toHexString(color.value()));
        // A tint keeps its look (at most 7/255 per channel) and gains the marker.
        var tinted = HudText.paint(Component.text("x"), assets, screen, TextColor.fromHexString("#ff8800"));
        assertTrue(GlyphColor.marked(tinted.color().value()));
        assertTrue(Math.abs(((tinted.color().value() >> 8) & 255) - 0x88) <= 7);
    }

    @Test void withoutAutoFitTheComponentsAreExactlyWhatTheyWereBefore() {
        var screen = menu(1, "menu");
        var off = HudAssets.create(List.of(screen), List.of(4, 8), HudAssets.DEFAULT_CURSOR_SCALE, Map.of(), Map.of(), false);
        String glyphs = off.frame(screen, null, 8, 8);
        assertEquals(HudText.layer(off, glyphs, 8), HudText.layer(off, glyphs, 8, screen), "same layer, white, no marker");
        var plain = HudText.paint(Component.text("x"), off, screen, TextColor.fromHexString("#ff8800"));
        assertEquals(TextColor.fromHexString("#ff8800"), plain.color());
        assertEquals(NamedTextColor.WHITE, HudText.layer(off, glyphs, 8, screen).color());
        // An enlarged HUD page is never marked, even in an auto-fit pack.
        var overlay = menu(2, "overlay");
        var on = HudAssets.create(List.of(overlay), List.of(4), HudAssets.DEFAULT_CURSOR_SCALE, Map.of(), Map.of(), true);
        assertEquals(NamedTextColor.WHITE, HudText.layer(on, on.frame(overlay, null, 8, 4), 4, overlay).color());
    }

    @Test void theShadowSwitchIsOptionalAndNeverBreaksAComponent() {
        // The test classpath has an Adventure without text shadows (the build's API); the server of 26.x has them.
        var component = Component.text("x");
        if (!Shadow.available()) assertEquals(component, Shadow.off(component), "without the API the component is returned as it is");
        else assertNotEquals(component, Shadow.off(component), "with the API the shadow is switched off");
    }

    @Test void thePackCarriesTheShaderOnlyInAutoFitMode(@TempDir Path dir) throws Exception {
        var screen = menu(1, "menu");
        var on = HudAssets.create(List.of(screen), List.of(4), HudAssets.DEFAULT_CURSOR_SCALE, Map.of(), Map.of(), true);
        var off = HudAssets.create(List.of(screen), List.of(4), HudAssets.DEFAULT_CURSOR_SCALE, Map.of(), Map.of(), false);
        var fitted = PackGenerator.generate(dir.resolve("on"), dev.linqfy.bigCasares.modules.tideframe.engine.pack.ImageGlyphs.empty(), on);
        var plain = PackGenerator.generate(dir.resolve("off"), dev.linqfy.bigCasares.modules.tideframe.engine.pack.ImageGlyphs.empty(), off);
        try (var zip = new ZipFile(fitted.path().toFile())) {
            String mcmeta = new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes(), StandardCharsets.UTF_8);
            var json = dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec.parse(mcmeta);
            assertTrue(json instanceof Map<?, ?> m && m.containsKey("overlays") && m.containsKey("pack"), mcmeta);
            assertNotNull(zip.getEntry("tideframe_fit_26_2/assets/minecraft/shaders/core/text.vsh"));
            assertNull(zip.getEntry("tideframe_fit_26_3/assets/minecraft/shaders/core/text.vsh"), "no shader for 26.3");
            assertTrue(new String(zip.getInputStream(zip.getEntry("ASSET_PROVENANCE.txt")).readAllBytes(), StandardCharsets.UTF_8).contains("Auto-fit"));
        }
        try (var zip = new ZipFile(plain.path().toFile())) {
            String mcmeta = new String(zip.getInputStream(zip.getEntry("pack.mcmeta")).readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(mcmeta.contains("overlays"), "off: the metadata is what it always was");
            assertNull(zip.getEntry("tideframe_fit_26_2/assets/minecraft/shaders/core/text.vsh"));
            assertFalse(new String(zip.getInputStream(zip.getEntry("ASSET_PROVENANCE.txt")).readAllBytes(), StandardCharsets.UTF_8).contains("Auto-fit"));
        }
        assertFalse(java.util.Arrays.equals(fitted.sha256(), plain.sha256()));
    }
}
