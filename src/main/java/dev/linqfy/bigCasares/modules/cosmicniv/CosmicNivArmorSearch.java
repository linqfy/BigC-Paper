package dev.linqfy.bigCasares.modules.cosmicniv;

/**
 * Where armor is looked for on a player. {@code WORN} counts only what is equipped; {@code INVENTORY} also counts
 * the pieces carried in the inventory and keeps the best one per slot. Mobs always use what they wear.
 */
public enum CosmicNivArmorSearch {
    WORN,
    INVENTORY
}
