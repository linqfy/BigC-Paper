package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * One step of a button, keybind or function: what Tideframe does, in order. Immutable and validated; strings may
 * hold {variables}, ${variables}, {params} and %placeholders%, resolved only when the step runs.
 */
public sealed interface UiAction {
    /** Same action with every free text passed through {@code text} (function parameters, imports). */
    UiAction mapText(UnaryOperator<String> text);

    /** Runs as the player, with their own permissions. */
    record Command(String line) implements UiAction {
        public Command { line = clean(line, 256, "command"); }
        public UiAction mapText(UnaryOperator<String> t) { return new Command(t.apply(line)); }
    }
    /** Runs from the console; allowed only when config.yml allows it (actions.console). */
    record Console(String line) implements UiAction {
        public Console { line = clean(line, 256, "console command"); }
        public UiAction mapText(UnaryOperator<String> t) { return new Console(t.apply(line)); }
    }
    /** Chat message; MiniMessage (&lt;gold&gt;) or legacy &amp; colours. */
    record Message(String text) implements UiAction {
        public Message { text = clean(text, 512, "message"); }
        public UiAction mapText(UnaryOperator<String> t) { return new Message(t.apply(text)); }
    }
    record ActionBar(String text) implements UiAction {
        public ActionBar { text = clean(text, 256, "action bar"); }
        public UiAction mapText(UnaryOperator<String> t) { return new ActionBar(t.apply(text)); }
    }
    /** Sound key (minecraft:entity.player.levelup or ENTITY_PLAYER_LEVELUP), volume 0..10, pitch 0.5..2. */
    record Sound(String sound, float volume, float pitch) implements UiAction {
        public Sound {
            sound = clean(sound, 128, "sound");
            if (!Float.isFinite(volume) || volume < 0 || volume > 10 || !Float.isFinite(pitch) || pitch < 0 || pitch > 2)
                throw new IllegalArgumentException("Sound volume must be 0-10 and pitch 0-2");
        }
        public UiAction mapText(UnaryOperator<String> t) { return new Sound(t.apply(sound), volume, pitch); }
    }
    /** Coordinates stay text until the step runs, so {variables} and %placeholders% work in them. */
    record Teleport(String world, String x, String y, String z, String yaw, String pitch) implements UiAction {
        public Teleport {
            world = clean(world, 64, "teleport world"); x = clean(x, 64, "teleport x"); y = clean(y, 64, "teleport y"); z = clean(z, 64, "teleport z");
            yaw = yaw == null ? null : clean(yaw, 64, "teleport yaw"); pitch = pitch == null ? null : clean(pitch, 64, "teleport pitch");
        }
        public UiAction mapText(UnaryOperator<String> t) {
            return new Teleport(t.apply(world), t.apply(x), t.apply(y), t.apply(z), yaw == null ? null : t.apply(yaw), pitch == null ? null : t.apply(pitch));
        }
    }
    /** Waits before the next step; 1 tick = 50 ms, at most 10 minutes. */
    record Delay(int ticks) implements UiAction {
        public Delay { if (ticks < 0 || ticks > 12_000) throw new IllegalArgumentException("Delay must be 0 to 12000 ticks (10 minutes)"); }
        public UiAction mapText(UnaryOperator<String> t) { return this; }
    }
    /** Gives an item (material name, optionally namespaced) to the player; the amount may be a {variable}. */
    record Give(String item, String amount) implements UiAction {
        public Give { item = clean(item, 128, "item"); amount = clean(amount, 64, "amount"); }
        public UiAction mapText(UnaryOperator<String> t) { return new Give(t.apply(item), t.apply(amount)); }
    }
    /** Opens another screen in the same session (ends the chain, like Ultimate UI's redirect). */
    record Open(String screen) implements UiAction {
        public Open { screen = clean(screen, 64, "screen id"); }
        public UiAction mapText(UnaryOperator<String> t) { return new Open(t.apply(screen)); }
    }
    /** Back to the previous screen of this session; closes when there is none. */
    record Back() implements UiAction { public UiAction mapText(UnaryOperator<String> t) { return this; } }
    record Close() implements UiAction { public UiAction mapText(UnaryOperator<String> t) { return this; } }
    /** Shows, hides or flips elements (ids). Only elements Tideframe baked as separate layers can change. */
    record Visibility(List<String> show, List<String> hide, List<String> toggle) implements UiAction {
        public Visibility {
            show = ids(show); hide = ids(hide); toggle = ids(toggle);
            if (show.isEmpty() && hide.isEmpty() && toggle.isEmpty()) throw new IllegalArgumentException("Visibility needs at least one element id");
        }
        public UiAction mapText(UnaryOperator<String> t) { return this; }
    }
    /** set, add, subtract, multiply or divide; a leading * or $ forces the global (per-player) variable. */
    record SetVariable(String name, String operation, String value) implements UiAction {
        public static final List<String> OPERATIONS = List.of("set", "add", "subtract", "multiply", "divide");
        public SetVariable {
            name = Variables.validName(name);
            if (operation == null || !OPERATIONS.contains(operation)) throw new IllegalArgumentException("Variable operation must be one of " + OPERATIONS);
            value = clean(value, 256, "variable value");
        }
        public UiAction mapText(UnaryOperator<String> t) { return new SetVariable(name, operation, t.apply(value)); }
    }
    /** Runs {@code then} when the expression holds ({coins} >= {price}, %vault_eco_balance% >= 1000, a == b), else {@code otherwise}. */
    record Condition(String expression, List<UiAction> then, List<UiAction> otherwise) implements UiAction {
        public Condition { expression = clean(expression, 256, "condition"); then = List.copyOf(then); otherwise = List.copyOf(otherwise); }
        public UiAction mapText(UnaryOperator<String> t) { return new Condition(t.apply(expression), map(then, t), map(otherwise, t)); }
    }
    record Permission(String permission, List<UiAction> then, List<UiAction> otherwise) implements UiAction {
        public Permission {
            if (permission == null || !permission.matches("[A-Za-z0-9_.*-]{1,128}")) throw new IllegalArgumentException("Invalid permission node");
            then = List.copyOf(then); otherwise = List.copyOf(otherwise);
        }
        public UiAction mapText(UnaryOperator<String> t) { return new Permission(permission, map(then, t), map(otherwise, t)); }
    }
    /** Calls a function of the screen; its actions run here, then the rest of this list continues. */
    record Call(String function, Map<String, String> arguments) implements UiAction {
        public Call {
            if (function == null || !function.matches("[a-z][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("Invalid function name");
            if (arguments.size() > 32) throw new IllegalArgumentException("At most 32 function arguments");
            arguments.forEach((k, v) -> { Variables.validName(k); clean(v, 256, "argument"); });
            arguments = Map.copyOf(arguments);
        }
        public UiAction mapText(UnaryOperator<String> t) {
            var mapped = new java.util.LinkedHashMap<String, String>();
            arguments.forEach((k, v) -> mapped.put(k, t.apply(v)));
            return new Call(function, mapped);
        }
    }
    /**
     * Asks for text in the game's own text field (a dialog). The confirmed text goes to {@code variable};
     * {@code onSubmit} runs after it is saved, {@code onCancel} when the player backs out.
     */
    record Input(String title, String label, String initial, String variable, int maxLength, String filter, boolean allowEmpty,
                 List<UiAction> onSubmit, List<UiAction> onCancel) implements UiAction {
        public static final List<String> FILTERS = List.of("any", "digits", "signed-digits", "decimal");
        public Input {
            title = clean(title, 64, "input title"); label = clean(label, 64, "input label"); initial = clean(initial, 256, "input initial text");
            variable = Variables.validName(variable);
            if (maxLength < 1 || maxLength > 256) throw new IllegalArgumentException("Input max-length must be 1 to 256");
            if (filter == null || !FILTERS.contains(filter)) throw new IllegalArgumentException("Input filter must be one of " + FILTERS);
            onSubmit = List.copyOf(onSubmit); onCancel = List.copyOf(onCancel);
        }
        public UiAction mapText(UnaryOperator<String> t) {
            return new Input(t.apply(title), t.apply(label), t.apply(initial), variable, maxLength, filter, allowEmpty, map(onSubmit, t), map(onCancel, t));
        }
        /** True when {@code text} passes the filter and the length limit. */
        public boolean accepts(String text) {
            if (text == null || text.length() > maxLength || text.isEmpty() && !allowEmpty) return false;
            return switch (filter) {
                case "digits" -> text.matches("[0-9]*");
                case "signed-digits" -> text.matches("-?[0-9]*");
                case "decimal" -> text.matches("-?[0-9]*(\\.[0-9]*)?");
                default -> true;
            };
        }
    }
    /** Moves an element sideways at run time (logical pixels, may be a {variable}); 0 puts it back. */
    record Offset(String element, String dx) implements UiAction {
        public Offset { element = Variables.validId(element); dx = clean(dx, 64, "offset"); }
        public UiAction mapText(UnaryOperator<String> t) { return new Offset(element, t.apply(dx)); }
    }
    /** Tints an element (#RRGGBB multiplies its colours; null or "none" removes the tint). */
    record Tint(String element, String color) implements UiAction {
        public Tint { element = Variables.validId(element); color = color == null || color.equals("none") ? null : clean(color, 32, "tint"); }
        public UiAction mapText(UnaryOperator<String> t) { return new Tint(element, color == null ? null : t.apply(color)); }
    }
    /** Restarts an animated element (frames) from its first frame. */
    record Animate(String element) implements UiAction {
        public Animate { element = Variables.validId(element); }
        public UiAction mapText(UnaryOperator<String> t) { return this; }
    }
    /** Scrolls a list element by {@code rows} (negative = up), or to an absolute row when {@code absolute}. */
    record Scroll(String element, String rows, boolean absolute) implements UiAction {
        public Scroll { element = Variables.validId(element); rows = clean(rows, 64, "scroll rows"); }
        public UiAction mapText(UnaryOperator<String> t) { return new Scroll(element, t.apply(rows), absolute); }
    }

    /**
     * Takes {@code amount} from the player's balance (Vault) only when it is enough, then runs {@code then}; otherwise
     * takes nothing and runs {@code otherwise}. The amount may be a {variable}; without an economy it always fails.
     */
    record Charge(String amount, List<UiAction> then, List<UiAction> otherwise) implements UiAction {
        public Charge { amount = clean(amount, 64, "charge amount"); then = List.copyOf(then); otherwise = List.copyOf(otherwise); }
        public UiAction mapText(UnaryOperator<String> t) { return new Charge(t.apply(amount), map(then, t), map(otherwise, t)); }
    }
    /** Adds {@code amount} to the player's balance (Vault). */
    record Pay(String amount) implements UiAction {
        public Pay { amount = clean(amount, 64, "pay amount"); }
        public UiAction mapText(UnaryOperator<String> t) { return new Pay(t.apply(amount)); }
    }
    /** Closes the menu and asks the proxy (BungeeCord, or Velocity with its BungeeCord channel) to move the player to {@code server}. */
    record Connect(String server) implements UiAction {
        public Connect { server = clean(server, 64, "server"); }
        public UiAction mapText(UnaryOperator<String> t) { return new Connect(t.apply(server)); }
    }
    /**
     * Asks first, in the game's own dialog: {@code yes} runs {@code then}, {@code no} (or Escape) runs {@code otherwise}.
     * Where the game has no dialog screen it always runs {@code otherwise}: nothing happens without asking.
     */
    record Confirm(String title, String message, String yes, String no, List<UiAction> then, List<UiAction> otherwise) implements UiAction {
        public Confirm {
            title = clean(title, 64, "confirm title"); message = clean(message, 256, "confirm message");
            yes = clean(yes, 32, "confirm yes label"); no = clean(no, 32, "confirm no label");
            then = List.copyOf(then); otherwise = List.copyOf(otherwise);
        }
        public UiAction mapText(UnaryOperator<String> t) { return new Confirm(t.apply(title), t.apply(message), t.apply(yes), t.apply(no), map(then, t), map(otherwise, t)); }
    }
    /** Adds HUD page {@code screen} over the HUD pages shown ({@code add}) or takes it away, without closing the menu. */
    record Hud(String screen, boolean add) implements UiAction {
        public Hud { screen = clean(screen, 64, "HUD screen id"); }
        public UiAction mapText(UnaryOperator<String> t) { return new Hud(t.apply(screen), add); }
    }
    /**
     * Shows HUD page {@code screen} as a toast for {@code seconds} (1 to 60; may be a {variable}), after the toasts
     * already waiting; {@code values} become its variables, so its texts can say {titulo} or {texto}.
     */
    record Toast(String screen, String seconds, Map<String, String> values) implements UiAction {
        public Toast {
            screen = clean(screen, 64, "toast screen id"); seconds = clean(seconds, 64, "toast seconds");
            if (values.size() > 16) throw new IllegalArgumentException("At most 16 toast values");
            values.forEach((k, v) -> { Variables.validName(k); clean(v, 256, "toast value"); });
            values = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(values));
        }
        public UiAction mapText(UnaryOperator<String> t) {
            var mapped = new java.util.LinkedHashMap<String, String>();
            values.forEach((k, v) -> mapped.put(k, t.apply(v)));
            return new Toast(t.apply(screen), t.apply(seconds), mapped);
        }
    }
    /**
     * Controls a PNG-folder or GIF animation: "pause" holds the frame showing, "resume" continues from it, "frame"
     * jumps to frame {@code frame} (1 = the first; may be a {variable}) and keeps playing or paused as it was.
     */
    record Playback(String element, String command, String frame) implements UiAction {
        public static final List<String> COMMANDS = List.of("pause", "resume", "frame");
        public Playback {
            element = Variables.validId(element);
            if (command == null || !COMMANDS.contains(command)) throw new IllegalArgumentException("Playback must be pause, resume or frame");
            frame = command.equals("frame") ? clean(frame, 64, "frame") : "";
        }
        public UiAction mapText(UnaryOperator<String> t) { return new Playback(element, command, t.apply(frame)); }
    }

    static List<UiAction> map(List<UiAction> actions, UnaryOperator<String> text) {
        return actions.stream().map(a -> a.mapText(text)).toList();
    }
    private static String clean(String value, int max, String what) {
        Objects.requireNonNull(value, what + " is required");
        if (value.length() > max) throw new IllegalArgumentException(what + " is longer than " + max + " characters");
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < 32 && value.charAt(i) != '\n') throw new IllegalArgumentException(what + " contains control characters");
        return value;
    }
    private static List<String> ids(List<String> ids) {
        if (ids == null) return List.of();
        if (ids.size() > 128) throw new IllegalArgumentException("At most 128 element ids");
        ids.forEach(Variables::validId);
        return List.copyOf(ids);
    }
}
