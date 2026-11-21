package dev.linqfy.bigCasares.modules.mythicdungeons;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StructureSearchResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * {@code /mazmorra}: herramientas de admin de las mazmorras miticas (dar items, crear mobs y jefes, limpiar, ver donde
 * esta uno y buscar la mazmorra mas cercana).
 */
final class MythicDungeonCommand implements CommandExecutor, TabCompleter {

    private static final double REMOVE_RADIUS = 64.0;
    private static final int LOCATE_RADIUS_CHUNKS = 100;
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("dar", "mob", "jefe", "quitar", "info",
        "localizar");
    static final String ADMIN_PERMISSION = "bigcasares.mythicdungeons.admin";
    static final String CREDITS_PERMISSION = "bigcasares.mythicdungeons.credits";

    private final CustomItemRegistry items;
    private final MythicMobs mobs;
    private final DungeonLocator locator;
    private final MythicBossHost bosses;
    private final DungeonBossRegistry summons;

    MythicDungeonCommand(CustomItemRegistry items, MythicMobs mobs, DungeonLocator locator, MythicBossHost bosses,
                         DungeonBossRegistry summons) {
        this.items = items;
        this.mobs = mobs;
        this.locator = locator;
        this.bosses = bosses;
        this.summons = summons;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("creditos")) {
            if (!sender.hasPermission(CREDITS_PERMISSION)) {
                sender.sendMessage("§cNo tenés permiso para ver los créditos.");
                return true;
            }
            MythicCredits.lines().forEach(sender::sendMessage);
            return true;
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§eUsá /" + label + " creditos para ver de dónde salen las mazmorras.");
            return true;
        }
        switch (sub) {
            case "dar" -> give(sender, args);
            case "mob" -> spawnMob(sender, args);
            case "jefe" -> spawnBoss(sender, args);
            case "quitar" -> remove(sender);
            case "info" -> info(sender);
            case "localizar" -> locate(sender, args);
            default -> sender.sendMessage(usage(label));
        }
        return true;
    }

    private static String usage(String label) {
        return "§eUso: /" + label + " dar <fragmento|invocador> <cultura> [jugador] [cantidad]"
            + " §7| §emob <id|cultura> [mazmorra] §7| §ejefe <cultura> §7| §equitar §7| §einfo §7| §elocalizar <cultura>"
            + " §7| §ecreditos";
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eUso: /mazmorra dar <fragmento|invocador> <cultura> [jugador] [cantidad]");
            return;
        }
        Optional<MythicCulture> culture = MythicCulture.byId(args[2]);
        if (culture.isEmpty()) {
            sender.sendMessage("§cNo conozco la cultura '" + args[2] + "'. Opciones: " + cultureIds());
            return;
        }
        // sin jefe no hay fragmento ni invocador que dar
        if (!culture.get().hasBoss()) {
            sender.sendMessage(culture.get().notAvailableMessage());
            return;
        }
        String itemId = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "fragmento" -> culture.get().fragmentItemId().orElseThrow();
            case "invocador" -> culture.get().summonerItemId().orElseThrow();
            default -> null;
        };
        if (itemId == null) {
            sender.sendMessage("§cEl item tiene que ser 'fragmento' o 'invocador'.");
            return;
        }
        Player target;
        if (args.length >= 4) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null) {
                sender.sendMessage("§cNo hay ningún jugador conectado llamado " + args[3] + ".");
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage("§cDesde la consola tenés que decir a qué jugador.");
            return;
        }
        int amount = 1;
        if (args.length >= 5) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[4])));
            } catch (NumberFormatException invalid) {
                sender.sendMessage("§cLa cantidad tiene que ser un número.");
                return;
            }
        }
        ItemStack stack;
        try {
            stack = items.createItemStack(itemId, amount);
        } catch (RuntimeException unavailable) {
            sender.sendMessage("§cEl catálogo de items no tiene " + itemId + " (" + unavailable.getMessage() + ").");
            return;
        }
        target.getInventory().addItem(stack).values()
            .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        sender.sendMessage("§aDiste " + amount + "x " + itemId + " a " + target.getName() + ".");
    }

    private void spawnMob(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo un jugador puede crear mobs donde está parado.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§eUso: /mazmorra mob <id> [mazmorra]  §7(" + String.join(", ", mobs.catalog().ids())
                + ")");
            return;
        }
        Optional<MythicMobType> type = mobs.catalog().find(args[1]);
        Optional<MythicCulture> culture = MythicCulture.byId(args[1]);
        if (type.isEmpty() && culture.isPresent()) {
            if (!culture.get().hasMobs()) {
                sender.sendMessage("§eLa cultura " + culture.get().displayName() + " todavía no tiene mobs.");
                return;
            }
            // una cultura: un mob sorteado como en sus spawners
            type = MythicDungeonSpawns.pick(mobs.catalog(), culture.get(), ThreadLocalRandom.current().nextDouble());
        }
        if (type.isEmpty()) {
            sender.sendMessage("§cNo existe el mob '" + args[1] + "'.");
            return;
        }
        boolean dungeonSpawn = args.length >= 3 && args[2].equalsIgnoreCase("mazmorra");
        if (mobs.spawn(type.get(), player.getLocation(), dungeonSpawn).isPresent()) {
            String fragment;
            if (type.get().culture().fragmentItemId().isEmpty()) {
                fragment = " §7(su cultura todavía no tiene fragmento).";
            } else {
                fragment = dungeonSpawn ? " §7(como de mazmorra: puede soltar su fragmento)."
                    : " §7(no suelta fragmentos).";
            }
            sender.sendMessage("§aApareció " + type.get().displayName() + fragment);
        } else {
            sender.sendMessage("§cEl mundo no dejó aparecer a " + type.get().displayName() + ".");
        }
    }

    private void spawnBoss(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo un jugador puede invocar jefes donde está parado.");
            return;
        }
        Optional<MythicCulture> culture = args.length >= 2 ? MythicCulture.byId(args[1]) : Optional.empty();
        if (culture.isEmpty()) {
            sender.sendMessage("§eUso: /mazmorra jefe <cultura>  §7(" + bossCultureIds() + ")");
            return;
        }
        if (!culture.get().hasBoss()) {
            sender.sendMessage(culture.get().notAvailableMessage());
            return;
        }
        if (!bosses.available(culture.get())) {
            sender.sendMessage("§cEl sistema de jefes está apagado o " + culture.get().requireBoss().displayName()
                + " no quedó registrado (mirá la consola).");
            return;
        }
        try {
            Location at = player.getLocation();
            UUID boss = bosses.spawn(culture.get(), at);
            summons.remember("admin:" + boss, new DungeonBossRegistry.Summon(boss, culture.get(),
                at.getWorld().getName(), at.getX(), at.getY(), at.getZ()));
            sender.sendMessage("§aInvocaste a " + culture.get().requireBoss().displayName() + " §7(" + boss + ").");
        } catch (RuntimeException failure) {
            sender.sendMessage("§cNo se pudo invocar a " + culture.get().requireBoss().displayName() + ": "
                + failure.getMessage());
        }
    }

    private void remove(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo un jugador puede limpiar alrededor suyo.");
            return;
        }
        Location here = player.getLocation();
        int bossesRemoved = 0;
        for (Map.Entry<String, DungeonBossRegistry.Summon> entry : summons.snapshot().entrySet()) {
            DungeonBossRegistry.Summon summon = entry.getValue();
            boolean near = summon.world().equals(here.getWorld().getName())
                && here.distanceSquared(new Location(here.getWorld(), summon.x(), summon.y(), summon.z()))
                <= REMOVE_RADIUS * REMOVE_RADIUS;
            if (near && bosses.remove(summon.bossInstanceId())) {
                bossesRemoved++;
            }
            if (near) {
                summons.forget(summon.bossInstanceId());
            }
        }
        int mobsRemoved = mobs.removeNearby(here, REMOVE_RADIUS);
        sender.sendMessage("§aSaqué " + bossesRemoved + " jefes y " + mobsRemoved + " entidades de las mazmorras.");
    }

    private void info(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo un jugador tiene posición.");
            return;
        }
        Optional<DungeonBounds> bounds = locator.dungeonBounds(player.getLocation());
        if (bounds.isEmpty()) {
            sender.sendMessage("§7No estás adentro de ninguna mazmorra mítica.");
            return;
        }
        DungeonBounds dungeon = bounds.get();
        sender.sendMessage("§5Estás en " + dungeon.culture().dungeonName() + " §7(" + dungeon.culture().structureKey()
            + ")");
        sender.sendMessage("§7Caja: " + dungeon.minX() + " " + dungeon.minY() + " " + dungeon.minZ() + " → "
            + dungeon.maxX() + " " + dungeon.maxY() + " " + dungeon.maxZ());
        if (!dungeon.culture().hasBoss()) {
            sender.sendMessage("§7Esta cultura todavía no tiene jefe" + (dungeon.culture().hasMobs()
                ? " §7| mobs de mazmorra cerca: " + mobs.countNearby(player.getLocation(), 48.0, true)
                : " ni mobs: la mazmorra sigue vanilla."));
            return;
        }
        boolean alive = summons.alive(dungeon.key(), bosses::isActive).isPresent();
        sender.sendMessage("§7Jefe: " + dungeon.culture().requireBoss().displayName()
            + (alive ? " §c(despierto)" : " §a(dormido)")
            + " §7| mobs de mazmorra cerca: " + mobs.countNearby(player.getLocation(), 48.0, true));
    }

    private void locate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo un jugador puede buscar desde donde está.");
            return;
        }
        Optional<MythicCulture> culture = args.length >= 2 ? MythicCulture.byId(args[1]) : Optional.empty();
        if (culture.isEmpty()) {
            sender.sendMessage("§eUso: /mazmorra localizar <cultura>  §7(" + cultureIds() + ")");
            return;
        }
        Optional<Structure> structure = PaperDungeonLocator.structureOf(culture.get());
        if (structure.isEmpty()) {
            sender.sendMessage("§cLa estructura " + culture.get().structureKey() + " no está en el registro: el"
                + " datapack de las mazmorras todavía no está cargado (hace falta instalarlo y reiniciar).");
            return;
        }
        StructureSearchResult result = player.getWorld().locateNearestStructure(player.getLocation(),
            structure.get(), LOCATE_RADIUS_CHUNKS, false);
        if (result == null) {
            sender.sendMessage("§7No encontré " + culture.get().dungeonName() + " cerca en este mundo.");
            return;
        }
        Location found = result.getLocation();
        sender.sendMessage("§aLa " + culture.get().dungeonName().substring(3) + " más cercana está en §f"
            + found.getBlockX() + " ~ " + found.getBlockZ() + " §7(a "
            + Math.round(found.distance(new Location(found.getWorld(), player.getLocation().getX(), found.getY(),
            player.getLocation().getZ()))) + " bloques).");
    }

    private static String cultureIds() {
        return String.join(", ", Arrays.stream(MythicCulture.values()).map(MythicCulture::id).toList());
    }

    private static String bossCultureIds() {
        return String.join(", ", MythicCulture.bossCultures().stream().map(MythicCulture::id).toList());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);
        if (args.length == 1) {
            List<String> options = new ArrayList<>(admin ? ADMIN_SUBCOMMANDS : List.of());
            if (sender.hasPermission(CREDITS_PERMISSION)) {
                options.add("creditos");
            }
            return filter(options.stream(), args[0]);
        }
        if (!admin) {
            return List.of();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        // dar y jefe solo ofrecen las culturas con jefe; mob, todos los mobs y las culturas que tienen
        Stream<String> withBoss = MythicCulture.bossCultures().stream().map(MythicCulture::id);
        Stream<String> withMobs = MythicCulture.mobCultures().stream().map(MythicCulture::id);
        if (args.length == 2) {
            return switch (sub) {
                case "dar" -> filter(Stream.of("fragmento", "invocador"), args[1]);
                case "mob" -> filter(Stream.concat(mobs.catalog().ids().stream(), withMobs), args[1]);
                case "jefe" -> filter(withBoss, args[1]);
                case "localizar" -> filter(Arrays.stream(MythicCulture.values()).map(MythicCulture::id), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "dar" -> filter(withBoss, args[2]);
                case "mob" -> filter(Stream.of("mazmorra"), args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4 && sub.equals("dar")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName), args[3]);
        }
        if (args.length == 5 && sub.equals("dar")) {
            return filter(Stream.of("1", "4", "16"), args[4]);
        }
        return List.of();
    }

    private static List<String> filter(Stream<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        options.filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).forEach(matches::add);
        return matches;
    }
}
