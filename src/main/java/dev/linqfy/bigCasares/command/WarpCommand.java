package dev.linqfy.bigCasares.command;

import dev.linqfy.bigCasares.modules.warp.Warp;
import dev.linqfy.bigCasares.modules.warp.WarpMenuHolder;
import dev.linqfy.bigCasares.modules.warp.WarpModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class WarpCommand implements CommandExecutor, TabCompleter {
    private final WarpModule module;

    public WarpCommand(WarpModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden usar este comando.");
            return true;
        }
        if (module == null || module.getStorage() == null) {
            sender.sendMessage(ChatColor.RED + "El sistema de warps esta desactivado.");
            return true;
        }

        if (args.length > 0 && "manage".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("bigcasares.warp.manage") && !player.isOp()) {
                player.sendMessage(ChatColor.RED + "No tenes permiso para administrar warps.");
                return true;
            }
            return handleManage(player, args);
        }

        if (!player.hasPermission("bigcasares.warp.use")) {
            player.sendMessage(ChatColor.RED + "No tenes permiso para usar los warps.");
            return true;
        }

        openWarpMenu(player);
        return true;
    }

    private void openWarpMenu(Player player) {
        Collection<Warp> warps = module.getStorage().getAll();
        if (warps.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Todavia no hay warps.");
            return;
        }
        int size = Math.min(54, Math.max(9, (int) Math.ceil(warps.size() / 9.0) * 9));

        WarpMenuHolder holder = new WarpMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, "Menú de Warps");
        holder.inventory(inventory);

        NamespacedKey key = new NamespacedKey(module.getPlugin(), "warp_id");

        int slot = 0;
        for (Warp warp : warps) {
            if (slot >= size) break;

            Material mat = Material.matchMaterial(warp.icon());
            if (mat == null) mat = Material.ENDER_PEARL;

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + warp.name());
                List<String> lore = new ArrayList<>();
                if (warp.description() != null && !warp.description().isBlank()) {
                    lore.add(ChatColor.GRAY + warp.description());
                }
                lore.add("");
                lore.add(ChatColor.YELLOW + "Click para teletransportarte");
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, warp.id());
                item.setItemMeta(meta);
            }
            inventory.setItem(slot++, item);
        }

        player.openInventory(inventory);
    }

    private boolean handleManage(Player player, String[] args) {
        if (args.length < 3) {
            sendManageUsage(player);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        String id = args[2];

        try {
            if ("add".equals(action)) {
                if (module.getStorage().get(id) != null) {
                    player.sendMessage(ChatColor.RED + "Ya existe un warp con ese id.");
                    return true;
                }
                String name = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : id;
                Warp warp = new Warp(id, name, player.getLocation(), "ENDER_PEARL", "");
                module.getStorage().add(warp);
                player.sendMessage(ChatColor.GREEN + "Warp " + id + " creado.");
                return true;
            }

            Warp warp = module.getStorage().get(id);
            if (warp == null) {
                player.sendMessage(ChatColor.RED + "No se encontró el warp " + id + ".");
                return true;
            }

            if ("delete".equals(action)) {
                module.getStorage().remove(id);
                player.sendMessage(ChatColor.GREEN + "Warp " + id + " eliminado.");
                return true;
            }

            if ("rename".equals(action) && args.length >= 4) {
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                warp.name(name);
                module.getStorage().save();
                player.sendMessage(ChatColor.GREEN + "Warp " + id + " renombrado a " + name + ".");
                return true;
            }

            if ("icon".equals(action) && args.length >= 4) {
                Material mat = Material.matchMaterial(args[3]);
                if (mat == null) {
                    player.sendMessage(ChatColor.RED + "Material inválido.");
                    return true;
                }
                warp.icon(mat.name());
                module.getStorage().save();
                player.sendMessage(ChatColor.GREEN + "Icono de warp " + id + " cambiado a " + mat.name() + ".");
                return true;
            }

            if ("desc".equals(action) && args.length >= 4) {
                String desc = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                warp.description(desc);
                module.getStorage().save();
                player.sendMessage(ChatColor.GREEN + "Descripción de warp " + id + " actualizada.");
                return true;
            }
        } catch (RuntimeException ex) {
            module.getPlugin().getLogger().log(java.util.logging.Level.WARNING, "Fallo /warp manage " + action, ex);
            player.sendMessage(ChatColor.RED + "Ocurrió un error: " + ex.getMessage());
            return true;
        }

        sendManageUsage(player);
        return true;
    }

    private void sendManageUsage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Uso de /warp manage:");
        player.sendMessage(ChatColor.GRAY + "/warp manage add <id> [nombre]");
        player.sendMessage(ChatColor.GRAY + "/warp manage delete <id>");
        player.sendMessage(ChatColor.GRAY + "/warp manage rename <id> <nombre>");
        player.sendMessage(ChatColor.GRAY + "/warp manage icon <id> <material>");
        player.sendMessage(ChatColor.GRAY + "/warp manage desc <id> <descripcion...>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (module == null || module.getStorage() == null) {
            return List.of();
        }
        if (args.length == 1) {
            if (sender.hasPermission("bigcasares.warp.manage") || sender.isOp()) {
                return "manage".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("manage") : List.of();
            }
            return List.of();
        }

        if (args.length >= 2 && "manage".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return List.of("add", "delete", "rename", "icon", "desc").stream()
                        .filter(opt -> opt.startsWith(prefix))
                        .toList();
            }

            if (args.length == 3) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return module.getStorage().getAll().stream()
                        .map(Warp::id)
                        .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }

            if (args.length == 4 && "icon".equalsIgnoreCase(args[1])) {
                String prefix = args[3].toLowerCase(Locale.ROOT);
                return java.util.Arrays.stream(Material.values())
                        .map(Material::name)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
        }

        return List.of();
    }
}
