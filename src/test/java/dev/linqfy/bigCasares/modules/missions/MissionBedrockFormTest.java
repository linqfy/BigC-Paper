package dev.linqfy.bigCasares.modules.missions;

import dev.linqfy.bigCasares.modules.geyser.BedrockFormStyle;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionBedrockFormTest {
    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    private static MissionAssignment assignment(String id, String title, int goal, double reward, int progress,
                                                boolean completed, boolean claimed) {
        var definition = new MissionDefinition(id, MissionScope.DAILY, title, "", MissionType.values()[0], goal, reward, 1, true, Map.of());
        return new MissionAssignment(definition, new MissionProgressSnapshot(progress, completed, claimed, Set.of()));
    }

    private static MissionPlayerState state(List<MissionAssignment> daily, List<MissionAssignment> weekly) {
        Map<String, MissionAssignment> dailyMap = new LinkedHashMap<>();
        daily.forEach(assignment -> dailyMap.put(assignment.definition().id(), assignment));
        Map<String, MissionAssignment> weeklyMap = new LinkedHashMap<>();
        weekly.forEach(assignment -> weeklyMap.put(assignment.definition().id(), assignment));
        return new MissionPlayerState(UUID.randomUUID(), NOW, NOW.plus(Duration.ofMinutes(185)), NOW.plus(Duration.ofDays(3)),
            dailyMap, weeklyMap, null);
    }

    private final MissionPlayerState state = state(List.of(
        assignment("piedra", "§fPicar 64 de piedra", 64, 250, 16, false, false),
        assignment("zombis", "Matar 10 zombis", 10, 400, 10, true, false),
        assignment("pan", "Comer pan", 5, 100, 5, true, true)
    ), List.of(assignment("viaje", "Viajar 5000 bloques", 5000, 2000, 1200, false, false)));

    @Test
    void dailyPageHasTheTabsEveryMissionAndReclamarInOrder() {
        var page = MissionBedrockForm.build(state, MissionScope.DAILY, 1, amount -> "$" + (int) amount, true, NOW);

        assertEquals(BedrockFormStyle.TAG + "Misiones · Diarias", page.title());
        assertEquals(List.of(MissionBedrockForm.Kind.DAILY, MissionBedrockForm.Kind.WEEKLY, MissionBedrockForm.Kind.MISSION,
            MissionBedrockForm.Kind.MISSION, MissionBedrockForm.Kind.MISSION, MissionBedrockForm.Kind.CLAIM),
            page.buttons().stream().map(MissionBedrockForm.Button::kind).toList());
        assertEquals("§e» Diarias «", page.texts().get(0));
        assertEquals("§7Semanales", page.texts().get(1));
        assertTrue(page.texts().get(2).startsWith("§fMatar 10 zombis\n"), "ready to claim first: " + page.texts().get(2));
        assertTrue(page.texts().get(2).contains("§a¡Lista!"));
        assertTrue(page.texts().get(3).startsWith("§fPicar 64 de piedra\n"), page.texts().get(3));
        assertTrue(page.texts().get(3).endsWith("§b16/64 §a$250"), page.texts().get(3));
        assertTrue(page.texts().get(4).contains("§8Cobrada"), "claimed last");
        assertEquals("§e§lReclamar §r§e(1)", page.texts().get(5));
        assertEquals("§7Se renuevan en 3h 5m\n§e1 para cobrar §7· §a$400", page.content());
    }

    @Test
    void weeklyPageSelectsItsTabAndHasNoReclamarWithoutRewards() {
        var page = MissionBedrockForm.build(state, MissionScope.WEEKLY, 0, amount -> "$" + (int) amount, true, NOW);

        assertEquals(BedrockFormStyle.TAG + "Misiones · Semanales", page.title());
        assertEquals(List.of("§7Diarias", "§e» Semanales «"), page.texts().subList(0, 2));
        assertEquals(3, page.buttons().size());
        assertTrue(page.content().endsWith("§8Nada para cobrar"));
    }

    @Test
    void theDailyTabOffersCambiarWhenAllowedAndAsksToConfirm() {
        var asking = MissionBedrockForm.build(state, MissionScope.DAILY, 1, amount -> "$", true, NOW, MissionReroll.Check.ALLOWED, false, "");
        assertEquals(MissionBedrockForm.Kind.REROLL, asking.buttons().get(2).kind());
        assertEquals("§6Cambiar las diarias §7(1 vez por día)", asking.texts().get(2));
        var confirming = MissionBedrockForm.build(state, MissionScope.DAILY, 1, amount -> "$", true, NOW, MissionReroll.Check.ALLOWED, true, "");
        assertEquals("§c¿Seguro? Tocá de nuevo para cambiar las diarias", confirming.texts().get(2));

        var weekly = MissionBedrockForm.build(state, MissionScope.WEEKLY, 1, amount -> "$", true, NOW, MissionReroll.Check.ALLOWED, false, "");
        assertTrue(weekly.buttons().stream().noneMatch(button -> button.kind() == MissionBedrockForm.Kind.REROLL));
        var used = MissionBedrockForm.build(state, MissionScope.DAILY, 1, amount -> "$", true, NOW, MissionReroll.Check.USED_TODAY, false, "");
        assertTrue(used.buttons().stream().noneMatch(button -> button.kind() == MissionBedrockForm.Kind.REROLL));
    }

    @Test
    void theSummaryLineGoesUnderTheReadyCount() {
        var page = MissionBedrockForm.build(state, MissionScope.DAILY, 1, amount -> "$" + (int) amount, true, NOW,
            MissionReroll.Check.USED_TODAY, false, "racha 3 días");
        assertEquals("§7Se renuevan en 3h 5m\n§e1 para cobrar §7· §a$400\n§6racha 3 días", page.content());
    }

    @Test
    void emptyListSaysSoAndOnlyOffersTheTabs() {
        var page = MissionBedrockForm.build(state(List.of(), List.of()), MissionScope.DAILY, 0, amount -> "", true, NOW);

        assertEquals(2, page.buttons().size());
        assertTrue(page.content().endsWith("\n\n§7No hay misiones activas"));
    }

    @Test
    void barShowsProgressReadyAndClaimedWithThePackGlyphs() {
        String running = MissionBedrockForm.bar(new MissionProgressSnapshot(3, false, false), 10, true);
        assertEquals("§f" + "".repeat(3) + "".repeat(7), running);
        assertEquals("§f" + "".repeat(10), MissionBedrockForm.bar(new MissionProgressSnapshot(10, true, false), 10, true));
        assertEquals("§f" + "".repeat(10), MissionBedrockForm.bar(new MissionProgressSnapshot(10, true, true), 10, true));
        // Over the goal, or a broken goal of 0, never draws more than the bar.
        assertEquals("§f" + "".repeat(10), MissionBedrockForm.bar(new MissionProgressSnapshot(50, false, false), 10, true));
        assertEquals("§f" + "".repeat(10), MissionBedrockForm.bar(new MissionProgressSnapshot(0, false, false), 0, true));
    }

    @Test
    void everyBarGlyphIsDrawnInTheBedrockPack() throws java.io.IOException {
        char[] glyphs = {MissionBedrockForm.FILLED, MissionBedrockForm.EMPTY, MissionBedrockForm.READY, MissionBedrockForm.CLAIMED};
        int page = glyphs[0] >> 8;
        var sheet = javax.imageio.ImageIO.read(java.nio.file.Path.of(String.format("resourcepack/bedrock/font/glyph_%02X.png", page)).toFile());
        assertEquals(256, sheet.getWidth());
        assertEquals(256, sheet.getHeight());
        for (char glyph : glyphs) {
            assertEquals(page, glyph >> 8);
            int cell = glyph & 0xff;
            assertEquals(0xff, sheet.getRGB((cell % 16) * 16 + 2, (cell / 16) * 16 + 8) >>> 24, Integer.toHexString(glyph));
        }
        assertEquals(0, sheet.getRGB(4 * 16 + 2, 8) >>> 24, "the next cell stays empty");
    }

    @Test
    void withoutThePackTheBarUsesPlainCharacters() {
        var page = MissionBedrockForm.build(state, MissionScope.DAILY, 1, amount -> "$" + (int) amount, false, NOW);

        assertEquals("§b" + "|".repeat(2) + "§8" + "|".repeat(8),
            MissionBedrockForm.bar(new MissionProgressSnapshot(16, false, false), 64, false));
        for (String text : page.texts()) {
            assertFalse(text.chars().anyMatch(c -> c >= 0xE000 && c <= 0xF8FF), text);
        }
    }
}
