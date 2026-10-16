package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Los iconos de las Botas de Neguechen y la Cruz Andina para Bedrock: arma el pack (mutango/relics-bedrock/ del jar) y
 * se lo da a Geyser junto con los dos items personalizados (MutangoGeyserBridge). Los de Java reciben las texturas en el
 * pack de BigCasares (resourcepack/java/assets/mutango), igual que el resto de los assets, y los modelos 3D por el pack
 * de BetterModel que ya mezcla el modulo del resource pack.
 */
final class MutangoBedrockItems {

    private static final String PACK_FOLDER = "mutango/relics-bedrock/";

    private final JavaPlugin plugin;
    /** Puente con Geyser (items de Bedrock); null si no hay Geyser o esta apagado en la configuracion. */
    private MutangoGeyserBridge geyserBridge;

    MutangoBedrockItems(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Bedrock: el pack con los iconos de las botas y la cruz y los items personalizados de Geyser. Hay que reiniciar si
     * cambia algo, igual que con GeyserModelEngine.
     */
    void start(boolean enabled) {
        if (!enabled || Arrays.stream(MutangoBedrock.GEYSER_PLUGINS)
            .noneMatch(name -> plugin.getServer().getPluginManager().getPlugin(name) != null)) {
            return;
        }
        Path pack = plugin.getDataFolder().toPath().resolve("mutango").resolve("bedrock")
            .resolve("premios-bedrock.mcpack");
        if (!zipResources(pack, PACK_FOLDER, MutangoRelics.BEDROCK_PACK_FILES)) {
            return;
        }
        try {
            geyserBridge = new MutangoGeyserBridge(pack);
            plugin.getLogger().info("[Mutango] Bedrock: iconos de las botas y la cruz registrados en Geyser.");
        } catch (LinkageError | RuntimeException exception) {
            plugin.getLogger().warning("[Mutango] No se pudo registrar los items en Geyser (" + exception + ").");
        }
    }

    void stop() {
        if (geyserBridge != null) {
            geyserBridge.close();
            geyserBridge = null;
        }
    }

    /**
     * Arma un zip con archivos del jar (siempre el mismo contenido en el mismo orden, asi el hash no cambia) y lo escribe
     * solo si es distinto del que ya esta.
     */
    private boolean zipResources(Path zip, String folder, List<String> entries) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new ZipOutputStream(bytes)) {
                for (String entry : entries) {
                    try (InputStream stream = plugin.getResource(folder + entry)) {
                        if (stream == null) {
                            throw new IOException("Falta " + folder + entry + " en el jar");
                        }
                        var zipEntry = new ZipEntry(entry);
                        zipEntry.setTime(0L);
                        out.putNextEntry(zipEntry);
                        stream.transferTo(out);
                        out.closeEntry();
                    }
                }
            }
            byte[] built = bytes.toByteArray();
            Files.createDirectories(zip.getParent());
            if (!Files.isRegularFile(zip) || !Arrays.equals(Files.readAllBytes(zip), built)) {
                Files.write(zip, built);
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("[Mutango] No se pudo armar " + zip.getFileName() + " (" + exception.getMessage()
                + ").");
            return false;
        }
    }
}
