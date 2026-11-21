package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import dev.linqfy.bigCasares.dimension.nbt.Nbt;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reads a generated pack back: templates (from their NBT bytes) and template pools (from JSON). */
final class MythicPackIndex {

    record Element(String location, int weight) {
    }

    record Pool(String id, List<Element> elements, String fallback) {
    }

    final GeneratedDatapack pack;
    final Map<String, StructureTemplate> templates = new TreeMap<>();
    final Map<String, Pool> pools = new TreeMap<>();

    MythicPackIndex(GeneratedDatapack pack) {
        this(pack, null);
    }

    /**
     * Index of one culture only ({@code null}: all). The decoded templates of the whole pack do not fit
     * the shared test heap next to the other worldgen tests, so tests check cultures one at a time.
     */
    MythicPackIndex(GeneratedDatapack pack, String culture) {
        this.pack = pack;
        String prefix = culture == null ? null : culture + "/";
        for (String path : pack.paths()) {
            String[] parts = path.split("/", 3);
            if (parts.length < 3 || !parts[0].equals("data")) {
                continue;
            }
            String namespace = parts[1];
            String rest = parts[2];
            if (prefix != null && !(namespace.equals(MythicDungeonTheme.NAMESPACE)
                && (rest.startsWith("structure/" + prefix) || rest.startsWith("worldgen/template_pool/" + prefix)))) {
                continue;
            }
            if (rest.startsWith("structure/") && rest.endsWith(".nbt")) {
                String id = namespace + ":" + rest.substring("structure/".length(), rest.length() - 4);
                try {
                    templates.put(id, StructureTemplate.of(NamespacedId.parse(id), Nbt.readGzip(pack.file(path))));
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            } else if (rest.startsWith("worldgen/template_pool/")) {
                String id = namespace + ":" + rest.substring("worldgen/template_pool/".length(), rest.length() - 5);
                JsonObject json = json(path).getAsJsonObject();
                List<Element> elements = new ArrayList<>();
                for (JsonElement element : json.getAsJsonArray("elements")) {
                    JsonObject entry = element.getAsJsonObject();
                    elements.add(new Element(entry.getAsJsonObject("element").get("location").getAsString(),
                        entry.get("weight").getAsInt()));
                }
                pools.put(id, new Pool(id, elements, json.get("fallback").getAsString()));
            }
        }
    }

    JsonElement json(String path) {
        return JsonParser.parseString(pack.text(path));
    }

    static String jsonPath(String registry, String id) {
        NamespacedId parsed = NamespacedId.parse(id);
        return "data/" + parsed.namespace() + "/" + registry + "/" + parsed.path() + ".json";
    }

    /** Templates of a pool (empty for {@code minecraft:empty}). */
    List<StructureTemplate> poolTemplates(String pool) {
        Pool definition = pools.get(pool);
        if (definition == null) {
            return List.of();
        }
        return definition.elements().stream().map(element -> templates.get(element.location())).toList();
    }

    List<StructureTemplate> cultureTemplates(String culture) {
        String prefix = MythicDungeonTheme.NAMESPACE + ":" + culture + "/";
        return templates.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
            .map(Map.Entry::getValue).toList();
    }
}
