package dev.linqfy.bigCasares.modules.copperapple;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.items.CustomItem;
import dev.linqfy.bigCasares.modules.missions.MissionCommand;
import dev.linqfy.bigCasares.modules.missions.MissionHudView;
import dev.linqfy.bigCasares.modules.missions.MissionModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class CopperAppleCommand implements CommandExecutor, TabCompleter {

    private static final String GIVE_PERMISSION = "bigcasares.command.give";
    private static final String GIVE_OTHERS_PERMISSION = "bigcasares.command.give.others";

    private final BigCasares plugin;

    public CopperAppleCommand(BigCasares plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        if ("misiones".equalsIgnoreCase(args[0])) {
            return handleMissions(sender, args, label);
        }

        if (!"give".equalsIgnoreCase(args[0])) {
            sendUsage(sender, label);
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Missing item id.");
            sendUsage(sender, label);
            return true;
        }

        String itemId = args[1];
        Optional<CustomItem> itemOpt = plugin.getCustomItemRegistry().findById(itemId);
        if (itemOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Unknown item id: " + itemId);
            return true;
        }

        if (sender instanceof Player playerSender) {
            return handleFromPlayer(playerSender, sender, itemOpt.get(), args, label);
        }

        return handleFromConsole(sender, itemOpt.get(), args, label);
    }

    private boolean handleMissions(CommandSender sender, String[] args, String label) {
        MissionModule missionModule = plugin.getMissionModule();
        if (missionModule == null || !missionModule.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "El sistema de misiones no esta disponible.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden usar misiones.");
            return true;
        }

        if (args.length >= 2 && "reroll".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("bigcasares.missions.open")) {
                sender.sendMessage(ChatColor.RED + "No tenes permiso para usar misiones.");
                return true;
            }
            missionModule.rerollDaily(player);
            return true;
        }

        if (args.length >= 2 && "admin".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("bigcasares.missions.admin")) {
                sender.sendMessage(ChatColor.RED + "No tenes permiso para administrar misiones.");
                return true;
            }
            return handleMissionAdmin(player, sender, args, label, missionModule);
        }

        if (args.length >= 2 && "reclamar".equalsIgnoreCase(args[1])) {
            if (!sender.hasPermission("bigcasares.missions.claim")) {
                sender.sendMessage(ChatColor.RED + "No tenes permiso para reclamar recompensas.");
                return true;
            }
            missionModule.claimAll(player);
            missionModule.openHud(player, MissionHudView.CLAIMABLES);
            return true;
        }

        if (!sender.hasPermission("bigcasares.missions.open")) {
            sender.sendMessage(ChatColor.RED + "No tenes permiso para abrir el HUD de misiones.");
            return true;
        }

        missionModule.openHud(player, MissionCommand.resolveView(args));
        return true;
    }

    private boolean handleMissionAdmin(Player player, CommandSender sender, String[] args, String label, MissionModule missionModule) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Uso: /" + label + " misiones admin <reroll|reset|aprobar> <jugador> [mision]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[3]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado o desconectado: " + args[3]);
            return true;
        }

        if ("reroll".equalsIgnoreCase(args[2])) {
            missionModule.reroll(target);
            sender.sendMessage(ChatColor.GREEN + "Misiones reasignadas para " + target.getName() + ".");
            return true;
        }
        if ("reset".equalsIgnoreCase(args[2])) {
            missionModule.reset(target);
            sender.sendMessage(ChatColor.GREEN + "Progreso reiniciado para " + target.getName() + ".");
            return true;
        }

        if ("aprobar".equalsIgnoreCase(args[2]) || "approve".equalsIgnoreCase(args[2])) {
            String missionId = args.length >= 5 ? args[4] : null;
            int approved = missionModule.approveMissions(target, missionId);
            if (approved == 0) {
                sender.sendMessage(ChatColor.YELLOW + target.getName() + " no tiene misiones pendientes para aprobar"
                    + (missionId == null ? "." : " con id " + missionId + "."));
                return true;
            }
            sender.sendMessage(ChatColor.GREEN + "Aprobaste " + approved + " mision(es) de " + target.getName() + ".");
            target.sendMessage(ChatColor.GREEN + "Un admin aprobo tu mision. Reclamala en /misiones.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Accion admin desconocida: " + args[2]);
        sender.sendMessage(ChatColor.YELLOW + "Uso: /" + label + " misiones admin <reroll|reset|aprobar> <jugador> [mision]");
        return true;
    }

    private boolean handleFromPlayer(Player playerSender, CommandSender sender, CustomItem item, String[] args, String label) {
        if (args.length == 2) {
            if (!sender.hasPermission(GIVE_PERMISSION)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission.");
                return true;
            }
            giveItem(sender, playerSender, item, 1);
            return true;
        }

        if (args.length == 3) {
            if (isInteger(args[2])) {
                if (!sender.hasPermission(GIVE_PERMISSION)) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }
                int amount = parseAmount(args[2], sender);
                if (amount < 0) {
                    return true;
                }
                giveItem(sender, playerSender, item, amount);
                return true;
            }

            if (!sender.hasPermission(GIVE_OTHERS_PERMISSION)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to give items to others.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found or offline: " + args[2]);
                return true;
            }

            giveItem(sender, target, item, 1);
            return true;
        }

        if (args.length == 4) {
            if (!sender.hasPermission(GIVE_OTHERS_PERMISSION)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to give items to others.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found or offline: " + args[2]);
                return true;
            }

            int amount = parseAmount(args[3], sender);
            if (amount < 0) {
                return true;
            }

            giveItem(sender, target, item, amount);
            return true;
        }

        sendUsage(sender, label);
        return true;
    }

    private boolean handleFromConsole(CommandSender sender, CustomItem item, String[] args, String label) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Console usage requires a player target.");
            sendUsage(sender, label);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found or offline: " + args[2]);
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            amount = parseAmount(args[3], sender);
            if (amount < 0) {
                return true;
            }
        }

        giveItem(sender, target, item, amount);
        return true;
    }

    private int parseAmount(String value, CommandSender sender) {
        if (!isInteger(value)) {
            sender.sendMessage(ChatColor.RED + "Amount must be an integer.");
            return -1;
        }

        int amount = Integer.parseInt(value);
        if (amount < 1 || amount > 64) {
            sender.sendMessage(ChatColor.RED + "Amount must be between 1 and 64.");
            return -1;
        }

        return amount;
    }

    private void giveItem(CommandSender sender, Player target, CustomItem item, int amount) {
        ItemStack stack = item.createItemStack(amount);
        var leftovers = target.getInventory().addItem(stack);

        leftovers.values().forEach(leftover ->
            target.getWorld().dropItemNaturally(target.getLocation(), leftover)
        );

        if (sender == target) {
            sender.sendMessage(ChatColor.GREEN + "Received " + amount + "x " + item.getId() + ".");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + "x " + item.getId() + " to " + target.getName() + ".");
            target.sendMessage(ChatColor.GREEN + "Received " + amount + "x " + item.getId() + " from " + sender.getName() + ".");
        }

        if (!leftovers.isEmpty()) {
            target.sendMessage(ChatColor.YELLOW + "Some items were dropped because your inventory is full.");
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "Usage:");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " give <item_id>");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " give <item_id> <amount>");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " give <item_id> <player> [amount]");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " misiones [diarias|semanales|reclamar]");
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("give", "misiones").stream()
                .filter(option -> option.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && "misiones".equalsIgnoreCase(args[0])) {
            return List.of("diarias", "semanales", "reclamar", "admin", "reroll").stream()
                .filter(option -> option.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && "misiones".equalsIgnoreCase(args[0]) && "admin".equalsIgnoreCase(args[1])) {
            return List.of("reroll", "reset", "aprobar").stream()
                .filter(option -> option.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 4 && "misiones".equalsIgnoreCase(args[0]) && "admin".equalsIgnoreCase(args[1])) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[3].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return plugin.getCustomItemRegistry().getAllItems().stream()
                .map(CustomItem::getId)
                .filter(id -> id.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            List<String> suggestions = new ArrayList<>();
            suggestions.addAll(Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList()));
            for (String amount : List.of("1", "5", "10", "32", "64")) {
                if (amount.startsWith(args[2])) {
                    suggestions.add(amount);
                }
            }
            return suggestions;
        }

        if (args.length == 4 && "give".equalsIgnoreCase(args[0])) {
            List<String> suggestions = new ArrayList<>();
            for (String amount : List.of("1", "5", "10", "32", "64")) {
                if (amount.startsWith(args[3])) {
                    suggestions.add(amount);
                }
            }
            return suggestions;
        }

        return List.of();
    }
}
