package dev.linqfy.bigCasares.dimension.datapack;

import dev.linqfy.bigCasares.dimension.worldgen.GenerationContext;

/**
 * Adds extra datapack resources for a dimension: loot tables, advancements,
 * tags, jukebox songs, or imported raw JSON. Called once per compiled instance.
 */
@FunctionalInterface
public interface DatapackContributor {

    void contribute(GenerationContext context, DatapackBuilder datapack);
}
