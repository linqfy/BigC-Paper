package dev.linqfy.bigCasares.testsupport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a bundled BigCasares Tideframe screen (src/main/resources/tideframe/screens) promises its Java menu: the element
 * ids the menu handles, list templates repeating exactly the rows the menu sends, no Tideframe action except "close",
 * and only {bigc_...} texts the menu sets, none of them declared by the screen (a local variable would hide it).
 */
public final class TideframeScreenContract {
    private static final Pattern TOKEN = Pattern.compile("\\{(bigc_[a-z0-9_]+)}");
    private final JsonObject screen;

    private TideframeScreenContract(JsonObject screen) {
        this.screen = screen;
    }

    public static TideframeScreenContract load(String id) throws IOException {
        try (InputStream in = TideframeScreenContract.class.getClassLoader().getResourceAsStream("tideframe/screens/" + id + ".json")) {
            assertNotNull(in, "bundled screen " + id);
            JsonObject screen = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(9, screen.get("version").getAsInt(), id + " is a schema 9 page");
            assertEquals(id, screen.get("id").getAsString());
            assertEquals("menu", screen.getAsJsonObject("presentation").get("mode").getAsString());
            return new TideframeScreenContract(screen);
        }
    }

    private Map<String, JsonObject> elements() {
        Map<String, JsonObject> out = new TreeMap<>();
        for (JsonElement element : screen.getAsJsonArray("elements")) {
            JsonObject object = element.getAsJsonObject();
            out.put(object.get("id").getAsString(), object);
        }
        return out;
    }

    /**
     * The menu's buttons exist. The X and the {@code closing} buttons only close, with Tideframe's own action; nothing
     * else carries a Tideframe action.
     */
    public void buttons(Set<String> handled, Set<String> closing) {
        Map<String, JsonObject> elements = elements();
        for (String id : handled) {
            assertTrue(elements.containsKey(id), "element " + id);
            assertEquals("button", elements.get(id).get("kind").getAsString(), id + " is clickable");
        }
        JsonObject close = elements.get("cerrar");
        assertNotNull(close, "the close X");
        assertEquals("X", close.get("text").getAsString());
        assertEquals("close", close.get("action").getAsString(), "Tideframe's close: no other X is added over the panel");
        for (var entry : elements.entrySet()) {
            JsonObject element = entry.getValue();
            boolean closes = entry.getKey().equals("cerrar") || closing.contains(entry.getKey());
            assertEquals(closes ? "close" : "", element.get("action").getAsString(), entry.getKey() + (closes ? " only closes" : " has no Tideframe action"));
            JsonObject logic = element.has("logic") ? element.getAsJsonObject("logic") : new JsonObject();
            for (String key : Set.of("actions", "right-actions", "on-drop", "on-hover", "on-click")) assertFalse(logic.has(key), entry.getKey() + " has no " + key);
            if (logic.has("list")) assertFalse(logic.getAsJsonObject("list").has("on-select"), entry.getKey() + " has no on-select");
        }
    }

    /** Every list template repeats {@code source} exactly {@code rows} times; {@code templates} are those elements. */
    public void lists(String source, int rows, Set<String> templates) {
        Set<String> found = new TreeSet<>();
        for (var entry : elements().entrySet()) {
            JsonObject element = entry.getValue();
            if (!element.has("logic") || !element.getAsJsonObject("logic").has("list")) continue;
            JsonObject list = element.getAsJsonObject("logic").getAsJsonObject("list");
            assertEquals(source, list.get("source").getAsString(), entry.getKey());
            assertEquals(rows, list.get("rows").getAsInt(), entry.getKey() + " shows exactly the rows the menu sends");
            found.add(entry.getKey());
        }
        assertEquals(new TreeSet<>(templates), found);
    }

    /** The {bigc_...} texts (and visible-if conditions) are exactly variables the menu sets, and the page declares none. */
    public void texts(Set<String> setByTheMenu) {
        Set<String> used = new TreeSet<>();
        for (JsonObject element : elements().values()) {
            collect(element.get("text").getAsString(), used);
            if (element.has("logic") && element.getAsJsonObject("logic").has("visible-if"))
                collect(element.getAsJsonObject("logic").get("visible-if").getAsString(), used);
        }
        assertTrue(setByTheMenu.containsAll(used), "texts the menu never sets: " + new TreeSet<>(used) + " vs " + new TreeSet<>(setByTheMenu));
        if (screen.has("logic") && screen.getAsJsonObject("logic").has("variables")) {
            for (String name : screen.getAsJsonObject("logic").getAsJsonObject("variables").keySet())
                assertFalse(name.replaceFirst("^[*$]", "").startsWith("bigc_"), "declared " + name + " would hide BigCasares' value");
        }
    }

    private static void collect(String text, Set<String> into) {
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) into.add(matcher.group(1));
    }
}
