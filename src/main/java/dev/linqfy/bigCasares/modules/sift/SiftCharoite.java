package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.view.EnchantmentView;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Charoite enchanting and the anvil rules that go with the mod's items
 * ({@code CharoiteEnchanting}, {@code EnchantmentMenuMixin} and {@code AnvilMenuMixin}).
 *
 * <p>Charoite in the enchanting table's lapis slot raises each offer's level
 * requirement by 20 and enchants one level above the rolled level, beyond the
 * vanilla maximum, for enchantments with more than one level. The item is marked
 * and can no longer be combined in an anvil. The anvil also only repairs Siftite
 * gear with Siftite ingots (the mod's {@code siftite_repair_materials}) and keeps
 * Sift items from combining with the vanilla items they are built on.</p>
 *
 * <p>Differences: Charoite is a lapis lazuli underneath (the table's slot only takes
 * lapis), and the recipe guard keeps it out of lapis recipes. Paper rolls the
 * enchantments from the displayed cost, which includes the 20 extra levels, so they
 * are rolled again from the base cost with the table's seed through Paper's
 * {@code enchantWithLevels}, the vanilla selection; this is expected to match the
 * preview, but it has not been checked on a server. For a book the vanilla table
 * drops one of several rolled enchantments; this port drops one in a fixed order
 * and never the previewed one, so the remaining set can differ from vanilla's.</p>
 */
final class SiftCharoite implements Listener {

    static final NamespacedId CHAROITE = NamespacedId.of(SiftBlocks.NS, "charoite");
    static final NamespacedId SIFTITE_INGOT = NamespacedId.of(SiftBlocks.NS, "siftite_ingot");
    /** The mod's extra level requirement for a Charoite enchantment. */
    static final int EXTRA_COST = 20;
    static final String BETTER_ENCHANTER = "the_sift/better_enchanter";

    private static final Set<NamespacedId> SIFTITE_GEAR = Stream.of(SiftiteGear.values())
        .map(piece -> NamespacedId.of(SiftBlocks.NS, piece.path())).collect(Collectors.toUnmodifiableSet());

    private final NamespacedKey usedKey = new NamespacedKey(SiftBlocks.NS, "charoite_used");

    /** Offer level requirement with Charoite. */
    static int cost(int cost) {
        return cost > 0 ? cost + EXTRA_COST : cost;
    }

    /** The requirement the enchantments are rolled from (the mod's {@code max(1, cost - 20)}). */
    static int baseCost(int displayedCost) {
        return Math.max(1, displayedCost - EXTRA_COST);
    }

    /** Charoite adds a level to enchantments that have more than one. */
    static int level(int level, int maxLevel) {
        return level >= 0 && maxLevel > 1 ? level + 1 : level;
    }

    /** One side of an anvil: content id (null for vanilla items), material and the Charoite mark. */
    record AnvilInput(NamespacedId contentId, String material, boolean charoiteUsed) {
        static final AnvilInput EMPTY = new AnvilInput(null, "AIR", false);

        boolean isEmpty() {
            return contentId == null && material.equals("AIR");
        }
    }

    /**
     * Whether the anvil may combine these: never with a Charoite-enchanted item; a Sift
     * item only with an enchanted book, another of the same item, or (Siftite gear) a
     * Siftite ingot; vanilla items as in vanilla.
     */
    static boolean anvilAllows(AnvilInput base, AnvilInput addition) {
        if (base.charoiteUsed() || addition.charoiteUsed()) {
            return false;
        }
        if (addition.isEmpty() || base.contentId() == null && addition.contentId() == null) {
            return true;
        }
        if (addition.contentId() == null && addition.material().equals("ENCHANTED_BOOK")) {
            return true;
        }
        if (Objects.equals(base.contentId(), addition.contentId())) {
            return true;
        }
        return base.contentId() != null && SIFTITE_GEAR.contains(base.contentId())
            && SIFTITE_INGOT.equals(addition.contentId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (!(event.getInventory() instanceof EnchantingInventory table) || !isCharoite(table.getSecondary())) {
            return;
        }
        for (EnchantmentOffer offer : event.getOffers()) {
            if (offer != null) {
                offer.setCost(cost(offer.getCost()));
                offer.setEnchantmentLevel(level(offer.getEnchantmentLevel(), offer.getEnchantment().getMaxLevel()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (!(event.getInventory() instanceof EnchantingInventory table) || !isCharoite(table.getSecondary())
            || !(event.getView() instanceof EnchantmentView view)) {
            return;
        }
        Map<Enchantment, Integer> rolled = roll(event.getItem(), baseCost(event.getExpLevelCost()),
            view.getEnchantmentSeed() + event.whichButton(), event.getEnchantmentHint());
        if (rolled.isEmpty()) {
            return;
        }
        event.getEnchantsToAdd().clear();
        rolled.forEach((enchantment, rolledLevel) ->
            event.getEnchantsToAdd().put(enchantment, level(rolledLevel, enchantment.getMaxLevel())));
        ItemStack marked = event.getItem().clone();
        marked.editPersistentDataContainer(data -> data.set(usedKey, PersistentDataType.BOOLEAN, true));
        event.setItem(marked);
        SiftAdvancements.award(event.getEnchanter(), BETTER_ENCHANTER);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory anvil = event.getInventory();
        if (!anvilAllows(input(anvil.getFirstItem()), input(anvil.getSecondItem()))) {
            event.setResult(null);
        }
    }

    /** The vanilla table roll for {@code levels}, seeded like the table ({@code seed + button}). */
    private static Map<Enchantment, Integer> roll(ItemStack item, int levels, int seed, Enchantment hint) {
        Random random = new Random(seed);
        ItemStack enchanted = Bukkit.getItemFactory().enchantWithLevels(item.clone(), levels, false, random);
        Map<Enchantment, Integer> rolled = new LinkedHashMap<>(enchanted.getItemMeta() instanceof EnchantmentStorageMeta storage
            ? storage.getStoredEnchants() : enchanted.getEnchantments());
        if (item.getType() == Material.BOOK && rolled.size() > 1) {
            List<Enchantment> removable = new ArrayList<>(rolled.keySet());
            removable.remove(hint);
            removable.sort(Comparator.comparing(enchantment -> enchantment.getKey().asString()));
            if (!removable.isEmpty()) {
                rolled.remove(removable.get(random.nextInt(removable.size())));
            }
        }
        return rolled;
    }

    private static boolean isCharoite(ItemStack stack) {
        return ContentItemFactory.idOf(stack).filter(CHAROITE::equals).isPresent();
    }

    private AnvilInput input(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return AnvilInput.EMPTY;
        }
        boolean used = stack.getPersistentDataContainer().has(usedKey, PersistentDataType.BOOLEAN);
        return new AnvilInput(ContentItemFactory.idOf(stack).orElse(null), stack.getType().name(), used);
    }
}
