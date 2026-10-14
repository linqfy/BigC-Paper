package dev.linqfy.bigCasares.modules.cosmicniv;

import java.util.Objects;

/**
 * Where each part is looked for on a player: {@code armor-search}, {@code weapon-search} and {@code tool-search}.
 */
public record CosmicNivSearch(CosmicNivArmorSearch armor, CosmicNivHeldSearch weapon, CosmicNivHeldSearch tool) {
    public static final CosmicNivSearch DEFAULT = new CosmicNivSearch(
        CosmicNivArmorSearch.INVENTORY, CosmicNivHeldSearch.HOTBAR, CosmicNivHeldSearch.INVENTORY);

    public CosmicNivSearch {
        Objects.requireNonNull(armor, "armor");
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(tool, "tool");
    }
}
