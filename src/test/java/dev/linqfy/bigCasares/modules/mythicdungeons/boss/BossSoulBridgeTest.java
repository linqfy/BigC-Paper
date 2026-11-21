package dev.linqfy.bigCasares.modules.mythicdungeons.boss;

import dev.linqfy.bigCasares.modules.pveboss.BossDamageStanding;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossSoulBridgeTest {

    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID FOURTH = UUID.fromString("00000000-0000-0000-0000-000000000004");

    /** Same public shape as the netherite quest module. */
    public static final class FakeQuests {
        final List<String> calls = new ArrayList<>();
        boolean fail;

        public void awardBossSouls(String bossId, String bossName, List<UUID> topDamagers) {
            if (fail) {
                throw new IllegalStateException("boom");
            }
            calls.add(bossId + "|" + bossName + "|" + topDamagers);
        }
    }

    public static final class PluginWithSouls {
        final FakeQuests quests;

        PluginWithSouls(FakeQuests quests) {
            this.quests = quests;
        }

        public FakeQuests getNetheriteQuestModule() {
            return quests;
        }
    }

    public static final class PluginWithoutSouls {
    }

    private static List<BossDamageStanding> podium() {
        return List.of(new BossDamageStanding(SECOND, 80.0, 2), new BossDamageStanding(FIRST, 120.0, 1),
            new BossDamageStanding(THIRD, 40.0, 3));
    }

    @Test
    void soulsGoToThePodiumInPlacementOrder() {
        FakeQuests quests = new FakeQuests();
        BossSoulBridge bridge = new BossSoulBridge(new PluginWithSouls(quests), (message, cause) -> { });

        assertTrue(bridge.award("myth-zeus", "Zeus", podium()));

        assertEquals(List.of("myth-zeus|Zeus|" + List.of(FIRST, SECOND, THIRD)), quests.calls);
    }

    @Test
    void onlyThreePlayersAreSentEvenWithALongerRanking() {
        List<BossDamageStanding> four = new ArrayList<>(podium());
        four.add(new BossDamageStanding(FOURTH, 10.0, 4));

        assertEquals(List.of(FIRST, SECOND, THIRD), BossSoulBridge.topDamagers(four));
    }

    @Test
    void aPluginWithoutTheSoulSystemIsANoOp() {
        List<String> failures = new ArrayList<>();
        BossSoulBridge bridge = new BossSoulBridge(new PluginWithoutSouls(), (message, cause) -> failures.add(message));

        assertFalse(bridge.award("myth-zeus", "Zeus", podium()));
        assertTrue(failures.isEmpty());
    }

    @Test
    void aDisabledSoulModuleIsANoOp() {
        BossSoulBridge bridge = new BossSoulBridge(new PluginWithSouls(null), (message, cause) -> { });

        assertFalse(bridge.award("myth-zeus", "Zeus", podium()));
    }

    @Test
    void nobodyOnThePodiumMeansNoCall() {
        FakeQuests quests = new FakeQuests();
        BossSoulBridge bridge = new BossSoulBridge(new PluginWithSouls(quests), (message, cause) -> { });

        assertFalse(bridge.award("myth-zeus", "Zeus", List.of()));
        assertTrue(quests.calls.isEmpty());
    }

    @Test
    void aFailingSoulSystemIsReportedAndDoesNotThrow() {
        FakeQuests quests = new FakeQuests();
        quests.fail = true;
        List<String> failures = new ArrayList<>();
        BossSoulBridge bridge = new BossSoulBridge(new PluginWithSouls(quests),
            (message, cause) -> failures.add(message + ": " + cause.getMessage()));

        assertFalse(bridge.award("myth-zeus", "Zeus", podium()));
        assertEquals(List.of("No se pudieron dar las almas de myth-zeus: boom"), failures);
    }
}
