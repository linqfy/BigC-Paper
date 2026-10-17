package dev.linqfy.bigCasares.modules.content;

import dev.linqfy.bigCasares.blocks.CustomBlockRegistry;
import dev.linqfy.bigCasares.blocks.CustomBlockService;
import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.content.ContentRecipeGuard;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code custom-content}: runtime behaviour for code-defined content items and
 * custom blocks shared by content modules (the Sift and future expansions), plus
 * {@code /content give|list}.
 */
public final class CustomContentModule implements PluginModule {

    public static final String MODULE_ID = "custom-content";
    static final String ADMIN = "bigcasares.content.admin";

    private final JavaPlugin plugin;
    private final CustomBlockRegistry blocks;
    private final ContentItemFactory items;
    private RuntimeRegistrationScope compatibilityScope;
    private CustomBlockService service;

    public CustomContentModule(JavaPlugin plugin, CustomBlockRegistry blocks, ContentItemFactory items) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.items = items;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        CustomBlockService created = new CustomBlockService(plugin, blocks, items);
        created.checkServerConfiguration();
        this.service = created;
        registrations.registerListener("custom-block-listener", created);
        registrations.registerListener("content-recipe-guard", new ContentRecipeGuard());
        PluginCommand command = plugin.getCommand("content");
        if (command == null) {
            throw new IllegalStateException("Required command is not declared: content");
        }
        ContentCommand executor = new ContentCommand();
        registrations.bindCommand("content-command", command, executor, executor);
        scope.register("module-state", () -> service = null);
    }

    @Override
    public void onDisable() {
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    public Optional<CustomBlockService> blockService() {
        return Optional.ofNullable(service);
    }

    private final class ContentCommand implements CommandExecutor, TabCompleter {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!sender.hasPermission(ADMIN)) {
                sender.sendMessage("§cNo tenés permiso.");
                return true;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                String namespace = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null;
                List<String> ids = items.registry().items().stream().map(item -> item.id().asString())
                    .filter(id -> namespace == null || id.startsWith(namespace + ":")).toList();
                sender.sendMessage("§7Ítems de contenido (" + ids.size() + "): §f" + String.join(", ", ids));
                sender.sendMessage("§7Bloques custom: §f" + blocks.definitions().size());
                return true;
            }
            if (args[0].equalsIgnoreCase("give") && args.length >= 2) {
                Optional<ContentItem> item = items.registry().find(args[1]);
                if (item.isEmpty()) {
                    sender.sendMessage("§cÍtem desconocido: " + args[1]);
                    return true;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2])
                    : sender instanceof Player player ? player : null;
                if (target == null) {
                    sender.sendMessage("§cJugador no encontrado.");
                    return true;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Math.max(1, Math.min(6400, Integer.parseInt(args[3])));
                    } catch (NumberFormatException invalid) {
                        sender.sendMessage("§cCantidad inválida.");
                        return true;
                    }
                }
                int remaining = amount;
                while (remaining > 0) {
                    int batch = Math.min(remaining, item.get().maxStackSize());
                    ItemStack stack = items.create(item.get(), batch);
                    target.getInventory().addItem(stack).values()
                        .forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
                    remaining -= batch;
                }
                sender.sendMessage("§aEntregado " + amount + "x " + item.get().id() + " a " + target.getName());
                return true;
            }
            sender.sendMessage("§eUso: /" + label + " <list [namespace]|give <id> [jugador] [cantidad]>");
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> options = new ArrayList<>();
            if (args.length == 1) {
                options.addAll(List.of("list", "give"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                items.registry().items().forEach(item -> options.add(item.id().asString()));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            }
            String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
            return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).limit(80).toList();
        }
    }
}
