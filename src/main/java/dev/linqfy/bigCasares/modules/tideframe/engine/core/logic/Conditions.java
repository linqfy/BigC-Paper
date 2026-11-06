package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates conditions: {@code a OP b} with ==, =, !=, >=, <=, >, <, joined by && or || (&& binds tighter, no
 * parentheses). Numbers compare as numbers; =, == and != also compare text (case-sensitive, trimmed, colour codes
 * removed). A bare value is true unless it is empty, "false", "no" or "0".
 * <p>The expression is split <em>before</em> anything is resolved and every operand is resolved on its own, so a
 * value (a variable a player typed, a placeholder) can never add an operator or another clause.
 * Placeholder clamps like {@code %player_health,MIN=0,MAX=20,DEFAULT=20%} are handled by {@link #clamp}.
 */
public final class Conditions {
    private Conditions() { }
    private static final String[] OPERATORS = {"==", "!=", ">=", "<=", "=", ">", "<"};
    private static final Pattern COLOUR = Pattern.compile("(?i)[&§][0-9a-fk-orx]|<[^<>]{1,32}>");
    /** Placeholders, {tokens}, calculated {a * 2} and quoted text: operators inside them are not operators. */
    private static final Pattern OPAQUE = Pattern.compile("%[^%\\s]{1,128}%|\\$?\\{[^{}]{1,256}}|\"[^\"]*\"|'[^']*'");

    /** An expression whose values are already in place. */
    public static boolean test(String expression) { return test(expression, UnaryOperator.identity()); }

    /** Splits {@code template} into clauses and operands, then resolves each operand with {@code resolve}. */
    public static boolean test(String template, UnaryOperator<String> resolve) {
        if (template == null) return false;
        String masked = mask(template);
        for (int[] any : split(masked, "||", 0, masked.length())) {
            boolean all = true;
            for (int[] part : split(masked, "&&", any[0], any[1])) {
                if (!single(template, masked, part[0], part[1], resolve)) { all = false; break; }
            }
            if (all) return true;
        }
        return false;
    }
    static String mask(String text) {
        var out = new StringBuilder(text);
        Matcher m = OPAQUE.matcher(text);
        while (m.find()) for (int i = m.start(); i < m.end(); i++) out.setCharAt(i, 'x');
        return out.toString();
    }
    private static List<int[]> split(String masked, String separator, int from, int to) {
        var parts = new ArrayList<int[]>();
        int start = from, at;
        while ((at = masked.indexOf(separator, start)) >= 0 && at < to) { parts.add(new int[]{start, at}); start = at + separator.length(); }
        parts.add(new int[]{start, to});
        return parts;
    }
    private static boolean single(String template, String masked, int from, int to, UnaryOperator<String> resolve) {
        int at = -1; String op = null;
        for (int i = from; i < to && at < 0; i++) {
            for (String candidate : OPERATORS) {
                if (i + candidate.length() <= to && masked.startsWith(candidate, i)) { at = i; op = candidate; break; }
            }
        }
        if (at < 0) return truthy(plain(resolve.apply(template.substring(from, to).trim())));
        String left = plain(resolve.apply(template.substring(from, at).trim()));
        String right = plain(resolve.apply(template.substring(at + op.length(), to).trim()));
        BigDecimal a = Variables.number(left), b = Variables.number(right);
        if (a != null && b != null) {
            int c = a.compareTo(b);
            return switch (op) {
                case "==", "=" -> c == 0;
                case "!=" -> c != 0;
                case ">=" -> c >= 0;
                case "<=" -> c <= 0;
                case ">" -> c > 0;
                default -> c < 0;
            };
        }
        return switch (op) {
            case "==", "=" -> left.equals(right);
            case "!=" -> !left.equals(right);
            default -> false; // ordering needs numbers on both sides
        };
    }
    private static boolean truthy(String value) {
        String v = value.toLowerCase(Locale.ROOT);
        return !(v.isEmpty() || v.equals("false") || v.equals("no") || v.equals("0"));
    }
    private static String plain(String value) {
        String v = COLOUR.matcher(value == null ? "" : value).replaceAll("").trim();
        if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) v = v.substring(1, v.length() - 1);
        return v;
    }

    /**
     * Ultimate UI style clamp written inside a placeholder: {@code %name,MIN=0,MAX=20,DEFAULT=20%} becomes
     * {@code %name%} for resolution; {@link #applyClamp} then bounds the resolved number.
     */
    public record Clamp(String placeholder, BigDecimal min, BigDecimal max, String fallback) { }
    private static final Pattern CLAMPED = Pattern.compile("%([A-Za-z0-9_:.-]{1,64})((?:,(?:MIN|MAX|DEFAULT)=[^,%]{1,32}){1,3})%");
    public static List<Clamp> clamps(String text) {
        var out = new ArrayList<Clamp>();
        Matcher m = CLAMPED.matcher(text);
        while (m.find()) {
            BigDecimal min = null, max = null; String fallback = null;
            for (String option : m.group(2).substring(1).split(",")) {
                String key = option.substring(0, option.indexOf('=')), value = option.substring(option.indexOf('=') + 1);
                switch (key) {
                    case "MIN" -> min = Variables.number(value);
                    case "MAX" -> max = Variables.number(value);
                    default -> fallback = value;
                }
            }
            out.add(new Clamp("%" + m.group(1) + "%", min, max, fallback));
        }
        return out;
    }
    /** Replaces each clamped placeholder using {@code resolve} (which returns the raw value, or the token itself when unknown). */
    public static String clamp(String text, UnaryOperator<String> resolve) {
        if (text.indexOf('%') < 0) return text;
        Matcher m = CLAMPED.matcher(text);
        var out = new StringBuilder();
        while (m.find()) {
            var clamp = clamps(m.group()).get(0);
            String raw = resolve.apply(clamp.placeholder());
            m.appendReplacement(out, Matcher.quoteReplacement(applyClamp(clamp, raw)));
        }
        return m.appendTail(out).toString();
    }
    static String applyClamp(Clamp clamp, String raw) {
        BigDecimal value = raw == null || raw.equals(clamp.placeholder()) ? null : Variables.number(raw);
        if (value == null) return clamp.fallback() != null ? clamp.fallback() : raw == null ? "0" : raw;
        if (clamp.min() != null && value.compareTo(clamp.min()) < 0) value = clamp.min();
        if (clamp.max() != null && value.compareTo(clamp.max()) > 0) value = clamp.max();
        return Variables.format(value);
    }
}
