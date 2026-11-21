package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /caichet, para administradores: entregar los objetos del modulo, viajar entre sus mundos sin bloque ni totem y
 * mandar el tren y levantar un pozo.
 */
final class CaiChetCommand implements CommandExecutor, TabCompleter {

    static final List<String> SUBCOMMANDS = List.of("give", "tp", "tren", "hotel", "sueno", "pozo");
    static final List<String> ITEMS = List.of("dimension", "mundo", "reloj", "despertar", "arma", "botella", "jarra",
        "llave");
    private static final String USAGE = "Uso: /caichet give <" + String.join("|", ITEMS)
        + "> [cantidad] | tp | tren [aqui] | hotel | sueno | pozo";

    private final TrainDirector director;
    private final PitStructure pits;

    CaiChetCommand(TrainDirector director, PitStructure pits) {
        this.director = director;
        this.pits = pits;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player = sender instanceof ProxiedCommandSender proxied && proxied.getCallee() instanceof Player callee
            ? callee : sender instanceof Player self ? self : null;
        if (player == null) {
            // desde la consola: el ultimo argumento dice sobre que jugador actua
            player = args.length == 0 ? null : Bukkit.getPlayerExact(args[args.length - 1]);
            if (player == null) {
                sender.sendMessage("Desde la consola, agrega al final el nombre de un jugador conectado.");
                return true;
            }
            args = Arrays.copyOf(args, args.length - 1);
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(player, args);
            case "tren" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("aqui")) {
                    director.startInFront(player);
                } else {
                    director.startNow();
                }
            }
            // ir al hotel sin gastar un Despertar
            case "hotel" -> DespertarListener.wake(player);
            case "sueno" -> {
                if (DreamWorld.get() != null) {
                    player.teleportAsync(DreamWorld.spawn(DreamWorld.get()));
                }
            }
            case "pozo" -> {
                String result = pits.buildAt(player.getChunk());
                player.sendMessage(result);
                if (sender != player) {
                    sender.sendMessage(result);
                }
            }
            case "tp" -> travel(player);
            default -> player.sendMessage(USAGE);
        }
        return true;
    }

    /** Entra o sale sin bloque: desde el Sueño al mundo, desde el hotel a donde estabas, y si no, al desierto. */
    private static void travel(Player player) {
        if (DreamWorld.isDream(player.getWorld())) {
            player.teleportAsync(Bukkit.getWorlds().getFirst().getSpawnLocation());
        } else if (HotelWorld.isHotel(player.getWorld())) {
            DespertarListener.leave(player);
        } else if (player.getWorld().equals(PortalListener.dimension())) {
            PortalListener.leave(player);
        } else {
            PortalListener.enter(player);
        }
    }

    private static void give(Player player, String[] args) {
        ItemStack base = args.length >= 2 ? item(args[1].toLowerCase(Locale.ROOT)) : null;
        if (base == null) {
            player.sendMessage(USAGE);
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = amount(args[2], base.getMaxStackSize());
            } catch (NumberFormatException exception) {
                player.sendMessage("La cantidad tiene que ser un numero.");
                return;
            }
        }
        base.setAmount(amount);
        player.getInventory().addItem(base).values()
            .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    /** La cantidad pedida, entre 1 y lo que entra en un stack de ese objeto. */
    static int amount(String raw, int maxStackSize) {
        return Math.max(1, Math.min(maxStackSize, Integer.parseInt(raw)));
    }

    private static ItemStack item(String id) {
        PortalKind kind = PortalKind.byId(id);
        if (kind != null) {
            return kind.createItem(1);
        }
        return switch (id) {
            case "reloj" -> DreamItems.clock();
            case "despertar" -> DreamItems.totem();
            case "arma" -> HotelLoot.gun();
            case "botella" -> HotelLoot.bottle();
            case "jarra" -> HotelLoot.jar();
            case "llave" -> HotelLoot.key();
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return complete(args);
    }

    static List<String> complete(String[] args) {
        if (args.length <= 1) {
            return prefixed(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return prefixed(ITEMS, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tren")) {
            return prefixed(List.of("aqui"), args[1]);
        }
        return List.of();
    }

    private static List<String> prefixed(List<String> options, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
