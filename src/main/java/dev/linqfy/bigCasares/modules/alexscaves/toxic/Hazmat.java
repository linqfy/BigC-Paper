package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Worn Hazmat suit pieces; each one takes 25 % off radiation and acid (mod {@code HazmatArmorItem}). */
public final class Hazmat {
    static final Set<String> PIECES = Set.of("ac_hazmat_mask", "ac_hazmat_chestplate", "ac_hazmat_leggings", "ac_hazmat_boots");

    private Hazmat() {
    }

    public static int pieces(LivingEntity entity, Function<ItemStack, Optional<String>> itemId) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return 0;
        }
        int worn = 0;
        for (ItemStack armor : equipment.getArmorContents()) {
            if (armor != null && !armor.isEmpty() && itemId.apply(armor).filter(PIECES::contains).isPresent()) {
                worn++;
            }
        }
        return worn;
    }
}
