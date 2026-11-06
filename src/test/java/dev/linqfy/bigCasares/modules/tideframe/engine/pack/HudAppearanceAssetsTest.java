package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HudAppearanceAssetsTest {
    private static Screen textButton(Screen.Style style) {
        return new Screen("hover", "HOVER", List.of(new Screen.Element("button", "A", 0, 0, .5, .5,
                "button", "close", "rectangle", false, null, false, null, style, 0, null)), RenderQuality.low());
    }
    private static Screen.Style style(String hoverColor, String hoverEffect) {
        return new Screen.Style(1, "#28384b", "#ffffff", "#ffffff", false,
                0, .08, 1, hoverColor, hoverEffect, "center", 0);
    }
    @Test void pureTextHoverColorIsBakedWithoutBackgroundOrBorder() {
        var screen = textButton(style("#00ff00", "none"));
        var assets = HudAssets.create(List.of(screen));
        assertNotEquals(assets.frame(screen, null, 8), assets.frame(screen, "button", 8),
                "The native pack must contain the visible custom text hover, not only the raster preview");
        assertEquals(0xff00ff00, HudRaster.render(screen, "button", 8).getRGB(256, 136));
    }
    @Test void textOnlyTranslateAndScaleHoverAreBaked() {
        for (String effect : List.of("translate", "scale")) {
            var screen = textButton(style(null, effect));
            var assets = HudAssets.create(List.of(screen));
            assertNotEquals(assets.frame(screen, null, 8), assets.frame(screen, "button", 8), effect);
        }
    }
    @Test void oldVersionFourTextAndImageWithoutVisibleHoverStillShareTheFinalSprite() throws Exception {
        var image = ImageAsset.fromPng(PngFixtures.rgba(2, 2, 0xff123456));
        var screen = new Screen("unchanged", "UNCHANGED", List.of(new Screen.Element("button", "A", 0, 0, .5, .5,
                "button", "close", "rectangle", false, null, false, image)), RenderQuality.low());
        var assets = HudAssets.create(List.of(screen));
        assertEquals(assets.frame(screen, null, 8), assets.frame(screen, "button", 8));
        assertArrayEquals(HudRaster.render(screen, null, 8).getRGB(0, 0, 512, 288, null, 0, 512),
                HudRaster.render(screen, "button", 8).getRGB(0, 0, 512, 288, null, 0, 512));
    }
}
