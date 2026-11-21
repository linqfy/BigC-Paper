package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.BlockBase;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated table, its pack layer files, and the golden rules: solid server states only borrow full-collision
 * families, real vanilla blocks are normalised to a state that looks the same, and every state the remap does not
 * borrow draws exactly as vanilla 26.2.
 */
class ClientRemapTableTest {
    private static final Path RESOURCES = Path.of("src/main/resources/alexscaves");
    private static final Path LAYER = RESOURCES.resolve("pack-layer");
    private static final Path BLOCKSTATES = LAYER.resolve("assets/minecraft/blockstates");

    private static ClientRemapTable shipped() throws IOException {
        return ClientRemapTable.parse(Files.readString(RESOURCES.resolve("client-remap.txt")));
    }

    private static ModBlockRegistry registry(boolean shapedBases) throws IOException {
        return ModBlockRegistry.parse(Files.readString(RESOURCES.resolve("blocks.yml")), shapedBases);
    }

    private static String block(String state) {
        return state.substring("minecraft:".length(), state.indexOf('['));
    }

    private static Map<String, String> properties(String state) {
        Map<String, String> out = new TreeMap<>();
        for (String pair : state.substring(state.indexOf('[') + 1, state.length() - 1).split(",")) {
            String[] parts = pair.split("=");
            out.put(parts[0], parts[1]);
        }
        return out;
    }

    private static boolean leaves(String state) {
        return block(state).endsWith("_leaves");
    }

    @Test
    void shippedTableMatchesBlocksYml() throws IOException {
        ClientRemapTable table = shipped();
        assertEquals(List.of(), table.mismatches(registry(true)));
        assertTrue(table.entries().size() > 150, "every non-full and waterloggable state: " + table.entries().size());
        // Without Paper's tripwire / chorus options only the note block entries stay, and they still match.
        ClientRemapTable legacy = table.forRegistry(registry(false));
        assertEquals(List.of(), legacy.mismatches(registry(false)));
        assertTrue(legacy.entries().stream().allMatch(entry -> entry.server().base() == BlockBase.NOTE_BLOCK));
        assertTrue(legacy.entries().size() < table.entries().size());
        for (ClientRemapTable.Entry entry : legacy.entries()) {
            for (String client : entry.clientStates()) {
                assertEquals(table.normalize().get(client), legacy.normalize().get(client));
            }
        }
        assertEquals(legacy.entries().stream().mapToInt(entry -> entry.clientStates().size()).sum(),
            legacy.normalize().size());
    }

    @Test
    void serverCollisionIsNeverBiggerThanTheClients() throws IOException {
        for (ClientRemapTable.Entry entry : shipped().entries()) {
            BlockBase base = entry.server().base();
            if (base != BlockBase.NOTE_BLOCK) {
                assertNull(entry.dry(), "tripwire and chorus plant do not hide their neighbours: " + entry);
            }
            for (String client : entry.clientStates()) {
                if (base != BlockBase.TRIPWIRE) {
                    // Note block and chorus plant collide: only full-collision families.
                    assertTrue(leaves(client) || block(client).equals("chorus_flower"), entry + " -> " + client);
                }
            }
        }
    }

    @Test
    void wetStatesHoldWaterAndDryOnesDoNot() throws IOException {
        int wet = 0;
        for (ClientRemapTable.Entry entry : shipped().entries()) {
            if (entry.dry() != null) {
                assertFalse(entry.dry().contains("waterlogged=true"), entry.dry());
                assertFalse(block(entry.dry()).equals("kelp"), entry.dry());
            }
            if (entry.wet() != null) {
                wet++;
                assertTrue(block(entry.wet()).equals("kelp") || entry.wet().contains("waterlogged=true"), entry.wet());
            }
        }
        assertTrue(wet > 100, "waterloggable states covered: " + wet);
    }

    @Test
    void realBlocksAreNormalisedToALookAlikeState() throws IOException {
        for (Map.Entry<String, String> normal : shipped().normalize().entrySet()) {
            String borrowed = normal.getKey();
            String canonical = normal.getValue();
            assertEquals(block(borrowed), block(canonical));
            Map<String, String> expected = new TreeMap<>(properties(borrowed));
            switch (block(borrowed)) {
                case "kelp" -> expected.put("age", "25");
                case "chorus_flower" -> expected.put("age", "4");
                default -> {
                    if (leaves(borrowed)) {
                        expected.put("distance", "7");
                        expected.put("persistent", "false");
                    } else if (block(borrowed).endsWith("lightning_rod")) {
                        expected.put("powered", "false");
                    } else {
                        throw new AssertionError("familia desconocida " + borrowed);
                    }
                }
            }
            assertEquals(expected, properties(canonical), borrowed);
        }
    }

    @Test
    void borrowedBlockstatesDrawTheModuleOnlyOnBorrowedStates() throws IOException {
        ClientRemapTable table = shipped();
        Map<String, JsonElement> rendered = new HashMap<>();
        for (ClientRemapTable.Entry entry : table.entries()) {
            JsonElement model = serverModel(entry);
            for (String client : entry.clientStates()) {
                rendered.put(client, model);
            }
        }
        Set<String> families = new TreeSet<>();
        for (String client : rendered.keySet()) {
            families.add(block(client));
        }
        int borrowed = 0;
        for (String family : families) {
            JsonObject variants = json(BLOCKSTATES.resolve(family + ".json")).getAsJsonObject("variants");
            for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
                String state = "minecraft:" + family + "[" + variant.getKey() + "]";
                JsonObject value = variant.getValue().getAsJsonObject();
                if (rendered.containsKey(state)) {
                    borrowed++;
                    assertEquals(rendered.get(state), value, state);
                    assertTrue(value.get("model").getAsString().startsWith("alexscaves:"), state);
                } else {
                    assertTrue(value.get("model").getAsString().startsWith("minecraft:block/"), state);
                }
                if (table.normalize().containsValue(state)) {
                    assertFalse(rendered.containsKey(state), "a canonical state is never borrowed: " + state);
                }
            }
        }
        assertEquals(rendered.size(), borrowed, "every borrowed state is in its family's blockstate");
    }

    @Test
    void borrowedBlockstatesOnlyShipWithTheRemap() throws IOException {
        Set<String> remapFiles = new TreeSet<>(lines(LAYER.resolve("files-packet-remap.txt")));
        Set<String> expected = new TreeSet<>();
        for (String client : shipped().normalize().keySet()) {
            expected.add("assets/minecraft/blockstates/" + block(client) + ".json");
        }
        assertEquals(expected, remapFiles);
        for (String file : remapFiles) {
            assertTrue(Files.isRegularFile(LAYER.resolve(file)), file);
        }
        for (String file : lines(LAYER.resolve("files.txt"))) {
            assertFalse(remapFiles.contains(file), file + " must not ship without the remap");
        }
    }

    @Test
    void parsingIsStrict() {
        String note = "block thin_bone x_0 note_block 428 minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false] -\n"
            + "normalize minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false] "
            + "minecraft:oak_leaves[distance=7,persistent=false,waterlogged=false]\n";
        ClientRemapTable table = ClientRemapTable.parse("# comment\n\n" + note);
        assertEquals(1, table.entries().size());
        ClientRemapTable.Entry entry = table.entries().get(0);
        assertEquals(BlockBase.NOTE_BLOCK.blockDataString(428), entry.serverState());
        assertNull(entry.wet());
        assertEquals(1, table.normalize().size());

        assertThrows(IllegalStateException.class, () -> ClientRemapTable.parse(note.replace("] -\n", "]\n")), "columns");
        assertThrows(IllegalStateException.class, () -> ClientRemapTable.parse(
            note.replace("note_block 428", "note_block 10")), "harp note blocks stay vanilla");
        assertThrows(IllegalStateException.class, () -> ClientRemapTable.parse(
            note.replace("note_block 428", "stone 428")), "unknown base");
        assertThrows(IllegalStateException.class, () -> ClientRemapTable.parse(
            "block a b note_block 428 - -\n"), "an entry needs a client state");
        assertThrows(IllegalStateException.class, () -> ClientRemapTable.parse(
            note.substring(0, note.indexOf('\n') + 1)), "a borrowed state must be normalised");
        assertThrows(IllegalStateException.class, () -> ClientRemapTable.parse(
            note + "block a b note_block 429 minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false] -\n"),
            "one module state per client state");
        assertThrows(IllegalStateException.class, () -> ClientRemapTable.parse(
            note + "block a b note_block 428 - minecraft:kelp[age=0]\nnormalize minecraft:kelp[age=0] minecraft:kelp[age=25]\n"),
            "one entry per server state");
        assertThrows(IllegalStateException.class, () -> ClientRemapTable.parse(note
            + "normalize minecraft:oak_leaves[distance=7,persistent=false,waterlogged=false] "
            + "minecraft:oak_leaves[distance=6,persistent=false,waterlogged=false]\n"), "canonical states are never borrowed");
        assertThrows(IllegalStateException.class, () -> ClientRemapTable.parse(
            note.replace("minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false] -", "oak_leaves -")),
            "full state strings");
    }

    /** The rendered model of a server state in the pack layer (note block / tripwire variants, chorus multipart). */
    private static JsonElement serverModel(ClientRemapTable.Entry entry) throws IOException {
        BlockBase base = entry.server().base();
        Map<String, String> props = base.properties(entry.server().index());
        JsonObject blockstate = json(BLOCKSTATES.resolve(base.id() + ".json"));
        if (blockstate.has("variants")) {
            StringBuilder key = new StringBuilder();
            props.forEach((name, value) -> key.append(key.isEmpty() ? "" : ",").append(name).append('=').append(value));
            JsonElement variant = blockstate.getAsJsonObject("variants").get(key.toString());
            assertNotNull(variant, entry.toString());
            return variant;
        }
        for (JsonElement part : blockstate.getAsJsonArray("multipart")) {
            JsonObject when = part.getAsJsonObject().getAsJsonObject("when");
            if (!when.has("OR") && when.size() == props.size()
                && props.entrySet().stream().allMatch(p -> when.has(p.getKey())
                && when.get(p.getKey()).getAsString().equals(p.getValue()))) {
                return part.getAsJsonObject().get("apply");
            }
        }
        throw new AssertionError("sin modelo para " + entry);
    }

    private static JsonObject json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static Set<String> lines(Path file) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        for (String line : Files.readAllLines(file)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                out.add(line.trim());
            }
        }
        return out;
    }
}
