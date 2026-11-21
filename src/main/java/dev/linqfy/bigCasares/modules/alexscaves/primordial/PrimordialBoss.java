package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World state of the Primordial Caves boss (mod {@code ACWorldData}: {@code primordialBossDefeatedOnce},
 * {@code firstPrimordialBossDefeatTimestamp} and the tracked Luxtructosaurus ids). The mod keeps it in the overworld's
 * saved data for every dimension; here it lives in the persistent data of the server's main world.
 */
public final class PrimordialBoss {
    static final NamespacedKey DEFEATED_KEY = new NamespacedKey("bigcasares", "ac_primordial_boss_defeated");
    static final NamespacedKey FIRST_DEFEAT_KEY = new NamespacedKey("bigcasares", "ac_primordial_boss_first_defeat");
    /** Mod {@code isMigration}: the amber monoliths bring the Atlatitans for a day (24000 ticks) after the first defeat. */
    static final long MIGRATION_TICKS = 24000L;

    /** Bosses that are erupting (mod {@code trackedLuxtructosaurusIds}): alive, with a player seeing them. */
    private static final Map<UUID, UUID> ACTIVE = new ConcurrentHashMap<>();

    private PrimordialBoss() {
    }

    // --- pure rules ---

    /** Mod {@code AmberMonolithBlockEntity.isMigration}. */
    static boolean migration(boolean defeatedOnce, long firstDefeat, long now) {
        return defeatedOnce && firstDefeat != -1L && firstDefeat + MIGRATION_TICKS > now;
    }

    // --- saved state ---

    private static PersistentDataContainer store() {
        World main = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return main == null ? null : main.getPersistentDataContainer();
    }

    /** One clock for the whole server (the main world's), as the mod's dimensions share the overworld's game time. */
    static long clock() {
        return Bukkit.getWorlds().isEmpty() ? 0L : Bukkit.getWorlds().get(0).getGameTime();
    }

    public static boolean defeatedOnce() {
        PersistentDataContainer data = store();
        return data != null && data.getOrDefault(DEFEATED_KEY, PersistentDataType.BYTE, (byte) 0) != 0;
    }

    public static long firstDefeat() {
        PersistentDataContainer data = store();
        return data == null ? -1L : data.getOrDefault(FIRST_DEFEAT_KEY, PersistentDataType.LONG, -1L);
    }

    /** Whether the monoliths are in the migration window now (game time of the monolith's world). */
    public static boolean migration(World world) {
        return migration(defeatedOnce(), firstDefeat(), clock());
    }

    /** Marks the boss as defeated; true when it is the first time (the world is told). */
    static boolean markDefeated(World world) {
        PersistentDataContainer data = store();
        if (data == null) {
            return false;
        }
        boolean first = data.getOrDefault(DEFEATED_KEY, PersistentDataType.BYTE, (byte) 0) == 0;
        data.set(DEFEATED_KEY, PersistentDataType.BYTE, (byte) 1);
        if (first) {
            data.set(FIRST_DEFEAT_KEY, PersistentDataType.LONG, clock());
        }
        return first;
    }

    // --- erupting bosses ---

    static void setActive(UUID boss, UUID world, boolean active) {
        if (active) {
            ACTIVE.put(boss, world);
        } else {
            ACTIVE.remove(boss);
        }
    }

    /** Mod {@code isPrimordialBossActive}: some boss is erupting in this world. */
    public static boolean active(World world) {
        return ACTIVE.containsValue(world.getUID());
    }

    static Set<UUID> activeBosses() {
        return Set.copyOf(ACTIVE.keySet());
    }

    static void clearActive() {
        ACTIVE.clear();
    }
}
