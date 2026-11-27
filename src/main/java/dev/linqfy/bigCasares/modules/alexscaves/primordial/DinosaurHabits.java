package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import org.bukkit.Material;

import java.util.Set;

/**
 * Pure rules of the chores the plain Primordial dinosaurs do besides fighting, after the mod's goals: the raptor's leap
 * ({@code VallumraptorMeleeGoal}), its taste for dropped food ({@code VallumraptorEntity.canTargetItem}), the
 * relicheirus nibbling and toppling trees and the grottoceratops grazing. The behaviour that applies them lives in
 * {@link DinosaurChores}.
 */
final class DinosaurHabits {
    /** Mod: the leap starts from 3 to 7 blocks away. */
    static final double LEAP_NEAR = 3.0;
    static final double LEAP_FAR = 7.0;
    /** Mod: the animation tick after which the raptor actually jumps. */
    static final int LEAP_WINDUP = 15;
    /** Mod {@code VallumraptorOpenDoorGoal}: the door opens at this tick of the grab. */
    static final int GRAB_OPENS_DOOR = 16;
    /** Mod {@code onGetItem}: the item is taken after this tick of the grab. */
    static final int GRAB_TAKES_ITEM = 15;
    /** Mod {@code RelicheirusNibblePewensGoal}: the nibble ends at this tick and crumbs every 8. */
    static final int NIBBLE_END = 30;
    static final int NIBBLE_PERIOD = 8;
    /** Mod {@code RelicheirusPushTreesGoal}: the tree goes over at this tick of the push. */
    static final int PUSH_TOPPLES = 35;
    /** Mod {@code GrottoceratopsEatPlantsGoal}: the plant is eaten after this tick of the chew. */
    static final int GRAZE_EATS = 15;
    /** Mod {@code gatherAttachedBlocks}: at most 300 blocks, within 12 of the first one. */
    static final int TREE_MAX_BLOCKS = 300;
    static final double TREE_SPREAD = 12.0;

    private static final Set<Material> MEATS = Set.of(Material.BEEF, Material.COOKED_BEEF, Material.PORKCHOP,
        Material.COOKED_PORKCHOP, Material.MUTTON, Material.COOKED_MUTTON, Material.CHICKEN, Material.COOKED_CHICKEN,
        Material.RABBIT, Material.COOKED_RABBIT, Material.ROTTEN_FLESH);
    private static final Set<Material> EGGS = Set.of(Material.EGG, Material.BLUE_EGG, Material.BROWN_EGG);

    private DinosaurHabits() {
    }

    /** Mod: it leaps at a target 3 to 7 blocks away, one try in 10 a tick (one in 5 when tamed). */
    static boolean leapRange(double distance) {
        return distance > LEAP_NEAR && distance < LEAP_FAR;
    }

    static int leapOdds(boolean tamed) {
        return tamed ? 5 : 10;
    }

    /** Mod: the leap adds 0.45 toward the target to the velocity and lifts it to 0.6. */
    static double[] leapVelocity(double velocityX, double velocityZ, double toX, double toZ) {
        double length = toX * toX + toZ * toZ;
        double x = velocityX;
        double z = velocityZ;
        if (length > 1.0E-7) {
            double scale = 0.9 * 0.5 / Math.sqrt(length);
            x += toX * scale;
            z += toZ * scale;
        }
        return new double[] {x, 0.6, z};
    }

    /**
     * Mod {@code canTargetItem}: the raptor takes eggs, dinosaur chops and meat, but never its own egg. {@code moduleId}
     * is the module item id of the stack ({@code ac_dinosaur_chop}...), null for a vanilla one.
     */
    static boolean steals(Material material, String moduleId) {
        if (moduleId != null) {
            return moduleId.equals("ac_dinosaur_chop") || moduleId.equals("ac_cooked_dinosaur_chop");
        }
        return EGGS.contains(material) || MEATS.contains(material);
    }

    /** Mod {@code highEnough}: an adult nibbles leaves 4 to 6 blocks high, a baby only the low ones. */
    static boolean nibbleHigh(int height, boolean baby) {
        return baby ? height <= 1 : height > 3 && height < 7;
    }

    /** The side (unit x and z) a point at {@code (dx, dz)} lies on: the dominant axis. */
    static int[] cardinal(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return new int[] {dx > 0 ? 1 : -1, 0};
        }
        return new int[] {0, dz >= 0 ? 1 : -1};
    }

    /**
     * Mod {@code getStandAtTreePos}: four blocks back from the tree on the side it is seen from (the tree's own
     * position moves one block toward that side when nothing is under it).
     *
     * @return the block x and z to stand on
     */
    static int[] standAt(int treeX, int treeZ, double fromX, double fromZ, boolean airUnderTree) {
        int[] side = cardinal(treeX + 0.5 - fromX, treeZ + 0.5 - fromZ);
        int x = treeX;
        int z = treeZ;
        if (airUnderTree) {
            x += side[0];
            z += side[1];
        }
        return new int[] {x - side[0] * 4, z - side[1] * 4};
    }

    /** Whether {@code now} passed a multiple of {@code period} since {@code previous}. */
    static boolean passes(int previous, int now, int period) {
        return now / period > previous / period;
    }

    /** Mod {@code gatherAttachedBlocks}: a block joins the tree within 12 blocks of the first one. */
    static boolean attached(double dx, double dy, double dz) {
        return Math.sqrt(dx * dx + dy * dy + dz * dz) < TREE_SPREAD;
    }

    /** Whether a material is something players build with: a tree does not grow next to it. */
    static boolean builtMaterial(Material type) {
        String name = type.name();
        if (name.equals("AIR") || name.endsWith("_AIR")) {
            return false;
        }
        return name.endsWith("_PLANKS") || name.endsWith("_STAIRS") || name.endsWith("_SLAB") || name.endsWith("_DOOR")
            || name.endsWith("_TRAPDOOR") || name.endsWith("_FENCE") || name.endsWith("_FENCE_GATE") || name.endsWith("_SIGN")
            || name.endsWith("_WALL_SIGN") || name.endsWith("_HANGING_SIGN") || name.contains("GLASS") || name.contains("BRICK") || name.contains("COBBLE") || name.contains("TORCH")
            || name.contains("LANTERN") || name.contains("CHEST") || name.contains("TABLE") || name.contains("WOOL")
            || name.contains("CARPET") || name.contains("_BED") || name.contains("BANNER") || name.contains("FRAME")
            || name.startsWith("STRIPPED_") || name.contains("CONCRETE") || name.contains("TERRACOTTA")
            || name.contains("POLISHED") || name.contains("FURNACE") || name.contains("BARREL");
    }
}
