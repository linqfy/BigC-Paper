package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calculated text (0.23): a brace pair holding an expression instead of a plain name, such as {coins * 2},
 * {(price - discount) / 2}, {coins >= 100 ? "Enough" : "Missing"} or {round(ratio * 100, 1)}.
 * <p>
 * Numbers use + - * / % and parentheses; text joins with + ("Level " + level); comparisons are == != >= <= > <
 * (numbers as numbers, text by equality, like {@link Conditions}); && || ! and the ternary {@code a ? b : c} decide;
 * round, floor, ceil, abs, min and max are the only functions. Names are variables, list fields (item.price),
 * built-in values (health) and %placeholders%; {@code $name} reads only the player's global variable.
 * <p>
 * The expression is parsed from the page text before any value is looked up, and a value is only ever an operand:
 * text a player typed can never become an operator, a name or another expression. A token that does not parse, names
 * something unknown or cannot be computed (division by zero, a word where a number is needed) stays exactly as
 * written, like an unknown {token}. Pure and thread-safe.
 */
public final class Expressions {
    private Expressions() { }

    /** Value of a name ({@code coins}, {@code $coins}, {@code item.price}) or a placeholder ({@code %vault_eco_balance%}); null when unknown. */
    public interface Names { String value(String name); }

    private static final Pattern BRACES = Pattern.compile("\\{([^{}]{1,256})}");
    /** A plain {token}: the regular variable path handles it. */
    private static final Pattern PLAIN = Pattern.compile("[a-z_][a-z0-9_.]{0,47}");
    private static final Pattern COLOUR = Pattern.compile("(?i)[&§][0-9a-fk-orx]|<[^<>]{1,32}>");
    private static final List<String> FUNCTIONS = List.of("round", "floor", "ceil", "abs", "min", "max");
    private static final int MAX_TOKENS = 128, MAX_DEPTH = 32;

    /** True when {@code text} has at least one brace pair that is an expression (not a plain name). */
    public static boolean contains(String text) {
        if (text == null || text.indexOf('{') < 0) return false;
        Matcher m = BRACES.matcher(text);
        while (m.find()) if (!PLAIN.matcher(m.group(1)).matches() && parse(m.group(1)) != null) return true;
        return false;
    }

    /**
     * {@code text} with every computable expression token replaced by its value; the text around them (and every token
     * that stays as written) goes through {@code chunk}, the regular placeholder and variable pass.
     */
    public static String render(String text, UnaryOperator<String> chunk, Names names) {
        if (text.indexOf('{') < 0) return chunk.apply(text);
        Matcher m = BRACES.matcher(text);
        var out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String body = m.group(1);
            if (PLAIN.matcher(body).matches()) continue;
            var node = parse(body);
            if (node == null) continue;
            String value = node.eval(names);
            if (value == null) continue;
            out.append(chunk.apply(text.substring(last, m.start()))).append(value);
            last = m.end();
        }
        if (last == 0) return chunk.apply(text);
        return out.append(chunk.apply(text.substring(last))).toString();
    }

    /** The value of one expression (no braces), or null when it does not parse or cannot be computed. */
    public static String evaluate(String expression, Names names) {
        var node = parse(expression);
        return node == null ? null : node.eval(names);
    }

    // ----- values -----
    static BigDecimal number(String value) { return value == null ? null : Variables.number(plain(value)); }
    static String plain(String value) { return COLOUR.matcher(value).replaceAll("").trim(); }
    static boolean truthy(String value) {
        String v = plain(value).toLowerCase(Locale.ROOT);
        return !(v.isEmpty() || v.equals("false") || v.equals("no") || v.equals("0"));
    }
    private static String bool(boolean value) { return value ? "true" : "false"; }

    // ----- syntax tree -----
    private interface Node { String eval(Names names); }
    private record Literal(String value) implements Node { public String eval(Names names) { return value; } }
    private record Name(String name) implements Node { public String eval(Names names) { return names.value(name); } }
    private record Not(Node inner) implements Node {
        public String eval(Names names) { String v = inner.eval(names); return v == null ? null : bool(!truthy(v)); }
    }
    private record Negate(Node inner) implements Node {
        public String eval(Names names) { var n = number(inner.eval(names)); return n == null ? null : Variables.format(n.negate()); }
    }
    private record Ternary(Node test, Node then, Node otherwise) implements Node {
        public String eval(Names names) {
            String v = test.eval(names);
            return v == null ? null : truthy(v) ? then.eval(names) : otherwise.eval(names);
        }
    }
    private record Binary(String op, Node left, Node right) implements Node {
        public String eval(Names names) {
            if (op.equals("&&") || op.equals("||")) {
                String a = left.eval(names);
                if (a == null) return null;
                if (op.equals("&&") ? !truthy(a) : truthy(a)) return bool(op.equals("||"));
                String b = right.eval(names);
                return b == null ? null : bool(truthy(b));
            }
            String a = left.eval(names), b = right.eval(names);
            if (a == null || b == null) return null;
            BigDecimal x = number(a), y = number(b);
            switch (op) {
                case "==", "!=" -> {
                    boolean same = x != null && y != null ? x.compareTo(y) == 0 : plain(a).equals(plain(b));
                    return bool(op.equals("==") == same);
                }
                case ">=", "<=", ">", "<" -> {
                    if (x == null || y == null) return bool(false); // ordering needs numbers on both sides
                    int c = x.compareTo(y);
                    return bool(switch (op) { case ">=" -> c >= 0; case "<=" -> c <= 0; case ">" -> c > 0; default -> c < 0; });
                }
                case "+" -> { return x != null && y != null ? Variables.format(x.add(y)) : a + b; }
                default -> {
                    if (x == null || y == null) return null;
                    return switch (op) {
                        case "-" -> Variables.format(x.subtract(y));
                        case "*" -> Variables.format(x.multiply(y, MathContext.DECIMAL64));
                        case "/" -> y.signum() == 0 ? null : Variables.format(x.divide(y, MathContext.DECIMAL64));
                        // Exact, like editor/expressions.mjs: a DECIMAL64 remainder throws once the quotient passes 16 digits.
                        default -> y.signum() == 0 ? null : Variables.format(x.remainder(y));
                    };
                }
            }
        }
    }
    private record Call(String function, List<Node> args) implements Node {
        public String eval(Names names) {
            var values = new ArrayList<BigDecimal>();
            for (var arg : args) { var n = number(arg.eval(names)); if (n == null) return null; values.add(n); }
            BigDecimal x = values.get(0);
            BigDecimal result = switch (function) {
                case "round" -> {
                    if (values.size() == 1) yield x.setScale(0, RoundingMode.HALF_UP);
                    int places = values.get(1).intValue();
                    if (values.get(1).stripTrailingZeros().scale() > 0 || places < 0 || places > 6) yield null;
                    yield x.setScale(places, RoundingMode.HALF_UP);
                }
                case "floor" -> x.setScale(0, RoundingMode.FLOOR);
                case "ceil" -> x.setScale(0, RoundingMode.CEILING);
                case "abs" -> x.abs();
                case "min" -> values.stream().min(BigDecimal::compareTo).orElseThrow();
                default -> values.stream().max(BigDecimal::compareTo).orElseThrow();
            };
            return result == null ? null : Variables.format(result);
        }
    }

    // ----- parsing -----
    private record Token(char kind, String text) { }
    private static final Pattern NUMBER = Pattern.compile("[0-9]+(\\.[0-9]+)?|\\.[0-9]+");
    private static final Pattern NAME = Pattern.compile("[$]?[a-z_][a-z0-9_.]{0,47}");
    private static final Pattern PLACEHOLDER = Pattern.compile("%[A-Za-z0-9_:.,=-]{1,128}%");
    private static final String[] OPERATORS = {"==", "!=", ">=", "<=", "&&", "||", ">", "<", "+", "-", "*", "/", "%", "!", "?", ":", "(", ")", ","};

    /** Tokens of an expression: n(umber) s(tring) i(dentifier) p(laceholder) o(perator); null when something is not one. */
    private static List<Token> tokens(String source) {
        var out = new ArrayList<Token>();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == ' ') { i++; continue; }
            if (out.size() >= MAX_TOKENS) return null;
            if (c == '"' || c == '\'') {
                int end = source.indexOf(c, i + 1);
                if (end < 0) return null;
                out.add(new Token('s', source.substring(i + 1, end))); i = end + 1; continue;
            }
            Matcher m;
            if (c == '%' && (m = PLACEHOLDER.matcher(source).region(i, source.length())).lookingAt()) { out.add(new Token('p', m.group())); i = m.end(); continue; }
            if ((m = NUMBER.matcher(source).region(i, source.length())).lookingAt()) { out.add(new Token('n', m.group())); i = m.end(); continue; }
            if ((m = NAME.matcher(source).region(i, source.length())).lookingAt()) {
                if (m.group().endsWith(".")) return null;
                out.add(new Token('i', m.group())); i = m.end(); continue;
            }
            String op = null;
            for (String candidate : OPERATORS) if (source.startsWith(candidate, i)) { op = candidate; break; }
            if (op == null) return null;
            out.add(new Token('o', op)); i += op.length();
        }
        return out.isEmpty() ? null : out;
    }

    /** The syntax tree, or null when {@code source} is not a whole expression. */
    private static Node parse(String source) {
        var tokens = tokens(source);
        if (tokens == null) return null;
        var parser = new Parser(tokens);
        try {
            var node = parser.ternary(0);
            return parser.at == tokens.size() ? node : null;
        } catch (IllegalStateException notAnExpression) { return null; }
    }

    private static final class Parser {
        final List<Token> tokens;
        int at;
        Parser(List<Token> tokens) { this.tokens = tokens; }

        boolean accept(String op) {
            if (at < tokens.size() && tokens.get(at).kind() == 'o' && tokens.get(at).text().equals(op)) { at++; return true; }
            return false;
        }
        void expect(String op) { if (!accept(op)) throw new IllegalStateException(); }
        static void deep(int depth) { if (depth > MAX_DEPTH) throw new IllegalStateException(); }

        Node ternary(int depth) {
            deep(depth);
            Node test = or(depth);
            if (!accept("?")) return test;
            Node then = ternary(depth + 1);
            expect(":");
            return new Ternary(test, then, ternary(depth + 1));
        }
        Node or(int depth) {
            Node node = and(depth);
            while (accept("||")) node = new Binary("||", node, and(depth));
            return node;
        }
        Node and(int depth) {
            Node node = compare(depth);
            while (accept("&&")) node = new Binary("&&", node, compare(depth));
            return node;
        }
        Node compare(int depth) {
            Node node = sum(depth);
            for (String op : new String[]{"==", "!=", ">=", "<=", ">", "<"}) if (accept(op)) return new Binary(op, node, sum(depth));
            return node;
        }
        Node sum(int depth) {
            Node node = product(depth);
            while (true) {
                if (accept("+")) node = new Binary("+", node, product(depth));
                else if (accept("-")) node = new Binary("-", node, product(depth));
                else return node;
            }
        }
        Node product(int depth) {
            Node node = unary(depth);
            while (true) {
                if (accept("*")) node = new Binary("*", node, unary(depth));
                else if (accept("/")) node = new Binary("/", node, unary(depth));
                else if (accept("%")) node = new Binary("%", node, unary(depth));
                else return node;
            }
        }
        Node unary(int depth) {
            deep(depth);
            if (accept("-")) return new Negate(unary(depth + 1));
            if (accept("!")) return new Not(unary(depth + 1));
            return primary(depth);
        }
        Node primary(int depth) {
            if (at >= tokens.size()) throw new IllegalStateException();
            var token = tokens.get(at++);
            switch (token.kind()) {
                case 'n' -> { return new Literal(token.text()); }
                case 's' -> { return new Literal(token.text()); }
                case 'p' -> { return new Name(token.text()); }
                case 'i' -> {
                    if (FUNCTIONS.contains(token.text()) && accept("(")) {
                        var args = new ArrayList<Node>();
                        do { args.add(ternary(depth + 1)); } while (accept(","));
                        expect(")");
                        int count = args.size();
                        boolean fits = switch (token.text()) { case "round" -> count <= 2; case "min", "max" -> count <= 16; default -> count == 1; };
                        if (!fits) throw new IllegalStateException();
                        return new Call(token.text(), List.copyOf(args));
                    }
                    return new Name(token.text());
                }
                default -> {
                    if (!token.text().equals("(")) throw new IllegalStateException();
                    Node inner = ternary(depth + 1);
                    expect(")");
                    return inner;
                }
            }
        }
    }
}
