package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewState;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import java.util.Map;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** Builds the title components for a screen's live text and progress bars. Pure: safe on any thread. */
final class LiveLayer {
    private LiveLayer() { }
    static Component build(HudAssets assets, Screen screen, Map<String, String> values, int quarters) {
        return build(assets, screen, values, quarters, ViewState.EMPTY);
    }
    /**
     * {@code view}: hidden runtime elements draw no text, moved ones move their text too, tints multiply the text
     * colour, and list rows draw the values of "id#row".
     */
    static Component build(HudAssets assets, Screen screen, Map<String, String> values, int quarters, ViewState view) {
        if (values.isEmpty()) return Component.empty();
        int scale = screen.presentation().scale();
        Key hud = Key.key(HudAssets.fontName(quarters));
        Component layer = Component.empty();
        for (var element : screen.elements()) {
            boolean runtime = view.runtime().contains(element.id());
            if (runtime && !view.shown(element.id())) continue;
            int dx = runtime ? view.offset(element.id()) : 0;
            String tint = runtime ? view.tint(element.id()) : null;
            var spec = element.logic().list();
            if (spec != null) {
                for (int i = 0; i < Math.min(view.rows(element.id()), spec.rows()); i++) {
                    String value = values.get(ListRows.rowId(element.id(), i));
                    if (value != null) layer = layer.append(one(assets, screen, ListRows.row(element, i), value, quarters, scale, hud, dx, tint));
                }
                continue;
            }
            String value = values.get(element.id());
            if (value != null) layer = layer.append(one(assets, screen, element, value, quarters, scale, hud, dx, tint));
        }
        return layer;
    }
    private static Component one(HudAssets assets, Screen screen, Screen.Element element, String value, int quarters, int scale, Key hud, int dx, String tint) {
        if (LiveText.isBar(element)) return shifted(bar(assets, screen, element, value, quarters, scale, hud), dx * scale, hud);
        if (assets.liveFont(screen, element, quarters) == null) return Component.empty();
        // Words in their own colour: LivePlaceholders sends the filled-in runs in RichText.compose's frame.
        var runs = !screen.legacyText() && RichText.hasTags(element.text()) ? RichText.decompose(value) : java.util.List.of(new RichText.Run(value, null));
        // Text that shrinks to fit (TextFit) reads one character more than fits at half size, to know when to cut it.
        boolean fit = TextFit.allowed(screen);
        int budget = fit ? TextFit.maxChars(element, TextFit.HALF) + 1 : LiveText.maxChars(element), length = 0;
        var parts = new java.util.ArrayList<RichText.Run>();
        for (var run : runs) {
            String text = LiveText.sanitize(run.text(), budget - length, element.style().upperCase());
            if (!text.isEmpty()) { parts.add(new RichText.Run(text, run.color())); length += text.length(); }
            if (length >= budget) break;
        }
        if (length == 0) return Component.empty();
        int size = TextFit.FULL;
        if (fit) {
            // The largest size the pack has a font for at which the value fits; past the smallest, it is cut with "…".
            var sizes = new java.util.ArrayList<Integer>();
            for (int candidate : TextFit.SIZES) if (assets.liveFont(screen, element, quarters, null, candidate) != null) sizes.add(candidate);
            size = TextFit.size(element, length, sizes);
            int max = TextFit.maxChars(element, size);
            if (length > max) { parts = cut(parts, max); length = Math.min(length, max); }
            if (length == 0) return Component.empty();
        }
        String font = assets.liveFont(screen, element, quarters, null, size);
        int pad = HudAssets.livePad(element), textScale = element.style().textScale();
        // The glyph cell starts on a whole logical pixel; shrunk text takes the nearest one that keeps it in its box.
        int prefix = (TextFit.cellLeft(element, length, size, pad) - 128 + dx) * scale;
        long advanceQuarters = (long) length * 6 * textScale * size * scale; // quarter logical px
        int advance = (int) (advanceQuarters / 4);
        Component glyphs = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            var part = parts.get(i);
            // Styled text bakes its colours into the font (one font per colour); plain text is tinted by the component colour.
            // Past the pack's font budget a coloured word has no font of its own: it is drawn in the element's colour.
            String partFont = assets.liveFont(screen, element, quarters, part.color(), size);
            if (partFont == null) partFont = font;
            String base = HudAssets.liveColorsBaked(element) ? "#ffffff" : part.color() == null ? element.style().textColor() : part.color();
            TextColor color = TextColor.fromHexString(DynamicLayer.multiply(base, tint));
            String text = HudAssets.liveGlyphs(element, part.text(), scale, quarters, size);
            // An odd count at three quarters ends half a pixel past a whole one: the font's half step back returns it.
            if (i == parts.size() - 1 && advanceQuarters % 4 != 0) text += HudAssets.LIVE_HALF_BACK;
            glyphs = glyphs.append(HudText.paint(Component.text(text).font(Key.key(partFont)), assets, screen, color));
        }
        return Component.text(HudAssets.hudSpace(prefix)).font(hud).append(glyphs)
            .append(Component.text(HudAssets.hudSpace(-prefix - advance)).font(hud));
    }
    /** {@code parts} cut to {@code max} characters in all, the last one an ellipsis in the colour of the text it ends. */
    private static java.util.ArrayList<RichText.Run> cut(java.util.List<RichText.Run> parts, int max) {
        var out = new java.util.ArrayList<RichText.Run>();
        if (max <= 0) return out;
        int left = max - 1;
        for (var part : parts) {
            if (left == 0) break;
            String text = part.text().length() <= left ? part.text() : part.text().substring(0, left);
            out.add(new RichText.Run(text, part.color()));
            left -= text.length();
        }
        if (out.isEmpty()) out.add(new RichText.Run(String.valueOf(TextFit.ELLIPSIS), parts.getFirst().color()));
        else { var last = out.removeLast(); out.add(new RichText.Run(last.text() + TextFit.ELLIPSIS, last.color())); }
        return out;
    }
    private static Component shifted(Component inner, int dx, Key hud) {
        if (dx == 0 || inner.equals(Component.empty())) return inner;
        return Component.text(HudAssets.hudSpace(dx)).font(hud).append(inner).append(Component.text(HudAssets.hudSpace(-dx)).font(hud));
    }

    /** {@code value} is the fill fraction 0..1; the fill snaps to whole logical pixels. */
    private static Component bar(HudAssets assets, Screen screen, Screen.Element element, String value, int quarters, int scale, Key hud) {
        String font = assets.barFont(screen, element, quarters);
        if (font == null) return Component.empty();
        int[] box = LiveText.barBox(element);
        int filled = LiveText.barPixels(value, box[2]), trail = LiveText.barTrailPixels(value, box[2]);
        if (trail == 0) return Component.empty();
        int prefix = (box[0] - 128) * scale;
        return Component.text(HudAssets.hudSpace(prefix)).font(hud)
            .append(HudText.paint(Component.text(HudAssets.barGlyphs(filled, trail, HudAssets.barCellWidth(element))).font(Key.key(font)), assets, screen, NamedTextColor.WHITE))
            .append(Component.text(HudAssets.hudSpace(-prefix - trail * scale)).font(hud));
    }
}
