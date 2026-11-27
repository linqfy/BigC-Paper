package dev.linqfy.bigCasares.modules.alexscaves;

import dev.linqfy.bigCasares.items.CustomItem;
import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** {@code /alexscaves items}: a paged chest with every item of the module; clicking one takes it. */
final class AlexsCavesItemMenu implements Listener {
    static final String PREFIX = "ac_";
    static final int PAGE_SIZE = 45;
    private static final int PREVIOUS = 45;
    private static final int NEXT = 53;

    private final CustomItemRegistry registry;

    AlexsCavesItemMenu(CustomItemRegistry registry) {
        this.registry = registry;
    }

    /** The module's item ids, sorted. */
    List<String> ids() {
        return ids(registry.getAllItems().stream().map(CustomItem::getId).toList());
    }

    static List<String> ids(List<String> all) {
        return all.stream().filter(id -> id.startsWith(PREFIX)).sorted(Comparator.naturalOrder()).toList();
    }

    static int pages(int items) {
        return Math.max(1, (items + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    static int clampPage(int page, int items) {
        return Math.max(0, Math.min(page, pages(items) - 1));
    }

    void open(Player player, int requestedPage) {
        List<String> ids = ids();
        int page = clampPage(requestedPage, ids.size());
        Page holder = new Page(page, ids);
        Inventory inventory = Bukkit.createInventory(holder, 54,
            "Alex's Caves " + (page + 1) + "/" + pages(ids.size()));
        holder.inventory = inventory;
        int from = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && from + slot < ids.size(); slot++) {
            ItemStack stack = registry.createItemStack(ids.get(from + slot), 1);
            if (stack != null) {
                inventory.setItem(slot, stack);
            }
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS, button("« Página anterior"));
        }
        if (page < pages(ids.size()) - 1) {
            inventory.setItem(NEXT, button("Página siguiente »"));
        }
        player.openInventory(inventory);
    }

    private static ItemStack button(String name) {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        arrow.setItemMeta(meta);
        return arrow;
    }

    /** Gives {@code amount} of an item, dropping what does not fit; false when the id is not one of the module's. */
    boolean give(Player target, String id, int amount) {
        String key = id.toLowerCase(Locale.ROOT);
        if (!key.startsWith(PREFIX)) {
            key = PREFIX + key;
        }
        if (registry.findById(key).isEmpty()) {
            return false;
        }
        ItemStack sample = registry.createItemStack(key, 1);
        if (sample == null) {
            return false;
        }
        int max = Math.max(1, sample.getMaxStackSize());
        for (int left = amount; left > 0; left -= max) {
            ItemStack stack = sample.clone();
            stack.setAmount(Math.min(left, max));
            target.getInventory().addItem(stack).values()
                .forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        }
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Page page)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != page.inventory) {
            return;
        }
        if (!player.hasPermission(AlexsCavesCommand.ADMIN)) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        if (slot == PREVIOUS && page.page > 0) {
            open(player, page.page - 1);
        } else if (slot == NEXT) {
            open(player, page.page + 1);
        } else if (slot < PAGE_SIZE) {
            int index = page.page * PAGE_SIZE + slot;
            if (index < page.ids.size()) {
                ItemStack shown = page.inventory.getItem(slot);
                int amount = event.isShiftClick() && shown != null ? shown.getMaxStackSize() : 1;
                give(player, page.ids.get(index), amount);
            }
        }
    }

    /** Closes every open menu, so its stacks cannot be taken once the listener is gone. */
    static void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Page) {
                player.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Page) {
            event.setCancelled(true);
        }
    }

    private static final class Page implements InventoryHolder {
        private final int page;
        private final List<String> ids;
        private Inventory inventory;

        private Page(int page, List<String> ids) {
            this.page = page;
            this.ids = ids;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
