package dev.linqfy.bigCasares.modules.bounties;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class PaymentPaperListener implements Listener {

    private final PaymentPaper paymentPaper;
    private final PaymentVoucherService vouchers;
    private final BountyEconomy economy;

    public PaymentPaperListener(PaymentPaper paymentPaper, PaymentVoucherService vouchers, BountyEconomy economy) {
        this.paymentPaper = paymentPaper;
        this.vouchers = vouchers;
        this.economy = economy;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        // RIGHT_CLICK_AIR is delivered pre-cancelled, so ignoreCancelled would drop it;
        // honour other plugins through the item-use result instead.
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || !isRightClick(event.getAction())) {
            return;
        }
        ItemStack paper = event.getItem();
        UUID voucherId = paymentPaper.voucherId(paper).orElse(null);
        if (voucherId == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        PaymentClaimResult result = vouchers.claim(voucherId, player.getUniqueId());
        switch (result) {
            case CLAIMED -> {
                consumeMainHandPaper(player, paper);
                player.sendMessage(ChatColor.GREEN + "Cobraste " + economy.format(amount(voucherId)) + ".");
            }
            case ALREADY_CLAIMED -> player.sendMessage(ChatColor.RED + "Este vale ya fue cobrado.");
            case CLAIM_IN_PROGRESS -> player.sendMessage(ChatColor.RED + "Este vale esta siendo procesado. Contacta a un administrador si persiste.");
            case PAYOUT_FAILED -> player.sendMessage(ChatColor.RED + "No se pudo confirmar el cobro. El vale fue bloqueado para evitar duplicados; contacta a un administrador.");
            case NOT_FOUND -> player.sendMessage(ChatColor.RED + "Este vale no es válido.");
        }
    }

    private double amount(UUID voucherId) {
        // The claim succeeded only for a stored voucher. The paper never controls the payout amount.
        return vouchers.amount(voucherId);
    }

    private void consumeMainHandPaper(Player player, ItemStack paper) {
        if (paper.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            paper.setAmount(paper.getAmount() - 1);
        }
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }
}
