package dev.linqfy.bigCasares.modules.duels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /duel <jugador> [kit|elegir] [chica|media|grande] [apuesta]}, plus {@code aceptar}, {@code rechazar},
 * {@code cancelar}, {@code menu} and {@code kits}. Without a kit both players ban and pick in a menu.
 */
final class DuelCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("aceptar", "rechazar", "cancelar", "menu", "kits");
    private static final List<String> ANY_KIT = List.of("elegir", "-", "random");

    private final DuelService service;
    private final Server server;

    DuelCommand(DuelService service, Server server) {
        this.service = service;
        this.server = server;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }
        if (args.length == 0) {
            usage(player, label);
            return true;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        String other = args.length > 1 ? args[1] : null;
        switch (first) {
            case "aceptar", "accept" -> service.accept(player, other);
            case "rechazar", "deny" -> service.deny(player, other);
            case "cancelar", "cancel" -> service.cancel(player);
            case "menu" -> {
                DuelSession session = service.sessionOf(player.getUniqueId());
                if (session != null && session.phase() == DuelSession.Phase.DRAFT) {
                    service.openDraft(session, player);
                } else {
                    player.sendMessage(Component.text("No estás eligiendo kit.", NamedTextColor.RED));
                }
            }
            case "kits" -> player.sendMessage(Component.text("Kits: " + String.join(", ", service.settings().kitIds()),
                NamedTextColor.AQUA));
            default -> challenge(player, label, args);
        }
        return true;
    }

    private void challenge(Player player, String label, String[] args) {
        Player target = server.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Component.text("Ese jugador no está conectado.", NamedTextColor.RED));
            return;
        }
        String kit = null;
        DuelArenaSize arena = service.settings().defaultArena();
        long wager = 0L;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            Optional<DuelArenaSize> size = DuelArenaSize.parse(arg);
            if (size.isPresent()) {
                arena = size.get();
            } else if (ANY_KIT.contains(arg)) {
                kit = null;
            } else if (service.settings().kits().containsKey(arg)) {
                kit = arg;
            } else {
                try {
                    wager = Long.parseLong(arg.startsWith("$") ? arg.substring(1) : arg);
                    if (wager < 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException invalid) {
                    player.sendMessage(Component.text("No entiendo '" + args[i] + "'.", NamedTextColor.RED));
                    usage(player, label);
                    return;
                }
            }
        }
        service.challenge(player, target, kit, arena, wager);
    }

    private static void usage(Player player, String label) {
        player.sendMessage(Component.text("/" + label + " <jugador> [kit|elegir] [chica|media|grande] [apuesta]",
            NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/" + label + " aceptar|rechazar [jugador] · cancelar · menu · kits",
            NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
            server.getOnlinePlayers().forEach(online -> options.add(online.getName()));
        } else if (SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT))) {
            if (args.length == 2) {
                server.getOnlinePlayers().forEach(online -> options.add(online.getName()));
            }
        } else {
            options.addAll(service.settings().kitIds());
            options.add("elegir");
            options.addAll(List.of("chica", "media", "grande"));
        }
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
