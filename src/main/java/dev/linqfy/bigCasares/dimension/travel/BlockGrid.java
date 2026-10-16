package dev.linqfy.bigCasares.dimension.travel;

/**
 * Read-only block access used by portal detection and arrival search, so the
 * algorithms can be tested with an in-memory grid and run against Bukkit worlds.
 */
public interface BlockGrid {

    /** Vanilla block id at the position, e.g. {@code minecraft:air}; never null. */
    String blockId(int x, int y, int z);

    /** True for blocks a player can stand on (full collision, not dangerous). */
    boolean isSolidFloor(int x, int y, int z);

    /** True for blocks a player's body can occupy without suffocating or harm. */
    boolean isPassable(int x, int y, int z);

    /** True for blocks that hurt players (lava, fire, magma, powder snow, cactus...). */
    boolean isHazard(int x, int y, int z);

    int minY();

    int maxY();
}
