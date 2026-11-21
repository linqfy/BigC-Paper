package dev.linqfy.bigCasares.modules.trimabilities;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Tracks which players wear a full trim set and keeps their passive bonus applied. */
final class TrimAbilityRuntime {

    /** Refreshed every check; long enough that night vision never reaches its flicker window between checks. */
    private static final int EFFECT_TICKS = 300;
    private static final List<String> MANAGED_ATTRIBUTES = List.of(
        "movement_speed", "max_health", "knockback_resistance", "attack_speed");

    private final TrimSetRules rules;
    private final NamespacedKey modifierKey;
    private final Map<UUID, TrimAbility> active = new HashMap<>();

    TrimAbilityRuntime(TrimSetRules rules, NamespacedKey modifierKey) {
        this.rules = rules;
        this.modifierKey = modifierKey;
    }

    Optional<TrimAbility> abilityOf(Player player) {
        return Optional.ofNullable(active.get(player.getUniqueId()));
    }

    void check(Collection<? extends Player> players) {
        for (Player player : players) {
            check(player);
        }
    }

    void check(Player player) {
        TrimAbility current = resolve(player).orElse(null);
        TrimAbility previous = active.get(player.getUniqueId());
        if (previous != current) {
            if (previous != null) {
                remove(player, previous);
                player.sendActionBar(ChatColor.GRAY + "Aleación " + previous.displayName() + " desactivada.");
            }
            if (current == null) {
                active.remove(player.getUniqueId());
            } else {
                active.put(player.getUniqueId(), current);
                applyAttribute(player, current);
                player.sendMessage(ChatColor.AQUA + "✦ Aleación " + current.displayName() + " completa: "
                    + ChatColor.WHITE + current.description());
            }
        }
        if (current != null) {
            refreshEffect(player, current);
        }
    }

    void forget(Player player) {
        TrimAbility previous = active.remove(player.getUniqueId());
        if (previous != null) {
            remove(player, previous);
        }
        stripModifiers(player);
    }

    void shutdown(Collection<? extends Player> players) {
        for (Player player : players) {
            forget(player);
        }
        active.clear();
    }

    Optional<TrimAbility> resolve(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        List<TrimSetRules.Piece> pieces = new ArrayList<>(4);
        for (ItemStack item : armor) {
            pieces.add(piece(item));
        }
        return rules.fullSet(pieces);
    }

    private static TrimSetRules.Piece piece(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof ArmorMeta armorMeta) || !armorMeta.hasTrim()) {
            return new TrimSetRules.Piece(item.getType().name(), null);
        }
        NamespacedKey pattern = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN)
            .getKey(armorMeta.getTrim().getPattern());
        return new TrimSetRules.Piece(item.getType().name(), pattern == null ? null : pattern.asString());
    }

    private void refreshEffect(Player player, TrimAbility ability) {
        TrimAbility.Perk perk = ability.perk();
        if (perk.type() != TrimAbility.PerkType.EFFECT) {
            return;
        }
        if (perk.waterOnly() && !player.isInWater()) {
            return;
        }
        PotionEffectType type = effect(perk.keys().getFirst());
        if (type != null) {
            int ticks = perk.waterOnly() ? 60 : EFFECT_TICKS;
            player.addPotionEffect(new PotionEffect(type, ticks, 0, true, false, true));
        }
    }

    private void remove(Player player, TrimAbility ability) {
        TrimAbility.Perk perk = ability.perk();
        if (perk.type() == TrimAbility.PerkType.EFFECT) {
            PotionEffectType type = effect(perk.keys().getFirst());
            PotionEffect current = type == null ? null : player.getPotionEffect(type);
            // Only remove what this module gave: ambient, level I and no longer than our refresh window.
            if (current != null && current.isAmbient() && current.getAmplifier() == 0
                && current.getDuration() <= EFFECT_TICKS) {
                player.removePotionEffect(type);
            }
        }
        stripModifiers(player);
    }

    private void applyAttribute(Player player, TrimAbility ability) {
        TrimAbility.Perk perk = ability.perk();
        if (perk.type() != TrimAbility.PerkType.ATTRIBUTE) {
            return;
        }
        AttributeInstance instance = attributeInstance(player, perk.keys().getFirst());
        if (instance == null) {
            return;
        }
        instance.removeModifier(modifierKey);
        instance.addTransientModifier(new AttributeModifier(modifierKey, perk.amount(),
            perk.multiply() ? AttributeModifier.Operation.MULTIPLY_SCALAR_1 : AttributeModifier.Operation.ADD_NUMBER));
    }

    /** Removes our modifier from every managed attribute, including leftovers from a crash. */
    void stripModifiers(Player player) {
        for (String key : MANAGED_ATTRIBUTES) {
            AttributeInstance instance = attributeInstance(player, key);
            if (instance != null) {
                instance.removeModifier(modifierKey);
            }
        }
        AttributeInstance health = attributeInstance(player, "max_health");
        if (health != null && player.getHealth() > health.getValue()) {
            player.setHealth(health.getValue());
        }
    }

    private static AttributeInstance attributeInstance(Player player, String key) {
        Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        return attribute == null ? null : player.getAttribute(attribute);
    }

    static PotionEffectType effect(String key) {
        return Registry.MOB_EFFECT.get(NamespacedKey.minecraft(key));
    }
}
