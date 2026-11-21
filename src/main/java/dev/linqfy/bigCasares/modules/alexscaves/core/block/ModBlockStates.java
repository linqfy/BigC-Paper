package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Module block states for code that has no block service at hand (mob block-breaking rules, structure templates).
 * Published once the module's block palette is built, cleared at shutdown. The tripwire and chorus plant states stay
 * empty while those bases are not in use, so a vanilla string or chorus plant is always an ordinary block then.
 */
public final class ModBlockStates {
    private static volatile Set<BlockData> chorus = Set.of();
    private static volatile Set<BlockData> tripwire = Set.of();
    private static volatile Map<BlockData, BlockData> legacy = Map.of();

    private ModBlockStates() {
    }

    /** Reads the registry's tripwire / chorus plant states and the legacy note block states they replace. */
    static void publish(ModBlockRegistry registry) {
        Set<BlockData> chorusStates = new HashSet<>();
        Set<BlockData> tripwireStates = new HashSet<>();
        for (CustomBlockState state : registry.usedStates()) {
            if (state.base() == BlockBase.CHORUS_PLANT) {
                chorusStates.add(Bukkit.createBlockData(state.blockDataString()));
            } else if (state.base() == BlockBase.TRIPWIRE) {
                tripwireStates.add(Bukkit.createBlockData(state.blockDataString()));
            }
        }
        Map<BlockData, BlockData> moved = new HashMap<>();
        registry.legacy().forEach((old, current) ->
            moved.put(Bukkit.createBlockData(old.blockDataString()), Bukkit.createBlockData(current.blockDataString())));
        chorus = Set.copyOf(chorusStates);
        tripwire = Set.copyOf(tripwireStates);
        legacy = Map.copyOf(moved);
    }

    static void clear() {
        chorus = Set.of();
        tripwire = Set.of();
        legacy = Map.of();
    }

    /** Whether this chorus plant or tripwire block is a module block (always false for any other block). */
    public static boolean isModuleShaped(Block block) {
        return switch (block.getType()) {
            case CHORUS_PLANT -> !chorus.isEmpty() && chorus.contains(block.getBlockData());
            case TRIPWIRE -> !tripwire.isEmpty() && tripwire.contains(block.getBlockData());
            default -> false;
        };
    }

    /** Whether this state is a module chorus plant or tripwire state (templates must not rotate those with Bukkit). */
    public static boolean isModuleShaped(BlockData data) {
        return switch (data.getMaterial()) {
            case CHORUS_PLANT -> chorus.contains(data);
            case TRIPWIRE -> tripwire.contains(data);
            default -> false;
        };
    }

    /**
     * A structure template state brought up to date: the structure files keep the note block states the blocks had
     * when they were converted, so a block that moved to tripwire or chorus plant is read as its new state.
     */
    public static BlockData modernize(BlockData data) {
        Map<BlockData, BlockData> moved = legacy;
        if (moved.isEmpty() || data.getMaterial() != Material.NOTE_BLOCK) {
            return data;
        }
        return moved.getOrDefault(data, data);
    }
}
