package dev.linqfy.bigCasares.modules.alexscaves.core.enchant;

import java.util.Optional;

/** Exposes the package-private enchantment rule to tests in other packages. */
public final class EnchantmentGuardTestAccess {
    private EnchantmentGuardTestAccess() {
    }

    public static boolean allowedOn(Optional<String> itemId, ModEnchantment enchantment) {
        return EnchantmentGuardListener.allowedOn(itemId, enchantment);
    }
}
