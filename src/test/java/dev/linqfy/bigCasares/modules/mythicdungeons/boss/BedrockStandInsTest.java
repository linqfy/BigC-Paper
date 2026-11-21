package dev.linqfy.bigCasares.modules.mythicdungeons.boss;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BedrockStandInsTest {

    private final UUID bedrockId = UUID.randomUUID();
    private final UUID javaId = UUID.randomUUID();
    private final Predicate<UUID> bedrock = Set.of(bedrockId)::contains;
    private final Player bedrockPlayer = fake(Player.class, Map.of("getUniqueId", bedrockId));
    private final Player javaPlayer = fake(Player.class, Map.of("getUniqueId", javaId));
    private final Entity standIn = fake(Zombie.class, Map.of("getUniqueId", UUID.randomUUID()));

    @Test
    void forwardsOnlyDirectMeleeFromABedrockPlayer() {
        assertSame(bedrockPlayer, BedrockStandIns.forwardedSource(hit(bedrockPlayer, DamageCause.ENTITY_ATTACK),
            bedrock));
        assertNull(BedrockStandIns.forwardedSource(hit(javaPlayer, DamageCause.ENTITY_ATTACK), bedrock));
    }

    @Test
    void neverForwardsSweepsExplosionsOrEnvironmentalDamage() {
        assertNull(BedrockStandIns.forwardedSource(hit(bedrockPlayer, DamageCause.ENTITY_SWEEP_ATTACK), bedrock));
        assertNull(BedrockStandIns.forwardedSource(hit(javaPlayer, DamageCause.ENTITY_SWEEP_ATTACK), bedrock));
        assertNull(BedrockStandIns.forwardedSource(hit(bedrockPlayer, DamageCause.ENTITY_EXPLOSION), bedrock));
        Entity zombie = fake(Zombie.class, Map.of("getUniqueId", UUID.randomUUID()));
        assertNull(BedrockStandIns.forwardedSource(hit(zombie, DamageCause.ENTITY_ATTACK), bedrock));
        for (DamageCause cause : new DamageCause[] {DamageCause.FIRE, DamageCause.FIRE_TICK, DamageCause.SUFFOCATION,
            DamageCause.CRAMMING, DamageCause.BLOCK_EXPLOSION, DamageCause.LAVA}) {
            EntityDamageEvent event = new EntityDamageEvent(standIn, cause, source(), 4.0);
            assertNull(BedrockStandIns.forwardedSource(event, bedrock));
        }
    }

    @Test
    void forwardsProjectilesShotOnlyByBedrockPlayers() {
        Arrow fromBedrock = fake(Arrow.class, Map.of("getShooter", bedrockPlayer));
        Arrow fromJava = fake(Arrow.class, Map.of("getShooter", javaPlayer));
        Arrow fromDispenser = fake(Arrow.class, Map.of());

        assertSame(fromBedrock, BedrockStandIns.forwardedSource(hit(fromBedrock, DamageCause.PROJECTILE), bedrock));
        assertNull(BedrockStandIns.forwardedSource(hit(fromJava, DamageCause.PROJECTILE), bedrock));
        assertNull(BedrockStandIns.forwardedSource(hit(fromDispenser, DamageCause.PROJECTILE), bedrock));
    }

    private EntityDamageByEntityEvent hit(Entity damager, DamageCause cause) {
        return new EntityDamageByEntityEvent(damager, standIn, cause, source(), 6.0);
    }

    private static DamageSource source() {
        return fake(DamageSource.class, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Object> results) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, arguments) -> {
                if (results.containsKey(method.getName())) {
                    return results.get(method.getName());
                }
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Fake";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> defaultValue(method.getReturnType());
                };
            });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        return switch (type.getName()) {
            case "boolean" -> false;
            case "void" -> null;
            case "float" -> 0.0F;
            case "double" -> 0.0;
            case "long" -> 0L;
            case "char" -> '\0';
            case "byte" -> (byte) 0;
            case "short" -> (short) 0;
            default -> 0;
        };
    }
}
