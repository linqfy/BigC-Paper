package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.NoteBlockService;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Turning module blocks with a structure template. Note block states do not rotate, so the module's facing and axis
 * blocks swap to the variant the mod's block would turn into. Variants are named after the facing or axis first
 * ({@code north}, {@code east_2}, {@code x_active}); {@code baleen_bone} keeps its {@code x} flag as a variant.
 */
public final class NoteBlockRotations {
    private final Map<StructureRotation, Map<BlockData, BlockData>> turns = new EnumMap<>(StructureRotation.class);
    /** Every module state: tripwire and chorus plant states must not be turned by Bukkit either. */
    private final java.util.Set<BlockData> moduleStates = new java.util.HashSet<>();

    private NoteBlockRotations() {
    }

    /** Built on the main thread from every module block. */
    public static NoteBlockRotations of(ModBlockPalette palette) {
        NoteBlockRotations rotations = new NoteBlockRotations();
        for (String block : palette.registry().blockIds()) {
            for (String variant : palette.registry().variants(block)) {
                rotations.moduleStates.add(palette.get(block, variant));
            }
        }
        for (StructureRotation rotation : StructureRotation.values()) {
            Map<BlockData, BlockData> turn = new HashMap<>();
            if (rotation != StructureRotation.NONE) {
                for (String block : palette.registry().blockIds()) {
                    for (String variant : palette.registry().variants(block)) {
                        String turned = turnVariant(block, variant, rotation);
                        if (turned != null && !turned.equals(variant) && palette.registry().variants(block).contains(turned)) {
                            turn.put(palette.get(block, variant), palette.get(block, turned));
                        }
                    }
                }
            }
            rotations.turns.put(rotation, Map.copyOf(turn));
        }
        return rotations;
    }

    /** The block turned with the template: module blocks by variant, vanilla blocks by Bukkit. */
    public BlockData rotate(BlockData data, StructureRotation rotation) {
        if (rotation == StructureRotation.NONE) {
            return data;
        }
        BlockData turned = turns.get(rotation).get(data);
        if (turned != null) {
            return turned;
        }
        if (data.getMaterial() == org.bukkit.Material.NOTE_BLOCK
            || NoteBlockService.isBase(data.getMaterial()) && moduleStates.contains(data)) {
            return data;
        }
        BlockData copy = data.clone();
        copy.rotate(rotation);
        return copy;
    }

    /** The variant name a rotation turns a variant into, or null when it does not turn. Pure logic. */
    static String turnVariant(String block, String variant, StructureRotation rotation) {
        boolean quarter = rotation == StructureRotation.CLOCKWISE_90 || rotation == StructureRotation.COUNTERCLOCKWISE_90;
        if (block.equals("baleen_bone")) {
            if (!quarter) {
                return null;
            }
            return variant.equals("x") ? "default" : "x";
        }
        int cut = variant.indexOf('_');
        String head = cut < 0 ? variant : variant.substring(0, cut);
        String tail = cut < 0 ? "" : variant.substring(cut);
        String turned = switch (head) {
            case "x" -> quarter ? "z" : "x";
            case "z" -> quarter ? "x" : "z";
            case "north", "east", "south", "west" -> horizontal(head, rotation);
            default -> null;
        };
        return turned == null ? null : turned + tail;
    }

    private static String horizontal(String facing, StructureRotation rotation) {
        String[] clockwise = {"north", "east", "south", "west"};
        int index = java.util.Arrays.asList(clockwise).indexOf(facing);
        int steps = switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            case NONE -> 0;
        };
        return clockwise[(index + steps) % 4];
    }
}
