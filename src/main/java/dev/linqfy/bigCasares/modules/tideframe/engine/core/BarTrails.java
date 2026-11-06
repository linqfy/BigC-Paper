package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fighting-game "recent damage" trail for live bars: when a bar drops, the lost part stays visible in a
 * lighter colour for a moment and then drains away. One instance per viewer; call once per tick.
 * Values go in as "0.6200" and come out as "0.6200|0.8000" while a trail is showing.
 */
public final class BarTrails {
    public static final int HOLD_TICKS = 10;
    public static final double DRAIN_PER_TICK = .04;
    private record Trail(double value, int hold) { }
    private final Map<String, Trail> trails = new HashMap<>();

    public Map<String, String> apply(Screen screen, Map<String, String> values) {
        boolean any = false;
        for (var element : screen.elements()) if (LiveText.isBar(element) && values.containsKey(element.id())) { any = true; break; }
        if (!any) { trails.clear(); return values; }
        var out = new LinkedHashMap<>(values);
        for (var element : screen.elements()) {
            // Timers count down on purpose: no damage trail.
            if (!LiveText.isBar(element) || "timer".equals(LiveText.barSource(element))) continue;
            String raw = values.get(element.id());
            if (raw == null) continue;
            double now;
            try { now = Double.parseDouble(raw.contains("|") ? raw.substring(0, raw.indexOf('|')) : raw); } catch (NumberFormatException bad) { continue; }
            var previous = trails.get(element.id());
            Trail next;
            if (previous == null || now >= previous.value()) next = new Trail(now, 0);
            else if (previous.hold() < HOLD_TICKS) next = new Trail(previous.value(), previous.hold() + 1);
            else next = new Trail(Math.max(now, previous.value() - DRAIN_PER_TICK), previous.hold() + 1);
            // A fresh drop restarts the hold so chained hits read as one chunk.
            if (previous != null && now < previous.value() && previous.hold() > HOLD_TICKS && lastFill.getOrDefault(element.id(), now) > now)
                next = new Trail(next.value(), 0);
            lastFill.put(element.id(), now);
            trails.put(element.id(), next);
            if (next.value() > now + 1e-9) out.put(element.id(), String.format(Locale.ROOT, "%.4f|%.4f", now, next.value()));
        }
        return out;
    }
    private final Map<String, Double> lastFill = new HashMap<>();
}
