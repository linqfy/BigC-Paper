package dev.linqfy.bigCasares.command;

import dev.linqfy.bigCasares.modules.discord.DiscordLinkService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LinkDiscordCommand implements CommandExecutor {

    private final DiscordLinkService linkService;

    public LinkDiscordCommand(DiscordLinkService linkService) {
        this.linkService = linkService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Este comando es solo para jugadores.");
            return true;
        }

        if (linkService.isLinked(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "Tu cuenta ya está vinculada a Discord.");
            return true;
        }

        String code = linkService.generateCode(player.getUniqueId());
        
        player.sendMessage(ChatColor.AQUA + "===============================");
        player.sendMessage(ChatColor.WHITE + "Para vincular tu cuenta, envía un " + ChatColor.YELLOW + "Mensaje Directo" + ChatColor.WHITE + " a nuestro bot de Discord con este código:");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + code);
        player.sendMessage(ChatColor.GRAY + "El codigo vence en 10 minutos.");
        player.sendMessage(ChatColor.AQUA + "===============================");

        return true;
    }
}
