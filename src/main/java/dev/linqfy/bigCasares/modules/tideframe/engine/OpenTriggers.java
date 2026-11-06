package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;

/**
 * config.yml open: menus that open without a command. A key outside a menu (F, or Shift+F for HUD screens), an item
 * with Tideframe's mark, walking into a zone, or a sign with [Tideframe] on its first line. Everything is off until
 * configured. Parsing and the zone rules live here (no server needed); the plugin wires the events.
 */
final class OpenTriggers {
    static final OpenTriggers NONE = new OpenTriggers(Map.of(), Map.of(), List.of(), false);
    private static final String ID = "[a-z][a-z0-9_-]{0,31}";

    /** An item that opens {@code screen} with right click; {@code giveOnJoin} puts it in {@code slot} (0-8) on join when missing. */
    record Item(String screen, String material, String name, int slot, boolean giveOnJoin) { }
    /** A box of blocks (both corners included) that opens {@code screen} once per visit, at most every {@code cooldownSeconds}. */
    record Zone(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, String screen, int cooldownSeconds) {
        boolean contains(String worldName, int x, int y, int z) {
            return world.equals(worldName) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    final Map<String, String> keys;
    final Map<String, Item> items;
    final List<Zone> zones;
    final boolean signs;

    OpenTriggers(Map<String, String> keys, Map<String, Item> items, List<Zone> zones, boolean signs) {
        this.keys = Map.copyOf(keys); this.items = Map.copyOf(items); this.zones = List.copyOf(zones); this.signs = signs;
    }
    boolean any() { return !keys.isEmpty() || !items.isEmpty() || !zones.isEmpty() || signs; }
    /** The screen the swap-hands key opens outside a menu ({@code sneaking}: Shift+F), or null. */
    String keyScreen(boolean sneaking) { return keys.get(sneaking ? "SHIFT_F" : "F"); }
    /** The first zone containing the block, or null. */
    Zone zoneAt(String world, int x, int y, int z) {
        for (var zone : zones) if (zone.contains(world, x, y, z)) return zone;
        return null;
    }
    /** "[Tideframe]" on the first line and a screen id on the second: that id; otherwise null. */
    static String signScreen(String first, String second) {
        if (first == null || second == null || !first.trim().equalsIgnoreCase("[tideframe]")) return null;
        String id = second.trim().toLowerCase(Locale.ROOT);
        return id.matches(ID) ? id : null;
    }

    /** Reads config.yml open; {@code warn} receives what was left out and why. */
    static OpenTriggers parse(ConfigurationSection section, Consumer<String> warn) {
        if (section == null) return NONE;
        var keys = new LinkedHashMap<String, String>();
        var keySection = section.getConfigurationSection("keys");
        if (keySection != null) for (String key : keySection.getKeys(false)) {
            String name = key.toUpperCase(Locale.ROOT).replace('+', '_'), screen = keySection.getString(key, "");
            if (!name.equals("F") && !name.equals("SHIFT_F")) warn.accept("open.keys." + key + ": only F and SHIFT_F can open menus (the other keys move the player)");
            else if (!screen.matches(ID)) warn.accept("open.keys." + key + ": '" + screen + "' is not a screen id");
            else keys.put(name, screen);
        }
        var items = new LinkedHashMap<String, Item>();
        var itemSection = section.getConfigurationSection("items");
        if (itemSection != null) for (String screen : itemSection.getKeys(false)) {
            var item = itemSection.getConfigurationSection(screen);
            if (!screen.matches(ID) || item == null) { warn.accept("open.items." + screen + ": needs a screen id with material, name and slot"); continue; }
            String material = item.getString("material", "COMPASS").toUpperCase(Locale.ROOT);
            int slot = item.getInt("slot", -1);
            if (!material.matches("[A-Z0-9_]{1,64}")) { warn.accept("open.items." + screen + ": invalid material " + material); continue; }
            if (slot < -1 || slot > 8) { warn.accept("open.items." + screen + ": slot must be 0-8 (or leave it out)"); slot = -1; }
            items.put(screen, new Item(screen, material, item.getString("name", ""), slot, item.getBoolean("give-on-join", false)));
        }
        var zones = new ArrayList<Zone>();
        var zoneSection = section.getConfigurationSection("zones");
        if (zoneSection != null) for (String id : zoneSection.getKeys(false)) {
            var zone = zoneSection.getConfigurationSection(id);
            List<Integer> from = zone == null ? List.of() : zone.getIntegerList("from"), to = zone == null ? List.of() : zone.getIntegerList("to");
            String world = zone == null ? "" : zone.getString("world", ""), screen = zone == null ? "" : zone.getString("screen", "");
            if (from.size() != 3 || to.size() != 3 || world.isBlank() || !screen.matches(ID)) {
                warn.accept("open.zones." + id + ": needs world, from: [x, y, z], to: [x, y, z] and screen"); continue;
            }
            int cooldown = Math.max(0, Math.min(86_400, zone.getInt("cooldown-seconds", 30)));
            zones.add(new Zone(id, world, Math.min(from.get(0), to.get(0)), Math.min(from.get(1), to.get(1)), Math.min(from.get(2), to.get(2)),
                Math.max(from.get(0), to.get(0)), Math.max(from.get(1), to.get(1)), Math.max(from.get(2), to.get(2)), screen, cooldown));
        }
        return new OpenTriggers(keys, items, zones, section.getBoolean("signs", false));
    }

    /**
     * Zone visits of every player: a zone opens its screen when the player walks into it, again only after leaving and
     * after its cooldown. Owner threads only (one entry per player).
     */
    static final class Visits {
        private final Map<UUID, String> inside = new HashMap<>();
        private final Map<String, Long> lastOpen = new HashMap<>();
        /** The player is now in {@code zone} (null: in none); true when its screen should open now. */
        synchronized boolean entered(UUID player, Zone zone, long nowMillis) {
            String before = inside.get(player);
            if (zone == null) { inside.remove(player); return false; }
            inside.put(player, zone.id());
            if (zone.id().equals(before)) return false;
            String key = player + "/" + zone.id();
            Long last = lastOpen.get(key);
            if (last != null && nowMillis - last < zone.cooldownSeconds() * 1000L) return false;
            lastOpen.put(key, nowMillis);
            return true;
        }
        synchronized void forget(UUID player) { inside.remove(player); lastOpen.keySet().removeIf(key -> key.startsWith(player + "/")); }
        synchronized void clear() { inside.clear(); lastOpen.clear(); }
    }
}
