package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Abyssal items and interactions: eating a sea pig (mod {@code ACFoods.SEA_PIG}: hunger for a minute, 7 times in 10),
 * feeding a sea pig clay, mud or muck (mod tag {@code sea_pig_digests}), and the depth charge (mod
 * {@code DepthChargeEntity}: thrown, 2-3 damage on a hit, it stops where it lands and blows up 30 ticks later with a
 * radius 2 mine explosion).
 */
public final class AbyssalItems implements Listener {
    static final String DEPTH_CHARGE = "ac_depth_charge";
    /** Mod {@code ThrownProjectileItem(..., 0.65F, ...)} against a snowball's 1.5. */
    static final double CHARGE_SPEED = 0.65 / 1.5;

    private final ModMobService mobs;
    private final NamespacedKey thrownKey;

    public AbyssalItems(ModMobService mobs) {
        this.mobs = mobs;
        this.thrownKey = new NamespacedKey(mobs.plugin(), "alexscaves_depth_charge");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEat(PlayerItemConsumeEvent event) {
        if (mobs.itemId(event.getItem()).filter("ac_sea_pig"::equals).isPresent() && ThreadLocalRandom.current().nextFloat() < 0.7F) {
            event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 1200, 0));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFeed(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (mobs.typeOf(clicked).filter(AbyssalMobs.SEA_PIG::equals).isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        if (!digestible(held)) {
            return;
        }
        event.setCancelled(true);
        if (clicked instanceof LivingEntity pig && AbyssalMobs.seaPig(mobs, pig).map(seaPig -> seaPig.feed(pig)).orElse(false)
            && player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
    }

    /** Mod tag {@code sea_pig_digests}: clay balls, mud and muck. */
    private boolean digestible(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        var id = mobs.itemId(stack);
        if (id.isPresent()) {
            return id.get().equals("ac_muck");
        }
        return stack.getType() == Material.CLAY_BALL || stack.getType() == Material.MUD;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Snowball snowball && mobs.itemId(snowball.getItem()).filter(DEPTH_CHARGE::equals).isPresent()) {
            snowball.getPersistentDataContainer().set(thrownKey, PersistentDataType.BYTE, (byte) 1);
            Vector velocity = snowball.getVelocity().multiply(CHARGE_SPEED);
            // Mod: thrown 10 degrees above the look.
            velocity.setY(velocity.getY() + Math.sin(Math.toRadians(10)) * 0.65);
            snowball.setVelocity(velocity);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball charge) || !charge.getPersistentDataContainer().has(thrownKey, PersistentDataType.BYTE)) {
            return;
        }
        charge.getPersistentDataContainer().remove(thrownKey);
        Entity shooter = charge.getShooter() instanceof Entity thrower ? thrower : null;
        if (event.getHitEntity() instanceof LivingEntity target && !target.equals(shooter)) {
            double damage = 2 + ThreadLocalRandom.current().nextInt(2);
            if (shooter != null) {
                target.damage(damage, shooter);
            } else {
                target.damage(damage);
            }
        }
        Location at = charge.getLocation();
        ItemStack look = charge.getItem().clone();
        charge.remove();
        ItemDisplay lying = at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(look);
            display.setPersistent(false);
        });
        mobs.tasks().repeat(new org.bukkit.scheduler.BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (!lying.isValid()) {
                    cancel();
                    return;
                }
                if (lying.getLocation().getBlock().getType() == Material.WATER) {
                    lying.getWorld().spawnParticle(Particle.BUBBLE, lying.getLocation(), 4, 0.1, 0.1, 0.1, 0.0);
                }
                if (++ticks > 30) {
                    cancel();
                    Location center = lying.getLocation();
                    lying.remove();
                    // Mod: the charge itself explodes, so its thrower is hurt too; the thrower is the cause.
                    MineExplosion.explode(lying, shooter == null ? lying : shooter, center, 2.0F);
                }
            }
        }, 1L, 1L, lying::remove);
    }

    /** The guardian under a mine guardian must not prick its attackers (vanilla spikes). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onThorns(EntityDamageByEntityEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.THORNS
            && mobs.typeOf(event.getDamager()).filter(AbyssalMobs.MINE_GUARDIAN::equals).isPresent()) {
            event.setCancelled(true);
        }
    }
}
