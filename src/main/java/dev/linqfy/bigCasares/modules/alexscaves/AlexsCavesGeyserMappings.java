package dev.linqfy.bigCasares.modules.alexscaves;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Geyser custom mappings (items v2 and note block states v1) of the module,
 * copied into {@code plugins/Geyser-Spigot/custom_mappings/} like Don Pollos
 * does. They are removed while the module is off: the block mapping would
 * otherwise restyle vanilla note blocks for Bedrock players. Geyser reads them
 * at startup, so changes need a restart.
 */
final class AlexsCavesGeyserMappings {
    static final List<String> FILES = List.of("alexscaves-items.json", "alexscaves-blocks.json");
    private static final String GEYSER = "Geyser-Spigot";

    private AlexsCavesGeyserMappings() {
    }

    /** Returns true when something changed (Geyser needs a restart to load it). */
    static boolean install(Plugin plugin) {
        Path folder = mappingsFolder(plugin);
        if (folder == null) {
            return false;
        }
        boolean changed = AlexsCavesBedrockModels.install(plugin);
        for (String file : FILES) {
            try (InputStream input = plugin.getResource("alexscaves/geyser/" + file)) {
                if (input == null) {
                    throw new IllegalStateException("Falta alexscaves/geyser/" + file + " en el JAR");
                }
                changed |= write(folder.resolve(file), input.readAllBytes());
            } catch (IOException exception) {
                plugin.getLogger().warning("[AlexsCaves] No se pudo instalar el mapeo de Geyser " + file + ": " + exception.getMessage());
            }
        }
        if (changed) {
            plugin.getLogger().warning("[AlexsCaves] Mapeos de Geyser actualizados: reiniciá el servidor para que Bedrock los vea.");
        }
        return changed;
    }

    static void uninstall(Plugin plugin) {
        AlexsCavesBedrockModels.uninstall(plugin);
        Path folder = mappingsFolder(plugin);
        if (folder == null) {
            return;
        }
        Logger logger = plugin.getLogger();
        for (String file : FILES) {
            try {
                if (Files.deleteIfExists(folder.resolve(file))) {
                    logger.info("[AlexsCaves] Mapeo de Geyser " + file + " quitado (módulo apagado); reiniciá para aplicarlo.");
                }
            } catch (IOException exception) {
                logger.warning("[AlexsCaves] No se pudo quitar " + file + ": " + exception.getMessage());
            }
        }
    }

    private static Path mappingsFolder(Plugin plugin) {
        Plugin geyser = plugin.getServer().getPluginManager().getPlugin(GEYSER);
        return geyser == null ? null : geyser.getDataFolder().toPath().resolve("custom_mappings");
    }

    private static boolean write(Path target, byte[] bytes) throws IOException {
        if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), bytes)) {
            return false;
        }
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, bytes);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }
}
