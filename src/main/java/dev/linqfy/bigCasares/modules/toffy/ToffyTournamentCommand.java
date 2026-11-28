package dev.linqfy.bigCasares.modules.toffy;

import org.bukkit.Location;
import org.bukkit.World;
import dev.linqfy.bigCasares.modules.toffy.eventos.RewardItem;
import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEventSettings;
import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEventType;
import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEvents;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.Set;
import java.util.UUID;

/**
 * Admin subcommands of {@code /toffy}: {@code torneo ...}, {@code tp ...}, {@code evento ...} and {@code programados}.
 */
final class ToffyTournamentCommand {
    static final String PERMISSION = "bigcasares.toffy.torneo.admin";
    private static final String PREFIX = "§6Toffy §8» §7";
    private static final List<String> TORNEO_SUBCOMMANDS = List.of("crear", "lista", "ver", "abrir", "cerrar",
        "lugar", "sacar", "mensaje", "premio", "borrar", "nivel", "cupo", "minimo", "cierre", "grupo");
    private static final List<String> TP_SUBCOMMANDS = List.of("concursantes", "devolver", "programar", "programados");
    private static final List<String> EVENT_SUBCOMMANDS = List.of("iniciar", "detener", "tipos", "estado", "programar");
    private static final String EVENT_USAGE =
        "§cUso: /toffy evento <iniciar <tipo> <torneo> [segundos] [forzar]|detener|tipos|estado|programar ...>";
    private static final String FORCE = "forzar";
    private static final String REWARD_USAGE =
        "/toffy torneo premio <torneo> ver|item <id[:cant]>|comando <comando>|quitar <n>|borrar";

    private final ToffyModule module;
    private final ToffyGroupCommand groups;

    ToffyTournamentCommand(ToffyModule module) {
        this.module = module;
        this.groups = new ToffyGroupCommand(module);
    }

    void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION) && !sender.hasPermission(ToffyCommand.ADMIN_PERMISSION)) {
            sender.sendMessage("§cNo tenes permiso para usar este comando.");
            return;
        }
        ToffyTournamentService tournaments = module.tournaments();
        if (tournaments == null) {
            sender.sendMessage("§cToffy Complementaries no esta disponible.");
            return;
        }
        guarded(sender, args, () -> {
            if (args[0].equalsIgnoreCase("tp")) {
                teleport(sender, tournaments, args);
            } else {
                tournament(sender, tournaments, args);
            }
        });
    }

    /** Runs a dispatch: a save failure is told to the sender, anything else is logged and reported as internal. */
    private void guarded(CommandSender sender, String[] args, Runnable dispatch) {
        try {
            dispatch.run();
        } catch (UncheckedIOException failure) {
            sender.sendMessage("§cNo se pudo guardar torneos.yml: " + failure.getCause().getMessage());
        } catch (RuntimeException failure) {
            module.logSevere("Error en /toffy " + String.join(" ", args), failure);
            sender.sendMessage("§cError interno, mira la consola.");
        }
    }

    private void tournament(CommandSender sender, ToffyTournamentService tournaments, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "crear" -> create(sender, tournaments, args);
            case "lista" -> list(sender, tournaments);
            case "ver" -> {
                if (needs(sender, args, 3, "/toffy torneo ver <nombre>")) {
                    view(sender, tournaments, args[2]);
                }
            }
            case "abrir" -> {
                if (needs(sender, args, 3, "/toffy torneo abrir <nombre>")) {
                    open(sender, tournaments, args[2]);
                }
            }
            case "cerrar" -> {
                if (needs(sender, args, 3, "/toffy torneo cerrar <nombre> [mensaje...]")) {
                    close(sender, tournaments, args);
                }
            }
            case "lugar" -> {
                if (needs(sender, args, 3, "/toffy torneo lugar <torneo> [agregar|borrar]")) {
                    place(sender, tournaments, args);
                }
            }
            case "sacar" -> {
                if (needs(sender, args, 4, "/toffy torneo sacar <nombre> <jugador>")) {
                    remove(sender, tournaments, args[2], args[3]);
                }
            }
            case "mensaje" -> {
                if (needs(sender, args, 5, "/toffy torneo mensaje <nombre> <caso> <texto...|-|reset>")) {
                    message(sender, tournaments, args);
                }
            }
            case "premio" -> {
                if (needs(sender, args, 4, REWARD_USAGE)) {
                    rewards(sender, tournaments, args);
                }
            }
            case "borrar" -> {
                if (needs(sender, args, 3, "/toffy torneo borrar <nombre> [forzar]")) {
                    int saved = tournaments.savedPlaces(args[2]);
                    boolean force = args.length > 3 && args[3].equalsIgnoreCase(FORCE);
                    if (saved > 0 && !force) {
                        sender.sendMessage("§c" + saved + " jugador(es) tienen un lugar guardado en " + args[2]
                            + ". Usa /toffy tp devolver " + args[2] + " primero, o /toffy torneo borrar "
                            + args[2] + " forzar para perder esos lugares.");
                        return;
                    }
                    sender.sendMessage(tournaments.delete(args[2])
                        ? PREFIX + "§aTorneo " + args[2] + " borrado."
                        : "§cNo existe el torneo " + args[2] + ".");
                }
            }
            case "nivel" -> {
                if (needs(sender, args, 4, "/toffy torneo nivel <torneo> <min|*> <max|*> | /toffy torneo nivel <torneo> sin")) {
                    level(sender, tournaments, args);
                }
            }
            case "cupo" -> {
                if (needs(sender, args, 4, "/toffy torneo cupo <torneo> <n|sin>")) {
                    capacity(sender, tournaments, args);
                }
            }
            case "minimo" -> {
                if (needs(sender, args, 4, "/toffy torneo minimo <torneo> <n|sin>")) {
                    minimum(sender, tournaments, args);
                }
            }
            case "cierre" -> {
                if (needs(sender, args, 4, "/toffy torneo cierre <torneo> <HH:mm> [dd/MM] | /toffy torneo cierre <torneo> cancelar")) {
                    closeAt(sender, tournaments, args);
                }
            }
            case "grupo" -> {
                if (needs(sender, args, 4, "/toffy torneo grupo <torneo> <subcomando> (mira /toffy ayuda)")) {
                    groups.handle(sender, tournaments, args);
                }
            }
            default -> sender.sendMessage("§cUso: /toffy torneo <crear|lista|ver|abrir|cerrar|lugar|sacar|mensaje|"
                + "borrar|nivel|cupo|minimo|cierre|grupo> (mira /toffy ayuda)");
        }
    }

    /**
     * {@code /toffy evento ...}: staff start, stop, schedule and inspect events. An event always runs on a tournament's
     * sign-up list.
     */
    void event(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ToffyEvents.ADMIN_PERMISSION) && !sender.hasPermission(ToffyCommand.ADMIN_PERMISSION)) {
            sender.sendMessage("§cNo tenes permiso para usar este comando.");
            return;
        }
        ToffyEvents events = module.events();
        if (events == null) {
            sender.sendMessage(ToffyEvents.UNAVAILABLE_TEXT);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(EVENT_USAGE);
            return;
        }
        guarded(sender, args, () -> dispatchEvent(sender, events, args));
    }

    private void dispatchEvent(CommandSender sender, ToffyEvents events, String[] args) {
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "iniciar" -> start(sender, events, args);
            case "programar" -> scheduleEvent(sender, args);
            case "detener" -> events.stop(sender);
            case "tipos" -> {
                for (ToffyEventType type : ToffyEventType.values()) {
                    sender.sendMessage("§e" + type.id() + " §7— " + type.displayName()
                        + (type.arena() ? " §8(arena, inventario de evento)" : " §8(mundo normal, keep inventory)"));
                }
            }
            case "estado" -> events.status().forEach(sender::sendMessage);
            default -> sender.sendMessage("§cSubcomando desconocido. Mira /toffy ayuda.");
        }
    }

    private void start(CommandSender sender, ToffyEvents events, String[] raw) {
        Extracted extracted = extractGroup(raw, 4);
        boolean forced = forced(extracted.args());
        String[] args = forced ? Arrays.copyOf(extracted.args(), extracted.args().length - 1) : extracted.args();
        if (args.length < 4 || extracted.dangling()) {
            sender.sendMessage("§cUso: /toffy evento iniciar <tipo> <torneo> [grupo <grupo>] [segundos de espera 5-600] [forzar]");
            return;
        }
        Optional<ToffyEventType> type = ToffyEventType.parse(args[2]);
        if (type.isEmpty()) {
            sender.sendMessage("§cTipo desconocido. Usa /toffy evento tipos.");
            return;
        }
        ToffyTournamentService tournaments = module.tournaments();
        Optional<ToffyTournament> tournament = tournaments == null ? Optional.empty() : tournaments.find(args[3]);
        if (tournament.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + args[3] + ".");
            return;
        }
        Integer lobby = null;
        if (args.length >= 5) {
            lobby = lobbySeconds(args[4]);
            if (lobby == null) {
                sender.sendMessage("§cLa espera tiene que estar entre 5 y 600 segundos.");
                return;
            }
        }
        String groupKey = null;
        if (extracted.group() != null) {
            Optional<ToffyGroup> group = tournaments.group(tournament.get().key(), extracted.group());
            if (group.isEmpty()) {
                sender.sendMessage("§cNo existe el grupo " + extracted.group() + " en " + tournament.get().displayName() + ".");
                return;
            }
            groupKey = group.get().key();
        }
        if (!forced && belowMinimum(sender, tournament.get())) {
            return;
        }
        events.start(sender, type.get(), tournament.get().key(), groupKey, lobby);
    }

    /** {@code /toffy evento programar <tipo> <torneo> <HH:mm> [dd/MM] [segundos]} and {@code <torneo> cancelar}. */
    private void scheduleEvent(CommandSender sender, String[] raw) {
        Extracted extracted = extractGroup(raw, 5);
        String[] args = extracted.args();
        if (extracted.dangling()) {
            sender.sendMessage("§cUso: /toffy evento programar <tipo> <torneo> <HH:mm> [dd/MM] [segundos] [grupo <grupo>]");
            return;
        }
        if (!needs(sender, args, 4, "/toffy evento programar <tipo> <torneo> <HH:mm> [dd/MM] [segundos] [grupo <grupo>] | /toffy evento programar <torneo> cancelar")) {
            return;
        }
        ToffyTournamentService tournaments = module.tournaments();
        if (tournaments == null) {
            sender.sendMessage("§cToffy Complementaries no esta disponible.");
            return;
        }
        if (args[3].equalsIgnoreCase("cancelar")) {
            Optional<ToffyTournament> found = tournaments.find(args[2]);
            if (found.isEmpty()) {
                sender.sendMessage("§cNo existe el torneo " + args[2] + ".");
                return;
            }
            sender.sendMessage(tournaments.cancelEvent(found.get().key())
                ? PREFIX + "§aEvento programado de " + found.get().displayName() + " cancelado."
                : "§eEl torneo " + found.get().displayName() + " no tenia un evento programado.");
            return;
        }
        if (!needs(sender, args, 5, "/toffy evento programar <tipo> <torneo> <HH:mm> [dd/MM] [segundos]")) {
            return;
        }
        Optional<ToffyEventType> type = ToffyEventType.parse(args[2]);
        if (type.isEmpty()) {
            sender.sendMessage("§cTipo desconocido. Usa /toffy evento tipos.");
            return;
        }
        Optional<ToffyTournament> found = tournaments.find(args[3]);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + args[3] + ".");
            return;
        }
        int next = 5;
        String day = null;
        if (args.length > next && args[next].contains("/")) {
            day = args[next];
            next++;
        }
        Integer lobby = null;
        if (args.length > next) {
            lobby = lobbySeconds(args[next]);
            if (lobby == null) {
                sender.sendMessage("§cLa espera tiene que estar entre 5 y 600 segundos.");
                return;
            }
        }
        ZoneId zone = zone();
        long at;
        try {
            at = ToffyTournamentTime.resolve(args[4], day, tournaments.now(), zone, timeFormat(), dateFormat());
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("§c" + invalid.getMessage());
            return;
        }
        ToffyTournament tournament = found.get();
        String groupKey = null;
        if (extracted.group() != null) {
            Optional<ToffyGroup> group = tournaments.group(tournament.key(), extracted.group());
            if (group.isEmpty()) {
                sender.sendMessage("§cNo existe el grupo " + extracted.group() + " en " + tournament.displayName() + ".");
                return;
            }
            groupKey = group.get().key();
        }
        Optional<Long> replaced = tournaments.scheduleEvent(tournament.key(), type.get().id(), lobby, at, groupKey);
        replaced.ifPresent(old -> sender.sendMessage(PREFIX + "§eSe reemplazo el evento programado para "
            + when(old) + "."));
        sender.sendMessage(PREFIX + "§aEvento " + type.get().displayName() + " de " + tournament.displayName()
            + (groupKey == null ? "" : " (grupo " + tournament.group(groupKey).orElseThrow().displayName() + ")")
            + " programado para §e" + when(at) + "§a.");
    }

    private static Integer lobbySeconds(String raw) {
        try {
            int seconds = Integer.parseInt(raw);
            return seconds >= 5 && seconds <= 600 ? seconds : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /** Tab completion for {@code /toffy evento ...}; {@code args[0]} is "evento". */
    List<String> completeEvent(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ToffyEvents.ADMIN_PERMISSION) && !sender.hasPermission(ToffyCommand.ADMIN_PERMISSION)) {
            return List.of();
        }
        ToffyTournamentService tournaments = module.tournaments();
        List<String> options = new ArrayList<>();
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 2) {
            options.addAll(EVENT_SUBCOMMANDS);
        } else if (args.length == 3 && sub.equals("iniciar")) {
            for (ToffyEventType type : ToffyEventType.values()) {
                options.add(type.id());
            }
        } else if (args.length == 4 && sub.equals("iniciar") && tournaments != null) {
            options.addAll(tournaments.keys());
        } else if (args.length == 5 && sub.equals("iniciar")) {
            options.add("grupo");
            options.add(FORCE);
        } else if (args.length == 6 && sub.equals("iniciar") && args[4].equalsIgnoreCase("grupo") && tournaments != null) {
            tournaments.find(args[3]).ifPresent(tournament -> tournament.groups().forEach(group -> options.add(group.key())));
        } else if (args.length >= 6 && sub.equals("programar") && tournaments != null) {
            if (args.length >= 7 && args[args.length - 2].equalsIgnoreCase("grupo")) {
                tournaments.find(args[3]).ifPresent(tournament -> tournament.groups().forEach(group -> options.add(group.key())));
            } else {
                options.add("grupo");
            }
        } else if (args.length == 3 && sub.equals("programar")) {
            for (ToffyEventType type : ToffyEventType.values()) {
                options.add(type.id());
            }
            if (tournaments != null) {
                options.addAll(tournaments.keys());
            }
        } else if (args.length == 4 && sub.equals("programar")) {
            if (tournaments != null) {
                options.addAll(tournaments.keys());
            }
            options.add("cancelar");
            options.add(nextFiveMinutes(zone()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }

    static boolean needs(CommandSender sender, String[] args, int length, String usage) {
        if (args.length >= length) {
            return true;
        }
        sender.sendMessage("§cUso: " + usage);
        return false;
    }

    /**
     * Arguments without the optional {@code grupo <nombre>} pair that starts at {@code from}. {@code group} is null when
     * there is no such pair, the empty string when "grupo" is the last token ({@code dangling}).
     */
    record Extracted(String[] args, String group, boolean dangling) {
    }

    static Extracted extractGroup(String[] args, int from) {
        for (int index = from; index < args.length; index++) {
            if (args[index].equalsIgnoreCase("grupo")) {
                if (index + 1 >= args.length) {
                    return new Extracted(Arrays.copyOf(args, index), "", true);
                }
                String[] rest = new String[args.length - 2];
                System.arraycopy(args, 0, rest, 0, index);
                System.arraycopy(args, index + 2, rest, index, args.length - index - 2);
                return new Extracted(rest, args[index + 1], false);
            }
        }
        return new Extracted(args, null, false);
    }

    /**
     * Whether the last argument is {@code forzar}. It never counts as an argument of its own: it needs at least one
     * token before it, so {@code tp concursantes forzar} keeps "forzar" as a tournament name (and fails as unknown).
     */
    static boolean forced(String[] args) {
        return args.length > 3 && args[args.length - 1].equalsIgnoreCase(FORCE);
    }

    private void create(CommandSender sender, ToffyTournamentService tournaments, String[] args) {
        if (!needs(sender, args, 3, "/toffy torneo crear <nombre>")) {
            return;
        }
        try {
            ToffyTournament created = tournaments.create(args[2]);
            sender.sendMessage(PREFIX + "§aTorneo §e" + created.displayName()
                + "§a creado. Los jugadores se anotan con §e/anotar " + created.key() + "§a.");
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            sender.sendMessage("§c" + invalid.getMessage());
        }
    }

    private void close(CommandSender sender, ToffyTournamentService tournaments, String[] args) {
        ToffyTournamentService.Change change = tournaments.close(args[2]);
        if (change == ToffyTournamentService.Change.UNKNOWN) {
            sender.sendMessage("§cNo existe el torneo " + args[2] + ".");
            return;
        }
        if (change == ToffyTournamentService.Change.UNCHANGED) {
            sender.sendMessage(PREFIX + "§eLas inscripciones ya estaban cerradas.");
            return;
        }
        ToffyTournament tournament = tournaments.find(args[2]).orElseThrow();
        Map<String, String> placeholders = placeholders(tournament, null);
        String announcement = args.length > 3
            ? ToffyMessages.format(String.join(" ", Arrays.copyOfRange(args, 3, args.length)), placeholders)
            : module.message(ToffyMessages.CIERRE, tournament.key(), placeholders);
        sender.sendMessage(PREFIX + "§aInscripciones del torneo §e" + tournament.displayName() + "§a cerradas.");
        if (announcement != null) {
            module.server().broadcastMessage(announcement);
        }
    }

    private void open(CommandSender sender, ToffyTournamentService tournaments, String name) {
        ToffyTournamentService.Change change = tournaments.open(name);
        if (change == ToffyTournamentService.Change.UNKNOWN) {
            sender.sendMessage("§cNo existe el torneo " + name + ".");
            return;
        }
        if (change == ToffyTournamentService.Change.UNCHANGED) {
            sender.sendMessage(PREFIX + "§eLas inscripciones ya estaban abiertas.");
            return;
        }
        ToffyTournament tournament = tournaments.find(name).orElseThrow();
        sender.sendMessage(PREFIX + "§aInscripciones del torneo §e" + tournament.displayName() + "§a abiertas.");
        String announcement = module.message(ToffyMessages.APERTURA, tournament.key(), placeholders(tournament, null));
        if (announcement != null) {
            module.server().broadcastMessage(announcement);
        }
    }

    private void list(CommandSender sender, ToffyTournamentService tournaments) {
        List<ToffyTournament> all = tournaments.list();
        if (all.isEmpty()) {
            sender.sendMessage(PREFIX + "No hay torneos. Crea uno con /toffy torneo crear <nombre>.");
            return;
        }
        sender.sendMessage(PREFIX + "Torneos (" + all.size() + "):");
        for (ToffyTournament tournament : all) {
            sender.sendMessage("§7  §f" + tournament.displayName() + " §8- "
                + (tournament.open() ? "§aabierto" : "§cinscripcion cerrada")
                + " §8- §e" + tournament.participants().size() + "§7 participantes"
                + (tournament.capacity() == null ? "" : " §8/ §e" + tournament.capacity())
                + " §8- nivel " + rangeText(tournament) + scheduleSuffix(tournament, module.tournamentTimes()));
        }
    }

    private void view(CommandSender sender, ToffyTournamentService tournaments, String name) {
        Optional<ToffyTournament> found = tournaments.find(name);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + name + ".");
            return;
        }
        ToffyTournament tournament = found.get();
        ZoneId zone = zone();
        sender.sendMessage(PREFIX + "§f" + tournament.displayName() + "§7 - "
            + (tournament.open() ? "§aabierto" : "§cinscripcion cerrada") + "§7 - §e"
            + tournament.participants().size() + "§7 participantes:");
        sender.sendMessage("§7  Nivel: §f" + rangeText(tournament) + " §8(de 0 a " + ToffyModule.coordinate(
            module.maximum()) + ")");
        sender.sendMessage("§7  Cupo: §f" + (tournament.capacity() == null ? "sin cupo" : tournament.capacity()));
        sender.sendMessage("§7  Minimo para programados: §f" + (tournament.minPlayers() == null
            ? "sin minimo" : tournament.minPlayers()));
        if (tournament.places().isEmpty()) {
            sender.sendMessage("§7  Lugares: §8ninguno (usa /toffy torneo lugar " + tournament.key() + ")");
        } else {
            sender.sendMessage("§7  Lugares: §f" + tournament.places().size());
            for (ToffyStoredLocation place : tournament.places()) {
                sender.sendMessage("§7    - §f" + placeText(place));
            }
        }
        sender.sendMessage("§7  Teletransporte: " + (tournament.scheduledAt() == null ? "§8no programado"
            : "§bprogramado " + when(tournament.scheduledAt())));
        sender.sendMessage("§7  Cierre de inscripciones: " + (tournament.closeAt() == null ? "§8no programado"
            : "§bprogramado " + when(tournament.closeAt())));
        sender.sendMessage("§7  Grupo obligatorio: " + (tournament.groupMandatory() ? "§asi" : "§8no"));
        sender.sendMessage("§7  Grupos: " + (tournament.groups().isEmpty() ? "§8ninguno (usa /toffy torneo grupo "
            + tournament.key() + " crear <grupo>)" : "§f" + tournament.groups().size()));
        for (ToffyGroup group : tournament.groups()) {
            sender.sendMessage("§7    - " + ToffyGroupCommand.summary(group));
        }
        sender.sendMessage("§7  Evento: " + (tournament.eventAt() == null ? "§8no programado"
            : "§b" + tournament.eventType() + " " + when(tournament.eventAt())
            + (tournament.eventLobby() == null ? "" : " §7(espera " + tournament.eventLobby() + " s)")));
        int position = 1;
        for (Map.Entry<UUID, String> entry : tournament.participants().entrySet()) {
            Player online = module.server().getPlayer(entry.getKey());
            sender.sendMessage("§7  " + position++ + ". §f" + (online != null ? online.getName() : entry.getValue())
                + (online != null ? " §a(online)" : " §8(offline)")
                + tournament.groupOf(entry.getKey()).map(key -> " §b[grupo " + key + "]").orElse("")
                + (tournament.returns().containsKey(entry.getKey()) ? " §6[lugar guardado]" : ""));
        }
        if (!tournament.messages().isEmpty()) {
            sender.sendMessage("§7  Mensajes propios: §f" + String.join(", ", tournament.messages().keySet()));
        }
    }

    /** Short text of the level range: "sin rango", "40-120", "*-120", "40-*". */
    static String rangeText(ToffyTournament tournament) {
        return ToffyGroupRules.rangeText(tournament.minLevel(), tournament.maxLevel());
    }

    /** Suffix for {@code torneo lista} and {@code anotar}: every schedule of the tournament. */
    static String scheduleSuffix(ToffyTournament tournament, ToffyEventSettings.Tournaments times) {
        StringBuilder text = new StringBuilder();
        if (tournament.scheduledAt() != null) {
            text.append(" §8- §bteletransporte ").append(when(tournament.scheduledAt(), times));
        }
        if (tournament.closeAt() != null) {
            text.append(" §8- §bcierre ").append(when(tournament.closeAt(), times));
        }
        if (tournament.eventAt() != null) {
            text.append(" §8- §bevento ").append(tournament.eventType()).append(" ")
                .append(when(tournament.eventAt(), times));
        }
        return text.toString();
    }

    private void remove(CommandSender sender, ToffyTournamentService tournaments, String name, String playerName) {
        Optional<ToffyTournament> found = tournaments.find(name);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + name + ".");
            return;
        }
        UUID target = null;
        for (Map.Entry<UUID, String> entry : found.get().participants().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(playerName)) {
                target = entry.getKey();
            }
        }
        if (target == null) {
            sender.sendMessage("§c" + playerName + " no esta anotado en " + found.get().displayName() + ".");
            return;
        }
        tournaments.removeParticipant(name, target);
        ToffyEvents events = module.events();
        if (events != null) {
            events.withdraw(target, found.get().key());
        }
        sender.sendMessage(PREFIX + "§a" + playerName + " fue sacado del torneo " + found.get().displayName() + ".");
    }

    /** {@code /toffy torneo lugar <torneo> [agregar|borrar]}: replaces all places, adds one, or clears them. */
    private void place(CommandSender sender, ToffyTournamentService tournaments, String[] args) {
        Optional<ToffyTournament> found = tournaments.find(args[2]);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + args[2] + ".");
            return;
        }
        ToffyTournament tournament = found.get();
        String mode = args.length > 3 ? args[3].toLowerCase(Locale.ROOT) : "";
        if (mode.equals("borrar")) {
            if (tournament.places().isEmpty()) {
                sender.sendMessage("§eEl torneo " + tournament.displayName() + " no tenia lugar.");
                return;
            }
            boolean scheduled = tournament.scheduledAt() != null;
            tournaments.clearPlace(tournament.key());
            sender.sendMessage(PREFIX + "§aLugares del torneo " + tournament.displayName() + " borrados"
                + (scheduled ? "; el teletransporte programado tambien se cancelo." : "."));
            return;
        }
        if (!mode.isEmpty() && !mode.equals("agregar")) {
            sender.sendMessage("§cUso: /toffy torneo lugar <torneo> [agregar|borrar]");
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDesde consola no hay lugar: ubicate en el juego y usa /toffy torneo lugar "
                + tournament.key() + ".");
            return;
        }
        ToffyStoredLocation saved = ToffyModule.stored(player.getLocation());
        if (mode.equals("agregar")) {
            tournaments.addPlace(tournament.key(), saved);
            sender.sendMessage(PREFIX + "§aLugar agregado al torneo §e" + tournament.displayName() + "§a: ahora son §e"
                + tournament.places().size() + "§a (" + placeText(saved) + ").");
            return;
        }
        tournaments.setPlace(tournament.key(), saved);
        sender.sendMessage(PREFIX + "§aLugar del torneo §e" + tournament.displayName() + "§a guardado: §f"
            + placeText(saved) + "§a (reemplaza los anteriores).");
    }

    /** {@code /toffy torneo nivel <torneo> <min|*> <max|*>} and {@code ... sin}. */
    private void level(CommandSender sender, ToffyTournamentService tournaments, String[] args) {
        Optional<ToffyTournament> found = tournaments.find(args[2]);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + args[2] + ".");
            return;
        }
        ToffyTournament tournament = found.get();
        if (args[3].equalsIgnoreCase("sin")) {
            tournaments.setLevelRange(tournament.key(), null, null);
            sender.sendMessage(PREFIX + "§aTorneo " + tournament.displayName() + " sin rango de nivel.");
            return;
        }
        if (args.length < 5) {
            sender.sendMessage("§cUso: /toffy torneo nivel <torneo> <min|*> <max|*> | /toffy torneo nivel <torneo> sin");
            return;
        }
        if (!validBound(args[3]) || !validBound(args[4])) {
            sender.sendMessage("§cEl nivel tiene que ser un numero entre 0 y " + ToffyModule.coordinate(module.maximum())
                + ", o * para no poner limite.");
            return;
        }
        Double min = parseBound(args[3]);
        Double max = parseBound(args[4]);
        if ((min != null && min > module.maximum()) || (max != null && max > module.maximum())) {
            sender.sendMessage("§cEl nivel tiene que estar entre 0 y " + ToffyModule.coordinate(module.maximum()) + ".");
            return;
        }
        try {
            tournaments.setLevelRange(tournament.key(), min, max);
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("§c" + invalid.getMessage());
            return;
        }
        sender.sendMessage(PREFIX + "§aTorneo " + tournament.displayName() + ": nivel " + rangeText(tournament) + ".");
    }

    /** "*" (no bound) or a finite number. */
    static boolean validBound(String raw) {
        if (raw.equals("*")) {
            return true;
        }
        try {
            return Double.isFinite(Double.parseDouble(raw));
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    /** The bound as a number, or null for "*". Only call after {@link #validBound}. */
    static Double parseBound(String raw) {
        return raw.equals("*") ? null : Double.parseDouble(raw);
    }

    /** {@code /toffy torneo cupo <torneo> <n|sin>}. */
    private void capacity(CommandSender sender, ToffyTournamentService tournaments, String[] args) {
        Optional<ToffyTournament> found = tournaments.find(args[2]);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + args[2] + ".");
            return;
        }
        ToffyTournament tournament = found.get();
        try {
            if (args[3].equalsIgnoreCase("sin")) {
                tournaments.setCapacity(tournament.key(), null);
                sender.sendMessage(PREFIX + "§aTorneo " + tournament.displayName() + " sin cupo.");
                return;
            }
            int capacity = Integer.parseInt(args[3]);
            boolean closed = tournaments.setCapacity(tournament.key(), capacity);
            sender.sendMessage(PREFIX + "§aCupo de " + tournament.displayName() + ": " + capacity + ".");
            if (closed) {
                announceClose(tournament);
            } else if (!tournament.open()) {
                sender.sendMessage("§eLa inscripcion sigue cerrada; usa /toffy torneo abrir " + tournament.key());
            }
        } catch (NumberFormatException invalid) {
            sender.sendMessage("§cEl cupo tiene que ser un numero o sin.");
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("§c" + invalid.getMessage());
        }
    }

    private void announceClose(ToffyTournament tournament) {
        String announcement = module.message(ToffyMessages.CIERRE, tournament.key(), placeholders(tournament, null));
        if (announcement != null) {
            module.server().broadcastMessage(announcement);
        }
    }

    /** {@code /toffy torneo minimo <torneo> <n|sin>}. */
    private void minimum(CommandSender sender, ToffyTournamentService tournaments, String[] args) {
        Optional<ToffyTournament> found = tournaments.find(args[2]);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + args[2] + ".");
            return;
        }
        try {
            Integer minimum = args[3].equalsIgnoreCase("sin") ? null : Integer.parseInt(args[3]);
            tournaments.setMinPlayers(found.get().key(), minimum);
            sender.sendMessage(PREFIX + "§aMinimo de " + found.get().displayName() + ": "
                + (minimum == null ? "sin minimo" : minimum + " anotados conectados") + ".");
        } catch (NumberFormatException invalid) {
            sender.sendMessage("§cEl minimo tiene que ser un numero o sin.");
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("§c" + invalid.getMessage());
        }
    }

    /** {@code /toffy torneo cierre <torneo> <HH:mm> [dd/MM]} and {@code ... cancelar}. */
    private void closeAt(CommandSender sender, ToffyTournamentService tournaments, String[] args) {
        Optional<ToffyTournament> found = tournaments.find(args[2]);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + args[2] + ".");
            return;
        }
        ToffyTournament tournament = found.get();
        if (args[3].equalsIgnoreCase("cancelar")) {
            sender.sendMessage(tournaments.cancelClose(tournament.key())
                ? PREFIX + "§aCierre programado de " + tournament.displayName() + " cancelado."
                : "§eEl torneo " + tournament.displayName() + " no tenia un cierre programado.");
            return;
        }
        ZoneId zone = zone();
        try {
            long at = ToffyTournamentTime.resolve(args[3], args.length > 4 ? args[4] : null, tournaments.now(), zone, timeFormat(), dateFormat());
            Long previous = tournament.closeAt();
            tournaments.scheduleClose(tournament.key(), at);
            if (previous != null) {
                sender.sendMessage(PREFIX + "§eSe reemplazo el cierre programado para "
                    + when(previous) + ".");
            }
            sender.sendMessage(PREFIX + "§aLas inscripciones de " + tournament.displayName() + " se cierran el §e"
                + when(at) + "§a.");
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("§c" + invalid.getMessage());
        }
    }

    private void message(CommandSender sender, ToffyTournamentService tournaments, String[] args) {
        Optional<ToffyTournament> found = tournaments.find(args[2]);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + args[2] + ".");
            return;
        }
        String caseKey = args[3].toLowerCase(Locale.ROOT);
        if (!ToffyMessages.isCase(caseKey)) {
            sender.sendMessage("§cCaso desconocido. Casos: " + String.join(", ", ToffyMessages.CASES));
            return;
        }
        String key = found.get().key();
        if (args.length == 5 && args[4].equalsIgnoreCase("reset")) {
            sender.sendMessage(tournaments.resetMessage(key, caseKey)
                ? PREFIX + "§aMensaje " + caseKey + " vuelve al de toffy_mensajes.yml."
                : "§eEl torneo no tenia un mensaje propio para " + caseKey + ".");
            return;
        }
        String text = args.length == 5 && args[4].equals("-")
            ? ""
            : String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        tournaments.setMessage(key, caseKey, text);
        sender.sendMessage(PREFIX + "§aMensaje " + caseKey + " del torneo " + found.get().displayName() + ": §r"
            + ToffyMessages.colors(text));
    }

    /**
     * {@code /toffy torneo premio <torneo> ver|item|comando|quitar|borrar}: the prizes of this tournament's events. With
     * prizes set, the event winners get these instead of the ones of toffy_eventos.yml.
     */
    private void rewards(CommandSender sender, ToffyTournamentService tournaments, String[] args) {
        String name = args[2];
        String action = args[3].toLowerCase(Locale.ROOT);
        Optional<ToffyTournament> found = tournaments.find(name);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + name + ".");
            return;
        }
        ToffyTournament tournament = found.get();
        switch (action) {
            case "ver" -> {
                List<String> lines = tournament.rewardLines();
                if (lines.isEmpty()) {
                    sender.sendMessage(PREFIX + "§7El torneo " + tournament.displayName()
                        + " usa los premios de toffy_eventos.yml.");
                    return;
                }
                sender.sendMessage(PREFIX + "§6Premios de " + tournament.displayName() + ":");
                for (int index = 0; index < lines.size(); index++) {
                    sender.sendMessage("§7  " + (index + 1) + ". §f" + lines.get(index));
                }
            }
            case "item" -> {
                if (args.length != 5) {
                    sender.sendMessage("§cUso: " + REWARD_USAGE);
                    return;
                }
                if (RewardItem.parse(args[4]).isEmpty()) {
                    sender.sendMessage("§cItem no valido: " + args[4] + " (id o id:cantidad, por ejemplo nuke_shot:1).");
                    return;
                }
                String entry = args[4].trim().toLowerCase(Locale.ROOT);
                changeRewards(sender, tournaments, name, t -> {
                    t.addRewardItem(entry);
                    return true;
                }, "Item agregado a los premios de ");
            }
            case "comando" -> {
                if (args.length < 5) {
                    sender.sendMessage("§cUso: " + REWARD_USAGE);
                    return;
                }
                String command = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                if (command.startsWith("/")) {
                    command = command.substring(1);
                }
                String entry = command;
                changeRewards(sender, tournaments, name, t -> {
                    t.addRewardCommand(entry);
                    return true;
                }, "Comando agregado a los premios de ");
            }
            case "quitar" -> {
                int number;
                try {
                    number = args.length == 5 ? Integer.parseInt(args[4]) : -1;
                } catch (NumberFormatException notANumber) {
                    number = -1;
                }
                if (number < 1) {
                    sender.sendMessage("§cUso: " + REWARD_USAGE + " (n es el numero de /toffy torneo premio <torneo> ver).");
                    return;
                }
                int position = number;
                changeRewards(sender, tournaments, name, t -> t.removeReward(position),
                    "Premio quitado de ");
            }
            case "borrar" -> changeRewards(sender, tournaments, name, t -> {
                t.clearRewards();
                return true;
            }, "Premios borrados de ");
            default -> sender.sendMessage("§cUso: " + REWARD_USAGE);
        }
    }

    private void changeRewards(CommandSender sender, ToffyTournamentService tournaments, String name,
                               Predicate<ToffyTournament> change, String done) {
        Optional<Boolean> result = tournaments.editRewards(name, change);
        if (result.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + name + ".");
        } else if (!result.get()) {
            sender.sendMessage("§cNo hay un premio con ese numero en el torneo " + name + ".");
        } else {
            ToffyTournament tournament = tournaments.find(name).orElseThrow();
            sender.sendMessage(PREFIX + "§a" + done + tournament.displayName() + ". Ver con /toffy torneo premio "
                + tournament.key() + " ver.");
        }
    }

    private void teleport(CommandSender sender, ToffyTournamentService tournaments, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "concursantes" -> {
                if (needs(sender, args, 3, "/toffy tp concursantes <torneo> [lugar | x y z [mundo]] [forzar]")) {
                    bring(sender, tournaments, args);
                }
            }
            case "devolver" -> {
                if (needs(sender, args, 3, "/toffy tp devolver <torneo>")) {
                    giveBack(sender, tournaments, args[2]);
                }
            }
            case "programar" -> schedule(sender, tournaments, args);
            case "programados" -> scheduled(sender, tournaments);
            default -> sender.sendMessage("§cUso: /toffy tp <concursantes|devolver|programar|programados> (mira /toffy ayuda)");
        }
    }

    private void bring(CommandSender sender, ToffyTournamentService tournaments, String[] raw) {
        Extracted extracted = extractGroup(raw, 3);
        boolean forced = forced(extracted.args());
        String[] args = forced ? Arrays.copyOf(extracted.args(), extracted.args().length - 1) : extracted.args();
        if (args.length < 3 || extracted.dangling()) {
            sender.sendMessage("§cUso: /toffy tp concursantes <torneo> [grupo <grupo>] [lugar | x y z [mundo]] [forzar]");
            return;
        }
        Optional<ToffyTournament> found = tournaments.find(args[2]);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + args[2] + ".");
            return;
        }
        ToffyTournament tournament = found.get();
        ToffyGroup group = null;
        if (extracted.group() != null) {
            group = tournaments.group(tournament.key(), extracted.group()).orElse(null);
            if (group == null) {
                sender.sendMessage("§cNo existe el grupo " + extracted.group() + " en " + tournament.displayName() + ".");
                return;
            }
        }
        boolean groupHasPlaces = group != null && !group.places().isEmpty();
        boolean lugar = args.length > 3 && args[3].equalsIgnoreCase("lugar");
        // Coordinates typed in the command are an explicit destination: everyone goes there, group places included.
        boolean explicit = args.length > 3 && !lugar;
        List<Location> targets;
        if (lugar) {
            if (tournament.places().isEmpty() && !groupHasPlaces) {
                sender.sendMessage("§cEl torneo no tiene lugar. Usa /toffy torneo lugar " + tournament.key());
                return;
            }
            targets = module.savedLocations(tournament);
            if (targets.isEmpty() && !groupHasPlaces) {
                sender.sendMessage("§cNingun lugar del torneo esta en un mundo cargado.");
                return;
            }
        } else if (groupHasPlaces && !(sender instanceof Player)) {
            // Without coordinates a console has no location: the group places are the destination.
            targets = List.of();
        } else {
            Location target = target(sender, args);
            if (target == null) {
                return;
            }
            targets = List.of(target);
        }
        if (!forced && belowMinimum(sender, tournament)) {
            return;
        }
        ToffyModule.Brought result = module.bringParticipants(tournaments, tournament, targets,
            group == null ? null : group.key(), explicit);
        sender.sendMessage(PREFIX + "§a" + result.moved() + " concursantes llevados" + (group == null ? ""
            : " del grupo " + group.displayName()) + "§7, §e" + result.offline()
            + " offline" + (result.outOfRange() > 0 ? "§7, §e" + result.outOfRange() + " fuera de rango" : "")
            + (result.failed() > 0 ? "§7, §c" + result.failed() + " no se pudieron mover" : "") + "§7.");
    }

    /** Warns the admin when the eligible online sign-ups are below the minimum (the run goes on only with forzar). */
    private boolean belowMinimum(CommandSender sender, ToffyTournament tournament) {
        Integer minimum = tournament.minPlayers();
        if (minimum == null) {
            return false;
        }
        int eligible = module.eligibleOnline(tournament).size();
        if (eligible >= minimum) {
            return false;
        }
        sender.sendMessage("§cSolo hay " + eligible + " anotados conectados en rango (minimo " + minimum
            + "). Agrega forzar al final para hacerlo igual.");
        return true;
    }

    private Location target(CommandSender sender, String[] args) {
        if (args.length == 3) {
            if (sender instanceof Player player) {
                return player.getLocation();
            }
            sender.sendMessage("§cDesde consola usa /toffy tp concursantes <torneo> lugar o <torneo> <x> <y> <z> [mundo].");
            return null;
        }
        if (args.length < 6) {
            sender.sendMessage("§cUso: /toffy tp concursantes <torneo> [lugar | x y z [mundo]]");
            return null;
        }
        double x;
        double y;
        double z;
        try {
            x = Double.parseDouble(args[3]);
            y = Double.parseDouble(args[4]);
            z = Double.parseDouble(args[5]);
        } catch (NumberFormatException invalid) {
            sender.sendMessage("§cCoordenadas invalidas.");
            return null;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            sender.sendMessage("§cCoordenadas invalidas.");
            return null;
        }
        World world;
        if (args.length > 6) {
            world = module.server().getWorld(args[6]);
        } else if (sender instanceof Player player) {
            world = player.getWorld();
        } else {
            List<World> worlds = module.server().getWorlds();
            world = worlds.isEmpty() ? null : worlds.getFirst();
        }
        if (world == null) {
            sender.sendMessage("§cMundo no encontrado.");
            return null;
        }
        return new Location(world, x, y, z);
    }

    private void giveBack(CommandSender sender, ToffyTournamentService tournaments, String name) {
        Optional<ToffyTournament> found = tournaments.find(name);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + name + ".");
            return;
        }
        ToffyTournament tournament = found.get();
        Set<UUID> ids = new LinkedHashSet<>(tournament.participants().keySet());
        ids.addAll(tournament.returns().keySet());
        int returned = 0;
        int pending = 0;
        int missingWorld = 0;
        int noPlace = 0;
        for (UUID id : ids) {
            Player player = module.server().getPlayer(id);
            ToffyStoredLocation saved = tournament.returns().get(id);
            if (saved == null) {
                if (player != null) {
                    module.send(player, ToffyMessages.SIN_LUGAR_GUARDADO, tournament.key(),
                        placeholders(tournament, player));
                    noPlace++;
                }
            } else if (player == null) {
                if (tournaments.markPending(tournament.key(), id)) {
                    pending++;
                }
            } else if (module.server().getWorld(saved.world()) == null) {
                missingWorld++;
            } else if (module.returnPlayer(player, tournament.key(), saved)) {
                returned++;
            }
        }
        StringBuilder report = new StringBuilder(PREFIX + "§a" + returned + " devueltos§7, §e" + pending
            + " offline (volveran al entrar)");
        if (noPlace > 0) {
            report.append("§7, §e").append(noPlace).append(" sin lugar guardado");
        }
        if (missingWorld > 0) {
            report.append("§7, §c").append(missingWorld)
                .append(" con un mundo que ya no existe (su lugar queda guardado)");
        }
        sender.sendMessage(report.append("§7.").toString());
    }

    /** {@code /toffy tp programar <torneo> <HH:mm> [dd/MM]} and {@code ... cancelar}. */
    private void schedule(CommandSender sender, ToffyTournamentService tournaments, String[] args) {
        if (!needs(sender, args, 4,
            "/toffy tp programar <torneo> <HH:mm> [dd/MM] | /toffy tp programar <torneo> cancelar")) {
            return;
        }
        Optional<ToffyTournament> found = tournaments.find(args[2]);
        if (found.isEmpty()) {
            sender.sendMessage("§cNo existe el torneo " + args[2] + ".");
            return;
        }
        ToffyTournament tournament = found.get();
        ZoneId zone = zone();
        if (args[3].equalsIgnoreCase("cancelar")) {
            Long at = tournament.scheduledAt();
            if (at == null) {
                sender.sendMessage("§eEl torneo " + tournament.displayName()
                    + " no tenia un teletransporte programado.");
                return;
            }
            tournaments.cancelSchedule(tournament.key());
            sender.sendMessage(PREFIX + "§aTeletransporte programado de " + tournament.displayName() + " cancelado.");
            module.send(sender, ToffyMessages.TP_CANCELADO, tournament.key(), scheduleTexts(tournament, at, zone));
            return;
        }
        if (tournament.places().isEmpty()) {
            sender.sendMessage("§cEl torneo no tiene lugar. Usa /toffy torneo lugar " + tournament.key());
            return;
        }
        long at;
        try {
            at = ToffyTournamentTime.resolve(args[3], args.length > 4 ? args[4] : null, tournaments.now(), zone, timeFormat(), dateFormat());
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("§c" + invalid.getMessage());
            return;
        }
        Optional<Long> replaced = tournaments.schedule(tournament.key(), at);
        replaced.ifPresent(old -> sender.sendMessage(PREFIX + "§eSe reemplazo el teletransporte programado para "
            + when(old) + "."));
        sender.sendMessage(PREFIX + "§aTeletransporte de " + tournament.displayName() + " programado para §e"
            + when(at) + "§a.");
        String announcement = module.message(ToffyMessages.TP_PROGRAMADO, tournament.key(),
            scheduleTexts(tournament, at, zone));
        if (announcement != null) {
            module.server().broadcastMessage(announcement);
        }
    }

    /** {@code /toffy programados} and {@code /toffy tp programados}: every scheduled teleport, close and event. */
    void scheduled(CommandSender sender, ToffyTournamentService tournaments) {
        ZoneId zone = zone();
        List<Map.Entry<Long, String>> lines = new ArrayList<>();
        for (ToffyTournament tournament : tournaments.list()) {
            String name = tournament.displayName();
            if (tournament.scheduledAt() != null) {
                lines.add(Map.entry(tournament.scheduledAt(), "§7  §f" + name + " §8- §bteletransporte §f"
                    + when(tournament.scheduledAt()) + " §8- §7lugares: §f"
                    + tournament.places().size()));
            }
            if (tournament.closeAt() != null) {
                lines.add(Map.entry(tournament.closeAt(), "§7  §f" + name + " §8- §bcierre de inscripciones §f"
                    + when(tournament.closeAt())));
            }
            if (tournament.eventAt() != null) {
                lines.add(Map.entry(tournament.eventAt(), "§7  §f" + name + " §8- §bevento §f"
                    + tournament.eventType() + " " + when(tournament.eventAt())));
            }
        }
        if (lines.isEmpty()) {
            sender.sendMessage(PREFIX + "No hay nada programado. Mira /toffy ayuda.");
            return;
        }
        lines.sort(Comparator.comparingLong(Map.Entry::getKey));
        sender.sendMessage(PREFIX + "Programados (" + lines.size() + "):");
        lines.forEach(line -> sender.sendMessage(line.getValue()));
    }

    private Map<String, String> scheduleTexts(ToffyTournament tournament, long at, ZoneId zone) {
        Map<String, String> map = new HashMap<>();
        map.put("torneo", tournament.displayName());
        map.put("hora", ToffyTournamentTime.hour(at, zone, timeFormat()));
        map.put("fecha", ToffyTournamentTime.date(at, zone, dateFormat()));
        return map;
    }

    static String placeText(ToffyStoredLocation place) {
        return place.world() + " " + ToffyModule.coordinate(place.x()) + ", " + ToffyModule.coordinate(place.y())
            + ", " + ToffyModule.coordinate(place.z());
    }

    /** Zone of the scheduled times: {@code torneos.zona-horaria} in toffy_eventos.yml. */
    private ZoneId zone() {
        return module.tournamentTimes().zoneId();
    }

    private String timeFormat() {
        return module.tournamentTimes().timeFormat();
    }

    private String dateFormat() {
        return module.tournamentTimes().dateFormat();
    }

    /** Date and time of an epoch millis value in the configured zone and formats. */
    private String when(long at) {
        return when(at, module.tournamentTimes());
    }

    static String when(long at, ToffyEventSettings.Tournaments times) {
        return ToffyTournamentTime.dateTime(at, times.zoneId(), times.timeFormat(), times.dateFormat());
    }

    private static Map<String, String> placeholders(ToffyTournament tournament, Player player) {
        return placeholders(tournament, player, null);
    }

    static Map<String, String> placeholders(ToffyTournament tournament, Player player, Location at) {
        Map<String, String> map = new HashMap<>();
        map.put("torneo", tournament.displayName());
        map.put("cantidad", String.valueOf(tournament.participants().size()));
        map.put("jugador", player == null ? "" : player.getName());
        map.put("grupo", "");
        if (at != null) {
            map.put("x", ToffyModule.coordinate(at.getX()));
            map.put("y", ToffyModule.coordinate(at.getY()));
            map.put("z", ToffyModule.coordinate(at.getZ()));
            map.put("mundo", at.getWorld() == null ? "" : at.getWorld().getName());
        }
        return map;
    }

    List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION) && !sender.hasPermission(ToffyCommand.ADMIN_PERMISSION)) {
            return List.of();
        }
        ToffyTournamentService tournaments = module.tournaments();
        List<String> options = new ArrayList<>();
        String current = args[args.length - 1];
        boolean tp = args[0].equalsIgnoreCase("tp");
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (!tp && sub.equals("grupo") && args.length >= 4 && tournaments != null) {
            return groups.complete(tournaments, args);
        }
        if (args.length == 2) {
            options.addAll(tp ? TP_SUBCOMMANDS : TORNEO_SUBCOMMANDS);
        } else if (tournaments != null && args.length == 3 && !sub.equals("crear") && !sub.equals("lista")
            && !sub.equals("programados")) {
            options.addAll(tournaments.keys());
        } else if (tp && args.length == 4 && sub.equals("concursantes")) {
            options.add("lugar");
            options.add("grupo");
            options.add(FORCE);
        } else if (tp && args.length == 5 && sub.equals("concursantes") && args[3].equalsIgnoreCase("grupo")
            && tournaments != null) {
            tournaments.find(args[2]).ifPresent(tournament -> tournament.groups().forEach(group -> options.add(group.key())));
        } else if (tp && args.length == 4 && sub.equals("programar")) {
            options.add("cancelar");
            options.add(nextFiveMinutes(zone()));
        } else if (!tp && args.length == 4 && sub.equals("lugar")) {
            options.add("agregar");
            options.add("borrar");
        } else if (!tp && args.length == 4 && sub.equals("nivel")) {
            options.add("sin");
            options.add("*");
        } else if (!tp && args.length == 5 && sub.equals("nivel")) {
            options.add("*");
        } else if (!tp && args.length == 4 && (sub.equals("cupo") || sub.equals("minimo"))) {
            options.add("sin");
        } else if (!tp && args.length == 4 && sub.equals("cierre")) {
            options.add("cancelar");
            options.add(nextFiveMinutes(zone()));
        } else if (tournaments != null && args.length == 4 && !tp && sub.equals("sacar")) {
            tournaments.find(args[2]).ifPresent(tournament -> options.addAll(tournament.participants().values()));
        } else if (args.length == 4 && !tp && sub.equals("mensaje")) {
            options.addAll(ToffyMessages.CASES);
        } else if (args.length == 5 && !tp && sub.equals("mensaje")) {
            options.add("reset");
            options.add("-");
        } else if (args.length == 4 && !tp && sub.equals("premio")) {
            options.addAll(List.of("ver", "item", "comando", "quitar", "borrar"));
        } else if (args.length == 7 && tp && sub.equals("concursantes")) {
            module.server().getWorlds().forEach(world -> options.add(world.getName()));
        }
        String prefix = current.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    /** The first HH:mm that is a multiple of five minutes after now, to suggest for scheduling. */
    private String nextFiveMinutes(ZoneId zone) {
        ZonedDateTime next = ZonedDateTime.now(zone).truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        int remainder = next.getMinute() % 5;
        if (remainder != 0) {
            next = next.plusMinutes(5 - remainder);
        }
        return ToffyTournamentTime.hour(next.toInstant().toEpochMilli(), zone, timeFormat());
    }
}
