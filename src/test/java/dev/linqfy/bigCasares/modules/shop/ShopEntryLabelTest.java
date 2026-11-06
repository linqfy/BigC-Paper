package dev.linqfy.bigCasares.modules.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The chat and the history name a product the way the menus show it, not by its catalog id. */
class ShopEntryLabelTest {

    private static ShopEntry named(String displayName) {
        return new ShopEntry("apple", 10, Material.APPLE, null, 16, 52.0, 2.0, displayName, List.of());
    }

    @Test
    void theDisplayNameWithoutColourCodesIsTheLabel() {
        assertEquals("Manzanas x16", named("&aManzanas x16").label());
        assertEquals("Manzanas", named("§6 Manzanas ").label());
    }

    @Test
    void withoutAReadableDisplayNameTheIdIsTheLabel() {
        assertEquals("apple", named(null).label());
        assertEquals("apple", named("&a").label(), "only colour codes: nothing to read");
    }
}
