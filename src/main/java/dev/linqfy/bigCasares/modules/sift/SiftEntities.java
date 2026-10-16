package dev.linqfy.bigCasares.modules.sift;

import java.util.Map;

/**
 * Server-side backings for the mod's creatures. Vanilla clients cannot know new
 * entity types, so each creature is an ordinary vanilla entity (spawned natively
 * by the biome's spawn table) that the Sift runtime converts and dresses with a
 * BetterModel model.
 */
public final class SiftEntities {

    /** Mod entity id &rarr; vanilla entity spawned natively by the biome. */
    private static final Map<String, String> SPAWN_BACKINGS = Map.of(
        "minecraft:sniffer", "minecraft:sniffer",
        // Rabbit-like and neutral in the mod; a rabbit gives the matching AI and hitbox.
        "the_sift:blub", "minecraft:rabbit"
    );

    private SiftEntities() {
    }

    /** Vanilla entity to list in biome spawners, or null when the creature is not ported yet. */
    public static String spawnBacking(String modEntity) {
        return SPAWN_BACKINGS.get(modEntity);
    }
}
