package dev.linqfy.bigCasares.command;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.modules.copperapple.CopperAppleCommand;
import dev.linqfy.bigCasares.modules.skillrating.SkillRatingView;
import dev.linqfy.bigCasares.modules.resourcepack.ActivePackManifest;
import dev.linqfy.bigCasares.modules.resourcepack.PackReloadMessageFormatter;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogReloadMessageFormatter;
import dev.linqfy.bigCasares.modules.teams.Team;
import dev.linqfy.bigCasares.modules.teams.TeamColor;
import dev.linqfy.bigCasares.reload.ReloadMessageFormatter;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class BigCasaresCommand implements CommandExecutor, TabCompleter {

    private static final String SHOP_OPEN_PERMISSION = "bigcasares.shop.open";
    private static final String SHOP_RELOAD_PERMISSION = "bigcasares.shop.reload";
    private static final String PACK_RELOAD_PERMISSION = "bigcasares.pack.reload";
    private static final String PACK_INFO_PERMISSION = "bigcasares.pack.info";
    private static final String PACK_SEND_PERMISSION = "bigcasares.pack.send";
    private static final String ITEMS_RELOAD_PERMISSION = "bigcasares.items.reload";
    private static final String ITEMS_INFO_PERMISSION = "bigcasares.items.info";
    private static final String TEAM_CREATE_PERMISSION = "bigcasares.team.create";
    private static final String TEAM_JOIN_PERMISSION = "bigcasares.team.join";
    private static final String TEAM_LEAVE_PERMISSION = "bigcasares.team.leave";
    private static final String TEAM_EDIT_PERMISSION = "bigcasares.team.edit";
    private static final String TEAM_APPEARANCE_PERMISSION = "bigcasares.team.appearance";

    private static final String TIDEFRAME_STATUS_PERMISSION = "bigcasares.tideframe.status";
    private static final String SHOP_HISTORY_PERMISSION = "bigcasares.shop.history";
    private static final String DANGER_VIEW_PERMISSION = "bigcasares.danger.view";
    private static final String DANGER_VIEW_OTHERS_PERMISSION = "bigcasares.danger.view.others";

    private final BigCasares plugin;
    private final CopperAppleCommand legacyCommand;

    public BigCasaresCommand(BigCasares plugin) {
        this.plugin = plugin;
        this.legacyCommand = new CopperAppleCommand(plugin);
    }

    public static boolean isReload(String value) {
        return "reload".equalsIgnoreCase(value);
    }

    public static boolean isShop(String value) {
        return "shop".equalsIgnoreCase(value);
    }

    public static boolean isDanger(String value) {
        return "peligro".equalsIgnoreCase(value);
    }

    public static List<String> rootSuggestions(String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        return List.of("give", "misiones", "shop", "peligro", "nexus", "team", "boss", "tumba", "reload", "pack", "items", "baltop", "tideframe", "historial").stream()
            .filter(option -> option.startsWith(lowered))
            .collect(Collectors.toList());
    }

    public static List<String> reloadSuggestions(String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        return List.of("items", "pack").stream().filter(option -> option.startsWith(lowered)).toList();
    }

    public static List<String> packSuggestions(String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        return List.of("info", "send").stream().filter(option -> option.startsWith(lowered)).toList();
    }

    public static List<String> bossIdSuggestions(String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        return List.of("abyss-guardian", "tung-tung-sahur").stream()
            .filter(id -> id.startsWith(lowered))
            .toList();
    }

    public static boolean isSupportedBossId(String value) {
        return value != null && dev.linqfy.bigCasares.modules.pveboss.PveBossModule.supportedBossIds()
            .contains(value.toLowerCase(Locale.ROOT));
    }

    public static List<String> tombstoneSuggestions(String prefix) {
        return "abrir".startsWith(prefix.toLowerCase(Locale.ROOT)) ? List.of("abrir") : List.of();
    }

    public static List<String> teamSubcommandSuggestions(String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        return List.of("create", "invite", "join", "leave", "kick", "dissolve", "rename", "tag", "color", "appearance").stream()
            .filter(o -> o.startsWith(lowered))
            .collect(Collectors.toList());
    }

    public static List<String> teamColorSuggestions(String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        return List.of("DARK_AQUA", "DARK_BLUE", "DARK_GRAY", "DARK_GREEN", "DARK_PURPLE", "DARK_RED").stream()
            .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lowered))
            .collect(Collectors.toList());
    }

    public static List<String> parseQuotedArgs(String[] args) {
        List<String> parsed = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (String arg : args) {
            if (inQuotes) {
                if (arg.endsWith("\"")) {
                    current.append(" ").append(arg, 0, arg.length() - 1);
                    parsed.add(current.toString());
                    current.setLength(0);
                    inQuotes = false;
                } else {
                    current.append(" ").append(arg);
                }
            } else {
                if (arg.startsWith("\"")) {
                    if (arg.endsWith("\"") && arg.length() > 1) {
                        parsed.add(arg.substring(1, arg.length() - 1));
                    } else {
                        current.append(arg.substring(1));
                        inQuotes = true;
                    }
                } else {
                    parsed.add(arg);
                }
            }
        }
        if (inQuotes) {
            parsed.add(current.toString());
        }
        return parsed;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("shop".equalsIgnoreCase(command.getName())) {
            sender.sendMessage(ChatColor.RED + "El comando /shop está desactivado. Por favor, busca a los Mercaderes en el mundo.");
            return true;
        }
        if ("resourcepack".equalsIgnoreCase(command.getName())) {
            return handleManualResourcePack(sender);
        }
        if ("peligro".equalsIgnoreCase(command.getName())) {
            return showDanger(sender, args);
        }
        if ("team".equalsIgnoreCase(command.getName())) {
            return handleTeam(sender, args);
        }
        if ("teammanage".equalsIgnoreCase(command.getName())) {
            return handleTeamManage(sender, args);
        }
        if ("boss".equalsIgnoreCase(command.getName())) {
            return handleBoss(sender, args);
        }
        if ("nexus".equalsIgnoreCase(command.getName())) {
            return handleNexus(sender, args);
        }
        if ("tumba".equalsIgnoreCase(command.getName())) {
            return handleTombstone(sender, args);
        }
        if ("misiones".equalsIgnoreCase(command.getName())) {
            return delegateToMisionesHandler(sender, command, label, args);
        }
        if ("baltop".equalsIgnoreCase(command.getName())) {
            return handleBaltop(sender, args);
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        if (isReload(args[0])) {
            if (args.length == 2 && "pack".equalsIgnoreCase(args[1])) {
                return reloadPack(sender);
            }
            if (args.length == 2 && "items".equalsIgnoreCase(args[1])) {
                return reloadItems(sender);
            }
            if (!sender.hasPermission(SHOP_RELOAD_PERMISSION)) {
                sender.sendMessage(ChatColor.RED + "No tenes permiso para recargar configs.");
                return true;
            }
            sender.sendMessage(ReloadMessageFormatter.format(plugin.reloadPluginState()));
            return true;
        }

        if ("pack".equalsIgnoreCase(args[0])) {
            return handlePack(sender, dropFirst(args));
        }

        if ("items".equalsIgnoreCase(args[0])) {
            return handleItems(sender, dropFirst(args));
        }

        if ("historial".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Solo un jugador tiene historial de la tienda.");
                return true;
            }
            if (!sender.hasPermission(SHOP_HISTORY_PERMISSION)) {
                sender.sendMessage(ChatColor.RED + "No tenes permiso para ver tu historial.");
                return true;
            }
            plugin.getShopModule().history().lines(player.getUniqueId()).forEach(sender::sendMessage);
            return true;
        }

        if ("tideframe".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission(TIDEFRAME_STATUS_PERMISSION)) {
                sender.sendMessage(ChatColor.RED + "No tenes permiso para ver el estado de Tideframe.");
                return true;
            }
            plugin.tideframeStatus().lines().forEach(sender::sendMessage);
            return true;
        }

        if (isShop(args[0])) {
            sender.sendMessage(ChatColor.RED + "El comando /shop está desactivado. Por favor, busca a los Mercaderes en el mundo.");
            return true;
        }

        if (isDanger(args[0])) {
            return showDanger(sender, dropFirst(args));
        }

        if ("team".equalsIgnoreCase(args[0])) {
            return handleTeam(sender, dropFirst(args));
        }

        if ("teammanage".equalsIgnoreCase(args[0])) {
            return handleTeamManage(sender, dropFirst(args));
        }

        if ("boss".equalsIgnoreCase(args[0])) {
            return handleBoss(sender, dropFirst(args));
        }

        if ("nexus".equalsIgnoreCase(args[0])) {
            return handleNexus(sender, dropFirst(args));
        }

        if ("tumba".equalsIgnoreCase(args[0])) {
            return handleTombstone(sender, dropFirst(args));
        }

        if ("baltop".equalsIgnoreCase(args[0])) {
            return handleBaltop(sender, dropFirst(args));
        }

        return legacyCommand.onCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ("shop".equalsIgnoreCase(command.getName())) {
            return List.of();
        }
        if ("peligro".equalsIgnoreCase(command.getName())) {
            if (args.length == 1) return "expandir".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("expandir") : null;
            return null;
        }
        if ("team".equalsIgnoreCase(command.getName())) {
            return teamSuggestions(sender, args);
        }
        if ("boss".equalsIgnoreCase(command.getName())) {
            return args.length == 1
                ? List.of("spawn").stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList()
                : args.length == 2 && "spawn".equalsIgnoreCase(args[0])
                    ? bossIdSuggestions(args[1])
                    : List.of();
        }
        if ("nexus".equalsIgnoreCase(command.getName())) {
            if (args.length == 1) return List.of("place", "claim", "give").stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
            if (args.length == 2 && "give".equalsIgnoreCase(args[0])) return null; // Player names
            return List.of();
        }
        if ("tumba".equalsIgnoreCase(command.getName())) {
            return args.length == 1 ? tombstoneSuggestions(args[0]) : List.of();
        }
        if ("misiones".equalsIgnoreCase(command.getName())) {
            String[] cmdArgs = new String[args.length + 1];
            cmdArgs[0] = "misiones";
            System.arraycopy(args, 0, cmdArgs, 1, args.length);
            return legacyCommand.onTabComplete(sender, command, alias, cmdArgs);
        }

        if (args.length == 1) {
            return rootSuggestions(args[0]);
        }

        if (args.length == 2 && isReload(args[0])) {
            return reloadSuggestions(args[1]);
        }

        if ("pack".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return packSuggestions(args[1]);
            }
            if (args.length == 3 && "send".equalsIgnoreCase(args[1])) {
                return "all".startsWith(args[2].toLowerCase(Locale.ROOT)) ? List.of("all") : null;
            }
            return List.of();
        }

        if ("items".equalsIgnoreCase(args[0])) {
            return args.length == 2 && "info".startsWith(args[1].toLowerCase(Locale.ROOT))
                ? List.of("info") : List.of();
        }

        if ("team".equalsIgnoreCase(args[0])) {
            return teamSuggestions(sender, dropFirst(args));
        }

        if ("boss".equalsIgnoreCase(args[0])) {
            String[] bossArgs = dropFirst(args);
            return bossArgs.length == 1
                ? List.of("spawn").stream().filter(value -> value.startsWith(bossArgs[0].toLowerCase(Locale.ROOT))).toList()
                : bossArgs.length == 2 && "spawn".equalsIgnoreCase(bossArgs[0])
                    ? bossIdSuggestions(bossArgs[1])
                    : List.of();
        }

        if ("tumba".equalsIgnoreCase(args[0])) {
            return args.length == 2 ? tombstoneSuggestions(args[1]) : List.of();
        }

        return legacyCommand.onTabComplete(sender, command, alias, args);
    }

    private boolean handleTeam(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden administrar equipos.");
            return true;
        }
        var module = plugin.getTeamModule();
        var service = module == null ? java.util.Optional.<dev.linqfy.bigCasares.modules.teams.TeamService>empty()
            : module.service();
        var commands = module == null
            ? java.util.Optional.<dev.linqfy.bigCasares.modules.teams.TeamCommandService>empty()
            : module.commandService();
        if (service.isEmpty() || commands.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Team System no esta disponible.");
            return true;
        }
        if (args.length == 0) {
            sendTeamUsage(sender);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        try {
            if ("create".equals(action)) {
                if (!requirePermission(sender, TEAM_CREATE_PERMISSION)) {
                    return true;
                }
                List<String> parsedArgs = parseQuotedArgs(args);
                if (parsedArgs.size() < 3 || parsedArgs.size() > 4) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /team create <\"nombre\"> <tag> [color]");
                    return true;
                }
                org.bukkit.NamespacedKey limitKey = new org.bukkit.NamespacedKey(plugin, "last_team_creation");
                var pdc = player.getPersistentDataContainer();
                if (pdc.has(limitKey, org.bukkit.persistence.PersistentDataType.LONG)) {
                    Long lastCreated = pdc.get(limitKey, org.bukkit.persistence.PersistentDataType.LONG);
                    if (lastCreated != null) {
                        long elapsed = System.currentTimeMillis() - lastCreated;
                        long oneDayMs = 24L * 60L * 60L * 1000L;
                        if (elapsed < oneDayMs) {
                            long remainingMs = oneDayMs - elapsed;
                            long hours = remainingMs / (60 * 60 * 1000);
                            long minutes = (remainingMs % (60 * 60 * 1000)) / (60 * 1000);
                            sender.sendMessage(ChatColor.RED + String.format(
                                "Solo puedes crear un equipo por día. Debes esperar %d horas y %d minutos.", hours, minutes));
                            return true;
                        }
                    }
                }
                String name = parsedArgs.get(1);
                String tag = parsedArgs.get(2);
                TeamColor color = parsedArgs.size() == 4 ? TeamColor.parse(parsedArgs.get(3)) : TeamColor.WHITE;
                Team created = commands.orElseThrow().create(player.getUniqueId(), name, tag, color);
                pdc.set(limitKey, org.bukkit.persistence.PersistentDataType.LONG, System.currentTimeMillis());
                sender.sendMessage(ChatColor.GREEN + "Equipo " + created.name() + " [" + created.tag() + "] creado.");
                return true;
            }
            if ("invite".equals(action)) {
                if (!requirePermission(sender, TEAM_EDIT_PERMISSION)) {
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /team invite <jugador>");
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ChatColor.RED + "Ese jugador debe estar conectado.");
                    return true;
                }
                Team team = commands.orElseThrow().invite(player.getUniqueId(), target.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + "Invitaste a " + target.getName() + " durante 5 minutos.");
                target.sendMessage(ChatColor.AQUA + "Fuiste invitado a " + team.name()
                    + ". Usa /team join " + team.tag() + ".");
                return true;
            }
            if ("join".equals(action)) {
                if (!requirePermission(sender, TEAM_JOIN_PERMISSION)) {
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /team join <tag>");
                    return true;
                }
                Team joined = commands.orElseThrow().join(player.getUniqueId(), args[1]);
                sender.sendMessage(ChatColor.GREEN + "Ahora perteneces a " + joined.name() + ".");
                return true;
            }
            if ("leave".equals(action)) {
                if (!requirePermission(sender, TEAM_LEAVE_PERMISSION)) {
                    return true;
                }
                if (args.length != 1) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /team leave");
                    return true;
                }
                commands.orElseThrow().leave(player.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + "Abandonaste el equipo.");
                return true;
            }
            if ("kick".equals(action)) {
                if (!requirePermission(sender, TEAM_EDIT_PERMISSION)) {
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /team kick <jugador>");
                    return true;
                }
                Team team = requirePlayerTeam(service.orElseThrow(), player.getUniqueId());
                UUID targetId = findMemberByName(team, args[1]);
                Team updated = commands.orElseThrow().kick(player.getUniqueId(), targetId);
                sender.sendMessage(ChatColor.GREEN + "Expulsaste a " + args[1] + " de " + updated.name() + ".");
                Player target = plugin.getServer().getPlayer(targetId);
                if (target != null) {
                    target.sendMessage(ChatColor.RED + "Fuiste expulsado del equipo " + team.name() + ".");
                }
                return true;
            }
            if ("dissolve".equals(action)) {
                if (!requirePermission(sender, TEAM_EDIT_PERMISSION)) {
                    return true;
                }
                if (args.length != 1) {
                    sender.sendMessage(ChatColor.YELLOW + "Uso: /team dissolve");
                    return true;
                }
                Team dissolved = commands.orElseThrow().dissolve(player.getUniqueId());
                if (plugin.getNexusModule() != null) {
                    plugin.getNexusModule().makeNexusTeamless(dissolved.id());
                }
                sender.sendMessage(ChatColor.GREEN + "Disolviste el equipo " + dissolved.name() + ".");
                return true;
            }
            Team team = requirePlayerTeam(service.orElseThrow(), player.getUniqueId());
            if ("appearance".equals(action) && args.length == 1) {
                if (!requirePermission(sender, TEAM_APPEARANCE_PERMISSION)
                    || !service.orElseThrow().canViewAppearance(team.id(), player.getUniqueId())) {
                    sender.sendMessage(ChatColor.RED + "Solo OWNER o ADMIN pueden consultar la apariencia.");
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + "Apariencia de " + team.name());
                sender.sendMessage(ChatColor.GRAY + "Tag: " + dev.linqfy.bigCasares.modules.teams.TeamPresentation.from(team).formattedPrefix());
                sender.sendMessage(ChatColor.GRAY + "Color: " + ChatColor.WHITE + team.color().name());
                sender.sendMessage(ChatColor.GRAY + "Estilo: " + ChatColor.WHITE + team.tagStyle());
                sender.sendMessage(ChatColor.GRAY + "Rol: " + ChatColor.WHITE
                    + team.roleOf(player.getUniqueId()).orElseThrow().name());
                sender.sendMessage(ChatColor.GRAY + "Miembros: " + ChatColor.WHITE + team.members().size());
                return true;
            }
            if (("tag".equals(action) || "color".equals(action) || "rename".equals(action) || "appearance".equals(action))
                && !requirePermission(sender, TEAM_EDIT_PERMISSION)) {
                return true;
            }
            if ("tag".equals(action) && args.length == 2) {
                module.presentationService().orElseThrow()
                    .updateTagAndRefresh(team.id(), player.getUniqueId(), args[1]);
                sender.sendMessage(ChatColor.GREEN + "Tag actualizado a " + args[1] + ".");
                return true;
            }
            if ("rename".equals(action) && args.length >= 2) {
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                module.presentationService().orElseThrow().renameAndRefresh(team.id(), player.getUniqueId(), name);
                sender.sendMessage(ChatColor.GREEN + "Equipo renombrado a " + name + ".");
                return true;
            }
            if ("appearance".equals(action) && args.length == 3) {
                var style = team.tagStyle();
                String setting = args[1].toLowerCase(Locale.ROOT);
                String value = args[2];
                style = switch (setting) {
                    case "bold", "negrita" -> style.withBold(parseToggle(value));
                    case "italic", "cursiva" -> style.withItalic(parseToggle(value));
                    case "underline", "subrayado" -> style.withUnderlined(parseToggle(value));
                    case "strikethrough", "tachado" -> style.withStrikethrough(parseToggle(value));
                    case "wrapper", "envoltorio" -> style.withWrapper(dev.linqfy.bigCasares.modules.teams.TeamTagWrapper.parse(value));
                    default -> throw new IllegalArgumentException("Ajuste inválido: " + setting + ".");
                };
                module.presentationService().orElseThrow().updateStyleAndRefresh(team.id(), player.getUniqueId(), style);
                sender.sendMessage(ChatColor.GREEN + "Apariencia actualizada.");
                return true;
            }
            if ("color".equals(action) && args.length == 2) {
                TeamColor color = TeamColor.parse(args[1]);
                module.presentationService().orElseThrow()
                    .updateColorAndRefresh(team.id(), player.getUniqueId(), color);
                sender.sendMessage(ChatColor.GREEN + "Color actualizado a " + color.name() + ".");
                return true;
            }
        } catch (IllegalArgumentException | SecurityException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }
        sendTeamUsage(sender);
        return true;
    }

    private static boolean parseToggle(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "si", "sí" -> true;
            case "off", "false", "no" -> false;
            default -> throw new IllegalArgumentException("Usa on u off.");
        };
    }

    private List<String> teamSuggestions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return teamSubcommandSuggestions(args[0]);
        }
        if (args.length == 2 && "color".equalsIgnoreCase(args[0])) {
            return teamColorSuggestions(args[1]);
        }
        if (args.length == 4 && "create".equalsIgnoreCase(args[0])) {
            return teamColorSuggestions(args[3]);
        }
        String prefix = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 2 && "invite".equalsIgnoreCase(args[0])) {
            return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        }
        var module = plugin.getTeamModule();
        var service = module == null ? java.util.Optional.<dev.linqfy.bigCasares.modules.teams.TeamService>empty()
            : module.service();
        if (args.length == 2 && "join".equalsIgnoreCase(args[0]) && service.isPresent()) {
            return service.orElseThrow().findAll().stream()
                .map(Team::tag)
                .filter(tag -> tag.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        }
        if (args.length == 2 && "kick".equalsIgnoreCase(args[0])
            && sender instanceof Player player && service.isPresent()) {
            return service.orElseThrow().findByMember(player.getUniqueId()).stream()
                .flatMap(team -> team.members().keySet().stream())
                .filter(id -> !id.equals(player.getUniqueId()))
                .map(id -> plugin.getServer().getOfflinePlayer(id).getName())
                .filter(java.util.Objects::nonNull)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        }
        return List.of();
    }

    private Team requirePlayerTeam(dev.linqfy.bigCasares.modules.teams.TeamService service, UUID playerId) {
        return service.findByMember(playerId)
            .orElseThrow(() -> new IllegalArgumentException("No perteneces a un equipo."));
    }

    private UUID findMemberByName(Team team, String playerName) {
        return team.members().keySet().stream()
            .filter(id -> {
                String knownName = plugin.getServer().getOfflinePlayer(id).getName();
                return knownName != null && knownName.equalsIgnoreCase(playerName);
            })
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Ese jugador no pertenece a tu equipo."));
    }

    private static boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "No tenes permiso para usar ese subcomando.");
        return false;
    }

    private boolean reloadPack(CommandSender sender) {
        if (!sender.hasPermission(PACK_RELOAD_PERMISSION) && !sender.hasPermission(SHOP_RELOAD_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "No tenes permiso para reconstruir el pack.");
            return true;
        }
        var module = plugin.getResourcePackModule();
        if (module == null) {
            sender.sendMessage(ChatColor.RED + "Resource Pack System no está disponible.");
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Construcción del pack iniciada…");
        module.reloadPack().thenAccept(result -> sender.sendMessage(PackReloadMessageFormatter.format(result)));
        return true;
    }

    private boolean reloadItems(CommandSender sender) {
        if (!sender.hasPermission(ITEMS_RELOAD_PERMISSION) && !sender.hasPermission(SHOP_RELOAD_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "No tenes permiso para recargar items.");
            return true;
        }
        var module = plugin.getItemCatalogModule();
        if (module == null) {
            sender.sendMessage(ChatColor.RED + "El catálogo de items no está disponible.");
            return true;
        }
        sender.sendMessage(ItemCatalogReloadMessageFormatter.format(module.reloadCatalog()));
        return true;
    }

    private boolean handleItems(CommandSender sender, String[] args) {
        var module = plugin.getItemCatalogModule();
        if (module == null) {
            sender.sendMessage(ChatColor.RED + "El catálogo de items no está disponible.");
            return true;
        }
        if (args.length == 1 && "info".equalsIgnoreCase(args[0])) {
            if (!requirePermission(sender, ITEMS_INFO_PERMISSION)) {
                return true;
            }
            Optional<dev.linqfy.bigCasares.items.catalog.CustomItemCatalog> catalog = plugin.getCustomItemRegistry().catalog();
            if (catalog.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No hay un catálogo de items activo.");
                return true;
            }
            var active = catalog.orElseThrow();
            sender.sendMessage(ChatColor.GOLD + "Catálogo BigCasares " + active.revision());
            sender.sendMessage(ChatColor.GRAY + "Definiciones activas: " + active.size());
            sender.sendMessage(ChatColor.GRAY + "Recarga activa: " + module.isCatalogReloadRunning());
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Uso: /bigcasares items info");
        return true;
    }

    private boolean handlePack(CommandSender sender, String[] args) {
        var module = plugin.getResourcePackModule();
        if (module == null) {
            sender.sendMessage(ChatColor.RED + "Resource Pack System no está disponible.");
            return true;
        }
        if (args.length == 1 && "info".equalsIgnoreCase(args[0])) {
            if (!requirePermission(sender, PACK_INFO_PERMISSION)) {
                return true;
            }
            Optional<ActivePackManifest> active = module.activeManifest();
            if (active.isEmpty()) {
                sender.sendMessage(ChatColor.YELLOW + "No hay un pack runtime activo.");
                return true;
            }
            ActivePackManifest manifest = active.orElseThrow();
            sender.sendMessage(ChatColor.GOLD + "Pack BigCasares " + manifest.version());
            sender.sendMessage(ChatColor.GRAY + "Java SHA-256: " + manifest.javaSha256());
            sender.sendMessage(ChatColor.GRAY + "URL: "
                + (manifest.javaUri() == null ? "copy-only" : manifest.javaUri()));
            sender.sendMessage(ChatColor.GRAY + "Construcción activa: " + module.isPackReloadRunning());
            sender.sendMessage(ChatColor.YELLOW + "Bedrock: requiere reinicio para aplicar cambios dinámicos.");
            return true;
        }
        if (args.length == 2 && "send".equalsIgnoreCase(args[0])) {
            if (!requirePermission(sender, PACK_SEND_PERMISSION)) {
                return true;
            }
            if ("all".equalsIgnoreCase(args[1])) {
                int sent = module.resendToAll();
                sender.sendMessage(ChatColor.GREEN + "Pack enviado a " + sent + " jugadores Java.");
                return true;
            }
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ChatColor.RED + "Jugador no encontrado o desconectado.");
                return true;
            }
            boolean sent = module.sendPack(target.getUniqueId());
            sender.sendMessage(sent
                ? ChatColor.GREEN + "Pack enviado a " + target.getName() + "."
                : ChatColor.YELLOW + "El pack no se envió; revisá plataforma y modo de publicación.");
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Uso: /bigcasares pack <info|send <jugador|all>>");
        return true;
    }

    private static void sendTeamUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Uso de /team:");
        sender.sendMessage(ChatColor.GRAY + "/team create <nombre> <tag> [color]");
        sender.sendMessage(ChatColor.GRAY + "/team invite <jugador> | join <tag> | leave");
        sender.sendMessage(ChatColor.GRAY + "/team kick <jugador> | dissolve");
        sender.sendMessage(ChatColor.GRAY + "/team rename <nombre> | tag <TAG> | color <COLOR>");
        sender.sendMessage(ChatColor.GRAY + "/team appearance [bold|italic|underline|strikethrough|wrapper] <valor>");
    }

    private boolean handleTeamManage(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bigcasares.team.admin") && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "No tenes permiso para administrar equipos.");
            return true;
        }
        var module = plugin.getTeamModule();
        var serviceOpt = module == null ? java.util.Optional.<dev.linqfy.bigCasares.modules.teams.TeamService>empty() : module.service();
        var presentationOpt = module == null ? java.util.Optional.<dev.linqfy.bigCasares.modules.teams.TeamPresentationService>empty() : module.presentationService();
        if (serviceOpt.isEmpty() || presentationOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Team System no esta disponible.");
            return true;
        }
        var service = serviceOpt.get();
        var presentation = presentationOpt.get();

        if (args.length < 2) {
            sendTeamManageUsage(sender);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String targetTag = args[1];
        Optional<Team> teamOpt = service.findByTag(targetTag);
        if (teamOpt.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No se encontró el equipo con tag " + targetTag + ".");
            return true;
        }
        Team team = teamOpt.get();
        UUID adminId = dev.linqfy.bigCasares.modules.teams.TeamService.ADMIN_ACTOR;

        try {
            if ("delete".equals(action)) {
                presentation.dissolveAndClear(team.id(), adminId);
                sender.sendMessage(ChatColor.GREEN + "Equipo " + targetTag + " eliminado.");
                return true;
            }
            if ("rename".equals(action) && args.length >= 3) {
                String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                presentation.renameAndRefresh(team.id(), adminId, name);
                sender.sendMessage(ChatColor.GREEN + "Equipo renombrado a " + name + ".");
                return true;
            }
            if ("tag".equals(action) && args.length == 3) {
                presentation.updateTagAndRefresh(team.id(), adminId, args[2]);
                sender.sendMessage(ChatColor.GREEN + "Tag actualizado a " + args[2] + ".");
                return true;
            }
            if ("color".equals(action) && args.length == 3) {
                TeamColor color = TeamColor.parse(args[2]);
                presentation.updateColorAndRefresh(team.id(), adminId, color);
                sender.sendMessage(ChatColor.GREEN + "Color actualizado a " + color.name() + ".");
                return true;
            }
            if ("kick".equals(action) && args.length == 3) {
                UUID targetId = findMemberByName(team, args[2]);
                presentation.removeMemberAndRefresh(team.id(), adminId, targetId);
                sender.sendMessage(ChatColor.GREEN + "Jugador " + args[2] + " expulsado de " + team.name() + ".");
                return true;
            }
        } catch (IllegalArgumentException | SecurityException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return true;
        }
        sendTeamManageUsage(sender);
        return true;
    }

    private static void sendTeamManageUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Uso de /teammanage:");
        sender.sendMessage(ChatColor.GRAY + "/teammanage delete <tag>");
        sender.sendMessage(ChatColor.GRAY + "/teammanage rename <tag> <nombre>");
        sender.sendMessage(ChatColor.GRAY + "/teammanage tag <tag> <nuevo_tag>");
        sender.sendMessage(ChatColor.GRAY + "/teammanage color <tag> <color>");
        sender.sendMessage(ChatColor.GRAY + "/teammanage kick <tag> <jugador>");
    }

    private boolean handleBoss(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden elegir la ubicación del boss.");
            return true;
        }
        if (!sender.hasPermission("bigcasares.boss.spawn")) {
            sender.sendMessage(ChatColor.RED + "No tenes permiso para crear bosses.");
            return true;
        }
        if (args.length != 2 || !"spawn".equalsIgnoreCase(args[0])
            || !isSupportedBossId(args[1])) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /boss spawn abyss-guardian|tung-tung-sahur");
            return true;
        }
        try {
            org.bukkit.util.Vector facing = player.getLocation().getDirection().setY(0.0);
            if (facing.lengthSquared() < 0.0001) {
                facing.setZ(1.0);
            }
            org.bukkit.Location location = player.getLocation().clone()
                .add(facing.normalize().multiply(6.0));
            java.util.UUID id = plugin.getPveBossModule().spawn(args[1], location);
            sender.sendMessage(ChatColor.DARK_PURPLE + "Boss creado: " + args[1] + " (" + id + ")");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
        }
        return true;
    }

    private boolean handleNexus(CommandSender sender, String[] args) {
        if (args.length >= 2 && "give".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("bigcasares.command.give")) {
                sender.sendMessage(ChatColor.RED + "No tenes permiso.");
                return true;
            }
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Jugador no encontrado.");
                return true;
            }
            var itemOpt = plugin.getCustomItemRegistry().findById("nexus");
            if (itemOpt.isPresent()) {
                target.getInventory().addItem(itemOpt.get().createItemStack(1)).values()
                    .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
                sender.sendMessage(ChatColor.GREEN + "Diste un Nexus a " + target.getName() + ".");
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden colocar o reclamar un Nexus.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /nexus <place|claim|give>");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if ("claim".equals(action)) {
            if (!player.hasPermission("bigcasares.nexus.claim")) {
                sender.sendMessage(ChatColor.RED + "No tenes permiso para reclamar un Nexus.");
                return true;
            }
            var teamService = plugin.getTeamModule().service().orElse(null);
            Team team = teamService == null ? null : teamService.findByMember(player.getUniqueId()).orElse(null);
            if (team == null) {
                sender.sendMessage(ChatColor.RED + "Necesitas pertenecer a un equipo.");
                return true;
            }
            var nexusModule = plugin.getNexusModule();
            if (nexusModule == null) {
                sender.sendMessage(ChatColor.RED + "Nexus System no está disponible.");
                return true;
            }
            if (nexusModule.hasActiveNexus(team.id())) {
                sender.sendMessage(ChatColor.RED + "Tu equipo ya tiene un Nexus activo.");
                return true;
            }
            if (nexusModule.hasNexusItemInTeam(team)) {
                sender.sendMessage(ChatColor.RED + "Alguien de tu equipo ya tiene el ítem del Nexus.");
                return true;
            }
            var customItem = plugin.getCustomItemRegistry().findById("nexus").orElse(null);
            if (customItem == null) {
                sender.sendMessage(ChatColor.RED + "El ítem del Nexus no está registrado.");
                return true;
            }
            var itemStack = customItem.createItemStack(1);
            var leftovers = player.getInventory().addItem(itemStack);
            if (!leftovers.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Tu inventario está lleno.");
                return true;
            }
            sender.sendMessage(ChatColor.GREEN + "¡Has reclamado el Nexus de tu equipo!");
            return true;
        } else if ("place".equals(action)) {
            if (!player.hasPermission("bigcasares.nexus.place")) {
                sender.sendMessage(ChatColor.RED + "No tenes permiso para colocar un Nexus.");
                return true;
            }
            var teamService = plugin.getTeamModule().service().orElse(null);
            Team team = teamService == null ? null : teamService.findByMember(player.getUniqueId()).orElse(null);
            if (team == null) {
                sender.sendMessage(ChatColor.RED + "Necesitas pertenecer a un equipo.");
                return true;
            }
            if (!team.ownerId().equals(player.getUniqueId())) {
                sender.sendMessage(ChatColor.RED + "Solo el OWNER puede colocar el Nexus del equipo.");
                return true;
            }
            var customItem = plugin.getCustomItemRegistry().findById("nexus").orElse(null);
            if (customItem == null) {
                sender.sendMessage(ChatColor.RED + "El ítem del Nexus no está registrado.");
                return true;
            }
            if (!hasNexusItem(player, customItem)) {
                sender.sendMessage(ChatColor.RED + "Necesitas tener el ítem del Nexus en tu inventario para colocarlo.");
                return true;
            }
            org.bukkit.block.Block target = player.getTargetBlockExact(8);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Mira un bloque libre a menos de 8 bloques.");
                return true;
            }
            org.bukkit.Location location = target.getLocation().add(0.5, 1.0, 0.5);
            try {
                var attempt = plugin.getNexusModule().placeNexus(location, team);
                if (attempt.result().allowed()) {
                    consumeOneNexusItem(player, customItem);
                    sender.sendMessage(ChatColor.AQUA + "Nexus colocado para [" + team.tag() + "]: " + attempt.nexusId());
                } else {
                    String blocker;
                    if (attempt.result().rejection() == dev.linqfy.bigCasares.modules.nexus.NexusPlacementRejection.PROTECTED_NEXUS_VOLUME) {
                        blocker = "Demasiado cerca de otro Nexus";
                    } else {
                        blocker = attempt.result().blockingMaterial().orElse(attempt.result().rejection().name());
                    }
                    sender.sendMessage(ChatColor.RED + "No se puede colocar el Nexus: " + blocker + ".");
                }
            } catch (IllegalArgumentException | IllegalStateException ex) {
                sender.sendMessage(ChatColor.RED + ex.getMessage());
            }
            return true;
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /nexus <place|claim|give>");
            return true;
        }
    }

    private boolean hasNexusItem(Player player, dev.linqfy.bigCasares.items.CustomItem item) {
        for (org.bukkit.inventory.ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && item.matches(stack)) {
                return true;
            }
        }
        org.bukkit.inventory.ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && item.matches(cursor)) {
            return true;
        }
        return false;
    }

    private void consumeOneNexusItem(Player player, dev.linqfy.bigCasares.items.CustomItem item) {
        org.bukkit.inventory.ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && item.matches(mainHand)) {
            mainHand.setAmount(mainHand.getAmount() - 1);
            return;
        }
        org.bukkit.inventory.ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && item.matches(offHand)) {
            offHand.setAmount(offHand.getAmount() - 1);
            return;
        }
        org.bukkit.inventory.ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && item.matches(cursor)) {
            cursor.setAmount(cursor.getAmount() - 1);
            return;
        }
        org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            org.bukkit.inventory.ItemStack stack = contents[i];
            if (stack != null && item.matches(stack)) {
                stack.setAmount(stack.getAmount() - 1);
                player.getInventory().setItem(i, stack.getAmount() > 0 ? stack : null);
                return;
            }
        }
    }

    private boolean openShop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden abrir el shop.");
            return true;
        }
        if (!sender.hasPermission(SHOP_OPEN_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "No tenes permiso para abrir el shop.");
            return true;
        }
        plugin.openShop(player);
        return true;
    }

    private boolean handleManualResourcePack(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden usar /resourcepack.");
            return true;
        }
        var module = plugin.getResourcePackModule();
        if (module == null) {
            sender.sendMessage(ChatColor.RED + "Resource Pack System no está disponible.");
            return true;
        }
        List<dev.linqfy.bigCasares.modules.resourcepack.JavaPackDelivery> downloads =
            module.enableManualDownloads(player);
        if (downloads.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No hay un resource pack HTTP disponible ahora.");
            return true;
        }
        dev.linqfy.bigCasares.modules.resourcepack.ManualResourcePackMessages
            .sendDownloads(player, downloads);
        return true;
    }

    private boolean handleTombstone(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Solo jugadores pueden abrir tumbas.");
            return true;
        }
        if (args.length != 1 || !"abrir".equalsIgnoreCase(args[0])) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /tumba abrir");
            return true;
        }
        var module = plugin.getTombstoneModule();
        if (module == null) {
            sender.sendMessage(ChatColor.RED + "El sistema de tumbas no está disponible.");
            return true;
        }
        if (!module.openNearby(player, 3.0)) {
            sender.sendMessage(ChatColor.RED + "No hay ninguna tumba a 3 bloques.");
        }
        return true;
    }

    private boolean showDanger(CommandSender sender, String[] args) {
        if (!sender.hasPermission(DANGER_VIEW_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "No tenés permiso para consultar el peligro.");
            return true;
        }
        if (plugin.getDangerModule() == null || plugin.getDangerModule().service().isEmpty()) {
            sender.sendMessage(ChatColor.RED + "El sistema de peligro no está disponible.");
            return true;
        }
        var danger = plugin.getDangerModule().service().orElseThrow();
        boolean expand = args.length > 0 && "expandir".equalsIgnoreCase(args[0]);
        String requested = expand ? (args.length > 1 ? args[1] : null) : (args.length > 0 ? args[0] : null);
        OfflinePlayer target = requested == null && sender instanceof Player player
            ? player : requested == null ? null : plugin.getServer().getOfflinePlayer(requested);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Uso: /peligro [jugador] o /peligro expandir [jugador]");
            return true;
        }
        if (!target.getUniqueId().equals(sender instanceof Player player ? player.getUniqueId() : null)
            && !sender.hasPermission(DANGER_VIEW_OTHERS_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "No tenés permiso para consultar a otros jugadores.");
            return true;
        }
        var snapshot = danger.snapshot(target.getUniqueId());
        String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        sender.sendMessage(ChatColor.GOLD + "Peligro de " + name + ": " + ChatColor.WHITE + snapshot.totalScore()
            + ChatColor.GRAY + "/100 - " + snapshot.tier().displayName());
        sender.sendMessage(ChatColor.GRAY + "Actividad: " + snapshot.activityScore()
            + " | Aporte de habilidad: " + snapshot.skillContribution());
        if (expand && plugin.getSkillRatingModule() != null && plugin.getSkillRatingModule().service() != null) {
            var state = plugin.getSkillRatingModule().service().ratingFor(target.getUniqueId());
            sender.sendMessage(ChatColor.AQUA + "Skill Rating: " + state.skillRating() + " | Tier " + state.tier());
            sender.sendMessage(ChatColor.GRAY + "Mu: " + state.mu() + " | Sigma: " + state.sigma()
                + " | Actualizado: " + state.updatedAt());
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                return true;
            }
            sender.sendMessage(ChatColor.GOLD + "--- Top 10 de Peligro ---");
            var top = danger.top(10);
            int rank = 1;
            for (var standing : top) {
                String standingName = plugin.getServer().getOfflinePlayer(standing.playerId()).getName();
                if (standingName == null) standingName = "Desconocido";
                sender.sendMessage(ChatColor.YELLOW + String.valueOf(rank) + ". " + standingName
                    + ChatColor.GRAY + " - " + standing.totalScore() + " (" + standing.tier().displayName() + ")");
                rank++;
            }
        }
        return true;
    }

    private boolean delegateToMisionesHandler(CommandSender sender, Command command, String label, String[] args) {
        String[] cmdArgs = new String[args.length + 1];
        cmdArgs[0] = "misiones";
        System.arraycopy(args, 0, cmdArgs, 1, args.length);
        return legacyCommand.onCommand(sender, command, label, cmdArgs);
    }

    private String[] dropFirst(String[] args) {
        if (args.length <= 1) {
            return new String[0];
        }
        String[] result = new String[args.length - 1];
        System.arraycopy(args, 1, result, 0, result.length);
        return result;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "Usage:");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " give <item_id> [player] [amount]");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " misiones [diarias|semanales|reclamar]");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " shop");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " peligro [jugador]");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " team <create|invite|join|leave|kick|dissolve|tag|color|appearance>");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " boss spawn abyss-guardian|tung-tung-sahur");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " nexus <place|claim|give>");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " tumba abrir");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " baltop");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " reload");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " reload pack");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " reload items");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " pack <info|send <jugador|all>>");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " items info");
    }

    private boolean handleBaltop(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Calculando el top de balances...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                net.milkbowl.vault.economy.Economy economy = dev.linqfy.bigCasares.modules.missions.VaultEconomyGateway.resolveOrThrow(plugin);
                java.util.List<java.util.Map.Entry<String, Double>> top = new java.util.ArrayList<>();
                for (org.bukkit.OfflinePlayer p : plugin.getServer().getOfflinePlayers()) {
                    if (p.getName() != null && p.hasPlayedBefore()) {
                        top.add(new java.util.AbstractMap.SimpleEntry<>(p.getName(), economy.getBalance(p)));
                    }
                }
                top.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
                java.util.List<java.util.Map.Entry<String, Double>> finalTop = top.subList(0, Math.min(10, top.size()));
                
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.GOLD + "--- Top 10 Balances ---");
                    int rank = 1;
                    for (var entry : finalTop) {
                        String formatted = dev.linqfy.bigCasares.modules.missions.VaultEconomyGateway.formatFallback(entry.getValue(), "$");
                        try {
                            formatted = economy.format(entry.getValue());
                        } catch (Exception ignored) {}
                        sender.sendMessage(ChatColor.YELLOW + String.valueOf(rank) + ". " + entry.getKey() + ChatColor.GRAY + " - " + ChatColor.GREEN + formatted);
                        rank++;
                    }
                });
            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(ChatColor.RED + "No se pudo obtener el baltop. Vault no esta disponible.");
                });
            }
        });
        return true;
    }
}
