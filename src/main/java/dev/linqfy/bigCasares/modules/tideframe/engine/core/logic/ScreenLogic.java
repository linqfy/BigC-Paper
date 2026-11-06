package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Screen-level behaviour (schema 9): how it closes, its variables, functions and keybinds, an optional command
 * that opens it, whether it opens on join, actions run when it opens or closes, and its open/close animation.
 * Triggers (0.23): {@code timers} repeat while the page is open, {@code onDamage} runs when the player is hurt
 * ({damage} and {cause} in scope), {@code onWorldChange} when they change world ({world} and {from}), and
 * {@code onChange} when a variable changes ({old} and {new}).
 */
public record ScreenLogic(Behavior behavior, Map<String, String> variables, Map<String, Function> functions,
                          Map<String, List<UiAction>> keybinds, String command, String permission, boolean openOnJoin,
                          List<UiAction> onOpen, List<UiAction> onClose, Animation animation,
                          List<Timer> timers, List<UiAction> onDamage, List<UiAction> onWorldChange, Map<String, List<UiAction>> onChange) {
    public static final ScreenLogic EMPTY = new ScreenLogic(Behavior.DEFAULTS, Map.of(), Map.of(), Map.of(), null, null, false, List.of(), List.of());
    public ScreenLogic(Behavior behavior, Map<String, String> variables, Map<String, Function> functions,
                       Map<String, List<UiAction>> keybinds, String command, String permission, boolean openOnJoin,
                       List<UiAction> onOpen, List<UiAction> onClose) {
        this(behavior, variables, functions, keybinds, command, permission, openOnJoin, onOpen, onClose, Animation.DEFAULT);
    }
    public ScreenLogic(Behavior behavior, Map<String, String> variables, Map<String, Function> functions,
                       Map<String, List<UiAction>> keybinds, String command, String permission, boolean openOnJoin,
                       List<UiAction> onOpen, List<UiAction> onClose, Animation animation) {
        this(behavior, variables, functions, keybinds, command, permission, openOnJoin, onOpen, onClose, animation, List.of(), List.of(), List.of(), Map.of());
    }

    /** Runs {@code actions} every {@code everyTicks} (5 ticks = 0.25 s to 72000 = one hour) while the page is open. */
    public record Timer(int everyTicks, List<UiAction> actions) {
        public static final int MIN_TICKS = 5, MAX_TICKS = 72_000;
        public Timer {
            if (everyTicks < MIN_TICKS || everyTicks > MAX_TICKS) throw new IllegalArgumentException("A timer runs every 0.25 to 3600 seconds");
            if (actions.isEmpty()) throw new IllegalArgumentException("A timer needs at least one action");
            actions = List.copyOf(actions);
        }
    }

    /**
     * One transition. "fade" is the title's own fade (in when opening, out when closing); "slide" moves the whole
     * menu over {@code distance} logical pixels: in from the {@code from} side ("left" or "right") when opening, out
     * towards that side when closing (the title cannot fade while it moves); "none" appears or disappears at once.
     */
    public record Transition(String effect, int durationMs, String easing, String from, int distance) {
        public static final Set<String> EFFECTS = Set.of("fade", "slide", "none");
        public Transition {
            if (effect == null || !EFFECTS.contains(effect)) throw new IllegalArgumentException("Animation effect must be fade, slide or none");
            if (durationMs < 0 || durationMs > 3000) throw new IllegalArgumentException("Animation duration must be 0 to 3000 ms");
            if (easing == null || !Motion.EASINGS.contains(easing)) throw new IllegalArgumentException("Easing must be linear, ease-in, ease-out or ease-in-out");
            if (from == null || !Set.of("left", "right").contains(from)) throw new IllegalArgumentException("Slide from must be left or right");
            if (distance < 1 || distance > 256) throw new IllegalArgumentException("Slide distance must be 1 to 256 logical pixels");
            if (effect.equals("none")) durationMs = 0;
        }
        public static Transition fade(int durationMs) { return new Transition("fade", durationMs, "ease-out", "left", 48); }
        public static final Transition NONE = new Transition("none", 0, "ease-out", "left", 48);
        /** Sideways offset (logical px) {@code elapsedMs} into an opening slide; 0 once it has arrived or for other effects. */
        public int offset(double elapsedMs) {
            if (!effect.equals("slide") || durationMs == 0 || elapsedMs >= durationMs) return 0;
            double t = Motion.ease(easing, Math.max(0, elapsedMs) / durationMs);
            int side = from.equals("left") ? -1 : 1;
            return (int) Math.round(side * distance * (1 - t));
        }
        /** Sideways offset (logical px) {@code elapsedMs} into a closing slide, towards {@code from}; 0 for other effects. */
        public int exitOffset(double elapsedMs) {
            if (!effect.equals("slide") || durationMs == 0) return 0;
            double t = Motion.ease(easing, Math.min(1, Math.max(0, elapsedMs) / durationMs));
            int side = from.equals("left") ? -1 : 1;
            return (int) Math.round(side * distance * t);
        }
        /** The same slide played backwards: it leaves towards the side it came from, with the mirrored easing. */
        public Transition reversed() {
            String mirrored = switch (easing) { case "ease-in" -> "ease-out"; case "ease-out" -> "ease-in"; default -> easing; };
            return new Transition(effect, durationMs, mirrored, from, distance);
        }
    }
    /**
     * Opening and closing. A closing fade lets the client fade the last frame; a closing slide keeps sending the last
     * frame moved sideways for its duration. Either way the player is free the moment the menu closes.
     */
    public record Animation(Transition open, Transition close) {
        public static final Animation DEFAULT = new Animation(Transition.fade(200), Transition.NONE);
        public Animation {
            if (open == null) open = DEFAULT.open();
            if (close == null) close = Transition.NONE;
        }
    }
    private static final Pattern COMMAND = Pattern.compile("[a-z0-9_-]{1,32}");
    /** Keys a page can bind; every one also has a SHIFT_ variant (Shift held). */
    public static final List<String> KEYS = List.of("W", "A", "S", "D", "SPACE", "Q", "F", "L", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "SCROLL_UP", "SCROLL_DOWN");

    /**
     * closeOnSneak: Shift closes (the default, as before). closeOnDamage: taking damage closes. closeOnSwap /
     * closeOnShiftSwap: F or Shift+F closes. reopen: closing goes back to the previous screen instead.
     * keyboardNavigation: W A S D move the cursor to the nearest button that way and Space clicks (keybinds win).
     */
    public record Behavior(boolean closeOnSneak, boolean closeOnDamage, boolean closeOnSwap, boolean closeOnShiftSwap, boolean reopen,
                           boolean keyboardNavigation) {
        public static final Behavior DEFAULTS = new Behavior(true, false, false, false, false);
        public Behavior(boolean closeOnSneak, boolean closeOnDamage, boolean closeOnSwap, boolean closeOnShiftSwap, boolean reopen) {
            this(closeOnSneak, closeOnDamage, closeOnSwap, closeOnShiftSwap, reopen, false);
        }
    }
    /** {@code params}: names in order with their default values (empty when only named). */
    public record Function(Map<String, String> params, List<UiAction> actions) {
        public Function {
            if (actions.isEmpty()) throw new IllegalArgumentException("A function needs at least one action");
            if (params.size() > 32) throw new IllegalArgumentException("At most 32 function parameters");
            params.keySet().forEach(Variables::validName);
            params = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(params));
            actions = List.copyOf(actions);
        }
    }

    public ScreenLogic {
        if (behavior == null) behavior = Behavior.DEFAULTS;
        if (animation == null) animation = Animation.DEFAULT;
        if (variables.size() > Variables.MAX_VARIABLES) throw new IllegalArgumentException("Too many variables");
        variables.forEach((name, value) -> {
            Variables.validName(name);
            if (value == null || value.length() > Variables.MAX_VALUE) throw new IllegalArgumentException("Invalid value for variable " + name);
        });
        variables = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        if (functions.size() > 64) throw new IllegalArgumentException("At most 64 functions");
        functions.keySet().forEach(name -> { if (!name.matches("[a-z][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("Invalid function name " + name); });
        functions = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(functions));
        for (String key : keybinds.keySet()) if (!validKey(key)) throw new IllegalArgumentException("Unknown keybind key " + key + " (use " + KEYS + ", optionally SHIFT_)");
        var binds = new LinkedHashMap<String, List<UiAction>>();
        keybinds.forEach((k, v) -> binds.put(k, List.copyOf(v)));
        keybinds = java.util.Collections.unmodifiableMap(binds);
        if (command != null && !COMMAND.matcher(command).matches()) throw new IllegalArgumentException("Invalid screen command " + command + " (a-z, 0-9, _ and -)");
        if (command != null && Set.of("tideframe", "tf", "help", "op", "stop", "reload", "plugins", "pl").contains(command))
            throw new IllegalArgumentException("Screen command " + command + " is reserved");
        if (permission != null && !permission.matches("[A-Za-z0-9_.-]{1,128}")) throw new IllegalArgumentException("Invalid screen permission");
        onOpen = List.copyOf(onOpen); onClose = List.copyOf(onClose);
        if (timers.size() > 16) throw new IllegalArgumentException("At most 16 timers");
        timers = List.copyOf(timers); onDamage = List.copyOf(onDamage); onWorldChange = List.copyOf(onWorldChange);
        if (onChange.size() > 64) throw new IllegalArgumentException("At most 64 on-change variables");
        var changes = new LinkedHashMap<String, List<UiAction>>();
        onChange.forEach((name, actions) -> {
            String bare = Variables.validName(name).replaceFirst("^[*$]", "");
            if (changes.put(bare, List.copyOf(actions)) != null) throw new IllegalArgumentException("on-change lists " + bare + " twice");
        });
        onChange = java.util.Collections.unmodifiableMap(changes);
    }
    public static boolean validKey(String key) {
        String base = key.startsWith("SHIFT_") ? key.substring(6) : key;
        return KEYS.contains(base);
    }
    public boolean isEmpty() { return equals(EMPTY); }
    public ScreenLogic withBehavior(Behavior value) {
        return new ScreenLogic(value, variables, functions, keybinds, command, permission, openOnJoin, onOpen, onClose, animation, timers, onDamage, onWorldChange, onChange);
    }
    public ScreenLogic withAnimation(Animation value) {
        return new ScreenLogic(behavior, variables, functions, keybinds, command, permission, openOnJoin, onOpen, onClose, value, timers, onDamage, onWorldChange, onChange);
    }
    /** The same logic with these triggers (timers, on-damage, on-world-change and on-change). */
    public ScreenLogic withTriggers(List<Timer> everyTimer, List<UiAction> damage, List<UiAction> world, Map<String, List<UiAction>> change) {
        return new ScreenLogic(behavior, variables, functions, keybinds, command, permission, openOnJoin, onOpen, onClose, animation, everyTimer, damage, world, change);
    }
    /** Every action list of the screen (functions, keybinds, open/close, triggers), for analysis. */
    public List<List<UiAction>> actionLists() {
        var all = new java.util.ArrayList<List<UiAction>>();
        functions.values().forEach(f -> all.add(f.actions()));
        all.addAll(keybinds.values());
        all.add(onOpen); all.add(onClose);
        timers.forEach(t -> all.add(t.actions()));
        all.add(onDamage); all.add(onWorldChange);
        all.addAll(onChange.values());
        return all;
    }
}
