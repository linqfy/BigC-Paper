package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Counts the bombs primed in a world (kept with the world), so each alert can name its bomb: A1, A2... */
final class NukeSerial {
    private NukeSerial() {
    }

    /** The name of the next bomb primed in the world. */
    static String next(Plugin plugin, World world) {
        PersistentDataContainer data = world.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "nuke_serial");
        int serial = data.getOrDefault(key, PersistentDataType.INTEGER, 0) + 1;
        data.set(key, PersistentDataType.INTEGER, serial);
        return NukeSchedule.label(serial);
    }
}
