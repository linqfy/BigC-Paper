package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import java.util.List;
import java.util.Map;

/** Writes screen/element logic and actions back to JSON in the same shape {@link ActionParser} reads. */
public final class LogicJson {
    private LogicJson() { }

    public static String screen(ScreenLogic logic) {
        var out = new StringBuilder("{");
        var b = logic.behavior();
        sep(out).append("\"behavior\":{\"close-on-sneak\":").append(b.closeOnSneak()).append(",\"close-on-damage\":").append(b.closeOnDamage())
                .append(",\"close-on-f\":").append(b.closeOnSwap()).append(",\"close-on-shift-f\":").append(b.closeOnShiftSwap())
                .append(",\"reopen\":").append(b.reopen());
        // Written only when on, so pages without it keep their exact JSON.
        if (b.keyboardNavigation()) out.append(",\"keyboard-navigation\":true");
        out.append('}');
        if (!logic.variables().isEmpty()) sep(out).append("\"variables\":").append(stringMap(logic.variables()));
        if (!logic.functions().isEmpty()) {
            sep(out).append("\"functions\":{");
            boolean first = true;
            for (var f : logic.functions().entrySet()) {
                if (!first) out.append(','); first = false;
                out.append(quote(f.getKey())).append(":{\"params\":").append(stringMap(f.getValue().params()))
                        .append(",\"actions\":").append(actions(f.getValue().actions())).append('}');
            }
            out.append('}');
        }
        if (!logic.keybinds().isEmpty()) {
            sep(out).append("\"keybinds\":[");
            boolean first = true;
            for (var k : logic.keybinds().entrySet()) {
                if (!first) out.append(','); first = false;
                out.append("{\"key\":").append(quote(k.getKey())).append(",\"actions\":").append(actions(k.getValue())).append('}');
            }
            out.append(']');
        }
        if (logic.command() != null) sep(out).append("\"command\":").append(quote(logic.command()));
        if (logic.permission() != null) sep(out).append("\"permission\":").append(quote(logic.permission()));
        if (logic.openOnJoin()) sep(out).append("\"open-on-join\":true");
        if (!logic.onOpen().isEmpty()) sep(out).append("\"on-open\":").append(actions(logic.onOpen()));
        if (!logic.onClose().isEmpty()) sep(out).append("\"on-close\":").append(actions(logic.onClose()));
        if (!logic.animation().equals(ScreenLogic.Animation.DEFAULT))
            sep(out).append("\"animation\":{\"open\":").append(transition(logic.animation().open())).append(",\"close\":").append(transition(logic.animation().close())).append('}');
        if (!logic.timers().isEmpty()) {
            sep(out).append("\"timers\":[");
            for (int i = 0; i < logic.timers().size(); i++) {
                var timer = logic.timers().get(i);
                if (i > 0) out.append(',');
                out.append("{\"every\":\"").append(timer.everyTicks()).append("t\",\"actions\":").append(actions(timer.actions())).append('}');
            }
            out.append(']');
        }
        if (!logic.onDamage().isEmpty()) sep(out).append("\"on-damage\":").append(actions(logic.onDamage()));
        if (!logic.onWorldChange().isEmpty()) sep(out).append("\"on-world-change\":").append(actions(logic.onWorldChange()));
        if (!logic.onChange().isEmpty()) {
            sep(out).append("\"on-change\":{");
            boolean first = true;
            for (var change : logic.onChange().entrySet()) {
                if (!first) out.append(','); first = false;
                out.append(quote(change.getKey())).append(':').append(actions(change.getValue()));
            }
            out.append('}');
        }
        return out.append('}').toString();
    }
    private static String transition(ScreenLogic.Transition t) {
        return "{\"effect\":" + quote(t.effect()) + ",\"duration-ms\":" + t.durationMs() + ",\"easing\":" + quote(t.easing())
                + ",\"from\":" + quote(t.from()) + ",\"distance\":" + t.distance() + "}";
    }

    public static String element(ElementLogic logic) {
        var out = new StringBuilder("{");
        if (!logic.actions().isEmpty()) sep(out).append("\"actions\":").append(actions(logic.actions()));
        if (!logic.rightActions().isEmpty()) sep(out).append("\"right-actions\":").append(actions(logic.rightActions()));
        if (logic.hidden()) sep(out).append("\"hidden\":true");
        if (logic.visibleIf() != null) sep(out).append("\"visible-if\":").append(quote(logic.visibleIf()));
        if (logic.frames() != null) {
            sep(out).append("\"frames\":{\"folder\":").append(quote(logic.frames().folder()));
            if (logic.frames().intervalMs() != ElementLogic.Frames.AUTO) out.append(",\"interval-ms\":").append(logic.frames().intervalMs());
            out.append(",\"loop\":").append(logic.frames().loop()).append('}');
        }
        if (logic.list() != null) {
            var l = logic.list();
            sep(out).append("\"list\":{\"source\":").append(quote(l.source())).append(",\"rows\":").append(l.rows()).append(",\"spacing\":").append(l.spacing());
            if (l.filter() != null) out.append(",\"filter\":").append(quote(l.filter()));
            if (!l.onSelect().isEmpty()) out.append(",\"on-select\":").append(actions(l.onSelect()));
            out.append('}');
        }
        if (logic.draggable()) sep(out).append("\"draggable\":true");
        if (!logic.onDrop().isEmpty()) sep(out).append("\"on-drop\":").append(actions(logic.onDrop()));
        if (!logic.hoverActions().isEmpty()) sep(out).append("\"on-hover\":").append(actions(logic.hoverActions()));
        if (!logic.leaveActions().isEmpty()) sep(out).append("\"on-leave\":").append(actions(logic.leaveActions()));
        if (logic.tooltip() != null) sep(out).append("\"tooltip\":").append(quote(logic.tooltip()));
        if (logic.motion() != null) {
            var motion = logic.motion();
            sep(out).append("\"keyframes\":{\"trigger\":").append(quote(motion.trigger())).append(",\"easing\":").append(quote(motion.easing())).append(",\"steps\":[");
            for (int i = 0; i < motion.steps().size(); i++) {
                var step = motion.steps().get(i);
                if (i > 0) out.append(',');
                out.append("{\"at\":").append(step.at());
                number(out, "x", step.x()); number(out, "y", step.y()); number(out, "scale", step.scale());
                number(out, "opacity", step.opacity()); number(out, "rotation", step.rotation());
                out.append('}');
            }
            out.append("]}");
        }
        if (logic.cooldownMs() > 0) sep(out).append("\"cooldown\":\"").append(logic.cooldownMs()).append("ms\"");
        if (!logic.onCooldown().isEmpty()) sep(out).append("\"on-cooldown\":").append(actions(logic.onCooldown()));
        return out.append('}').toString();
    }

    private static void number(StringBuilder out, String key, Double value) {
        if (value == null) return;
        var plain = java.math.BigDecimal.valueOf(value).stripTrailingZeros();
        out.append(",\"").append(key).append("\":").append(plain.scale() < 0 ? plain.setScale(0).toPlainString() : plain.toPlainString());
    }
    public static String actions(List<UiAction> actions) {
        var out = new StringBuilder("[");
        for (int i = 0; i < actions.size(); i++) { if (i > 0) out.append(','); out.append(action(actions.get(i))); }
        return out.append(']').toString();
    }

    static String action(UiAction action) {
        return switch (action) {
            case UiAction.Command c -> "{\"type\":\"command\",\"value\":" + quote(c.line()) + "}";
            case UiAction.Console c -> "{\"type\":\"console\",\"value\":" + quote(c.line()) + "}";
            case UiAction.Message m -> "{\"type\":\"message\",\"value\":" + quote(m.text()) + "}";
            case UiAction.ActionBar a -> "{\"type\":\"actionbar\",\"value\":" + quote(a.text()) + "}";
            case UiAction.Sound s -> "{\"type\":\"sound\",\"sound\":" + quote(s.sound()) + ",\"volume\":" + s.volume() + ",\"pitch\":" + s.pitch() + "}";
            case UiAction.Teleport t -> "{\"type\":\"teleport\",\"world\":" + quote(t.world()) + ",\"x\":" + quote(t.x()) + ",\"y\":" + quote(t.y())
                    + ",\"z\":" + quote(t.z()) + (t.yaw() == null ? "" : ",\"yaw\":" + quote(t.yaw())) + (t.pitch() == null ? "" : ",\"pitch\":" + quote(t.pitch())) + "}";
            case UiAction.Delay d -> "{\"type\":\"delay\",\"value\":\"" + d.ticks() + "t\"}";
            case UiAction.Give g -> "{\"type\":\"give\",\"item\":" + quote(g.item()) + ",\"amount\":" + quote(g.amount()) + "}";
            case UiAction.Open o -> "{\"type\":\"redirect\",\"value\":" + quote(o.screen()) + "}";
            case UiAction.Back b -> "{\"type\":\"back\"}";
            case UiAction.Close c -> "{\"type\":\"close\"}";
            case UiAction.Visibility v -> "{\"type\":\"toggle\"" + (v.show().isEmpty() ? "" : ",\"show\":" + ids(v.show()))
                    + (v.hide().isEmpty() ? "" : ",\"hide\":" + ids(v.hide())) + (v.toggle().isEmpty() ? "" : ",\"elements\":" + ids(v.toggle())) + "}";
            case UiAction.SetVariable s -> "{\"type\":\"variable\",\"variable\":" + quote(s.name()) + ",\"operation\":" + quote(s.operation()) + ",\"value\":" + quote(s.value()) + "}";
            case UiAction.Condition c -> "{\"type\":\"condition\",\"value\":" + quote(c.expression()) + ",\"then\":" + actions(c.then()) + ",\"else\":" + actions(c.otherwise()) + "}";
            case UiAction.Permission p -> "{\"type\":\"permission\",\"permission\":" + quote(p.permission()) + ",\"then\":" + actions(p.then()) + ",\"else\":" + actions(p.otherwise()) + "}";
            case UiAction.Call c -> "{\"type\":\"function\",\"function\":" + quote(c.function()) + ",\"arguments\":" + stringMap(c.arguments()) + "}";
            case UiAction.Input i -> "{\"type\":\"input\",\"variable\":" + quote(i.variable()) + ",\"title\":" + quote(i.title()) + ",\"label\":" + quote(i.label())
                    + ",\"default\":" + quote(i.initial()) + ",\"max-length\":" + i.maxLength() + ",\"filter\":" + quote(i.filter()) + ",\"allow-empty\":" + i.allowEmpty()
                    + ",\"on-submit\":" + actions(i.onSubmit()) + ",\"on-cancel\":" + actions(i.onCancel()) + "}";
            case UiAction.Offset o -> "{\"type\":\"offset\",\"element\":" + quote(o.element()) + ",\"dx\":" + quote(o.dx()) + "}";
            case UiAction.Tint t -> "{\"type\":\"tint\",\"element\":" + quote(t.element()) + ",\"value\":" + quote(t.color() == null ? "none" : t.color()) + "}";
            case UiAction.Animate a -> "{\"type\":\"animation\",\"element\":" + quote(a.element()) + "}";
            case UiAction.Scroll s -> "{\"type\":\"scroll\",\"element\":" + quote(s.element()) + ",\"rows\":" + quote(s.rows()) + ",\"absolute\":" + s.absolute() + "}";
            case UiAction.Charge c -> "{\"type\":\"charge\",\"amount\":" + quote(c.amount()) + ",\"then\":" + actions(c.then()) + ",\"else\":" + actions(c.otherwise()) + "}";
            case UiAction.Pay p -> "{\"type\":\"pay\",\"amount\":" + quote(p.amount()) + "}";
            case UiAction.Connect c -> "{\"type\":\"server\",\"server\":" + quote(c.server()) + "}";
            case UiAction.Confirm c -> "{\"type\":\"confirm\",\"title\":" + quote(c.title()) + ",\"value\":" + quote(c.message()) + ",\"yes\":" + quote(c.yes())
                    + ",\"no\":" + quote(c.no()) + ",\"then\":" + actions(c.then()) + ",\"else\":" + actions(c.otherwise()) + "}";
            case UiAction.Hud h -> "{\"type\":\"hud\",\"value\":" + quote(h.screen()) + (h.add() ? "" : ",\"mode\":\"remove\"") + "}";
            case UiAction.Toast t -> "{\"type\":\"toast\",\"value\":" + quote(t.screen()) + ",\"seconds\":" + quote(t.seconds())
                    + (t.values().isEmpty() ? "" : ",\"values\":" + stringMap(t.values())) + "}";
            case UiAction.Playback p -> "{\"type\":" + quote(p.command()) + ",\"element\":" + quote(p.element())
                    + (p.command().equals("frame") ? ",\"value\":" + quote(p.frame()) : "") + "}";
        };
    }
    private static String ids(List<String> ids) {
        var out = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) { if (i > 0) out.append(','); out.append(quote(ids.get(i))); }
        return out.append(']').toString();
    }
    private static String stringMap(Map<String, String> map) {
        var out = new StringBuilder("{");
        boolean first = true;
        for (var e : map.entrySet()) { if (!first) out.append(','); first = false; out.append(quote(e.getKey())).append(':').append(quote(e.getValue())); }
        return out.append('}').toString();
    }
    private static StringBuilder sep(StringBuilder out) { if (out.length() > 1) out.append(','); return out; }
    static String quote(String s) {
        var out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                default -> { if (c < 32) out.append(String.format("\\u%04x", (int) c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
}
