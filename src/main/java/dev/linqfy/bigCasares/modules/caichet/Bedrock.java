package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.platform.ClientPlatform;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Quienes juegan desde Bedrock (por Geyser). Bedrock no muestra bien las entidades "display", los maniquies ni las
 * fuentes propias, asi que a esos jugadores se les muestran cuerpos de mobs comunes y particulas en su lugar. Quien es
 * de Bedrock lo dice BigCasares (su integracion con Geyser).
 */
final class Bedrock {

    private static final String STAND_TAG = "caichet_stand";
    /**
     * La marca que BigCasares reconoce como "entidad de otro sistema" (MobScalingListener.isEventOrSystemMob): sin ella,
     * su escalado de mobs le cambia la vida y el daño a gallinas, aldeanos y calamares, y los puede mutar. Hay que
     * ponerla dentro del consumer de spawn, antes de que salga el CreatureSpawnEvent.
     */
    static final String SYSTEM_MOB_KEY = "custom_entity";
    static final String SYSTEM_MOB_VALUE = "caichet";

    private Bedrock() {
    }

    static BigCasares plugin() {
        return CaiChetRuntime.plugin();
    }

    static boolean is(Player player) {
        return isBedrock(CaiChetRuntime.plugin().resolvePlayerPlatform(player.getUniqueId()));
    }

    /** Solo cuenta lo confirmado: un jugador sin plataforma conocida se trata como Java. */
    static boolean isBedrock(ClientPlatform platform) {
        return platform == ClientPlatform.BEDROCK;
    }

    static void markSystem(Entity entity) {
        entity.getPersistentDataContainer().set(new NamespacedKey(CaiChetRuntime.plugin(), SYSTEM_MOB_KEY),
            PersistentDataType.STRING, SYSTEM_MOB_VALUE);
    }

    /** Marca una entidad que solo es el cuerpo vanilla de algo que Bedrock no puede ver. */
    static void markStand(Entity entity) {
        entity.addScoreboardTag(STAND_TAG);
        markSystem(entity);
        if (entity instanceof LivingEntity living) {
            living.setCollidable(false); // no empuja ni es empujado
        }
    }

    static boolean isStand(Entity entity) {
        return entity.getScoreboardTags().contains(STAND_TAG);
    }

    /** Los jugadores de Bedrock que estan en este mundo. */
    static List<Player> in(World world) {
        List<Player> out = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (is(player)) {
                out.add(player);
            }
        }
        return out;
    }
}
