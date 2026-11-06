package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudRaster;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeDemoCatalogTest {
    @Test void boundedCatalogHasImagesValidLinksNonoverlappingTargetsAndCloseButtons() throws Exception {
        var screens = RuntimeDemoCatalog.screens();
        assertEquals(53, screens.size());
        assertThrows(UnsupportedOperationException.class, () -> screens.clear());
        var menu = screens.get("image_demo08");
        assertNotNull(menu);
        var background = menu.elements().stream().filter(e -> e.id().equals("art")).findFirst().orElseThrow();
        assertEquals(2, background.width()); assertEquals(2, background.height());
        assertNotNull(background.image());
        assertEquals(1920, ImageIO.read(new ByteArrayInputStream(background.image().png())).getWidth(), "full HD photograph");
        for (var screen : screens.values()) {
            assertEquals(RenderQuality.low(), screen.quality());
            assertEquals(screen, ScreenCodec.decode(ScreenCodec.encode(screen)));
            assertDoesNotThrow(screen::validateActionAreas);
            assertTrue(screen.elements().size() <= 128);
            assertTrue(screen.id().length() <= 32);
            if (screen.id().equals("overlay_demo") || screen.presentation().mode().equals("overlay")) {
                assertTrue(screen.actionTargets().isEmpty(), "Passive HUD must never capture clicks");
            } else {
                assertTrue(screen.elements().stream().anyMatch(e -> e.id().equals("close_x") && e.text().equals("X") && e.action().equals("close")));
            }
            for (var element : screen.elements()) {
                if (element.action().startsWith("open:")) assertTrue(screens.containsKey(element.action().substring(5))
                        || element.action().equals("open:" + dev.linqfy.bigCasares.modules.tideframe.engine.core.VisualMenuCatalog.screen().id()), element.action());
                if (element.image() != null) assertNotNull(ImageIO.read(new ByteArrayInputStream(element.image().png())));
            }
        }
    }

    @Test void eachCorrectLetterFadesThenIsAbsentWhileRemainingLettersStillExist() {
        var screens = RuntimeDemoCatalog.screens();
        String[] order = {"a", "c", "b", "d"};
        for (int step = 0; step < order.length; step++) {
            double previousOpacity = 1;
            for (int phase = 1; phase <= 3; phase++) {
                var fade = screens.get("seq_f" + step + "_" + phase);
                String id = "letter_" + order[step];
                var letter = fade.elements().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
                assertTrue(letter.style().opacity() < previousOpacity);
                assertEquals("decoration", letter.kind());
                assertEquals("", letter.action());
                previousOpacity = letter.style().opacity();
            }
            var next = screens.get("seq_s" + (step + 1));
            String removed = "letter_" + order[step];
            assertTrue(next.elements().stream().noneMatch(e -> e.id().equals(removed)));
        }
        assertTrue(screens.get("seq_s4").elements().stream().noneMatch(e -> e.id().startsWith("letter_")));
    }

    @Test void entireCatalogBakesToKnownFramesWithinNativeAtlasAndPackLimits() {
        var screens = RuntimeDemoCatalog.screens();
        var combined = new ArrayList<>(screens.values());
        combined.addAll(Screen.demos().values());
        assertEquals(57, combined.size());
        var assets = HudAssets.create(combined);
        for (var screen : screens.values()) {
            assertFalse(assets.frame(screen, null, 8).isEmpty());
            for (var target : screen.actionTargets()) assertFalse(assets.frame(screen, target.id(), 8).isEmpty());
        }
        long bytes = assets.entries().values().stream().mapToLong(a -> a.length).sum();
        assertTrue(bytes < HudAssets.MAX_PACK_BYTES);
        var font = new String(assets.entries().get("assets/tideframe/font/hud.json"), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(font.contains("\"height\""));
    }

    @Test void wheelHas24DistinctOrientationsAndResultColorIsActuallyUnderThePointer() throws Exception {
        var screens = RuntimeDemoCatalog.screens();
        var images = new HashSet<String>();
        for (int angle = 0; angle < 24; angle++) {
            var wheel = screens.get(String.format(java.util.Locale.ROOT, "spin_%02d", angle)).elements().stream()
                    .filter(e -> e.id().equals("wheel")).findFirst().orElseThrow().image();
            assertTrue(images.add(wheel.sha256()), "Rotating15degrees must change the rendered wheel");
        }
        int[] colors = {0xef6676, 0x71d6ad, 0x78aaf6, 0xf6d478};
        for (var color : RouletteSpin.Color.values()) {
            var wheel = screens.get("win_" + color.name().toLowerCase(java.util.Locale.ROOT)).elements().stream()
                    .filter(e -> e.id().equals("wheel")).findFirst().orElseThrow().image();
            var image = ImageIO.read(new ByteArrayInputStream(wheel.png()));
            assertEquals(colors[color.ordinal()], image.getRGB(360, 150) & 0xffffff, // (120, 50) at 3x detail
                    "The declared result must match actual pixels below the fixed pointer");
        }
    }

    @Test void resultSelectionButtonsHaveReadableTextContrast() {
        for (var element : RuntimeDemoCatalog.screens().get("roulette_demo").elements()) {
            if (!element.id().startsWith("choose_")) continue;
            double background = luminance(element.style().fillColor());
            double text = luminance(element.style().textColor());
            double ratio = (Math.max(background, text) + .05) / (Math.min(background, text) + .05);
            assertTrue(ratio >= 4.5, element.id() + " contrast=" + ratio);
        }
    }

    @Test void passiveHudLabelsDoNotClipGlyphsComparedWithUnrestrictedLabelWidths() {
        var overlay = RuntimeDemoCatalog.screens().get("overlay_demo");
        for (var label : overlay.elements()) {
            if (!label.kind().equals("label") || dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.isBar(label)) continue;
            var expanded = new Screen.Element(label.id(), label.text(), label.x(), label.y(), 2, label.height(), label.kind(),
                    label.action(), label.shape(), label.border(), label.messageCondition(), label.background(), label.image(),
                    label.style(), label.layer(), label.actionArea());
            var actual = HudRaster.render(new Screen("actual", "ACTUAL", java.util.List.of(label), RenderQuality.low()), null, 8);
            var reference = HudRaster.render(new Screen("reference", "REFERENCE", java.util.List.of(expanded), RenderQuality.low()), null, 8);
            assertArrayEquals(reference.getRGB(0, 0, 512, 288, null, 0, 512), actual.getRGB(0, 0, 512, 288, null, 0, 512), label.id());
        }
    }

    @Test void resultTextAndReplayButtonDoNotCoverTheWheel() {
        var result = RuntimeDemoCatalog.screens().get("win_gold");
        var wheel = result.elements().stream().filter(e -> e.id().equals("wheel")).findFirst().orElseThrow();
        var status = result.elements().stream().filter(e -> e.id().equals("status")).findFirst().orElseThrow();
        var again = result.elements().stream().filter(e -> e.id().equals("again")).findFirst().orElseThrow();
        assertTrue(wheel.y() - wheel.height() / 2 > status.y() + status.height() / 2);
        assertTrue(status.y() - status.height() / 2 > again.y() + again.height() / 2);
    }

    private static double luminance(String color) {
        int rgb = Integer.parseInt(color.substring(1), 16);
        return .2126 * channel(rgb >>> 16 & 255) + .7152 * channel(rgb >>> 8 & 255) + .0722 * channel(rgb & 255);
    }

    private static double channel(int value) {
        double x = value / 255.0;
        return x <= .04045 ? x / 12.92 : Math.pow((x + .055) / 1.055, 2.4);
    }

    @Test void overlayHasLiveBarsAndMenusUseTheNewStyles() {
        var overlay = RuntimeDemoCatalog.screens().get("overlay_demo");
        // The passive HUD shows only the money: no health text, no health or XP bars.
        assertTrue(overlay.elements().stream().noneMatch(dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText::isBar));
        assertTrue(overlay.elements().stream().noneMatch(e -> e.text().contains("health")));
        assertTrue(overlay.elements().stream().anyMatch(e -> e.text().equals("{balance_short}") && e.style().textScale() == 2));
        assertTrue(overlay.elements().stream().anyMatch(e -> e.id().equals("coin") && e.image() != null));
        var heading = RuntimeDemoCatalog.screens().get("roulette_demo").elements().stream().filter(e -> e.id().equals("heading")).findFirst().orElseThrow();
        assertEquals(2, heading.style().textScale()); assertTrue(heading.style().textShadow());
        // Boss prompts: transparent overlays (no background art), white timer bar, black-on-white keycaps.
        for (String id : java.util.List.of(KeyChallenge.SCREEN, KeyChallenge.KEY_SCREEN, KeyChallenge.WIDE_SCREEN, KeyChallenge.WIN_SCREEN, KeyChallenge.LOSE_SCREEN)) {
            var screen = RuntimeDemoCatalog.screens().get(id);
            assertEquals("overlay", screen.presentation().mode(), id);
            assertTrue(screen.elements().stream().noneMatch(e -> e.image() != null || e.width() >= 2), id + " has no background");
        }
        var key = RuntimeDemoCatalog.screens().get(KeyChallenge.KEY_SCREEN);
        assertEquals("#ffffff", key.elements().stream().filter(e -> e.id().equals("timer")).findFirst().orElseThrow().style().textColor());
        assertEquals("#ffffff", key.elements().stream().filter(e -> e.id().equals("cap_face")).findFirst().orElseThrow().style().fillColor());
        assertEquals("#111111", key.elements().stream().filter(e -> e.id().equals("key")).findFirst().orElseThrow().style().textColor());
        assertTrue(RuntimeDemoCatalog.screens().values().stream().flatMap(s -> s.elements().stream())
                .filter(e -> e.kind().equals("button")).allMatch(e -> e.style().hoverEffect().equals("glow")));
    }
}
