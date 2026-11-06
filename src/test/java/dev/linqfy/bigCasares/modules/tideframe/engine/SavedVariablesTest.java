package dev.linqfy.bigCasares.modules.tideframe.engine;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** config.yml variables.saved: which globals are kept, and how they live in the player's data. */
class SavedVariablesTest {
    /** A player whose persistent data container is a plain map (only get, set and has are used). */
    private static Player player(Map<NamespacedKey, Object> data) {
        var container = (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(), new Class<?>[]{PersistentDataContainer.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "get" -> data.get((NamespacedKey) args[0]);
                case "set" -> { data.put((NamespacedKey) args[0], args[2]); yield null; }
                case "has" -> data.containsKey((NamespacedKey) args[0]);
                default -> null;
            });
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> method.getName().equals("getPersistentDataContainer") ? container : null);
    }

    @Test void onlyTheConfiguredGlobalsAreKeptAndTheyComeBackInANewSession() {
        var data = new HashMap<NamespacedKey, Object>();
        var saved = new SavedVariables(List.of("coins", "*level", "bad name"));
        assertTrue(saved.keeps("coins") && saved.keeps("level") && !saved.keeps("temp"));
        var session = new LinkedHashMap<>(Map.of("coins", "120", "level", "3", "temp", "x"));
        saved.store(player(data), session);
        var next = new LinkedHashMap<String, String>();
        saved.load(player(data), next);
        assertEquals(Map.of("coins", "120", "level", "3"), next, "temp was not saved");
        assertEquals(java.util.Set.of("bad name"), SavedVariables.invalid(List.of("coins", "*", "bad name", "$ok")));
    }

    @Test void storingKeepsValuesTheSessionNeverLoadedAndStarSavesEverything() {
        var data = new HashMap<NamespacedKey, Object>();
        var saved = new SavedVariables(List.of("coins", "level"));
        saved.store(player(data), Map.of("coins", "5", "level", "9"));
        saved.store(player(data), Map.of("coins", "7"));
        var back = new LinkedHashMap<String, String>(); saved.load(player(data), back);
        assertEquals(Map.of("coins", "7", "level", "9"), back, "level stays although this session never had it");
        var all = new SavedVariables(List.of("*"));
        all.store(player(data), Map.of("anything", "yes"));
        var everything = new LinkedHashMap<String, String>(); all.load(player(data), everything);
        assertEquals("yes", everything.get("anything"));
        assertFalse(SavedVariables.NONE.enabled());
        SavedVariables.NONE.store(player(data), Map.of("coins", "999"));
        var unchanged = new LinkedHashMap<String, String>(); saved.load(player(data), unchanged);
        assertEquals("7", unchanged.get("coins"), "with nothing configured nothing is written");
    }

    @Test void theStoredTextRoundTripsAndBrokenDataIsIgnored() {
        var values = new LinkedHashMap<String, String>(); values.put("quote", "say \"hi\" \\ ok"); values.put("lines", "a\nb");
        assertEquals(values, SavedVariables.decode(SavedVariables.encode(values)));
        assertEquals(Map.of(), SavedVariables.decode("{not json"));
        assertEquals(Map.of(), SavedVariables.decode("[1,2]"));
        assertEquals(Map.of("ok", "1"), SavedVariables.decode("{\"ok\":\"1\",\"Bad\":\"2\",\"n\":3}"), "only valid names with text values");
    }
}
