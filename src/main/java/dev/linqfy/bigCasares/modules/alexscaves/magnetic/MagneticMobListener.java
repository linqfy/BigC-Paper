package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.SlimeSplitEvent;

/** Removes vanilla behaviour of the Magnetic mobs' base entities that the mod does not have. */
public final class MagneticMobListener implements Listener {
    private final ModMobService mobs;

    public MagneticMobListener(ModMobService mobs) {
        this.mobs = mobs;
    }

    /** Teletors use a blaze base for hovering, but never shoot fireballs. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof LivingEntity shooter
            && mobs.typeOf(shooter).filter(type -> type == MagneticMobs.TELETOR).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** Ferrouslimes split into smaller ferrouslimes, not vanilla slimes. */
    @EventHandler(ignoreCancelled = true)
    public void onSplit(SlimeSplitEvent event) {
        if (!(event.getEntity() instanceof Slime slime)) {
            return;
        }
        if (mobs.typeOf(slime).filter(type -> type == MagneticMobs.FERROUSLIME).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        int heads = slime.getSize() / 2;
        if (heads < 1) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            mobs.spawn(MagneticMobs.FERROUSLIME.id(), slime.getLocation()).ifPresent(child -> {
                if (child instanceof Slime childSlime) {
                    MagneticMobs.Ferrouslime.heads(childSlime, heads);
                }
            });
        }
    }
}
