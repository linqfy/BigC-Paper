package dev.linqfy.bigCasares.modules.villagermood;

/**
 * Read-only view of the blocks around a villager, so the cell geometry can be tested without a world.
 */
interface BlockProbe {

    /** Whether an adult villager's body can occupy this block (air, flowers, water, open doors...). */
    boolean passable(int x, int y, int z);

    /** Whether the block is taller than one block (fences, walls), so it cannot be jumped onto. */
    boolean tall(int x, int y, int z);
}
