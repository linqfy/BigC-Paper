package dev.linqfy.bigCasares.modules.auctionhouse;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /ah [vender <precio>|mis|reclamar|quitar <id>|ayuda]}. */
final class AuctionHouseCommand implements CommandExecutor, TabCompleter {

    static final String USE_PERMISSION = "bigcasares.auctionhouse.use";
    static final String ADMIN_PERMISSION = "bigcasares.auctionhouse.admin";

    private final AuctionHouseController controller;

    AuctionHouseCommand(AuctionHouseController controller) {
        this.controller = controller;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("quitar") || sub.equals("remove")) {
            return remove(sender, label, args);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores pueden usar la casa de subastas.");
            return true;
        }
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendMessage("§cNo tenés permiso para usar la casa de subastas.");
            return true;
        }
        controller.service().expire();
        switch (sub) {
            case "" -> controller.menus().openBrowse(player, 0);
            case "vender", "sell" -> {
                if (args.length < 2) {
                    player.sendMessage("§eUso: §f/" + label + " vender <precio> §7(con el item en la mano)");
                    return true;
                }
                long price;
                try {
                    price = AuctionFormat.parsePrice(args[1]);
                } catch (NumberFormatException | ArithmeticException invalid) {
                    player.sendMessage("§cPrecio inválido: " + args[1]);
                    return true;
                }
                controller.sell(player, price);
            }
            case "mis", "mine" -> controller.menus().openMine(player);
            case "reclamar", "claim" -> controller.claim(player);
            default -> help(player, label);
        }
        return true;
    }

    private boolean remove(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNo tenés permiso para quitar subastas.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§eUso: §f/" + label + " quitar <id>");
            return true;
        }
        long id;
        try {
            id = Long.parseLong(args[1]);
        } catch (NumberFormatException invalid) {
            sender.sendMessage("§cId inválido: " + args[1]);
            return true;
        }
        controller.service().remove(id).ifPresentOrElse(
            listing -> sender.sendMessage("§aSubasta #" + id + " de " + listing.sellerName()
                + " quitada; el item quedó para que la reclame."),
            () -> sender.sendMessage("§cNo existe la subasta #" + id + "."));
        return true;
    }

    private static void help(Player player, String label) {
        player.sendMessage("§6Casa de subastas");
        player.sendMessage("§f/" + label + " §7- ver las subastas");
        player.sendMessage("§f/" + label + " vender <precio> §7- vender el item de tu mano");
        player.sendMessage("§f/" + label + " mis §7- ver y retirar tus subastas");
        player.sendMessage("§f/" + label + " reclamar §7- recibir items vencidos o pendientes");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("vender", "mis", "reclamar", "ayuda"));
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            options.add("quitar");
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
