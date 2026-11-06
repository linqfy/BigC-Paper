package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.LogicDemo;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.MediaDemo;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MenuCatalogTest {
    @Test void everyMenuAppearsWithItsElementsOriginAndLogic() {
        var screens = new java.util.TreeMap<String, Screen>(Screen.demos());
        screens.put(LogicDemo.ID, LogicDemo.screen());
        screens.put(MediaDemo.ID, MediaDemo.screen());
        var text = MenuCatalog.render(screens, Set.of("menu"), "9.9.9");
        assertTrue(text.startsWith("# Menús de Tideframe 9.9.9"));
        for (var screen : screens.values()) {
            assertTrue(text.contains("## `" + screen.id() + "`"), "heading of " + screen.id());
            for (var element : screen.elements()) assertTrue(text.contains("| `" + element.id() + "` |"), screen.id() + "/" + element.id());
        }
        assertTrue(text.contains("| `menu` | TIDEFRAME | archivo |"), "a menu with a file says so");
        assertTrue(text.contains("| `profile` | PROFILE | incorporado |"), "a built-in one too");
        assertTrue(text.contains("no se crean ni se modifican desde el juego"), "the rule is written in the file");
        assertTrue(text.contains("imagen en hover") && text.contains("vidrio 6") && text.contains("sombra difuminada"), "0.22 features are described");
        assertTrue(text.contains("lógica `{"), "logic is shown");
    }

    @Test void tablesStayWellFormedWithPipesAndNewlinesInText() {
        var tricky = new Screen("tricky", "A|B", java.util.List.of(new Screen.Element("t", "uno|dos\ntres", 0, 0, .5, .2, "label", "")));
        var text = MenuCatalog.render(Map.of("tricky", tricky), Set.of(), "1");
        assertTrue(text.contains("| `tricky` | A¦B | incorporado | 1 |"));
        assertTrue(text.contains("uno¦dos ⏎ tres"));
        // 11 columns: 12 pipes, so 13 pieces around them. A stray pipe or line break in the text would change that.
        for (String line : text.split("\n")) if (line.startsWith("| `t` |")) assertEquals(13, line.split("\\|", -1).length, line);
    }

    @Test void numbersAreShortAndLocaleIndependent() {
        var screen = new Screen("n", "N", java.util.List.of(new Screen.Element("a", "", -0.0, .125, 1, .3333333, "label", "")));
        var text = MenuCatalog.render(Map.of("n", screen), Set.of(), "1");
        assertTrue(text.contains("| 0 | 0.125 | 1 | 0.333 |"), text);
    }

    @Test void theSameMenusAlwaysProduceTheSameText() {
        var a = MenuCatalog.render(Screen.demos(), Set.of(), "1");
        assertEquals(a, MenuCatalog.render(new java.util.HashMap<>(Screen.demos()), Set.of(), "1"));
    }
}
