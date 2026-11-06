package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewState;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.UiAction;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Screen logic for every player: one {@link ScreenRuntime} per open menu and per open overlay, the player's global
 * variables and back history (both live while any Tideframe screen is open), data lists supplied by plugins, and the
 * game side of every action (commands, sounds, teleports, dialogs...). Owner-thread only unless noted.
 */
final class LogicController {
    /** What the controller needs from the plugin. */
    interface Plugin {
        /** Opens through the normal path (permission, events); {@code back} navigations do not push history. */
        void open(Player player, String screenId, boolean back);
        void close(Player player, TideframeCloseEvent.Reason reason);
        UiSession session(Player player);
        PassiveHudSession overlay(Player player);
        /** Built-in {tokens} and %placeholders% for this player (no variables). */
        String placeholders(Player player, String text);
        /** The schema 1-8 action path ("close", "open:id", "message:text", "command:/x"). */
        void legacy(Player player, String action);
        SchedulerBridge scheduler();
        java.util.logging.Logger logger();
        void callEvent(org.bukkit.event.Event event);
        /** Closes the player's menu and sends them to {@code server} through the proxy. */
        void connect(Player player, String server);
        /** The pack's baked screens (frame counts and intervals of animations). */
        dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets assets();
        /** Adds a HUD page over the shown ones, or takes it away, keeping any menu open. */
        void hud(Player player, String screenId, boolean add);
        /** Queues a toast: HUD page {@code screenId} for {@code seconds}, its variables set from {@code values}. */
        void toast(Player player, String screenId, double seconds, Map<String, String> values);
    }
    enum Mode { MENU, OVERLAY }
    private record Key(UUID player, Mode mode) { }

    private final Plugin plugin;
    private final InputDialogs dialogs = new InputDialogs();
    private final Map<Key, ScreenRuntime> runtimes = new ConcurrentHashMap<>();
    /** The runtimes of the player's HUD pages (several since 0.23: stacked pages, toasts), in the order they opened. */
    private final Map<UUID, Map<String, ScreenRuntime>> overlays = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, String>> globals = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<String>> history = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, List<Map<String, String>>>> lists = new ConcurrentHashMap<>();
    /** Click cooldowns per player ("screen/element" -> nanoTime the wait ends); they last while the player is online. */
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private volatile Set<String> consoleCommands = Set.of("*");
    private final VaultEconomy economy = new VaultEconomy();
    private volatile SavedVariables saved = SavedVariables.NONE;

    LogicController(Plugin plugin) { this.plugin = Objects.requireNonNull(plugin); }

    /** config.yml → actions.console-commands: root commands console actions may run ("*" = any). */
    void configure(List<String> console) {
        consoleCommands = Set.copyOf(console.stream().map(s -> s.toLowerCase(Locale.ROOT).replaceFirst("^/", "")).toList());
    }
    boolean dialogsAvailable() { return dialogs.available(); }
    /** After a reload: look the economy up again (plugins may have changed). */
    void resetIntegrations() { economy.reset(); }
    /** config.yml variables.saved. */
    void saveVariables(SavedVariables value) { saved = Objects.requireNonNull(value); }
    /** The player's global variables; a new session starts from their saved ones. */
    private Map<String, String> globalsOf(Player player) {
        return globals.computeIfAbsent(player.getUniqueId(), id -> { var fresh = new java.util.LinkedHashMap<String, String>(); saved.load(player, fresh); return fresh; });
    }
    /** Writes the saved variables of an open session into the player's data (in memory; the server saves it to disk). */
    private void persist(Player player) {
        var known = globals.get(player.getUniqueId());
        if (known == null) return;
        try { saved.store(player, known); } catch (RuntimeException failure) { plugin.logger().warning("Could not save the variables of " + player.getName() + ": " + failure.getMessage()); }
    }
    /** Server stopping (owner thread of every player): keep what the open sessions changed. */
    void persistAll(java.util.Collection<? extends Player> players) { for (var player : players) persist(player); }

    // ----- lifecycle -----
    /** A menu or overlay just opened (or replaced the previous one) on the owner thread. An overlay replaces every HUD page. */
    void attach(Player player, Screen screen, Mode mode) {
        detach(player, mode);
        if (mode == Mode.OVERLAY) { attachOverlay(player, screen); return; }
        var runtime = new ScreenRuntime(screen, globalsOf(player), host(player, mode, screen));
        runtimes.put(new Key(player.getUniqueId(), mode), runtime);
        if (mode == Mode.MENU) wireMenu(player, screen);
        runtime.open();
        for (String problem : screen.logicProblems()) plugin.logger().fine("Screen " + screen.id() + ": " + problem);
    }
    /** A HUD page opened over the others (or again): its own runtime, alongside the other pages' ones. */
    /**
     * Opening a page runs its on-open actions at once, so a page that opens another from them nests one opening in the
     * other. {@value #MAX_OPEN_CHAIN} in a row is a loop (a page that opens itself, two that open each other): it stops
     * there with a warning instead of overflowing the owner thread's stack.
     */
    static final int MAX_OPEN_CHAIN = 8;
    private static final ThreadLocal<int[]> OPENING = ThreadLocal.withInitial(() -> new int[1]);
    private void chained(Runnable opening) {
        int[] depth = OPENING.get();
        if (depth[0] >= MAX_OPEN_CHAIN) {
            plugin.logger().warning("Pages kept opening each other from their opening actions; stopped after " + MAX_OPEN_CHAIN + " (a page that opens itself?)");
            return;
        }
        depth[0]++;
        try { opening.run(); } finally { depth[0]--; }
    }
    void attachOverlay(Player player, Screen screen) { attachOverlay(player, screen, Map.of()); }
    /** As above; {@code values} (a toast's {titulo}, {texto}...) are set before the page's on-open actions run. */
    void attachOverlay(Player player, Screen screen, Map<String, String> values) {
        detachOverlay(player, screen.id());
        var runtime = new ScreenRuntime(screen, globalsOf(player), host(player, Mode.OVERLAY, screen));
        overlays.computeIfAbsent(player.getUniqueId(), id -> java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>())).put(screen.id(), runtime);
        runtime.open(values);
        for (String problem : screen.logicProblems()) plugin.logger().fine("Screen " + screen.id() + ": " + problem);
    }
    /** HUD page {@code screenId} is going away: its on-close actions run now. */
    void detachOverlay(Player player, String screenId) {
        var pages = overlays.get(player.getUniqueId());
        var runtime = pages == null ? null : pages.remove(screenId);
        if (runtime == null) return;
        try { runtime.shutdown(); }
        catch (RuntimeException failure) { plugin.logger().warning("On-close actions of " + runtime.screen().id() + " failed: " + failure.getMessage()); }
        persist(player);
    }
    /** The runtimes of the player's HUD pages, in the order they opened (a copy). */
    List<ScreenRuntime> overlayRuntimes(Player player) {
        var pages = overlays.get(player.getUniqueId());
        if (pages == null) return List.of();
        synchronized (pages) { return List.copyOf(pages.values()); }
    }
    /** The menu's runtime (if any), then every HUD page's. */
    private List<ScreenRuntime> allRuntimes(Player player) {
        var all = new java.util.ArrayList<ScreenRuntime>();
        var menu = runtimes.get(new Key(player.getUniqueId(), Mode.MENU));
        if (menu != null) all.add(menu);
        all.addAll(overlayRuntimes(player));
        return all;
    }
    /** The screen of {@code mode} is going away (for OVERLAY: every HUD page): its on-close actions run now. */
    void detach(Player player, Mode mode) {
        if (mode == Mode.OVERLAY) { for (var page : overlayRuntimes(player)) detachOverlay(player, page.screen().id()); return; }
        var runtime = runtimes.remove(new Key(player.getUniqueId(), mode));
        if (runtime != null) {
            try { runtime.shutdown(); }
            catch (RuntimeException failure) { plugin.logger().warning("On-close actions of " + runtime.screen().id() + " failed: " + failure.getMessage()); }
            // After the on-close actions, which may change variables too.
            persist(player);
        }
    }
    /** Nothing of Tideframe stays open: global variables and back history are cleared. */
    void forgetSession(Player player) {
        UUID id = player.getUniqueId();
        if (runtimes.containsKey(new Key(id, Mode.MENU)) || !overlayRuntimes(player).isEmpty()) return;
        persist(player);
        globals.remove(id); history.remove(id);
    }
    void quit(Player player) {
        detach(player, Mode.MENU); detach(player, Mode.OVERLAY);
        UUID id = player.getUniqueId();
        persist(player);
        globals.remove(id); history.remove(id); lists.remove(id); cooldowns.remove(id); overlays.remove(id);
    }
    void clear() { runtimes.clear(); overlays.clear(); globals.clear(); history.clear(); lists.clear(); cooldowns.clear(); }
    /** The menu's runtime, or for OVERLAY the first HUD page's (null when none). */
    ScreenRuntime runtime(Player player, Mode mode) {
        if (mode == Mode.OVERLAY) { var pages = overlayRuntimes(player); return pages.isEmpty() ? null : pages.get(0); }
        return runtimes.get(new Key(player.getUniqueId(), mode));
    }
    /** Live-text values of the screen's runtime (variables, list rows), or none when it has no logic running. */
    LivePlaceholders.Values liveValues(Player player, Screen shown) {
        for (var runtime : allRuntimes(player)) if (runtime.screen() == shown) return runtime.liveValues();
        return LivePlaceholders.Values.NONE;
    }
    /** Called a few times a second before live text is resolved: visible-if conditions. */
    void refresh(Player player, Screen shown) {
        for (var runtime : allRuntimes(player)) if (runtime.screen() == shown) runtime.refresh();
    }

    // ----- history -----
    void pushHistory(Player player, String screenId) {
        var stack = history.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        if (screenId.equals(stack.peek())) return;
        stack.push(screenId);
        while (stack.size() > 32) stack.removeLast();
    }
    Optional<String> popHistory(Player player) {
        var stack = history.get(player.getUniqueId());
        return stack == null || stack.isEmpty() ? Optional.empty() : Optional.of(stack.pop());
    }
    boolean hasHistory(Player player) { var stack = history.get(player.getUniqueId()); return stack != null && !stack.isEmpty(); }

    // ----- input -----
    private void wireMenu(Player player, Screen screen) {
        var session = plugin.session(player);
        if (session == null) return;
        session.setRouter(new UiSession.Router() {
            public void click(Screen shown, String target, UiSession.Click type, String legacy) { LogicController.this.click(player, shown, target, type, legacy); }
            public void scroll(Screen shown, String target, int direction) { wheel(player, direction); }
        });
        session.setKeyListener(key -> key(player, key));
        session.setHoverListener((previous, current) -> {
            if (TideframeHoverEvent.getHandlerList().getRegisteredListeners().length > 0)
                plugin.callEvent(new TideframeHoverEvent(player, session.screen().id(), previous, current));
            var runtime = runtime(player, Mode.MENU);
            if (runtime != null && runtime.screen() == session.screen()) runtime.hover(previous, current);
        });
    }
    void click(Player player, Screen screen, String target, UiSession.Click type, String legacy) {
        if (target != null) {
            var event = new TideframeClickEvent(player, screen.id(), target, legacy == null ? "" : legacy,
                    type == UiSession.Click.LEFT ? TideframeClickEvent.ClickType.LEFT : TideframeClickEvent.ClickType.RIGHT, player.isSneaking());
            plugin.callEvent(event);
            if (event.isCancelled()) return;
        }
        var runtime = runtime(player, Mode.MENU);
        if (runtime == null || runtime.screen() != screen) {
            if (target != null && type == UiSession.Click.LEFT && legacy != null && !legacy.isEmpty()) plugin.legacy(player, legacy);
            return;
        }
        runtime.click(target, type, legacy);
    }
    /**
     * A key while a menu is open: Q, F, L (network), W A S D SPACE (movement input), 1-9 and wheel (hotbar).
     * Shift held adds SHIFT_. Returns true when something handled it.
     */
    boolean key(Player player, String baseKey) {
        var session = plugin.session(player);
        var runtime = runtime(player, Mode.MENU);
        if (session == null || runtime == null) return false;
        String key = (player.isSneaking() ? "SHIFT_" : "") + baseKey;
        var event = new TideframeKeyEvent(player, runtime.screen().id(), key, session.hover());
        plugin.callEvent(event);
        if (event.isCancelled()) return true;
        var behavior = runtime.screen().logic().behavior();
        if (key.equals("F") && behavior.closeOnSwap() || key.equals("SHIFT_F") && behavior.closeOnShiftSwap()) {
            plugin.close(player, TideframeCloseEvent.Reason.KEY);
            return true;
        }
        if (runtime.key(key)) return true;
        // behavior.keyboard-navigation: unbound W A S D move to the nearest button that way; Space clicks it.
        if (!behavior.keyboardNavigation()) return false;
        switch (key) {
            case "W" -> session.navigate(0, 1);
            case "S" -> session.navigate(0, -1);
            case "A" -> session.navigate(-1, 0);
            case "D" -> session.navigate(1, 0);
            case "SPACE" -> session.click();
            default -> { return false; }
        }
        return true;
    }
    /** One wheel step (+1 down, -1 up): scrolls the list under the cursor, else the SCROLL_DOWN / SCROLL_UP keybind. */
    void wheel(Player player, int direction) {
        var session = plugin.session(player);
        var runtime = runtime(player, Mode.MENU);
        if (session == null || runtime == null) return;
        String hover = session.hover();
        if (hover != null) {
            var element = runtime.screen().element(ListRows.template(hover)).orElse(null);
            if (element != null && element.logic().list() != null) { runtime.scrollList(element.id(), direction, false); return; }
        }
        key(player, direction < 0 ? "SCROLL_UP" : "SCROLL_DOWN");
    }
    /**
     * The hotbar slot changed while a menu is open (the plugin cancels the change). One slot either way reads as a
     * wheel step when the page can scroll or binds no number keys; anything else is the number key itself.
     */
    void hotbar(Player player, int previous, int next) {
        var runtime = runtime(player, Mode.MENU);
        if (runtime == null || previous == next) return;
        int step = Math.floorMod(next - previous, 9);
        boolean numbers = false;
        for (int n = 1; n <= 9; n++) numbers |= runtime.binds(String.valueOf(n)) || runtime.binds("SHIFT_" + n);
        if ((step == 1 || step == 8) && (runtime.scrollable() || !numbers)) { wheel(player, step == 1 ? 1 : -1); return; }
        key(player, String.valueOf(next + 1));
    }
    /** The player took damage: on-damage actions of the menu and the HUD, then the menu closes if it asks for that. */
    void damaged(Player player, double damage, String cause) {
        for (var overlay : overlayRuntimes(player)) overlay.damaged(damage, cause);
        var runtime = runtime(player, Mode.MENU);
        if (runtime == null) return;
        runtime.damaged(damage, cause);
        // The on-damage actions may have closed or replaced the menu already.
        var still = runtime(player, Mode.MENU);
        if (still == runtime && runtime.screen().logic().behavior().closeOnDamage()) plugin.close(player, TideframeCloseEvent.Reason.DAMAGE);
    }
    /** The player changed world: on-world-change actions of the menu and the HUD. */
    void worldChanged(Player player, String world, String from) {
        for (var runtime : allRuntimes(player)) runtime.worldChanged(world, from);
    }
    /** Shift while a menu is open: closes unless the page turned that off. */
    boolean closesOnSneak(Player player) {
        var runtime = runtime(player, Mode.MENU);
        return runtime == null || runtime.screen().logic().behavior().closeOnSneak();
    }
    /** Closing goes back to the previous screen instead when the page asks for it (behavior.reopen). */
    boolean reopens(Player player) {
        var runtime = runtime(player, Mode.MENU);
        return runtime != null && runtime.screen().logic().behavior().reopen() && hasHistory(player);
    }

    // ----- API -----
    void setList(Player player, String source, List<Map<String, String>> rows) {
        var copy = rows.stream().map(Map::copyOf).toList();
        lists.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>()).put(source, copy);
        for (var runtime : allRuntimes(player)) runtime.syncLists();
    }
    void clearList(Player player, String source) {
        var known = lists.get(player.getUniqueId());
        if (known != null) known.remove(source);
        for (var runtime : allRuntimes(player)) runtime.syncLists();
    }
    /** Runs actions (maps in the page format) in the open menu, else the overlay. */
    boolean run(Player player, Object actions) {
        var runtime = runtime(player, Mode.MENU);
        if (runtime == null) runtime = runtime(player, Mode.OVERLAY);
        if (runtime == null) return false;
        runtime.run(ActionParser.actions(actions), Map.of());
        return true;
    }
    boolean clickElement(Player player, String id) {
        var runtime = runtime(player, Mode.MENU);
        if (runtime == null || runtime.screen().element(ListRows.template(id)).isEmpty()) return false;
        var element = runtime.screen().element(ListRows.template(id)).get();
        runtime.click(id, UiSession.Click.LEFT, dev.linqfy.bigCasares.modules.tideframe.engine.core.MessageRules.action(element, player::hasPermission));
        return true;
    }
    Optional<String> variable(Player player, String name) {
        var runtime = runtime(player, Mode.MENU);
        if (runtime == null) runtime = runtime(player, Mode.OVERLAY);
        if (runtime != null) return Optional.ofNullable(runtime.variables().get(name));
        var known = globals.get(player.getUniqueId());
        // No session open: a saved variable is still the player's.
        if (known == null) { known = new java.util.LinkedHashMap<>(); saved.load(player, known); }
        return Optional.ofNullable(known.get(name.replaceFirst("^[*$]", "")));
    }
    void setVariable(Player player, String name, String value) {
        dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Variables.validName(name);
        var runtime = runtime(player, Mode.MENU);
        if (runtime == null) runtime = runtime(player, Mode.OVERLAY);
        if (runtime == null) { globalsOf(player).put(name.replaceFirst("^[*$]", ""), value); persist(player); return; }
        if (runtime.variables().apply(name, "set", value)) runtime.variablesChanged();
    }
    boolean view(Player player, UnaryOperator<ViewState> change) {
        var session = plugin.session(player);
        if (session != null) { session.updateView(change); return true; }
        var overlay = plugin.overlay(player);
        if (overlay != null) { overlay.updateView(change); return true; }
        return false;
    }

    // ----- the game side of actions -----
    private ScreenRuntime.Host host(Player player, Mode mode, Screen screen) {
        return new ScreenRuntime.Host() {
            public void command(String line) { String clean = sanitize(line); if (!clean.isEmpty()) player.performCommand(clean); }
            public void console(String line) {
                String clean = sanitize(line);
                if (clean.isEmpty()) return;
                String root = clean.split(" ", 2)[0].toLowerCase(Locale.ROOT);
                if (!consoleCommands.contains("*") && !consoleCommands.contains(root)) {
                    plugin.logger().warning("Screen " + screen.id() + ": console command '" + root + "' is not in actions.console-commands; skipped.");
                    return;
                }
                plugin.scheduler().global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), clean));
            }
            public void message(String text) { player.sendMessage(TextFormat.parse(text)); }
            public void actionBar(String text) { player.sendActionBar(TextFormat.parse(text)); }
            public void sound(String sound, float volume, float pitch) { playSound(player, sound, volume, pitch); }
            public void teleport(String world, String x, String y, String z, String yaw, String pitch) { teleportPlayer(player, world, x, y, z, yaw, pitch); }
            public void give(String item, int amount) { givePlayer(player, item, amount); }
            public void open(String target) { chained(() -> plugin.open(player, target, false)); }
            public void back() {
                var previous = popHistory(player);
                if (previous.isPresent()) chained(() -> plugin.open(player, previous.get(), true));
                else plugin.close(player, TideframeCloseEvent.Reason.ACTION);
            }
            public void close() { plugin.close(player, TideframeCloseEvent.Reason.ACTION); }
            public void legacy(String action) { plugin.legacy(player, action); }
            public boolean hasPermission(String node) { return player.hasPermission(node); }
            public boolean charge(java.math.BigDecimal amount) {
                if (!economy.available()) { warn("Screen " + screen.id() + ": charge needs Vault with an economy plugin; nothing was charged."); return false; }
                return economy.charge(player, amount);
            }
            public boolean pay(java.math.BigDecimal amount) {
                if (!economy.available()) { warn("Screen " + screen.id() + ": pay needs Vault with an economy plugin; nothing was paid."); return false; }
                boolean paid = economy.pay(player, amount);
                if (!paid) warn("Screen " + screen.id() + ": the economy refused to pay " + amount.toPlainString() + " to " + player.getName() + ".");
                return paid;
            }
            public void connect(String server) { plugin.connect(player, server); }
            public void input(UiAction.Input input, java.util.function.Consumer<String> submit, Runnable cancel) {
                if (!dialogs.available()) { warn("Text input needs the dialog screen of Minecraft 1.21.6+ / 26.x; the input was cancelled."); cancel.run(); return; }
                dialogs.show(player, TextFormat.parse(input.title()), TextFormat.parse(input.label().isEmpty() ? input.title() : input.label()), input.initial(), input.maxLength(),
                        typed -> plugin.scheduler().entity(player, () -> {
                            if (!input.accepts(typed)) { player.sendMessage(Component.text("That text is not allowed here.")); cancel.run(); return; }
                            var event = new TideframeInputEvent(player, screen.id(), input.variable(), typed);
                            plugin.callEvent(event);
                            if (event.isCancelled()) cancel.run(); else submit.accept(typed);
                        }),
                        () -> plugin.scheduler().entity(player, cancel));
            }
            public void later(int ticks, Runnable task) { plugin.scheduler().later(player, ticks, task); }
            public void warn(String message) { plugin.logger().warning(message); }
            public String placeholders(String text) { return plugin.placeholders(player, text); }
            public void view(UnaryOperator<ViewState> change) {
                if (mode == Mode.MENU) { var session = plugin.session(player); if (session != null && session.screen() == screen) session.updateView(change); }
                else { var overlay = plugin.overlay(player); if (overlay != null) overlay.updateView(screen.id(), change); }
            }
            public ViewState view() {
                if (mode == Mode.MENU) { var session = plugin.session(player); return session != null && session.screen() == screen ? session.view() : ViewState.EMPTY; }
                var overlay = plugin.overlay(player);
                return overlay == null ? ViewState.EMPTY : overlay.view(screen.id());
            }
            public void refreshLive() {
                if (mode == Mode.MENU) { var session = plugin.session(player); if (session != null) session.refreshLiveNow(); }
                else { var overlay = plugin.overlay(player); if (overlay != null) overlay.refreshLiveNow(); }
            }
            public List<Map<String, String>> list(String source) { var known = lists.get(player.getUniqueId()); return known == null ? null : known.get(source); }
            public boolean dropped(String dragged, String target) {
                var event = new TideframeDropEvent(player, screen.id(), dragged, target);
                plugin.callEvent(event);
                return !event.isCancelled();
            }
            public long now() { return System.nanoTime(); }
            public Map<String, Long> cooldowns() { return cooldowns.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>()); }
            public void confirm(UiAction.Confirm confirm, Runnable yes, Runnable no) {
                if (!dialogs.available()) { warn("Screen " + screen.id() + ": confirm needs the dialog screen of Minecraft 1.21.6+ / 26.x; it answered no."); no.run(); return; }
                dialogs.confirm(player, TextFormat.parse(confirm.title()), confirm.message().isEmpty() ? Component.empty() : TextFormat.parse(confirm.message()),
                        TextFormat.parse(confirm.yes()), TextFormat.parse(confirm.no()),
                        () -> plugin.scheduler().entity(player, yes), () -> plugin.scheduler().entity(player, no));
            }
            public int frameCount(String element) { var assets = plugin.assets(); return assets == null ? 0 : assets.frameCount(screen, element); }
            public int frameInterval(String element) {
                var assets = plugin.assets(); var target = screen.element(element).orElse(null);
                return assets == null || target == null || target.logic().frames() == null ? 0 : assets.frameInterval(target);
            }
            public void hud(String target, boolean add) { plugin.hud(player, target, add); }
            public void toast(String target, double seconds, Map<String, String> values) { plugin.toast(player, target, seconds, values); }
            public void variableChanged(String name, String before, String after) {
                if (TideframeVariableChangeEvent.getHandlerList().getRegisteredListeners().length > 0)
                    plugin.callEvent(new TideframeVariableChangeEvent(player, screen.id(), name, before, after));
                // Global variables are shared: the player's other open screens (the menu, other HUD pages) see the change too.
                for (var other : allRuntimes(player)) if (other.screen() != screen) other.externalChange();
            }
        };
    }
    /** Commands come from page text with values inserted: one line, no control characters, at most 256 characters. */
    static String sanitize(String line) {
        var out = new StringBuilder(line.length());
        for (char c : line.toCharArray()) out.append(c < 32 || c == 127 ? ' ' : c);
        String clean = out.toString().trim();
        if (clean.startsWith("/")) clean = clean.substring(1);
        return clean.length() > 256 ? clean.substring(0, 256) : clean;
    }
    private void playSound(Player player, String sound, float volume, float pitch) {
        try {
            if (sound.matches("[A-Z0-9_]+")) {
                Object constant = org.bukkit.Sound.class.getField(sound).get(null);
                player.playSound(player.getLocation(), (org.bukkit.Sound) constant, SoundCategory.MASTER, volume, pitch);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError notAConstant) { /* fall back to the key */ }
        player.playSound(player.getLocation(), sound.toLowerCase(Locale.ROOT), SoundCategory.MASTER, volume, pitch);
    }
    private void teleportPlayer(Player player, String worldName, String x, String y, String z, String yaw, String pitch) {
        var here = player.getLocation();
        var world = worldName.equals("~") ? player.getWorld() : Bukkit.getWorld(worldName);
        if (world == null) { plugin.logger().warning("Teleport: unknown world " + worldName); return; }
        try {
            var target = new Location(world, coordinate(x, here.getX()), coordinate(y, here.getY()), coordinate(z, here.getZ()),
                    yaw == null ? here.getYaw() : (float) coordinate(yaw, here.getYaw()), pitch == null ? here.getPitch() : (float) coordinate(pitch, here.getPitch()));
            plugin.close(player, TideframeCloseEvent.Reason.ACTION);
            player.teleportAsync(target);
        } catch (NumberFormatException invalid) { plugin.logger().warning("Teleport: invalid coordinates " + x + " " + y + " " + z); }
    }
    static double coordinate(String text, double relativeTo) {
        String t = text.trim();
        if (t.startsWith("~")) return relativeTo + (t.length() == 1 ? 0 : Double.parseDouble(t.substring(1)));
        double value = Double.parseDouble(t);
        if (!Double.isFinite(value) || Math.abs(value) > 30_000_000) throw new NumberFormatException("out of range");
        return value;
    }
    private void givePlayer(Player player, String item, int amount) {
        Material material = Material.matchMaterial(item);
        if (material == null || !material.isItem() || material.isAir()) { plugin.logger().warning("Give: unknown item " + item); return; }
        int left = amount;
        while (left > 0) {
            int stack = Math.min(left, material.getMaxStackSize());
            var overflow = player.getInventory().addItem(new ItemStack(material, stack));
            overflow.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            left -= stack;
        }
    }
}
