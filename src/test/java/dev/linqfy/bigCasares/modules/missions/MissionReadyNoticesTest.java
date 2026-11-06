package dev.linqfy.bigCasares.modules.missions;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissionReadyNoticesTest {
    private static final UUID JUAN = UUID.randomUUID();
    private static final Instant TODAY = Instant.parse("2026-11-05T00:00:00Z");

    /** A passive "hold 32 stone" mission at {@code progress} for the day ending {@code resetsAt}. */
    private static MissionPlayerState holding(int progress, Instant resetsAt) {
        var definition = new MissionDefinition("piedra", MissionScope.DAILY, "Tener 32 de piedra", "", MissionType.HOLD_EXACT_ITEM_COUNT,
            32, 100, 1, true, Map.of());
        var assignment = new MissionAssignment(definition, new MissionProgressSnapshot(progress, progress >= 32, false, Set.of()));
        return new MissionPlayerState(JUAN, Instant.EPOCH, resetsAt, TODAY, Map.of("piedra", assignment), Map.of(), null);
    }

    private static List<String> ids(List<MissionDefinition> definitions) {
        return definitions.stream().map(MissionDefinition::id).toList();
    }

    @Test
    void aPassiveMissionThatTurnsOffAndOnIsAnnouncedOncePerPeriod() {
        MissionReadyNotices notices = new MissionReadyNotices();
        assertEquals(List.of("piedra"), ids(notices.toAnnounce(holding(10, TODAY), holding(32, TODAY))));
        // Drops the stone, picks it up again: complete again, but already told.
        assertEquals(List.of(), ids(notices.toAnnounce(holding(32, TODAY), holding(5, TODAY))));
        assertEquals(List.of(), ids(notices.toAnnounce(holding(5, TODAY), holding(32, TODAY))));

        Instant tomorrow = TODAY.plusSeconds(86_400);
        assertEquals(List.of("piedra"), ids(notices.toAnnounce(holding(5, tomorrow), holding(32, tomorrow))),
            "a new day is a new period");
    }

    @Test
    void theMessageNamesTheMissionAndItsReward() {
        var definition = new MissionDefinition("piedra", MissionScope.DAILY, "§fTener 32 de piedra", "", MissionType.HOLD_EXACT_ITEM_COUNT,
            32, 250, 1, true, Map.of());
        assertEquals("§a¡Misión lista! §fTener 32 de piedra §7· premio §a$250 §7· /misiones para cobrarla",
            MissionReadyNotices.message(definition, amount -> "§e$" + (int) amount));
    }

    @Test
    void theGreetingSaysWhatIsReadyOrNothing() {
        assertEquals(java.util.Optional.empty(), MissionReadyNotices.greeting(0, 0, amount -> "$0"));
        assertEquals(java.util.Optional.of("§eTenés 1 misión para cobrar §7($400) · /misiones"),
            MissionReadyNotices.greeting(1, 400, amount -> "$" + (int) amount));
        assertEquals(java.util.Optional.of("§eTenés 3 misiones para cobrar §7($650) · /misiones"),
            MissionReadyNotices.greeting(3, 650, amount -> "§a$" + (int) amount));
    }

    @Test
    void aPlayerWhoLeftIsToldAgainAtMostOnce() {
        MissionReadyNotices notices = new MissionReadyNotices();
        notices.toAnnounce(holding(0, TODAY), holding(32, TODAY));
        notices.forget(JUAN);
        assertEquals(List.of("piedra"), ids(notices.toAnnounce(holding(0, TODAY), holding(32, TODAY))));
        assertEquals(List.of(), ids(notices.toAnnounce(holding(0, TODAY), holding(32, TODAY))));
    }
}
