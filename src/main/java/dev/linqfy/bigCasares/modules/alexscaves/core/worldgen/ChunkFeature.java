package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

/** Terrain or decoration of one cave biome, applied to newly generated chunks. */
public interface ChunkFeature {
    /** Stable id; also salts the feature's random so features stay independent. */
    String id();

    CaveBiomeId biome();

    void place(CaveChunk chunk);

    /**
     * True for structures: their pieces are placed in every chunk they reach, whatever the biome there (vanilla
     * {@code postProcess}); the structure checks its own biome where it starts.
     */
    default boolean placesAnywhere() {
        return false;
    }
}
