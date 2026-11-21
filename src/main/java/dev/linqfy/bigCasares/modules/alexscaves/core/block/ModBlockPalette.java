package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Block data of module blocks for world generation. Safe to use from Paper's
 * generation threads: entries are created once and cached.
 */
public final class ModBlockPalette {
    private final ModBlockRegistry registry;
    private final Map<String, BlockData> cache = new ConcurrentHashMap<>();

    public ModBlockPalette(ModBlockRegistry registry) {
        this.registry = registry;
    }

    public BlockData get(String blockId) {
        return get(blockId, BlockVariants.DEFAULT);
    }

    public BlockData get(String blockId, String variant) {
        return cache.computeIfAbsent(blockId + "#" + variant, ignored -> {
            CustomBlockState state = registry.state(blockId, variant)
                .orElseThrow(() -> new IllegalStateException("Bloque sin estado: " + blockId + " " + variant));
            return Bukkit.createBlockData(state.blockDataString());
        });
    }

    /** Creates every block state up front on the main thread, so worldgen threads only read the cache. */
    public ModBlockPalette preload() {
        ModBlockStates.publish(registry);
        for (String blockId : registry.blockIds()) {
            for (String variant : registry.variants(blockId)) {
                get(blockId, variant);
            }
        }
        return this;
    }

    public ModBlockRegistry registry() {
        return registry;
    }
}
