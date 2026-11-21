package dev.linqfy.bigCasares.modules.netheritequest;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** "Alma de Boss": dropped to the top three damage dealers of a boss; it remembers which boss it came from. */
public final class BossSoulItem {

    private final NamespacedKey bossKey;

    public BossSoulItem(NamespacedKey bossKey) {
        this.bossKey = Objects.requireNonNull(bossKey, "bossKey");
    }

    public ItemStack create(String bossId, String bossName, int amount) {
        ItemStack stack = new ItemStack(Material.ECHO_SHARD, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName("§5§lAlma de " + bossName);
        meta.setLore(List.of(
            "§7Se gana quedando en el top 3 de daño.",
            "§7Activa una misión de armadura de netherita.",
            "§8Cada boss sirve para una sola pieza.",
            "§8Boss: " + bossId));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, bossId);
        stack.setItemMeta(meta);
        return stack;
    }

    public Optional<String> bossOf(ItemStack stack) {
        if (stack == null || stack.getType() != Material.ECHO_SHARD || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer()
            .get(bossKey, PersistentDataType.STRING));
    }
}
