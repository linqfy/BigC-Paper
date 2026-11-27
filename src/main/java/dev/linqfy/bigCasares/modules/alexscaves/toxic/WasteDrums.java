package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockRegistry;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** The waste drum block states (one per facing), read once from the packaged block allocation. */
final class WasteDrums {
    private static final String BLOCKS_FILE = "/alexscaves/blocks.yml";
    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};
    private static Set<BlockData> states;
    private static BlockData up;

    private WasteDrums() {
    }

    /** Every waste drum state; empty when the allocation cannot be read. Main thread only. */
    static synchronized Set<BlockData> states() {
        if (states == null) {
            Set<BlockData> loaded = new HashSet<>();
            try (InputStream input = WasteDrums.class.getResourceAsStream(BLOCKS_FILE)) {
                if (input != null) {
                    ModBlockRegistry registry = ModBlockRegistry.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                    for (String face : FACES) {
                        registry.state("waste_drum", face).ifPresent(state -> {
                            BlockData data = Bukkit.createBlockData(state.blockDataString());
                            loaded.add(data);
                            if (face.equals("up")) {
                                up = data;
                            }
                        });
                    }
                }
            } catch (IOException ignored) {
                // No drums to find: the Brainiac simply never picks one up.
            }
            states = Set.copyOf(loaded);
        }
        return states;
    }

    /** The state the thrown drum is drawn with. */
    static BlockData thrown() {
        states();
        return up != null ? up : Bukkit.createBlockData(org.bukkit.Material.BARREL);
    }
}
