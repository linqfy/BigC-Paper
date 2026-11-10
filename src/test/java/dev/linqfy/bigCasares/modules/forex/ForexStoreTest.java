package dev.linqfy.bigCasares.modules.forex;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForexStoreTest {

    @Test
    void theWholeMarketSurvivesARestart() throws Exception {
        UUID creator = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID rugger = UUID.randomUUID();
        FakeBank bank = new FakeBank().give(creator, 50_000).give(alice, 50_000).give(rugger, 50_000);
        long[] now = {5_000_000L};
        ForexMarket market = new ForexMarket(ForexSettings.defaults(), bank, new ScriptedRandom(), () -> now[0]);
        market.buyEmeralds(alice, 2_000);
        market.create(creator, "MOON", "Moon Coin");
        market.linkTeam(creator, "MOON", "team-7");
        market.setCreatorFee(creator, "MOON", 0.05);
        market.buy(alice, "MOON", 8_000);
        market.stake(alice, "MOON", 300);
        market.create(rugger, "SCAM", "Scam");
        market.rugPull(rugger, "SCAM");
        market.toggleAura(alice);
        market.tick();

        String saved = ForexStore.serialize(market).saveToString();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(saved);
        ForexMarket restored = new ForexMarket(ForexSettings.defaults(), bank, new ScriptedRandom(), () -> now[0]);
        ForexStore.deserialize(yaml, restored, Logger.getAnonymousLogger());

        Shitcoin before = market.coin("MOON").orElseThrow();
        Shitcoin after = restored.coin("MOON").orElseThrow();
        assertEquals(market.emeralds(alice), restored.emeralds(alice), 1e-9);
        assertEquals(before.pool(), after.pool());
        assertEquals(before.dividendPot(), after.dividendPot());
        assertEquals(before.supply(), after.supply(), 1e-9);
        assertEquals(before.balance(alice), after.balance(alice), 1e-9);
        assertEquals(300, after.staked(alice), 1e-9);
        assertEquals(before.lockedUntil(alice), after.lockedUntil(alice));
        assertEquals(0.05, after.creatorFee(), 1e-12);
        assertEquals("team-7", after.teamId().orElseThrow());
        assertEquals(before.history(), after.history());
        assertEquals(market.price(before), restored.price(after), 1e-12);
        assertEquals(1, restored.rugPulls(rugger));
        assertTrue(restored.auraHidden(alice));
        assertEquals(market.graveyard(), restored.graveyard());
        assertEquals(market.millisToNextCycle(), restored.millisToNextCycle());
    }
}
