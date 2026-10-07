package dev.linqfy.bigCasares.modules.celular;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

final class CelularCommand implements TabExecutor {

    private final Server server;
    private final CelularItems items;

    CelularCommand(Server server, CelularItems items) {
        this.server = server;
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§eUso: /" + label + " give [jugador]");
            return true;
        }
        Player target;
        if (args.length >= 2) {
            target = server.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cNo encontré al jugador " + args[1] + ".");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cDesde la consola tenés que poner el jugador: /" + label + " give <jugador>");
            return true;
        }
        target.getInventory().addItem(items.create())
            .values().forEach(left -> target.getWorld().dropItem(target.getLocation(), left));
        sender.sendMessage("§aLe di un celular a " + target.getName() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("give");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return server.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}
