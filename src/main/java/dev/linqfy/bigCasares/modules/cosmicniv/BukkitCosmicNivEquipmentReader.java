package dev.linqfy.bigCasares.modules.cosmicniv;

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

final class BukkitCosmicNivEquipmentReader {
    private static final int HOTBAR_SIZE = 9;
    private static final NamespacedKey CATALOG_ID_KEY = new NamespacedKey("bigcasares", "item_id");

    private CosmicNivService keysFor;
    private Map<String, NamespacedKey> persistentKeys = Map.of();

    CosmicNivLoadout read(LivingEntity entity, CosmicNivService service) {
        if (entity == null) {
            return CosmicNivLoadout.empty();
        }
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return CosmicNivLoadout.empty();
        }
        Items items = new Items(service, persistentKeys(service));
        CosmicNivLoadout.Builder builder = CosmicNivLoadout.builder();
        worn(builder, items, CosmicNivArmorSlot.HELMET, equipment.getHelmet());
        worn(builder, items, CosmicNivArmorSlot.CHESTPLATE, equipment.getChestplate());
        worn(builder, items, CosmicNivArmorSlot.LEGGINGS, equipment.getLeggings());
        worn(builder, items, CosmicNivArmorSlot.BOOTS, equipment.getBoots());

        if (entity instanceof Player player) {
            Carried carried = Carried.of(player.getInventory());
            CosmicNivSearch search = service.settings().search();
            if (search.armor() == CosmicNivArmorSearch.INVENTORY) {
                for (ItemStack stack : carried.stacks(CosmicNivHeldSearch.INVENTORY)) {
                    carriedArmor(builder, items, service, stack);
                }
            }
            for (ItemStack stack : carried.stacks(search.weapon())) {
                CosmicNivItem item = items.of(stack);
                if (item != null) {
                    builder.weapon(item);
                }
            }
            for (ItemStack stack : carried.stacks(search.tool())) {
                CosmicNivItem item = items.of(stack);
                if (item != null) {
                    builder.tool(item);
                }
            }
        } else {
            for (ItemStack stack : new ItemStack[] {equipment.getItemInMainHand(), equipment.getItemInOffHand()}) {
                CosmicNivItem item = items.of(stack);
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

        List<ItemStack> stacks(CosmicNivHeldSearch search) {
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

    private static void worn(CosmicNivLoadout.Builder builder, Items items, CosmicNivArmorSlot slot, ItemStack stack) {
        CosmicNivItem item = items.of(stack);
        if (item != null) {
            builder.armor(slot, item);
        }
    }

    private static void carriedArmor(CosmicNivLoadout.Builder builder, Items items, CosmicNivService service,
                                     ItemStack stack) {
        CosmicNivItem item = items.of(stack);
        if (item == null || service.categoryOf(item) != CosmicNivItemCategory.ARMOR) {
            return;
        }
        CosmicNivArmorSlot slot = armorSlot(stack);
        if (slot != null) {
            builder.armor(slot, item);
        }
    }

    private static CosmicNivArmorSlot armorSlot(ItemStack stack) {
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta.hasEquippable()) {
                return armorSlot(meta.getEquippable().getSlot());
            }
        }
        return armorSlot(stack.getType().getEquipmentSlot());
    }

    private static CosmicNivArmorSlot armorSlot(EquipmentSlot slot) {
        if (slot == null) {
            return null;
        }
        return switch (slot) {
            case HEAD -> CosmicNivArmorSlot.HELMET;
            case CHEST -> CosmicNivArmorSlot.CHESTPLATE;
            case LEGS -> CosmicNivArmorSlot.LEGGINGS;
            case FEET -> CosmicNivArmorSlot.BOOTS;
            default -> null;
        };
    }

    private synchronized Map<String, NamespacedKey> persistentKeys(CosmicNivService service) {
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

    static CosmicNivItem toItem(ItemStack stack, CosmicNivService service, Map<String, NamespacedKey> persistentKeys) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return null;
        }
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> enchantment : stack.getEnchantments().entrySet()) {
            enchantments.put(enchantment.getKey().getKey().toString(), enchantment.getValue());
        }
        String catalogId = null;
        String itemModel = null;
        Map<String, String> persistentData = new LinkedHashMap<>();
        if (!service.settings().customItems().isEmpty() && stack.hasItemMeta()) {
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
        return service.identify(new CosmicNivItemIdentity(stack.getType().getKey().toString(), catalogId, itemModel,
            persistentData, enchantments));
    }

    private static String readString(PersistentDataContainer data, NamespacedKey key) {
        return data.has(key, PersistentDataType.STRING) ? data.get(key, PersistentDataType.STRING) : null;
    }

    private static final class Items {
        private final CosmicNivService service;
        private final Map<String, NamespacedKey> persistentKeys;
        private final Map<ItemStack, CosmicNivItem> read = new IdentityHashMap<>();

        private Items(CosmicNivService service, Map<String, NamespacedKey> persistentKeys) {
            this.service = service;
            this.persistentKeys = persistentKeys;
        }

        CosmicNivItem of(ItemStack stack) {
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
