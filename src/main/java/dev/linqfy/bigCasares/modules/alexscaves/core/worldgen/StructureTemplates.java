package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.CavePaintings;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.FacingDisplays;
import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.structure.Structure;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The mod's structure templates converted for the module ({@code tools/alexscaves/convert_structures.py}): module
 * blocks as note block states, loot chests pointing at the module's loot tables. Loaded on the main thread.
 */
public final class StructureTemplates {
    private static final String ROOT = "alexscaves/structures/";

    private StructureTemplates() {
    }

    /** Every template of a group ({@code magnetic_ruins}, {@code toxic_ruins}, {@code caveman_house}). */
    public static List<Structure> load(String group) {
        JsonObject index;
        try (InputStream input = resource(ROOT + "index.json")) {
            index = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        JsonArray names = index.getAsJsonArray(group);
        if (names == null || names.isEmpty()) {
            throw new IllegalStateException("Sin plantillas para " + group);
        }
        List<Structure> structures = new ArrayList<>();
        for (var name : names) {
            try (InputStream input = resource(ROOT + name.getAsString() + ".nbt")) {
                structures.add(Bukkit.getStructureManager().loadStructure(input));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
        return List.copyOf(structures);
    }

    /**
     * The cave paintings of each template of a group ({@code paintings.json}, written by {@code generate_content.py}),
     * in the order of {@link #load}; templates without paintings get an empty list.
     */
    public static List<List<CavePaintings.Mark>> loadPaintings(String group) {
        JsonObject index;
        JsonObject marks;
        try (InputStream input = resource(ROOT + "index.json"); InputStream paintings = resource(ROOT + "paintings.json")) {
            index = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            marks = JsonParser.parseReader(new InputStreamReader(paintings, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        List<List<CavePaintings.Mark>> result = new ArrayList<>();
        for (var name : index.getAsJsonArray(group)) {
            result.add(parseMarks(marks.getAsJsonArray(name.getAsString())));
        }
        return List.copyOf(result);
    }

    /** {@code [[x, y, z, painting, facing], ...]} of one template (null: none). Pure logic. */
    static List<CavePaintings.Mark> parseMarks(JsonArray entries) {
        if (entries == null) {
            return List.of();
        }
        List<CavePaintings.Mark> marks = new ArrayList<>();
        for (var entry : entries) {
            JsonArray mark = entry.getAsJsonArray();
            BlockFace facing = FacingDisplays.parse(mark.get(4).getAsString());
            if (facing == null) {
                throw new IllegalStateException("Cara de pintura desconocida: " + mark);
            }
            marks.add(new CavePaintings.Mark(mark.get(0).getAsInt(), mark.get(1).getAsInt(), mark.get(2).getAsInt(),
                mark.get(3).getAsString(), facing));
        }
        return List.copyOf(marks);
    }

    private static InputStream resource(String path) throws IOException {
        InputStream input = StructureTemplates.class.getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IOException("Falta " + path + " en el JAR");
        }
        return input;
    }
}
