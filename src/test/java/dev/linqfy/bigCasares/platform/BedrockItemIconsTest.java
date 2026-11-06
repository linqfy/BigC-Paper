package dev.linqfy.bigCasares.platform;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockItemIconsTest {

    @Test
    void renamedTexturesExistInBedrocksOwnPack() {
        BedrockItemIcons.RENAMED.forEach((name, path) ->
            assertTrue(BedrockItemIcons.available().contains(path), name + " -> " + path));
    }

    @Test
    void sameNameItemsAndBlocksAndRenamedOnes() {
        assertEquals(Optional.of("textures/items/apple"), BedrockItemIcons.path(Material.APPLE));
        assertEquals(Optional.of("textures/blocks/stone"), BedrockItemIcons.path(Material.STONE));
        assertEquals(Optional.of("textures/items/heartofthesea_closed"), BedrockItemIcons.path(Material.HEART_OF_THE_SEA));
        assertEquals(Optional.of("textures/items/apple_golden"), BedrockItemIcons.path(Material.GOLDEN_APPLE));
    }

    @Test
    void noIconRatherThanAMissingTexture() {
        assertEquals(Optional.empty(), BedrockItemIcons.path(Material.AIR));
        assertEquals(Optional.empty(), BedrockItemIcons.path(null));
    }

    @Test
    void everyVanillaItemOfTheBundledShopHasAnIcon() {
        YamlConfiguration shop = YamlConfiguration.loadConfiguration(new File("src/main/resources/shop.yml"));
        List<String> missing = new ArrayList<>();
        var categories = shop.getConfigurationSection("categories");
        assertTrue(categories != null && !categories.getKeys(false).isEmpty());
        for (String categoryId : categories.getKeys(false)) {
            var section = categories.getConfigurationSection(categoryId);
            check(section.getString("icon"), missing);
            var items = section.getConfigurationSection("items");
            if (items == null) {
                continue;
            }
            for (String itemId : items.getKeys(false)) {
                var item = items.getConfigurationSection(itemId);
                if (item.getString("custom-item-id") == null) {
                    check(item.getString("material"), missing);
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    private static void check(String material, List<String> missing) {
        if (material != null && BedrockItemIcons.path(Material.valueOf(material)).isEmpty()) {
            missing.add(material);
        }
    }
}
