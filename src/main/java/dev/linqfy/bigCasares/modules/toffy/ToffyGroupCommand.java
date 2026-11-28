package dev.linqfy.bigCasares.modules.toffy;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * {@code /toffy torneo grupo <torneo> <subcomando> ...}: the groups (sub-categories) of a tournament. Admin only; the
 * permission is checked by {@link ToffyTournamentCommand}.
 */
final class ToffyGroupCommand {
    private static final String PREFIX = "§6Toffy §8» §7";
    static final String USAGE = "§cUso: /toffy torneo grupo <torneo> <crear|borrar|lista|ver|abrir|cerrar|cupo|nivel|"
        + "lugar|asignar|quitar|repartir|obligatorio> ...";
    private static final String FORCE = "forzar";
    private static final List<String> SUBCOMMANDS = List.of("crear", "borrar", "lista", "ver", "abrir", "cerrar",
        "cupo", "nivel", "lugar", "asignar", "quitar", "repartir", "obligatorio");

    private final ToffyModule module;

    ToffyGroupCommand(ToffyModule module) {
        this.module = module;
    }

    /** {@code args} is {@code [torneo, grupo, <torneo>, <subcomando>, ...]}. */
    void handle(CommandSender sender, ToffyTournamentService tournaments, String[] args) {
        Optional<ToffyTournament> found = tournaments.find(args[2]);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + args[2] + ".");
            return;
        }
        ToffyTournament tournament = found.get();
        switch (args[3].toLowerCase(Locale.ROOT)) {
            case "crear" -> create(sender, tournaments, tournament, args);
            case "borrar" -> {
                if (ToffyTournamentCommand.needs(sender, args, 5, "/toffy torneo grupo <torneo> borrar <grupo>")) {
                    delete(sender, tournaments, tournament, args[4]);
                }
            }
            case "lista" -> list(sender, tournament);
            case "ver" -> {
                if (ToffyTournamentCommand.needs(sender, args, 5, "/toffy torneo grupo <torneo> ver <grupo>")) {
                    view(sender, tournament, args[4]);
                }
            }
            case "abrir" -> setOpen(sender, tournaments, tournament, args, true);
            case "cerrar" -> setOpen(sender, tournaments, tournament, args, false);
            case "cupo" -> capacity(sender, tournaments, tournament, args);
            case "nivel" -> level(sender, tournaments, tournament, args);
            case "lugar" -> place(sender, tournaments, tournament, args);
            case "asignar" -> assign(sender, tournaments, tournament, args);
            case "quitar" -> leave(sender, tournaments, tournament, args);
            case "repartir" -> repartir(sender, tournaments, tournament, args);
            case "obligatorio" -> mandatory(sender, tournaments, tournament, args);
            default -> sender.sendMessage(USAGE);
        }
    }

    private void create(CommandSender sender, ToffyTournamentService tournaments, ToffyTournament tournament,
                        String[] args) {
        if (!ToffyTournamentCommand.needs(sender, args, 5, "/toffy torneo grupo <torneo> crear <grupo> [abierto]")) {
            return;
        }
        boolean open = args.length > 5 && args[5].equalsIgnoreCase("abierto");
        if (args.length > 5 && !open) {
            sender.sendMessage("§cUso: /toffy torneo grupo <torneo> crear <grupo> [abierto]");
            return;
        }
        try {
            ToffyGroup group = tournaments.createGroup(tournament.key(), args[4], open);
            sender.sendMessage(PREFIX + "§aGrupo §e" + group.displayName() + "§a creado en " + tournament.displayName()
                + (open ? " (abierto: los jugadores lo eligen con /anotar " + tournament.key() + " " + group.key() + ")"
                : " (cerrado: lo asigna el staff o repartir)") + ".");
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            sender.sendMessage("§c" + invalid.getMessage());
        }
    }

    /** Deletes the group; its members stay signed up without a group, and a scheduled event of it is cancelled. */
    private void delete(CommandSender sender, ToffyTournamentService tournaments, ToffyTournament tournament,
                        String name) {
        ToffyTournamentService.GroupDeletion deletion = tournaments.deleteGroup(tournament.key(), name);
        if (!deletion.deleted()) {
            sender.sendMessage("§cNo existe el grupo " + name + " en " + tournament.displayName() + ".");
            return;
        }
        sender.sendMessage(PREFIX + "§aGrupo " + name + " borrado. Sus miembros siguen anotados al torneo, sin grupo.");
        if (deletion.eventCancelled()) {
            sender.sendMessage(PREFIX + "§eEl evento programado de ese grupo se cancelo.");
        }
        module.groupChanged(tournament.key(), deletion.members());
    }

    private void list(CommandSender sender, ToffyTournament tournament) {
        List<ToffyGroup> groups = tournament.groups();
        if (groups.isEmpty()) {
            sender.sendMessage(PREFIX + "El torneo " + tournament.displayName()
                + " no tiene grupos. Crea uno con /toffy torneo grupo " + tournament.key() + " crear <grupo>.");
            return;
        }
        sender.sendMessage(PREFIX + "Grupos de " + tournament.displayName() + " (" + groups.size() + "):");
        for (ToffyGroup group : groups) {
            sender.sendMessage("§7  " + summary(group));
        }
    }

    /** One line about a group: name, members, capacity, range, open or closed and places. */
    static String summary(ToffyGroup group) {
        return "§f" + group.displayName() + " §8- §e" + group.members().size()
            + (group.capacity() == null ? "" : "§7/§e" + group.capacity()) + "§7 miembros §8- "
            + (group.open() ? "§aabierto" : "§cno abierto") + " §8- nivel "
            + ToffyGroupRules.rangeText(group.minLevel(), group.maxLevel())
            + " §8- §7lugares: §f" + group.places().size();
    }

    private void view(CommandSender sender, ToffyTournament tournament, String name) {
        Optional<ToffyGroup> found = tournament.group(safeKey(name));
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el grupo " + name + " en " + tournament.displayName() + ".");
            return;
        }
        ToffyGroup group = found.get();
        sender.sendMessage(PREFIX + "§f" + group.displayName() + "§7 de " + tournament.displayName() + " - "
            + (group.open() ? "§aabierto" : "§cno abierto") + "§7 - §e" + group.members().size() + "§7 miembros:");
        sender.sendMessage("§7  Nivel: §f" + ToffyGroupRules.rangeText(group.minLevel(), group.maxLevel())
            + " §8(sin rango = todos)");
        sender.sendMessage("§7  Cupo: §f" + (group.capacity() == null ? "sin cupo" : group.capacity()));
        if (group.places().isEmpty()) {
            sender.sendMessage("§7  Lugares: §8ninguno (usa el lugar del torneo)");
        } else {
            sender.sendMessage("§7  Lugares: §f" + group.places().size());
            for (ToffyStoredLocation place : group.places()) {
                sender.sendMessage("§7    - §f" + ToffyTournamentCommand.placeText(place));
            }
        }
        int position = 1;
        for (UUID id : group.members()) {
            Player online = module.server().getPlayer(id);
            String memberName = online != null ? online.getName() : tournament.participants().getOrDefault(id, "?");
            sender.sendMessage("§7  " + position++ + ". §f" + memberName
                + (online != null ? " §a(online)" : " §8(offline)"));
        }
    }

    private void setOpen(CommandSender sender, ToffyTournamentService tournaments, ToffyTournament tournament,
                         String[] args, boolean open) {
        String usage = open ? "/toffy torneo grupo <torneo> abrir <grupo>" : "/toffy torneo grupo <torneo> cerrar <grupo>";
        if (!ToffyTournamentCommand.needs(sender, args, 5, usage)) {
            return;
        }
        ToffyTournamentService.Change change = tournaments.setGroupOpen(tournament.key(), args[4], open);
        if (change == ToffyTournamentService.Change.UNKNOWN) {
            sender.sendMessage("§cNo existe el grupo " + args[4] + " en " + tournament.displayName() + ".");
        } else if (change == ToffyTournamentService.Change.UNCHANGED) {
            sender.sendMessage(PREFIX + "§eEl grupo ya estaba " + (open ? "abierto" : "cerrado") + ".");
        } else {
            sender.sendMessage(PREFIX + "§aGrupo " + args[4] + (open
                ? " abierto: los jugadores pueden elegirlo con /anotar."
                : " cerrado: solo lo asigna el staff o repartir."));
        }
    }

    private void capacity(CommandSender sender, ToffyTournamentService tournaments, ToffyTournament tournament,
                          String[] args) {
        if (!ToffyTournamentCommand.needs(sender, args, 6, "/toffy torneo grupo <torneo> cupo <grupo> <n|sin>")) {
            return;
        }
        try {
            Integer capacity = args[5].equalsIgnoreCase("sin") ? null : Integer.parseInt(args[5]);
            if (!tournaments.setGroupCapacity(tournament.key(), args[4], capacity)) {
                sender.sendMessage("§cNo existe el grupo " + args[4] + " en " + tournament.displayName() + ".");
                return;
            }
            sender.sendMessage(PREFIX + "§aCupo de " + args[4] + ": " + (capacity == null ? "sin cupo" : capacity) + ".");
        } catch (NumberFormatException invalid) {
            sender.sendMessage("§cEl cupo tiene que ser un numero o sin.");
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("§c" + invalid.getMessage());
        }
    }

    /** {@code nivel <grupo> <min|*> <max|*>} and {@code nivel <grupo> sin}. */
    private void level(CommandSender sender, ToffyTournamentService tournaments, ToffyTournament tournament,
                       String[] args) {
        String usage = "/toffy torneo grupo <torneo> nivel <grupo> <min|*> <max|*> | ... nivel <grupo> sin";
        if (!ToffyTournamentCommand.needs(sender, args, 5, usage)) {
            return;
        }
        String groupName = args[4];
        try {
            if (args.length > 5 && args[5].equalsIgnoreCase("sin")) {
                if (!tournaments.setGroupLevelRange(tournament.key(), groupName, null, null)) {
                    sender.sendMessage("§cNo existe el grupo " + groupName + " en " + tournament.displayName() + ".");
                    return;
                }
                sender.sendMessage(PREFIX + "§aGrupo " + groupName + " sin rango de nivel.");
                return;
            }
            if (args.length < 7 || !ToffyTournamentCommand.validBound(args[5]) || !ToffyTournamentCommand.validBound(args[6])) {
                sender.sendMessage("§cUso: " + usage);
                return;
            }
            Double min = ToffyTournamentCommand.parseBound(args[5]);
            Double max = ToffyTournamentCommand.parseBound(args[6]);
            if ((min != null && min > module.maximum()) || (max != null && max > module.maximum())) {
                sender.sendMessage("§cEl nivel tiene que estar entre 0 y " + ToffyModule.coordinate(module.maximum()) + ".");
                return;
            }
            if (!tournaments.setGroupLevelRange(tournament.key(), groupName, min, max)) {
                sender.sendMessage("§cNo existe el grupo " + groupName + " en " + tournament.displayName() + ".");
                return;
            }
            sender.sendMessage(PREFIX + "§aGrupo " + groupName + ": nivel " + ToffyGroupRules.rangeText(min, max) + ".");
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("§c" + invalid.getMessage());
        }
    }

    /** {@code grupo ... lugar <grupo> [agregar|borrar]}: same semantics as the place of the tournament. */
    private void place(CommandSender sender, ToffyTournamentService tournaments, ToffyTournament tournament,
                       String[] args) {
        if (!ToffyTournamentCommand.needs(sender, args, 5, "/toffy torneo grupo <torneo> lugar <grupo> [agregar|borrar]")) {
            return;
        }
        String groupName = args[4];
        Optional<ToffyGroup> group = tournament.group(safeKey(groupName));
        if (group.isEmpty()) {
            sender.sendMessage("§cNo existe el grupo " + groupName + " en " + tournament.displayName() + ".");
            return;
        }
        String mode = args.length > 5 ? args[5].toLowerCase(Locale.ROOT) : "";
        if (mode.equals("borrar")) {
            sender.sendMessage(tournaments.clearGroupPlaces(tournament.key(), groupName)
                ? PREFIX + "§aLugares del grupo " + group.get().displayName() + " borrados."
                : "§eEl grupo " + group.get().displayName() + " no tenia lugar.");
            return;
        }
        if (!mode.isEmpty() && !mode.equals("agregar")) {
            sender.sendMessage("§cUso: /toffy torneo grupo <torneo> lugar <grupo> [agregar|borrar]");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDesde consola no hay lugar: ubicate en el juego y usa el comando de nuevo.");
            return;
        }
        ToffyStoredLocation saved = ToffyModule.stored(player.getLocation());
        if (mode.equals("agregar")) {
            tournaments.addGroupPlace(tournament.key(), groupName, saved);
            sender.sendMessage(PREFIX + "§aLugar agregado al grupo " + group.get().displayName() + ": ahora son §e"
                + group.get().places().size() + "§a (" + ToffyTournamentCommand.placeText(saved) + ").");
            return;
        }
        tournaments.setGroupPlace(tournament.key(), groupName, saved);
        sender.sendMessage(PREFIX + "§aLugar del grupo " + group.get().displayName() + " guardado: §f"
            + ToffyTournamentCommand.placeText(saved) + "§a (reemplaza los anteriores).");
    }

    /** {@code asignar <grupo> <jugador> [forzar]}: signs the player up to the tournament if needed, then places it. */
    private void assign(CommandSender sender, ToffyTournamentService tournaments, ToffyTournament tournament,
                        String[] args) {
        if (!ToffyTournamentCommand.needs(sender, args, 6, "/toffy torneo grupo <torneo> asignar <grupo> <jugador> [forzar]")) {
            return;
        }
        boolean force = args.length > 6 && args[args.length - 1].equalsIgnoreCase(FORCE);
        String playerName = args[5];
        Player online = module.server().getPlayer(playerName);
        UUID id = online != null ? online.getUniqueId() : participantId(tournament, playerName);
        if (id == null) {
            sender.sendMessage("§cNo encuentro a " + playerName + ": tiene que estar conectado o ya anotado en "
                + tournament.displayName() + ".");
            return;
        }
        String name = online != null ? online.getName() : tournament.participants().get(id);
        Double level = online == null ? null : module.realLevel(online);
        ToffyTournamentService.Placement placement = tournaments.placeInGroup(tournament.key(), args[4], id, name,
            level, force ? ToffyTournamentService.GroupMode.FORCED : ToffyTournamentService.GroupMode.STAFF);
        ToffyTournamentService.GroupResult result = placement.result();
        String groupName = tournaments.group(tournament.key(), args[4]).map(ToffyGroup::displayName).orElse(args[4]);
        if (result == ToffyTournamentService.GroupResult.ASSIGNED || result == ToffyTournamentService.GroupResult.MOVED) {
            module.groupChanged(tournament.key(), List.of(id));
        }
        switch (result) {
            case ASSIGNED, MOVED -> {
                sender.sendMessage(PREFIX + "§a" + name + (result == ToffyTournamentService.GroupResult.MOVED
                    ? " paso al grupo " : " entro al grupo ") + groupName + "§a." + (placement.registered()
                    ? " Tambien quedo anotado al torneo." : ""));
                if (placement.closedByCapacity()) {
                    module.announceClosed(tournament);
                }
                if (online != null) {
                    module.send(online, ToffyMessages.GRUPO_ASIGNADO, tournament.key(), Map.of(
                        "jugador", name, "torneo", tournament.displayName(), "grupo", groupName));
                }
            }
            case ALREADY_IN_GROUP -> sender.sendMessage("§e" + name + " ya estaba en el grupo " + groupName + ".");
            case UNKNOWN_GROUP -> sender.sendMessage("§cNo existe el grupo " + args[4] + " en " + tournament.displayName() + ".");
            case GROUP_FULL -> sender.sendMessage("§cEl grupo " + groupName + " esta lleno. Agrega forzar al final para ignorar el cupo.");
            case GROUP_LEVEL_OUT_OF_RANGE, TOURNAMENT_LEVEL_OUT -> sender.sendMessage("§c" + name
                + " tiene nivel " + ToffyModule.coordinate(level == null ? 0 : level) + ", fuera del rango. Agrega forzar al final para igual.");
            case LEVEL_UNKNOWN -> sender.sendMessage("§cNo se puede leer el nivel de " + name
                + " (esta desconectado) y el torneo o el grupo tiene rango. Agrega forzar al final para igual.");
            case GROUP_CLOSED -> sender.sendMessage("§cEl grupo " + groupName + " no esta abierto.");
            case TOURNAMENT_CLOSED -> sender.sendMessage("§cLas inscripciones de " + tournament.displayName()
                + " estan cerradas. Agrega forzar al final para anotarlo igual.");
            case UNKNOWN_TOURNAMENT -> sender.sendMessage("§cNo existe el torneo " + tournament.displayName() + ".");
        }
    }

    private void leave(CommandSender sender, ToffyTournamentService tournaments, ToffyTournament tournament,
                       String[] args) {
        if (!ToffyTournamentCommand.needs(sender, args, 5, "/toffy torneo grupo <torneo> quitar <jugador>")) {
            return;
        }
        UUID id = participantId(tournament, args[4]);
        if (id == null) {
            sender.sendMessage("§c" + args[4] + " no esta anotado en " + tournament.displayName() + ".");
            return;
        }
        boolean left = tournaments.leaveGroup(tournament.key(), id);
        if (left) {
            module.groupChanged(tournament.key(), List.of(id));
        }
        sender.sendMessage(left
            ? PREFIX + "§a" + args[4] + " salio de su grupo; sigue anotado al torneo."
            : "§e" + args[4] + " no estaba en ningun grupo.");
    }

    /** {@code repartir <parejo|nivel> [todos]}: the plugin places the players of the tournament. */
    private void repartir(CommandSender sender, ToffyTournamentService tournaments, ToffyTournament tournament,
                          String[] args) {
        if (!ToffyTournamentCommand.needs(sender, args, 5, "/toffy torneo grupo <torneo> repartir <parejo|nivel> [todos]")) {
            return;
        }
        String mode = args[4].toLowerCase(Locale.ROOT);
        if (!mode.equals("parejo") && !mode.equals("nivel")) {
            sender.sendMessage("§cUso: /toffy torneo grupo <torneo> repartir <parejo|nivel> [todos]");
            return;
        }
        if (tournament.groups().isEmpty()) {
            sender.sendMessage("§cEl torneo " + tournament.displayName() + " no tiene grupos para repartir.");
            return;
        }
        boolean all = args.length > 5 && args[5].equalsIgnoreCase("todos");
        Map<UUID, Double> levels = new HashMap<>();
        int offline = 0;
        for (UUID id : tournament.participants().keySet()) {
            Player online = module.server().getPlayer(id);
            if (online == null) {
                offline++;
            } else {
                levels.put(id, module.realLevel(online));
            }
        }
        ToffyTournamentService.AutoResult result = tournaments.autoAssign(tournament.key(), mode.equals("parejo"),
            all, levels, new Random());
        sender.sendMessage(PREFIX + "§aRepartidos (" + mode + (all ? ", todos" : "") + "): §e" + result.assigned().size()
            + "§a. Sin grupo: §e" + result.ungrouped() + "§a" + (offline > 0
            ? " (" + offline + " desconectados no se reparten)" : "")
            + (result.outOfTournamentRange() > 0 ? " (" + result.outOfTournamentRange()
            + " fuera del rango del torneo)" : "") + ".");
        module.groupChanged(tournament.key(), tournament.participants().keySet());
        for (Map.Entry<UUID, String> entry : result.assigned().entrySet()) {
            Player online = module.server().getPlayer(entry.getKey());
            if (online != null) {
                ToffyGroup group = tournament.group(entry.getValue()).orElseThrow();
                module.send(online, ToffyMessages.GRUPO_ASIGNADO, tournament.key(), Map.of(
                    "jugador", online.getName(), "torneo", tournament.displayName(), "grupo", group.displayName()));
            }
        }
    }

    private void mandatory(CommandSender sender, ToffyTournamentService tournaments, ToffyTournament tournament,
                           String[] args) {
        if (!ToffyTournamentCommand.needs(sender, args, 5, "/toffy torneo grupo <torneo> obligatorio <si|no>")) {
            return;
        }
        String answer = args[4].toLowerCase(Locale.ROOT);
        if (!answer.equals("si") && !answer.equals("no")) {
            sender.sendMessage("§cUso: /toffy torneo grupo <torneo> obligatorio <si|no>");
            return;
        }
        tournaments.setGroupMandatory(tournament.key(), answer.equals("si"));
        sender.sendMessage(PREFIX + "§aGrupo obligatorio de " + tournament.displayName() + ": "
            + (answer.equals("si") ? "si" : "no") + ".");
        if (answer.equals("si") && ToffyGroupRules.openGroups(tournament).isEmpty()) {
            sender.sendMessage("§eNo hay grupos abiertos: /anotar no va a listar ninguno hasta que abras uno.");
        }
    }

    /** Tab completion: {@code args} starts with "torneo", "grupo", the tournament name. */
    List<String> complete(ToffyTournamentService tournaments, String[] args) {
        List<String> options = new ArrayList<>();
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        Optional<ToffyTournament> found = tournaments == null || args.length <= 2 ? Optional.empty()
            : tournaments.find(args[2]);
        if (found.isEmpty()) {
            return List.of();
        }
        ToffyTournament tournament = found.get();
        String sub = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "";
        int index = args.length;
        if (index == 4) {
            options.addAll(SUBCOMMANDS);
        } else if (index == 5) {
            switch (sub) {
                case "borrar", "ver", "abrir", "cerrar", "cupo", "nivel", "lugar", "asignar" ->
                    tournament.groups().forEach(group -> options.add(group.key()));
                case "crear" -> { }
                case "quitar" -> tournament.participants().values().forEach(options::add);
                case "repartir" -> options.addAll(List.of("parejo", "nivel"));
                case "obligatorio" -> options.addAll(List.of("si", "no"));
                default -> { }
            }
        } else if (index == 6) {
            switch (sub) {
                case "crear" -> options.add("abierto");
                case "cupo" -> options.add("sin");
                case "nivel" -> {
                    options.add("sin");
                    options.add("*");
                }
                case "lugar" -> options.addAll(List.of("agregar", "borrar"));
                case "asignar" -> tournament.participants().values().forEach(options::add);
                case "repartir" -> options.add("todos");
                default -> { }
            }
        } else if (index == 7 && sub.equals("nivel")) {
            options.add("*");
        } else if (index == 7 && sub.equals("asignar")) {
            options.add(FORCE);
        }
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(current)).toList();
    }

    private static UUID participantId(ToffyTournament tournament, String playerName) {
        return tournament.participants().entrySet().stream()
            .filter(entry -> entry.getValue().equalsIgnoreCase(playerName))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    private static String safeKey(String name) {
        try {
            return ToffyGroup.keyOf(name);
        } catch (IllegalArgumentException invalid) {
            return "";
        }
    }
}
