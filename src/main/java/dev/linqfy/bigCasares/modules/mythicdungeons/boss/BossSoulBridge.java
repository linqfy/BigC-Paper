package dev.linqfy.bigCasares.modules.mythicdungeons.boss;

import dev.linqfy.bigCasares.modules.pveboss.BossDamageStanding;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Las almas de jefe del sistema de misiones de netherita: al morir un jefe, sus tres mayores daños reciben su alma
 * ({@code plugin.getNetheriteQuestModule().awardBossSouls(bossId, bossName, topDamagers)}).
 *
 * <p>Ese modulo todavia no esta en todas las versiones de BigCasares, asi que se busca por reflexion: si no existe (o
 * esta apagado) no pasa nada y el jefe da solo su premio normal. Cuando el modulo este siempre presente esto se puede
 * cambiar por la llamada directa.</p>
 */
public final class BossSoulBridge {

    static final String MODULE_GETTER = "getNetheriteQuestModule";
    static final String AWARD_METHOD = "awardBossSouls";

    private final Object plugin;
    private final BiConsumer<String, Throwable> failures;

    /**
     * @param plugin   el plugin de BigCasares (o cualquier objeto con el getter del modulo, en los tests)
     * @param failures recibe el mensaje y la causa si la llamada falla
     */
    public BossSoulBridge(Object plugin, BiConsumer<String, Throwable> failures) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.failures = Objects.requireNonNull(failures, "failures");
    }

    /** Los jugadores del podio en orden de puesto, como los espera el sistema de almas. */
    public static List<UUID> topDamagers(List<BossDamageStanding> standings) {
        return standings.stream()
            .sorted(Comparator.comparingInt(BossDamageStanding::placement))
            .map(BossDamageStanding::playerId)
            .distinct()
            .limit(3)
            .toList();
    }

    /**
     * Da las almas del jefe a su podio.
     *
     * @return {@code true} si el sistema de almas existe y recibio el pedido
     */
    public boolean award(String bossId, String bossName, List<BossDamageStanding> standings) {
        List<UUID> winners = topDamagers(standings);
        if (winners.isEmpty()) {
            return false;
        }
        try {
            Object quests = plugin.getClass().getMethod(MODULE_GETTER).invoke(plugin);
            if (quests == null) {
                return false;
            }
            Method award = quests.getClass().getMethod(AWARD_METHOD, String.class, String.class, List.class);
            award.invoke(quests, bossId, bossName, winners);
            return true;
        } catch (NoSuchMethodException absent) {
            return false; // esta version de BigCasares no tiene almas de jefe
        } catch (InvocationTargetException failure) {
            failures.accept("No se pudieron dar las almas de " + bossId, failure.getCause());
            return false;
        } catch (IllegalAccessException | RuntimeException failure) {
            failures.accept("No se pudieron dar las almas de " + bossId, failure);
            return false;
        }
    }
}
