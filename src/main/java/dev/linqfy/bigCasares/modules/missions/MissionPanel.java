package dev.linqfy.bigCasares.modules.missions;

import dev.linqfy.bigCasares.modules.tideframe.Panel;
import dev.linqfy.bigCasares.modules.tideframe.PixelFont;
import dev.linqfy.bigCasares.modules.tideframe.PixelImage;
import dev.linqfy.bigCasares.modules.tideframe.ScreenArt;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** The three assignment plates and the actions in the 256 × 144 missions window. */
final class MissionPanel {
    static final int ROWS = 3;
    private static final int BAR_WIDTH = 116;
    private static final PixelFont FONT = PixelFont.get();
    private static final PixelImage PLATE = new PixelImage(216, 18)
        .fill(0, 0, 216, 18, PixelImage.rgb(0x1b1b19)).slot(0, 0)
        .fill(22, 11, BAR_WIDTH + 2, 5, PixelImage.rgb(0x0c0c0b))
        .fill(23, 12, BAR_WIDTH, 3, PixelImage.rgb(0x1e2a16));

    private MissionPanel() {}

    record Page(MissionScope scope, int index, int count, List<MissionAssignment> rows) {
        static Page of(MissionPlayerState state, MissionScope scope, int requested) {
            var rows = List.copyOf((scope == MissionScope.WEEKLY ? state.weeklyAssignments() : state.dailyAssignments()).values());
            int count = Math.max(1, Math.ceilDiv(rows.size(), ROWS));
            int index = Math.clamp(requested, 0, count - 1);
            int start = index * ROWS;
            return new Page(scope, index, count, rows.subList(start, start + Math.min(ROWS, rows.size() - start)));
        }
    }

    static Panel draw(Page page, double balance, boolean claimable, BiConsumer<MissionScope, Integer> navigate,
                      Runnable collect, Runnable store) {
        boolean weekly = page.scope() == MissionScope.WEEKLY;
        Panel panel = new Panel(false, weekly ? 1 : 0, 2);
        panel.text(weekly ? "Misiones semanales" : "Misiones diarias", 21, 43, weekly ? ScreenArt.AQUA : ScreenArt.GOLD);
        right(panel, "$ " + (Double.isFinite(balance) ? ScreenArt.amount(balance) : "?"), 224, 43, 37, ScreenArt.GOLD);
        if (claimable) {
            panel.image(claimButton(false), 120, 41);
            panel.hover(120, 41, 66, 11, claimButton(true), 120, 41);
            panel.button(120, 41, 66, 11, right -> { if (!right) collect.run(); });
        } else {
            panel.image(ScreenArt.fitted(new PixelImage(66, 11), "Sin premios", ScreenArt.GRAY), 120, 41);
        }
        if (page.rows().isEmpty()) {
            panel.image(ScreenArt.fitted(new PixelImage(216, 11), "No hay misiones activas", ScreenArt.GRAY), 20, 70);
        }
        for (int i = 0; i < page.rows().size(); i++) drawRow(panel, page.rows().get(i), 53 + 19 * i);
        tab(panel, ScreenArt.tabX(0), "clock", () -> navigate.accept(MissionScope.DAILY, 0));
        tab(panel, ScreenArt.tabX(1), "compass", () -> navigate.accept(MissionScope.WEEKLY, 0));
        tab(panel, ScreenArt.NAV_X, "emerald", store);
        if (page.count() > 1) {
            right(panel, (page.index() + 1) + "/" + page.count(), 171, 121, 98, ScreenArt.WHITE);
            if (page.index() > 0) arrow(panel, 176, true, () -> navigate.accept(page.scope(), page.index() - 1));
            if (page.index() < page.count() - 1) arrow(panel, 190, false, () -> navigate.accept(page.scope(), page.index() + 1));
        }
        return panel;
    }

    private static void drawRow(Panel panel, MissionAssignment mission, int y) {
        MissionDefinition definition = mission.definition();
        MissionProgressSnapshot progress = mission.snapshot();
        panel.image(PLATE, 20, y);
        panel.item(icon(definition, Panel::knowsItem), 21, y + 1);
        String reward = FONT.fit("$ " + ScreenArt.amount(definition.reward()), 61);
        int rewardStart = 232 - FONT.width(reward);
        panel.text(FONT.fit(definition.title(), rewardStart - 45), 42, y + 2, ScreenArt.WHITE);
        panel.text(reward, rewardStart, y + 2, ScreenArt.GOLD);
        int goal = Math.max(1, definition.goal());
        int amount = Math.max(0, progress.progress());
        int fill = (int) Math.round(Math.min(1.0, (double) amount / goal) * BAR_WIDTH);
        if (fill > 0) {
            PixelImage bar = new PixelImage(fill, 3).fill(0, 0, fill, 1, PixelImage.rgb(0x9dff22))
                .fill(0, 1, fill, 1, PixelImage.rgb(0x5ed10f)).fill(0, 2, fill, 1, PixelImage.rgb(0x3a8209));
            panel.image(bar, 43, y + 12);
        }
        if (progress.claimed()) right(panel, "Reclamada", 232, y + 9, 69, ScreenArt.GREEN);
        else if (progress.completed()) right(panel, "Completada", 232, y + 9, 69, ScreenArt.GOLD);
        else {
            String exact = amount + "/" + goal;
            String count = FONT.width(exact) <= 69 ? exact : ScreenArt.amount(amount) + "/" + ScreenArt.amount(goal);
            right(panel, count, 232, y + 9, 69, ScreenArt.YELLOW);
        }
    }

    static String icon(MissionDefinition mission, Predicate<String> drawable) {
        Object value = mission.params().get("material");
        if (value != null) {
            String named = value.toString().strip().toLowerCase(Locale.ROOT);
            if (drawable.test(named)) return named;
        }
        String fallback = switch (mission.type()) {
            case CROUCH_ON_SLEEPING_BED -> "red_wool";
            case FINAL_HIT_PLAYER_WITH_ITEM, KILL_ENTITY_WITH_ITEM_ONLY, KILL_ENTITY_IN_BIOME -> "iron_sword";
            case HOLD_EXACT_ITEM_COUNT -> "bundle";
            case OPEN_LOOT_TABLE -> "barrel";
            case RENAME_ITEM_TO_EXACT_NAME, NAME_ENTITY_AFTER_PLAYER -> "name_tag";
            case EQUIP_SPECIFIC_ITEM -> "iron_chestplate";
            case STAND_ON_BLOCK_AT_Y -> "ladder";
            case WAX_BLOCK_COUNT -> "honeycomb";
            case VISIT_BIOME, VISIT_BIOME_SET -> "filled_map";
            case REACH_DISTANCE_FROM_SPAWN -> "compass";
            case ENTER_ENVIRONMENT -> "obsidian";
            case CATCH_FISH_IN_BIOME -> "fishing_rod";
            case ADMIN_APPROVAL -> "writable_book";
            case HIT_TARGET -> "bow";
            case KILL_TARGET, KILL_SAME_TEAMMATE -> "iron_sword";
            case PLACE_BLOCK_COUNT -> "bricks";
            case WHISPER_SAME_PLAYER -> "paper";
            case BREAK_CURSED_HELMET -> "iron_helmet";
            case HORSE_JUMP_COUNT -> "saddle";
        };
        return drawable.test(fallback) ? fallback : "paper";
    }

    private static void right(Panel panel, String text, int x, int y, int width, int colour) {
        String fit = FONT.fit(text, width);
        panel.text(fit, x - FONT.width(fit), y, colour);
    }

    private static void tab(Panel panel, int x, String icon, Runnable action) {
        panel.item(icon, x + 4, ScreenArt.TAB_Y + 1);
        panel.highlight(x, ScreenArt.TAB_Y, ScreenArt.TAB_W, ScreenArt.TAB_H, ScreenArt.tabHover(), x, ScreenArt.TAB_Y);
        panel.button(x, ScreenArt.TAB_Y, ScreenArt.TAB_W, ScreenArt.TAB_H, right -> { if (!right) action.run(); });
    }

    private static PixelImage claimButton(boolean hovered) {
        PixelImage button = new PixelImage(66, 11).button(0, 0, 66, 11, hovered ? 0x4f9a41 : 0x3c7a31,
            hovered ? 0x8fdc7c : 0x6dbd5b, 0x1e4718);
        if (hovered) button.outline(0, 0, 66, 11, ScreenArt.WHITE);
        return ScreenArt.fitted(button, "Cobrar todo", ScreenArt.WHITE);
    }

    private static void arrow(Panel panel, int x, boolean previous, Runnable action) {
        var normal = new PixelImage(11, 11).button(0, 0, 11, 11);
        var hover = new PixelImage(11, 11).button(0, 0, 11, 11, 0x77777a, 0xa0a09c, 0x3a3a38);
        ScreenArt.arrow(normal, previous ? 3 : 4, 2, previous, ScreenArt.WHITE);
        ScreenArt.arrow(hover, previous ? 3 : 4, 2, previous, ScreenArt.WHITE);
        panel.image(normal, x, 119);
        panel.hover(x, 119, 11, 11, hover, x, 119);
        panel.button(x, 119, 11, 11, right -> { if (!right) action.run(); });
    }
}
