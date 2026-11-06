package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Screens built in code: pixel coordinates, defaults, properties, logic and validation. */
class ScreenBuilderTest {
    @Test void buildsAValidatedScreenFromPixelCoordinates() {
        Screen screen = ScreenBuilder.create("daily_rewards")
                .title("Recompensas")
                .block("background", 660, 240, 600, 600, "101418")
                .text("title", "Recompensa diaria ★", 760, 280, 400, 48, "#ffd966")
                .button("claim", "Reclamar", 760, 700, 400, 80, "#2b6cb0", List.of(Map.of("type", "message", "value", "<green>¡Listo!")))
                .text("tip", "Vuelve mañana", 760, 800)
                .property("title", "style.textScale", 2)
                .property("tip", "style.textCase", "upper")
                .logic("claim", Map.of("tooltip", "tip", "keyframes", Map.of("trigger", "hover", "steps", List.of(Map.of("at", 0), Map.of("at", 150, "scale", 1.06)))))
                .screenLogic(Map.of("variables", Map.of("coins", "10"), "animation", Map.of("open", Map.of("effect", "slide", "from", "left", "duration-ms", 300))))
                .build();
        assertEquals("Recompensas", screen.title());
        var background = screen.element("background").orElseThrow();
        assertEquals("decoration", background.kind()); assertEquals("#101418", background.style().fillColor());
        assertEquals(0, background.x(), 1e-9); assertEquals(1 - 540.0 / 540, background.y(), 1e-9);
        assertEquals(600 / 960.0, background.width(), 1e-9); assertEquals(600 / 540.0, background.height(), 1e-9);
        var title = screen.element("title").orElseThrow();
        assertEquals("Recompensa diaria ★", title.text()); assertEquals("original", title.style().textCase()); assertEquals(2, title.style().textScale());
        assertEquals("upper", screen.element("tip").orElseThrow().style().textCase());
        var claim = screen.element("claim").orElseThrow();
        assertEquals("button", claim.kind()); assertEquals(1, claim.logic().actions().size()); assertEquals("tip", claim.logic().tooltip());
        assertEquals("hover", claim.logic().motion().trigger());
        assertEquals("slide", screen.logic().animation().open().effect());
        assertTrue(screen.element("title").orElseThrow().layer() < claim.layer(), "later elements draw on top");
        assertEquals(screen, ScreenCodec.decode(ScreenCodec.encode(screen)));
    }

    @Test void mistakesFailAtOnceWithAReason() {
        assertThrows(IllegalArgumentException.class, () -> ScreenBuilder.create("Bad Id"));
        var builder = ScreenBuilder.create("x").text("a", "A", 0, 0);
        assertThrows(IllegalArgumentException.class, () -> builder.text("a", "again", 0, 0), "duplicate id");
        assertThrows(IllegalArgumentException.class, () -> builder.property("missing", "text", "x"));
        assertThrows(IllegalArgumentException.class, () -> builder.property("a", "wobble", 1));
        assertThrows(IllegalArgumentException.class, () -> ScreenBuilder.create("y").block("b", 0, 0, 10, 10, "red"));
        var overlapping = ScreenBuilder.create("z")
                .button("one", "1", 0, 0, 200, 100, "#333333", List.of())
                .button("two", "2", 100, 50, 200, 100, "#333333", List.of());
        var error = assertThrows(IllegalArgumentException.class, overlapping::build);
        assertTrue(error.getMessage().contains("overlap"), error.getMessage());
        var badLogic = ScreenBuilder.create("w").text("t", "T", 0, 0).logic("t", Map.of("on-hover", List.of(Map.of("type", "message", "value", "x"))));
        assertThrows(IllegalArgumentException.class, badLogic::build, "hover actions need a button");
        var overlay = ScreenBuilder.create("hud").overlay().quality("low").text("t", "♥ 20", 0, 0).build();
        assertEquals("overlay", overlay.presentation().mode()); assertEquals("❤ 20", overlay.element("t").orElseThrow().text(), "look-alikes are normalized");
        assertEquals("{\"a\":[1,2.5,\"q\\\"\\n\",null,true]}", ScreenBuilder.json(new java.util.LinkedHashMap<>(Map.of("a", java.util.Arrays.asList(1, 2.5, "q\"\n", null, true)))));
    }
}
