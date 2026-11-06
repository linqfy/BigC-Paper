package dev.linqfy.bigCasares.modules.missions;

import dev.linqfy.bigCasares.modules.geyser.BedrockFormStyle;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * The missions as a Bedrock form, the counterpart of the Java Tideframe screen (bigc_misiones): Diarias and Semanales
 * tabs, every mission with its progress bar, status and reward, and "Reclamar" while something is ready. "Reclamar" pays
 * through the module's own claim, as in the inventory menu. The bars use the Bedrock pack's glyphs (font/glyph_E5.png) and
 * fall back to plain bars when that pack is not sent.
 */
final class MissionBedrockForm {
    static final char FILLED = '';
    static final char EMPTY = '';
    static final char READY = '';
    static final char CLAIMED = '';
    static final int SEGMENTS = 10;

    enum Kind { DAILY, WEEKLY, REROLL, MISSION, CLAIM }

    record Button(String text, Kind kind) {
    }

    record Page(String title, String content, List<Button> buttons) {
        Page {
            buttons = List.copyOf(buttons);
        }

        List<String> texts() {
            return buttons.stream().map(Button::text).toList();
        }
    }

    private MissionBedrockForm() {
    }

    /** Without "Cambiar". */
    static Page build(MissionPlayerState state, MissionScope scope, int claimables, DoubleFunction<String> money,
                      boolean glyphs, Instant now) {
        return build(state, scope, claimables, money, glyphs, now, MissionReroll.Check.USED_TODAY, false, "");
    }

    /**
     * {@code reroll}: whether the daily missions may change now (the daily tab then offers "Cambiar");
     * {@code confirming}: the player tapped it a moment ago, so this tap confirms.
     */
    static Page build(MissionPlayerState state, MissionScope scope, int claimables, DoubleFunction<String> money,
                      boolean glyphs, Instant now, MissionReroll.Check reroll, boolean confirming, String summary) {
        boolean daily = scope == MissionScope.DAILY;
        Collection<MissionAssignment> assignments = (daily ? state.dailyAssignments() : state.weeklyAssignments()).values();
        Instant resetsAt = daily ? state.dailyResetsAt() : state.weeklyResetsAt();
        StringBuilder content = new StringBuilder("§7Se renuevan en ")
            .append(MissionTexts.formatTimeRemaining(Duration.between(now, resetsAt)))
            .append('\n')
            .append(claimables == 0 ? "§8Nada para cobrar"
                : "§e" + claimables + " para cobrar §7· §a" + MissionTideframeMenu.plain(money.apply(MissionOrder.pendingReward(state))));
        if (!summary.isEmpty()) {
            content.append("\n§6").append(summary);
        }
        if (assignments.isEmpty()) {
            content.append("\n\n§7No hay misiones activas");
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(new Button(daily ? "§e» Diarias «" : "§7Diarias", Kind.DAILY));
        buttons.add(new Button(daily ? "§7Semanales" : "§e» Semanales «", Kind.WEEKLY));
        if (daily && reroll == MissionReroll.Check.ALLOWED) {
            buttons.add(new Button(confirming ? "§c¿Seguro? Tocá de nuevo para cambiar las diarias"
                : "§6Cambiar las diarias §7(1 vez por día)", Kind.REROLL));
        }
        for (MissionAssignment assignment : MissionOrder.readyFirst(assignments)) {
            MissionDefinition definition = assignment.definition();
            MissionProgressSnapshot snapshot = assignment.snapshot();
            String status = snapshot.claimed() ? "§8Cobrada"
                : snapshot.completed() ? "§a¡Lista!"
                : "§b" + Math.min(snapshot.progress(), definition.goal()) + "/" + definition.goal();
            buttons.add(new Button("§f" + MissionTideframeMenu.plain(definition.title()) + "\n"
                + bar(snapshot, definition.goal(), glyphs) + " " + status
                + " §a" + MissionTideframeMenu.plain(money.apply(definition.reward())), Kind.MISSION));
        }
        if (claimables > 0) {
            buttons.add(new Button("§e§lReclamar §r§e(" + claimables + ")", Kind.CLAIM));
        }
        return new Page(BedrockFormStyle.styled("Misiones · " + (daily ? "Diarias" : "Semanales")), content.toString(), buttons);
    }

    /** {@link #SEGMENTS} segments: progress so far, all green when ready, all grey once claimed. */
    static String bar(MissionProgressSnapshot snapshot, int goal, boolean glyphs) {
        int filled = snapshot.completed() ? SEGMENTS
            : Math.max(0, Math.min(SEGMENTS, snapshot.progress() * SEGMENTS / Math.max(1, goal)));
        char full = snapshot.claimed() ? CLAIMED : snapshot.completed() ? READY : FILLED;
        if (glyphs) {
            return "§f" + String.valueOf(full).repeat(filled) + String.valueOf(EMPTY).repeat(SEGMENTS - filled);
        }
        String colour = snapshot.claimed() ? "§8" : snapshot.completed() ? "§a" : "§b";
        return colour + "|".repeat(filled) + "§8" + "|".repeat(SEGMENTS - filled);
    }
}
