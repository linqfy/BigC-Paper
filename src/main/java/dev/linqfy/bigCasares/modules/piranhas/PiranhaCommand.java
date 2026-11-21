package dev.linqfy.bigCasares.modules.piranhas;

import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** {@code /piranha spawn [cantidad]}: spawns a school in the water the admin is looking at or standing in. */
final class PiranhaCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_SCHOOL = 20;

    private final PiranhaRuntime runtime;

    PiranhaCommand(PiranhaRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores.");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("spawn")) {
            player.sendMessage(ChatColor.YELLOW + "Uso: /" + label + " spawn [cantidad]");
            return true;
        }
        int amount = 4;
        if (args.length > 1) {
            try {
                amount = Math.max(1, Math.min(MAX_SCHOOL, Integer.parseInt(args[1])));
            } catch (NumberFormatException invalid) {
                player.sendMessage(ChatColor.RED + "Cantidad inválida.");
                return true;
            }
        }
        Block target = player.getTargetBlockExact(16, FluidCollisionMode.ALWAYS);
        if (target == null || target.getType() != Material.WATER) {
            target = player.getLocation().getBlock();
        }
        if (target.getType() != Material.WATER) {
            player.sendMessage(ChatColor.RED + "Mirá o metete en el agua.");
            return true;
        }
        int spawned = runtime.spawnSchool(target.getLocation().add(0.5, 0.5, 0.5), amount);
        player.sendMessage(ChatColor.GREEN + "Aparecieron " + spawned + " pirañas.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("spawn") : List.of();
    }
}
