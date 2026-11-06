package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewState;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionRunner;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Conditions;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.UiAction;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Variables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The logic of one open screen for one player: variables, action runs, keybinds, visibility conditions, data
 * lists, scrolling, drag and drop, click cooldowns, triggers (timers, damage, world change, variable change) and
 * animation playback. Everything game-side goes through {@link Host}, so this class has no Bukkit dependency and
 * is driven on the player's owner thread.
 */
final class ScreenRuntime implements ActionRunner.Effects {
    /** The game side for one player and one open screen. */
    interface Host {
        void command(String line);
        void console(String line);
        void message(String text);
        void actionBar(String text);
        void sound(String sound, float volume, float pitch);
        void teleport(String world, String x, String y, String z, String yaw, String pitch);
        void give(String item, int amount);
        void open(String screen);
        void back();
        void close();
        /** Runs a schema 1-8 action ("close", "open:id", "message:...", "command:...") the old way. */
        void legacy(String action);
        boolean hasPermission(String node);
        /** Vault: takes {@code amount} when the balance is enough; true when it was taken (false without an economy). */
        boolean charge(java.math.BigDecimal amount);
        /** Vault: adds {@code amount}; false without an economy. */
        boolean pay(java.math.BigDecimal amount);
        /** Closes the menu and sends the player to another server of the proxy. */
        void connect(String server);
        void input(UiAction.Input input, Consumer<String> submit, Runnable cancel);
        void later(int ticks, Runnable task);
        void warn(String message);
        /** Resolves built-in {tokens} and %placeholders% (never variables). */
        String placeholders(String text);
        void view(UnaryOperator<ViewState> change);
        ViewState view();
        /** Variables or list data changed: recompute live text now. */
        void refreshLive();
        /** Data a plugin supplied for a list source (TideframeApi#setList), or null. */
        List<Map<String, String>> list(String source);
        /** A drop finished (for the event); return false to cancel it. */
        boolean dropped(String dragged, String target);
        long now();
        /** The player's click cooldowns ("screen/element" -> nanoTime the wait ends), kept while they are online. */
        Map<String, Long> cooldowns();
        /** The game's yes/no dialog; exactly one callback runs later on the player's thread ({@code no} when there is no dialog). */
        void confirm(UiAction.Confirm confirm, Runnable yes, Runnable no);
        /** Frames baked for an animated element (0 = none) and the interval they play at, in ms. */
        int frameCount(String element);
        int frameInterval(String element);
        /** A variable of this screen changed value (for the event and the player's other open screen). */
        void variableChanged(String name, String before, String after);
        /** Adds HUD page {@code screen} over the shown ones, or takes it away (the menu stays open). */
        void hud(String screen, boolean add);
        /** Queues HUD page {@code screen} as a toast for {@code seconds}, with {@code values} as its variables. */
        void toast(String screen, double seconds, Map<String, String> values);
    }
    /** How deep on-change actions may set off more on-change actions. */
    static final int MAX_CHANGE_DEPTH = 8;

    private final Screen screen;
    private final Host host;
    private final Variables variables;
    private final ActionRunner runner;
    private final Map<String, Integer> scrolled = new HashMap<>();
    private boolean alive = true;
    /** Variables as on-change last saw them, and the on-change lists running now. */
    private Map<String, String> seen = Map.of();
    private final java.util.Set<String> changing = new HashSet<>();
    private int changeDepth;

    ScreenRuntime(Screen screen, Map<String, String> globals, Host host) {
        this.screen = Objects.requireNonNull(screen); this.host = Objects.requireNonNull(host);
        variables = new Variables(globals);
        runner = new ActionRunner(screen.logic().functions(), variables, host::placeholders, this);
    }

    Screen screen() { return screen; }
    Variables variables() { return variables; }

    /** Declares the screen's variables, fills its lists, evaluates conditions, runs its on-open actions and starts its timers. */
    void open() { open(Map.of()); }
    /** Opens with {@code values} set over the page's own variables before anything runs (a toast's {titulo}, {texto}). */
    void open(Map<String, String> values) {
        variables.declare(screen.logic().variables());
        values.forEach((name, value) -> variables.apply(name, "set", value));
        syncLists();
        refresh();
        seen = variables.snapshot();
        runner.run(screen.logic().onOpen());
        for (var timer : screen.logic().timers()) schedule(timer);
    }
    /** A timer: every period while the screen is open (the first run one period after opening). */
    private void schedule(ScreenLogic.Timer timer) {
        host.later(timer.everyTicks(), () -> {
            if (!alive) return;
            try { runner.run(timer.actions(), Map.of()); }
            finally { if (alive) schedule(timer); }
        });
    }
    /** The player took {@code damage} hit points ({@code cause}: fall, lava, entity_attack...): on-damage actions. */
    void damaged(double damage, String cause) {
        if (!alive || screen.logic().onDamage().isEmpty()) return;
        String amount = java.math.BigDecimal.valueOf(damage).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        runner.run(screen.logic().onDamage(), Map.of("damage", amount, "cause", cause));
    }
    /** The player moved from world {@code from} to {@code world}: on-world-change actions. */
    void worldChanged(String world, String from) {
        if (!alive || screen.logic().onWorldChange().isEmpty()) return;
        runner.run(screen.logic().onWorldChange(), Map.of("world", world, "from", from));
    }
    /** A global variable changed in the player's other open screen (menu or HUD): lists, conditions and on-change here. */
    void externalChange() {
        if (!alive) return;
        syncLists(); refresh(); changes(false);
    }
    /**
     * Fires on-change for every variable whose value differs from the last look ({old} and {new} in scope) and, with
     * {@code notify}, tells the host. A list never sets itself off again while it runs, and chains stop at
     * {@link #MAX_CHANGE_DEPTH}.
     */
    private void changes(boolean notify) {
        var now = variables.snapshot();
        if (now.equals(seen)) return;
        var before = seen;
        seen = now;
        var names = new java.util.LinkedHashSet<>(before.keySet());
        names.addAll(now.keySet());
        for (String name : names) {
            String old = before.get(name), next = now.get(name);
            if (java.util.Objects.equals(old, next)) continue;
            if (notify) host.variableChanged(name, old, next);
            var actions = screen.logic().onChange().get(name);
            if (actions == null || !alive || changing.contains(name)) continue;
            if (changeDepth >= MAX_CHANGE_DEPTH) { warn("on-change stopped after " + MAX_CHANGE_DEPTH + " changes in a row (variables changing each other?)"); continue; }
            changing.add(name); changeDepth++;
            try { runner.run(actions, Map.of("old", old == null ? "" : old, "new", next == null ? "" : next)); }
            finally { changeDepth--; changing.remove(name); }
        }
    }
    /**
     * The screen is going away. Pending delays stop; on-close actions run once, right now (their own delays,
     * redirects and closes are ignored: the screen is already closing).
     */
    void shutdown() {
        if (!alive) return;
        alive = false;
        if (screen.logic().onClose().isEmpty()) return;
        var closing = new ActionRunner(screen.logic().functions(), variables, host::placeholders, new ClosingEffects());
        closing.run(screen.logic().onClose());
    }

    /** Runs actions as if the page defined them (API, keybinds); {@code scope} adds read-only tokens. */
    void run(List<UiAction> actions, Map<String, String> scope) { if (alive) runner.run(actions, scope); }

    /**
     * A click on {@code target} (element id, list row "id#2" or null for empty space). Schema-9 actions win; an
     * element without them runs its legacy action on a left click. While carrying, a left click drops.
     */
    void click(String target, UiSession.Click type, String legacyAction) {
        if (!alive) return;
        var view = host.view();
        if (view.carried() != null && type == UiSession.Click.LEFT) { drop(view.carried(), target); return; }
        if (target == null) return;
        var element = screen.element(ListRows.template(target)).orElse(null);
        if (element == null) return;
        var logic = element.logic();
        int row = ListRows.index(target);
        Map<String, String> scope = row >= 0 ? rowScope(element, row) : Map.of();
        if (row >= 0 && scope.isEmpty()) return; // a row without data
        // A click that would run something waits for the element's cooldown (rows share their list's).
        if (type == UiSession.Click.RIGHT) {
            if (!logic.rightActions().isEmpty() && !coolingDown(element, scope)) runner.run(logic.rightActions(), scope);
            return;
        }
        if (logic.draggable()) {
            if (coolingDown(element, scope)) return;
            host.view(state -> state.carry(target));
            runner.run(logic.actions(), scope);
            return;
        }
        if (row >= 0 && logic.list() != null && !logic.list().onSelect().isEmpty()) { if (!coolingDown(element, scope)) runner.run(logic.list().onSelect(), scope); return; }
        if (!logic.actions().isEmpty()) { if (!coolingDown(element, scope)) runner.run(logic.actions(), scope); return; }
        if (legacyAction != null && !legacyAction.isEmpty() && !coolingDown(element, scope)) host.legacy(legacyAction);
    }
    /**
     * True when the element is still cooling down: its on-cooldown actions run instead, with {cooldown} (seconds left).
     * Otherwise its wait starts now and the click goes ahead.
     */
    private boolean coolingDown(Screen.Element element, Map<String, String> scope) {
        var logic = element.logic();
        if (logic.cooldownMs() <= 0) return false;
        String key = screen.id() + "/" + element.id();
        long now = host.now();
        Long until = host.cooldowns().get(key);
        if (until != null && now - until < 0) {
            if (!logic.onCooldown().isEmpty()) {
                var withLeft = new LinkedHashMap<>(scope);
                withLeft.put("cooldown", secondsLeft(until - now));
                runner.run(logic.onCooldown(), withLeft);
            }
            return true;
        }
        host.cooldowns().put(key, now + logic.cooldownMs() * 1_000_000L);
        return false;
    }
    /** Seconds left, rounded up to a tenth so it never reads 0 while the element still waits: "2", "0.4". */
    static String secondsLeft(long nanos) {
        long tenths = Math.max(1, (nanos + 99_999_999L) / 100_000_000L);
        return tenths % 10 == 0 ? String.valueOf(tenths / 10) : tenths / 10 + "." + tenths % 10;
    }
    private void drop(String dragged, String target) {
        host.view(state -> state.carry(null));
        if (target == null || target.equals(dragged)) return;
        var element = screen.element(ListRows.template(target)).orElse(null);
        if (element == null || !host.dropped(dragged, target)) return;
        var scope = new LinkedHashMap<String, String>();
        int row = ListRows.index(target);
        if (row >= 0) scope.putAll(rowScope(element, row));
        scope.put("dragged", dragged); scope.put("target", target);
        var source = screen.element(ListRows.template(dragged)).orElse(null);
        int draggedRow = ListRows.index(dragged);
        if (source != null && draggedRow >= 0) rowScope(source, draggedRow).forEach((key, value) -> scope.put("dragged." + key, value));
        runner.run(element.logic().onDrop(), scope);
    }
    /**
     * The cursor moved from {@code previous} to {@code current} (element ids or list rows "id#2"; either may be null):
     * the old element's tooltip hides, its hover keyframes play back and its on-leave actions run; then the new
     * element's tooltip shows, its keyframes play forward and its on-hover actions run.
     */
    void hover(String previous, String current) {
        if (!alive) return;
        hovered(previous, false);
        hovered(current, true);
    }
    private void hovered(String target, boolean entering) {
        if (target == null || !alive) return;
        var element = screen.element(ListRows.template(target)).orElse(null);
        if (element == null) return;
        var logic = element.logic();
        String tip = logic.tooltip();
        if (tip != null) host.view(state -> entering ? state.visibility(List.of(tip), List.of(), List.of()) : state.visibility(List.of(), List.of(tip), List.of()));
        if (logic.motion() != null) { long now = host.now(); host.view(state -> state.hoverMotion(element, entering, now, logic.motion().frames())); }
        var actions = entering ? logic.hoverActions() : logic.leaveActions();
        if (actions.isEmpty()) return;
        int row = ListRows.index(target);
        Map<String, String> scope = row >= 0 ? rowScope(element, row) : Map.of();
        if (row >= 0 && scope.isEmpty()) return;
        var withTarget = new LinkedHashMap<>(scope); withTarget.put("target", target);
        runner.run(actions, withTarget);
    }
    /** A bound key (W, SHIFT_Q, 1, SCROLL_UP...). Returns true when the page binds it. */
    boolean key(String key) {
        if (!alive) return false;
        var actions = screen.logic().keybinds().get(key);
        if (actions == null) return false;
        runner.run(actions, Map.of());
        return true;
    }
    /** True when the page binds this key, without running it. */
    boolean binds(String key) { return screen.logic().keybinds().containsKey(key); }
    /** A wheel step: scrolls the list under the cursor, else runs the SCROLL_UP / SCROLL_DOWN keybind. */
    boolean scroll(String hover, int direction) {
        if (!alive) return false;
        if (hover != null) {
            var element = screen.element(ListRows.template(hover)).orElse(null);
            if (element != null && element.logic().list() != null) { scrollList(element.id(), direction, false); return true; }
        }
        return key(direction < 0 ? "SCROLL_UP" : "SCROLL_DOWN");
    }
    /** True when some list could scroll (wheel steps are worth reading as scrolling). */
    boolean scrollable() { return screen.elements().stream().anyMatch(e -> e.logic().list() != null) || binds("SCROLL_UP") || binds("SCROLL_DOWN"); }

    /** Owner thread, a few times a second: visible-if conditions. */
    void refresh() {
        if (!alive) return;
        var gated = new HashSet<String>();
        for (var e : screen.elements()) {
            String condition = e.logic().visibleIf();
            if (condition != null && !Conditions.test(condition, this::resolve)) gated.add(e.id());
        }
        host.view(state -> state.gate(gated));
    }
    String resolve(String text) { return runner.resolve(text); }

    // ----- data lists -----
    /** Rows (all of them, filtered) of a list element's source. */
    List<Map<String, String>> data(Screen.Element element) {
        var spec = element.logic().list();
        var supplied = host.list(spec.source());
        List<Map<String, String>> rows;
        if (supplied != null) rows = supplied;
        else {
            String text = variables.get(spec.source());
            rows = new ArrayList<>();
            if (text != null && !text.isBlank()) for (String part : text.split("\\s*[,\n]\\s*")) if (!part.isEmpty()) rows.add(Map.of("name", part));
        }
        if (spec.filter() == null) return rows;
        String filter = variables.get(spec.filter());
        if (filter == null || filter.isBlank()) return rows;
        String needle = plain(filter);
        var out = new ArrayList<Map<String, String>>();
        for (var row : rows) if (row.values().stream().anyMatch(value -> plain(value).contains(needle))) out.add(row);
        return out;
    }
    private static String plain(String text) { return text.replaceAll("(?i)[&§][0-9a-fk-orx]|<[^<>]{1,32}>", "").toLowerCase(Locale.ROOT); }
    int scrolled(String id) { return scrolled.getOrDefault(id, 0); }
    /** Scope of a visible row: {item}, {index} (1-based position in the data), {row} and {item.field}. */
    Map<String, String> rowScope(Screen.Element element, int row) {
        var spec = element.logic().list();
        if (spec == null) return Map.of();
        var data = data(element);
        int at = scrolled(element.id()) + row;
        if (row < 0 || row >= spec.rows() || at >= data.size()) return Map.of();
        var item = data.get(at);
        var scope = new LinkedHashMap<String, String>();
        item.forEach((key, value) -> scope.put("item." + key, value));
        scope.put("item", item.getOrDefault("name", item.isEmpty() ? "" : item.values().iterator().next()));
        scope.put("index", String.valueOf(at + 1));
        scope.put("row", String.valueOf(row));
        return scope;
    }
    /** Scope of every visible row of a list element, in order. */
    List<Map<String, String>> visibleRows(Screen.Element element) {
        var out = new ArrayList<Map<String, String>>();
        var spec = element.logic().list();
        if (spec == null) return out;
        for (int i = 0; i < spec.rows(); i++) { var scope = rowScope(element, i); if (scope.isEmpty()) break; out.add(scope); }
        return out;
    }
    /** Clamps scroll positions and publishes how many rows each list shows. */
    void syncLists() {
        var counts = new LinkedHashMap<String, Integer>();
        for (var e : screen.elements()) {
            var spec = e.logic().list();
            if (spec == null) continue;
            int size = data(e).size(), max = Math.max(0, size - spec.rows());
            int offset = Math.max(0, Math.min(max, scrolled(e.id())));
            scrolled.put(e.id(), offset);
            counts.put(e.id(), Math.max(0, Math.min(spec.rows(), size - offset)));
        }
        if (!counts.isEmpty()) host.view(state -> { var next = state; for (var c : counts.entrySet()) next = next.rows(c.getKey(), c.getValue()); return next; });
        host.refreshLive();
    }
    void scrollList(String id, int rows, boolean absolute) {
        var element = screen.element(id).orElse(null);
        if (element == null || element.logic().list() == null) return;
        int before = scrolled(id);
        scrolled.put(id, absolute ? rows : before + rows);
        syncLists();
    }
    /** Live-text values: variables and row scopes go in after placeholders (see LivePlaceholders.Values). */
    LivePlaceholders.Values liveValues() {
        return new LivePlaceholders.Values() {
            public String insert(String text, Map<String, String> scope) { return ActionRunner.substitute(text, scope, variables); }
            public String variable(String name) { return variables.get(name); }
            public List<Map<String, String>> rows(Screen.Element template) { return visibleRows(template); }
            public boolean shown(String id) { return host.view().shown(id); }
        };
    }

    // ----- ActionRunner.Effects -----
    public void command(String line) { host.command(line); }
    public void console(String line) { host.console(line); }
    public void message(String text) { host.message(text); }
    public void actionBar(String text) { host.actionBar(text); }
    public void sound(String sound, float volume, float pitch) { host.sound(sound, volume, pitch); }
    public void teleport(String world, String x, String y, String z, String yaw, String pitch) { host.teleport(world, x, y, z, yaw, pitch); }
    public void give(String item, int amount) { host.give(item, amount); }
    public void open(String target) { host.open(target); }
    public void back() { host.back(); }
    public void close() { host.close(); }
    public void visibility(List<String> show, List<String> hide, List<String> toggle) { host.view(state -> state.visibility(show, hide, toggle)); }
    public void offset(String element, int dx) { host.view(state -> state.offset(element, dx)); }
    public void tint(String element, String color) {
        if (color != null && !color.matches("#[0-9a-fA-F]{6}")) { host.warn("Tint must be #RRGGBB, got " + color); return; }
        host.view(state -> state.tint(element, color));
    }
    public void animate(String element) { long now = host.now(); host.view(state -> state.restart(element, now)); }
    public void scroll(String element, int rows, boolean absolute) { scrollList(element, rows, absolute); }
    public boolean hasPermission(String node) { return host.hasPermission(node); }
    // A balance shown on the page ({balance}, %vault_eco_balance%) follows a charge or a payment at once.
    public boolean charge(java.math.BigDecimal amount) { boolean taken = host.charge(amount); if (taken) host.refreshLive(); return taken; }
    public void pay(java.math.BigDecimal amount) { if (host.pay(amount)) host.refreshLive(); }
    public void connect(String server) { host.connect(server); }
    public void input(UiAction.Input input, Consumer<String> submit, Runnable cancel) { host.input(input, submit, cancel); }
    public void confirm(UiAction.Confirm confirm, Runnable yes, Runnable no) { host.confirm(confirm, yes, no); }
    /** Pause holds the frame showing; resume plays on from it; frame jumps (paused stays paused, playing keeps playing). */
    public void playback(String element, String command, int frame) {
        var target = screen.element(element).orElse(null);
        int count = host.frameCount(element), interval = host.frameInterval(element);
        if (target == null || target.logic().frames() == null || count <= 0 || interval <= 0) { warn(command + ": " + element + " is not a PNG or GIF animation"); return; }
        long now = host.now(), frameNanos = interval * 1_000_000L;
        var view = host.view();
        Integer paused = view.frozen(element);
        int showing = paused != null ? Math.min(paused, count - 1)
                : DynamicLayer.frameIndex(now - view.started(element), interval, count, target.logic().frames().loop());
        switch (command) {
            case "pause" -> { if (paused == null) host.view(state -> state.playback(element, showing, state.started(element))); }
            case "resume" -> { if (paused != null) host.view(state -> state.playback(element, null, now - showing * frameNanos)); }
            default -> {
                int to = Math.max(0, Math.min(count - 1, frame - 1));
                host.view(state -> paused != null ? state.playback(element, to, state.started(element)) : state.playback(element, null, now - to * frameNanos));
            }
        }
    }
    public void hud(String target, boolean add) { host.hud(target, add); }
    public void toast(String target, double seconds, Map<String, String> values) { host.toast(target, seconds, values); }
    public void variablesChanged() { syncLists(); refresh(); changes(true); }
    public void later(int ticks, Runnable resume) { host.later(ticks, resume); }
    public boolean alive() { return alive; }
    public void warn(String message) { host.warn("Screen " + screen.id() + ": " + message); }

    /** on-close actions: the screen is already going, so navigation and delays do nothing. */
    private final class ClosingEffects implements ActionRunner.Effects {
        private boolean running = true;
        public void command(String line) { host.command(line); }
        public void console(String line) { host.console(line); }
        public void message(String text) { host.message(text); }
        public void actionBar(String text) { host.actionBar(text); }
        public void sound(String sound, float volume, float pitch) { host.sound(sound, volume, pitch); }
        public void teleport(String world, String x, String y, String z, String yaw, String pitch) { host.teleport(world, x, y, z, yaw, pitch); }
        public void give(String item, int amount) { host.give(item, amount); }
        public void open(String target) { }
        public void back() { }
        public void close() { }
        public void visibility(List<String> show, List<String> hide, List<String> toggle) { }
        public void offset(String element, int dx) { }
        public void tint(String element, String color) { }
        public void animate(String element) { }
        public void scroll(String element, int rows, boolean absolute) { }
        public boolean hasPermission(String node) { return host.hasPermission(node); }
        public boolean charge(java.math.BigDecimal amount) { return host.charge(amount); }
        public void pay(java.math.BigDecimal amount) { host.pay(amount); }
        // Moving the player to another server while the menu is still closing is left to the page's buttons.
        public void connect(String server) { warn("server: ignored in on-close actions"); }
        public void input(UiAction.Input input, Consumer<String> submit, Runnable cancel) { }
        // No dialog can open while the screen closes: the safe answer.
        public void confirm(UiAction.Confirm confirm, Runnable yes, Runnable no) { no.run(); }
        public void playback(String element, String command, int frame) { }
        // A HUD page or a toast does not depend on the closing screen: "thanks for buying" on close works.
        public void hud(String target, boolean add) { host.hud(target, add); }
        public void toast(String target, double seconds, Map<String, String> values) { host.toast(target, seconds, values); }
        public void variablesChanged() { }
        public void later(int ticks, Runnable resume) { running = false; }
        public boolean alive() { return running; }
        public void warn(String message) { host.warn("Screen " + screen.id() + " (closing): " + message); }
    }
}
