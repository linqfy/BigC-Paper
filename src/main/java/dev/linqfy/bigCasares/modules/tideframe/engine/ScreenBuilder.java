package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a screen in code, with the same pieces a screen JSON file has. Positions and sizes are pixels of a design
 * screen (1920x1080 unless {@link #screenSize}), measured from its top-left corner, like Ultimate UI's builder;
 * they become Tideframe's centred -1..1 coordinates. Text is drawn as written (lower case and accents included)
 * unless {@code property(id, "style.textCase", "upper")}. {@link #build()} validates everything at once.
 *
 * <pre>{@code
 * Screen screen = ScreenBuilder.create("daily_rewards")
 *     .title("Daily rewards")
 *     .block("background", 660, 240, 600, 600, "#101418")
 *     .text("title", "Recompensa diaria ★", 760, 280, 400, 48, "#ffd966")
 *     .button("claim", "Reclamar", 760, 700, 400, 80, "#2b6cb0", List.of(Map.of("type", "message", "value", "<green>¡Listo!")))
 *     .property("title", "style.textScale", 2)
 *     .build();
 * api.registerScreen(screen).thenRun(() -> api.openWhenReady(player, "daily_rewards"));
 * }</pre>
 */
public final class ScreenBuilder {
    private final String id;
    private String title;
    private double width = 1920, height = 1080;
    private Map<String, Object> quality = preset("medium");
    private final Map<String, Object> presentation = new LinkedHashMap<>(Map.of("mode", "menu", "scale", 1, "cursor", "arrow"));
    private final Map<String, Map<String, Object>> elements = new LinkedHashMap<>();
    private Map<String, Object> logic;

    private ScreenBuilder(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("Screen id must be a-z, 0-9, _ or - (1-32, starting with a letter)");
        this.id = id; this.title = id.toUpperCase(Locale.ROOT).replace('_', ' ');
    }
    public static ScreenBuilder create(String id) { return new ScreenBuilder(id); }

    public ScreenBuilder title(String value) { title = Objects.requireNonNull(value); return this; }
    /** Design screen in pixels; positions and sizes use it. */
    public ScreenBuilder screenSize(double designWidth, double designHeight) {
        if (!(designWidth >= 16 && designHeight >= 16)) throw new IllegalArgumentException("Design size must be at least 16x16");
        width = designWidth; height = designHeight; return this;
    }
    /** Raster quality: "low", "medium" (default) or "high". */
    public ScreenBuilder quality(String preset) { quality = preset(preset); return this; }
    private static Map<String, Object> preset(String preset) {
        return switch (preset) {
            case "low" -> Map.of("preset", "low", "width", 512, "height", 288);
            case "medium" -> Map.of("preset", "medium", "width", 1920, "height", 1080);
            case "high" -> Map.of("preset", "high", "width", 2560, "height", 1440);
            default -> throw new IllegalArgumentException("Quality must be low, medium or high");
        };
    }
    /** A passive overlay (no cursor, the player keeps moving) instead of a menu. */
    public ScreenBuilder overlay() { presentation.put("mode", "overlay"); presentation.put("cursor", "text"); return this; }
    /** Cursor icon: cross, block, text or arrow. */
    public ScreenBuilder cursor(String shape) { presentation.put("cursor", shape); return this; }

    /** A text label; {@code color} is the text colour (#RRGGBB, # optional). */
    public ScreenBuilder text(String elementId, String text, double x, double y, double w, double h, String color) {
        var e = element(elementId, "label", text, x, y, w, h);
        style(e).put("textColor", hex(color));
        return this;
    }
    /** Text with Ultimate UI's default 200x32 box and white text. */
    public ScreenBuilder text(String elementId, String text, double x, double y) { return text(elementId, text, x, y, 200, 32, "#ffffff"); }
    /** A solid box (no text, not clickable). */
    public ScreenBuilder block(String elementId, double x, double y, double w, double h, String color) {
        var e = element(elementId, "decoration", "", x, y, w, h);
        e.put("background", true); e.put("border", false);
        style(e).put("fillColor", hex(color));
        return this;
    }
    /** A clickable box with text; {@code actions} are page-format maps ({@code Map.of("type","message","value","Hi")}), may be empty. */
    public ScreenBuilder button(String elementId, String text, double x, double y, double w, double h, String color, List<Map<String, Object>> actions) {
        var e = element(elementId, "button", text, x, y, w, h);
        e.put("background", true); e.put("border", true);
        var style = style(e);
        style.put("fillColor", hex(color)); style.put("outlineWidth", 1); style.put("radius", .12);
        style.put("hoverColor", "#53dbc1"); style.put("hoverEffect", "glow");
        if (actions != null && !actions.isEmpty()) logic(elementId).put("actions", List.copyOf(actions));
        return this;
    }
    /** A PNG image (the bytes of a .png file), scaled into the box. */
    public ScreenBuilder image(String elementId, byte[] png, double x, double y, double w, double h) {
        var e = element(elementId, "label", "", x, y, w, h);
        e.put("image", Base64.getEncoder().encodeToString(Objects.requireNonNull(png)));
        return this;
    }
    /**
     * Sets any field of an element added before, by its JSON name: "text", "layer", "shape" (rectangle, rounded,
     * ellipse), "border", "action", or "style.X" (textScale, textCase, textOutline, textShadow, radius, rotation,
     * opacity, gradientColor, hoverEffect, blur, backdropBlur, shadowBlur...).
     */
    public ScreenBuilder property(String elementId, String path, Object value) {
        var e = existing(elementId);
        if (path.startsWith("style.")) style(e).put(path.substring(6), value instanceof String s && s.matches("[0-9a-fA-F]{6}") ? "#" + s : value);
        else if (path.startsWith("logic.")) logic(elementId).put(path.substring(6), value);
        else if (List.of("text", "layer", "shape", "border", "action", "background", "kind").contains(path)) e.put(path, value);
        else throw new IllegalArgumentException("Unknown element property " + path);
        return this;
    }
    /** Element logic in the page format: actions, on-hover, tooltip, keyframes, hidden, visible-if, list... */
    public ScreenBuilder logic(String elementId, Map<String, Object> value) { logic(elementId).putAll(value); return this; }
    /** Lets the player pick the element up and drop it on another one (the element becomes clickable). */
    public ScreenBuilder draggable(String elementId) {
        var e = existing(elementId);
        e.put("kind", "button");
        logic(elementId).put("draggable", true);
        return this;
    }
    /** Screen logic in the page format: variables, functions, keybinds, behavior, command, animation, on-open... */
    public ScreenBuilder screenLogic(Map<String, Object> value) {
        if (logic == null) logic = new LinkedHashMap<>();
        logic.putAll(value);
        return this;
    }

    /** The screen; IllegalArgumentException names the first problem. */
    public Screen build() {
        var root = new LinkedHashMap<String, Object>();
        root.put("version", 9); root.put("id", id); root.put("title", title); root.put("quality", quality); root.put("presentation", presentation);
        if (logic != null) root.put("logic", logic);
        var list = new ArrayList<Object>();
        for (var e : elements.values()) {
            var copy = new LinkedHashMap<>(e);
            if (copy.get("logic") instanceof Map<?, ?> m && m.isEmpty()) copy.remove("logic");
            list.add(copy);
        }
        root.put("elements", list);
        return ScreenCodec.decode(json(root));
    }

    private Map<String, Object> element(String elementId, String kind, String text, double x, double y, double w, double h) {
        if (elementId == null || !elementId.matches("[a-z][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("Element id must be a-z, 0-9, _ or - (1-32, starting with a letter)");
        if (elements.containsKey(elementId)) throw new IllegalArgumentException("Duplicate element id " + elementId);
        if (!(w > 0 && h > 0)) throw new IllegalArgumentException("Element " + elementId + " needs a positive size");
        var e = new LinkedHashMap<String, Object>();
        e.put("id", elementId); e.put("text", dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.normalize(Objects.requireNonNull(text)));
        e.put("x", round((x + w / 2) / width * 2 - 1)); e.put("y", round(1 - (y + h / 2) / height * 2));
        e.put("width", round(w / width * 2)); e.put("height", round(h / height * 2));
        e.put("kind", kind); e.put("action", ""); e.put("shape", "rectangle"); e.put("border", false); e.put("messageCondition", null);
        e.put("background", false); e.put("image", null);
        var style = new LinkedHashMap<String, Object>();
        style.put("opacity", 1); style.put("fillColor", "#28384b"); style.put("borderColor", "#ffffff"); style.put("textColor", "#ffffff"); style.put("shadow", false);
        style.put("rotation", 0); style.put("radius", 0); style.put("outlineWidth", 0); style.put("hoverColor", null); style.put("hoverEffect", "none");
        style.put("textAlign", "center"); style.put("wrapWidth", 0); style.put("brightness", 1);
        style.put("textScale", 1); style.put("textOutline", null); style.put("textShadow", false);
        style.put("gradientColor", null); style.put("gradientDirection", "vertical"); style.put("textGradient", null); style.put("textCase", "original");
        e.put("style", style); e.put("layer", Math.min(128, elements.size())); e.put("actionArea", null);
        e.put("logic", new LinkedHashMap<String, Object>());
        elements.put(elementId, e);
        return e;
    }
    private Map<String, Object> existing(String elementId) {
        var e = elements.get(elementId);
        if (e == null) throw new IllegalArgumentException("No element " + elementId + " yet");
        return e;
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> style(Map<String, Object> e) { return (Map<String, Object>) e.get("style"); }
    @SuppressWarnings("unchecked") private Map<String, Object> logic(String elementId) { return (Map<String, Object>) existing(elementId).get("logic"); }
    private static String hex(String color) {
        String value = Objects.requireNonNull(color).trim();
        if (!value.startsWith("#")) value = "#" + value;
        if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Colours are #RRGGBB, got " + color);
        return value.toLowerCase(Locale.ROOT);
    }
    /** Coordinates are kept to 1e-9: inside the -1..1 bounds even after the pixel conversion's rounding error. */
    private static double round(double value) { return Math.round(value * 1e9) / 1e9; }

    // A minimal JSON writer for maps, lists, strings, numbers, booleans and null (the codec does the validation).
    static String json(Object value) {
        var out = new StringBuilder();
        write(out, value);
        return out.toString();
    }
    private static void write(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append("null");
            case String s -> quote(out, s);
            case Boolean b -> out.append(b);
            case Integer i -> out.append(i);
            case Long l -> out.append(l);
            case Number n -> {
                double d = n.doubleValue();
                if (!Double.isFinite(d)) throw new IllegalArgumentException("Numbers must be finite");
                var plain = new BigDecimal(String.valueOf(d)).stripTrailingZeros();
                out.append(plain.scale() < 0 ? plain.setScale(0).toPlainString() : plain.toPlainString());
            }
            case Map<?, ?> map -> {
                out.append('{'); boolean first = true;
                for (var entry : map.entrySet()) {
                    if (!first) out.append(','); first = false;
                    quote(out, String.valueOf(entry.getKey())); out.append(':'); write(out, entry.getValue());
                }
                out.append('}');
            }
            case List<?> list -> {
                out.append('['); boolean first = true;
                for (Object item : list) { if (!first) out.append(','); first = false; write(out, item); }
                out.append(']');
            }
            default -> throw new IllegalArgumentException("Unsupported value " + value.getClass().getSimpleName() + " (use maps, lists, text, numbers and booleans)");
        }
    }
    private static void quote(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c)); else out.append(c); }
            }
        }
        out.append('"');
    }
}
