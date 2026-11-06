package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryEffectOwnershipTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final String SPEED = "minecraft:speed";

    @Test
    void infiniteOriginalEffectsKeepTheirInfiniteDurationAfterTemporaryUpgrade() {
        Fake gateway = new Fake();
        gateway.effects.put(SPEED, effect(-1, 0));
        var owned = new LegendaryEffectOwnership(gateway);
        owned.apply(PLAYER, SPEED, effect(140, 1), 0);
        gateway.effects.put(SPEED, effect(120, 1));
        owned.cleanup(20);
        assertEquals(effect(-1, 0), gateway.effects.get(SPEED));
    }

    @Test
    void anExternalInfiniteEffectCannotBeMistakenForTheTemporaryBuff() {
        Fake gateway = new Fake();
        var owned = new LegendaryEffectOwnership(gateway);
        owned.apply(PLAYER, SPEED, effect(140, 1), 0);
        gateway.effects.put(SPEED, effect(-1, 1));
        owned.cleanup(20);
        assertEquals(effect(-1, 1), gateway.effects.get(SPEED));
    }

    @Test
    void shutdownRemovesItsBuffAndRestoresOnlyTheRemainingOriginalDuration() {
        Fake gateway = new Fake();
        gateway.effects.put(SPEED, effect(1_000, 0));
        var owned = new LegendaryEffectOwnership(gateway);
        owned.apply(PLAYER, SPEED, effect(140, 1), 0);
        gateway.effects.put(SPEED, effect(100, 1));
        owned.cleanup(40);
        assertEquals(effect(960, 0), gateway.effects.get(SPEED));
    }

    @Test
    void refreshedStormDebuffsDoNotBecomeTheOriginalEffect() {
        Fake gateway = new Fake();
        var owned = new LegendaryEffectOwnership(gateway);
        owned.apply(PLAYER, SPEED, effect(35, 2), 0);
        gateway.effects.put(SPEED, effect(15, 2));
        owned.apply(PLAYER, SPEED, effect(35, 2), 20);
        owned.cleanup(20);
        assertFalse(gateway.effects.containsKey(SPEED));
    }

    @Test
    void cleanupNeverErasesAnEffectReplacedByAnotherPlugin() {
        for (var replacement : java.util.List.of(effect(300, 1), effect(140, 2))) {
            Fake gateway = new Fake();
            var owned = new LegendaryEffectOwnership(gateway);
            owned.apply(PLAYER, SPEED, effect(140, 1), 0);
            gateway.effects.put(SPEED, replacement);
            owned.cleanup(5);
            assertEquals(replacement, gateway.effects.get(SPEED));
        }
    }

    @Test
    void aCuredBuffCannotResurrectItsSavedOriginalWhenTheAbilityEnds() {
        Fake gateway = new Fake();
        gateway.effects.put(SPEED, effect(1_000, 0));
        var owned = new LegendaryEffectOwnership(gateway);
        owned.apply(PLAYER, SPEED, effect(140, 1), 0);
        gateway.effects.clear();
        owned.prune(10);
        owned.cleanup(140);
        assertTrue(gateway.effects.isEmpty());
    }

    @Test
    void bloodlustExpiresAfterTenSecondsAndOnlyRestoresRemainingPriorStrength() {
        String strength = "minecraft:strength";
        Fake gateway = new Fake();
        gateway.effects.put(strength, effect(400, 0));
        var owned = new LegendaryEffectOwnership(gateway);
        owned.apply(PLAYER, strength, effect(200, 2), 0);
        gateway.effects.remove(strength);
        owned.prune(200);
        owned.cleanup(200);
        assertEquals(effect(200, 0), gateway.effects.get(strength));
    }

    @Test
    void endingBloodlustEarlyRemovesItsStrengthWithoutLeavingACombatBonus() {
        String strength = "minecraft:strength";
        Fake gateway = new Fake();
        var owned = new LegendaryEffectOwnership(gateway);
        owned.apply(PLAYER, strength, effect(200, 2), 0);
        gateway.effects.put(strength, effect(180, 2));
        owned.cleanup(20);
        assertFalse(gateway.effects.containsKey(strength));
    }

    private static LegendaryEffectOwnership.Effect effect(int duration, int amplifier) {
        return new LegendaryEffectOwnership.Effect(duration, amplifier, false, true, true);
    }
    private static final class Fake implements LegendaryEffectOwnership.Gateway {
        final Map<String, LegendaryEffectOwnership.Effect> effects = new HashMap<>();
        @Override public Optional<LegendaryEffectOwnership.Effect> current(UUID target, String type) {
            return Optional.ofNullable(effects.get(type));
        }
        @Override public boolean apply(UUID target, String type, LegendaryEffectOwnership.Effect effect) {
            effects.put(type, effect);
            return true;
        }
        @Override public void remove(UUID target, String type) { effects.remove(type); }
    }
}
