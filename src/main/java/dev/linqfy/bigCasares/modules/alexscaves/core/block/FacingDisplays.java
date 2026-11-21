package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import org.bukkit.block.BlockFace;
import org.bukkit.block.structure.StructureRotation;
import org.joml.Quaternionf;

/**
 * Orientation of the {@code ItemDisplay}s that draw the mod's directional blocks without a block state (cave
 * paintings, dinosaur chops). Their models are the mod's {@code facing=north} model; the block states of the mod turn
 * it with {@code x}/{@code y} (degrees, clockwise) per facing. Pure logic.
 */
public final class FacingDisplays {
    /** The six facings in the order of the mod's {@code DirectionProperty}. */
    public static final BlockFace[] FACINGS = {BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
        BlockFace.WEST, BlockFace.EAST};

    private FacingDisplays() {
    }

    /** Block state rotation {@code {x, y}} of the mod's models for a facing (cave_painting / dinosaur_chop). */
    public static int[] blockstateRotation(BlockFace facing) {
        return switch (facing) {
            case DOWN -> new int[] {90, 0};
            case UP -> new int[] {270, 0};
            case EAST -> new int[] {0, 90};
            case SOUTH -> new int[] {0, 180};
            case WEST -> new int[] {0, 270};
            default -> new int[] {0, 0};
        };
    }

    /**
     * The model rotation of a block state: {@code x} first, then {@code y}, both clockwise looking down the positive
     * axis (counterclockwise in JOML).
     */
    public static Quaternionf modelRotation(BlockFace facing) {
        int[] rotation = blockstateRotation(facing);
        return new Quaternionf().rotationY((float) Math.toRadians(-rotation[1]))
            .rotateX((float) Math.toRadians(-rotation[0]));
    }

    /**
     * Left rotation of the display transformation: vanilla turns every item display half a turn around Y before
     * drawing the item, which is undone here so the model ends up as the block state draws it.
     */
    public static Quaternionf leftRotation(BlockFace facing) {
        return modelRotation(facing).rotateY((float) Math.PI);
    }

    /**
     * Mod {@code nearestLookingDirection().getOpposite()}: the facing of a block placed by someone looking along
     * ({@code dx}, {@code dy}, {@code dz}), the axis with the largest component, pointing back at them.
     */
    public static BlockFace placedFacing(double dx, double dy, double dz) {
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        BlockFace looking;
        if (ay >= ax && ay >= az) {
            looking = dy > 0 ? BlockFace.UP : BlockFace.DOWN;
        } else if (ax >= az) {
            looking = dx > 0 ? BlockFace.EAST : BlockFace.WEST;
        } else {
            looking = dz > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
        return looking.getOppositeFace();
    }

    /** A facing turned with a structure template (vanilla {@code Rotation.rotate}): up and down stay. */
    public static BlockFace rotate(BlockFace facing, StructureRotation rotation) {
        if (facing == BlockFace.UP || facing == BlockFace.DOWN) {
            return facing;
        }
        int turns = switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            case NONE -> 0;
        };
        BlockFace result = facing;
        for (int i = 0; i < turns; i++) {
            result = switch (result) {
                case NORTH -> BlockFace.EAST;
                case EAST -> BlockFace.SOUTH;
                case SOUTH -> BlockFace.WEST;
                default -> BlockFace.NORTH;
            };
        }
        return result;
    }

    /** Parses a mod facing ({@code north}, {@code up}...); null when it is not one of the six. */
    public static BlockFace parse(String name) {
        for (BlockFace face : FACINGS) {
            if (face.name().equalsIgnoreCase(name)) {
                return face;
            }
        }
        return null;
    }
}
