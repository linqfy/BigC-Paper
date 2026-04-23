package dev.linqfy.bigCasares.modules.airdrop;

import dev.linqfy.bigCasares.module.PluginModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public final class AirdropModule implements PluginModule {

    private static final String MODULE_ID = "airdrop-system";

    private final JavaPlugin plugin;

    private AirdropService service;
    private AirdropListener listener;
    private BukkitTask intervalTask;
    private BukkitTask fallingTask;

    public AirdropModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        AirdropSettings settings = AirdropSettings.fromConfig(plugin.getConfig());
        Map<String, Material> lootMaterials = new AirdropLootMaterialResolver().resolveAll();

        Path dataPath = plugin.getDataFolder().toPath()
                .resolve("data")
                .resolve("airdrop-system");

        YamlAirdropStorage storage = new YamlAirdropStorage(dataPath, plugin.getLogger());

        World world = Bukkit.getWorld("world");
        if (world == null) {
            throw new IllegalStateException("Default world 'world' not found");
        }

        BukkitAirdropWorldGateway gateway = new BukkitAirdropWorldGateway(world);
        service = new AirdropService(settings, gateway, storage);
        listener = new AirdropListener(service, lootMaterials);

        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        long intervalTicks = (long) settings.intervalMinutes() * 60L * 20L;
        intervalTask = new BukkitRunnable() {
            @Override
            public void run() {
                triggerAirdrop(world);
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);

        registerCommand(world);
        restorePersistedDrop(world);

        plugin.getLogger().info("[AirdropModule] Enabled. Interval: " + settings.intervalMinutes() + " minutes.");
    }

    @Override
    public void onDisable() {
        cancelIntervalTask();
        cancelFallingTask();
        if (listener != null) {
            listener.clearChestPosition();
        }
        plugin.getLogger().info("[AirdropModule] Disabled.");
    }

    public AirdropService getService() {
        return service;
    }

    private Optional<AirdropData> triggerAirdrop(World world) {
        Optional<AirdropData> result = service.spawnAirdrop();
        result.ifPresent(data -> {
            AirdropPosition pos = data.position();
            plugin.getServer().broadcastMessage(
                    "Â§6âœˆ Airdrop en camino en X: " + pos.x() + " Z: " + pos.z()
            );
            startFallingTask(world, pos);
        });
        return result;
    }

    private void registerCommand(World world) {
        var cmd = plugin.getCommand("airdrop");
        if (cmd == null) return;
        cmd.setExecutor((CommandSender sender, Command command, String label, String[] args) -> {
            if (args.length < 1 || !args[0].equalsIgnoreCase("spawn")) return false;
            if (!sender.hasPermission("bigcasares.airdrop.spawn")) {
                sender.sendMessage("Â§cNo tienes permiso.");
                return true;
            }
            Optional<AirdropData> result = triggerAirdrop(world);
            if (result.isPresent()) {
                sender.sendMessage("Â§aAirdrop forzado.");
            } else {
                sender.sendMessage("Â§cNo se pudo generar el airdrop.");
            }
            return true;
        });
    }

    private void restorePersistedDrop(World world) {
        Optional<AirdropData> current = service.getCurrentDrop();
        if (current.isEmpty() || !current.get().isActive()) {
            return;
        }

        AirdropData data = current.get();
        if (data.phase() == AirdropPhase.FALLING) {
            startFallingTask(world, data.position());
            return;
        }

        if (data.phase() == AirdropPhase.LANDED) {
            AirdropPosition chestPosition = data.position();
            world.getBlockAt(chestPosition.x(), chestPosition.y(), chestPosition.z()).setType(Material.CHEST);
            listener.setChestPosition(chestPosition);
        }
    }

    private void startFallingTask(World world, AirdropPosition position) {
        cancelFallingTask();
        AirdropFallingTask task = new AirdropFallingTask(world, position, landedPosition -> {
            fallingTask = null;
            Optional<AirdropData> landed = service.markLanded(landedPosition);
            if (landed.isPresent()) {
                listener.setChestPosition(landedPosition);
                return;
            }
            world.getBlockAt(landedPosition.x(), landedPosition.y(), landedPosition.z()).setType(Material.AIR);
        });
        fallingTask = task.runTaskTimer(plugin, 0L, 1L);
    }

    private void cancelIntervalTask() {
        if (intervalTask != null) {
            intervalTask.cancel();
            intervalTask = null;
        }
    }

    private void cancelFallingTask() {
        if (fallingTask != null) {
            fallingTask.cancel();
            fallingTask = null;
        }
    }
}
