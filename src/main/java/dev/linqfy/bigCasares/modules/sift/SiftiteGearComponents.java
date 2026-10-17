package dev.linqfy.bigCasares.modules.sift;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DamageResistant;
import io.papermc.paper.datacomponent.item.Enchantable;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.KineticWeapon;
import io.papermc.paper.datacomponent.item.SwingAnimation;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.DamageTypeTagKeys;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.util.TriState;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockType;
import org.bukkit.damage.DamageType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;

/**
 * Writes the stats of {@link SiftiteGear} onto a stack of its netherite base item, with the data component
 * API: maximum damage, enchantability, fire resistance, the attribute modifiers that replace netherite's, the
 * tool rules of the mining tools, the {@code the_sift:siftite} equipment asset and equip sound of the armour,
 * and the swing animation and kinetic weapon of the spear. This is the thin Paper-side half; everything it
 * writes is computed (and tested) in {@link SiftiteGear}.
 *
 * <p>Differences: the mod builds these components in item properties when it registers the items; here they
 * are written to every stack the content item factory creates. Components that Siftite shares with netherite
 * (weapon, piercing weapon, attack range, use effects, damage type, repair rule, rarity) are left to the base
 * item. Registry tags are looked up when a stack is created, so the stack can only be created on a running
 * server.</p>
 */
final class SiftiteGearComponents {

    private SiftiteGearComponents() {
    }

    /** Applies {@code gear}'s stats to {@code stack}, a stack of its base material. */
    static void apply(SiftiteGear gear, ItemStack stack) {
        stack.setData(DataComponentTypes.MAX_DAMAGE, gear.durability());
        stack.setData(DataComponentTypes.ENCHANTABLE, Enchantable.enchantable(SiftiteGear.ENCHANTMENT_VALUE));
        stack.setData(DataComponentTypes.DAMAGE_RESISTANT, DamageResistant.damageResistant(fireDamage()));
        stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, attributes(gear));
        if (gear.hasToolRules()) {
            stack.setData(DataComponentTypes.TOOL, tool(gear));
        }
        if (gear.kind().isArmor()) {
            stack.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(equipmentSlot(gear.slot()))
                .assetId(Key.key(SiftiteGear.EQUIPMENT_ASSET))
                .equipSound(Key.key(SiftiteGear.EQUIP_SOUND)));
        }
        gear.spear().ifPresent(spear -> applySpear(stack, spear));
    }

    private static ItemAttributeModifiers.Builder attributes(SiftiteGear gear) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.itemAttributes();
        for (SiftiteGear.Modifier modifier : gear.modifiers()) {
            builder.addModifier(
                attribute(modifier.attribute()),
                new AttributeModifier(NamespacedKey.fromString(modifier.key()), modifier.amount(),
                    AttributeModifier.Operation.ADD_NUMBER, slotGroup(modifier.slot())));
        }
        return builder;
    }

    private static Tool.Builder tool(SiftiteGear gear) {
        // Vanilla: default speed 1, one damage per block, usable to break blocks in creative.
        Tool.Builder builder = Tool.tool().defaultMiningSpeed(1.0F).damagePerBlock(1).canDestroyBlocksInCreative(true);
        for (SiftiteGear.ToolRule rule : gear.toolRules()) {
            builder.addRule(Tool.rule(blocks(rule.blockTag()), rule.speed(), TriState.byBoolean(rule.correctForDrops())));
        }
        return builder;
    }

    /**
     * Spear: only what the mod's {@code spear(...)} call changes against netherite. The kinetic weapon starts
     * from the base item's own (contact cooldown, forward movement, sounds) and takes the mod's timings,
     * thresholds and damage multiplier.
     */
    private static void applySpear(ItemStack stack, SiftiteGear.SpearStats spear) {
        stack.setData(DataComponentTypes.SWING_ANIMATION, SwingAnimation.swingAnimation()
            .type(SwingAnimation.Animation.STAB).duration(spear.swingTicks()));
        KineticWeapon base = stack.getData(DataComponentTypes.KINETIC_WEAPON);
        KineticWeapon.Builder kinetic = KineticWeapon.kineticWeapon()
            .delayTicks(spear.delayTicks())
            .dismountConditions(KineticWeapon.condition(spear.dismountTicks(), spear.dismountMinSpeed(), 0.0F))
            .knockbackConditions(KineticWeapon.condition(spear.knockbackTicks(), spear.knockbackMinSpeed(), 0.0F))
            .damageConditions(KineticWeapon.condition(spear.damageTicks(), 0.0F, spear.damageMinRelativeSpeed()))
            .damageMultiplier(spear.damageMultiplier());
        if (base != null) {
            kinetic.contactCooldownTicks(base.contactCooldownTicks()).forwardMovement(base.forwardMovement())
                .sound(base.sound()).hitSound(base.hitSound());
        }
        stack.setData(DataComponentTypes.KINETIC_WEAPON, kinetic);
    }

    private static RegistryKeySet<BlockType> blocks(String tag) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK).getTag(TagKey.create(RegistryKey.BLOCK, tag));
    }

    private static RegistryKeySet<DamageType> fireDamage() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).getTag(DamageTypeTagKeys.IS_FIRE);
    }

    private static Attribute attribute(SiftiteGear.Attr attribute) {
        return switch (attribute) {
            case ATTACK_DAMAGE -> Attribute.ATTACK_DAMAGE;
            case ATTACK_SPEED -> Attribute.ATTACK_SPEED;
            case ARMOR -> Attribute.ARMOR;
            case ARMOR_TOUGHNESS -> Attribute.ARMOR_TOUGHNESS;
            case KNOCKBACK_RESISTANCE -> Attribute.KNOCKBACK_RESISTANCE;
        };
    }

    private static EquipmentSlotGroup slotGroup(SiftiteGear.Slot slot) {
        return switch (slot) {
            case MAINHAND -> EquipmentSlotGroup.MAINHAND;
            case HEAD -> EquipmentSlotGroup.HEAD;
            case CHEST -> EquipmentSlotGroup.CHEST;
            case LEGS -> EquipmentSlotGroup.LEGS;
            case FEET -> EquipmentSlotGroup.FEET;
        };
    }

    private static EquipmentSlot equipmentSlot(SiftiteGear.Slot slot) {
        return switch (slot) {
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
            case MAINHAND -> EquipmentSlot.HAND;
        };
    }
}
