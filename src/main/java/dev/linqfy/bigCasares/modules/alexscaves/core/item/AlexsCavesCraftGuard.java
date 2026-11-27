package dev.linqfy.bigCasares.modules.alexscaves.core.item;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.Keyed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Map;

/**
 * Module items share vanilla base materials (a neodymium ingot is an iron
 * nugget underneath), so vanilla recipes would accept them. Only catalog recipes
 * of the module ({@code bigcasares:ac_*}) may use them.
 */
public final class AlexsCavesCraftGuard implements Listener {
    /** Raw module item -> cooked module item, as the mod's smelting, smoking and campfire recipes. */
    public static final Map<String, String> COOKING = Map.of(
        "ac_dinosaur_chop", "ac_cooked_dinosaur_chop",
        "ac_trilocaris_tail", "ac_cooked_trilocaris_tail",
        "ac_lanternfish", "ac_cooked_lanternfish",
        "ac_tripodfish", "ac_cooked_tripodfish");

    private final CustomItemRegistry items;

    public AlexsCavesCraftGuard(CustomItemRegistry items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (isModuleRecipe(event.getRecipe())) {
            return;
        }
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (isModuleItem(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Module foods cook only through their own recipes ({@link ModCookingRecipes}). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSmelt(FurnaceSmeltEvent event) {
        if (!isModuleRecipe(event.getRecipe()) && isModuleItem(event.getSource())) {
            event.setCancelled(true);
        }
    }

    /** Campfires fire this instead of {@link FurnaceSmeltEvent}. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCook(BlockCookEvent event) {
        if (!(event instanceof FurnaceSmeltEvent) && !isModuleRecipe(event.getRecipe()) && isModuleItem(event.getSource())) {
            event.setCancelled(true);
        }
    }

    /** Paper-based module items (eggs, plants) must not extend maps on a cartography table. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCartography(PrepareResultEvent event) {
        if (!(event.getInventory() instanceof CartographyInventory)) {
            return;
        }
        for (ItemStack ingredient : event.getInventory().getContents()) {
            if (isModuleItem(ingredient)) {
                event.setResult(null);
                return;
            }
        }
    }

    static boolean isModuleRecipeKey(String namespace, String key) {
        return "bigcasares".equals(namespace) && key.startsWith("ac_");
    }

    private static boolean isModuleRecipe(Recipe recipe) {
        return recipe instanceof Keyed keyed
            && isModuleRecipeKey(keyed.getKey().getNamespace(), keyed.getKey().getKey());
    }

    private boolean isModuleItem(ItemStack item) {
        return item != null && !item.isEmpty()
            && items.resolveItemId(item).filter(id -> id.startsWith("ac_")).isPresent();
    }
}
