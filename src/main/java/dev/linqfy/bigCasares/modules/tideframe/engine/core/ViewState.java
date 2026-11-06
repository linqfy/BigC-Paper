package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Motion;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the runtime layers of one open screen currently look like: which are hidden (by actions or by their
 * visible-if condition), how far each is moved sideways, its tint, when its animation started, how keyframe
 * animations are playing, how many list rows show data, which element the player is carrying, and which PNG or GIF
 * animations are paused on a frame. Immutable: the owner thread publishes a new state and the network thread draws
 * whatever it last saw.
 */
public record ViewState(Set<String> runtime, Set<String> hidden, Set<String> gated, Map<String, Integer> offsets,
                        Map<String, String> tints, Map<String, Long> started, Map<String, Play> plays, Map<String, Integer> rows,
                        String carried, long version, Map<String, Integer> frozen) {
    public static final ViewState EMPTY = new ViewState(Set.of(), Set.of(), Set.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), null, 0, Map.of());
    /** Tint of the element being carried (drag and drop). */
    public static final String CARRY_TINT = "#ffe27a";

    /** A keyframe playback: from frame {@code from} at {@code started} (nanoTime), forwards or back to frame 0. */
    public record Play(long started, int from, boolean reverse) {
        /** Resting on the first frame until something starts it. */
        public static final Play REST = new Play(0, 0, true);
    }

    public ViewState {
        runtime = Set.copyOf(runtime); hidden = Set.copyOf(hidden); gated = Set.copyOf(gated);
        offsets = Map.copyOf(offsets); tints = Map.copyOf(tints); started = Map.copyOf(started); plays = Map.copyOf(plays); rows = Map.copyOf(rows);
        frozen = Map.copyOf(frozen);
    }
    /**
     * Fresh state when a screen opens: hidden elements and tooltips start hidden, frame animations and "open"/"loop"
     * keyframes start now, "hover"/"action" keyframes rest on their first frame, lists are empty.
     */
    public static ViewState initial(Screen screen, long now) {
        var runtime = screen.runtimeIds();
        var hidden = new HashSet<String>(); var started = new HashMap<String, Long>(); var plays = new HashMap<String, Play>();
        for (var e : screen.elements()) {
            String tip = e.logic().tooltip();
            if (tip != null && !tip.equals(e.id()) && runtime.contains(tip)) hidden.add(tip);
            if (!runtime.contains(e.id())) continue;
            if (e.logic().hidden()) hidden.add(e.id());
            if (e.logic().frames() != null) started.put(e.id(), now);
            var motion = e.logic().motion();
            if (motion != null) plays.put(e.id(), motion.trigger().equals("open") || motion.trigger().equals("loop") ? new Play(now, 0, false) : Play.REST);
        }
        return new ViewState(runtime, hidden, Set.of(), Map.of(), Map.of(), started, plays, Map.of(), null, 1, Map.of());
    }
    public boolean shown(String id) { return !hidden.contains(id) && !gated.contains(id); }
    public int offset(String id) { return offsets.getOrDefault(id, 0); }
    /** Tint for drawing: the carry highlight wins over an assigned tint. */
    public String tint(String id) { return id.equals(carried) ? CARRY_TINT : tints.get(id); }
    public int rows(String id) { return rows.getOrDefault(id, 0); }
    public long started(String id) { return started.getOrDefault(id, 0L); }
    public Play play(String id) { return plays.getOrDefault(id, Play.REST); }
    /** Keyframe frame of {@code element} (of {@code count} baked) at {@code now}. */
    public int motionFrame(Screen.Element element, long now, int count) {
        var motion = element.logic().motion();
        if (motion == null || count <= 1) return 0;
        var play = play(element.id());
        return Motion.frameAt(now - play.started(), motion.intervalMs(), count, play.from(), play.reverse(), motion.trigger().equals("loop") && !play.reverse());
    }

    public ViewState visibility(Collection<String> show, Collection<String> hide, Collection<String> toggle) {
        var next = new HashSet<>(hidden);
        for (String id : show) if (runtime.contains(id)) next.remove(id);
        for (String id : hide) if (runtime.contains(id)) next.add(id);
        for (String id : toggle) if (runtime.contains(id) && !next.remove(id)) next.add(id);
        return next.equals(hidden) ? this : new ViewState(runtime, next, gated, offsets, tints, started, plays, rows, carried, version + 1, frozen);
    }
    /** Elements whose visible-if condition is currently false. */
    public ViewState gate(Set<String> value) {
        return value.equals(gated) ? this : new ViewState(runtime, hidden, value, offsets, tints, started, plays, rows, carried, version + 1, frozen);
    }
    public ViewState offset(String id, int dx) {
        if (!runtime.contains(id) || offset(id) == dx) return this;
        var next = new HashMap<>(offsets);
        if (dx == 0) next.remove(id); else next.put(id, Math.max(-512, Math.min(512, dx)));
        return new ViewState(runtime, hidden, gated, next, tints, started, plays, rows, carried, version + 1, frozen);
    }
    public ViewState tint(String id, String color) {
        if (!runtime.contains(id)) return this;
        if (color != null && !color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Tint must be #RRGGBB");
        var next = new HashMap<>(tints);
        if (color == null) next.remove(id); else next.put(id, color.toLowerCase(java.util.Locale.ROOT));
        return next.equals(tints) ? this : new ViewState(runtime, hidden, gated, offsets, next, started, plays, rows, carried, version + 1, frozen);
    }
    /** Restarts a frame animation (playing, even if it was paused) or plays keyframes from the start (the {@code animation} action). */
    public ViewState restart(String id, long now) {
        if (started.containsKey(id)) {
            var next = new HashMap<>(started); next.put(id, now);
            var thawed = new HashMap<>(frozen); thawed.remove(id);
            return new ViewState(runtime, hidden, gated, offsets, tints, next, plays, rows, carried, version + 1, thawed);
        }
        if (plays.containsKey(id)) {
            var next = new HashMap<>(plays); next.put(id, new Play(now, 0, false));
            return new ViewState(runtime, hidden, gated, offsets, tints, started, next, rows, carried, version + 1, frozen);
        }
        return this;
    }
    /**
     * Hover keyframes: the cursor entered ({@code forward}) or left the element; the playback turns around from
     * whatever frame is showing, so a quick in-and-out never jumps.
     */
    public ViewState hoverMotion(Screen.Element element, boolean forward, long now, int count) {
        var motion = element.logic().motion();
        if (motion == null || !motion.trigger().equals("hover") || !plays.containsKey(element.id())) return this;
        var current = play(element.id());
        if (current.reverse() == !forward) return this;
        int at = motionFrame(element, now, count);
        var next = new HashMap<>(plays); next.put(element.id(), new Play(now, at, !forward));
        return new ViewState(runtime, hidden, gated, offsets, tints, started, next, rows, carried, version + 1, frozen);
    }
    /** Frame a PNG-folder or GIF animation is paused on (0 = the first), or null while it plays. */
    public Integer frozen(String id) { return frozen.get(id); }
    /**
     * A PNG-folder or GIF animation timed from {@code startedAt} (nanoTime) and paused on {@code frame} (0 = the
     * first), or playing when {@code frame} is null (the pause, resume and frame actions).
     */
    public ViewState playback(String id, Integer frame, long startedAt) {
        if (!started.containsKey(id)) return this;
        var nextStarted = new HashMap<>(started); nextStarted.put(id, startedAt);
        var nextFrozen = new HashMap<>(frozen);
        if (frame == null) nextFrozen.remove(id); else nextFrozen.put(id, Math.max(0, frame));
        if (nextStarted.equals(started) && nextFrozen.equals(frozen)) return this;
        return new ViewState(runtime, hidden, gated, offsets, tints, nextStarted, plays, rows, carried, version + 1, nextFrozen);
    }
    public ViewState rows(String id, int count) {
        if (!runtime.contains(id) || rows(id) == count) return this;
        var next = new HashMap<>(rows); next.put(id, Math.max(0, count));
        return new ViewState(runtime, hidden, gated, offsets, tints, started, plays, next, carried, version + 1, frozen);
    }
    public ViewState carry(String id) {
        if (java.util.Objects.equals(id, carried)) return this;
        return new ViewState(runtime, hidden, gated, offsets, tints, started, plays, rows, id, version + 1, frozen);
    }

    /**
     * Click targets, lowest layer first (the hit tester visits the last first): static buttons as baked, runtime
     * buttons only while shown and moved by their offset, and each list row that shows data ({@code id#row}).
     */
    public List<HitTester.Rect> targets(Screen screen) {
        var out = new ArrayList<HitTester.Rect>();
        var buttons = screen.elements().stream().filter(e -> e.kind().equals("button")).sorted(Comparator.comparingInt(Screen.Element::layer)).toList();
        for (var e : buttons) {
            if (!runtime.contains(e.id())) { out.add(rect(e.id(), e, 0)); continue; }
            if (!shown(e.id())) continue;
            int dx = offset(e.id());
            if (e.logic().list() != null) {
                for (int i = 0; i < Math.min(rows(e.id()), e.logic().list().rows()); i++) out.add(rect(ListRows.rowId(e.id(), i), ListRows.row(e, i), dx));
            } else out.add(rect(e.id(), e, dx));
        }
        return List.copyOf(out);
    }
    private static HitTester.Rect rect(String id, Screen.Element e, int dx) {
        var a = e.actionArea();
        double shift = dx / 128.0;
        return a == null ? new HitTester.Rect(id, e.x() + shift, e.y(), e.width(), e.height())
                : new HitTester.Rect(id, a.x() + shift, a.y(), a.width(), a.height());
    }
}
