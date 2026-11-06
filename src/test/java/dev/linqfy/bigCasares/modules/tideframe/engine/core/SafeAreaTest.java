package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.BossFightScreens;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.BossFightSettings;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.RuntimeDemoCatalog;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Menus fill the screen edge to edge, so every built-in text and button must stay inside the safe area. */
class SafeAreaTest {
    @Test void everyBuiltInTextAndButtonStaysInsideTheSafeArea() {
        var screens = new ArrayList<Screen>();
        screens.add(VisualMenuCatalog.screen());
        screens.addAll(RuntimeDemoCatalog.screens().values());
        screens.addAll(BossFightScreens.build(BossFightSettings.defaults()).values());
        screens.add(CloseControl.prepare(new Screen("plain", "PLAIN", java.util.List.of(), RenderQuality.low())));
        int checked = 0;
        for (var screen : screens) for (var e : screen.elements()) {
            double centerX = (e.x() + 1) * 128, centerY = (1 - e.y()) * 72;
            double left, right, top, bottom;
            if (e.kind().equals("button") || LiveText.isBar(e)) {
                left = centerX - e.width() * 64; right = centerX + e.width() * 64; top = centerY - e.height() * 36; bottom = centerY + e.height() * 36;
            } else if (e.kind().equals("label") && !e.text().isEmpty()) {
                int scale = e.style().textScale(), pad = e.style().outlineThickness() + (e.style().textShadow() ? scale : 0);
                String[] lines = e.text().split("\n");
                int longest = 0; for (String line : lines) longest = Math.max(longest, line.length());
                // Live text: assume the box is filled (worst case); static text: its real width.
                double textWidth = LiveText.isLive(e) ? Math.min(e.width() * 128, 16 * 6 * scale) : (longest * 6 - 1) * scale;
                double boxLeft = centerX - e.width() * 64;
                left = switch (e.style().textAlign()) { case "left" -> boxLeft; case "right" -> boxLeft + e.width() * 128 - textWidth; default -> centerX - textWidth / 2; };
                right = left + textWidth + pad;
                double blockHeight = lines.length * 8 * scale - scale;
                top = centerY - blockHeight / 2 - 1 - e.style().outlineThickness(); bottom = top + blockHeight + pad + e.style().outlineThickness();
            } else continue;
            String where = screen.id() + "/" + e.id();
            assertTrue(left >= ViewportFit.SAFE_LEFT - .5 && right <= ViewportFit.SAFE_RIGHT + .5, where + " x " + left + ".." + right);
            assertTrue(top >= ViewportFit.SAFE_TOP - .5 && bottom <= ViewportFit.SAFE_BOTTOM + .5, where + " y " + top + ".." + bottom);
            checked++;
        }
        assertTrue(checked > 300, "elements checked: " + checked);
    }
}
