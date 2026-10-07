package dev.linqfy.bigCasares.modules.donpollos;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

final class DonPollosCommand implements CommandExecutor, TabCompleter {

    static final String PERMISSION = "bigcasares.donpollos.admin";
    private static final List<String> SUBCOMMANDS = List.of("spawn", "remove", "list", "give", "bailar", "ciudad", "boss");

    private final DonPollosModule module;

    DonPollosCommand(DonPollosModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§cNo tenes permiso.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§eUso: /" + label + " <spawn <variante>|remove|list|give <producto> [cantidad]|bailar <1-5|parar> [todos]|ciudad <aqui|cerca>|boss [quitar]>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> spawn(sender, label, args);
            case "remove" -> remove(sender);
            case "list" -> sender.sendMessage("§eDon Pollos cargados: §f" + module.loadedSummary());
            case "give" -> give(sender, label, args);
            case "bailar" -> dance(sender, label, args);
            case "ciudad" -> city(sender, label, args);
            case "boss" -> boss(sender, label, args);
            default -> sender.sendMessage("§cSubcomando desconocido. Usa spawn, remove, list, give, bailar, ciudad o boss.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return filter(DonPolloVariant.ids(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("boss")) {
            return filter(List.of("quitar"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ciudad")) {
            return filter(List.of("aqui", "cerca"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bailar")) {
            List<String> options = new java.util.ArrayList<>();
            for (int i = 1; i <= module.danceNames().size(); i++) {
                options.add(String.valueOf(i));
            }
            options.add("parar");
            return filter(options, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("bailar")) {
            return filter(List.of("todos"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> items = new java.util.ArrayList<>(
                java.util.Arrays.stream(DonPollosProduct.values()).map(DonPollosProduct::id).toList());
            items.add("balde");
            items.add("secreto");
            return filter(items, args[1]);
        }
        return List.of();
    }

    private void spawn(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            spawnFromConsole(sender, args);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§eUso: /" + label + " spawn <" + String.join("|", DonPolloVariant.ids()) + ">");
            return;
        }
        DonPolloVariant.fromId(args[1]).ifPresentOrElse(
            variant -> {
                module.spawn(player.getLocation(), variant);
                sender.sendMessage("§aSpawneaste a Don Pollo " + variant.id() + ".");
            },
            () -> sender.sendMessage("§cVariante desconocida: " + args[1]));
    }

    private void spawnFromConsole(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage("Uso desde consola: donpollo spawn <variante> <mundo> <x> <y> <z>");
            return;
        }
        World world = Bukkit.getWorld(args[2]);
        DonPolloVariant variant = DonPolloVariant.fromId(args[1]).orElse(null);
        if (world == null || variant == null) {
            sender.sendMessage("Mundo o variante desconocidos.");
            return;
        }
        try {
            Location location = new Location(world,
                Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5]));
            module.spawn(location, variant);
            sender.sendMessage("Don Pollo " + variant.id() + " spawneado en " + location.toVector());
        } catch (NumberFormatException exception) {
            sender.sendMessage("Coordenadas invalidas.");
        }
    }

    private void boss(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length >= 5) {
                World world = Bukkit.getWorld(args[1]);
                try {
                    if (world != null && world.getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
                        sender.sendMessage("El Don Pollo Boss no puede existir en dificultad pacifica: usa difficulty easy.");
                        return;
                    }
                    if (world != null) {
                        module.bosses().spawn(new Location(world, Double.parseDouble(args[2]),
                            Double.parseDouble(args[3]), Double.parseDouble(args[4])));
                        sender.sendMessage("Don Pollo Boss spawneado.");
                        return;
                    }
                } catch (NumberFormatException ignored) {
                    // cae al mensaje de uso
                }
            }
            sender.sendMessage("Desde la consola: donpollo boss <mundo> <x> <y> <z>");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("quitar")) {
            sender.sendMessage(module.bosses().removeNearest(player.getLocation(), 20)
                ? "§aDon Pollo Boss eliminado." : "§cNo hay ningun Don Pollo Boss a menos de 20 bloques.");
            return;
        }
        if (player.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
            sender.sendMessage("§cEl Don Pollo Boss es un monstruo y en dificultad pacifica no puede existir.");
            sender.sendMessage("§eSubi la dificultad con §f/difficulty easy §e(o normal/hard) y volve a probar.");
            return;
        }
        Location at = player.getLocation();
        org.bukkit.util.Vector ahead = at.getDirection().setY(0);
        if (ahead.lengthSquared() < 1e-6) {
            ahead = new org.bukkit.util.Vector(0, 0, 1);
        }
        Location spawn = at.clone().add(ahead.normalize().multiply(6));
        spawn.setY(player.getWorld().getHighestBlockYAt(spawn) + 1);
        spawn.setYaw(at.getYaw() + 180);
        module.bosses().spawn(spawn);
        sender.sendMessage("§4§l¡Aparecio el Don Pollo Boss!");
    }

    private void city(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length >= 5 && args[1].equalsIgnoreCase("cerca") && module.cities() != null) {
                World world = Bukkit.getWorld(args[2]);
                try {
                    sender.sendMessage(world == null ? "Mundo desconocido." : module.cities().nearest(world,
                        Integer.parseInt(args[3]), Integer.parseInt(args[4])));
                } catch (NumberFormatException exception) {
                    sender.sendMessage("Coordenadas invalidas.");
                }
                return;
            }
            sender.sendMessage("Desde la consola: donpollo ciudad cerca <mundo> <x> <z>");
            return;
        }
        if (module.cities() == null) {
            sender.sendMessage("§cLas ciudades estan desactivadas (mira la consola).");
            return;
        }
        String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (mode) {
            case "aqui" -> module.cities().buildHere(player);
            case "cerca" -> sender.sendMessage(module.cities().nearest(player));
            default -> sender.sendMessage("§eUso: /" + label + " ciudad <aqui|cerca>");
        }
    }

    private void dance(CommandSender sender, String label, String[] args) {
        List<String> dances = module.danceNames();
        if (args.length < 2) {
            sender.sendMessage("§eUso: /" + label + " bailar <1-" + dances.size() + "|parar> [todos]");
            return;
        }
        String dance = null;
        if (!args[1].equalsIgnoreCase("parar")) {
            String raw = args[1].toLowerCase(Locale.ROOT);
            if (dances.contains(raw)) {
                dance = raw;
            } else {
                try {
                    int index = Integer.parseInt(raw);
                    dance = index >= 1 && index <= dances.size() ? dances.get(index - 1) : null;
                } catch (NumberFormatException ignored) {
                    dance = null;
                }
            }
            if (dance == null) {
                sender.sendMessage("§cBaile desconocido. Elegi del 1 al " + dances.size() + " o parar.");
                return;
            }
        }
        boolean all = args.length >= 3 && args[2].equalsIgnoreCase("todos");
        if (!all && !(sender instanceof Player)) {
            sender.sendMessage("Desde la consola usa: donpollo bailar <1-" + dances.size() + "|parar> todos");
            return;
        }
        int changed = module.setDance(all ? null : ((Player) sender).getLocation(), 10.0, dance);
        if (changed == 0) {
            sender.sendMessage(all ? "§cNo hay Salseros cargados (o ya estaban asi)."
                : "§cNo hay ningun Don Pollo Salsero a menos de 10 bloques (o ya estaba asi).");
        } else if (dance == null) {
            sender.sendMessage("§aEl Salsero dejo de bailar. (" + changed + ")");
        } else {
            sender.sendMessage("§d¡A bailar! §f" + dance + " §7(" + changed + " Salsero" + (changed > 1 ? "s" : "") + ")");
        }
    }

    private void give(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo un jugador puede recibir productos.");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("secreto")) {
            player.getInventory().addItem(module.secret().secretBlock());
            sender.sendMessage("§aRecibiste el Secreto del Don Pollo");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("balde")) {
            player.getInventory().addItem(module.bosses().kfcBucket());
            sender.sendMessage("§aRecibiste el Balde de KFC dado vuelta");
            return;
        }
        DonPollosProduct product = args.length < 2 ? null : DonPollosProduct.fromId(args[1]).orElse(null);
        if (product == null) {
            sender.sendMessage("§eUso: /" + label + " give <pollo-frito|salsa|picante|balde|secreto> [cantidad]");
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException exception) {
                sender.sendMessage("§cCantidad invalida.");
                return;
            }
        }
        module.giveProduct(player, product, amount);
        sender.sendMessage("§aRecibiste " + amount + "x " + product.displayName());
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo un jugador puede quitar Don Pollos.");
            return;
        }
        sender.sendMessage(module.removeNearest(player.getLocation(), 5.0)
            ? "§aDon Pollo eliminado."
            : "§cNo hay ningun Don Pollo a menos de 5 bloques.");
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(lowered)).toList();
    }
}
