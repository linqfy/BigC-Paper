package dev.linqfy.bigCasares.modules.alexscaves;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.remap.PacketRemapService;
import dev.linqfy.bigCasares.modules.resourcepack.JavaPackLayerProvider;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Java pack layer that only ships while the module is enabled: it overrides the
 * note block blockstate to draw module blocks. In the main pack it would
 * restyle every player-placed note block even with the module switched off.
 * The borrowed blockstates of the packet remap ({@code files-packet-remap.txt}: leaves, kelp,
 * chorus flower, lightning rods) only ship while {@code blocks.packet-remap} is on and packetevents
 * is installed: without the remap real blocks in a borrowed state would draw a module block.
 */
public final class AlexsCavesPackLayer {
    public static final String LAYER_ID = "alexscaves-blocks";
    static final String ROOT = "alexscaves/pack-layer/";

    private AlexsCavesPackLayer() {
    }

    /** Builds the layer archive when the module is explicitly enabled. */
    public static Optional<JavaPackLayerProvider> provide(Plugin plugin, Path directory) throws IOException {
        if (!AlexsCavesModule.isExplicitlyEnabled(plugin.getConfig())) {
            return Optional.empty();
        }
        Path archive = directory.resolve(LAYER_ID + ".zip");
        Files.createDirectories(directory);
        Path temporary = directory.resolve(LAYER_ID + ".zip.tmp");
        try (OutputStream output = Files.newOutputStream(temporary);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            List<String> files = new ArrayList<>(files(plugin, "files.txt"));
            if (packetRemapWanted(plugin)) {
                files.addAll(files(plugin, "files-packet-remap.txt"));
            }
            for (String file : files) {
                try (InputStream input = plugin.getResource(ROOT + file)) {
                    if (input == null) {
                        throw new IllegalStateException("Falta " + ROOT + file + " en el JAR");
                    }
                    ZipEntry entry = new ZipEntry(file);
                    entry.setTime(0L);
                    zip.putNextEntry(entry);
                    input.transferTo(zip);
                    zip.closeEntry();
                }
            }
        }
        Files.move(temporary, archive, StandardCopyOption.REPLACE_EXISTING);
        return Optional.of(JavaPackLayerProvider.fixed(LAYER_ID, archive));
    }

    /** {@code blocks.packet-remap} of alexs-caves.yml (read from disk: the pack may build before the module enables). */
    static boolean packetRemapWanted(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), AlexsCavesModule.CONFIG_FILE);
        return file.isFile() && YamlConfiguration.loadConfiguration(file).getBoolean("blocks.packet-remap", false)
            && PacketRemapService.packetEventsPresent(plugin);
    }

    static List<String> files(Plugin plugin, String list) throws IOException {
        try (InputStream input = plugin.getResource(ROOT + list)) {
            if (input == null) {
                throw new IllegalStateException("Falta " + ROOT + list + " en el JAR");
            }
            List<String> files = new ArrayList<>();
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    files.add(trimmed);
                }
            }
            return files;
        }
    }
}
