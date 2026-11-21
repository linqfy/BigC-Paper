package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.effect.DarknessIncarnate;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectType;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The darkness armor (mod {@code DarknessArmorItem}). Worn together, the hood and the cloak fill the cloak's meter in
 * 1000 ticks of darkness (block light below 11, and no open daylight sky); with the meter full, the special key (swap
 * hands, F) turns the wearer into Darkness Incarnate for 200 ticks, which is flight at four times the speed until they
 * step into the light. The meter pauses while the effect runs.
 */
public final class DarknessArmor implements Listener, Runnable {
    static final String HOOD = "ac_hood_of_darkness";
    static final String CLOAK = "ac_cloak_of_darkness";
    /** The mod ticks the charge every tick and the light every 10: here both in steps of 10 ticks. */
    private static final int STEP = 10;

    private final ModMobService mobs;
    private final NamespacedKey chargeKey;
    private final NamespacedKey greetedKey;
    private long tick;

    public DarknessArmor(ModMobService mobs) {
        this.mobs = mobs;
        this.chargeKey = new NamespacedKey(mobs.plugin(), "alexscaves_cloak_charge");
        this.greetedKey = new NamespacedKey(mobs.plugin(), "alexscaves_cloak_greeted");
    }

    private boolean is(ItemStack stack, String id) {
        return stack != null && !stack.getType().isAir() && mobs.itemId(stack).filter(id::equals).isPresent();
    }

    private boolean hooded(Player player) {
        return is(player.getInventory().getHelmet(), HOOD);
    }

    private int charge(ItemStack cloak) {
        return cloak.getItemMeta().getPersistentDataContainer().getOrDefault(chargeKey, PersistentDataType.INTEGER, 0);
    }

    private void setCharge(ItemStack cloak, int charge) {
        ItemMeta meta = cloak.getItemMeta();
        meta.getPersistentDataContainer().set(chargeKey, PersistentDataType.INTEGER, charge);
        cloak.setItemMeta(meta);
    }

    /** Mod {@code canChargeUp(entity, creative)}: dark enough (or a creative player), hooded, and not already incarnate. */
    private boolean canCharge(Player player, boolean creativeCounts) {
        return (!DarknessIncarnate.isInLight(player, DarknessIncarnate.LIGHT_THRESHOLD)
            || creativeCounts && player.getGameMode() == GameMode.CREATIVE)
            && hooded(player) && !mobs.effects().has(player, ModEffectType.DARKNESS_INCARNATE);
    }

    @Override
    public void run() {
        if (++tick % STEP != 0) {
            return;
        }
        for (Player player : mobs.plugin().getServer().getOnlinePlayers()) {
            ItemStack cloak = player.getInventory().getChestplate();
            if (!is(cloak, CLOAK)) {
                continue;
            }
            greet(player, cloak);
            int charge = charge(cloak);
            if (charge < ForlornItemRules.CLOAK_CHARGE_TICKS && canCharge(player, true)) {
                int next = ForlornItemRules.charged(charge, STEP);
                setCharge(cloak, next);
                player.getInventory().setChestplate(cloak);
                showMeter(player, charge, next);
            }
        }
    }

    /** Mod: the first time the cloak is worn, it says how to use it. */
    private void greet(Player player, ItemStack cloak) {
        if (cloak.getItemMeta().getPersistentDataContainer().has(greetedKey, PersistentDataType.BYTE)) {
            return;
        }
        ItemMeta meta = cloak.getItemMeta();
        meta.getPersistentDataContainer().set(greetedKey, PersistentDataType.BYTE, (byte) 1);
        cloak.setItemMeta(meta);
        player.getInventory().setChestplate(cloak);
        player.sendActionBar(Component.text("Usá la habilidad especial (cambiar de mano, F) con la capa cargada para convertirte en Oscuridad encarnada",
            NamedTextColor.GRAY));
    }

    /** The meter of the mod's HUD, as the action bar: every tenth of it, and when it is full. */
    private void showMeter(Player player, int before, int after) {
        int step = ForlornItemRules.CLOAK_CHARGE_TICKS / 10;
        if (after / step == before / step) {
            return;
        }
        if (after >= ForlornItemRules.CLOAK_CHARGE_TICKS) {
            player.sendActionBar(Component.text("Capa de oscuridad cargada: cambiá de mano (F) para usarla", NamedTextColor.DARK_PURPLE));
        } else {
            int percent = (int) (ForlornItemRules.meterProgress(after) * 100);
            player.sendActionBar(Component.text("Capa de oscuridad: " + percent + "%", NamedTextColor.DARK_GRAY));
        }
    }

    /** Mod {@code onKeyPacket}: with the meter full, the key spends it for the flight, but only in the dark. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onKey(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack cloak = player.getInventory().getChestplate();
        if (!is(cloak, CLOAK) || charge(cloak) < ForlornItemRules.CLOAK_CHARGE_TICKS) {
            return;
        }
        if (canCharge(player, false)) {
            event.setCancelled(true);
            setCharge(cloak, 0);
            player.getInventory().setChestplate(cloak);
            mobs.effects().apply(player, ModEffectType.DARKNESS_INCARNATE, 0, ForlornItemRules.CLOAK_FLIGHT_TICKS);
        } else if (hooded(player) && !mobs.effects().has(player, ModEffectType.DARKNESS_INCARNATE)) {
            // In the light the key keeps its normal job (swapping hands); only the mod's warning is shown.
            player.sendActionBar(Component.text("No podés usar esta habilidad con luz...", NamedTextColor.RED));
        }
    }
}
