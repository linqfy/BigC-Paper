package dev.linqfy.bigCasares.modules.alexscaves.core.mob;

import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Static description of one module mob: vanilla base, mod attributes, model,
 * spawn bucket and drops. Instances are programmer data validated at enable.
 *
 * @param id         mod id, e.g. {@code teletor}
 * @param baseEntity vanilla entity type name used as base (hitbox and AI)
 * @param attributes vanilla attribute key (e.g. {@code max_health}) to base value
 * @param modelKey   BetterModel key, or {@code null} for mobs without a model yet
 */
public record ModMobType(
    String id,
    CaveBiomeId biome,
    String baseEntity,
    SpawnCategory category,
    Map<String, Double> attributes,
    String modelKey,
    List<MobDrop> drops,
    boolean persistent
) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    public ModMobType(String id, CaveBiomeId biome, String baseEntity, SpawnCategory category,
                      Map<String, Double> attributes, String modelKey, List<MobDrop> drops) {
        this(id, biome, baseEntity, category, attributes, modelKey, drops, false);
    }

    /** Same type flagged as a boss: it is kept across chunk unloads and never despawns when far away. */
    public ModMobType asBoss() {
        return new ModMobType(id, biome, baseEntity, category, attributes, modelKey, drops, true);
    }

    public ModMobType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(biome, "biome");
        Objects.requireNonNull(baseEntity, "baseEntity");
        Objects.requireNonNull(category, "category");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid mob id: " + id);
        }
        attributes = Map.copyOf(attributes);
        drops = List.copyOf(drops);
        for (Map.Entry<String, Double> attribute : attributes.entrySet()) {
            if (!(attribute.getValue() >= 0) || !Double.isFinite(attribute.getValue())) {
                throw new IllegalArgumentException("Invalid attribute " + attribute.getKey() + " for " + id);
            }
        }
    }

    /** Value of {@code bigcasares:custom_entity}, which Mob Scaling and Blood Moon use to skip module mobs. */
    public String customEntityTag() {
        return ModMobIdentity.TAG_PREFIX + id;
    }

    /** Marker used by the Geyser bridge to give Bedrock players the custom entity. */
    public String geyserMarker() {
        return "alexscaves_" + id;
    }

    public double attribute(String key, double fallback) {
        return attributes.getOrDefault(key, fallback);
    }
}
