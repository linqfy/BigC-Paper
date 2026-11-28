package dev.linqfy.bigCasares.modules.toffy;

import java.util.Objects;

/**
 * Where each part is looked for on a player: {@code armor-search}, {@code weapon-search} and {@code tool-search}.
 */
public record ToffySearch(ToffyArmorSearch armor, ToffyHeldSearch weapon, ToffyHeldSearch tool) {
    public static final ToffySearch DEFAULT = new ToffySearch(
        ToffyArmorSearch.INVENTORY, ToffyHeldSearch.HOTBAR, ToffyHeldSearch.INVENTORY);

    public ToffySearch {
        Objects.requireNonNull(armor, "armor");
        Objects.requireNonNull(weapon, "weapon");
        Objects.requireNonNull(tool, "tool");
    }
}
