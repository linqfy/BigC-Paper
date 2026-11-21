package dev.linqfy.bigCasares.modules.alexscaves.core.item;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmokingRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Furnace, smoker and campfire recipes of the module foods (the mod's cooking recipes). The raw
 * items sit on a base material without vanilla cooking recipes, and the input is an exact item
 * choice, so only these recipes cook them and the cooked item stacks in the result slot.
 * Keys are {@code bigcasares:ac_*}, which {@link AlexsCavesCraftGuard} lets through.
 */
public final class ModCookingRecipes {
    private ModCookingRecipes() {
    }

    /** @return the keys added, to remove on shutdown */
    public static List<NamespacedKey> register(Server server, CustomItemRegistry items, Logger logger) {
        List<NamespacedKey> added = new ArrayList<>();
        for (Map.Entry<String, String> entry : AlexsCavesCraftGuard.COOKING.entrySet()) {
            ItemStack raw;
            ItemStack cooked;
            try {
                raw = items.createItemStack(entry.getKey(), 1);
                cooked = items.createItemStack(entry.getValue(), 1);
            } catch (RuntimeException missing) {
                logger.warning("[AlexsCaves] Sin receta de cocción para " + entry.getKey() + ": falta en el catálogo");
                continue;
            }
            RecipeChoice input = new RecipeChoice.ExactChoice(raw);
            String base = entry.getKey();
            add(server, added, new FurnaceRecipe(key(base, "smelting"), cooked, input, 0.35f, 200));
            add(server, added, new SmokingRecipe(key(base, "smoking"), cooked, input, 0.35f, 100));
            add(server, added, new CampfireRecipe(key(base, "campfire"), cooked, input, 0.35f, 600));
        }
        return added;
    }

    public static void unregister(Server server, List<NamespacedKey> keys) {
        for (NamespacedKey key : keys) {
            server.removeRecipe(key, false);
        }
        if (!keys.isEmpty()) {
            server.updateRecipes();
        }
    }

    static NamespacedKey key(String itemId, String kind) {
        return new NamespacedKey("bigcasares", itemId + "_" + kind);
    }

    private static void add(Server server, List<NamespacedKey> added, Recipe recipe) {
        if (server.addRecipe(recipe, false) && recipe instanceof Keyed keyed) {
            added.add(keyed.getKey());
        }
    }
}
