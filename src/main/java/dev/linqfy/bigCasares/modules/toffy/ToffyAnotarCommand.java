package dev.linqfy.bigCasares.modules.toffy;

import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEvents;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code /anotar} lists the tournaments with open sign-ups, {@code /anotar <torneo>} signs up for one (or lists the
 * open groups when the tournament needs a group), {@code /anotar <torneo> <grupo>} signs up and joins an open group,
 * {@code /anotar salir <torneo>} signs out (and leaves the running staff event of that tournament), {@code /anotar
 * estado} shows the running event and the tournaments of the player.
 */
final class ToffyAnotarCommand implements CommandExecutor, TabCompleter {
    static final String PERMISSION = "bigcasares.toffy.anotar";
    private static final String USAGE = " [torneo [grupo] | salir <torneo> | estado]";

    private final ToffyModule module;

    ToffyAnotarCommand(ToffyModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo los jugadores pueden anotarse.");
            return true;
        }
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§cNo tenes permiso para usar este comando.");
            return true;
        }
        if (args.length == 0) {
            openList(player, label);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("estado")) {
            status(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("salir")) {
            if (args.length != 2) {
                sender.sendMessage("§cUso: /" + label + " salir <torneo>");
                return true;
            }
            signOut(player, args[1]);
            return true;
        }
        if (args.length == 2) {
            signUpInGroup(player, args[0], args[1]);
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§cUso: /" + label + USAGE);
            return true;
        }
        signUpOrListGroups(player, args[0], label);
        return true;
    }

    /**
     * {@code /anotar <torneo>} and {@code /evento unirse}: when the tournament needs a group (grupo-obligatorio), lists
     * the open groups instead of signing the player up without one; otherwise signs up.
     */
    void signUpOrListGroups(Player player, String name, String label) {
        ToffyTournamentService tournaments = module.tournaments();
        Optional<ToffyTournament> found = tournaments == null ? Optional.empty() : tournaments.find(name);
        if (found.isPresent() && ToffyGroupRules.mustChooseGroup(found.get())) {
            listGroups(player, found.get(), label);
            return;
        }
        signUp(player, name);
    }

    /**
     * {@code /anotar <torneo> <grupo>}: the group is checked first (open, not full, level inside its range), then the
     * player is signed up to the tournament if needed, and placed in the group. Moving from another group is said.
     */
    private void signUpInGroup(Player player, String name, String groupName) {
        ToffyTournamentService tournaments = module.tournaments();
        if (tournaments == null) {
            player.sendMessage("§cToffy Complementaries no esta disponible.");
            return;
        }
        Optional<ToffyTournament> found = tournaments.find(name);
        if (found.isEmpty()) {
            module.send(player, ToffyMessages.TORNEO_NO_EXISTE, null, Map.of("torneo", name));
            return;
        }
        ToffyTournament tournament = found.get();
        Optional<ToffyGroup> group = tournaments.group(tournament.key(), groupName);
        if (group.isEmpty()) {
            player.sendMessage("§cNo existe el grupo " + groupName + " en " + tournament.displayName() + ".");
            listGroupNames(player, tournament);
            return;
        }
        double level = module.realLevel(player);
        Map<String, String> placeholders = Map.of(
            "jugador", player.getName(),
            "torneo", tournament.displayName(),
            "grupo", group.get().displayName(),
            "cantidad", String.valueOf(tournament.participants().size()));
        ToffyTournamentService.Placement placement;
        try {
            placement = tournaments.placeInGroup(tournament.key(), group.get().key(), player.getUniqueId(),
                player.getName(), level, ToffyTournamentService.GroupMode.PLAYER);
        } catch (UncheckedIOException failure) {
            player.sendMessage("§cNo se pudo guardar tu inscripcion: " + failure.getCause().getMessage());
            return;
        }
        String key = tournament.key();
        switch (placement.result()) {
            case ASSIGNED, MOVED, ALREADY_IN_GROUP -> {
                if (placement.registered()) {
                    module.send(player, ToffyMessages.ANOTADO, key, placeholders);
                    String announcement = module.message(ToffyMessages.ANUNCIO_ANOTADO, key, placeholders);
                    if (announcement != null) {
                        module.server().broadcastMessage(announcement);
                    }
                    if (placement.closedByCapacity()) {
                        module.announceClosed(tournament);
                    }
                }
                if (placement.result() == ToffyTournamentService.GroupResult.MOVED) {
                    module.groupChanged(key, List.of(player.getUniqueId()));
                }
                module.send(player, placement.result() == ToffyTournamentService.GroupResult.MOVED
                    ? ToffyMessages.GRUPO_CAMBIADO : ToffyMessages.GRUPO_ANOTADO, key, placeholders);
                joinLobby(player, key);
            }
            case GROUP_CLOSED -> module.send(player, ToffyMessages.GRUPO_CERRADO, key, placeholders);
            case GROUP_FULL -> module.send(player, ToffyMessages.GRUPO_LLENO, key, placeholders);
            case GROUP_LEVEL_OUT_OF_RANGE -> module.send(player, ToffyMessages.NIVEL_FUERA_DE_RANGO, key,
                module.rangeTexts(tournament, group.get(), player, level));
            case TOURNAMENT_CLOSED -> module.send(player, ToffyMessages.INSCRIPCION_CERRADA, key, placeholders);
            case TOURNAMENT_LEVEL_OUT -> module.send(player, ToffyMessages.NIVEL_FUERA_DE_RANGO, key,
                module.rangeTexts(tournament, player, level));
            default -> player.sendMessage("§cNo se pudo anotar al grupo " + group.get().displayName() + ".");
        }
    }

    /**
     * {@code /anotar <torneo>} when the tournament needs a group: lists the open groups with their members, capacity and
     * range, and the usage. Nobody is signed up by this.
     */
    private void listGroups(Player player, ToffyTournament tournament, String label) {
        List<ToffyGroup> open = ToffyGroupRules.openGroups(tournament);
        module.send(player, ToffyMessages.GRUPO_ELEGIR, tournament.key(), Map.of(
            "jugador", player.getName(),
            "torneo", tournament.displayName(),
            "grupos", ToffyGroupRules.names(open)));
        listGroupLines(player, open);
        player.sendMessage("§7Uso: /" + label + " " + tournament.displayName() + " <grupo>");
    }

    /** The open groups of the tournament, one line each. */
    private static void listGroupLines(Player player, List<ToffyGroup> open) {
        for (ToffyGroup group : open) {
            player.sendMessage("§7  §f" + group.displayName() + " §8- §e" + group.members().size()
                + (group.capacity() == null ? "" : "§7/§e" + group.capacity()) + "§7 anotados §8- §7nivel "
                + ToffyGroupRules.rangeText(group.minLevel(), group.maxLevel()));
        }
    }

    /** Tells the player which groups it can pick (used when a group name is wrong). */
    private void listGroupNames(Player player, ToffyTournament tournament) {
        List<ToffyGroup> open = ToffyGroupRules.openGroups(tournament);
        if (open.isEmpty()) {
            player.sendMessage("§7El torneo no tiene grupos abiertos ahora.");
            return;
        }
        player.sendMessage("§7Grupos abiertos: §f" + ToffyGroupRules.names(open));
    }

    /**
     * Signs the player up to the tournament, checking the sign-ups, the capacity and the level range, and joins the
     * lobby of its event when one is waiting. Also used by {@code /evento unirse}.
     */
    void signUp(Player player, String name) {
        ToffyTournamentService tournaments = module.tournaments();
        if (tournaments == null) {
            player.sendMessage("§cToffy Complementaries no esta disponible.");
            return;
        }
        double level = module.realLevel(player);
        ToffyTournamentService.RegisterResult result;
        try {
            result = tournaments.register(name, player.getUniqueId(), player.getName(), level);
        } catch (UncheckedIOException failure) {
            player.sendMessage("§cNo se pudo guardar tu inscripcion: " + failure.getCause().getMessage());
            return;
        }
        Optional<ToffyTournament> tournament = tournaments.find(name);
        String key = tournament.map(ToffyTournament::key).orElse(null);
        Map<String, String> placeholders = Map.of(
            "jugador", player.getName(),
            "torneo", tournament.map(ToffyTournament::displayName).orElse(name),
            "cantidad", String.valueOf(tournament.map(found -> found.participants().size()).orElse(0)));
        switch (result) {
            case REGISTERED, REGISTERED_CLOSED -> {
                module.send(player, ToffyMessages.ANOTADO, key, placeholders);
                String announcement = module.message(ToffyMessages.ANUNCIO_ANOTADO, key, placeholders);
                if (announcement != null) {
                    module.server().broadcastMessage(announcement);
                }
                if (result == ToffyTournamentService.RegisterResult.REGISTERED_CLOSED) {
                    String closed = module.message(ToffyMessages.CIERRE, key,
                        ToffyTournamentCommand.placeholders(tournament.orElseThrow(), null, null));
                    if (closed != null) {
                        module.server().broadcastMessage(closed);
                    }
                }
                joinLobby(player, key);
            }
            case ALREADY_REGISTERED -> {
                module.send(player, ToffyMessages.YA_ANOTADO, key, placeholders);
                joinLobby(player, key);
            }
            case CLOSED -> module.send(player, ToffyMessages.INSCRIPCION_CERRADA, key, placeholders);
            case LEVEL_OUT_OF_RANGE -> module.send(player, ToffyMessages.NIVEL_FUERA_DE_RANGO, key,
                module.rangeTexts(tournament.orElseThrow(), player, level));
            case UNKNOWN_TOURNAMENT -> module.send(player, ToffyMessages.TORNEO_NO_EXISTE, null, placeholders);
        }
    }

    /**
     * Someone who signs up (or was already signed up) while the event lobby of the tournament is open joins it. An event
     * limited to a group only takes the members of that group.
     */
    private void joinLobby(Player player, String key) {
        ToffyEvents events = module.events();
        if (events == null || key == null) {
            return;
        }
        ToffyTournamentService tournaments = module.tournaments();
        Optional<String> limited = events.lobbyGroup();
        if (limited.isPresent() && events.lobbyTournament().filter(key::equals).isPresent() && tournaments != null
            && !limited.get().equals(tournaments.groupOf(key, player.getUniqueId()).orElse(null))) {
            player.sendMessage("§7El evento en espera es solo para un grupo del torneo, y no sos parte de ese grupo.");
            return;
        }
        events.joinLobby(player, key);
    }

    /** {@code /anotar salir <torneo>} and {@code /evento salir}. */
    void signOut(Player player, String name) {
        ToffyTournamentService tournaments = module.tournaments();
        if (tournaments == null) {
            player.sendMessage("§cToffy Complementaries no esta disponible.");
            return;
        }
        Optional<ToffyTournament> found = tournaments.find(name);
        if (found.isEmpty()) {
            player.sendMessage("§cNo existe el torneo " + name + ".");
            return;
        }
        ToffyTournament tournament = found.get();
        if (!tournament.participants().containsKey(player.getUniqueId())) {
            player.sendMessage("§cNo estas anotado en el torneo " + tournament.displayName() + ".");
            return;
        }
        // The sign-up goes first: if saving it fails the player stays in the event too, never half out of it.
        try {
            tournaments.removeParticipant(tournament.key(), player.getUniqueId());
        } catch (UncheckedIOException failure) {
            player.sendMessage("§cNo se pudo guardar tu cambio: " + failure.getCause().getMessage());
            return;
        }
        ToffyEvents events = module.events();
        if (events != null) {
            events.withdraw(player.getUniqueId(), tournament.key());
        }
        player.sendMessage("§aSaliste del torneo §e" + tournament.displayName() + "§a.");
        if (events == null) {
            module.send(player, ToffyEvents.CASE_NO_DISPONIBLE, null, null);
        }
    }

    /** {@code /anotar} alone: open tournaments with their sign-ups, capacity, level range and schedules. */
    private void openList(Player player, String label) {
        ToffyTournamentService tournaments = module.tournaments();
        List<ToffyTournament> open = tournaments == null ? List.of() : tournaments.list().stream()
            .filter(ToffyTournament::open)
            .toList();
        if (open.isEmpty()) {
            player.sendMessage("§7No hay torneos con inscripcion abierta ahora.");
        } else {
            player.sendMessage("§6Torneos con inscripcion abierta:");
            for (ToffyTournament tournament : open) {
                player.sendMessage("§7  §f" + tournament.displayName() + " §8- §e" + tournament.participants().size()
                    + (tournament.capacity() == null ? "" : "/" + tournament.capacity())
                    + "§7 anotados §8- §7nivel " + ToffyTournamentCommand.rangeText(tournament)
                    + ToffyTournamentCommand.scheduleSuffix(tournament, module.tournamentTimes())
                    + (tournament.groupMandatory() ? " §8- §6por grupo" : "")
                    + yourGroup(tournament, player));
                listGroupLines(player, ToffyGroupRules.openGroups(tournament));
            }
        }
        player.sendMessage("§7Uso: /" + label + USAGE);
    }

    private void status(Player player) {
        ToffyEvents events = module.events();
        if (events == null) {
            player.sendMessage(ToffyEvents.UNAVAILABLE_TEXT);
        } else {
            events.status().forEach(player::sendMessage);
            if (events.participates(player.getUniqueId())) {
                player.sendMessage("§aEstas dentro del evento. Para salir: /anotar salir <torneo>.");
            }
        }
        ToffyTournamentService tournaments = module.tournaments();
        List<String> signedUp = tournaments == null ? List.of() : tournaments.list().stream()
            .filter(tournament -> tournament.participants().containsKey(player.getUniqueId()))
            .map(tournament -> tournament.displayName() + yourGroup(tournament, player))
            .toList();
        player.sendMessage(signedUp.isEmpty()
            ? "§7No estas anotado en ningun torneo."
            : "§7Anotado en: §f" + String.join("§7, §f", signedUp));
    }

    /** " (grupo X)" when the player is in a group of the tournament, empty otherwise. */
    private static String yourGroup(ToffyTournament tournament, Player player) {
        return tournament.groupOf(player.getUniqueId()).flatMap(tournament::group)
            .map(group -> " §7(grupo §f" + group.displayName() + "§7)")
            .orElse("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        ToffyTournamentService tournaments = module.tournaments();
        if (tournaments == null || args.length == 0 || args.length > 2) {
            return List.of();
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options;
        if (args.length == 1) {
            options = tournaments.list().stream()
                .filter(ToffyTournament::open)
                .map(ToffyTournament::key)
                .collect(Collectors.toCollection(ArrayList::new));
            options.add("salir");
            options.add("estado");
        } else if (args[0].equalsIgnoreCase("salir") && sender instanceof Player player) {
            options = tournaments.list().stream()
                .filter(tournament -> tournament.participants().containsKey(player.getUniqueId()))
                .map(ToffyTournament::key)
                .toList();
        } else if (args.length == 2) {
            options = tournaments.find(args[0])
                .map(tournament -> ToffyGroupRules.openGroups(tournament).stream().map(ToffyGroup::key).toList())
                .orElse(List.of());
        } else {
            return List.of();
        }
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
