package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.bukkit.block.structure.StructureRotation;

/** Vanilla {@code StructureTemplate.transform} of a template position around the zero corner. Pure logic. */
public final class StructureRotations {
    private StructureRotations() {
    }

    /** Rotated {@code {x, z}} of a template position. */
    public static int[] rotate(int x, int z, StructureRotation rotation) {
        return switch (rotation) {
            case COUNTERCLOCKWISE_90 -> new int[] {z, -x};
            case CLOCKWISE_90 -> new int[] {-z, x};
            case CLOCKWISE_180 -> new int[] {-x, -z};
            case NONE -> new int[] {x, z};
        };
    }
}
