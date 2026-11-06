package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewState;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewportFit;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import java.util.Comparator;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Title components for the runtime layers of a screen (elements that show/hide, move sideways, change tint,
 * animate or repeat as list rows). Each layer was baked into the pack; this only picks which glyphs to send.
 * Pure: safe on any thread.
 */
final class DynamicLayer {
    private DynamicLayer() { }

    static Component build(HudAssets assets, Screen screen, ViewState view, String hover, long now, int quarters) {
        var ids = assets.runtimeIds(screen);
        if (ids.isEmpty()) return Component.empty();
        Key font = Key.key(ViewportFit.font(quarters));
        int scale = screen.presentation().scale();
        Component out = Component.empty();
        for (var e : screen.elements().stream().filter(e -> ids.contains(e.id())).sorted(Comparator.comparingInt(Screen.Element::layer)).toList()) {
            if (!view.shown(e.id())) continue;
            int dx = view.offset(e.id()) * scale;
            String tint = view.tint(e.id());
            var spec = e.logic().list();
            if (spec != null) {
                for (int i = 0; i < Math.min(view.rows(e.id()), spec.rows()); i++) {
                    String row = ListRows.rowId(e.id(), i);
                    out = append(out, assets, screen, row.equals(hover) ? row + "@hover" : row, row, dx, tint, font, quarters);
                }
                continue;
            }
            int frames = assets.frameCount(screen, e.id());
            if (frames > 0 && (e.logic().frames() != null || e.logic().motion() != null)) {
                int k = frame(assets, e, view, now, frames);
                out = append(out, assets, screen, e.id() + "@" + k, e.id() + "@" + k, dx, tint, font, quarters);
                continue;
            }
            out = append(out, assets, screen, e.id().equals(hover) ? e.id() + "@hover" : e.id(), e.id(), dx, tint, font, quarters);
        }
        return out;
    }
    private static Component append(Component out, HudAssets assets, Screen screen, String key, String fallback, int dx, String tint, Key font, int quarters) {
        String glyphs = assets.layer(screen, key, quarters);
        if (glyphs == null) glyphs = assets.layer(screen, fallback, quarters);
        if (glyphs == null || glyphs.isEmpty()) return out;
        String text = dx == 0 ? glyphs : HudAssets.hudSpace(dx) + glyphs + HudAssets.hudSpace(-dx);
        return out.append(HudText.paint(Component.text(assets.displayText(text, quarters)).font(font), assets, screen,
                tint == null ? NamedTextColor.WHITE : TextColor.fromHexString(tint)));
    }
    /** Frame of a PNG-sequence, GIF or keyframe animation at {@code now}; a paused one holds its frame. */
    static int frame(HudAssets assets, Screen.Element e, ViewState view, long now, int frames) {
        if (e.logic().motion() != null) return view.motionFrame(e, now, frames);
        Integer paused = view.frozen(e.id());
        if (paused != null) return Math.min(paused, frames - 1);
        return frameIndex(now - view.started(e.id()), assets.frameInterval(e), frames, e.logic().frames().loop());
    }
    /** Animation frame for the time since the animation (re)started; a finished one-shot animation holds its last frame. */
    static int frameIndex(long elapsedNanos, int intervalMs, int count, boolean loop) {
        if (count <= 1 || elapsedNanos <= 0) return 0;
        long step = elapsedNanos / (intervalMs * 1_000_000L);
        return (int) (loop ? step % count : Math.min(step, count - 1));
    }
    /** Changes whenever {@link #build} would draw something different: the renderer resends the title then. */
    static String key(HudAssets assets, Screen screen, ViewState view, String hover, long now) {
        var ids = assets.runtimeIds(screen);
        if (ids.isEmpty()) return "";
        var key = new StringBuilder().append(view.version()).append('|').append(hover);
        for (var e : screen.elements()) {
            if (!ids.contains(e.id()) || e.logic().frames() == null && e.logic().motion() == null || !view.shown(e.id())) continue;
            int frames = assets.frameCount(screen, e.id());
            if (frames > 1) key.append('|').append(frame(assets, e, view, now, frames));
        }
        return key.toString();
    }
    /** Multiplies two #RRGGBB colours (a tint over a text colour), like the GPU does for glyph colours. */
    static String multiply(String color, String tint) {
        if (tint == null) return color;
        int a = Integer.parseInt(color.substring(1), 16), b = Integer.parseInt(tint.substring(1), 16);
        int r = ((a >> 16) & 255) * ((b >> 16) & 255) / 255, g = ((a >> 8) & 255) * ((b >> 8) & 255) / 255, bl = (a & 255) * (b & 255) / 255;
        return String.format(java.util.Locale.ROOT, "#%02x%02x%02x", r, g, bl);
    }
}
