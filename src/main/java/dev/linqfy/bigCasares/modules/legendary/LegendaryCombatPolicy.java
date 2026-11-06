package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.BigCasares;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

final class LegendaryCombatPolicy {
    private final BigCasares plugin;

    LegendaryCombatPolicy(BigCasares plugin) { this.plugin = plugin; }

    boolean target(Player source, LivingEntity target) {
        if (target instanceof ArmorStand) return false;
        if (!(target instanceof Player) && !(target instanceof org.bukkit.entity.Enemy)
            && !(target instanceof org.bukkit.entity.Mob mob && source.equals(mob.getTarget()))) {
            return false; // Passive animals and merchant NPCs are not enemies.
        }
        if (target instanceof Tameable tameable && tameable.getOwner() != null) {
            return false; // Do not treat pets as enemies.
        }
        return affect(source, target);
    }

    /** Shared protection checks for effects that can also affect passive entities. */
    boolean affect(Player source, LivingEntity target) {
        if (source.equals(target) || target.isDead() || !target.isValid()) return false;
        if (target instanceof Player player) {
            if (!source.getWorld().getPVP() || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR || player.isInvulnerable()) {
                return false;
            }
            if (!plugin.getConfig().getBoolean("legendary-weapons.friendly-fire", false)) {
                var team = source.getScoreboard().getEntryTeam(source.getName());
                if (team != null && !team.allowFriendlyFire() && team.hasEntry(player.getName())) {
                    return false;
                }
            }
        }
        return allowed(source, target.getLocation(), target, LegendaryProtectionEvent.Action.DAMAGE);
    }

    boolean allowed(Player player, Location location, LivingEntity target, LegendaryProtectionEvent.Action action) {
        var event = new LegendaryProtectionEvent(player, location, target, action);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        // Respect WorldGuard without making it a required dependency. Unknown
        // versions fail closed; never silently bypass installed protections.
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                Object vector = adapter.getMethod("adapt", Location.class).invoke(null, location);
                Class<?> worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard");
                Object instance = worldGuard.getMethod("getInstance").invoke(null);
                Object platform = worldGuard.getMethod("getPlatform").invoke(instance);
                Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
                Object query = container.getClass().getMethod("createQuery").invoke(container);
                var wgPlugin = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
                Object localPlayer = player == null ? null
                    : wgPlugin.getClass().getMethod("wrapPlayer", Player.class).invoke(wgPlugin, player);
                Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
                String flag = action == LegendaryProtectionEvent.Action.DAMAGE ? "PVP"
                    : action == LegendaryProtectionEvent.Action.MOVE ? "ENTRY" : "BUILD";
                boolean build = action == LegendaryProtectionEvent.Action.ALTAR || action == LegendaryProtectionEvent.Action.BREAK;
                Object flags = java.lang.reflect.Array.newInstance(stateFlag, build ? 0 : 1);
                if (!build) java.lang.reflect.Array.set(flags, 0,
                    Class.forName("com.sk89q.worldguard.protection.flags.Flags").getField(flag).get(null));
                return (boolean) query.getClass().getMethod(build ? "testBuild" : "testState",
                    Class.forName("com.sk89q.worldedit.util.Location"),
                    Class.forName(player == null ? "com.sk89q.worldguard.LocalPlayer"
                        : "com.sk89q.worldguard.protection.association.RegionAssociable"),
                    flags.getClass()).invoke(query, vector, localPlayer, flags);
            } catch (ReflectiveOperationException | LinkageError failure) {
                return false;
            }
        }
        return true;
    }
}
