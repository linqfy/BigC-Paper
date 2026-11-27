package dev.linqfy.bigCasares.modules.alexscaves.integration;

import dev.linqfy.bigCasares.modules.alexscaves.config.AlexsCavesSettings.IntegrationSettings;
import dev.linqfy.bigCasares.modules.missions.RewardGateway;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyPlacementRewardTest {
    private final UUID player = UUID.randomUUID();
    private final List<String> deposits = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final RewardGateway gateway = (id, amount) -> deposits.add(id + ":" + amount);

    private EconomyPlacementReward reward(IntegrationSettings settings, boolean vault) {
        return new EconomyPlacementReward(settings, () -> vault ? Optional.of(gateway) : Optional.empty(),
            warnings::add, (id, boss, place, amount) -> { });
    }

    @Test
    void disabledByDefaultPaysNothing() {
        reward(IntegrationSettings.defaults(), true).award(player, "luxtructosaurus", 1);
        assertTrue(deposits.isEmpty());
    }

    @Test
    void paysConfiguredAmountPerPlacement() {
        IntegrationSettings on = new IntegrationSettings(true, true, 2.0, Map.of("forsaken", List.of(10.0, 5.0)));
        EconomyPlacementReward reward = reward(on, true);
        reward.award(player, "forsaken", 2);
        reward.award(player, "forsaken", 3);
        reward.award(player, "hullbreaker", 1);
        assertEquals(List.of(player + ":5.0"), deposits);
    }

    @Test
    void withoutVaultWarnsOnlyOnce() {
        IntegrationSettings on = new IntegrationSettings(true, true, 2.0, Map.of("forsaken", List.of(10.0)));
        EconomyPlacementReward reward = reward(on, false);
        reward.award(player, "forsaken", 1);
        reward.award(player, "forsaken", 1);
        assertTrue(deposits.isEmpty());
        assertEquals(1, warnings.size());
    }
}
