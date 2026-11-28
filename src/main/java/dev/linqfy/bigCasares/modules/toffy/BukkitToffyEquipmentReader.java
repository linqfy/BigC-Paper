package dev.linqfy.bigCasares.modules.toffy;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BukkitToffyEquipmentReader {
    private static final int HOTBAR_SIZE = 9;
    private static final NamespacedKey CATALOG_ID_KEY = new NamespacedKey("bigcasares", "item_id");

    private ToffyService keysFor;
    private Map<String, NamespacedKey> persistentKeys = Map.of();

    ToffyLoadout read(LivingEntity entity, ToffyService service) {
        if (entity == null) {
            return ToffyLoadout.empty();
        }
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return ToffyLoadout.empty();
        }
        Items items = new Items(service, persistentKeys(service));
        ToffyLoadout.Builder builder = ToffyLoadout.builder();
        worn(builder, items, ToffyArmorSlot.HELMET, equipment.getHelmet());
        worn(builder, items, ToffyArmorSlot.CHESTPLATE, equipment.getChestplate());
        worn(builder, items, ToffyArmorSlot.LEGGINGS, equipment.getLeggings());
        worn(builder, items, ToffyArmorSlot.BOOTS, equipment.getBoots());

        if (entity instanceof Player player) {
            Carried carried = Carried.of(player.getInventory());
            ToffySearch search = service.settings().search();
            if (search.armor() == ToffyArmorSearch.INVENTORY) {
                for (ItemStack stack : carried.stacks(ToffyHeldSearch.INVENTORY)) {
                    carriedArmor(builder, items, service, stack);
                }
            }
            for (ItemStack stack : carried.stacks(search.weapon())) {
                ToffyItem item = items.of(stack);
                if (item != null) {
                    builder.weapon(item);
                }
            }
            for (ItemStack stack : carried.stacks(search.tool())) {
                ToffyItem item = items.of(stack);
                if (item != null) {
                    builder.tool(item);
                }
            }
        } else {
            for (ItemStack stack : new ItemStack[] {equipment.getItemInMainHand(), equipment.getItemInOffHand()}) {
                ToffyItem item = items.of(stack);
                if (item != null) {
                    builder.held(item);
                }
            }
        }
        return builder.build();
    }

    /**
     * The player's carried items read once per check, so every search sees the same stack objects and each one is
     * converted only once.
     */
    private record Carried(ItemStack[] storage, ItemStack offHand, int heldSlot) {
        static Carried of(PlayerInventory inventory) {
            return new Carried(inventory.getStorageContents(), inventory.getItemInOffHand(),
                inventory.getHeldItemSlot());
        }

        List<ItemStack> stacks(ToffyHeldSearch search) {
            List<ItemStack> stacks = new ArrayList<>();
            switch (search) {
                case MAIN_HAND -> stacks.add(mainHand());
                case HANDS -> {
                    stacks.add(mainHand());
                    stacks.add(offHand);
                }
                case HOTBAR -> {
                    for (int slot = 0; slot < HOTBAR_SIZE && slot < storage.length; slot++) {
                        stacks.add(storage[slot]);
                    }
                    stacks.add(offHand);
                }
                case INVENTORY -> {
                    stacks.addAll(Arrays.asList(storage));
                    stacks.add(offHand);
                }
            }
            return stacks;
        }

        private ItemStack mainHand() {
            return heldSlot >= 0 && heldSlot < storage.length ? storage[heldSlot] : null;
        }
    }

    private static void worn(ToffyLoadout.Builder builder, Items items, ToffyArmorSlot slot, ItemStack stack) {
        ToffyItem item = items.of(stack);
        if (item != null) {
            builder.armor(slot, item);
        }
    }

    private static void carriedArmor(ToffyLoadout.Builder builder, Items items, ToffyService service,
                                     ItemStack stack) {
        ToffyItem item = items.of(stack);
        if (item == null || service.categoryOf(item) != ToffyItemCategory.ARMOR) {
            return;
        }
        ToffyArmorSlot slot = service.armorSlotOf(item);
        if (slot == null) {
            slot = armorSlot(stack);
        }
        if (slot != null) {
            builder.armor(slot, item);
        }
    }

    private static ToffyArmorSlot armorSlot(ItemStack stack) {
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta.hasEquippable()) {
                return armorSlot(meta.getEquippable().getSlot());
            }
        }
        return armorSlot(stack.getType().getEquipmentSlot());
    }

    private static ToffyArmorSlot armorSlot(EquipmentSlot slot) {
        if (slot == null) {
            return null;
        }
        return switch (slot) {
            case HEAD -> ToffyArmorSlot.HELMET;
            case CHEST -> ToffyArmorSlot.CHESTPLATE;
            case LEGS -> ToffyArmorSlot.LEGGINGS;
            case FEET -> ToffyArmorSlot.BOOTS;
            default -> null;
        };
    }

    private synchronized Map<String, NamespacedKey> persistentKeys(ToffyService service) {
        if (keysFor != service) {
            Map<String, NamespacedKey> keys = new LinkedHashMap<>();
            for (String rawKey : service.persistentDataKeys()) {
                NamespacedKey key = NamespacedKey.fromString(rawKey);
                if (key != null) {
                    keys.put(rawKey, key);
                }
            }
            persistentKeys = Map.copyOf(keys);
            keysFor = service;
        }
        return persistentKeys;
    }

    static ToffyItem toItem(ItemStack stack, ToffyService service, Map<String, NamespacedKey> persistentKeys) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return null;
        }
        String material = stack.getType().getKey().toString();
        if (!service.mayCount(material)) {
            return null;
        }
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> enchantment : stack.getEnchantments().entrySet()) {
            enchantments.put(enchantment.getKey().getKey().toString(), enchantment.getValue());
        }
        String catalogId = null;
        String itemModel = null;
        Map<String, String> persistentData = new LinkedHashMap<>();
        if (service.needsCustomData(material) && stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();
            catalogId = readString(data, CATALOG_ID_KEY);
            if (meta.hasItemModel() && meta.getItemModel() != null) {
                itemModel = meta.getItemModel().toString();
            }
            persistentKeys.forEach((rawKey, key) -> {
                String value = readString(data, key);
                if (value != null) {
                    persistentData.put(rawKey, value);
                }
            });
        }
        return service.identify(new ToffyItemIdentity(material, catalogId, itemModel, persistentData, enchantments));
    }

    private static String readString(PersistentDataContainer data, NamespacedKey key) {
        return data.has(key, PersistentDataType.STRING) ? data.get(key, PersistentDataType.STRING) : null;
    }

    private static final class Items {
        private final ToffyService service;
        private final Map<String, NamespacedKey> persistentKeys;
        private final Map<ItemStack, ToffyItem> read = new IdentityHashMap<>();

        private Items(ToffyService service, Map<String, NamespacedKey> persistentKeys) {
            this.service = service;
            this.persistentKeys = persistentKeys;
        }

        ToffyItem of(ItemStack stack) {
            if (stack == null) {
                return null;
            }
            if (!read.containsKey(stack)) {
                read.put(stack, toItem(stack, service, persistentKeys));
            }
            return read.get(stack);
        }
    }
}
