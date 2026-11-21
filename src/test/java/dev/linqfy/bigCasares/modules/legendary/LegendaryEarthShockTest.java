package dev.linqfy.bigCasares.modules.legendary;

import com.google.common.base.Function;
import org.bukkit.damage.DamageSource;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryEarthShockTest {
    private EntityDamageEvent sample(boolean mitigation) {
        var values = new EnumMap<EntityDamageEvent.DamageModifier, Double>(EntityDamageEvent.DamageModifier.class);
        var calculations = new EnumMap<EntityDamageEvent.DamageModifier, Function<Double, Double>>(EntityDamageEvent.DamageModifier.class);
        for (var modifier : EntityDamageEvent.DamageModifier.values()) {
            if (!mitigation && modifier != EntityDamageEvent.DamageModifier.BASE) continue;
            values.put(modifier, modifier == EntityDamageEvent.DamageModifier.BASE ? 20.0 : -2.0);
            calculations.put(modifier, ignored -> -2.0);
        }
        var source = (DamageSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DamageSource.class},
            (proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); });
        return new EntityDamageEvent(null, EntityDamageEvent.DamageCause.SONIC_BOOM, source, values, calculations);
    }

    @Test
    void armorResistanceEnchantmentShieldAndAbsorptionLeaveExactlyFourHealthDamage() {
        var event = sample(true);
        LegendaryEarthShock.setHealthDamage(event);
        assertEquals(4, event.getFinalDamage());
        for (var modifier : EntityDamageEvent.DamageModifier.values()) {
            assertEquals(modifier == EntityDamageEvent.DamageModifier.BASE ? 4 : 0, event.getDamage(modifier));
        }
    }

    @Test
    void unarmoredTargetsDoNotRequireOptionalDamageModifiers() {
        var event = sample(false);
        LegendaryEarthShock.setHealthDamage(event);
        assertEquals(4, event.getFinalDamage());
        assertFalse(event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR));
    }

    @Test
    void cancelledDamageIsLeftCancelledAndUnmodified() {
        var event = sample(true);
        event.setCancelled(true);
        double previous = event.getFinalDamage();
        LegendaryEarthShock.setHealthDamage(event);
        assertTrue(event.isCancelled());
        assertEquals(previous, event.getFinalDamage());
    }
}
