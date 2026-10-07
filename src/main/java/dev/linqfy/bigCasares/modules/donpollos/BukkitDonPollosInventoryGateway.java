package dev.linqfy.bigCasares.modules.donpollos;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class BukkitDonPollosInventoryGateway implements DonPollosInventoryGateway {

    private final Player player;
    private final DonPollosItems items;

    BukkitDonPollosInventoryGateway(Player player, DonPollosItems items) {
        this.player = player;
        this.items = items;
    }

    @Override
    public void giveProduct(DonPollosProduct product, int amount) {
        player.getInventory().addItem(items.create(product, amount)).values()
            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    @Override
    public int count(String material) {
        ItemStack plain = new ItemStack(Material.valueOf(material));
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.isSimilar(plain)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    @Override
    public boolean remove(String material, int amount) {
        return player.getInventory().removeItem(new ItemStack(Material.valueOf(material), amount)).isEmpty();
    }

    @Override
    public void give(String material, int amount) {
        Material type = Material.valueOf(material);
        int remaining = amount;
        while (remaining > 0) {
            int chunk = Math.min(remaining, type.getMaxStackSize());
            player.getInventory().addItem(new ItemStack(type, chunk)).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            remaining -= chunk;
        }
    }
}
