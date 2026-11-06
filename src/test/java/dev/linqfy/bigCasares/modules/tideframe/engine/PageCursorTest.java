package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Per-page cursor size (its own sprites) and speed (times the player's sensitivity). */
class PageCursorTest {
    private static Screen screen(String id, Double size, double speed) {
        return new Screen(id, "C", List.of(new Screen.Element("b", "B", 0, 0, .4, .2, "button", "")), RenderQuality.low(),
                new Screen.Presentation("menu", 1, "cross", size, speed));
    }
    @Test void cursorSettingsRoundTripAndValidate() {
        var page = screen("big", 1.2, 1.5);
        String json = ScreenCodec.encode(page);
        assertTrue(json.startsWith("{\"version\":9") && json.contains("\"cursorSize\":1.2") && json.contains("\"cursorSpeed\":1.5"), json);
        assertEquals(page, ScreenCodec.decode(json));
        assertFalse(ScreenCodec.encode(screen("plain", null, 1)).contains("cursorSize"));
        assertThrows(IllegalArgumentException.class, () -> screen("x", 2.0, 1));
        assertThrows(IllegalArgumentException.class, () -> screen("x", null, 9));
        String v6 = ScreenCodec.encode(screen("v", null, 1).withPresentation(new Screen.Presentation("menu", 2, "cross")));
        assertTrue(v6.startsWith("{\"version\":6"), v6.substring(0, 20));
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(v6.replace("\"cursor\":\"cross\"", "\"cursor\":\"cross\",\"cursorSpeed\":2")),
                "schema 6-8 presentation has no cursorSpeed");
    }
    @Test void speedMultipliesThePlayersSensitivityInsideTheLimits() {
        assertEquals(3, UiSession.pageSensitivity(2, screen("a", null, 1.5)), 1e-9);
        assertEquals(4, UiSession.pageSensitivity(4, screen("a", null, 4)), 1e-9, "capped");
        assertEquals(.25, UiSession.pageSensitivity(.25, screen("a", null, .25)), 1e-9, "floored");
    }
    @Test void aPageSizedCursorGetsItsOwnSprites() {
        var assets = HudAssets.create(List.of(screen("normal", null, 1), screen("large", 1.4, 1)));
        String small = assets.cursor(screen("normal", null, 1), 0, 0), large = assets.cursor(screen("large", 1.4, 1), 0, 0);
        assertNotEquals(small, large, "different glyphs for a different size");
        assertFalse(large.isEmpty());
    }
}
