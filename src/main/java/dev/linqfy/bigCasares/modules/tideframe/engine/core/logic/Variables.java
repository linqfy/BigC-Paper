package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Screen variables. A plain name is local to the open screen; a name declared with a leading * or $ is global:
 * shared by every screen of the player's session and kept across redirects. Reading never needs the prefix.
 * Not thread-safe: owned by the player's thread.
 */
public final class Variables {
    public static final int MAX_VARIABLES = 256, MAX_VALUE = 256;
    private static final Pattern NAME = Pattern.compile("[a-z_][a-z0-9_]{0,23}");
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]{0,31}");
    /** {name} or ${name}; the same token shape as live text. */
    public static final Pattern TOKEN = Pattern.compile("\\$?\\{([a-z_][a-z0-9_]{0,23})}");
    private final Map<String, String> local = new LinkedHashMap<>();
    private final Map<String, String> global;

    public Variables() { this(new LinkedHashMap<>()); }
    /** {@code global} is shared with the player's other screens (the session owns it). */
    public Variables(Map<String, String> global) { this.global = global; }

    /** Validates a variable name; a single leading * or $ (global marker) is allowed and kept. */
    public static String validName(String name) {
        if (name == null) throw new IllegalArgumentException("Variable name is required");
        String bare = isGlobalName(name) ? name.substring(1) : name;
        if (!NAME.matcher(bare).matches()) throw new IllegalArgumentException("Invalid variable name: " + name + " (a-z, 0-9 and _, up to 24 characters)");
        return name;
    }
    public static String validId(String id) {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid element id: " + id);
        return id;
    }
    static boolean isGlobalName(String name) { return name.startsWith("*") || name.startsWith("$"); }
    static String bare(String name) { return isGlobalName(name) ? name.substring(1) : name; }

    /** Declares a screen's variables: locals reset, globals keep the player's current value when they already exist. */
    public void declare(Map<String, String> declarations) {
        local.clear();
        declarations.forEach((name, value) -> {
            if (isGlobalName(name)) global.putIfAbsent(bare(name), limit(value));
            else local.put(name, limit(value));
        });
    }
    /** Local first, then global; null when neither exists. */
    public String get(String name) {
        String key = bare(name);
        if (!isGlobalName(name) && local.containsKey(key)) return local.get(key);
        return global.get(key);
    }
    /**
     * Writes a variable. {@code *name} always writes the global; a plain name updates the global when the player
     * already has one with that name, otherwise the local. Arithmetic needs numbers on both sides (a missing
     * variable counts as 0); division by zero leaves the value unchanged. Returns true when the value changed.
     */
    public boolean apply(String name, String operation, String value) {
        String key = bare(name);
        boolean toGlobal = isGlobalName(name) || !local.containsKey(key) && global.containsKey(key);
        var target = toGlobal ? global : local;
        if (!target.containsKey(key) && target.size() >= MAX_VARIABLES) throw new IllegalStateException("Too many variables");
        String before = target.get(key), after;
        if (operation.equals("set")) after = limit(value);
        else {
            BigDecimal left = number(before == null ? "0" : before), right = number(value);
            if (left == null || right == null) return false;
            after = switch (operation) {
                case "add" -> format(left.add(right));
                case "subtract" -> format(left.subtract(right));
                case "multiply" -> format(left.multiply(right, MathContext.DECIMAL64));
                case "divide" -> right.signum() == 0 ? before : format(left.divide(right, MathContext.DECIMAL64));
                default -> throw new IllegalArgumentException("Unknown variable operation " + operation);
            };
            if (after == null) return false;
        }
        if (after.equals(before)) return false;
        target.put(key, after);
        return true;
    }
    /** Replaces {name} and ${name} with the variable's value; unknown names are left as they are. */
    public String substitute(String text) {
        if (text.indexOf('{') < 0) return text;
        Matcher m = TOKEN.matcher(text);
        var out = new StringBuilder();
        while (m.find()) {
            String value = get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? m.group() : value));
        }
        return m.appendTail(out).toString();
    }
    /** Snapshot for live text (local wins over global). */
    public Map<String, String> snapshot() {
        var all = new LinkedHashMap<>(global);
        all.putAll(local);
        return Map.copyOf(all);
    }
    public Map<String, String> globals() { return global; }

    static BigDecimal number(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (!trimmed.matches("[-+]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([eE][-+]?[0-9]{1,3})?")) return null;
        try { return new BigDecimal(trimmed); } catch (NumberFormatException bad) { return null; }
    }
    /** Plain decimal: whole numbers without ".0", at most 6 decimals. */
    static String format(BigDecimal value) {
        var rounded = value.setScale(Math.min(6, Math.max(0, value.scale())), RoundingMode.HALF_UP).stripTrailingZeros();
        if (rounded.scale() < 0) rounded = rounded.setScale(0);
        String plain = rounded.toPlainString();
        return plain.length() > MAX_VALUE ? null : plain.equals("-0") ? "0" : plain;
    }
    private static String limit(String value) {
        if (value == null) return "0";
        return value.length() > MAX_VALUE ? value.substring(0, MAX_VALUE) : value;
    }
}
