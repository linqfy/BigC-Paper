package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Primordial armor bonus (mod {@code DinosaurChopBlock.eat} with
 * {@code PrimordialArmorItem.getExtraSaturationFromArmor}): each worn piece adds one shank per
 * bite of raw dinosaur chop and 0.125 to the bite's saturation modifier.
 */
public final class PrimordialArmor implements Listener {
    static final List<String> PIECES = List.of("ac_primordial_helmet", "ac_primordial_tunic", "ac_primordial_pants");
    /** Raw chop bite in the mod: 3 food, 0.2 saturation modifier. The item here is worth this many bites. */
    static final int BITES_PER_ITEM = 2;
    private static final int BITE_FOOD = 3;
    private static final double BITE_MODIFIER = 0.2;

    private final ModMobService mobs;

    public PrimordialArmor(ModMobService mobs) {
        this.mobs = mobs;
    }

    /** Extra {food, saturation} for eating one raw chop item with {@code pieces} armor pieces worn. */
    static double[] bonus(int pieces) {
        double base = BITE_FOOD * BITE_MODIFIER * 2.0;
        double boosted = (BITE_FOOD + pieces) * (BITE_MODIFIER + pieces * 0.125) * 2.0;
        return new double[] {pieces * BITES_PER_ITEM, (boosted - base) * BITES_PER_ITEM};
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEat(PlayerItemConsumeEvent event) {
        if (mobs.itemId(event.getItem()).filter("ac_dinosaur_chop"::equals).isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        int pieces = 0;
        for (ItemStack worn : player.getInventory().getArmorContents()) {
            if (mobs.itemId(worn).filter(PIECES::contains).isPresent()) {
                pieces++;
            }
        }
        if (pieces == 0) {
            return;
        }
        double[] extra = bonus(pieces);
        // Applied on top of the item's own food, which vanilla adds right after this event.
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + (int) extra[0]));
        // Capped at 20: vanilla adds the item's own food next and clamps saturation to the food level then.
        player.setSaturation((float) Math.min(20.0, player.getSaturation() + extra[1]));
    }
}
