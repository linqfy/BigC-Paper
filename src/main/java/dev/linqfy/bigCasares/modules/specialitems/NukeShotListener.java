package dev.linqfy.bigCasares.modules.specialitems;

import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public final class NukeShotListener implements Listener {

    private final NukeShotItem item;
    private final NukeAnimationRuntime runtime;

    public NukeShotListener(NukeShotItem item, NukeAnimationRuntime runtime) {
        this.item = Objects.requireNonNull(item, "item");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        // RIGHT_CLICK_AIR is delivered pre-cancelled, so ignoreCancelled would drop it;
        // honour other plugins through the item-use result instead.
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND
            || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        if (!item.matches(held)) {
            return;
        }
        event.setCancelled(true);
        if (held.getAmount() <= 1) {
            event.getPlayer().getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            held.setAmount(held.getAmount() - 1);
        }
        runtime.start(event.getPlayer());
    }
}
