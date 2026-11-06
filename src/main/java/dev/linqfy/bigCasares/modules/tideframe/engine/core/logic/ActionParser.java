package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads actions and screen/element logic from parsed JSON (maps, lists, strings, BigDecimal numbers, booleans).
 * Keys follow Ultimate UI's YAML where it makes sense ({@code type}/{@code value}, {@code max-length}, {@code on-submit},
 * {@code then: {actions: [...]}}), so pages translate one to one; camelCase spellings are accepted too.
 */
public final class ActionParser {
    private ActionParser() { }
    private static final int MAX_DEPTH = 8, MAX_ACTIONS = 128;

    public static List<UiAction> actions(Object value) { return actions(value, 0); }
    private static List<UiAction> actions(Object value, int depth) {
        if (value == null) return List.of();
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Actions are nested more than " + MAX_DEPTH + " levels");
        if (value instanceof Map<?, ?> map && map.containsKey("actions") && !map.containsKey("type")) return actions(map.get("actions"), depth);
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Expected a list of actions");
        if (list.size() > MAX_ACTIONS) throw new IllegalArgumentException("At most " + MAX_ACTIONS + " actions in one list");
        var out = new ArrayList<UiAction>();
        for (Object item : list) out.add(action(item, depth));
        return List.copyOf(out);
    }

    public static UiAction action(Object value) { return action(value, 0); }
    private static UiAction action(Object value, int depth) {
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Each action must be an object with a type");
        Map<String, Object> m = keys(raw);
        String type = text(m, "type", null);
        if (type == null) throw new IllegalArgumentException("Action without type");
        type = type.toLowerCase(Locale.ROOT);
        String v = text(m, "value", "");
        return switch (type) {
            case "command", "player" -> new UiAction.Command(stripSlash(v));
            case "console" -> new UiAction.Console(stripSlash(v));
            case "message" -> new UiAction.Message(v);
            case "actionbar", "action-bar" -> new UiAction.ActionBar(v);
            case "sound" -> sound(m, v);
            case "teleport" -> teleport(m, v);
            case "delay", "wait" -> new UiAction.Delay(ticks(m.containsKey("ticks") ? text(m, "ticks", "0") : v));
            case "give" -> give(m, v);
            case "redirect", "open" -> new UiAction.Open(first(m, v, "screen", "page"));
            case "back" -> new UiAction.Back();
            case "close" -> new UiAction.Close();
            case "show" -> new UiAction.Visibility(ids(m, v, "elements", "element"), List.of(), List.of());
            case "hide" -> new UiAction.Visibility(List.of(), ids(m, v, "elements", "element"), List.of());
            case "toggle" -> {
                var show = idList(m.get("show")); var hide = idList(m.get("hide"));
                boolean flips = m.get("elements") != null || m.get("element") != null || !v.isBlank();
                yield new UiAction.Visibility(show, hide, flips || show.isEmpty() && hide.isEmpty() ? ids(m, v, "elements", "element") : List.of());
            }
            case "visibility" -> {
                var ids = ids(m, "", "elements", "element");
                String mode = v.toLowerCase(Locale.ROOT).trim();
                yield switch (mode) {
                    case "show", "true", "visible" -> new UiAction.Visibility(ids, List.of(), List.of());
                    case "hide", "false", "hidden" -> new UiAction.Visibility(List.of(), ids, List.of());
                    case "toggle" -> new UiAction.Visibility(List.of(), List.of(), ids);
                    default -> throw new IllegalArgumentException("visibility value must be show, hide or toggle");
                };
            }
            case "variable", "set" -> new UiAction.SetVariable(first(m, "", "variable", "name"),
                    text(m, "operation", "set").toLowerCase(Locale.ROOT), v);
            case "condition", "if" -> new UiAction.Condition(v.isEmpty() ? text(m, "condition", "") : v,
                    actions(m.get("then"), depth + 1), actions(m.get("else"), depth + 1));
            case "permission" -> new UiAction.Permission(first(m, v, "permission"), actions(m.get("then"), depth + 1), actions(m.get("else"), depth + 1));
            case "function", "call" -> new UiAction.Call(first(m, v, "function", "name"), arguments(m));
            case "input" -> input(m, depth);
            case "offset", "move" -> new UiAction.Offset(first(m, "", "element"), text(m, "dx", v.isEmpty() ? "0" : v));
            case "tint", "color" -> new UiAction.Tint(first(m, "", "element"), v.isEmpty() ? text(m, "color", "none") : v);
            case "animation", "animate" -> new UiAction.Animate(first(m, v, "element"));
            case "scroll" -> new UiAction.Scroll(first(m, "", "element"), text(m, "rows", v.isEmpty() ? "1" : v), bool(m, "absolute", false));
            case "charge", "withdraw" -> new UiAction.Charge(first(m, v, "amount"), actions(m.get("then"), depth + 1), actions(m.get("else"), depth + 1));
            case "pay", "deposit" -> new UiAction.Pay(first(m, v, "amount"));
            case "server", "connect" -> new UiAction.Connect(first(m, v, "server"));
            case "confirm" -> new UiAction.Confirm(text(m, "title", "Are you sure?"), v.isEmpty() ? text(m, "message", "") : v,
                    text(m, "yes", "Yes"), text(m, "no", "No"), actions(m.get("then"), depth + 1), actions(m.get("else"), depth + 1));
            case "pause", "resume" -> new UiAction.Playback(first(m, v, "element"), type, "");
            case "hud" -> {
                String mode = text(m, "mode", "add").toLowerCase(Locale.ROOT);
                if (!mode.equals("add") && !mode.equals("remove")) throw new IllegalArgumentException("hud mode must be add or remove");
                yield new UiAction.Hud(first(m, v, "screen", "page"), mode.equals("add"));
            }
            case "toast" -> {
                var values = new LinkedHashMap<String, String>();
                if (m.get("values") != null) object(m.get("values"), "toast values").forEach((k, given) -> values.put(String.valueOf(k), scalar(given, "toast value " + k)));
                yield new UiAction.Toast(first(m, v, "screen", "page"), text(m, "seconds", "4"), values);
            }
            case "frame" -> new UiAction.Playback(first(m, "", "element"), "frame", m.get("frame") != null ? text(m, "frame", "1") : v.isEmpty() ? "1" : v);
            default -> throw new IllegalArgumentException("Unknown action type: " + type
                    + " (command, console, message, actionbar, sound, teleport, delay, give, redirect, back, close, show, hide, toggle,"
                    + " visibility, variable, condition, permission, function, input, offset, tint, animation, scroll, charge, pay, server,"
                    + " confirm, pause, resume, frame, hud, toast)");
        };
    }

    public static ScreenLogic screen(Object value) {
        if (value == null) return ScreenLogic.EMPTY;
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Screen logic must be an object");
        Map<String, Object> m = keys(raw);
        allow(m, "screen logic", Set.of("behavior", "variables", "functions", "keybinds", "command", "permission", "open-on-join", "on-open", "on-close", "animation",
                "timers", "on-damage", "on-world-change", "on-change"));
        var b = m.get("behavior") == null ? Map.<String, Object>of() : keys(object(m.get("behavior"), "behavior"));
        allow(b, "behavior", Set.of("close-on-sneak", "close-on-damage", "close-on-f", "close-on-shift-f", "reopen", "close-on-death", "keep-open", "keyboard-navigation"));
        var behavior = new ScreenLogic.Behavior(bool(b, "close-on-sneak", true), bool(b, "close-on-damage", false),
                bool(b, "close-on-f", false), bool(b, "close-on-shift-f", false), bool(b, "reopen", false), bool(b, "keyboard-navigation", false));
        var variables = new LinkedHashMap<String, String>();
        if (m.get("variables") != null) object(m.get("variables"), "variables").forEach((k, v) -> variables.put(String.valueOf(k), scalar(v, "variable " + k)));
        var functions = new LinkedHashMap<String, ScreenLogic.Function>();
        if (m.get("functions") != null) object(m.get("functions"), "functions").forEach((k, v) -> functions.put(String.valueOf(k), function(v)));
        var keybinds = new LinkedHashMap<String, List<UiAction>>();
        Object binds = m.get("keybinds");
        if (binds instanceof List<?> list) for (Object item : list) {
            var bind = keys(object(item, "keybind"));
            String key = text(bind, "key", "").toUpperCase(Locale.ROOT).replace("SHIFT+", "SHIFT_").replace(' ', '_');
            keybinds.put(key, actions(bind.get("actions")));
        } else if (binds instanceof Map<?, ?> map) map.forEach((k, v) -> keybinds.put(String.valueOf(k).toUpperCase(Locale.ROOT), actions(v)));
        else if (binds != null) throw new IllegalArgumentException("keybinds must be a list of {key, actions}");
        var timers = new ArrayList<ScreenLogic.Timer>();
        if (m.get("timers") != null) {
            if (!(m.get("timers") instanceof List<?> list)) throw new IllegalArgumentException("timers must be a list of {every, actions}");
            for (Object item : list) {
                var timer = keys(object(item, "timer"));
                allow(timer, "timer", Set.of("every", "actions"));
                if (timer.get("every") == null) throw new IllegalArgumentException("A timer needs every (\"5s\", \"500ms\" or ticks)");
                timers.add(new ScreenLogic.Timer(ticks(text(timer, "every", "")), actions(timer.get("actions"))));
            }
        }
        var changes = new LinkedHashMap<String, List<UiAction>>();
        if (m.get("on-change") != null) object(m.get("on-change"), "on-change").forEach((k, v) -> changes.put(String.valueOf(k), actions(v)));
        return new ScreenLogic(behavior, variables, functions, keybinds, nullable(m, "command"), nullable(m, "permission"),
                bool(m, "open-on-join", false), actions(m.get("on-open")), actions(m.get("on-close")), animation(m.get("animation")),
                timers, actions(m.get("on-damage")), actions(m.get("on-world-change")), changes);
    }
    /** {"open": {effect, duration-ms, easing, from, distance}, "close": {...}, "close-reversed": true}. */
    static ScreenLogic.Animation animation(Object value) {
        if (value == null) return ScreenLogic.Animation.DEFAULT;
        var m = keys(object(value, "animation"));
        allow(m, "animation", Set.of("open", "close", "close-reversed"));
        var open = m.get("open") == null ? ScreenLogic.Animation.DEFAULT.open() : transition(m.get("open"), ScreenLogic.Animation.DEFAULT.open());
        ScreenLogic.Transition close;
        if (bool(m, "close-reversed", false)) close = open.effect().equals("none") ? ScreenLogic.Transition.NONE : ScreenLogic.Transition.fade(open.durationMs());
        else close = m.get("close") == null ? ScreenLogic.Transition.NONE : transition(m.get("close"), ScreenLogic.Transition.fade(200));
        return new ScreenLogic.Animation(open, close);
    }
    private static ScreenLogic.Transition transition(Object value, ScreenLogic.Transition defaults) {
        var t = keys(object(value, "transition"));
        allow(t, "transition", Set.of("effect", "duration-ms", "easing", "from", "distance"));
        return new ScreenLogic.Transition(text(t, "effect", defaults.effect()).toLowerCase(Locale.ROOT), integer(t, "duration-ms", defaults.durationMs()),
                text(t, "easing", defaults.easing()).toLowerCase(Locale.ROOT), text(t, "from", defaults.from()).toLowerCase(Locale.ROOT), integer(t, "distance", defaults.distance()));
    }

    public static ElementLogic element(Object value) {
        if (value == null) return ElementLogic.EMPTY;
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Element logic must be an object");
        Map<String, Object> m = keys(raw);
        allow(m, "element logic", Set.of("actions", "right-actions", "hidden", "visible-if", "frames", "list", "draggable", "on-drop",
                "on-hover", "on-leave", "tooltip", "keyframes", "cooldown", "on-cooldown"));
        ElementLogic.Frames frames = null;
        if (m.get("frames") != null) {
            var f = keys(object(m.get("frames"), "frames"));
            frames = new ElementLogic.Frames(text(f, "folder", ""), integer(f, "interval-ms", ElementLogic.Frames.AUTO), bool(f, "loop", true));
        }
        ElementLogic.ListSpec list = null;
        if (m.get("list") != null) {
            var l = keys(object(m.get("list"), "list"));
            list = new ElementLogic.ListSpec(text(l, "source", ""), integer(l, "rows", 5), integer(l, "spacing", 16), nullable(l, "filter"), actions(l.get("on-select")));
        }
        return new ElementLogic(actions(m.get("actions")), actions(m.get("right-actions")), bool(m, "hidden", false),
                nullable(m, "visible-if"), frames, list, bool(m, "draggable", false), actions(m.get("on-drop")),
                actions(m.get("on-hover")), actions(m.get("on-leave")), nullable(m, "tooltip"), motion(m.get("keyframes")),
                m.get("cooldown") == null ? 0 : millis(text(m, "cooldown", "0")), actions(m.get("on-cooldown")));
    }
    /** {"trigger": "open|loop|hover|action", "easing": "...", "steps": [{"at": ms, "x", "y", "scale", "opacity", "rotation"}, ...]}. */
    static Motion motion(Object value) {
        if (value == null) return null;
        var m = keys(object(value, "keyframes"));
        allow(m, "keyframes", Set.of("trigger", "easing", "steps"));
        if (!(m.get("steps") instanceof List<?> list)) throw new IllegalArgumentException("keyframes need a list of steps");
        var steps = new ArrayList<Motion.Step>();
        for (Object item : list) {
            var step = keys(object(item, "keyframe"));
            allow(step, "keyframe", Set.of("at", "x", "y", "scale", "opacity", "rotation"));
            steps.add(new Motion.Step(integer(step, "at", 0), optionalNumber(step, "x"), optionalNumber(step, "y"), optionalNumber(step, "scale"),
                    optionalNumber(step, "opacity"), optionalNumber(step, "rotation")));
        }
        return new Motion(text(m, "trigger", "open").toLowerCase(Locale.ROOT), text(m, "easing", "ease-in-out").toLowerCase(Locale.ROOT), steps);
    }
    private static Double optionalNumber(Map<String, Object> m, String key) { return m.get(key) == null ? null : number(m, key, 0); }

    private static ScreenLogic.Function function(Object value) {
        var m = keys(object(value, "function"));
        var params = new LinkedHashMap<String, String>();
        Object p = m.get("params");
        if (p instanceof Map<?, ?> map) map.forEach((k, v) -> params.put(String.valueOf(k), scalar(v, "parameter " + k)));
        else if (p instanceof List<?> list) for (Object name : list) params.put(String.valueOf(name), "");
        else if (p != null) throw new IllegalArgumentException("Function params must be a map of defaults or a list of names");
        return new ScreenLogic.Function(params, actions(m.get("actions")));
    }
    private static UiAction sound(Map<String, Object> m, String v) {
        String sound = text(m, "sound", null);
        float volume = 1, pitch = 1;
        if (sound == null) {
            // "entity.player.levelup 1,2" (Ultimate UI) or "ENTITY_PLAYER_LEVELUP 1 1".
            String[] parts = v.trim().split("[\\s,]+");
            if (parts.length == 0 || parts[0].isEmpty()) throw new IllegalArgumentException("Sound action needs a sound");
            sound = parts[0];
            if (parts.length > 1) volume = Float.parseFloat(parts[1]);
            if (parts.length > 2) pitch = Float.parseFloat(parts[2]);
        } else {
            volume = (float) number(m, "volume", 1);
            pitch = (float) number(m, "pitch", 1);
        }
        return new UiAction.Sound(sound, volume, pitch);
    }
    private static UiAction teleport(Map<String, Object> m, String v) {
        if (m.containsKey("x")) return new UiAction.Teleport(text(m, "world", "world"), text(m, "x", "0"), text(m, "y", "0"), text(m, "z", "0"),
                nullable(m, "yaw"), nullable(m, "pitch"));
        // "world_nether 0 64 0 [yaw pitch]" or "world:example x:0 y:64 z:0".
        var named = new LinkedHashMap<String, String>();
        var positional = new ArrayList<String>();
        for (String part : v.trim().split("\\s+")) {
            int colon = part.indexOf(':');
            if (colon > 0 && part.substring(0, colon).matches("world|x|y|z|yaw|pitch")) named.put(part.substring(0, colon), part.substring(colon + 1));
            else if (!part.isEmpty()) positional.add(part);
        }
        if (!named.isEmpty()) return new UiAction.Teleport(named.getOrDefault("world", "world"), named.getOrDefault("x", "0"), named.getOrDefault("y", "0"),
                named.getOrDefault("z", "0"), named.get("yaw"), named.get("pitch"));
        if (positional.size() != 4 && positional.size() != 6) throw new IllegalArgumentException("Teleport needs: world x y z [yaw pitch]");
        return new UiAction.Teleport(positional.get(0), positional.get(1), positional.get(2), positional.get(3),
                positional.size() == 6 ? positional.get(4) : null, positional.size() == 6 ? positional.get(5) : null);
    }
    private static UiAction give(Map<String, Object> m, String v) {
        if (m.containsKey("item")) return new UiAction.Give(text(m, "item", ""), text(m, "amount", "1"));
        String[] parts = v.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) throw new IllegalArgumentException("Give needs an item");
        return new UiAction.Give(parts[0], parts.length > 1 ? parts[1] : "1");
    }
    private static UiAction input(Map<String, Object> m, int depth) {
        String variable = nullable(m, "variable");
        String id = nullable(m, "id");
        if (variable == null) variable = id;
        if (variable == null) throw new IllegalArgumentException("Input needs a variable (or an id)");
        return new UiAction.Input(text(m, "title", "Enter text"), text(m, "label", text(m, "placeholder", "")), text(m, "default", text(m, "initial", "")),
                variable, integer(m, "max-length", 50), text(m, "filter", "any"), bool(m, "allow-empty", true),
                actions(m.containsKey("on-submit") ? m.get("on-submit") : m.get("actions"), depth + 1), actions(m.get("on-cancel"), depth + 1));
    }
    /** A cooldown in milliseconds, written like a delay: "2s", "500ms", "20t" or a plain number of ticks. */
    static int millis(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        double value;
        try {
            if (t.endsWith("ms")) value = Double.parseDouble(t.substring(0, t.length() - 2));
            else if (t.endsWith("s")) value = Double.parseDouble(t.substring(0, t.length() - 1)) * 1000;
            else if (t.endsWith("t")) value = Double.parseDouble(t.substring(0, t.length() - 1)) * 50;
            else value = Double.parseDouble(t) * 50;
        } catch (NumberFormatException bad) { throw new IllegalArgumentException("Invalid cooldown " + text + " (\"2s\", \"500ms\" or ticks)"); }
        if (!Double.isFinite(value) || value < 0 || value > ElementLogic.MAX_COOLDOWN_MS) throw new IllegalArgumentException("A cooldown must be 0 to 3600 seconds");
        return (int) Math.round(value);
    }
    /** Ultimate UI delays: "20t" ticks, "1s"/"1.5s" seconds, "500ms", or a plain number of ticks. */
    static int ticks(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        double value;
        if (t.endsWith("ms")) value = Double.parseDouble(t.substring(0, t.length() - 2)) / 50.0;
        else if (t.endsWith("s")) value = Double.parseDouble(t.substring(0, t.length() - 1)) * 20.0;
        else if (t.endsWith("t")) value = Double.parseDouble(t.substring(0, t.length() - 1));
        else value = Double.parseDouble(t);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Invalid delay " + text);
        return (int) Math.round(value);
    }
    private static Map<String, String> arguments(Map<String, Object> m) {
        Object args = m.containsKey("arguments") ? m.get("arguments") : m.get("args");
        var out = new LinkedHashMap<String, String>();
        if (args != null) object(args, "arguments").forEach((k, v) -> out.put(String.valueOf(k), scalar(v, "argument " + k)));
        return out;
    }
    private static List<String> ids(Map<String, Object> m, String value, String listKey, String singleKey) {
        if (m.get(listKey) != null) return idList(m.get(listKey));
        if (m.get(singleKey) != null) return List.of(String.valueOf(m.get(singleKey)));
        if (!value.isBlank()) return List.of(value.split("\\s*,\\s*"));
        throw new IllegalArgumentException("Name the elements: " + listKey + ": [ids]");
    }
    private static List<String> idList(Object value) {
        if (value == null) return List.of();
        if (value instanceof String s) return s.isBlank() ? List.of() : List.of(s.trim().split("\\s*,\\s*"));
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Expected a list of element ids");
        return list.stream().map(String::valueOf).toList();
    }

    /** Ultimate UI writes kebab-case; accept camelCase too ("maxLength" == "max-length"). */
    private static Map<String, Object> keys(Map<?, ?> raw) {
        var out = new LinkedHashMap<String, Object>();
        raw.forEach((k, v) -> {
            String key = String.valueOf(k).replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT).replace('_', '-');
            if (out.put(key, v) != null) throw new IllegalArgumentException("Duplicate key " + key);
        });
        return out;
    }
    private static void allow(Map<String, Object> m, String what, Set<String> allowed) {
        for (String key : m.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown " + what + " key: " + key);
    }
    private static Map<?, ?> object(Object value, String what) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(what + " must be an object");
        return map;
    }
    private static String text(Map<String, Object> m, String key, String fallback) {
        Object value = m.get(key);
        if (value == null) return fallback;
        return scalar(value, key);
    }
    private static String nullable(Map<String, Object> m, String key) { return text(m, key, null); }
    private static String first(Map<String, Object> m, String fallback, String... keys) {
        for (String key : keys) if (m.get(key) != null) return text(m, key, fallback);
        if (fallback == null || fallback.isBlank()) throw new IllegalArgumentException("Missing " + keys[0]);
        return fallback;
    }
    private static String scalar(Object value, String what) {
        if (value instanceof String s) return s;
        if (value instanceof BigDecimal n) return n.stripTrailingZeros().scale() <= 0 ? n.toBigInteger().toString() : n.toPlainString();
        if (value instanceof Number n) return String.valueOf(n);
        if (value instanceof Boolean b) return String.valueOf(b);
        throw new IllegalArgumentException("Expected text or a number for " + what);
    }
    private static boolean bool(Map<String, Object> m, String key, boolean fallback) {
        Object value = m.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean b) return b;
        throw new IllegalArgumentException("Expected true or false for " + key);
    }
    private static int integer(Map<String, Object> m, String key, int fallback) {
        Object value = m.get(key);
        if (value == null) return fallback;
        if (value instanceof BigDecimal n) return n.intValueExact();
        if (value instanceof Number n) return n.intValue();
        throw new IllegalArgumentException("Expected a whole number for " + key);
    }
    private static double number(Map<String, Object> m, String key, double fallback) {
        Object value = m.get(key);
        if (value == null) return fallback;
        if (value instanceof Number n) return n.doubleValue();
        throw new IllegalArgumentException("Expected a number for " + key);
    }
    private static String stripSlash(String line) { return line.startsWith("/") ? line.substring(1) : line; }
}
