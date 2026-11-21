package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tablet hits read by polling and the order of damage and wind push, with small fakes. */
class EnlilHitsTest {

    private final List<String> calls = new ArrayList<>();
    private double health = 1024.0;
    private EntityDamageEvent last;
    private final Slime tablet = fake(Slime.class);
    private final Player player = fake(Player.class);

    @SuppressWarnings("unchecked")
    private <T> T fake(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> switch (method.getName()) {
                case "getHealth" -> health;
                case "setHealth" -> {
                    health = (double) args[0];
                    yield null;
                }
                case "getLastDamageCause" -> last;
                case "damage", "setVelocity" -> {
                    calls.add(method.getName());
                    yield null;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> type.getSimpleName() + "Fake";
                default -> null;
            });
    }

    @SuppressWarnings("unchecked")
    private static <T> T simple(Class<T> type, Object shooter) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> switch (method.getName()) {
                case "getShooter" -> shooter;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            });
    }

    private static DamageSource source() {
        return simple(DamageSource.class, null);
    }

    @Test
    void fireAndPoisonDoNotBreakATablet() {
        for (DamageCause cause : new DamageCause[] {DamageCause.FIRE_TICK, DamageCause.POISON, DamageCause.FIRE}) {
            health = 1023.0;
            last = new EntityDamageEvent(tablet, cause, source(), 1.0);
            assertEquals(Optional.empty(), EnlilTablets.struck(tablet));
            assertEquals(1024.0, health);
        }
        health = 1020.0;
        last = new EntityDamageByEntityEvent(simple(Zombie.class, null), tablet, DamageCause.ENTITY_ATTACK, source(), 4.0);
        assertEquals(Optional.empty(), EnlilTablets.struck(tablet));
    }

    @Test
    void playerMeleeAndPlayerArrowsBreakIt() {
        health = 1020.0;
        last = new EntityDamageByEntityEvent(player, tablet, DamageCause.ENTITY_ATTACK, source(), 4.0);
        assertSame(player, EnlilTablets.struck(tablet).orElseThrow());
        assertEquals(1024.0, health);

        health = 1020.0;
        Entity arrow = simple(Arrow.class, player);
        last = new EntityDamageByEntityEvent(arrow, tablet, DamageCause.PROJECTILE, source(), 4.0);
        assertSame(player, EnlilTablets.struck(tablet).orElseThrow());

        last = new EntityDamageByEntityEvent(player, tablet, DamageCause.ENTITY_ATTACK, source(), 4.0);
        assertTrue(EnlilTablets.struck(tablet).isEmpty(), "no health lost since the last poll: no hit");
    }

    @Test
    void theWindPushIsSetAfterTheDamageSoItsKnockbackDoesNotOverrideIt() {
        EnlilTornadoes.hitThenPush(player, 4.0, null, new Vector(1, 0, 0));
        assertEquals(List.of("damage", "setVelocity"), calls);
        calls.clear();
        EnlilTornadoes.hitThenPush(player, 0.0, null, new Vector(1, 0, 0));
        assertEquals(List.of("setVelocity"), calls);
    }
}
