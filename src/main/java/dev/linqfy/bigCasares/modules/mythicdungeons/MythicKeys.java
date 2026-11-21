package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/** Las marcas persistentes que el modulo pone en sus entidades (con el espacio de nombres del plugin). */
public final class MythicKeys {

    /** Misma clave que {@code MutangoModule.SYSTEM_MOB_KEY}: mob-scaling y Jeremy dejan en paz a estas entidades. */
    public static final String SYSTEM_MOB_KEY = "custom_entity";
    public static final String SYSTEM_MOB_VALUE = MythicDungeonsModule.MODULE_ID;

    final NamespacedKey systemMob;
    final NamespacedKey mob;
    final NamespacedKey dungeonSpawn;
    /** En las entidades de un encuentro: la instancia de jefe a la que pertenecen. */
    final NamespacedKey bossOwner;

    public MythicKeys(Plugin plugin) {
        systemMob = new NamespacedKey(plugin, SYSTEM_MOB_KEY);
        mob = new NamespacedKey(plugin, "mythic_mob");
        dungeonSpawn = new NamespacedKey(plugin, "mythic_dungeon_spawn");
        bossOwner = new NamespacedKey(plugin, "mythic_boss_owner");
    }

    /** La marca de entidad de sistema de BigC con el valor del modulo. */
    public void markSystem(Entity entity) {
        entity.getPersistentDataContainer().set(systemMob, PersistentDataType.STRING, SYSTEM_MOB_VALUE);
    }

    /** El id del mob mitico de la entidad, si es uno. */
    public Optional<String> mobId(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entity.getPersistentDataContainer().get(mob, PersistentDataType.STRING));
    }

    public boolean isDungeonSpawn(Entity entity) {
        Byte flag = entity.getPersistentDataContainer().get(dungeonSpawn, PersistentDataType.BYTE);
        return flag != null && flag != 0;
    }

    /** Si la entidad es de este modulo (mob, minion o decoracion de un encuentro). */
    public boolean isOwnEntity(Entity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        return SYSTEM_MOB_VALUE.equals(data.get(systemMob, PersistentDataType.STRING));
    }

    public Optional<UUID> bossOwner(Entity entity) {
        String raw = entity.getPersistentDataContainer().get(bossOwner, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException corrupt) {
            return Optional.empty();
        }
    }

    public void markBossOwner(Entity entity, UUID bossInstanceId) {
        entity.getPersistentDataContainer().set(bossOwner, PersistentDataType.STRING, bossInstanceId.toString());
    }
}
