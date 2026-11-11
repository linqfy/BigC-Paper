package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.modules.tideframe.MenuScreen;
import dev.linqfy.bigCasares.modules.tideframe.MenuTitle;
import dev.linqfy.bigCasares.modules.tideframe.MenuTitleStyle;
import dev.linqfy.bigCasares.modules.tideframe.MenuTitles;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class ShopGuiController implements Listener {

    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    private final ShopModule module;
    private final ShopCatalog catalog;
    private final ShopService service;
    private final Supplier<MenuTitleStyle> titleStyle;
    private final Set<Player> viewers = new HashSet<>();

    public ShopGuiController(ShopModule module, ShopCatalog catalog, ShopService service) {
        this(module, catalog, service, () -> MenuTitleStyle.PLAIN);
    }

    public ShopGuiController(ShopModule module, ShopCatalog catalog, ShopService service, Supplier<MenuTitleStyle> titleStyle) {
        this.module = module;
        this.catalog = catalog;
        this.service = service;
        this.titleStyle = Objects.requireNonNull(titleStyle, "titleStyle");
    }

    public void openCategories(Player player) {
        MenuTitle title = titleStyle.get().title(player, MenuScreen.SHOP_CATEGORIES, ChatColor.GOLD + "Shop");
        Inventory inventory = MenuTitles.create(new CategoryMenuHolder(), MenuScreen.SHOP_CATEGORIES.size(), title);
        // A drawn background has its own slot frames; the glass filler is only for the plain chest.
        if (!title.drawsBackground()) {
            fill(inventory, Material.GRAY_STAINED_GLASS_PANE, " ");
        }
        for (ShopCategory category : catalog.categories()) {
            inventory.setItem(category.slot(), categoryItem(category));
        }
        viewers.add(player);
        player.openInventory(inventory);
    }

    public void closeAll() {
        for (Player viewer : new ArrayList<>(viewers)) {
            viewer.closeInventory();
        }
        viewers.clear();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            viewers.remove(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ShopMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        if (holder instanceof CategoryMenuHolder) {
            handleCategoryClick(player, event.getSlot());
            return;
        }

        if (holder instanceof ItemMenuHolder itemMenuHolder) {
            handleItemClick(player, itemMenuHolder.category(), event.getSlot(), event.isLeftClick(), event.isRightClick());
        }
    }

    private void handleCategoryClick(Player player, int slot) {
        catalog.categories().stream()
            .filter(category -> category.slot() == slot)
            .findFirst()
            .ifPresent(category -> openCategory(player, category));
    }

    public void openCategory(Player player, ShopCategory category) {
        MenuTitle title = titleStyle.get().title(player, MenuScreen.SHOP_ITEMS, ChatColor.GOLD + stripColor(category.name()));
        Inventory inventory = MenuTitles.create(new ItemMenuHolder(category), MenuScreen.SHOP_ITEMS.size(), title);
        if (!title.drawsBackground()) {
            fill(inventory, Material.BLACK_STAINED_GLASS_PANE, " ");
        }
        inventory.setItem(BACK_SLOT, simpleItem(Material.ARROW, ChatColor.YELLOW + "Volver"));
        inventory.setItem(CLOSE_SLOT, simpleItem(Material.BARRIER, ChatColor.RED + "Cerrar"));
        for (ShopEntry entry : category.entries()) {
            inventory.setItem(entry.slot(), entryItem(entry));
        }
        viewers.add(player);
        player.openInventory(inventory);
    }

    private void handleItemClick(Player player, ShopCategory category, int slot, boolean leftClick, boolean rightClick) {
        if (slot == BACK_SLOT) {
            openCategories(player);
            return;
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        category.entries().stream()
            .filter(entry -> entry.slot() == slot)
            .findFirst()
            .ifPresent(entry -> {
                ShopTransactionResult result = null;
                if (leftClick) {
                    result = service.buy(player, entry);
                } else if (rightClick) {
                    result = service.sell(player, entry);
                }

                if (result != null) {
                    player.sendMessage(result.success() ? ChatColor.GREEN + result.message() : ChatColor.RED + result.message());
                    player.updateInventory();
                }
            });
    }

    private ItemStack categoryItem(ShopCategory category) {
        return simpleItem(category.icon(), translate(category.name()));
    }

    private ItemStack entryItem(ShopEntry entry) {
        ItemStack stack = module.plugin().getCustomItemRegistry().findById(entry.customItemId() == null ? "" : entry.customItemId())
            .map(item -> item.createItemStack(entry.amount()))
            .orElseGet(() -> entry.material() == null ? new ItemStack(Material.BARRIER) : new ItemStack(entry.material(), entry.amount()));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (entry.displayName() != null) {
                meta.setDisplayName(translate(entry.displayName()));
            }
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GREEN + "Click izquierdo: comprar");
            lore.add(ChatColor.RED + "Click derecho: vender");
            lore.add(ChatColor.GRAY + "Compra: " + entry.buyPrice());
            lore.add(ChatColor.GRAY + "Venta: " + entry.sellPrice());
            for (String line : entry.lore()) {
                lore.add(translate(line));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void fill(Inventory inventory, Material material, String name) {
        ItemStack filler = simpleItem(material, name);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private ItemStack simpleItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(translate(name));
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String translate(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String stripColor(String text) {
        return ChatColor.stripColor(translate(text == null ? "" : text));
    }

    private sealed interface ShopMenuHolder extends InventoryHolder permits CategoryMenuHolder, ItemMenuHolder {
        @Override
        default Inventory getInventory() {
            return null;
        }
    }

    private static final class CategoryMenuHolder implements ShopMenuHolder {
    }

    private record ItemMenuHolder(ShopCategory category) implements ShopMenuHolder {
    }
}
