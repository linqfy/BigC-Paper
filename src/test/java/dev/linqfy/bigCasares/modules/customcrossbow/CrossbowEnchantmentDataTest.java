package dev.linqfy.bigCasares.modules.customcrossbow;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossbowEnchantmentDataTest {

    private final CrossbowEnchantmentData data = new CrossbowEnchantmentData();

    @Test
    void applicationPreservesOtherLoreAndStoresTheEnchantOnTheCrossbow() {
        TestCrossbow crossbow = new TestCrossbow();
        crossbow.lore = new ArrayList<>(List.of("Player lore"));
        data.apply(crossbow, CrossbowEnchantment.SNIPER);

        assertEquals(CrossbowEnchantment.SNIPER, data.enchantment(crossbow));
        assertNotNull(data.weaponId(crossbow));
        assertEquals("Player lore", crossbow.lore.getFirst());
        assertEquals(1, crossbow.lore.stream().filter(CrossbowEnchantment.SNIPER.loreLine()::equals).count());
    }

    @Test
    void fivePaidRoundsSurviveMetadataCopiesAndRunOutExactlyOnce() {
        TestCrossbow crossbow = new TestCrossbow();
        data.apply(crossbow, CrossbowEnchantment.MULTICHARGE);
        crossbow.persistent.put(key("crossbow_magazine_rounds"), 5);
        crossbow.persistent.put(key("crossbow_magazine_ammo"), new byte[]{1, 2, 3});

        for (int remaining = 5; remaining > 0; remaining--) {
            crossbow = crossbow.clone();
            assertEquals(remaining, data.rounds(crossbow));
            data.consumeRound(crossbow);
        }
        assertEquals(0, data.rounds(crossbow));
        assertFalse(crossbow.persistent.containsKey(key("crossbow_magazine_ammo")));
        data.consumeRound(crossbow);
        assertEquals(0, data.rounds(crossbow));
    }

    @Test
    void grindstoneRemovalKeepsUnrelatedLoreAndClearsGameplayData() {
        TestCrossbow crossbow = new TestCrossbow();
        crossbow.lore = new ArrayList<>(List.of("Player lore"));
        data.apply(crossbow, CrossbowEnchantment.MULTICHARGE);
        crossbow.persistent.put(key("crossbow_magazine_rounds"), 3);
        crossbow.persistent.put(key("crossbow_magazine_ammo"), new byte[]{1});

        data.remove(crossbow);

        assertNull(data.enchantment(crossbow));
        assertEquals(List.of("Player lore"), crossbow.lore);
        assertFalse(crossbow.persistent.containsKey(key("crossbow_magazine_ammo")));
        assertFalse(crossbow.persistent.containsKey(key("crossbow_magazine_rounds")));
    }

    @Test
    void updatesOldSpyglassInstructionsWithoutChangingTheWeaponIdentity() {
        TestCrossbow crossbow = new TestCrossbow();
        data.apply(crossbow, CrossbowEnchantment.SNIPER);
        java.util.UUID identity = data.weaponId(crossbow);
        crossbow.lore = new ArrayList<>(List.of("Player lore", "§7Francotirador I",
            "§7Apuntá con un catalejo en la otra mano.",
            "§7Soltá para gastar hasta 6 corazones y disparar."));

        data.refreshSniperLore(crossbow);

        assertEquals(identity, data.weaponId(crossbow));
        assertEquals("Player lore", crossbow.lore.getFirst());
        assertTrue(crossbow.lore.containsAll(CrossbowEnchantment.SNIPER.description()));
        assertFalse(crossbow.lore.contains("§7Apuntá con un catalejo en la otra mano."));
        assertFalse(crossbow.lore.contains("§7Soltá para gastar hasta 6 corazones y disparar."));
    }

    private static NamespacedKey key(String key) {
        return new NamespacedKey("bigcasares", key);
    }

    /** Metadata copies model persistence without a running Bukkit item factory. */
    static final class TestCrossbow extends ItemStack {
        final Map<NamespacedKey, Object> persistent = new HashMap<>();
        List<String> lore = new ArrayList<>();
        private final ItemMeta meta;

        @SuppressWarnings("unchecked")
        TestCrossbow() {
            super(Material.CROSSBOW);
            PersistentDataContainer container = (PersistentDataContainer) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{PersistentDataContainer.class}, (self, method, args) ->
                    switch (method.getName()) {
                        case "set" -> { persistent.put((NamespacedKey) args[0], args[2]); yield null; }
                        case "get" -> persistent.get(args[0]);
                        case "getOrDefault" -> persistent.getOrDefault(args[0], args[2]);
                        case "has" -> persistent.containsKey(args[0]);
                        case "remove" -> { persistent.remove(args[0]); yield null; }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            meta = (ItemMeta) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CrossbowMeta.class},
                (self, method, args) -> switch (method.getName()) {
                    case "getPersistentDataContainer" -> container;
                    case "hasLore" -> !lore.isEmpty();
                    case "getLore" -> new ArrayList<>(lore);
                    case "setLore" -> { lore = args[0] == null ? new ArrayList<>() : new ArrayList<>((List<String>) args[0]); yield null; }
                    case "setEnchantmentGlintOverride" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        }

        @Override
        public Material getType() { return Material.CROSSBOW; }
        @Override
        public boolean hasItemMeta() { return true; }
        @Override
        public ItemMeta getItemMeta() { return meta; }
        @Override
        public boolean setItemMeta(ItemMeta value) { return true; }
        @Override
        public TestCrossbow clone() {
            TestCrossbow copy = new TestCrossbow();
            copy.persistent.putAll(persistent);
            copy.lore = new ArrayList<>(lore);
            return copy;
        }
    }
}
