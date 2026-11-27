package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/** Primordial mob rules that the vanilla bases do not have. */
public final class PrimordialMobListener implements Listener {
    private final ModMobService mobs;

    public PrimordialMobListener(ModMobService mobs) {
        this.mobs = mobs;
    }

    /** Subterranodons are neutral: the phantom base never picks a player on its own, only after being hit. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getReason() == EntityTargetEvent.TargetReason.CLOSEST_PLAYER
            && mobs.typeOf(event.getEntity()).filter(type -> type == PrimordialMobs.SUBTERRANODON).isPresent()) {
            event.setCancelled(true);
        }
    }
}
