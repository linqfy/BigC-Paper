package dev.linqfy.bigCasares.modules.tideframe.engine.edit;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Converts an Ultimate UI page (its YAML format, as documented publicly) into a Tideframe schema-9 screen.
 * Positions are read in the page's pixel space (screen.width x screen.height, 1920x1080 by default, top-left
 * corners), components are expanded (params, case and loop blocks), list layouts are laid out once, and
 * actions, variables, functions, keybinds, behaviour, visibility, animation frames and inputs carry over.
 * What Tideframe cannot do without shaders (live layouts, scrolling frames, zoom, item icons, effects) is
 * reported as warnings instead of failing.
 */
public final class UltimateUiImporter {
    /** Files the importer may read: images by name ("icons/coin"), components by name. Null when missing. */
    public interface Sources {
        byte[] image(String name);
        String component(String name);
        /** contents/effects/{name}.yml, or null (open/close and hover effects). */
        default String effect(String name) { return null; }
    }
    /** The screen, what could not be converted, and animation folders to copy (Ultimate UI path → Tideframe folder). */
    public record Result(Screen screen, List<String> warnings, Map<String, String> frameFolders) { }

    private static final Set<String> KNOWN_ACTIONS = Set.of("command", "player", "console", "message", "actionbar", "action-bar", "sound", "teleport",
            "delay", "wait", "give", "redirect", "open", "back", "close", "show", "hide", "toggle", "visibility", "variable", "set", "condition", "if",
            "permission", "function", "call", "input", "offset", "move", "tint", "color", "animation", "animate", "scroll");
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]{0,31}");
    private final Sources sources;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, String> frameFolders = new LinkedHashMap<>();
    private final Map<String, String> ids = new HashMap<>();
    private final Set<String> used = new LinkedHashSet<>();
    private double pageWidth = 1920, pageHeight = 1080;
    private int generated;

    private UltimateUiImporter(Sources sources) { this.sources = sources; }

    public static Result convert(String yaml, String fileName, Sources sources) {
        var importer = new UltimateUiImporter(sources);
        return importer.run(load(yaml), fileName);
    }
    @SuppressWarnings("unchecked")
    static Map<String, Object> load(String yaml) {
        Object value = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("The page must be a YAML map");
        return (Map<String, Object>) map;
    }

    private Result run(Map<String, Object> page, String fileName) {
        String name = text(page.get("name"), fileName.replaceFirst("\\.ya?ml$", ""));
        String screenId = uniqueId(name, false);
        if (page.get("screen") instanceof Map<?, ?> screen) {
            pageWidth = number(screen.get("width"), 1920); pageHeight = number(screen.get("height"), 1080);
            if (pageWidth < 16 || pageHeight < 16) { pageWidth = 1920; pageHeight = 1080; warn("screen width/height ignored (too small)"); }
            for (Object key : screen.keySet()) if (!Set.of("width", "height").contains(String.valueOf(key))) warn("screen." + key + " (cursor and hitbox settings) has no Tideframe equivalent; Tideframe has its own cursor settings");
        }
        for (String key : List.of("animation-loop", "zoom", "hud"))
            if (page.containsKey(key)) warn(key + ": timeline looping and zoom are not available without shaders");
        var blocks = new ArrayList<Map<String, Object>>();
        expand(page.get("blocks"), Map.of(), blocks, 0);
        // First pass: ids, so actions can refer to renamed elements.
        for (var block : blocks) if (block.get("id") != null) ids.putIfAbsent(String.valueOf(block.get("id")), uniqueId(String.valueOf(block.get("id")), true));
        var flat = new ArrayList<Placed>();
        for (var block : blocks) place(block, 0, 0, pageWidth, pageHeight, flat, false);
        var layers = layerRanks(flat);
        var elements = new ArrayList<Screen.Element>();
        for (var placed : flat) {
            if (elements.size() >= 128) { warn("only the first 128 elements were imported (Tideframe's limit)"); break; }
            var element = element(placed, layers.get(placed.layer()));
            if (element != null) elements.add(element);
        }
        var logic = screenLogic(page);
        var screen = new Screen(screenId, title(name), elements, RenderQuality.custom(1024, 576), new Screen.Presentation("menu", 1, "arrow"), logic);
        try { screen.validateActionAreas(); }
        catch (IllegalArgumentException overlap) { warn(overlap.getMessage() + "; the later button wins where they overlap. Move them apart in the editor"); }
        for (String problem : screen.logicProblems()) warn(problem);
        return new Result(screen, List.copyOf(new LinkedHashSet<>(warnings)), Map.copyOf(frameFolders));
    }
    private void warn(String message) { warnings.add(message); }

    // ----- components, case and loop blocks -----
    /**
     * Nested loops multiply (four loops of 128 are 268 million blocks), so expansion has two ceilings: blocks kept, far
     * above the 128 elements a page can hold, and blocks looked at, for loops whose blocks produce nothing.
     */
    private static final int MAX_BLOCKS = 1024, MAX_VISITS = 100_000;
    private int visits;
    private boolean truncated;
    private boolean budgetLeft(List<Map<String, Object>> out) {
        if (out.size() < MAX_BLOCKS && visits < MAX_VISITS) return true;
        if (!truncated) { truncated = true; warn("components and loops expand to too many blocks; the rest was skipped"); }
        return false;
    }
    @SuppressWarnings("unchecked")
    private void expand(Object value, Map<String, String> params, List<Map<String, Object>> out, int depth) {
        if (value == null) return;
        if (!(value instanceof List<?> list)) { warn("blocks must be a list"); return; }
        if (depth > 8) { warn("components nest more than 8 levels; the rest was skipped"); return; }
        for (Object item : list) {
            if (!budgetLeft(out)) return;
            visits++;
            if (!(item instanceof Map<?, ?> raw)) continue;
            var block = (Map<String, Object>) substitute(raw, params);
            if (block.containsKey("component")) {
                String name = String.valueOf(block.get("component"));
                String yaml = sources.component(name);
                if (yaml == null) { warn("component " + name + " not found; skipped"); continue; }
                var component = load(yaml);
                var merged = new LinkedHashMap<String, String>();
                if (component.get("params") instanceof Map<?, ?> defaults) defaults.forEach((k, v) -> merged.put(String.valueOf(k), String.valueOf(v)));
                if (block.get("params") instanceof Map<?, ?> given) given.forEach((k, v) -> merged.put(String.valueOf(k), String.valueOf(v)));
                expand(component.get("blocks"), merged, out, depth + 1);
            } else if (block.containsKey("case")) {
                String chosen = String.valueOf(block.get("case")).trim();
                Object branch = block.get(chosen);
                if (branch == null && (chosen.equals("true") || chosen.equals("false"))) branch = block.get(Boolean.parseBoolean(chosen));
                expand(branch, params, out, depth + 1);
            } else if (block.containsKey("loop")) {
                int count = (int) Math.max(0, Math.min(128, Math.round(evaluate(String.valueOf(block.get("loop")), 0))));
                Object template = block.get("block");
                for (int i = 0; i < count && budgetLeft(out); i++) {
                    var scoped = new LinkedHashMap<>(params); scoped.put("val", String.valueOf(i));
                    expand(template instanceof List<?> ? template : List.of(template), scoped, out, depth + 1);
                }
            } else out.add(block);
        }
    }
    private static final Pattern PARAM = Pattern.compile("\\$?\\{([A-Za-z_][A-Za-z0-9_]{0,31})}");
    /** ${param} and {param} inside every string of a component; {val} inside loops. */
    private static Object substitute(Object value, Map<String, String> params) {
        if (params.isEmpty()) return value;
        if (value instanceof String s) {
            Matcher m = PARAM.matcher(s);
            var out = new StringBuilder();
            while (m.find()) { String v = params.get(m.group(1)); m.appendReplacement(out, Matcher.quoteReplacement(v == null ? m.group() : v)); }
            return m.appendTail(out).toString();
        }
        if (value instanceof Map<?, ?> map) { var out = new LinkedHashMap<String, Object>(); map.forEach((k, v) -> out.put(String.valueOf(k), substitute(v, params))); return out; }
        if (value instanceof List<?> list) return list.stream().map(v -> substitute(v, params)).toList();
        return value;
    }

    // ----- layout -----
    private record Placed(Map<String, Object> block, double x, double y, double width, double height, double layer) { }
    /** Places a block (top-left pixel corner) and its children; list layouts are resolved once, here. */
    private void place(Map<String, Object> block, double originX, double originY, double boxWidth, double boxHeight, List<Placed> out, boolean laidOut) {
        var size = block.get("size") instanceof Map<?, ?> s ? s : Map.of();
        var position = block.get("position") instanceof Map<?, ?> p ? p : Map.of();
        double width = number(size.get("width"), 100), height = number(size.get("height"), 100);
        double x = laidOut ? originX : originX + number(position.get("x"), 0), y = laidOut ? originY : originY + number(position.get("y"), 0);
        out.add(new Placed(block, x, y, width, height, number(block.get("layer"), 0)));
        if (!(block.get("children") instanceof List<?> children) || children.isEmpty()) return;
        if (block.containsKey("scroll")) warn("scrolling frame " + block.get("id") + " became static (no scrolling without shaders); children that do not fit were left out");
        if (block.containsKey("filter")) warn("filter on " + block.get("id") + " is not imported; use a Tideframe list with a filter variable");
        var kids = new ArrayList<Map<String, Object>>();
        for (Object child : children) if (child instanceof Map<?, ?> c) { @SuppressWarnings("unchecked") var m = (Map<String, Object>) c; kids.add(m); if (m.get("id") != null) ids.putIfAbsent(String.valueOf(m.get("id")), uniqueId(String.valueOf(m.get("id")), true)); }
        if (!(block.get("listLayout") instanceof Map<?, ?> layout)) { for (var child : kids) place(child, x, y, width, height, out, false); return; }
        boolean horizontal = "horizontal".equals(String.valueOf(layout.get("direction")).toLowerCase(Locale.ROOT));
        boolean wraps = Boolean.TRUE.equals(layout.get("wraps"));
        double spacing = number(layout.get("spacing"), 0), lineSpacing = number(layout.get("lineSpacing"), spacing);
        double padLeft, padTop, padRight, padBottom;
        if (layout.get("padding") instanceof Map<?, ?> pad) { padLeft = number(pad.get("left"), 0); padTop = number(pad.get("top"), 0); padRight = number(pad.get("right"), 0); padBottom = number(pad.get("bottom"), 0); }
        else { double pad = number(layout.get("padding"), 0); padLeft = padTop = padRight = padBottom = pad; }
        padLeft = number(layout.get("paddingLeft"), padLeft); padTop = number(layout.get("paddingTop"), padTop);
        padRight = number(layout.get("paddingRight"), padRight); padBottom = number(layout.get("paddingBottom"), padBottom);
        String sort = layout.get("sortOrder") == null ? "layout_order" : String.valueOf(layout.get("sortOrder"));
        if (!sort.equals("none")) kids.sort((a, b) -> switch (sort) {
            case "name" -> naturalKey(text(a.get("name"), text(a.get("id"), ""))).compareTo(naturalKey(text(b.get("name"), text(b.get("id"), ""))));
            case "id" -> naturalKey(text(a.get("id"), "")).compareTo(naturalKey(text(b.get("id"), "")));
            default -> Double.compare(number(a.getOrDefault("layoutOrder", a.get("order")), 0), number(b.getOrDefault("layoutOrder", b.get("order")), 0));
        });
        if (Boolean.TRUE.equals(layout.get("sortDescending"))) java.util.Collections.reverse(kids);
        double cellWidth = number(layout.get("cellWidth"), -1), cellHeight = number(layout.get("cellHeight"), -1);
        int maxPerLine = (int) number(layout.get("maxPerLine"), Integer.MAX_VALUE);
        double innerWidth = width - padLeft - padRight, innerHeight = height - padTop - padBottom;
        double cursorX = 0, cursorY = 0, line = 0; int inLine = 0, dropped = 0;
        for (var child : kids) {
            var childSize = child.get("size") instanceof Map<?, ?> s ? s : Map.of();
            double w = cellWidth > 0 ? cellWidth : number(childSize.get("width"), 64), h = cellHeight > 0 ? cellHeight : number(childSize.get("height"), 64);
            if (horizontal) {
                if (inLine > 0 && (inLine >= maxPerLine || wraps && cursorX + w > innerWidth)) { cursorX = 0; cursorY += line + lineSpacing; line = 0; inLine = 0; }
            } else if (inLine > 0 && (inLine >= maxPerLine || wraps && cursorY + h > innerHeight)) { cursorY = 0; cursorX += line + lineSpacing; line = 0; inLine = 0; }
            if (cursorX + w > innerWidth + .5 || cursorY + h > innerHeight + .5) { dropped++; continue; }
            place(child, x + padLeft + cursorX, y + padTop + cursorY, w, h, out, true);
            if (horizontal) { cursorX += w + spacing; line = Math.max(line, h); } else { cursorY += h + spacing; line = Math.max(line, w); }
            inLine++;
        }
        if (dropped > 0) warn(dropped + " children of " + block.get("id") + " did not fit in its box and were left out");
    }
    private static Map<Double, Integer> layerRanks(List<Placed> placed) {
        var values = placed.stream().map(Placed::layer).distinct().sorted().toList();
        var ranks = new HashMap<Double, Integer>();
        boolean fits = values.stream().allMatch(v -> v == Math.rint(v) && v >= -128 && v <= 128);
        for (int i = 0; i < values.size(); i++) ranks.put(values.get(i), fits ? (int) Math.round(values.get(i)) : Math.max(-128, Math.min(128, i - values.size() / 2)));
        return ranks;
    }

    // ----- elements -----
    private Screen.Element element(Placed placed, int layer) {
        var b = placed.block();
        String type = text(b.get("type"), "block").toLowerCase(Locale.ROOT);
        String rawId = b.get("id") == null ? null : String.valueOf(b.get("id"));
        String id = rawId == null ? uniqueId("e" + (++generated), true) : ids.get(rawId);
        double cx = clamp((placed.x() + placed.width() / 2) / pageWidth * 2 - 1, -1, 1), cy = clamp(1 - (placed.y() + placed.height() / 2) / pageHeight * 2, -1, 1);
        double w = clamp(placed.width() / pageWidth * 2, .002, 2), h = clamp(placed.height() / pageHeight * 2, .002, 2);
        if (placed.x() + placed.width() < 0 || placed.y() + placed.height() < 0 || placed.x() > pageWidth || placed.y() > pageHeight) { warn(id + " is outside the page; skipped"); return null; }
        double opacity = clamp(number(b.get("opacity"), 255) / 255.0, 0, 1);
        String fill = color(b.get("color"), "#28384b");
        String textColor = color(b.get("text-color"), "#ffffff");
        String shape = "rectangle"; double radius = .08;
        if (b.get("rounding") != null) {
            shape = "rounded";
            radius = switch (String.valueOf(b.get("rounding")).toLowerCase(Locale.ROOT)) { case "small" -> .04; case "medium" -> .12; case "large" -> .2; default -> .08; };
            if (b.get("rounding") instanceof Map<?, ?>) warn(id + ": custom corner glyphs (rounding.unicode) became plain rounded corners");
        }
        if (b.get("unicode") != null && !String.valueOf(b.get("unicode")).isBlank()) warn(id + ": unicode glyphs from other resource packs are not drawn; use an image");
        String align = text(b.get("align"), "center").toLowerCase(Locale.ROOT);
        if (!Set.of("left", "center", "right").contains(align)) align = "center";
        String kind; String text = ""; boolean background = b.get("color") != null, border = false; ImageAsset image = null;
        var logic = new LinkedHashMap<String, Object>();
        Object actions = convertActions(b.get("actions"), id);
        switch (type) {
            case "text" -> {
                kind = "label"; background = false;
                var parsed = plainText(text(b.get("text"), ""));
                text = parsed[0];
                textColor = parsed[1] != null ? parsed[1] : color(b.get("color"), "#ffffff");
            }
            case "image" -> {
                kind = "label";
                image = image(text(b.get("image"), ""), id);
                background = false;
            }
            case "input" -> {
                kind = "button"; border = true;
                String variable = text(b.get("variable"), text(b.get("id"), "input")).replaceAll("[^a-z0-9_]", "_").toLowerCase(Locale.ROOT);
                if (!variable.matches("[a-z_][a-z0-9_]{0,23}")) variable = "input_" + generated;
                text = "{" + variable + "}";
                var input = new LinkedHashMap<String, Object>();
                input.put("type", "input"); input.put("variable", variable);
                input.put("title", text(b.get("title"), "Enter text")); input.put("label", plainText(text(b.get("placeholder"), ""))[0]);
                if (b.get("default") != null) input.put("default", String.valueOf(b.get("default")));
                input.put("max-length", (int) clamp(number(b.get("max-length"), 50), 1, 256));
                input.put("filter", text(b.get("filter"), "any"));
                if (b.get("allow-empty") instanceof Boolean allow) input.put("allow-empty", allow);
                Object submit = convertActions(b.containsKey("on-submit") ? b.get("on-submit") : b.get("actions"), id);
                if (submit != null) input.put("on-submit", submit);
                Object cancel = convertActions(b.get("on-cancel"), id);
                if (cancel != null) input.put("on-cancel", cancel);
                actions = List.of(input);
                for (String unsupported : List.of("close-on-max", "idle-timeout-ticks", "caret", "font", "padding", "text-size"))
                    if (b.containsKey(unsupported)) warn(id + "." + unsupported + ": the text field is the game's own dialog screen");
            }
            case "item" -> {
                kind = "label";
                text = text(b.get("item"), "ITEM").replaceFirst("^minecraft:", "").replace('_', ' ').toUpperCase(Locale.ROOT);
                warn(id + ": item icons (" + b.get("item") + ") cannot be drawn on the title HUD; shown as its name. Use a PNG image instead");
            }
            case "block" -> { kind = "label"; border = false; }
            default -> { kind = "label"; warn(id + ": element type " + type + " is not supported; imported as a plain box"); }
        }
        if (actions != null || Boolean.TRUE.equals(b.get("draggable"))) kind = "button";
        if (kind.equals("button") && type.equals("block")) border = false;
        if (actions != null) logic.put("actions", actions);
        if (Boolean.TRUE.equals(b.get("draggable"))) logic.put("draggable", true);
        if (b.get("visibility") instanceof Map<?, ?> visibility) {
            boolean visible = !Boolean.FALSE.equals(visibility.get("visible"));
            Object condition = visibility.get("placeholder");
            if (!visible) { logic.put("hidden", true); if (condition != null) warn(id + ": visibility with visible: false and a placeholder became plain hidden"); }
            else if (condition != null) logic.put("visible-if", String.valueOf(condition));
        }
        if (b.get("frames") != null) {
            String source = String.valueOf(b.get("frames")).replace('\\', '/');
            String folder = source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_/-]", "_").replaceAll("/{2,}", "/").replaceAll("^/|/$", "");
            if (folder.isEmpty() || !folder.matches("[a-z0-9_-]{1,32}(/[a-z0-9_-]{1,32}){0,3}")) warn(id + ": animation folder " + source + " has an unusable name; not animated");
            else {
                var frames = new LinkedHashMap<String, Object>();
                frames.put("folder", folder);
                frames.put("interval-ms", (int) clamp(number(b.get("interval-ms"), 50), 50, 60_000));
                frames.put("loop", !Boolean.FALSE.equals(b.get("animation-loop")));
                logic.put("frames", frames);
                frameFolders.put(source, folder);
            }
        }
        ImageAsset hoverImage = null;
        if (b.get("hover") instanceof Map<?, ?> hover) {
            if (hover.get("image") != null) {
                // Ultimate UI's hover image swap: the button shows the other image while the cursor is on it.
                hoverImage = image(text(hover.get("image"), ""), id + ".hover");
                if (hoverImage != null) kind = "button";
            }
            var effect = effect(hover.get("effect"), id + ".hover");
            var keyframes = effect == null ? null : hoverKeyframes(effect);
            if (keyframes != null && !logic.containsKey("frames")) { logic.put("keyframes", keyframes); kind = "button"; }
        }
        if (b.containsKey("listLayout") && !(b.get("children") instanceof List<?>)) warn(id + ": listLayout without children ignored");
        if (text.length() > 512) text = text.substring(0, 512);
        var style = new Screen.Style(opacity, fill, "#ffffff", textColor, false, 0, radius, border ? 1 : 0, kind.equals("button") ? "#53dbc1" : null,
                kind.equals("button") ? "glow" : "none", align, 0, 1);
        // Ultimate UI draws text as written, so imported text keeps its lower case and accents.
        if (!text.isEmpty()) style = style.withTextCase("original");
        if (kind.equals("label") && text.isEmpty() && image == null && actions == null && logic.isEmpty()) kind = "decoration";
        if (kind.equals("decoration")) { border = false; text = ""; }
        var element = new Screen.Element(id, text, cx, cy, w, h, kind, "", shape, border, null, background, image, style, layer, null);
        if (hoverImage != null && kind.equals("button")) element = element.withHoverImage(hoverImage);
        if (!logic.isEmpty()) {
            try { element = element.withLogic(ActionParser.element(logic)); }
            catch (IllegalArgumentException invalid) { warn(id + ": logic not imported (" + invalid.getMessage() + ")"); }
        }
        return element;
    }
    private ImageAsset image(String name, String id) {
        if (name.isBlank()) return null;
        byte[] png = sources.image(name);
        if (png == null) { warn(id + ": image " + name + " not found; imported without it"); return null; }
        try { return ImageAsset.fromPng(png); }
        catch (IllegalArgumentException invalid) { warn(id + ": image " + name + " rejected (" + invalid.getMessage() + ")"); return null; }
    }

    // ----- logic -----
    private ScreenLogic screenLogic(Map<String, Object> page) {
        var logic = new LinkedHashMap<String, Object>();
        if (page.get("behavior") instanceof Map<?, ?> behavior) logic.put("behavior", behavior);
        if (page.get("variables") instanceof Map<?, ?> variables) {
            var values = new LinkedHashMap<String, Object>();
            variables.forEach((k, v) -> values.put(String.valueOf(k), v == null ? "0" : String.valueOf(v)));
            logic.put("variables", values);
        }
        if (page.get("functions") instanceof Map<?, ?> functions) {
            var converted = new LinkedHashMap<String, Object>();
            functions.forEach((name, body) -> {
                if (!(body instanceof Map<?, ?> fn)) return;
                Object actions = convertActions(fn.get("actions"), "function " + name);
                if (actions == null) { warn("function " + name + " has no actions; Ultimate UI does not register it either"); return; }
                var copy = new LinkedHashMap<String, Object>();
                if (fn.get("params") != null) copy.put("params", fn.get("params"));
                copy.put("actions", actions);
                converted.put(String.valueOf(name), copy);
            });
            logic.put("functions", converted);
        }
        if (page.get("keybinds") instanceof List<?> binds) {
            var converted = new ArrayList<Object>();
            for (Object bind : binds) if (bind instanceof Map<?, ?> b) {
                Object actions = convertActions(b.get("actions"), "keybind " + b.get("key"));
                var copy = new LinkedHashMap<String, Object>(); copy.put("key", String.valueOf(b.get("key"))); copy.put("actions", actions == null ? List.of() : actions);
                converted.add(copy);
            }
            logic.put("keybinds", converted);
        }
        if (page.get("command") != null) {
            String command = String.valueOf(page.get("command")).replaceFirst("^/", "").toLowerCase(Locale.ROOT);
            if (command.matches("[a-z0-9_-]{1,32}")) logic.put("command", command); else warn("command " + command + " cannot be registered (a-z, 0-9, _ and -)");
        }
        for (String key : List.of("permission", "open-on-join", "on-open", "on-close")) if (page.get(key) != null)
            logic.put(key, key.startsWith("on-") ? convertActions(page.get(key), key) : page.get(key));
        if (page.get("animation") instanceof Map<?, ?> animation) {
            var converted = pageAnimation(animation);
            if (converted != null) logic.put("animation", converted);
        }
        try { return ActionParser.screen(logic); }
        catch (IllegalArgumentException invalid) {
            warn("page logic not fully imported (" + invalid.getMessage() + "); retrying without keybinds and functions");
            logic.remove("keybinds"); logic.remove("functions");
            try { return ActionParser.screen(logic); } catch (IllegalArgumentException again) { warn("page logic skipped: " + again.getMessage()); return ScreenLogic.EMPTY; }
        }
    }
    /**
     * Ultimate UI actions mostly match Tideframe's. Unknown types run their value as a player command there, so
     * they become command actions here (with a warning); element animation effects are dropped; renamed element
     * ids are rewritten.
     */
    @SuppressWarnings("unchecked")
    private Object convertActions(Object value, String where) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> map && map.containsKey("actions") && !map.containsKey("type")) value = map.get("actions");
        if (!(value instanceof List<?> list)) { warn(where + ": actions must be a list"); return null; }
        var out = new ArrayList<Object>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            var copied = new LinkedHashMap<String, Object>();
            ((Map<Object, Object>) raw).forEach((k, v) -> copied.put(String.valueOf(k), v));
            var action = copied;
            String type = text(action.get("type"), "").toLowerCase(Locale.ROOT);
            if (type.equals("animation") && !action.containsKey("element")) { warn(where + ": animation effects are not imported"); continue; }
            if (!KNOWN_ACTIONS.contains(type)) {
                warn(where + ": unknown action type '" + type + "' runs as a player command, like in Ultimate UI");
                action = new LinkedHashMap<>(Map.of("type", "command", "value", text(action.get("value"), "")));
            }
            for (String key : List.of("element", "elements", "show", "hide")) action.computeIfPresent(key, (k, v) -> renamed(v));
            if (Set.of("show", "hide", "toggle").contains(type) && action.get("value") != null && !action.containsKey("elements") && !action.containsKey("element"))
                action.put("value", String.join(",", ((List<String>) renamed(List.of(String.valueOf(action.get("value")).split("\\s*,\\s*"))))));
            for (String branch : List.of("then", "else", "on-submit", "on-cancel"))
                if (action.get(branch) != null) action.put(branch, convertActions(action.get(branch), where + " " + branch));
            out.add(action);
        }
        return out.isEmpty() ? null : out;
    }
    private Object renamed(Object value) {
        if (value instanceof List<?> list) return list.stream().map(v -> ids.getOrDefault(String.valueOf(v), String.valueOf(v))).toList();
        return ids.getOrDefault(String.valueOf(value), String.valueOf(value));
    }

    // ----- helpers -----
    private String uniqueId(String raw, boolean element) {
        String id = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_").replaceAll("_+", "_").replaceAll("^[^a-z]+", "").replaceAll("[_-]+$", "");
        if (id.isEmpty()) id = element ? "e" : "page";
        if (id.length() > 28) id = id.substring(0, 28);
        if (!element) return ID.matcher(id).matches() ? id : "page";
        String candidate = id; int n = 2;
        while (!used.add(candidate)) candidate = id + "_" + n++;
        return candidate;
    }
    private static String title(String name) {
        String upper = drawable(dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.upper(dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.normalize(name.toUpperCase(Locale.ROOT)))).replace('\n', ' ').replace('_', ' ').trim();
        if (upper.isEmpty()) upper = "PAGE";
        return upper.length() > 40 ? upper.substring(0, 40) : upper;
    }
    private static final Map<String, String> NAMED = Map.ofEntries(Map.entry("0", "#000000"), Map.entry("1", "#0000aa"), Map.entry("2", "#00aa00"),
            Map.entry("3", "#00aaaa"), Map.entry("4", "#aa0000"), Map.entry("5", "#aa00aa"), Map.entry("6", "#ffaa00"), Map.entry("7", "#aaaaaa"),
            Map.entry("8", "#555555"), Map.entry("9", "#5555ff"), Map.entry("a", "#55ff55"), Map.entry("b", "#55ffff"), Map.entry("c", "#ff5555"),
            Map.entry("d", "#ff55ff"), Map.entry("e", "#ffff55"), Map.entry("f", "#ffffff"), Map.entry("black", "#000000"), Map.entry("dark_blue", "#0000aa"),
            Map.entry("dark_green", "#00aa00"), Map.entry("dark_aqua", "#00aaaa"), Map.entry("dark_red", "#aa0000"), Map.entry("dark_purple", "#aa00aa"),
            Map.entry("gold", "#ffaa00"), Map.entry("gray", "#aaaaaa"), Map.entry("dark_gray", "#555555"), Map.entry("blue", "#5555ff"),
            Map.entry("green", "#55ff55"), Map.entry("aqua", "#55ffff"), Map.entry("red", "#ff5555"), Map.entry("light_purple", "#ff55ff"),
            Map.entry("yellow", "#ffff55"), Map.entry("white", "#ffffff"));
    // ----- effects (contents/effects/*.yml) -----
    /** An effect file as a map, or null (with a warning) when it is missing or unreadable. */
    private Map<String, Object> effect(Object name, String where) {
        if (name == null) return null;
        String yaml = sources.effect(String.valueOf(name));
        if (yaml == null) { warn(where + ": effect " + name + " not found in contents/effects; imported without it"); return null; }
        try { return load(yaml); }
        catch (RuntimeException unreadable) { warn(where + ": effect " + name + " is not valid YAML"); return null; }
    }
    private static String easing(Object interpolation) {
        String value = interpolation == null ? "ease-out" : String.valueOf(interpolation).toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (value) {
            case "linear", "ease-in", "ease-out", "ease-in-out" -> value;
            case "smooth", "ease", "in-out" -> "ease-in-out";
            case "in" -> "ease-in";
            default -> "ease-out";
        };
    }
    private double percent(Object value, double fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.endsWith("%") ? evaluate(text.substring(0, text.length() - 1), fallback) : number(value, fallback);
    }
    /**
     * Page open/close effects: a sideways start (start-x / move-x) becomes a slide in, a sideways exit a slide out, and
     * close-reversed after a slide in slides back out; vertical moves, scaling and rotation of the whole page cannot be
     * drawn without re-baking it, so they become the title fade.
     */
    private Map<String, Object> pageAnimation(Map<?, ?> animation) {
        var out = new LinkedHashMap<String, Object>();
        Map<String, Object> openEffect = null;
        if (animation.get("open") instanceof Map<?, ?> open) {
            openEffect = effect(open.get("effect"), "animation.open");
            if (openEffect != null) {
                var transition = new LinkedHashMap<String, Object>();
                double startX = number(openEffect.getOrDefault("start-x", openEffect.get("move-x")), 0);
                int duration = (int) clamp(number(openEffect.get("duration-ms"), 300), 0, 3000);
                transition.put("duration-ms", duration);
                transition.put("easing", easing(openEffect.get("interpolation")));
                if (startX != 0) {
                    transition.put("effect", "slide");
                    transition.put("from", startX < 0 ? "left" : "right");
                    transition.put("distance", (int) clamp(Math.round(Math.abs(startX) * 256 / pageWidth), 1, 256));
                } else {
                    transition.put("effect", "fade");
                    if (number(openEffect.getOrDefault("start-y", openEffect.get("move-y")), 0) != 0 || percent(openEffect.get("amount-percent"), 0) != 0
                            || number(openEffect.get("rotation-deg"), 0) != 0)
                        warn("animation.open: the page cannot move up or down, scale or rotate on the title HUD; it fades in instead");
                }
                out.put("open", transition);
            }
        }
        if (animation.get("close") instanceof Map<?, ?> close) {
            var closeEffect = effect(close.get("effect"), "animation.close");
            if (closeEffect != null) {
                var transition = new LinkedHashMap<String, Object>();
                double moveX = number(closeEffect.getOrDefault("move-x", closeEffect.get("end-x")), 0);
                transition.put("duration-ms", (int) clamp(number(closeEffect.get("duration-ms"), 300), 0, 3000));
                transition.put("easing", easing(closeEffect.get("interpolation")));
                if (moveX != 0) {
                    // A sideways exit slides out towards that side.
                    transition.put("effect", "slide");
                    transition.put("from", moveX < 0 ? "left" : "right");
                    transition.put("distance", (int) clamp(Math.round(Math.abs(moveX) * 256 / pageWidth), 1, 256));
                } else {
                    transition.put("effect", "fade");
                    if (closeEffect.containsKey("move-y") || closeEffect.containsKey("start-y"))
                        warn("animation.close: the page cannot move up or down on the title HUD; it fades out instead");
                }
                out.put("close", transition);
            }
        } else if (openEffect != null && Boolean.TRUE.equals(openEffect.get("close-reversed"))) {
            @SuppressWarnings("unchecked") var open = (Map<String, Object>) out.get("open");
            if (open != null && "slide".equals(open.get("effect"))) {
                // Played backwards, a slide-in leaves towards the side it came from with the mirrored easing.
                var transition = new LinkedHashMap<String, Object>(open);
                transition.put("easing", switch (String.valueOf(open.get("easing"))) { case "ease-in" -> "ease-out"; case "ease-out" -> "ease-in"; default -> open.get("easing"); });
                out.put("close", transition);
            } else out.put("close-reversed", true);
        }
        return out.isEmpty() ? null : out;
    }
    /** A hover effect file → keyframes played forwards on hover and back on leave (move, scale, opacity, rotation). */
    private Map<String, Object> hoverKeyframes(Map<String, Object> effect) {
        int duration = (int) clamp(number(effect.get("duration-ms"), 250), 50, 10_000);
        double amount = percent(effect.get("amount-percent"), 0);
        double scaleX = percent(effect.get("scale-x"), amount), scaleY = percent(effect.get("scale-y"), amount);
        double scale = clamp(1 + (scaleX + scaleY) / 200, .1, 4);
        double x = clamp(number(effect.get("move-x"), 0) * 256 / pageWidth, -256, 256), y = clamp(number(effect.get("move-y"), 0) * 144 / pageHeight, -144, 144);
        double delta = number(effect.get("opacity-delta"), 0);
        double opacity = clamp(1 + (Math.abs(delta) > 1 ? delta / 255 : delta), 0, 1);
        double rotation = clamp(number(effect.get("rotation-deg"), 0), -360, 360);
        var end = new LinkedHashMap<String, Object>();
        end.put("at", duration);
        if (scale != 1) end.put("scale", scale);
        if (x != 0) end.put("x", x);
        if (y != 0) end.put("y", y);
        if (opacity != 1) end.put("opacity", opacity);
        if (rotation != 0) end.put("rotation", rotation);
        if (end.size() == 1) return null;
        var keyframes = new LinkedHashMap<String, Object>();
        keyframes.put("trigger", "hover");
        keyframes.put("easing", easing(effect.get("interpolation")));
        keyframes.put("steps", List.of(Map.of("at", 0), end));
        return keyframes;
    }

    /**
     * Text without colour codes or tags (Tideframe text has one colour), plus the first colour found. Characters the
     * font lacks become '?' (one per emoji); look-alikes such as ♥ or curly quotes become the font's own.
     */
    static String[] plainText(String raw) {
        String first = null;
        Matcher hex = Pattern.compile("(?i)[&§]#([0-9a-f]{6})|<#([0-9a-f]{6})>|[&§]([0-9a-f])|<(black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue|green|aqua|red|light_purple|yellow|white)>").matcher(raw);
        if (hex.find()) {
            if (hex.group(1) != null) first = "#" + hex.group(1).toLowerCase(Locale.ROOT);
            else if (hex.group(2) != null) first = "#" + hex.group(2).toLowerCase(Locale.ROOT);
            else if (hex.group(3) != null) first = NAMED.get(hex.group(3).toLowerCase(Locale.ROOT));
            else first = NAMED.get(hex.group(4));
        }
        String text = dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.normalize(raw.replaceAll("(?i)[&§]#[0-9a-f]{6}|[&§][0-9a-fk-orx]|</?[a-z_#0-9:.'\" -]{1,64}>", ""));
        return new String[]{drawable(text), first};
    }
    /** Keeps what the font draws (printable ASCII, line feeds, accented letters, symbols); the rest becomes '?'. */
    private static String drawable(String text) {
        var out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) { out.append('?'); i++; continue; }
            out.append(c == '\n' || c >= 32 && c <= 126 || dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.EXTRA.indexOf(c) >= 0 ? c : '?');
        }
        return out.toString();
    }
    private static String color(Object value, String fallback) {
        if (value == null) return fallback;
        String hex = String.valueOf(value).trim().replaceFirst("^#", "").replaceFirst("^0x", "");
        if (hex.matches("[0-9a-fA-F]{8}")) hex = hex.substring(2);
        return hex.matches("[0-9a-fA-F]{6}") ? "#" + hex.toLowerCase(Locale.ROOT) : fallback;
    }
    private static String text(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    private double number(Object value, double fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return n.doubleValue();
        String text = String.valueOf(value).trim();
        if (text.contains("%")) { warn("size or position '" + text + "' depends on a placeholder; imported at " + fallback + " (use a {bar:...} or visible-if)"); return fallback; }
        return evaluate(text, fallback);
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    /** + - * / and parentheses over numbers (loop positions such as "50 + (100 * 3)"). */
    static double evaluate(String expression, double fallback) {
        try {
            var parser = new Object() {
                int at; final String s = expression.replace(" ", "");
                double expr() { double v = term(); while (at < s.length() && (s.charAt(at) == '+' || s.charAt(at) == '-')) v = s.charAt(at++) == '+' ? v + term() : v - term(); return v; }
                double term() { double v = factor(); while (at < s.length() && (s.charAt(at) == '*' || s.charAt(at) == '/')) v = s.charAt(at++) == '*' ? v * factor() : v / factor(); return v; }
                double factor() {
                    if (at < s.length() && s.charAt(at) == '-') { at++; return -factor(); }
                    if (at < s.length() && s.charAt(at) == '(') { at++; double v = expr(); if (at >= s.length() || s.charAt(at++) != ')') throw new IllegalArgumentException(); return v; }
                    int start = at;
                    while (at < s.length() && (Character.isDigit(s.charAt(at)) || s.charAt(at) == '.')) at++;
                    return Double.parseDouble(s.substring(start, at));
                }
            };
            double value = parser.expr();
            return parser.at == parser.s.length() && Double.isFinite(value) ? value : fallback;
        } catch (RuntimeException invalid) { return fallback; }
    }
    private static String naturalKey(String name) {
        Matcher m = Pattern.compile("\\d+").matcher(name.toLowerCase(Locale.ROOT));
        var out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, String.format(Locale.ROOT, "%012d", Long.parseLong(m.group().length() > 12 ? m.group().substring(0, 12) : m.group())));
        return m.appendTail(out).toString();
    }
}
