package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectType;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.RadiationFormula;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Radioactive items in the inventory irradiate their carrier (mod: per tick, chance =
 * count x item chance x (1 - hazmat/4), only while not irradiated; Irradiated 1800 ticks,
 * amplifier 0). Checked once a second with the chance of 20 ticks.
 */
public final class ToxicRadiation implements Listener, Runnable {
    /** Module item -> chance per item per tick (mod {@code RadioactiveItem}/block items). */
    static final Map<String, Double> RADIOACTIVE = Map.of(
        "ac_uranium", 0.001,
        "ac_uranium_shard", 0.001,
        "ac_radrock_uranium_ore", 0.001,
        "ac_block_of_uranium", 0.001,
        "ac_unrefined_waste", 0.001);
    static final int CHECK_TICKS = 20;

    private final ModMobService mobs;
    private long tick;

    public ToxicRadiation(ModMobService mobs) {
        this.mobs = mobs;
    }

    /** Chance over {@code ticks} ticks of an event with {@code perTick} chance each tick. */
    static double overTicks(double perTick, int ticks) {
        return 1.0 - Math.pow(1.0 - Math.min(1.0, perTick), ticks);
    }

    @Override
    public void run() {
        if (++tick % CHECK_TICKS != 0) {
            return;
        }
        for (Player player : mobs.plugin().getServer().getOnlinePlayers()) {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || mobs.effects().has(player, ModEffectType.IRRADIATED)) {
                continue;
            }
            int hazmat = Hazmat.pieces(player, mobs::itemId);
            double perTick = 0;
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                Double chance = mobs.itemId(stack).map(RADIOACTIVE::get).orElse(null);
                if (chance != null) {
                    perTick += RadiationFormula.carriedItemChance(stack.getAmount(), chance, hazmat);
                }
            }
            if (perTick > 0 && ThreadLocalRandom.current().nextDouble() < overTicks(perTick, CHECK_TICKS)) {
                mobs.effects().apply(player, ModEffectType.IRRADIATED, 0, RadiationFormula.CARRIED_ITEM_DURATION_TICKS);
            }
        }
    }
}
