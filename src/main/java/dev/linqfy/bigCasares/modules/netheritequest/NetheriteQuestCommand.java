package dev.linqfy.bigCasares.modules.netheritequest;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /netherita [estado]}, {@code /netherita activar <pieza>}; admins also get
 * {@code alma <jugador> <boss> [cantidad]} and {@code reset <jugador>}.
 */
final class NetheriteQuestCommand implements TabExecutor {

    static final String ADMIN_PERMISSION = "bigcasares.netherita.admin";

    private final NetheriteQuestRuntime runtime;

    NetheriteQuestCommand(NetheriteQuestRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "estado" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "estado" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cSolo jugadores.");
                    return true;
                }
                runtime.status(player.getUniqueId()).forEach(player::sendMessage);
            }
            case "activar" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cSolo jugadores.");
                    return true;
                }
                Optional<ArmorQuestPiece> piece = args.length < 2 ? Optional.empty() : ArmorQuestPiece.parse(args[1]);
                if (piece.isEmpty()) {
                    sender.sendMessage("§cUso: /" + label + " activar <casco|peto|pantalones|botas>");
                    return true;
                }
                runtime.activate(player, piece.get());
            }
            case "alma" -> giveSoul(sender, label, args);
            case "reset" -> reset(sender, label, args);
            default -> sender.sendMessage("§cUso: /" + label + " [estado|activar <pieza>]");
        }
        return true;
    }

    private void giveSoul(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNo tenés permiso.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUso: /" + label + " alma <jugador> <boss> [cantidad]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cEse jugador no está conectado.");
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException invalid) {
                sender.sendMessage("§cCantidad inválida.");
                return;
            }
        }
        String bossId = args[2].toLowerCase(Locale.ROOT);
        runtime.giveOrDrop(target, runtime.souls().create(bossId, bossId, amount));
        sender.sendMessage("§aLe diste " + amount + "x Alma de " + bossId + " a " + target.getName() + ".");
    }

    @SuppressWarnings("deprecation")
    private void reset(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNo tenés permiso.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUso: /" + label + " reset <jugador>");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        runtime.book().resetPlayer(target.getUniqueId());
        runtime.markDirty();
        sender.sendMessage("§aMisiones de netherita reiniciadas para " + args[1] + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("estado", "activar"));
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                options.addAll(List.of("alma", "reset"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("activar")) {
            Arrays.stream(ArmorQuestPiece.values()).map(ArmorQuestPiece::id).forEach(options::add);
        } else if (args.length == 2 && sender.hasPermission(ADMIN_PERMISSION)
            && (args[0].equalsIgnoreCase("alma") || args[0].equalsIgnoreCase("reset"))) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
