package dev.linqfy.bigCasares.modules.quadrants;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuadrantPotionListenerTest {
    private final World world = proxy(World.class, Map.of("getName", "world"));
    private final Player attacker = player(10, 10);
    private final Player protectedPlayer = player(-10, -10);
    private final Player anarchyPlayer = player(-20, 20);
    private final QuadrantRuntime runtime = new QuadrantRuntime(QuadrantSettings.defaults().map(),
        new CombatTagTracker(150, () -> 1_000L));

    @Test
    void splashProtectsOnlyChillTargetsAndTagsAllowedTargets() throws Exception {
        ThrownPotion potion = proxy(ThrownPotion.class, Map.of("getShooter", attacker, "getEffects", List.of()));
        PotionSplashEvent event = new PotionSplashEvent(potion, new java.util.HashMap<>(Map.of(
            protectedPlayer, 1.0, anarchyPlayer, 1.0)));
        QuadrantListener listener = listener(true);
        call(listener, "onSplash", PotionSplashEvent.class, event);
        assertEquals(0.0, event.getIntensity(protectedPlayer));
        assertEquals(1.0, event.getIntensity(anarchyPlayer));
        call(listener, "onSplashApplied", PotionSplashEvent.class, event);
        assertTrue(runtime.isTagged(attacker));
        assertTrue(runtime.isTagged(anarchyPlayer));
        assertFalse(runtime.isTagged(protectedPlayer));
    }

    @Test
    void lingeringCloudFiltersTargetsButHelpfulPotionsRemainUnchanged() throws Exception {
        AreaEffectCloud cloud = proxy(AreaEffectCloud.class, Map.of("getSource", attacker, "getCustomEffects", List.of()));
        AreaEffectCloudApplyEvent event = new AreaEffectCloudApplyEvent(cloud,
            new ArrayList<>(List.of(protectedPlayer, anarchyPlayer)));
        call(listener(true), "onCloud", AreaEffectCloudApplyEvent.class, event);
        assertEquals(List.of(anarchyPlayer), event.getAffectedEntities());
        AreaEffectCloudApplyEvent helpful = new AreaEffectCloudApplyEvent(cloud,
            new ArrayList<>(List.of(protectedPlayer)));
        call(listener(false), "onCloud", AreaEffectCloudApplyEvent.class, helpful);
        assertEquals(List.of(protectedPlayer), helpful.getAffectedEntities());
    }

    @Test
    void consentIsRemovedOnQuitAndNotInheritedOnReconnect() {
        assertTrue(runtime.toggleOptIn(attacker));
        assertTrue(runtime.isOptedIn(attacker));
        runtime.forget(attacker.getUniqueId());
        assertFalse(runtime.isOptedIn(attacker));
    }

    private QuadrantListener listener(boolean harmful) throws Exception {
        var constructor = QuadrantListener.class.getDeclaredConstructor(QuadrantRuntime.class, java.util.function.Predicate.class);
        constructor.setAccessible(true);
        return constructor.newInstance(runtime, (java.util.function.Predicate<java.util.Collection<org.bukkit.potion.PotionEffect>>) effects -> harmful);
    }

    private static void call(Object target, String name, Class<?> eventType, Object event) throws Exception {
        target.getClass().getDeclaredMethod(name, eventType).invoke(target, event);
    }

    private Player player(double x, double z) {
        return proxy(Player.class, Map.of("getUniqueId", UUID.randomUUID(), "getLocation", new Location(world, x, 64, z)));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Map<String, Object> values) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (object, method, args) -> {
            if (method.getName().equals("hashCode")) return System.identityHashCode(object);
            if (method.getName().equals("equals")) return object == args[0];
            if (method.getName().equals("toString")) return type.getSimpleName();
            if (values.containsKey(method.getName())) return values.get(method.getName());
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType().isPrimitive()) return 0;
            return null;
        });
    }
}
