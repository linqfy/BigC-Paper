package dev.linqfy.bigCasares.modules.airdrop;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class AirdropListener implements Listener {

    private final AirdropService service;
    private final Map<String, Material> lootMaterials;

    private AirdropPosition chestPosition;

    public AirdropListener(AirdropService service, Map<String, Material> lootMaterials) {
        this.service = service;
        this.lootMaterials = lootMaterials;
    }

    public void setChestPosition(AirdropPosition position) {
        this.chestPosition = position;
    }

    public void clearChestPosition() {
        this.chestPosition = null;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (chestPosition == null) return;
        if (!service.isActive()) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (clicked.getType() != Material.CHEST) return;
        if (!chestPosition.matchesBlock(clicked.getX(), clicked.getY(), clicked.getZ())) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        List<AirdropLootEntry> loot = service.getLootForCurrent();
        if (!service.claim()) {
            return;
        }

        fillPlayerInventory(player, loot);
        clicked.setType(Material.AIR);
        chestPosition = null;

        player.getServer().broadcastMessage("Â§aâœ” " + player.getName() + " reclamÃ³ el airdrop!");
    }

    private void fillPlayerInventory(Player player, List<AirdropLootEntry> loot) {
        Inventory inv = player.getInventory();
        for (AirdropLootEntry entry : loot) {
            Material material = lootMaterials.get(entry.materialName());
            if (material == null) {
                throw new IllegalStateException("Unvalidated airdrop material: " + entry.materialName());
            }
            ItemStack stack = new ItemStack(material, entry.amount());
            inv.addItem(stack);
        }
    }
}
