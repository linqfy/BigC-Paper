package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;

import java.util.List;
import java.util.Objects;

/**
 * A data-driven jigsaw structure plus everything it needs: template pools,
 * processor lists, templates, the biome tag that restricts it and the structure
 * set that spaces it. Minecraft plans structure starts per chunk from the seed
 * and the set's salt, so placement is deterministic and chunk-border safe.
 */
public record StructureDefinition(
    NamespacedId id,
    List<NamespacedId> biomes,
    TemplatePool startPool,
    List<TemplatePool> extraPools,
    String step,
    int size,
    VerticalAnchor startHeight,
    String projectStartToHeightmap,
    int maxDistanceFromCenter,
    String terrainAdaptation,
    Placement placement
) {

    public StructureDefinition {
        Objects.requireNonNull(id, "id");
        biomes = List.copyOf(biomes);
        if (biomes.isEmpty()) {
            throw new IllegalArgumentException("Structure " + id + " needs at least one biome");
        }
        Objects.requireNonNull(startPool, "startPool");
        extraPools = List.copyOf(extraPools);
        Objects.requireNonNull(step, "step");
        Objects.requireNonNull(startHeight, "startHeight");
        if (size < 1 || size > 20) {
            throw new IllegalArgumentException("Jigsaw size must be within 1..20");
        }
        if (maxDistanceFromCenter < 1 || maxDistanceFromCenter > 128) {
            throw new IllegalArgumentException("max_distance_from_center must be within 1..128");
        }
        if (!List.of("none", "bury", "beard_thin", "beard_box", "encapsulate").contains(terrainAdaptation)) {
            throw new IllegalArgumentException("Unknown terrain adaptation: " + terrainAdaptation);
        }
        Objects.requireNonNull(placement, "placement");
    }

    /** Random-spread placement: one attempt per {@code spacing x spacing} chunk cell. */
    public record Placement(int spacing, int separation, int salt) {
        public Placement {
            if (spacing <= 0 || separation < 0 || separation >= spacing) {
                throw new IllegalArgumentException("Structure spacing must exceed separation");
            }
        }

        JsonObject toJson() {
            return Json.object()
                .put("type", "minecraft:random_spread")
                .put("salt", salt)
                .put("separation", separation)
                .put("spacing", spacing)
                .build();
        }
    }

    /** One pool element: a template placed with an optional processor list. */
    public record PoolElement(StructureTemplate template, ProcessorList processors, String projection, int weight) {
        public PoolElement {
            Objects.requireNonNull(template, "template");
            if (!"rigid".equals(projection) && !"terrain_matching".equals(projection)) {
                throw new IllegalArgumentException("Unknown projection: " + projection);
            }
            if (weight < 1 || weight > 150) {
                throw new IllegalArgumentException("Pool element weight must be within 1..150");
            }
        }
    }

    public record TemplatePool(NamespacedId id, List<PoolElement> elements) {
        public TemplatePool {
            Objects.requireNonNull(id, "id");
            elements = List.copyOf(elements);
            if (elements.isEmpty()) {
                throw new IllegalArgumentException("Template pool " + id + " is empty");
            }
        }

        public JsonObject toJson() {
            JsonArray array = new JsonArray();
            for (PoolElement element : elements) {
                array.add(Json.object()
                    .put("element", Json.object()
                        .put("element_type", "minecraft:single_pool_element")
                        .put("location", element.template().id())
                        .put("processors", element.processors() == null
                            ? "minecraft:empty" : element.processors().id().asString())
                        .put("projection", element.projection())
                        .build())
                    .put("weight", element.weight())
                    .build());
            }
            return Json.object().put("elements", array).put("fallback", "minecraft:empty").build();
        }
    }

    /** A {@code worldgen/processor_list}; processors are vanilla JSON. */
    public record ProcessorList(NamespacedId id, List<JsonObject> processors) {
        public ProcessorList {
            Objects.requireNonNull(id, "id");
            processors = processors.stream().map(JsonObject::deepCopy).toList();
        }

        public JsonObject toJson() {
            JsonArray array = new JsonArray();
            processors.forEach(processor -> array.add(processor.deepCopy()));
            return Json.object().put("processors", array).build();
        }
    }

    public NamespacedId biomeTag() {
        return NamespacedId.of(id.namespace(), "has_structure/" + id.path().replace('/', '_'));
    }

    public JsonObject toJson() {
        Json.ObjectBuilder builder = Json.object()
            .put("type", "minecraft:jigsaw")
            .put("biomes", "#" + biomeTag().asString())
            .put("max_distance_from_center", maxDistanceFromCenter);
        if (projectStartToHeightmap != null) {
            builder.put("project_start_to_heightmap", projectStartToHeightmap);
        }
        return builder
            .put("size", size)
            .put("spawn_overrides", new JsonObject())
            .put("start_height", startHeight)
            .put("start_pool", startPool.id())
            .put("step", step)
            .put("terrain_adaptation", terrainAdaptation)
            .put("use_expansion_hack", false)
            .build();
    }

    public JsonObject structureSetJson() {
        JsonArray structures = new JsonArray();
        structures.add(Json.object().put("structure", id).put("weight", 1).build());
        return Json.object().put("placement", placement.toJson()).put("structures", structures).build();
    }
}
