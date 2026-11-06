package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.BigCasares;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

final class LegendaryWeaponsCommand implements CommandExecutor, TabCompleter {
    private final BigCasares plugin;
    private final LegendaryStateStore store;
    private final LegendaryCraftingEvents events;
    private final LegendaryPvpLevels levels;

    LegendaryWeaponsCommand(
            BigCasares plugin,
            LegendaryStateStore store,
            LegendaryCraftingEvents events,
            LegendaryPvpLevels levels) {
        this.plugin = plugin;
        this.store = store;
        this.events = events;
        this.levels = levels;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "help" -> {
                    sender.sendMessage("§6/weapons list | info <arma> | events");
                    sender.sendMessage(
                            "§eColoca los nueve ingredientes en una mesa vanilla: comenzará un"
                                + " evento PvP de 20 minutos.");
                    sender.sendMessage(
                            "§eZonas a los 5/10 minutos; coordenadas a los 15. El primer clic a los"
                                + " 20 gana.");
                    sender.sendMessage(
                            "§eConsulta /weapons info <arma> para los controles. Cada baja de"
                                + " jugador con un arma suma un nivel, hasta 5; se conserva al"
                                + " morir.");
                    sender.sendMessage(
                            "§7Administración: /weapons level <jugador> <arma> <0-5> | cooldown"
                                + " reset <jugador> | reload");
                }
                case "list", "events" -> {
                    for (var weapon : LegendaryWeapon.available()) {
                        var session = events.ledger().find(weapon);
                        String status = session.map(s -> s.stage().name()).orElse("AVAILABLE");
                        long seconds =
                                session.map(
                                                s ->
                                                        (s.remaining(System.currentTimeMillis())
                                                                        + 999)
                                                                / 1000)
                                        .orElse(0L);
                        String progress =
                                sender instanceof org.bukkit.entity.Player player
                                        ? " · nivel "
                                                + levels.level(player.getUniqueId(), weapon)
                                                + "/5"
                                        : "";
                        sender.sendMessage(
                                "§b"
                                        + weapon.id
                                        + " §7— "
                                        + status
                                        + (status.equals("CRAFTING") ? " · " + seconds + " s" : "")
                                        + progress);
                    }
                }
                case "info" -> {
                    if (args.length != 2)
                        throw new IllegalArgumentException("Uso: /weapons info <arma>");
                    var weapon =
                            LegendaryWeapon.find(args[1])
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Arma desconocida."));
                    sender.sendMessage("§6" + weapon.displayName + " — " + weapon.abilityName);
                    sender.sendMessage("§b" + weapon.abilityDescription());
                    if (sender instanceof org.bukkit.entity.Player player)
                        sender.sendMessage(
                                "§aTu nivel: "
                                        + levels.level(player.getUniqueId(), weapon)
                                        + "/5. Las habilidades desbloqueadas se conservan al subir"
                                        + " de nivel.");
                    sender.sendMessage(
                            "§eReceta sin forma en mesa vanilla; una unidad de ingrediente por"
                                + " casilla:");
                    weapon.ingredients.forEach(
                            (material, amount) ->
                                    sender.sendMessage("§7" + amount + " × " + material));
                }
                case "level" -> {
                    if (!sender.hasPermission("bigcasares.weapons.admin"))
                        throw new IllegalArgumentException("No tienes permiso.");
                    if (args.length != 4)
                        throw new IllegalArgumentException(
                                "Uso: /weapons level <jugador> <arma> <0-5>");
                    var player = plugin.getServer().getOfflinePlayer(args[1]);
                    if (!player.hasPlayedBefore() && !player.isOnline())
                        throw new IllegalArgumentException("Jugador desconocido.");
                    var weapon =
                            LegendaryWeapon.find(args[2])
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Arma desconocida."));
                    levels.set(player.getUniqueId(), weapon, Integer.parseInt(args[3]));
                    sender.sendMessage(
                            "§a"
                                    + weapon.displayName
                                    + " — nivel "
                                    + levels.level(player.getUniqueId(), weapon)
                                    + "/5 guardado para "
                                    + player.getName()
                                    + ".");
                }
                case "reload", "cooldown" -> {
                    if (!sender.hasPermission("bigcasares.weapons.admin"))
                        throw new IllegalArgumentException("No tienes permiso.");
                    if (action.equals("reload")) {
                        plugin.reloadConfig();
                        sender.sendMessage(
                                "§aConfiguración recargada; eventos, zonas y cooldowns conservados."
                                    + " El pack se administra con /bigcasares pack.");
                    } else {
                        if (args.length != 3 || !args[1].equalsIgnoreCase("reset"))
                            throw new IllegalArgumentException(
                                    "Uso: /weapons cooldown reset <jugador>");
                        var player = plugin.getServer().getOfflinePlayer(args[2]);
                        if (!player.hasPlayedBefore() && !player.isOnline())
                            throw new IllegalArgumentException("Jugador desconocido.");
                        store.resetCooldowns(player.getUniqueId());
                        sender.sendMessage("§aCooldowns reiniciados.");
                    }
                }
                default ->
                        throw new IllegalArgumentException(
                                "Usa /weapons help. Las armas se obtienen mediante el evento de"
                                    + " mesa, sin altares ni entrega manual.");
            }
        } catch (IllegalArgumentException | java.io.IOException failure) {
            sender.sendMessage(
                    "§c"
                            + (failure instanceof java.io.IOException
                                    ? "No se pudo guardar el cambio."
                                    : failure.getMessage()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> options =
                args.length == 1
                        ? List.of("help", "list", "info", "events", "reload", "cooldown", "level")
                        : args.length == 2 && args[0].equalsIgnoreCase("info")
                                ? LegendaryWeapon.available().stream().map(w -> w.id).toList()
                                : args.length == 3 && args[0].equalsIgnoreCase("level")
                                        ? LegendaryWeapon.available().stream()
                                                .map(w -> w.id)
                                                .toList()
                                        : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.startsWith(prefix)).toList();
    }
}
