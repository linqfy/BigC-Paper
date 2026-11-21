package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El doble de Bedrock del espejo no se puede vestir ni desvestir, y los reflejos no copian totems. */
class EnmaMirrorCopyTest {

    @Test
    void everyHandAndArmorSlotOfTheBedrockStandIsLockedBothWays() {
        Set<String> locks = new HashSet<>();
        ArmorStand stand = (ArmorStand) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {ArmorStand.class}, (proxy, method, args) -> {
                if (method.getName().equals("addEquipmentLock")) {
                    locks.add(args[0] + "/" + args[1]);
                }
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            });
        EnmaMirror.lockEquipment(stand);
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            for (ArmorStand.LockType lock : ArmorStand.LockType.values()) {
                assertTrue(locks.contains(slot + "/" + lock), slot + "/" + lock);
            }
        }
        assertEquals(6 * ArmorStand.LockType.values().length, locks.size());
    }

    @Test
    void reflectionsNeverCopyWhatSavesFromDeath() {
        assertFalse(EnmaFightRules.mirrorable(Material.TOTEM_OF_UNDYING, false));
        assertFalse(EnmaFightRules.mirrorable(Material.SHIELD, true));
        assertTrue(EnmaFightRules.mirrorable(Material.SHIELD, false));
        assertTrue(EnmaFightRules.mirrorable(Material.DIAMOND_SWORD, false));
    }
}
