package dev.linqfy.bigCasares.modules.tideframe.engine.edit;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.UiAction;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class UltimateUiImporterTest {
    private static final UltimateUiImporter.Sources NONE = new UltimateUiImporter.Sources() {
        public byte[] image(String name) { return null; }
        public String component(String name) { return null; }
    };
    private static final String PAGE = """
        name: shop
        command: shop
        behavior:
          keep-open: false
          reopen: true
          close-on-damage: true
        animation:
          open:
            effect: uui_animation
        variables:
          page: 1
          '*coins': 100
          nickname:
        functions:
          buy:
            params:
              item: DIAMOND
              price: 100
            actions:
              - type: condition
                value: '{coins} >= {price}'
                then:
                  actions:
                    - type: variable
                      variable: coins
                      operation: subtract
                      value: '{price}'
                    - type: console
                      value: 'give %player% {item} 1'
                else:
                  actions:
                    - type: message
                      value: '<red>Not enough coins.'
          empty:
            actions: []
        keybinds:
          - key: Q
            actions:
              - type: message
                value: '&aYou pressed &e&lQ'
          - key: SHIFT_L
            actions:
              - type: redirect
                value: example_menu
        blocks:
          - type: text
            id: Balance-Text
            text: '&6Coins: {coins}'
            position: {x: 760, y: 100}
            size: {width: 400, height: 60}
          - type: block
            id: buy_button
            color: 1e1e1e
            rounding: large
            position: {x: 760, y: 300}
            size: {width: 400, height: 100}
            actions:
              - type: function
                function: buy
                arguments: {item: NETHERITE_INGOT, price: 500}
              - type: hide
                value: Balance-Text
              - type: mesage
                value: typo
          - type: input
            id: nickname
            color: 1e1e1e
            title: Enter your nickname
            placeholder: Click to type...
            variable: nickname
            max-length: 16
            filter: any
            position: {x: 760, y: 500}
            size: {width: 400, height: 48}
            on-submit:
              - type: message
                value: '<green>Your nickname is now {nickname}'
          - type: block
            id: vip_badge
            color: ffd700
            visibility:
              visible: true
              placeholder: '%player_health%=20'
            position: {x: 1700, y: 20}
            size: {width: 200, height: 60}
          - type: block
            id: backpack
            color: 1b1f24
            position: {x: 100, y: 600}
            size: {width: 500, height: 200}
            listLayout:
              direction: horizontal
              wraps: true
              spacing: 8
              padding: 16
            children:
              - {type: item, id: sword, item: DIAMOND_SWORD, draggable: true, size: {width: 64, height: 64}, layoutOrder: 2}
              - {type: item, id: bow, item: BOW, draggable: true, size: {width: 64, height: 64}, layoutOrder: 1}
          - type: image
            id: animated_img
            frames: animations/example
            interval-ms: 100
            animation-loop: false
            position: {x: 1500, y: 800}
            size: {width: 200, height: 200}
          - { component: badge, params: { label: "VIP" } }
        """;

    @Test void convertsAPageWithLogicLayoutAndWarnings() {
        var sources = new UltimateUiImporter.Sources() {
            public byte[] image(String name) { return null; }
            public String component(String name) {
                return name.equals("badge") ? """
                    params:
                      label: "Default"
                      count: 2
                    blocks:
                      - type: text
                        id: badge_${label}
                        text: '${label} badge'
                        position: {x: 0, y: 0}
                        size: {width: 125, height: 40}
                      - loop: "{count}"
                        block:
                          type: block
                          color: ffffff
                          position:
                            x: 50 + (100 * {val})
                            y: 1000
                          size: {width: 25, height: 25}
                    """ : null;
            }
        };
        var result = UltimateUiImporter.convert(PAGE, "shop.yml", sources);
        var screen = result.screen();
        assertEquals("shop", screen.id()); assertEquals("SHOP", screen.title());
        assertEquals("shop", screen.logic().command());
        assertTrue(screen.logic().behavior().reopen()); assertTrue(screen.logic().behavior().closeOnDamage());
        assertEquals(Map.of("page", "1", "*coins", "100", "nickname", "0"), screen.logic().variables());
        assertEquals(java.util.Set.of("buy"), screen.logic().functions().keySet(), "a function without actions is not registered");
        assertEquals(java.util.Set.of("Q", "SHIFT_L"), screen.logic().keybinds().keySet());

        var balance = screen.element("balance-text").orElseThrow();
        assertEquals("Coins: {coins}", balance.text()); assertEquals("#ffaa00", balance.style().textColor());
        assertEquals("original", balance.style().textCase(), "Ultimate UI draws text as written");
        assertEquals(0, balance.x(), 1e-9); assertEquals(1 - 130.0 / 1080 * 2, balance.y(), 1e-9);
        assertEquals(400.0 / 1920 * 2, balance.width(), 1e-9);

        var buy = screen.element("buy_button").orElseThrow();
        assertEquals("button", buy.kind()); assertEquals("rounded", buy.shape());
        var actions = buy.logic().actions();
        assertEquals(new UiAction.Call("buy", Map.of("item", "NETHERITE_INGOT", "price", "500")), actions.get(0));
        assertEquals(new UiAction.Visibility(List.of(), List.of("balance-text"), List.of()), actions.get(1), "renamed ids are rewritten");
        assertEquals(new UiAction.Command("typo"), actions.get(2), "unknown types run as commands, like in Ultimate UI");

        var nickname = screen.element("nickname").orElseThrow();
        assertEquals("{nickname}", nickname.text());
        var input = (UiAction.Input) nickname.logic().actions().get(0);
        assertEquals("nickname", input.variable()); assertEquals(16, input.maxLength()); assertEquals("Enter your nickname", input.title());
        assertEquals(1, input.onSubmit().size());

        assertEquals("%player_health%=20", screen.element("vip_badge").orElseThrow().logic().visibleIf());
        var bow = screen.element("bow").orElseThrow(); var sword = screen.element("sword").orElseThrow();
        assertTrue(bow.x() < sword.x(), "layout_order puts the bow first");
        assertTrue(sword.logic().draggable()); assertEquals("BOW", bow.text());
        var animated = screen.element("animated_img").orElseThrow().logic().frames();
        assertEquals("animations/example", animated.folder()); assertEquals(100, animated.intervalMs()); assertFalse(animated.loop());
        assertEquals(Map.of("animations/example", "animations/example"), result.frameFolders());
        assertTrue(screen.element("badge_vip").isPresent(), "component params are substituted");
        assertEquals("VIP badge", screen.element("badge_vip").orElseThrow().text());
        assertEquals(2, screen.elements().stream().filter(e -> e.kind().equals("decoration") && e.id().matches("e\\d+")).count(), "loop blocks expand");
        assertEquals("decoration", screen.element("backpack").orElseThrow().kind(), "a coloured box without text is a decoration");

        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("mesage")), result.warnings().toString());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("item icons")), result.warnings().toString());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("animation")), result.warnings().toString());
        assertEquals(screen, ScreenCodec.decode(ScreenCodec.encode(screen)), "the result is a valid schema 9 screen");
    }
    @Test void nestedLoopsStopAtTheExpansionCeilingInsteadOfFillingMemory() {
        // Four loops of 128 would be 268 million blocks; the second page's blocks produce nothing (a case without branch).
        for (var leaf : List.of(List.of("type: block", "position: {x: 0, y: 0}", "size: {width: 25, height: 25}"), List.of("case: nothing"))) {
            var yaml = new StringBuilder("name: many\nblocks:\n  - ");
            String indent = "    ";
            for (int level = 0; level < 4; level++) { yaml.append("loop: 128\n").append(indent).append("block:\n"); indent += "  "; yaml.append(indent); }
            yaml.append(String.join("\n" + indent, leaf)).append('\n');
            long start = System.nanoTime();
            var result = UltimateUiImporter.convert(yaml.toString(), "many.yml", NONE);
            assertTrue(System.nanoTime() - start < 10_000_000_000L, "finishes in seconds, not hours");
            assertTrue(result.screen().elements().size() <= 128);
            assertTrue(result.warnings().stream().anyMatch(w -> w.contains("too many blocks")), result.warnings().toString());
        }
    }
    @Test void textColoursAndExpressionsAreParsed() {
        assertArrayEquals(new String[]{"Welcome Steve!", "#33ff3d"}, UltimateUiImporter.plainText("<#33ff3d>Welcome Steve!</#5ea9ff>"));
        assertArrayEquals(new String[]{"Hi ★ ¿qué tal? ❤", null}, UltimateUiImporter.plainText("Hi ★ ¿qué tal? ♥\ufe0f"), "accents, symbols and look-alikes are kept");
        assertArrayEquals(new String[]{"Hi ? ?", null}, UltimateUiImporter.plainText("Hi \u2603 \ud83d\ude00"), "one ? per missing character, emoji included");
        assertEquals(350, UltimateUiImporter.evaluate("50 + (100 * 3)", 0), 1e-9);
        assertEquals(7, UltimateUiImporter.evaluate("bad +", 7), 1e-9);
        assertEquals(-4, UltimateUiImporter.evaluate("-(2*2)", 0), 1e-9);
    }
    @Test void effectsBecomeSlidesFadesAndHoverKeyframes() {
        var sources = new UltimateUiImporter.Sources() {
            public byte[] image(String name) { return null; }
            public String component(String name) { return null; }
            public String effect(String name) {
                return switch (name) {
                    case "slide_in" -> "id: slide_in\ntype: move\nstart-x: -480\nmove-x: 0\nduration-ms: 450\ninterpolation: ease-out\nclose-reversed: true\n";
                    case "rise" -> "id: rise\ntype: move\nstart-y: 200\nduration-ms: 300\n";
                    case "scale_animation" -> "id: scale_animation\ntype: scale\namount-percent: 5\nscale-x: 5%\nscale-y: 5%\nmove-y: -20\nduration-ms: 250\ninterpolation: ease-in-out\n";
                    default -> null;
                };
            }
        };
        var result = UltimateUiImporter.convert("""
            name: fx
            animation:
              open:
                effect: slide_in
            blocks:
              - {type: block, id: card, color: 202020, position: {x: 760, y: 400}, size: {width: 400, height: 200}, hover: {effect: scale_animation}}
            """, "fx.yml", sources);
        var animation = result.screen().logic().animation();
        assertEquals(new dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.Transition("slide", 450, "ease-out", "left", 64), animation.open());
        assertEquals(new dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.Transition("slide", 450, "ease-in", "left", 64), animation.close(),
                "close-reversed after a slide in slides back out to the left, with the mirrored easing");
        var card = result.screen().element("card").orElseThrow();
        assertEquals("button", card.kind(), "hover needs a hit target");
        var motion = card.logic().motion();
        assertEquals("hover", motion.trigger()); assertEquals("ease-in-out", motion.easing());
        var end = motion.at(250);
        assertEquals(-20 * 144 / 1080.0, end.y(), 1e-6); assertEquals(1.05, end.scale(), 1e-9); assertEquals(0, end.x()); assertEquals(1, end.opacity());
        var rise = UltimateUiImporter.convert("name: r\nanimation:\n  open:\n    effect: rise\n  close:\n    effect: missing\nblocks: []\n", "r.yml", sources);
        assertEquals("fade", rise.screen().logic().animation().open().effect());
        assertTrue(rise.warnings().stream().anyMatch(w -> w.contains("fades in instead")), rise.warnings().toString());
        assertTrue(rise.warnings().stream().anyMatch(w -> w.contains("missing")), rise.warnings().toString());
        assertEquals(result.screen(), ScreenCodec.decode(ScreenCodec.encode(result.screen())));
    }
    @Test void missingComponentsAndImagesOnlyWarn() {
        var result = UltimateUiImporter.convert("""
            name: Mini Page!
            blocks:
              - {component: nothing}
              - {type: image, id: logo, image: logo, position: {x: 0, y: 0}, size: {width: 100, height: 100}}
              - {type: block, id: far, position: {x: 5000, y: 0}, size: {width: 10, height: 10}}
            """, "mini.yml", NONE);
        assertEquals("mini_page", result.screen().id());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("component nothing")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("image logo")));
        assertTrue(result.screen().element("far").isEmpty());
    }
}
