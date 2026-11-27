package dev.linqfy.bigCasares.modules.alexscaves;

import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectType;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.WorldgenService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /alexscaves}: credits for everyone, administration for operators. */
final class AlexsCavesCommand implements CommandExecutor, TabCompleter {
    static final String ADMIN = "bigcasares.alexscaves.admin";

    private final AlexsCavesModule module;

    AlexsCavesCommand(AlexsCavesModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "ayuda" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "creditos", "créditos", "credits" -> AlexsCavesCredits.lines().forEach(sender::sendMessage);
            case "info" -> {
                if (requireAdmin(sender)) {
                    module.infoLines().forEach(sender::sendMessage);
                }
            }
            case "efecto" -> effect(sender, args);
            case "spawn" -> spawn(sender, args);
            case "buscar", "locate" -> locate(sender, args);
            case "remap" -> remap(sender, args);
            case "items" -> items(sender, args);
            case "give", "dar" -> give(sender, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void items(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "El menú de items es solo para jugadores; usá /alexscaves give.");
            return;
        }
        int page = parse(args, 1, 1);
        module.itemMenu().ifPresentOrElse(menu -> menu.open(player, Math.max(page, 1) - 1),
            () -> sender.sendMessage(ChatColor.RED + "El módulo no está activo."));
    }

    private void give(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Uso: /alexscaves give <item> [cantidad] [jugador]");
            return;
        }
        int amount = parse(args, 2, 1);
        if (amount < 1 || amount > 2304) {
            sender.sendMessage(ChatColor.RED + "Cantidad entre 1 y 2304.");
            return;
        }
        Player target = args.length > 3 ? sender.getServer().getPlayerExact(args[3])
            : sender instanceof Player player ? player : null;
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Jugador no encontrado.");
            return;
        }
        boolean given = module.itemMenu().map(menu -> menu.give(target, args[1], amount)).orElse(false);
        sender.sendMessage(given ? ChatColor.GREEN + "Diste " + amount + " " + args[1] + " a " + target.getName() + "."
            : ChatColor.RED + "Item desconocido o módulo inactivo: " + args[1]);
    }

    private void effect(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Uso: /alexscaves efecto <jugador> <efecto> [nivel] [segundos]");
            return;
        }
        Player target = sender.getServer().getPlayerExact(args[1]);
        ModEffectType type = ModEffectType.parse(args[2]).orElse(null);
        if (target == null || type == null) {
            sender.sendMessage(ChatColor.RED + "Jugador o efecto desconocido.");
            return;
        }
        int level = parse(args, 3, 1);
        int seconds = parse(args, 4, 30);
        if (level < 1 || level > 10 || seconds < 1 || seconds > 3600) {
            sender.sendMessage(ChatColor.RED + "Nivel entre 1 y 10, segundos entre 1 y 3600.");
            return;
        }
        boolean applied = module.effects().map(effects -> effects.apply(target, type, level - 1, seconds * 20)).orElse(false);
        sender.sendMessage(applied
            ? ChatColor.GREEN + "Aplicado " + type.displayName() + " " + level + " a " + target.getName() + "."
            : ChatColor.YELLOW + "No se aplicó (desactivado o ya tiene uno más fuerte).");
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        Location at = spawnLocation(sender, args);
        if (at == null) {
            sender.sendMessage(ChatColor.RED + "Uso: /alexscaves spawn <mob> (en el juego) o /alexscaves spawn <mob> <x> <y> <z> [mundo]");
            return;
        }
        boolean spawned = module.mobs().flatMap(mobs -> mobs.spawn(args[1].toLowerCase(Locale.ROOT), at)).isPresent();
        sender.sendMessage(spawned ? ChatColor.GREEN + "Apareció " + args[1] + "."
            : ChatColor.RED + "Mob desconocido o módulo inactivo: " + args[1]);
    }

    /** Where to spawn: the player, or the given coordinates (from the console too; world: the sender's or the first). */
    private Location spawnLocation(CommandSender sender, String[] args) {
        Location here = sender instanceof Player player ? player.getLocation()
            : sender instanceof org.bukkit.command.BlockCommandSender block ? block.getBlock().getLocation().add(0.5, 1, 0.5) : null;
        if (args.length == 2) {
            return here;
        }
        if (args.length != 5 && args.length != 6) {
            return null;
        }
        World world = args.length == 6 ? module.plugin().getServer().getWorld(args[5])
            : here != null ? here.getWorld() : module.plugin().getServer().getWorlds().get(0);
        if (world == null) {
            return null;
        }
        double[] at = new double[3];
        double[] base = here == null ? new double[] {0, 0, 0} : new double[] {here.getX(), here.getY(), here.getZ()};
        for (int i = 0; i < 3; i++) {
            String arg = args[2 + i];
            try {
                // "~" and "~n" are relative to the sender, as in vanilla commands.
                at[i] = arg.startsWith("~") ? (here == null ? Double.NaN : base[i] + (arg.length() > 1 ? Double.parseDouble(arg.substring(1)) : 0))
                    : Double.parseDouble(arg);
            } catch (NumberFormatException invalid) {
                return null;
            }
            if (!Double.isFinite(at[i])) {
                return null;
            }
        }
        return new Location(world, at[0], at[1], at[2]);
    }

    /** Finds the nearest column of a cave biome and takes the player into its cave (generating the chunk if needed). */
    private void locate(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        CaveBiomeId biome = args.length < 2 ? null : CaveBiomeId.parse(args[1]).orElse(null);
        if (!(sender instanceof Player player) || biome == null) {
            sender.sendMessage(ChatColor.RED + "Uso (en el juego): /alexscaves buscar <bioma>");
            return;
        }
        WorldgenService worldgen = module.worldgen().orElse(null);
        if (worldgen == null || !worldgen.active()) {
            sender.sendMessage(ChatColor.RED + "La generación de biomas está apagada (¿falta reiniciar para cargar el datapack? Mirá /ac info).");
            return;
        }
        World world = player.getWorld();
        if (!worldgen.generates(world)) {
            sender.sendMessage(ChatColor.RED + "Este mundo no genera biomas de Alex's Caves (mirá worlds en alexs-caves.yml).");
            return;
        }
        Location from = player.getLocation();
        sender.sendMessage(ChatColor.GRAY + "Buscando " + biome.path() + "...");
        BukkitScheduler scheduler = module.plugin().getServer().getScheduler();
        // The search samples thousands of columns: off the main thread, then the chunk loads asynchronously.
        scheduler.runTaskAsynchronously(module.plugin(), () -> {
            int[] found = worldgen.nearest(world, biome, from.getBlockX(), from.getBlockZ()).orElse(null);
            scheduler.runTask(module.plugin(), () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (found == null) {
                    player.sendMessage(ChatColor.YELLOW + "No encontré " + biome.path() + " a menos de 6000 bloques.");
                    return;
                }
                world.getChunkAtAsync(found[0] >> 4, found[1] >> 4).thenAccept(chunk -> {
                    if (player.isOnline()) {
                        teleportInto(player, world, biome, found[0], found[1], from);
                    }
                });
            });
        });
    }

    /** Takes the player to a cave spot of the column inside the biome's depth band, or above it if there is none. */
    private static void teleportInto(Player player, World world, CaveBiomeId biome, int x, int z, Location from) {
        int surface = world.getHighestBlockYAt(x, z);
        int top = surface - (int) Math.floor(biome.minDepth() * 128.0);
        int bottom = Math.max(world.getMinHeight() + 5, surface - (int) Math.ceil(biome.maxDepth() * 128.0));
        Location target = null;
        // Two air blocks over a solid floor that is not lava or magma.
        for (int y = top; y > bottom && target == null; y--) {
            Material floor = world.getBlockAt(x, y, z).getType();
            if (floor.isSolid() && floor != Material.MAGMA_BLOCK && world.getBlockAt(x, y + 1, z).getType().isAir()
                && world.getBlockAt(x, y + 2, z).getType().isAir()) {
                target = new Location(world, x + 0.5, y + 1, z + 0.5, from.getYaw(), from.getPitch());
            }
        }
        if (target == null) {
            target = new Location(world, x + 0.5, surface + 1, z + 0.5, from.getYaw(), from.getPitch());
            player.sendMessage(ChatColor.YELLOW + "No hay cueva abierta justo ahí; te dejo arriba. Cavá hacia abajo (el bioma empieza unos "
                + (int) Math.floor(biome.minDepth() * 128.0) + " bloques bajo la superficie).");
        }
        Location destination = target;
        player.teleportAsync(destination).thenAccept(done -> {
            if (done) {
                player.sendMessage(ChatColor.GREEN + biome.path() + " en " + x + ", " + destination.getBlockY() + ", " + z + ".");
            }
        });
    }

    /** Debug of the packet remap: counters, {@code reset}, or {@code bench [radius] [rounds]} around the sender. */
    private void remap(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        var service = module.packetRemap().orElse(null);
        if (service == null) {
            sender.sendMessage(ChatColor.RED + "Alex's Caves no está activo.");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (action.equals("reset")) {
            service.resetStats();
            sender.sendMessage(ChatColor.GREEN + "Contadores de la reescritura por paquetes en cero.");
        } else if (action.equals("bench")) {
            Location center = sender instanceof Player player ? player.getLocation()
                : sender.getServer().getWorlds().get(0).getSpawnLocation();
            if (args.length >= 6) {
                center = new Location(center.getWorld(), parse(args, 4, center.getBlockX()), 0, parse(args, 5, center.getBlockZ()));
            }
            int radius = Math.max(0, Math.min(16, parse(args, 2, 4)));
            int rounds = Math.max(1, Math.min(50, parse(args, 3, 5)));
            service.bench(center, radius, rounds).forEach(line -> sender.sendMessage("§7" + line));
        } else {
            service.statusLines().forEach(sender::sendMessage);
        }
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("§6§lAlex's Caves §7(recreación no oficial)");
        sender.sendMessage("§e/" + label + " creditos §7- créditos del mod original");
        if (sender.hasPermission(ADMIN)) {
            sender.sendMessage("§e/" + label + " info §7- estado del módulo y de los biomas");
            sender.sendMessage("§e/" + label + " efecto <jugador> <efecto> [nivel] [segundos] §7- aplicar un efecto");
            sender.sendMessage("§e/" + label + " spawn <mob> [x y z [mundo]] §7- hacer aparecer un mob del módulo");
            sender.sendMessage("§e/" + label + " buscar <bioma> §7- ir a la cueva más cercana de un bioma");
            sender.sendMessage("§e/" + label + " items [página] §7- menú con todos los items (shift: un stack)");
            sender.sendMessage("§e/" + label + " give <item> [cantidad] [jugador] §7- dar un item del módulo");
            sender.sendMessage("§e/" + label + " remap [reset|bench [radio] [rondas] [x z]] §7- bloques por paquetes (debug)");
        }
        sender.sendMessage("§8" + AlexsCavesCredits.shortLine());
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + "No tenés permiso para usar este comando.");
        return false;
    }

    private static boolean isGive(String action) {
        return action.equalsIgnoreCase("give") || action.equalsIgnoreCase("dar");
    }

    private static int parse(String[] args, int index, int fallback) {
        if (args.length <= index) {
            return fallback;
        }
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("creditos");
            if (sender.hasPermission(ADMIN)) {
                options.add("info");
                options.add("efecto");
                options.add("spawn");
                options.add("buscar");
                options.add("remap");
                options.add("items");
                options.add("give");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("buscar") && sender.hasPermission(ADMIN)) {
            for (CaveBiomeId biome : CaveBiomeId.values()) {
                options.add(biome.path());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("efecto") && sender.hasPermission(ADMIN)) {
            sender.getServer().getOnlinePlayers().forEach(player -> options.add(player.getName()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("spawn") && sender.hasPermission(ADMIN)) {
            module.mobs().ifPresent(mobs -> mobs.knownIds().forEach(options::add));
        } else if (args.length == 2 && isGive(args[0]) && sender.hasPermission(ADMIN)) {
            module.itemMenu().ifPresent(menu -> menu.ids().forEach(options::add));
        } else if (args.length == 4 && isGive(args[0]) && sender.hasPermission(ADMIN)) {
            sender.getServer().getOnlinePlayers().forEach(player -> options.add(player.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("efecto") && sender.hasPermission(ADMIN)) {
            for (ModEffectType type : ModEffectType.values()) {
                options.add(type.id());
            }
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
