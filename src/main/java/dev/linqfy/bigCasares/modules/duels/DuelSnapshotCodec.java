package dev.linqfy.bigCasares.modules.duels;

import org.bukkit.configuration.ConfigurationSection;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Decode the complete inventory before applying any of it. */
final class DuelSnapshotCodec {
    private DuelSnapshotCodec() { }

    static <T> Map<Integer, T> decode(ConfigurationSection section, int slots, Function<byte[], T> decoder) {
        Map<Integer, T> items = new HashMap<>();
        if (section == null) return items;
        for (String key : section.getKeys(false)) {
            int slot = Integer.parseInt(key);
            String encoded = section.getString(key);
            if (slot < 0 || slot >= slots || encoded == null) throw new IllegalArgumentException("Invalid inventory slot " + key);
            T item = decoder.apply(Base64.getDecoder().decode(encoded));
            if (item == null) throw new IllegalArgumentException("Invalid item in slot " + key);
            items.put(slot, item);
        }
        return items;
    }

    static boolean mayReplace(String revision, String marker) {
        return revision != null && revision.equals(marker);
    }

    static boolean mayRetire(String revision, String marker, boolean freshJoin) {
        return freshJoin && mayReplace(revision, marker);
    }
}
