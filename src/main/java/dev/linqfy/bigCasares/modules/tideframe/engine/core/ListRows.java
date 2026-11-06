package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ElementLogic;
import java.util.ArrayList;
import java.util.List;

/**
 * Data lists: an element with a {@code list} spec is a row template. Row {@code i} is the same element moved
 * {@code i * spacing} logical pixels down; its id at run time is {@code template#i}. Rows are baked at fixed
 * positions (the HUD cannot move glyphs vertically), so scrolling changes which data each row shows.
 */
public final class ListRows {
    private ListRows() { }
    public static final char SEPARATOR = '#';

    public static String rowId(String template, int row) { return template + SEPARATOR + row; }
    public static boolean isRow(String id) { return id != null && id.indexOf(SEPARATOR) > 0; }
    /** Element id of a row id (or the id itself). */
    public static String template(String id) { int at = id.indexOf(SEPARATOR); return at < 0 ? id : id.substring(0, at); }
    /** Row index of a row id, or -1. */
    public static int index(String id) {
        int at = id.indexOf(SEPARATOR);
        if (at < 0) return -1;
        try { return Integer.parseInt(id.substring(at + 1)); } catch (NumberFormatException bad) { return -1; }
    }
    /** Row {@code row} of a list template: moved down, without the list spec (it is one row, not a list). */
    public static Screen.Element row(Screen.Element template, int row) {
        var spec = template.logic().list();
        if (spec == null) throw new IllegalArgumentException("Element " + template.id() + " is not a list");
        if (row < 0 || row >= spec.rows()) throw new IllegalArgumentException("List row out of range");
        var logic = template.logic();
        var single = new ElementLogic(logic.actions(), logic.rightActions(), logic.hidden(), logic.visibleIf(), null, null, logic.draggable(), logic.onDrop())
                .withCooldown(logic.cooldownMs(), logic.onCooldown());
        return template.moved(template.x(), template.y() - row * spec.spacing() / 72.0).withLogic(single);
    }
    /** Every element as drawn: list templates become their rows, everything else stays. */
    public static List<Screen.Element> expand(List<Screen.Element> elements) {
        var out = new ArrayList<Screen.Element>(elements.size());
        for (var e : elements) {
            var spec = e.logic().list();
            if (spec == null) { out.add(e); continue; }
            for (int i = 0; i < spec.rows(); i++) out.add(row(e, i));
        }
        return out;
    }
    /** True when the last row's centre is still on the screen. */
    public static boolean fits(Screen.Element template) {
        var spec = template.logic().list();
        return spec == null || template.y() - (spec.rows() - 1) * spec.spacing() / 72.0 >= -1;
    }
}
