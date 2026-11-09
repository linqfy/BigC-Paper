package dev.linqfy.bigCasares.modules.staffevents;

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

/** {@code /evento}: staff start and stop events; players join, leave and check status. */
final class StaffEventCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_ACTIONS = List.of("unirse", "salir", "estado");
    private static final List<String> STAFF_ACTIONS = List.of("iniciar", "detener", "tipos");

    private final StaffEventRuntime runtime;

    StaffEventCommand(StaffEventRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "estado" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "unirse", "join" -> withPlayer(sender, runtime::join);
            case "salir", "leave" -> withPlayer(sender, runtime::leave);
            case "estado", "status" -> runtime.status().forEach(sender::sendMessage);
            case "iniciar", "start" -> {
                if (requireStaff(sender)) {
                    start(sender, args);
                }
            }
            case "detener", "stop" -> {
                if (requireStaff(sender)) {
                    runtime.stop(sender);
                }
            }
            case "tipos", "types" -> {
                for (StaffEventType type : StaffEventType.values()) {
                    sender.sendMessage("§e" + type.id() + " §7— " + type.displayName()
                        + (type.arena() ? " §8(arena, inventario de evento)" : " §8(mundo normal, keep inventory)"));
                }
            }
            default -> help(sender, label);
        }
        return true;
    }

    private void start(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(StaffEventRuntime.PREFIX + "§cUso: /evento iniciar <tipo> [segundos de espera]");
            return;
        }
        Optional<StaffEventType> type = StaffEventType.parse(args[1]);
        if (type.isEmpty()) {
            sender.sendMessage(StaffEventRuntime.PREFIX + "§cTipo desconocido. Usá /evento tipos.");
            return;
        }
        Integer lobby = null;
        if (args.length >= 3) {
            try {
                lobby = Integer.parseInt(args[2]);
            } catch (NumberFormatException invalid) {
                lobby = -1;
            }
            if (lobby < 5 || lobby > 600) {
                sender.sendMessage(StaffEventRuntime.PREFIX + "§cLa espera tiene que estar entre 5 y 600 segundos.");
                return;
            }
        }
        runtime.start(sender, type.get(), lobby);
    }

    private static void withPlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (sender instanceof Player player) {
            if (player.hasPermission("bigcasares.events.join")) {
                action.accept(player);
            } else {
                player.sendMessage(StaffEventRuntime.PREFIX + "§cNo tenés permiso para participar en eventos.");
            }
        } else {
            sender.sendMessage(StaffEventRuntime.PREFIX + "§cSolo jugadores.");
        }
    }

    private static boolean requireStaff(CommandSender sender) {
        if (sender.hasPermission(StaffEventRuntime.ADMIN_PERMISSION)) {
            return true;
        }
        sender.sendMessage(StaffEventRuntime.PREFIX + "§cSolo el staff puede iniciar o detener eventos.");
        return false;
    }

    private static void help(CommandSender sender, String label) {
        sender.sendMessage(StaffEventRuntime.PREFIX + "§e/" + label + " unirse|salir|estado");
        if (sender.hasPermission(StaffEventRuntime.ADMIN_PERMISSION)) {
            sender.sendMessage("§e/" + label + " iniciar <tipo> [segundos] §7— /" + label + " detener — /" + label + " tipos");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean staff = sender.hasPermission(StaffEventRuntime.ADMIN_PERMISSION);
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(PLAYER_ACTIONS);
            if (staff) {
                options.addAll(STAFF_ACTIONS);
            }
        } else if (args.length == 2 && staff && Arrays.asList("iniciar", "start").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (StaffEventType type : StaffEventType.values()) {
                options.add(type.id());
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
