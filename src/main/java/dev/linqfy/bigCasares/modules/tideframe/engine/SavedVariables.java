package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * config.yml variables.saved: global variables that stay with the player across menus, logouts and restarts. They
 * live in the player's own data (persistent data container, key tideframe:variables) as a small JSON object, so they
 * travel with the player file and need no database. A saved value wins over the page's declared default.
 */
final class SavedVariables {
    static final SavedVariables NONE = new SavedVariables(Set.of());
    static final int MAX_SAVED = 256;
    private static final NamespacedKey KEY = new NamespacedKey("tideframe", "variables");
    private final Set<String> names;
    private final boolean all;

    /** {@code configured}: bare variable names, or "*" for every global variable. Invalid names are left out. */
    SavedVariables(Collection<String> configured) {
        var valid = new LinkedHashSet<String>();
        boolean every = false;
        for (String raw : configured) {
            if (raw == null) continue;
            String name = raw.trim().replaceFirst("^[*$]", "");
            if (raw.trim().equals("*")) every = true;
            else if (name.matches("[a-z_][a-z0-9_]{0,23}")) valid.add(name);
        }
        names = Set.copyOf(valid); all = every;
    }
    /** Names in {@code configured} that are not variable names (for a warning). */
    static Set<String> invalid(Collection<String> configured) {
        var out = new LinkedHashSet<String>();
        for (String raw : configured) if (raw != null && !raw.trim().equals("*") && !raw.trim().replaceFirst("^[*$]", "").matches("[a-z_][a-z0-9_]{0,23}")) out.add(raw);
        return out;
    }
    boolean enabled() { return all || !names.isEmpty(); }
    boolean keeps(String name) { return all || names.contains(name); }

    /** Owner thread: the player's saved values into {@code globals} (a fresh session's global variables). */
    void load(Player player, Map<String, String> globals) {
        if (!enabled()) return;
        String raw = player.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        decode(raw).forEach((name, value) -> { if (keeps(name)) globals.put(name, value); });
    }
    /** Owner thread: the saved names of {@code globals} into the player's data, keeping values this session never touched. */
    void store(Player player, Map<String, String> globals) {
        if (!enabled()) return;
        var data = player.getPersistentDataContainer();
        var merged = decode(data.get(KEY, PersistentDataType.STRING));
        globals.forEach((name, value) -> { if (keeps(name) && (merged.containsKey(name) || merged.size() < MAX_SAVED)) merged.put(name, value); });
        if (merged.isEmpty()) return;
        data.set(KEY, PersistentDataType.STRING, encode(merged));
    }

    /** {"name":"value",...}; values are text (variables are always text). */
    static String encode(Map<String, String> values) {
        var out = new StringBuilder("{");
        values.forEach((name, value) -> {
            if (out.length() > 1) out.append(',');
            out.append(quote(name)).append(':').append(quote(value));
        });
        return out.append('}').toString();
    }
    private static String quote(String text) {
        var out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) out.append(String.format("\\u%04x", (int) c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
    /** The stored object; anything unreadable counts as nothing saved (the player keeps playing). */
    static Map<String, String> decode(String raw) {
        var out = new LinkedHashMap<String, String>();
        if (raw == null || raw.isBlank()) return out;
        try {
            if (dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec.parse(raw) instanceof Map<?, ?> map)
                for (var entry : map.entrySet())
                    if (entry.getKey() instanceof String name && name.matches("[a-z_][a-z0-9_]{0,23}") && entry.getValue() instanceof String value && value.length() <= 256)
                        out.put(name, value);
        } catch (RuntimeException unreadable) { out.clear(); }
        return out;
    }
}
