package dev.linqfy.bigCasares.modules.sift;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;

/**
 * The Soul Block in a world: what the block looks like to the server (a reserved mushroom block state, see
 * {@code SiftBlocks}), checks that never load a chunk, and a scan of a chunk for Soul Blocks. Echo Golems and
 * Singers share it.
 */
final class SoulBlockWorld {

    private final World world;
    private final BlockData soulData;

    SoulBlockWorld(World world) {
        this.world = world;
        this.soulData = soulBlockData();
    }

    /** The server-side state of the Soul Block. */
    static BlockData soulBlockData() {
        return Bukkit.createBlockData(SiftBlocks.custom("soul_block")
            .orElseThrow(() -> new IllegalStateException("Unknown Sift block soul_block")).state().asString());
    }

    World world() {
        return world;
    }

    boolean isLoaded(SiftPos pos) {
        return pos.y() >= world.getMinHeight() && pos.y() < world.getMaxHeight() && world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4);
    }

    /** Whether the block is a Soul Block; an unloaded chunk is not loaded to find out. */
    boolean isSoulBlock(SiftPos pos) {
        return isLoaded(pos) && world.getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData().equals(soulData);
    }

    boolean isSoulBlock(BlockData data) {
        return data.equals(soulData);
    }

    /** The recorded Soul Blocks near a position that still are Soul Blocks in a loaded chunk. */
    List<SiftPos> activeNear(SiftLandmarks landmarks, SiftPos center, int horizontal, int vertical) {
        List<SiftPos> result = new ArrayList<>();
        for (SiftPos candidate : landmarks.soulBlocksNear(center, horizontal, vertical)) {
            if (isSoulBlock(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /** Records every Soul Block of a loaded chunk; most chunks are skipped by looking at their palettes. */
    int scan(Chunk chunk, SiftLandmarks landmarks) {
        if (!chunk.contains(soulData)) {
            return 0;
        }
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        int found = 0;
        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (snapshot.getBlockData(x, y, z).equals(soulData)) {
                        landmarks.record(SiftLandmarks.Kind.SOUL_BLOCK, new SiftPos((chunk.getX() << 4) + x, y, (chunk.getZ() << 4) + z));
                        found++;
                    }
                }
            }
        }
        return found;
    }
}
