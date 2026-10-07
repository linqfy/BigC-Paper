package dev.linqfy.bigCasares.modules.donpollos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Menu del premio por ganarle al Don Pollo Boss: 5 lugares, el primero es el Balde de KFC dado vuelta y
 * los otros 4 los elegis vos (categoria y despues el item, con paginas). Al reclamar
 * te da el balde y un stack de cada item elegido.
 */
final class DonPollosRewardMenu implements Listener {

    private static final int CHOICES = DonPollosReward.CHOICES;
    private static final int ITEMS_PER_PAGE = DonPollosReward.ITEMS_PER_PAGE;
    private static final int BUCKET_SLOT = 11;
    private static final int[] CHOICE_SLOTS = {12, 13, 14, 15};
    private static final int CLAIM_SLOT = 22;
    private static final int BACK_SLOT = 22;
    private static final int PREV_SLOT = 45;
    private static final int ITEMS_BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    /** Items de administrador que no se pueden elegir. */
    private static final Set<String> FORBIDDEN = Set.of("COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK",
        "COMMAND_BLOCK_MINECART", "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "BARRIER", "LIGHT", "DEBUG_STICK",
        "KNOWLEDGE_BOOK", "TEST_BLOCK", "TEST_INSTANCE_BLOCK", "BEDROCK", "END_PORTAL_FRAME", "SPAWNER",
        "TRIAL_SPAWNER", "VAULT", "PETRIFIED_OAK_SLAB");

    private static final List<DonPollosReward.Category> CATEGORIES = List.of(DonPollosReward.Category.values());

    private final DonPollosKfcBucket bucket;
    /** Se llama al tocar "Reclamar": devuelve false si ese boss ya no da premio (asi no se cobra dos veces). */
    private final BiPredicate<Player, UUID> claim;
    private final Map<UUID, Material[]> choices = new HashMap<>();
    private Map<DonPollosReward.Category, List<Material>> itemsByCategory;

    DonPollosRewardMenu(DonPollosKfcBucket bucket, BiPredicate<Player, UUID> claim) {
        this.bucket = bucket;
        this.claim = claim;
    }

    // ------------------------------------------------------------ menus

    void open(Player player, UUID bossId) {
        Material[] chosen = choices.computeIfAbsent(player.getUniqueId(), id -> new Material[CHOICES]);
        Inventory inventory = Bukkit.createInventory(new RewardHolder(bossId), 27,
            LEGACY.deserialize("§6§lElegi lo que quieras"));
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(BUCKET_SLOT, bucket.create());
        for (int i = 0; i < CHOICES; i++) {
            inventory.setItem(CHOICE_SLOTS[i], chosen[i] == null
                ? icon(Material.CHEST, "§e§lElegi un item", "§7Click para ver las categorias")
                : icon(chosen[i], "§a" + pretty(chosen[i]) + " §7x" + chosen[i].getMaxStackSize(),
                    "§7Click para cambiarlo"));
        }
        boolean ready = java.util.Arrays.stream(chosen).allMatch(java.util.Objects::nonNull);
        inventory.setItem(CLAIM_SLOT, ready
            ? icon(Material.LIME_CONCRETE, "§a§lReclamar premios", "§7Te llevas el balde y los 4 items")
            : icon(Material.GRAY_DYE, "§7Elegi los 4 items", "§7El primero ya es tuyo: el balde de KFC"));
        player.openInventory(inventory);
    }

    private void openCategories(Player player, UUID bossId, int choice) {
        Inventory inventory = Bukkit.createInventory(new CategoryHolder(bossId, choice), 27,
            LEGACY.deserialize("§8Elegi una categoria"));
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < CATEGORIES.size(); i++) {
            DonPollosReward.Category category = CATEGORIES.get(i);
            inventory.setItem(9 + i, icon(Material.valueOf(category.icon), "§e§l" + category.title,
                "§7" + items(category).size() + " items"));
        }
        inventory.setItem(BACK_SLOT, icon(Material.ARROW, "§7Volver"));
        player.openInventory(inventory);
    }

    private void openItems(Player player, UUID bossId, int choice, int categoryIndex, int page) {
        DonPollosReward.Category category = CATEGORIES.get(categoryIndex);
        List<Material> all = items(category);
        int pages = DonPollosReward.pageCount(all.size(), ITEMS_PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));
        Inventory inventory = Bukkit.createInventory(new ItemsHolder(bossId, choice, categoryIndex, current), 54,
            LEGACY.deserialize("§8" + category.title + " §7(" + (current + 1) + "/" + pages + ")"));
        List<Material> shown = DonPollosReward.page(all, current, ITEMS_PER_PAGE);
        for (int i = 0; i < shown.size(); i++) {
            inventory.setItem(i, icon(shown.get(i), "§f" + pretty(shown.get(i)), "§eClick para elegirlo"));
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, icon(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        if (current > 0) {
            inventory.setItem(PREV_SLOT, icon(Material.ARROW, "§ePagina anterior"));
        }
        if (current < pages - 1) {
            inventory.setItem(NEXT_SLOT, icon(Material.ARROW, "§ePagina siguiente"));
        }
        inventory.setItem(ITEMS_BACK_SLOT, icon(Material.BARRIER, "§7Volver a las categorias"));
        player.openInventory(inventory);
    }

    private List<Material> items(DonPollosReward.Category category) {
        if (itemsByCategory == null) {
            itemsByCategory = new EnumMap<>(DonPollosReward.Category.class);
            for (Material material : Material.values()) {
                if (material.isLegacy() || !material.isItem() || material.isAir() || FORBIDDEN.contains(material.name())
                    || material.name().endsWith("_SPAWN_EGG")) {
                    continue;
                }
                DonPollosReward.Category found = DonPollosReward.category(material.name(), material.isBlock(),
                    material.isSolid(), material.isBlock() && material.isOccluding(), material.isEdible());
                itemsByCategory.computeIfAbsent(found, c -> new ArrayList<>()).add(material);
            }
        }
        return itemsByCategory.getOrDefault(category, List.of());
    }

    // ------------------------------------------------------------ clicks

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
            || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        switch (holder) {
            case RewardHolder reward -> clickReward(player, reward, slot);
            case CategoryHolder categories -> {
                if (slot == BACK_SLOT) {
                    open(player, categories.bossId());
                } else if (slot >= 9 && slot < 9 + CATEGORIES.size()) {
                    click(player);
                    openItems(player, categories.bossId(), categories.choice(), slot - 9, 0);
                }
            }
            case ItemsHolder items -> clickItems(player, items, slot);
        }
    }

    private void clickReward(Player player, RewardHolder reward, int slot) {
        for (int i = 0; i < CHOICES; i++) {
            if (slot == CHOICE_SLOTS[i]) {
                click(player);
                openCategories(player, reward.bossId(), i);
                return;
            }
        }
        if (slot != CLAIM_SLOT) {
            return;
        }
        Material[] chosen = choices.get(player.getUniqueId());
        if (chosen == null || java.util.Arrays.stream(chosen).anyMatch(java.util.Objects::isNull)) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        player.closeInventory();
        if (!claim.test(player, reward.bossId())) {
            player.sendMessage(LEGACY.deserialize("§cEse premio ya no esta disponible."));
            return;
        }
        List<ItemStack> prizes = new ArrayList<>();
        prizes.add(bucket.create());
        for (Material material : chosen) {
            prizes.add(new ItemStack(material, material.getMaxStackSize()));
        }
        for (ItemStack leftover : player.getInventory().addItem(prizes.toArray(ItemStack[]::new)).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        choices.remove(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        player.sendMessage(LEGACY.deserialize("§6¡Te llevaste el §c§lBalde de KFC dado vuelta §6y tus 4 premios!"));
    }

    private void clickItems(Player player, ItemsHolder items, int slot) {
        if (slot == ITEMS_BACK_SLOT) {
            openCategories(player, items.bossId(), items.choice());
        } else if (slot == PREV_SLOT && items.page() > 0) {
            click(player);
            openItems(player, items.bossId(), items.choice(), items.category(), items.page() - 1);
        } else if (slot == NEXT_SLOT) {
            click(player);
            openItems(player, items.bossId(), items.choice(), items.category(), items.page() + 1);
        } else if (slot < ITEMS_PER_PAGE) {
            List<Material> shown = DonPollosReward.page(items(CATEGORIES.get(items.category())), items.page(),
                ITEMS_PER_PAGE);
            if (slot < shown.size()) {
                choices.computeIfAbsent(player.getUniqueId(), id -> new Material[CHOICES])[items.choice()] = shown.get(slot);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
                open(player, items.bossId());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ ayuda

    private static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    private static void fill(Inventory inventory, Material pane) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, icon(pane, " "));
        }
    }

    private static ItemStack icon(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(LEGACY.deserialize(name).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(LEGACY.deserialize(line).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String pretty(Material material) {
        return DonPollosReward.pretty(material.name());
    }

    private sealed interface MenuHolder extends InventoryHolder permits RewardHolder, CategoryHolder, ItemsHolder {
        @Override
        default Inventory getInventory() {
            return null;
        }
    }

    private record RewardHolder(UUID bossId) implements MenuHolder {
    }

    private record CategoryHolder(UUID bossId, int choice) implements MenuHolder {
    }

    private record ItemsHolder(UUID bossId, int choice, int category, int page) implements MenuHolder {
    }
}
