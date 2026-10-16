package dev.linqfy.bigCasares.modules.content;

import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.blocks.CustomBlockRegistry;
import dev.linqfy.bigCasares.blocks.MushroomState;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.modules.sift.SiftBlocks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the vanilla mushroom blockstate overrides that render every built-in
 * custom block ({@code gradlew generateCustomBlockstates}).
 *
 * <p>The three files under {@code resourcepack/java/assets/minecraft/blockstates/}
 * are generated, not hand-edited: a contract test fails when they drift from the
 * block registry. Unallocated states keep their vanilla appearance.</p>
 */
public final class CustomBlockstateExportMain {

    static final String BLOCKSTATE_DIRECTORY = "java/assets/minecraft/blockstates";

    private CustomBlockstateExportMain() {
    }

    /** Every custom block shipped with the plugin, in registration order. */
    static List<CustomBlockDefinition> builtInBlocks() {
        return SiftBlocks.definitions();
    }

    /** File name ({@code brown_mushroom_block.json}) to JSON text, for every mushroom base. */
    static Map<String, String> generate() {
        CustomBlockRegistry registry = new CustomBlockRegistry();
        builtInBlocks().forEach(registry::register);
        Map<String, String> files = new LinkedHashMap<>();
        for (MushroomState.Base base : MushroomState.Base.values()) {
            String name = base.blockId().substring("minecraft:".length()).toLowerCase(Locale.ROOT) + ".json";
            files.put(name, Json.write(registry.blockstate(base)));
        }
        return files;
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: CustomBlockstateExportMain <resourcepack-directory>");
        }
        Path directory = Path.of(args[0]).resolve(BLOCKSTATE_DIRECTORY);
        Files.createDirectories(directory);
        for (Map.Entry<String, String> file : generate().entrySet()) {
            Files.writeString(directory.resolve(file.getKey()), file.getValue(), StandardCharsets.UTF_8);
        }
        System.out.println("Wrote " + generate().size() + " blockstates to " + directory);
    }
}
