package dev.linqfy.bigCasares.modules.dimension;

import dev.linqfy.bigCasares.dimension.DimensionDefinition;
import dev.linqfy.bigCasares.dimension.DimensionKey;
import dev.linqfy.bigCasares.dimension.DimensionStatus;
import dev.linqfy.bigCasares.dimension.environment.EnvironmentCycle;
import dev.linqfy.bigCasares.dimension.event.DimensionTravelEvent;
import dev.linqfy.bigCasares.dimension.runtime.DimensionExtension;
import dev.linqfy.bigCasares.dimension.travel.TravelResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /dimension <list|info|tp|leave|locate|phase|pack|export>} — operator tooling for
 * the dimension framework.
 */
final class DimensionCommand implements CommandExecutor, TabCompleter {

    static final String ADMIN = "bigcasares.dimension.admin";
    static final String LEAVE = "bigcasares.dimension.leave";

    private final DimensionRuntime runtime;
    private final Path dataFolder;

    DimensionCommand(DimensionRuntime runtime, Path dataFolder) {
        this.runtime = runtime;
        this.dataFolder = dataFolder;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("leave")) {
            return leave(sender, args);
        }
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage("§cNo tenés permiso.");
            return true;
        }
        switch (sub) {
            case "list" -> list(sender);
            case "info" -> withKey(sender, args, 1, key -> info(sender, key));
            case "tp" -> withKey(sender, args, 1, key -> teleport(sender, key, args));
            case "phase" -> withKey(sender, args, 1, key -> phase(sender, key, args));
            case "locate" -> locate(sender, args);
            case "pack" -> pack(sender);
            case "export" -> export(sender);
            default -> sender.sendMessage("§eUso: /" + label + " <list|info|tp|leave|locate|phase|pack|export>");
        }
        return true;
    }

    private void list(CommandSender sender) {
        if (runtime.definitions().isEmpty()) {
            sender.sendMessage("§7No hay dimensiones registradas.");
            return;
        }
        for (DimensionDefinition definition : runtime.definitions()) {
            DimensionStatus status = runtime.status(definition.key());
            sender.sendMessage("§b" + definition.key() + " §7(" + definition.displayName() + ") §f" + status.state()
                + (status.restartRecommended() ? " §6[reinicio recomendado]" : ""));
        }
    }

    private void info(CommandSender sender, DimensionKey key) {
        Optional<DimensionDefinition> definition = runtime.definition(key);
        if (definition.isEmpty()) {
            sender.sendMessage("§cDimensión desconocida: " + key);
            return;
        }
        DimensionStatus status = runtime.status(key);
        sender.sendMessage("§b" + key + " §7- " + status.state() + " §8" + status.message());
        runtime.world(key).ifPresent(world -> sender.sendMessage("§7Mundo: §f" + world.getName()
            + " §7jugadores: §f" + world.getPlayers().size() + " §7reloj: §f" + world.getFullTime()));
        for (EnvironmentCycle cycle : definition.get().cycles()) {
            runtime.cycleSnapshot(key, cycle.id()).ifPresent(snapshot -> sender.sendMessage("§7Ciclo §f" + cycle.id()
                + "§7: fase §a" + snapshot.phase().id() + " §7(" + snapshot.ticksUntilNextPhase() + " ticks restantes)"));
        }
    }

    private void teleport(CommandSender sender, DimensionKey key, String[] args) {
        Player target = resolvePlayer(sender, args, 2);
        if (target == null) {
            return;
        }
        runtime.enter(target, key, DimensionTravelEvent.Cause.COMMAND).thenAccept(result -> report(sender, result));
    }

    private boolean leave(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission(ADMIN)) {
                sender.sendMessage("§cNo tenés permiso.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
        } else {
            if (!sender.hasPermission(LEAVE) && !sender.hasPermission(ADMIN)) {
                sender.sendMessage("§cNo tenés permiso.");
                return true;
            }
            target = sender instanceof Player player ? player : null;
        }
        if (target == null) {
            sender.sendMessage("§cJugador no encontrado.");
            return true;
        }
        runtime.leave(target, DimensionTravelEvent.Cause.COMMAND).thenAccept(result -> report(sender, result));
        return true;
    }

    /** {@code /dimension locate <dimension> <landmark>}: the dimension extensions' landmarks (the Sift's main portal). */
    private void locate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eUso: /dimension locate <dimensión> <hito>");
            return;
        }
        withKey(sender, args, 1, key -> {
            Optional<DimensionDefinition> definition = runtime.definition(key);
            Optional<ActiveDimensionHandle> active = runtime.active(key);
            if (definition.isEmpty() || active.isEmpty()) {
                sender.sendMessage("§cDimensión desconocida o inactiva: " + key);
                return;
            }
            Location from = sender instanceof Player player ? player.getLocation() : active.get().world().getSpawnLocation();
            for (DimensionExtension extension : definition.get().extensions()) {
                if (!extension.landmarks().contains(args[2])) {
                    continue;
                }
                Optional<Location> found = extension.locate(active.get(), args[2], from);
                if (found.isEmpty()) {
                    sender.sendMessage("§e" + args[2] + " todavía no existe en " + key
                        + " (se está generando o no pudo generarse); volvé a probar en unos minutos.");
                    return;
                }
                sender.sendMessage(located(args[2], found.get(), from));
                return;
            }
            sender.sendMessage("§cHito desconocido en " + key + ": " + args[2]);
        });
    }

    private static Component located(String landmark, Location at, Location from) {
        String teleport = "/tp @s " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ();
        Component coordinates = Component.text("[" + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ() + "]",
            NamedTextColor.GREEN).clickEvent(ClickEvent.suggestCommand(teleport));
        Component message = Component.text(landmark + " está en ", NamedTextColor.GRAY).append(coordinates);
        if (from.getWorld() != null && from.getWorld().equals(at.getWorld())) {
            long distance = Math.round(Math.hypot(at.getX() - from.getX(), at.getZ() - from.getZ()));
            message = message.append(Component.text(" (a " + distance + " bloques)", NamedTextColor.GRAY));
        }
        return message;
    }

    private void phase(CommandSender sender, DimensionKey key, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§eUso: /dimension phase <dimensión> <ciclo> <fase>");
            return;
        }
        Optional<World> world = runtime.world(key);
        Optional<EnvironmentCycle> cycle = runtime.definition(key)
            .flatMap(def -> def.cycles().stream().filter(c -> c.id().equals(args[2])).findFirst());
        if (world.isEmpty() || cycle.isEmpty() || cycle.get().phase(args[3]).isEmpty()) {
            sender.sendMessage("§cDimensión inactiva, ciclo o fase desconocidos.");
            return;
        }
        long next = cycle.get().nextStartOf(args[3], world.get().getFullTime());
        world.get().setFullTime(next);
        sender.sendMessage("§aReloj de " + key + " movido a la fase " + args[3] + " (t=" + next + ").");
    }

    private void pack(CommandSender sender) {
        runtime.lastPack().ifPresentOrElse(pack -> {
            sender.sendMessage("§7Datapack: §f" + pack.size() + " archivos §7hash §f" + pack.contentHash().substring(0, 16));
            sender.sendMessage("§7Instalado en el mundo: §f" + (runtime.datapackPresent() ? "sí" : "no")
                + " §7(" + runtime.levelDatapacks() + ")");
        }, () -> sender.sendMessage("§7Todavía no se generó ningún datapack."));
    }

    private void export(CommandSender sender) {
        Optional<dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack> pack = runtime.lastPack();
        if (pack.isEmpty()) {
            sender.sendMessage("§7No hay datapack para exportar.");
            return;
        }
        Path output = dataFolder.resolve("dimensions/bigcasares-dimensions.zip");
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(output.getParent());
                try (OutputStream stream = Files.newOutputStream(output)) {
                    pack.get().writeZip(stream);
                }
                sender.sendMessage("§aDatapack exportado a " + output);
            } catch (IOException exception) {
                sender.sendMessage("§cNo se pudo exportar: " + exception.getMessage());
            }
        });
    }

    private void report(CommandSender sender, TravelResult result) {
        if (result.succeeded()) {
            sender.sendMessage("§aViaje completado: " + result.message());
        } else {
            sender.sendMessage("§cViaje fallido (" + result.status().name().toLowerCase(Locale.ROOT) + "): " + result.message());
        }
    }

    private Player resolvePlayer(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player player = Bukkit.getPlayerExact(args[index]);
            if (player == null) {
                sender.sendMessage("§cJugador no encontrado: " + args[index]);
            }
            return player;
        }
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage("§cIndicá un jugador.");
        return null;
    }

    private void withKey(CommandSender sender, String[] args, int index, java.util.function.Consumer<DimensionKey> action) {
        if (args.length <= index) {
            sender.sendMessage("§cIndicá una dimensión (namespace:path).");
            return;
        }
        try {
            action.accept(DimensionKey.parse(args[index]));
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("§cClave de dimensión inválida: " + args[index]);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("list", "info", "tp", "leave", "locate", "phase", "pack", "export"));
        } else if (args.length == 2 && List.of("info", "tp", "phase", "locate").contains(args[0].toLowerCase(Locale.ROOT))) {
            runtime.definitions().forEach(definition -> options.add(definition.key().asString()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("locate")) {
            try {
                runtime.definition(DimensionKey.parse(args[1]))
                    .ifPresent(def -> def.extensions().forEach(extension -> options.addAll(extension.landmarks())));
            } catch (IllegalArgumentException ignored) {
                // Invalid key: no suggestions.
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("phase")) {
            try {
                runtime.definition(DimensionKey.parse(args[1]))
                    .ifPresent(def -> def.cycles().forEach(cycle -> options.add(cycle.id())));
            } catch (IllegalArgumentException ignored) {
                // Invalid key: no suggestions.
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("phase")) {
            try {
                runtime.definition(DimensionKey.parse(args[1])).flatMap(def -> def.cycles().stream()
                        .filter(cycle -> cycle.id().equals(args[2])).findFirst())
                    .ifPresent(cycle -> cycle.phases().forEach(phase -> options.add(phase.id())));
            } catch (IllegalArgumentException ignored) {
                // Invalid key: no suggestions.
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("tp")) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
