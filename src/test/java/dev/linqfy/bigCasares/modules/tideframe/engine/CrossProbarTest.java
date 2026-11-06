package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewState;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.UiAction;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Probar = plugin (idea 27): the shared cases of src/test/resources/cross run through the plugin's ScreenRuntime here
 * and through the editor's Probar in editor/cross.test.mjs; both must give exactly the expected events, variables and
 * pages. The host below does what LogicController does around a runtime: page changes, history, shared globals and
 * cooldowns, and the built-in values Probar uses as samples.
 */
class CrossProbarTest {
    /** Probar's SAMPLES: the simulated player's built-in {tokens}. */
    private static final Map<String, String> SAMPLES = Map.ofEntries(Map.entry("player", "Steve"), Map.entry("health", "18"), Map.entry("max_health", "20"),
            Map.entry("food", "17"), Map.entry("level", "12"), Map.entry("xp", "45%"), Map.entry("balance", "1250"), Map.entry("balance_short", "1250"),
            Map.entry("online", "7"), Map.entry("max_players", "20"), Map.entry("ping", "42"), Map.entry("x", "120"), Map.entry("y", "64"),
            Map.entry("z", "-35"), Map.entry("time", "12:30"));
    private static final Pattern BUILT_IN = Pattern.compile("\\{([a-z_]{1,24})}");

    /** One player's run: the open page's runtime, as LogicController keeps it. */
    private static final class Run {
        final Map<String, Screen> pages = new LinkedHashMap<>();
        final List<String> events = new ArrayList<>();
        final Map<String, String> globals = new LinkedHashMap<>();
        final Map<String, Long> cooldowns = new HashMap<>();
        final Deque<String> history = new ArrayDeque<>();
        final Deque<Runnable> later = new ArrayDeque<>();
        final Deque<String> answers = new ArrayDeque<>();
        String world = "world";
        long clock;
        boolean closed;
        ScreenRuntime runtime;
        ViewState view;

        void open(String id) {
            var screen = pages.get(id);
            assertNotNull(screen, "page " + id);
            view = ViewState.initial(screen, clock);
            runtime = new ScreenRuntime(screen, globals, host(screen));
            runtime.open();
        }
        /** Menu to menu, as TideframeEngine.open: the old page goes (on-close), the history remembers it, the new one opens. */
        void navigate(String target, boolean back) {
            String from = runtime.screen().id();
            runtime.shutdown();
            if (!back && !target.equals(from)) history.push(from);
            open(target);
        }
        void close() { closed = true; runtime.shutdown(); }

        ScreenRuntime.Host host(Screen screen) {
            return new ScreenRuntime.Host() {
                boolean mine() { return runtime != null && runtime.screen() == screen && !closed; }
                public void command(String line) { events.add("command " + line); }
                public void console(String line) { events.add("console " + line); }
                public void message(String text) { events.add("message " + text); }
                public void actionBar(String text) { events.add("actionbar " + text); }
                public void sound(String sound, float volume, float pitch) { events.add("sound " + sound); }
                public void teleport(String world, String x, String y, String z, String yaw, String pitch) { events.add("teleport " + world); }
                public void give(String item, int amount) { events.add("give " + item + " " + amount); }
                public void open(String target) { events.add("open " + target); if (mine()) navigate(target, false); }
                public void back() {
                    events.add("back");
                    if (!mine()) return;
                    if (history.isEmpty()) close(); else navigate(history.pop(), true);
                }
                public void close() { events.add("close"); if (mine()) Run.this.close(); }
                public void legacy(String action) { events.add("legacy " + action); }
                public boolean hasPermission(String node) { return true; }
                public boolean charge(BigDecimal amount) { events.add("charge " + amount.toPlainString()); return true; }
                public boolean pay(BigDecimal amount) { events.add("pay " + amount.toPlainString()); return true; }
                public void connect(String server) { events.add("server " + server); }
                public void input(UiAction.Input input, Consumer<String> submit, Runnable cancel) { events.add("input " + input.variable()); cancel.run(); }
                public void later(int ticks, Runnable task) { later.add(task); }
                public void warn(String message) { }
                public String placeholders(String text) {
                    Matcher m = BUILT_IN.matcher(text);
                    var out = new StringBuilder();
                    while (m.find()) {
                        String name = m.group(1), value = name.equals("world") ? world : SAMPLES.get(name);
                        m.appendReplacement(out, Matcher.quoteReplacement(value == null ? m.group() : value));
                    }
                    return m.appendTail(out).toString();
                }
                public void view(UnaryOperator<ViewState> change) { if (runtime != null && runtime.screen() == screen) view = change.apply(view); }
                public ViewState view() { return runtime != null && runtime.screen() == screen ? view : ViewState.EMPTY; }
                public void refreshLive() { }
                public List<Map<String, String>> list(String source) { return null; }
                public boolean dropped(String dragged, String target) { return true; }
                public long now() { return clock; }
                public Map<String, Long> cooldowns() { return cooldowns; }
                public void confirm(UiAction.Confirm confirm, Runnable yes, Runnable no) {
                    events.add("confirm " + confirm.message());
                    if ("yes".equals(answers.poll())) yes.run(); else no.run();
                }
                public int frameCount(String element) { return 0; }
                public int frameInterval(String element) { return 0; }
                public void variableChanged(String name, String before, String after) { }
                public void hud(String screen, boolean add) { events.add("hud " + (add ? "add " : "remove ") + screen); }
                public void toast(String screen, double seconds, Map<String, String> values) { events.add("toast " + screen); }
            };
        }
    }

    @TestFactory Stream<DynamicTest> theSharedCasesPlayLikeProbar() throws IOException {
        var names = read("index.txt").lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        assertTrue(names.size() >= 10);
        return names.stream().map(name -> DynamicTest.dynamicTest(name, () -> play(name)));
    }

    @SuppressWarnings("unchecked")
    private static void play(String name) throws IOException {
        var fixture = (Map<String, Object>) ScreenCodec.parse(read(name + ".json"));
        var run = new Run();
        for (Object page : (List<Object>) fixture.get("pages")) {
            var screen = ScreenCodec.decode(json(page));
            run.pages.put(screen.id(), screen);
        }
        run.open(fixture.containsKey("start") ? (String) fixture.get("start") : run.pages.keySet().iterator().next());
        for (Object item : (List<Object>) fixture.get("steps")) {
            var step = (Map<String, Object>) item;
            if (step.containsKey("click")) click(run, (String) step.get("click"), UiSession.Click.LEFT);
            else if (step.containsKey("right")) click(run, (String) step.get("right"), UiSession.Click.RIGHT);
            else if (step.containsKey("row")) {
                var rowStep = (List<Object>) step.get("row");
                run.runtime.click(ListRows.rowId((String) rowStep.get(0), ((BigDecimal) rowStep.get(1)).intValue()), UiSession.Click.LEFT, "");
            } else if (step.containsKey("key")) run.runtime.key((String) step.get("key"));
            else if (step.containsKey("wait")) run.clock += ((BigDecimal) step.get("wait")).longValue() * 1_000_000L;
            else if (step.containsKey("answers")) ((List<Object>) step.get("answers")).forEach(answer -> run.answers.add((String) answer));
            else if (step.containsKey("damage")) {
                // LogicController.damaged: on-damage first, then close-on-damage if the page is still there.
                var before = run.runtime;
                before.damaged(((BigDecimal) step.get("damage")).doubleValue(), (String) step.get("cause"));
                if (run.runtime == before && !run.closed && before.screen().logic().behavior().closeOnDamage()) run.close();
            } else if (step.containsKey("world")) {
                String from = run.world;
                run.world = (String) step.get("world");
                run.runtime.worldChanged(run.world, from);
            } else if (step.containsKey("timer")) {
                var task = run.later.poll();
                assertNotNull(task, "a timer is waiting");
                task.run();
            } else fail("Unknown step " + step);
        }
        var expect = (Map<String, Object>) fixture.get("expect");
        assertEquals(expect.get("events"), run.events, name + ": events");
        var variables = (Map<String, Object>) expect.getOrDefault("variables", Map.of());
        variables.forEach((variable, value) -> assertEquals(value, run.runtime.variables().get(variable), name + ": variable " + variable));
        var shown = (Map<String, Object>) expect.getOrDefault("shown", Map.of());
        shown.forEach((id, visible) -> assertEquals(visible, run.view.shown(id), name + ": shown " + id));
        assertEquals(expect.get("page"), run.runtime.screen().id(), name + ": page");
        assertEquals(expect.get("closed"), run.closed, name + ": closed");
    }
    private static void click(Run run, String id, UiSession.Click type) {
        var element = run.runtime.screen().element(id).orElseThrow();
        run.runtime.click(id, type, element.action());
    }

    private static String read(String file) throws IOException {
        try (InputStream in = CrossProbarTest.class.getResourceAsStream("/cross/" + file)) {
            assertNotNull(in, "src/test/resources/cross/" + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    /** Writes parsed JSON back as text, for ScreenCodec.decode. */
    @SuppressWarnings("unchecked")
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) {
            var out = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    default -> { if (c < 32) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
                }
            }
            return out.append('"').toString();
        }
        if (value instanceof BigDecimal n) return n.toPlainString();
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof List<?> list) {
            var out = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) { if (i > 0) out.append(','); out.append(json(list.get(i))); }
            return out.append(']').toString();
        }
        var out = new StringBuilder("{");
        boolean first = true;
        for (var entry : ((Map<String, Object>) value).entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append(json(entry.getKey())).append(':').append(json(entry.getValue()));
        }
        return out.append('}').toString();
    }
}
