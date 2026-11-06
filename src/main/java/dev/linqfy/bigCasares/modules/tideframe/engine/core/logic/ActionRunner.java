package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs action lists for one player's open screen, in order, on the player's thread. Delays and text inputs pause a
 * run and resume it later; redirect, back and close end it. Every run is bounded (steps per resume, function depth)
 * so a looping page can never hold the server thread.
 */
public final class ActionRunner {
    public static final int MAX_STEPS = 2_000, MAX_DEPTH = 64;
    /** Largest amount one charge or pay may move. */
    static final java.math.BigDecimal MAX_MONEY = new java.math.BigDecimal("1000000000000");
    /** Proxy server names: what BungeeCord and Velocity accept in their configs. */
    static final Pattern SERVER = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    /** What the actions do in the game. Implemented by the plugin; called on the player's thread. */
    public interface Effects {
        void command(String line);
        void console(String line);
        void message(String text);
        void actionBar(String text);
        void sound(String sound, float volume, float pitch);
        /** Coordinates as text: numbers, or ~ / ~N relative to the player. */
        void teleport(String world, String x, String y, String z, String yaw, String pitch);
        void give(String item, int amount);
        void open(String screen);
        void back();
        void close();
        void visibility(List<String> show, List<String> hide, List<String> toggle);
        void offset(String element, int dx);
        void tint(String element, String color);
        void animate(String element);
        void scroll(String element, int rows, boolean absolute);
        boolean hasPermission(String node);
        /** Takes {@code amount} (positive) from the player's balance when it is enough; true when it was taken. */
        boolean charge(java.math.BigDecimal amount);
        /** Adds {@code amount} (positive) to the player's balance. */
        void pay(java.math.BigDecimal amount);
        /** Closes the menu and moves the player to another server of the proxy. */
        void connect(String server);
        /** Shows the text dialog; exactly one of the callbacks runs later on the player's thread (or none if the screen closed). */
        void input(UiAction.Input input, Consumer<String> submit, Runnable cancel);
        /** Shows the yes/no dialog; exactly one of the callbacks runs later on the player's thread. Without a dialog, {@code no}. */
        void confirm(UiAction.Confirm confirm, Runnable yes, Runnable no);
        /** Pauses, resumes or moves (to {@code frame}, 1 = the first) a PNG-folder or GIF animation. */
        void playback(String element, String command, int frame);
        /** Adds HUD page {@code screen} over the shown ones, or takes it away. */
        void hud(String screen, boolean add);
        /** Shows HUD page {@code screen} as a toast for {@code seconds}, with {@code values} as its variables. */
        void toast(String screen, double seconds, Map<String, String> values);
        void variablesChanged();
        /** Runs {@code resume} on the player's thread after {@code ticks}. */
        void later(int ticks, Runnable resume);
        /** False once the screen session closed: pending runs stop. */
        boolean alive();
        void warn(String message);
    }

    private final Map<String, ScreenLogic.Function> functions;
    private final Variables variables;
    private final UnaryOperator<String> placeholders;
    private final Effects effects;

    /** {@code placeholders} resolves %placeholders% and built-in {tokens}; it runs before variables are inserted (see {@link #resolve}). */
    public ActionRunner(Map<String, ScreenLogic.Function> functions, Variables variables, UnaryOperator<String> placeholders, Effects effects) {
        this.functions = Map.copyOf(functions); this.variables = Objects.requireNonNull(variables);
        this.placeholders = Objects.requireNonNull(placeholders); this.effects = Objects.requireNonNull(effects);
    }

    public Variables variables() { return variables; }

    /** Starts a run; {@code scope} adds read-only tokens such as {item} and {index} (list rows). */
    public void run(List<UiAction> actions) { run(actions, Map.of()); }
    public void run(List<UiAction> actions, Map<String, String> scope) {
        if (actions.isEmpty()) return;
        var execution = new Execution();
        execution.stack.push(new Frame(actions, Map.copyOf(scope)));
        execution.resume();
    }

    /**
     * Text as the action will see it. Placeholders and clamps are resolved on the page's own text first; then
     * {tokens} take scope values (function parameters, list rows) or variables. Values are inserted last and never
     * resolved again, so a player who types "%vault_eco_balance%" into an input gets exactly that text back.
     */
    public String resolve(String text) { return resolve(text, Map.of()); }
    String resolve(String text, Map<String, String> scope) {
        // Calculated tokens ({coins * 2}) take their operands from the same places; the rest of the text as before.
        return Expressions.render(text, part -> substitute(placeholders.apply(Conditions.clamp(part, placeholders)), scope, variables),
                name -> name(name, scope));
    }
    /** An expression operand: a %placeholder%, else a built-in {token}, a scope value or a variable ($name: global only). */
    private String name(String name, Map<String, String> scope) {
        if (name.startsWith("%")) {
            String placed = placeholders.apply(Conditions.clamp(name, placeholders));
            return placed.equals(name) ? null : placed;
        }
        if (name.startsWith("$")) return name.indexOf('.') < 0 ? variables.get(name) : null;
        String token = "{" + name + "}", builtIn = placeholders.apply(token);
        if (!builtIn.equals(token)) return builtIn;
        if (scope.containsKey(name)) return scope.get(name);
        return name.indexOf('.') < 0 ? variables.get(name) : null;
    }
    private static final Pattern TOKEN = Pattern.compile("(\\$?)\\{([a-z_][a-z0-9_.]{0,47})}");
    /** {name}: scope first, then variables; ${name}: variables only; unknown tokens stay as written. */
    public static String substitute(String text, Map<String, String> scope, Variables variables) {
        if (text.indexOf('{') < 0) return text;
        Matcher m = TOKEN.matcher(text);
        var out = new StringBuilder();
        while (m.find()) {
            String name = m.group(2), value = null;
            if (m.group(1).isEmpty()) value = scope.get(name);
            if (value == null && name.indexOf('.') < 0) value = variables.get(name);
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? m.group() : value));
        }
        return m.appendTail(out).toString();
    }

    /** {@code text} in at most {@code max} characters, control characters (but line feeds) as spaces. */
    static String fit(String text, int max) {
        var out = new StringBuilder(Math.min(text.length(), max));
        for (int i = 0; i < text.length() && out.length() < max; i++) { char c = text.charAt(i); out.append(c < 32 && c != '\n' ? ' ' : c); }
        return out.toString();
    }
    private record Frame(List<UiAction> actions, int[] index, Map<String, String> scope) {
        Frame(List<UiAction> actions, Map<String, String> scope) { this(actions, new int[1], scope); }
        boolean done() { return index[0] >= actions.size(); }
        UiAction next() { return actions.get(index[0]++); }
    }

    private final class Execution {
        final Deque<Frame> stack = new ArrayDeque<>();
        boolean paused;

        void resume() {
            paused = false;
            int steps = 0;
            while (!stack.isEmpty() && !paused) {
                if (!effects.alive()) { stack.clear(); return; }
                Frame frame = stack.peek();
                if (frame.done()) { stack.pop(); continue; }
                if (++steps > MAX_STEPS) { effects.warn("Stopped an action list after " + MAX_STEPS + " steps without a delay (endless loop?)"); stack.clear(); return; }
                scope = frame.scope();
                step(frame.next());
            }
        }
        private Map<String, String> scope = Map.of();
        private String text(String value) { return resolve(value, scope); }
        private int number(String value, int fallback, int min, int max) {
            String resolved = text(value).trim();
            try {
                double parsed = Double.parseDouble(resolved);
                if (!Double.isFinite(parsed)) return fallback;
                return (int) Math.max(min, Math.min(max, Math.round(parsed)));
            } catch (NumberFormatException notNumber) { return fallback; }
        }
        private void step(UiAction action) {
            switch (action) {
                case UiAction.Command c -> effects.command(text(c.line()));
                case UiAction.Console c -> effects.console(text(c.line()));
                case UiAction.Message m -> effects.message(text(m.text()));
                case UiAction.ActionBar a -> effects.actionBar(text(a.text()));
                case UiAction.Sound s -> effects.sound(text(s.sound()), s.volume(), s.pitch());
                case UiAction.Teleport t -> effects.teleport(text(t.world()), text(t.x()), text(t.y()), text(t.z()),
                        t.yaw() == null ? null : text(t.yaw()), t.pitch() == null ? null : text(t.pitch()));
                case UiAction.Delay d -> {
                    if (d.ticks() <= 0) return;
                    paused = true;
                    effects.later(d.ticks(), this::resume);
                }
                case UiAction.Give g -> effects.give(text(g.item()), number(g.amount(), 1, 1, 2304));
                case UiAction.Open o -> { stack.clear(); effects.open(text(o.screen())); }
                case UiAction.Back b -> { stack.clear(); effects.back(); }
                case UiAction.Close c -> { stack.clear(); effects.close(); }
                case UiAction.Visibility v -> effects.visibility(v.show(), v.hide(), v.toggle());
                case UiAction.SetVariable s -> { if (variables.apply(s.name(), s.operation(), text(s.value()))) effects.variablesChanged(); }
                case UiAction.Condition c -> branch(Conditions.test(c.expression(), this::text) ? c.then() : c.otherwise());
                case UiAction.Permission p -> branch(effects.hasPermission(p.permission()) ? p.then() : p.otherwise());
                case UiAction.Call c -> call(c);
                case UiAction.Input i -> {
                    paused = true;
                    // What the dialog shows, cut to its limits; the branches run later from the action itself.
                    var rendered = new UiAction.Input(fit(text(i.title()), 64), fit(text(i.label()), 64), fit(text(i.initial()), 256), i.variable(),
                            i.maxLength(), i.filter(), i.allowEmpty(), List.of(), List.of());
                    var answer = new Answer();
                    effects.input(rendered, typed -> answer.give(() -> {
                        if (!i.accepts(typed)) { branch(i.onCancel()); return; }
                        if (variables.apply(i.variable(), "set", typed)) effects.variablesChanged();
                        branch(i.onSubmit());
                    }), () -> answer.give(() -> branch(i.onCancel())));
                    answer.asked();
                }
                case UiAction.Offset o -> effects.offset(o.element(), number(o.dx(), 0, -512, 512));
                case UiAction.Tint t -> effects.tint(t.element(), t.color() == null ? null : text(t.color()));
                case UiAction.Animate a -> effects.animate(a.element());
                case UiAction.Scroll s -> effects.scroll(s.element(), number(s.rows(), 0, -10_000, 10_000), s.absolute());
                case UiAction.Charge c -> {
                    var amount = money(c.amount());
                    if (amount == null) { effects.warn("charge: '" + text(c.amount()) + "' is not a positive amount; nothing was charged"); branch(c.otherwise()); return; }
                    branch(effects.charge(amount) ? c.then() : c.otherwise());
                }
                case UiAction.Pay p -> {
                    var amount = money(p.amount());
                    if (amount == null) effects.warn("pay: '" + text(p.amount()) + "' is not a positive amount; nothing was paid");
                    else effects.pay(amount);
                }
                case UiAction.Connect c -> {
                    String server = text(c.server()).trim();
                    if (!SERVER.matcher(server).matches()) { effects.warn("server: '" + server + "' is not a server name (A-Z, 0-9, _ . -)"); return; }
                    stack.clear(); effects.connect(server);
                }
                case UiAction.Confirm c -> {
                    paused = true;
                    var rendered = new UiAction.Confirm(fit(text(c.title()), 64), fit(text(c.message()), 256), fit(text(c.yes()), 32), fit(text(c.no()), 32), List.of(), List.of());
                    var answer = new Answer();
                    effects.confirm(rendered, () -> answer.give(() -> branch(c.then())), () -> answer.give(() -> branch(c.otherwise())));
                    answer.asked();
                }
                case UiAction.Playback p -> effects.playback(p.element(), p.command(), p.command().equals("frame") ? number(p.frame(), 1, 1, 100_000) : 0);
                case UiAction.Hud h -> effects.hud(text(h.screen()).trim(), h.add());
                case UiAction.Toast t -> {
                    var values = new LinkedHashMap<String, String>();
                    t.values().forEach((name, value) -> values.put(name, text(value)));
                    double seconds;
                    try { seconds = Double.parseDouble(text(t.seconds()).trim()); } catch (NumberFormatException notNumber) { seconds = 4; }
                    effects.toast(text(t.screen()).trim(), Double.isFinite(seconds) ? Math.max(1, Math.min(60, seconds)) : 4, values);
                }
            }
        }
        /** A positive amount of money (at most two decimals kept), or null. */
        private java.math.BigDecimal money(String value) {
            var parsed = Variables.number(text(value));
            if (parsed == null || parsed.signum() <= 0 || parsed.compareTo(MAX_MONEY) > 0) return null;
            return parsed.setScale(2, java.math.RoundingMode.DOWN);
        }
        /**
         * One answer to a dialog: the first one counts, and only while the page is still open (an answer that arrives
         * after it closed changes nothing). An answer given at once, while the dialog is being asked (no dialog to show,
         * or the closing rules answering no), lets the running loop go on within its own step budget; a later one
         * resumes the run.
         */
        private final class Answer {
            private boolean answered, asking = true;
            void give(Runnable chosen) {
                if (answered) return;
                answered = true;
                if (!effects.alive()) { stack.clear(); return; }
                chosen.run();
                if (asking) paused = false; else resume();
            }
            void asked() { asking = false; }
        }
        private void branch(List<UiAction> actions) { push(actions, scope); }
        private void push(List<UiAction> actions, Map<String, String> frameScope) {
            if (actions.isEmpty()) return;
            // A call or branch at the end of a list replaces it (tail call): "repeat after a delay" loops never grow.
            while (!stack.isEmpty() && stack.peek().done()) stack.pop();
            if (stack.size() >= MAX_DEPTH) { effects.warn("Actions nested deeper than " + MAX_DEPTH + " (recursive function without a delay?)"); stack.clear(); return; }
            stack.push(new Frame(actions, frameScope));
        }
        private void call(UiAction.Call call) {
            var function = functions.get(call.function());
            if (function == null) {
                effects.warn("Unknown function '" + call.function() + "'; this screen defines " + functions.keySet());
                return;
            }
            // Parameters live in the body's scope: defaults, overridden by the call's arguments, resolved once here.
            // Each value is kept to a variable's length: a function that calls itself with "{x}{x}" cannot grow it forever.
            var values = new LinkedHashMap<>(scope);
            function.params().forEach((name, value) -> values.put(name, fit(text(value), Variables.MAX_VALUE)));
            call.arguments().forEach((name, value) -> values.put(name, fit(text(value), Variables.MAX_VALUE)));
            push(function.actions(), Map.copyOf(values));
        }
    }
}
