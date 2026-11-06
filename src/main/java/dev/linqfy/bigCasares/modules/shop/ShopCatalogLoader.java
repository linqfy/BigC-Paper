package dev.linqfy.bigCasares.modules.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public final class ShopCatalogLoader {

    public ShopCatalog load(ConfigurationSection root) {
        ConfigurationSection categoriesSection = root.getConfigurationSection("categories");
        if (categoriesSection == null) {
            return new ShopCatalog(List.of());
        }

        List<ShopCategory> categories = new ArrayList<>();
        for (String categoryId : categoriesSection.getKeys(false)) {
            ConfigurationSection categorySection = categoriesSection.getConfigurationSection(categoryId);
            if (categorySection == null) {
                continue;
            }

            String name = requiredString(categorySection, "name");
            Material icon = parseMaterial(requiredString(categorySection, "icon"), "categoria " + categoryId);
            int slot = categorySection.getInt("slot");
            ConfigurationSection itemsSection = categorySection.getConfigurationSection("items");

            List<ShopEntry> entries = new ArrayList<>();
            if (itemsSection != null) {
                for (String entryId : itemsSection.getKeys(false)) {
                    entries.add(loadEntry(entryId, itemsSection.getConfigurationSection(entryId)));
                }
            }

            categories.add(new ShopCategory(categoryId, name, icon, slot, List.copyOf(entries)));
        }
        return new ShopCatalog(categories);
    }

    private ShopEntry loadEntry(String entryId, ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("La entrada " + entryId + " no tiene configuracion.");
        }

        String materialName = section.getString("material");
        String customItemId = section.getString("custom-item-id");
        if ((materialName == null) == (customItemId == null)) {
            throw new IllegalArgumentException("La entrada " + entryId + " debe definir exactamente una fuente de item.");
        }

        Material material = materialName == null ? null : parseMaterial(materialName, "entrada " + entryId);
        return new ShopEntry(
            entryId,
            section.getInt("slot"),
            material,
            customItemId,
            section.getInt("amount"),
            section.getDouble("buy-price"),
            section.getDouble("sell-price"),
            section.getString("display-name"),
            section.getStringList("lore"),
            Math.max(0, section.getInt("limite-diario", 0))
        );
    }

    private String requiredString(ConfigurationSection section, String path) {
        String value = section.getString(path);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta configuracion obligatoria: " + path);
        }
        return value;
    }

    private Material parseMaterial(String raw, String owner) {
        Material material = Material.matchMaterial(raw);
        if (material == null) {
            throw new IllegalArgumentException("Material invalido en " + owner + ": " + raw);
        }
        return material;
    }
}
