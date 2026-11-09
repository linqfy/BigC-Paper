package dev.linqfy.bigCasares.modules.customcrossbow;

import com.google.common.base.Function;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("removal")
class SniperDamageServiceTest {

    private final SniperDamageService service = new SniperDamageService();
    private final Player shooter = proxy(Player.class, (method, args) -> null);
    private double health = 20;
    private int noDamageTicks = 10;
    private boolean cancel;
    private EntityDamageByEntityEvent hit;
    private final LivingEntity target = proxy(LivingEntity.class, (method, args) -> switch (method) {
        case "getNoDamageTicks" -> noDamageTicks;
        case "setNoDamageTicks" -> { noDamageTicks = (int) args[0]; yield null; }
        case "damage" -> {
            assertEquals(0, noDamageTicks);
            hit = armoredHit((Entity) args[1], targetEntity(), (double) args[0]);
            hit.setCancelled(cancel);
            service.onDamage(hit);
            service.onDamageResult(hit);
            if (!hit.isCancelled()) {
                health -= hit.getFinalDamage();
            }
            yield null;
        }
        default -> null;
    });

    private Entity targetEntity() { return target; }

    @Test
    void hitRemovesExactlySixHeartsThroughArmorResistanceAndAbsorption() {
        assertTrue(service.damage(shooter, target, 12.0));
        assertEquals(8.0, health);
        assertEquals(12.0, hit.getFinalDamage());
        assertEquals(shooter, hit.getDamager());
        assertEquals(10, noDamageTicks);
    }

    @Test
    void protectedHitStaysCancelledAndDoesNotEarnOnHitEffects() {
        cancel = true;
        assertFalse(service.damage(shooter, target, 12.0));
        assertEquals(20.0, health);
        assertEquals(10, noDamageTicks);
    }

    @Test
    void ordinaryDamageKeepsItsArmorReduction() {
        EntityDamageByEntityEvent ordinary = armoredHit(shooter, target, 12.0);
        service.onDamage(ordinary);
        service.onDamageResult(ordinary);
        assertEquals(1.0, ordinary.getFinalDamage());
    }

    private EntityDamageByEntityEvent armoredHit(Entity attacker, Entity victim, double base) {
        Map<DamageModifier, Double> modifiers = new EnumMap<>(DamageModifier.class);
        modifiers.put(DamageModifier.BASE, base);
        modifiers.put(DamageModifier.ARMOR, -6.0);
        modifiers.put(DamageModifier.RESISTANCE, -2.0);
        modifiers.put(DamageModifier.MAGIC, -1.0);
        modifiers.put(DamageModifier.ABSORPTION, -2.0);
        Map<DamageModifier, Function<Double, Double>> functions = new EnumMap<>(DamageModifier.class);
        modifiers.forEach((modifier, value) -> functions.put(modifier, ignored -> value));
        DamageSource source = proxy(DamageSource.class, (method, args) -> null);
        return new EntityDamageByEntityEvent(attacker, victim, DamageCause.ENTITY_ATTACK, source, modifiers, functions);
    }

    private interface Call { Object invoke(String method, Object[] args); }

    private static <T> T proxy(Class<T> type, Call call) {
        UUID id = UUID.randomUUID();
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) ->
            switch (method.getName()) {
                case "getUniqueId" -> id;
                case "equals" -> self == args[0];
                case "hashCode" -> System.identityHashCode(self);
                case "toString" -> type.getSimpleName();
                default -> call.invoke(method.getName(), args);
            }));
    }
}
