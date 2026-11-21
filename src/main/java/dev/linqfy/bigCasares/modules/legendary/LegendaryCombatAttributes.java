package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Explicit per-weapon stats, independent of the neutral carrier's default attributes. */
final class LegendaryCombatAttributes {
    private static final NamespacedKey VERSION = new NamespacedKey("bigcasares", "legendary_combat_version");
    private static final int CURRENT_VERSION = 1;

    record Plan(List<AttributeModifier> damage, List<AttributeModifier> speed) { }

    private LegendaryCombatAttributes() { }

    static boolean initialize(ItemMeta meta, LegendaryWeapon weapon) {
        boolean changed = LegendaryItemIdentity.stamp(meta, weapon);
        if (Integer.valueOf(CURRENT_VERSION).equals(meta.getPersistentDataContainer()
                .get(VERSION, PersistentDataType.INTEGER))) return changed;
        Plan plan = plan(weapon, meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE),
                meta.getAttributeModifiers(Attribute.ATTACK_SPEED));
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        plan.damage().forEach(modifier -> meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, modifier));
        plan.speed().forEach(modifier -> meta.addAttributeModifier(Attribute.ATTACK_SPEED, modifier));
        meta.getPersistentDataContainer().set(VERSION, PersistentDataType.INTEGER, CURRENT_VERSION);
        return true;
    }

    static Plan plan(LegendaryWeapon weapon, Collection<AttributeModifier> damage,
                     Collection<AttributeModifier> speed) {
        // Keep the unmodified 26.2 values, including the native float rounding of attack speeds.
        double damageBonus = switch (weapon) {
            case BLOODLUST, SHADOW_BLADE -> 7.0;
            case WINDWEAVER -> 5.0;
            case EARTHBREAKER -> 9.0;
            case THUNDER_SPEAR -> 4.0;
            default -> throw new IllegalArgumentException("Arma retirada.");
        };
        double speedBonus = switch (weapon) {
            case BLOODLUST, SHADOW_BLADE -> (double) -2.4F;
            case WINDWEAVER -> (double) -3.4F;
            case EARTHBREAKER -> (double) -3.0F;
            case THUNDER_SPEAR -> (double) (1.0F / 1.15F) - 4.0;
            default -> throw new IllegalArgumentException("Arma retirada.");
        };
        return new Plan(replace(damage, weapon, "attack_damage", damageBonus),
                replace(speed, weapon, "attack_speed", speedBonus));
    }

    private static List<AttributeModifier> replace(Collection<AttributeModifier> current, LegendaryWeapon weapon,
                                                   String attribute, double amount) {
        var own = new NamespacedKey("bigcasares", weapon.id + "/" + attribute);
        var vanilla = NamespacedKey.minecraft("base_" + attribute);
        var result = new ArrayList<AttributeModifier>();
        if (current != null) {
            current.stream().filter(modifier -> !modifier.getKey().equals(own)
                    && !modifier.getKey().equals(vanilla)).forEach(result::add);
        }
        result.add(new AttributeModifier(own, amount, AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND));
        return List.copyOf(result);
    }
}
