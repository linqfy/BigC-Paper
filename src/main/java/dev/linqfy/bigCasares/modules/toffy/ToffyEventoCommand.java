package dev.linqfy.bigCasares.modules.toffy;

import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEvents;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /evento} (alias {@code /eventos}): the staff event commands of {@code /toffy evento}, plus the player commands
 * {@code unirse} (sign up to the tournament whose event is in lobby and join it) and {@code salir} (leave the running
 * event, as {@code /anotar salir} does).
 */
final class ToffyEventoCommand implements CommandExecutor, TabCompleter {
    static final String JOIN_PERMISSION = "bigcasares.events.join";
    private static final String LABEL = "evento";
    private static final List<String> PLAYER_SUBCOMMANDS = List.of("unirse", "salir");
    private static final List<String> STAFF_SUBCOMMANDS = List.of("iniciar", "detener", "tipos", "estado", "programar");

    private final ToffyModule module;
    private final ToffyTournamentCommand tournamentCommand;
    private final ToffyAnotarCommand anotar;

    ToffyEventoCommand(ToffyModule module) {
        this.module = module;
        this.tournamentCommand = new ToffyTournamentCommand(module);
        this.anotar = new ToffyAnotarCommand(module);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!module.isAvailable()) {
            sender.sendMessage("§cToffy Complementaries no esta disponible.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("estado")) {
            tournamentCommand.event(sender, withLabel(new String[] {"estado"}));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "unirse" -> join(sender);
            case "salir" -> leave(sender);
            default -> tournamentCommand.event(sender, withLabel(args));
        }
        return true;
    }

    /** {@code /evento unirse}: signs the player up to the tournament of the lobby (level and capacity apply). */
    private void join(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo los jugadores pueden unirse a un evento.");
            return;
        }
        if (!canJoin(sender)) {
            sender.sendMessage("§cNo tenes permiso para usar este comando.");
            return;
        }
        ToffyEvents events = module.events();
        Optional<String> lobby = events == null ? Optional.empty() : events.lobbyTournament();
        if (lobby.isEmpty()) {
            sender.sendMessage("§7No hay ningun evento esperando jugadores.");
            return;
        }
        anotar.signUpOrListGroups(player, lobby.get(), "anotar");
    }

    /** {@code /evento salir}: the same as {@code /anotar salir <torneo>} for the event running now. */
    private void leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo los jugadores pueden salir de un evento.");
            return;
        }
        if (!canJoin(sender)) {
            sender.sendMessage("§cNo tenes permiso para usar este comando.");
            return;
        }
        ToffyEvents events = module.events();
        Optional<String> active = events == null ? Optional.empty() : events.activeTournament();
        if (active.isEmpty()) {
            sender.sendMessage("§7No hay ningun evento en curso.");
            return;
        }
        anotar.signOut(player, active.get());
    }

    /** Joining and leaving need the events.join permission, or the sign-up permission (which it grants). */
    private static boolean canJoin(CommandSender sender) {
        return sender.hasPermission(JOIN_PERMISSION) || sender.hasPermission(ToffyAnotarCommand.PERMISSION);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(PLAYER_SUBCOMMANDS);
            if (sender.hasPermission(ToffyEvents.ADMIN_PERMISSION) || sender.hasPermission(ToffyCommand.ADMIN_PERMISSION)) {
                options.addAll(STAFF_SUBCOMMANDS);
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return options.stream().filter(option -> option.startsWith(prefix)).toList();
        }
        if (PLAYER_SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT))) {
            return List.of();
        }
        return tournamentCommand.completeEvent(sender, withLabel(args));
    }

    /** The arguments as {@code /toffy evento ...} sees them: the label first. */
    private static String[] withLabel(String[] args) {
        String[] result = new String[args.length + 1];
        result[0] = LABEL;
        System.arraycopy(args, 0, result, 1, args.length);
        return result;
    }
}
