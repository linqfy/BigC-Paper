package dev.linqfy.bigCasares.modules.alexscaves;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.items.catalog.CustomItemCatalog;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogLoader;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogSeeder;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogValidator;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.BlockBase;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.CustomBlockState;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockRegistry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlexsCavesContentContractTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    @TempDir
    Path temp;

    @Test
    void everyListedItemHasAYamlThatSeedsAndValidates() {
        List<String> ids = AlexsCavesItemIds.ids();
        assertFalse(ids.isEmpty());
        for (String id : ids) {
            assertTrue(Files.isRegularFile(RESOURCES.resolve("content/items/" + id + ".yml")), id);
        }
        new ItemCatalogSeeder(AlexsCavesItemIds.defaultFiles())
            .seed(name -> resource("content/items/" + name), temp.resolve("items"));
        CustomItemCatalog catalog = new ItemCatalogLoader().load(temp.resolve("items"));
        new ItemCatalogValidator().validate(catalog, Path.of("resourcepack"));
        assertEquals(ids.size(), catalog.size());
        assertEquals(AlexsCavesItemIds.MECHANIC, catalog.require("ac_galena").mechanic());
        assertEquals("NOTE_BLOCK", catalog.require("ac_galena").material());
    }

    @Test
    void moduleNeedsAnExplicitOptIn() {
        YamlConfiguration config = new YamlConfiguration();
        assertFalse(AlexsCavesModule.isExplicitlyEnabled(config), "a missing switch must not enable it");
        config.set("modules.alexs-caves.enabled", false);
        assertFalse(AlexsCavesModule.isExplicitlyEnabled(config));
        config.set("modules.alexs-caves.enabled", true);
        assertTrue(AlexsCavesModule.isExplicitlyEnabled(config));
        config.set("modules.alexs-caves", true);
        assertTrue(AlexsCavesModule.isExplicitlyEnabled(config), "the legacy flat switch ModuleManager accepts");
        config.set("modules.alexs-caves", false);
        assertFalse(AlexsCavesModule.isExplicitlyEnabled(config));
        assertFalse(AlexsCavesModule.isExplicitlyEnabled(null));
    }

    @Test
    void noteBlockLayerKeepsHarpStatesVanillaAndCoversEveryState() throws IOException {
        JsonObject variants = JsonParser.parseString(Files.readString(
                RESOURCES.resolve("alexscaves/pack-layer/assets/minecraft/blockstates/note_block.json")))
            .getAsJsonObject().getAsJsonObject("variants");
        assertEquals(27 * 25 * 2, variants.size(), "every 26.2 instrument must be covered");
        Set<String> custom = new HashSet<>();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : variants.entrySet()) {
            String model = entry.getValue().getAsJsonObject().get("model").getAsString();
            if (entry.getKey().startsWith("instrument=harp,")) {
                assertEquals("minecraft:block/note_block", model, entry.getKey());
            } else if (model.startsWith("alexscaves:")) {
                custom.add(entry.getKey());
            }
        }
        assertFalse(custom.isEmpty());
        assertFalse(Files.exists(Path.of("resourcepack/java/assets/minecraft/blockstates/note_block.json")),
            "the override must only live in the module layer");
    }

    @Test
    void blockAllocationIsStableAndUnique() throws IOException {
        // The registry rejects reserved (vanilla) and duplicated states, legacy ones included.
        ModBlockRegistry registry = ModBlockRegistry.parse(Files.readString(RESOURCES.resolve("alexscaves/blocks.yml")));
        assertTrue(registry.usedStates().size() > 20);
        for (CustomBlockState state : registry.usedStates()) {
            assertTrue(state.allocatable(), state.toString());
        }
        // Blocks that are not full cubes left the occluding note block (see-through floors).
        assertEquals(BlockBase.TRIPWIRE, registry.state("mussel", "up_1").orElseThrow().base());
        assertEquals(BlockBase.CHORUS_PLANT, registry.state("tube_worm", "straight_north").orElseThrow().base());
        assertEquals(BlockBase.NOTE_BLOCK, registry.state("guano_layer", "8").orElseThrow().base());
        assertEquals(BlockBase.NOTE_BLOCK, registry.state("galena", "default").orElseThrow().base());
        assertFalse(registry.legacy().isEmpty());
        registry.legacy().forEach((old, current) -> {
            assertEquals(BlockBase.NOTE_BLOCK, old.base());
            assertEquals(registry.at(old), registry.at(current), "a legacy state draws the same block");
        });
    }

    /** Golden check of the module layer: every state of every base resolves to a model that exists. */
    @Test
    void everyLayerVariantResolvesToAnExistingModel() throws IOException {
        ModBlockRegistry registry = ModBlockRegistry.parse(Files.readString(RESOURCES.resolve("alexscaves/blocks.yml")));
        Path layer = RESOURCES.resolve("alexscaves/pack-layer/assets/minecraft/blockstates");
        for (BlockBase base : List.of(BlockBase.TRIPWIRE, BlockBase.CHORUS_PLANT)) {
            JsonObject definition = JsonParser.parseString(Files.readString(layer.resolve(base.id() + ".json"))).getAsJsonObject();
            for (int index = 0; index < base.count(); index++) {
                Map<String, String> state = base.properties(index);
                List<String> models = models(definition, state);
                boolean custom = registry.at(new CustomBlockState(base, index)).isPresent();
                assertFalse(models.isEmpty(), base + " " + state);
                for (String model : models) {
                    assertTrue(modelExists(model), base + " " + state + " -> " + model);
                    assertEquals(custom, model.startsWith("alexscaves:"), base + " " + state + " -> " + model);
                }
                if (custom) {
                    assertEquals(1, models.size(), "a module state draws only its own model: " + state);
                }
            }
        }
        JsonObject notes = JsonParser.parseString(Files.readString(layer.resolve("note_block.json")))
            .getAsJsonObject().getAsJsonObject("variants");
        for (CustomBlockState state : registry.usedStates()) {
            if (state.base() == BlockBase.NOTE_BLOCK) {
                String key = state.blockDataString().replaceAll("^.*\\[|]$", "");
                String model = notes.getAsJsonObject(key).get("model").getAsString();
                // Retired variants (still reserved in blocks.yml) keep the plain note block look.
                assertTrue(modelExists(model), key + " -> " + model);
            }
        }
        String files = Files.readString(RESOURCES.resolve("alexscaves/pack-layer/files.txt"));
        assertTrue(files.contains("tripwire.json") && files.contains("chorus_plant.json"));
    }

    private static List<String> models(JsonObject definition, Map<String, String> state) {
        List<String> out = new java.util.ArrayList<>();
        if (definition.has("variants")) {
            String key = String.join(",", state.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
            JsonObject variant = definition.getAsJsonObject("variants").getAsJsonObject(key);
            if (variant != null) {
                out.add(variant.get("model").getAsString());
            }
            return out;
        }
        for (com.google.gson.JsonElement part : definition.getAsJsonArray("multipart")) {
            JsonObject object = part.getAsJsonObject();
            if (matches(object.getAsJsonObject("when"), state)) {
                com.google.gson.JsonElement apply = object.get("apply");
                out.add((apply.isJsonArray() ? apply.getAsJsonArray().get(0) : apply).getAsJsonObject().get("model").getAsString());
            }
        }
        return out;
    }

    private static boolean matches(JsonObject when, Map<String, String> state) {
        if (when.has("OR")) {
            for (com.google.gson.JsonElement option : when.getAsJsonArray("OR")) {
                if (matches(option.getAsJsonObject(), state)) {
                    return true;
                }
            }
            return false;
        }
        for (Map.Entry<String, com.google.gson.JsonElement> entry : when.entrySet()) {
            if (!entry.getValue().getAsString().equals(state.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /** The petrified oak slab is drawn as the mod slab: listed, valid, covering every type, with existing models. */
    @Test
    void petrifiedSlabLayerIsListedAndResolves() throws IOException {
        String files = Files.readString(RESOURCES.resolve("alexscaves/pack-layer/files.txt"));
        assertTrue(files.contains("assets/minecraft/blockstates/petrified_oak_slab.json"));
        assertTrue(files.contains("assets/minecraft/items/petrified_oak_slab.json"));
        JsonObject variants = JsonParser.parseString(Files.readString(
            RESOURCES.resolve("alexscaves/pack-layer/assets/minecraft/blockstates/petrified_oak_slab.json")))
            .getAsJsonObject().getAsJsonObject("variants");
        assertEquals(Set.of("type=bottom", "type=top", "type=double"), variants.keySet());
        variants.entrySet().forEach(entry ->
            assertTrue(modelExists(entry.getValue().getAsJsonObject().get("model").getAsString()), entry.getKey()));
    }

    private static boolean modelExists(String model) {
        if (model.startsWith("minecraft:")) {
            return true;
        }
        return Files.isRegularFile(Path.of("resourcepack/java/assets/alexscaves/models", model.substring("alexscaves:".length()) + ".json"));
    }

    @Test
    void geyserMappingsAndLayerListAreShipped() throws IOException {
        for (String path : List.of("alexscaves/geyser/alexscaves-items.json", "alexscaves/geyser/alexscaves-blocks.json")) {
            JsonParser.parseString(Files.readString(RESOURCES.resolve(path)));
        }
        assertTrue(Files.readString(RESOURCES.resolve("alexscaves/pack-layer/files.txt"))
            .contains("assets/minecraft/blockstates/note_block.json"));
    }

    private static InputStream resource(String path) {
        InputStream input = AlexsCavesContentContractTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(input, path);
        return input;
    }

    /** The validator's lookup accepts names the 26.2 server lacks (CHAIN is IRON_CHAIN there); check the API enum. */
    @Test
    void everyVanillaMaterialInTheItemFilesExistsIn26_2() throws IOException {
        java.util.regex.Pattern single = java.util.regex.Pattern.compile(
            "^\\s*(?:material|[A-Za-z#]):\\s*([A-Z][A-Z0-9_|]*)\\s*$|^\\s*-\\s*([A-Z][A-Z0-9_]*)\\s*$",
            java.util.regex.Pattern.MULTILINE);
        List<String> missing = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.list(Path.of("src/main/resources/content/items"))) {
            for (Path file : files.filter(path -> path.getFileName().toString().startsWith("ac_")).sorted().toList()) {
                java.util.regex.Matcher matcher = single.matcher(Files.readString(file));
                while (matcher.find()) {
                    String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                    for (String part : value.split("\\|")) {
                        if (!part.isEmpty() && org.bukkit.Material.getMaterial(part) == null) {
                            missing.add(file.getFileName() + ": " + part);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), missing);
    }
}
