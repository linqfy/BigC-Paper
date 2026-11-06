package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import java.util.List;

/**
 * Element behaviour (schema 9). {@code actions} replace the legacy single action when present; {@code rightActions}
 * run on right click. {@code hidden} starts the element invisible; {@code visibleIf} shows it only while the condition
 * holds. {@code frames} animates it from a folder of PNG frames. {@code list} repeats it as rows of a data list.
 * {@code draggable} lets the player pick it up with a click and drop it on another element; the target's
 * {@code onDrop} actions then run with {dragged} (the picked element's id) in scope. {@code hoverActions} and
 * {@code leaveActions} run when the cursor enters and leaves the element; {@code tooltip} names an element shown only
 * while the cursor is on this one. {@code motion} animates the element with keyframes. {@code cooldownMs} (0 = none)
 * ignores the element's clicks for that long after one ran something; {@code onCooldown} runs instead, with
 * {cooldown} (seconds left) in scope. The wait belongs to the player while they are online, across menus.
 */
public record ElementLogic(List<UiAction> actions, List<UiAction> rightActions, boolean hidden, String visibleIf,
                           Frames frames, ListSpec list, boolean draggable, List<UiAction> onDrop,
                           List<UiAction> hoverActions, List<UiAction> leaveActions, String tooltip, Motion motion,
                           int cooldownMs, List<UiAction> onCooldown) {
    public static final ElementLogic EMPTY = new ElementLogic(List.of(), List.of(), false, null, null, null, false, List.of());
    /** Longest cooldown: one hour. */
    public static final int MAX_COOLDOWN_MS = 3_600_000;

    public ElementLogic(List<UiAction> actions, List<UiAction> rightActions, boolean hidden, String visibleIf,
                        Frames frames, ListSpec list, boolean draggable, List<UiAction> onDrop) {
        this(actions, rightActions, hidden, visibleIf, frames, list, draggable, onDrop, List.of(), List.of(), null, null);
    }
    public ElementLogic(List<UiAction> actions, List<UiAction> rightActions, boolean hidden, String visibleIf,
                        Frames frames, ListSpec list, boolean draggable, List<UiAction> onDrop,
                        List<UiAction> hoverActions, List<UiAction> leaveActions, String tooltip, Motion motion) {
        this(actions, rightActions, hidden, visibleIf, frames, list, draggable, onDrop, hoverActions, leaveActions, tooltip, motion, 0, List.of());
    }

    /**
     * Frames from plugins/BigCasares/tideframe/engine/images/{folder} (PNG files, or a GIF in that folder or at images/{folder}.gif),
     * shown every {@code intervalMs} (50 ms = one frame per tick). {@link #AUTO} (0, when "interval-ms" is left out)
     * plays PNG folders at 50 ms and a GIF at its own pace.
     */
    public record Frames(String folder, int intervalMs, boolean loop) {
        public static final int AUTO = 0;
        public Frames {
            if (folder == null || !folder.matches("[a-z0-9_-]{1,32}(/[a-z0-9_-]{1,32}){0,3}")) throw new IllegalArgumentException("Invalid frames folder " + folder);
            if (intervalMs != AUTO && (intervalMs < 50 || intervalMs > 60_000)) throw new IllegalArgumentException("Frame interval must be 50 to 60000 ms");
        }
    }
    /**
     * Repeats this element {@code rows} times downwards, {@code spacing} logical pixels apart, filled from the data
     * list {@code source} (set from the API or the screen's variables). Each row's text may use {item}, {index} and
     * {item.field}. {@code filter} names a variable whose text filters the rows (contains, ignoring case).
     */
    public record ListSpec(String source, int rows, int spacing, String filter, List<UiAction> onSelect) {
        public ListSpec {
            if (source == null || !source.matches("[a-z_][a-z0-9_]{0,23}")) throw new IllegalArgumentException("Invalid list source " + source);
            if (rows < 1 || rows > 16) throw new IllegalArgumentException("A list shows 1 to 16 rows");
            if (spacing < 1 || spacing > 72) throw new IllegalArgumentException("List spacing must be 1 to 72 logical pixels");
            if (filter != null) Variables.validName(filter);
            onSelect = List.copyOf(onSelect);
        }
    }

    public ElementLogic {
        actions = List.copyOf(actions); rightActions = List.copyOf(rightActions); onDrop = List.copyOf(onDrop);
        hoverActions = List.copyOf(hoverActions); leaveActions = List.copyOf(leaveActions); onCooldown = List.copyOf(onCooldown);
        if (cooldownMs < 0 || cooldownMs > MAX_COOLDOWN_MS) throw new IllegalArgumentException("A cooldown must be 0 to 3600 seconds");
        if (cooldownMs == 0 && !onCooldown.isEmpty()) throw new IllegalArgumentException("on-cooldown needs a cooldown");
        if (visibleIf != null && (visibleIf.isBlank() || visibleIf.length() > 256)) throw new IllegalArgumentException("visibleIf must be 1 to 256 characters");
        if (tooltip != null && !tooltip.matches("[a-z][a-z0-9_-]{0,31}")) throw new IllegalArgumentException("Invalid tooltip element id " + tooltip);
        if (motion != null && frames != null) throw new IllegalArgumentException("An element animates with PNG frames or with keyframes, not both");
        if (motion != null && list != null) throw new IllegalArgumentException("A list cannot use keyframes");
    }
    public boolean isEmpty() { return equals(EMPTY); }
    /**
     * True when a click, right click, drop or the cursor itself does something (the element must be a button:
     * only buttons are hit-tested).
     */
    public boolean interactive() {
        return !actions.isEmpty() || !rightActions.isEmpty() || !onDrop.isEmpty() || draggable || list != null && !list.onSelect().isEmpty()
                || !hoverActions.isEmpty() || !leaveActions.isEmpty() || tooltip != null || motion != null && motion.trigger().equals("hover");
    }
    /** Every action list of the element, for analysis. */
    public List<List<UiAction>> actionLists() {
        return list == null ? List.of(actions, rightActions, onDrop, hoverActions, leaveActions, onCooldown)
                : List.of(actions, rightActions, onDrop, hoverActions, leaveActions, onCooldown, list.onSelect());
    }
    /** The same logic with a cooldown (milliseconds, 0 = none) and what runs when a click comes too soon. */
    public ElementLogic withCooldown(int ms, List<UiAction> tooSoon) {
        return new ElementLogic(actions, rightActions, hidden, visibleIf, frames, list, draggable, onDrop, hoverActions, leaveActions, tooltip, motion, ms, tooSoon);
    }
    /** True when the element can change at run time and must be baked as its own layer. */
    public boolean changesAtRuntime() { return hidden || visibleIf != null || frames != null || list != null || draggable || motion != null; }
}
