package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Static analysis of action lists: which elements they change, and references a screen cannot satisfy. */
public final class LogicIndex {
    private LogicIndex() { }

    /** Element ids that actions show, hide, toggle, move, tint, animate or scroll (nested branches included). */
    public static Set<String> targets(Collection<List<UiAction>> lists) {
        var out = new TreeSet<String>();
        for (var list : lists) visit(list, out, null);
        return out;
    }
    /** Every function name the lists call (nested branches included). */
    public static Set<String> calls(Collection<List<UiAction>> lists) {
        var out = new TreeSet<String>();
        for (var list : lists) visit(list, null, out);
        return out;
    }
    private static void visit(List<UiAction> actions, Set<String> targets, Set<String> calls) {
        for (var action : actions) {
            switch (action) {
                case UiAction.Visibility v -> { if (targets != null) { targets.addAll(v.show()); targets.addAll(v.hide()); targets.addAll(v.toggle()); } }
                case UiAction.Offset o -> { if (targets != null) targets.add(o.element()); }
                case UiAction.Tint t -> { if (targets != null) targets.add(t.element()); }
                case UiAction.Animate a -> { if (targets != null) targets.add(a.element()); }
                case UiAction.Scroll s -> { if (targets != null) targets.add(s.element()); }
                case UiAction.Playback p -> { if (targets != null) targets.add(p.element()); }
                case UiAction.Confirm c -> { visit(c.then(), targets, calls); visit(c.otherwise(), targets, calls); }
                case UiAction.Condition c -> { visit(c.then(), targets, calls); visit(c.otherwise(), targets, calls); }
                case UiAction.Permission p -> { visit(p.then(), targets, calls); visit(p.otherwise(), targets, calls); }
                case UiAction.Input i -> { visit(i.onSubmit(), targets, calls); visit(i.onCancel(), targets, calls); }
                case UiAction.Charge c -> { visit(c.then(), targets, calls); visit(c.otherwise(), targets, calls); }
                case UiAction.Call c -> { if (calls != null) calls.add(c.function()); }
                default -> { }
            }
        }
    }

    /** Human-readable problems: unknown element ids, unknown functions, scroll targets without a list, animations without frames. */
    public static List<String> problems(Screen screen) {
        var problems = new ArrayList<String>();
        var lists = new ArrayList<List<UiAction>>(screen.logic().actionLists());
        for (var e : screen.elements()) lists.addAll(e.logic().actionLists());
        for (String id : targets(lists)) if (screen.element(id).isEmpty()) problems.add("actions refer to element '" + id + "', which this screen does not have");
        for (String name : calls(lists)) if (!screen.logic().functions().containsKey(name)) problems.add("actions call function '" + name + "', which this screen does not define");
        var scrolls = new TreeSet<String>(); var animations = new TreeSet<String>(); var playbacks = new TreeSet<String>();
        for (var list : lists) collect(list, scrolls, animations, playbacks);
        for (String id : scrolls) screen.element(id).filter(e -> e.logic().list() == null).ifPresent(e -> problems.add("scroll targets '" + id + "', which is not a list"));
        for (String id : animations) screen.element(id).filter(e -> e.logic().frames() == null && e.logic().motion() == null)
                .ifPresent(e -> problems.add("animation targets '" + id + "', which has no frames or keyframes"));
        for (String id : playbacks) screen.element(id).filter(e -> e.logic().frames() == null)
                .ifPresent(e -> problems.add("pause, resume or frame targets '" + id + "', which is not a PNG or GIF animation"));
        for (var e : screen.elements()) {
            String tip = e.logic().tooltip();
            if (tip != null && screen.element(tip).isEmpty()) problems.add("element '" + e.id() + "' shows tooltip '" + tip + "', which this screen does not have");
        }
        for (var e : screen.elements()) if (e.logic().list() != null && e.logic().frames() != null) problems.add("element '" + e.id() + "' cannot be a list and animated at once; frames are ignored");
        return problems;
    }
    private static void collect(List<UiAction> actions, Set<String> scrolls, Set<String> animations, Set<String> playbacks) {
        for (var action : actions) {
            switch (action) {
                case UiAction.Scroll s -> scrolls.add(s.element());
                case UiAction.Animate a -> animations.add(a.element());
                case UiAction.Playback p -> playbacks.add(p.element());
                case UiAction.Condition c -> { collect(c.then(), scrolls, animations, playbacks); collect(c.otherwise(), scrolls, animations, playbacks); }
                case UiAction.Permission p -> { collect(p.then(), scrolls, animations, playbacks); collect(p.otherwise(), scrolls, animations, playbacks); }
                case UiAction.Input i -> { collect(i.onSubmit(), scrolls, animations, playbacks); collect(i.onCancel(), scrolls, animations, playbacks); }
                case UiAction.Charge c -> { collect(c.then(), scrolls, animations, playbacks); collect(c.otherwise(), scrolls, animations, playbacks); }
                case UiAction.Confirm c -> { collect(c.then(), scrolls, animations, playbacks); collect(c.otherwise(), scrolls, animations, playbacks); }
                default -> { }
            }
        }
    }
}
