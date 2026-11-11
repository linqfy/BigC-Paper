package dev.linqfy.bigCasares.modules.villagermood;

import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerMafiaRuntimeTest {

    private final VillagerMafiaRuntime runtime = new VillagerMafiaRuntime(VillagerMafiaSettings.defaults(),
        new NamespacedKey("bigcasares", "villager_confined_ticks"), villager -> true, () -> 0.0);

    @Test
    void aCrewMemberCrossingAPortalIsOutsideChaseRange() {
        World overworld = proxy(World.class, (method, args) -> null);
        World nether = proxy(World.class, (method, args) -> null);
        Location target = new Location(overworld, 0, 64, 0);
        assertTrue(VillagerMafiaRuntime.withinChaseRange(new Location(overworld, 64, 64, 0), target));
        assertFalse(VillagerMafiaRuntime.withinChaseRange(new Location(overworld, 65, 64, 0), target));
        assertFalse(VillagerMafiaRuntime.withinChaseRange(new Location(nether, 0, 64, 0), target));
    }

    @Test
    void projectileDamageUsesConfiguredStrengthRegardlessOfArrowVelocity() {
        Target target = new Target();
        var event = damage(arrow(target.id), target.player(), 14.0);
        runtime.onDamage(event);
        assertFalse(event.isCancelled());
        assertEquals(4.0, event.getDamage());
    }

    @Test
    void arrowsCannotHurtBystandersOrProtectedPlayers() {
        Target target = new Target();
        Target bystander = new Target();
        var stray = damage(arrow(target.id), bystander.player(), 14.0);
        runtime.onDamage(stray);
        assertTrue(stray.isCancelled());
        for (GameMode mode : new GameMode[] {GameMode.CREATIVE, GameMode.SPECTATOR}) {
            target.mode = mode;
            var event = damage(arrow(target.id), target.player(), 14.0);
            runtime.onDamage(event);
            assertTrue(event.isCancelled());
        }
    }

    @Test
    void ordinaryProjectilesKeepTheirOriginalDamage() {
        var event = damage(arrow(null), new Target().player(), 14.0);
        runtime.onDamage(event);
        assertFalse(event.isCancelled());
        assertEquals(14.0, event.getDamage());
    }

    @Test
    void leavingDyingChangingModeOrPeacefulDifficultyEndsTargetEligibility() {
        Target target = new Target();
        assertTrue(VillagerMafiaRuntime.canTarget(target.player()));
        target.mode = GameMode.ADVENTURE;
        assertTrue(VillagerMafiaRuntime.canTarget(target.player()));
        target.difficulty = Difficulty.PEACEFUL;
        assertFalse(VillagerMafiaRuntime.canTarget(target.player()));
        target.difficulty = Difficulty.NORMAL;
        target.online = false;
        assertFalse(VillagerMafiaRuntime.canTarget(target.player()));
        target.online = true;
        target.dead = true;
        assertFalse(VillagerMafiaRuntime.canTarget(target.player()));
    }

    private static EntityDamageByEntityEvent damage(Entity attacker, Entity victim, double amount) {
        DamageSource source = proxy(DamageSource.class, (method, args) -> null);
        return new EntityDamageByEntityEvent(attacker, victim, DamageCause.PROJECTILE, source, amount);
    }

    private static Arrow arrow(UUID target) {
        PersistentDataContainer pdc = proxy(PersistentDataContainer.class, (method, args) -> switch (method) {
            case "has" -> target != null && args[0].toString().equals("bigcasares:villager_mafia_target");
            case "get" -> target == null ? null : target.toString();
            default -> throw new UnsupportedOperationException(method);
        });
        return proxy(Arrow.class, (method, args) -> switch (method) {
            case "getPersistentDataContainer" -> pdc;
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private static final class Target {
        private final UUID id = UUID.randomUUID();
        private GameMode mode = GameMode.SURVIVAL;
        private Difficulty difficulty = Difficulty.NORMAL;
        private boolean online = true;
        private boolean dead;

        Player player() {
            World world = proxy(World.class, (method, args) -> {
                if (method.equals("getDifficulty")) {
                    return difficulty;
                }
                throw new UnsupportedOperationException(method);
            });
            return proxy(Player.class, (method, args) -> switch (method) {
                case "getUniqueId" -> id;
                case "isOnline", "isValid" -> online;
                case "isDead" -> dead;
                case "getGameMode" -> mode;
                case "getWorld" -> world;
                default -> throw new UnsupportedOperationException(method);
            });
        }
    }

    private interface Answer {
        Object invoke(String method, Object[] args);
    }

    private static <T> T proxy(Class<T> type, Answer answer) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (object, method, args) -> answer.invoke(method.getName(), args)));
    }
}
