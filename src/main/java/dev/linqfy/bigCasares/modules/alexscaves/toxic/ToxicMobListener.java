package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.GameRules;
import org.bukkit.entity.Creeper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExplosionPrimeEvent;

/**
 * The Nucleeper blows up as a nuclear explosion instead of a creeper blast (mod {@code NucleeperEntity.explode}:
 * size 1, 1.75 when charged; no block damage when {@code mobGriefing} is off).
 */
public final class ToxicMobListener implements Listener, AutoCloseable {
    private final ModMobService mobs;

    public ToxicMobListener(ModMobService mobs) {
        this.mobs = mobs;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper)
            || mobs.typeOf(creeper).filter(type -> type == ToxicMobs.NUCLEEPER).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        float size = creeper.isPowered() ? NukeMath.CHARGED_NUCLEEPER_SIZE : NukeMath.NUCLEEPER_SIZE;
        boolean griefing = !Boolean.FALSE.equals(creeper.getWorld().getGameRuleValue(GameRules.MOB_GRIEFING));
        var center = creeper.getLocation();
        creeper.remove();
        NuclearExplosion.detonate(mobs, center, size, griefing);
    }

    /** Running nuclear explosions stop with the module. */
    @Override
    public void close() {
        NuclearExplosion.cancelAll();
        ThrownWasteDrum.cancelAll();
    }
}
