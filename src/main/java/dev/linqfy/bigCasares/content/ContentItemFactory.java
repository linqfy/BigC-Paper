package dev.linqfy.bigCasares.content;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;

/** Creates and identifies content item stacks. */
public final class ContentItemFactory {

    public static final NamespacedKey CONTENT_ID = new NamespacedKey("bigcasares", "content_id");

    private final ContentItemRegistry registry;

    public ContentItemFactory(ContentItemRegistry registry) {
        this.registry = registry;
    }

    public ContentItemRegistry registry() {
        return registry;
    }

    public ItemStack create(ContentItem item, int amount) {
        Material material = Material.matchMaterial(item.material());
        if (material == null || !material.isItem()) {
            throw new IllegalStateException("Content item " + item.id() + " has no item material " + item.material());
        }
        ItemStack stack = new ItemStack(material, Math.max(1, Math.min(amount, item.maxStackSize())));
        ItemMeta meta = stack.getItemMeta();
        meta.setItemModel(new NamespacedKey(item.itemModel().namespace(), item.itemModel().path()));
        meta.itemName(Component.translatable(item.nameKey(), item.fallbackName()));
        if (!item.lore().isEmpty()) {
            List<Component> lore = item.lore().stream()
                .map(line -> (Component) Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                .toList();
            meta.lore(lore);
        }
        if (item.maxStackSize() != material.getMaxStackSize()) {
            meta.setMaxStackSize(item.maxStackSize());
        }
        if (item.food() != null) {
            FoodComponent food = meta.getFood();
            food.setNutrition(item.food().nutrition());
            food.setSaturation(item.food().saturation());
            food.setCanAlwaysEat(item.food().canAlwaysEat());
            meta.setFood(food);
        }
        meta.getPersistentDataContainer().set(CONTENT_ID, PersistentDataType.STRING, item.id().asString());
        stack.setItemMeta(meta);
        if (item.customizer() != null) {
            // Last: setItemMeta replaces the stack's component patch, so components that the hook
            // sets through the data component API would be lost if they were written before it.
            item.customizer().accept(stack);
        }
        return stack;
    }

    public Optional<ItemStack> create(String id, int amount) {
        return registry.find(id).map(item -> create(item, amount));
    }

    /** Content id stored on a stack, if it is a content item. */
    public static Optional<NamespacedId> idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = stack.getItemMeta().getPersistentDataContainer().get(CONTENT_ID, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(NamespacedId.parse(raw));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    public Optional<ContentItem> itemOf(ItemStack stack) {
        return idOf(stack).flatMap(registry::find);
    }

    /** Resolves a drop/ingredient id: content id or vanilla material id. */
    public Optional<ItemStack> resolve(String id, int amount) {
        Optional<ItemStack> content = create(id, amount);
        if (content.isPresent()) {
            return content;
        }
        Material material = Material.matchMaterial(id);
        return material == null || !material.isItem() ? Optional.empty() : Optional.of(new ItemStack(material, amount));
    }
}
