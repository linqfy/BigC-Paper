package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Limestone spear (mod {@code LimestoneSpearEntity}): 4 damage on hit, then it lies on the ground
 * to be picked up again. The mod charges it like a bow up to 2.5 speed; here it is thrown like a
 * snowball (its base item) and launched faster.
 */
public final class PrimordialSpear implements Listener {
    static final String SPEAR = "ac_limestone_spear";
    static final double DAMAGE = 4.0;
    static final double SPEED_FACTOR = 1.6;

    private final ModMobService mobs;
    private final NamespacedKey thrownKey;

    public PrimordialSpear(ModMobService mobs) {
        this.mobs = mobs;
        this.thrownKey = new NamespacedKey(mobs.plugin(), "alexscaves_thrown_spear");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Snowball snowball && mobs.itemId(snowball.getItem()).filter(SPEAR::equals).isPresent()) {
            snowball.getPersistentDataContainer().set(thrownKey, PersistentDataType.BYTE, (byte) 1);
            snowball.setVelocity(snowball.getVelocity().multiply(SPEED_FACTOR));
        }
    }

    /** Last, and only once: a deflected or re-hitting snowball must not drop a second spear. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball spear) || !spear.getPersistentDataContainer().has(thrownKey, PersistentDataType.BYTE)) {
            return;
        }
        spear.getPersistentDataContainer().remove(thrownKey);
        spear.remove();
        Entity shooter = spear.getShooter() instanceof Entity thrower ? thrower : null;
        if (event.getHitEntity() instanceof LivingEntity target && !target.equals(shooter)) {
            if (shooter != null) {
                target.damage(DAMAGE, shooter);
            } else {
                target.damage(DAMAGE);
            }
        }
        Location at = spear.getLocation();
        at.getWorld().playSound(at, Sound.ITEM_TRIDENT_HIT, 0.8f, 1.2f);
        if (!(shooter instanceof Player player) || player.getGameMode() != GameMode.CREATIVE) {
            ItemStack dropped = spear.getItem().clone();
            dropped.setAmount(1);
            at.getWorld().dropItemNaturally(at, dropped);
        }
    }
}
