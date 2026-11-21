package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What ties the Deep Ones to the rest of the game: the reputation changes (hurting or killing a Deep One, killing a
 * mine guardian), the ink bomb (mod {@code InkBombItem} and {@code InkBombEntity}), the gazing pearl that shows the
 * reputation of whoever carries it, and the experience Deep Ones give.
 */
public final class DeepOneItems implements Listener, Runnable, AutoCloseable {
    static final String INK_BOMB = "ac_ink_bomb";
    static final String GLOW_INK_BOMB = "ac_glow_ink_bomb";
    static final String GAZING_PEARL = "ac_gazing_pearl";
    /** Mod {@code ThrownProjectileItem(..., 0.65F, ...)} against a snowball's 1.5. */
    static final double BOMB_SPEED = 0.65 / 1.5;
    private static NamespacedKey bombKey;

    private final ModMobService mobs;
    private final NamespacedKey pearlKey;
    private long tick;

    public DeepOneItems(ModMobService mobs) {
        this.mobs = mobs;
        bombKey = new NamespacedKey(mobs.plugin(), "alexscaves_ink_bomb");
        this.pearlKey = new NamespacedKey(mobs.plugin(), "alexscaves_gazing_pearl");
    }

    // --- reputation -----------------------------------------------------------------------------------------

    /** Mod {@code DeepOneBaseEntity.hurt}: -5 for a player who hurts a Deep One, -15 when the hit kills it. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHurt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || mobs.typeOf(victim).filter(DeepOnes.ALL::contains).isEmpty()
            || event.getCause() == EntityDamageEvent.DamageCause.THORNS) {
            return;
        }
        Entity damager = event.getDamager();
        if (damager instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof Player player && event.getFinalDamage() > 0) {
            boolean dies = victim.getHealth() - event.getFinalDamage() <= 0;
            DeepOnes.addReputation(mobs, player.getUniqueId(), dies ? -15 : -5);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        var type = mobs.typeOf(entity);
        if (type.isEmpty()) {
            return;
        }
        if (DeepOnes.ALL.contains(type.get())) {
            event.setDroppedExp(8);
        } else if (type.get().equals(AbyssalMobs.MINE_GUARDIAN)
            && event.getDamageSource().getCausingEntity() instanceof Player player) {
            // Mod MineGuardianEntity.die: killing a mine guardian makes up for 1 to 3 points of a bad reputation.
            DeepOneReputation store = DeepOneReputation.of(mobs.plugin());
            int relations = store.get(player.getUniqueId());
            if (relations < 0) {
                DeepOnes.addReputation(mobs, player.getUniqueId(), ThreadLocalRandom.current().nextInt(3) + 1);
            }
        }
    }

    // --- ink bomb ---------------------------------------------------------------------------------------------

    /** Throws an ink bomb the way the Deep Ones do ({@code InkBombEntity.shoot}); returns the projectile. */
    static Snowball throwInkBomb(ModMobContext context, LivingEntity thrower, Location from, Vector direction, double speed,
                                 double inaccuracy, boolean glowing) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector velocity = direction.clone().normalize().multiply(speed).add(new Vector(random.nextGaussian() * 0.0075 * inaccuracy,
            random.nextGaussian() * 0.0075 * inaccuracy, random.nextGaussian() * 0.0075 * inaccuracy));
        return from.getWorld().spawn(from, Snowball.class, snowball -> {
            snowball.setShooter(thrower);
            snowball.setItem(context.mobs().createItem(glowing ? GLOW_INK_BOMB : INK_BOMB, 1).orElse(new ItemStack(org.bukkit.Material.INK_SAC)));
            snowball.getPersistentDataContainer().set(bombKey, PersistentDataType.BYTE, (byte) (glowing ? 2 : 1));
            snowball.setVelocity(velocity);
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) {
            return;
        }
        var id = mobs.itemId(snowball.getItem());
        if (id.isPresent() && (id.get().equals(INK_BOMB) || id.get().equals(GLOW_INK_BOMB))) {
            snowball.getPersistentDataContainer().set(bombKey, PersistentDataType.BYTE, (byte) (id.get().equals(GLOW_INK_BOMB) ? 2 : 1));
            // Mod: thrown 0.65 fast, 20 degrees above the look.
            Vector velocity = snowball.getVelocity().multiply(BOMB_SPEED);
            velocity.setY(velocity.getY() + Math.sin(Math.toRadians(20)) * 0.65);
            snowball.setVelocity(velocity);
            snowball.getWorld().playSound(snowball.getLocation(), "minecraft:entity.snowball.throw", 0.5F, 0.4F);
        }
    }

    /** Mod {@code InkBombEntity.onHit}: blinds what it hits and leaves a cloud of ink that blinds (and, glowing, lights up). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball bomb)) {
            return;
        }
        Byte kind = bomb.getPersistentDataContainer().get(bombKey, PersistentDataType.BYTE);
        if (kind == null) {
            return;
        }
        bomb.getPersistentDataContainer().remove(bombKey);
        boolean glowing = kind == 2;
        if (event.getHitEntity() instanceof LivingEntity living) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
            if (!(living instanceof Player player && player.getGameMode() == org.bukkit.GameMode.CREATIVE)) {
                living.removePotionEffect(PotionEffectType.NIGHT_VISION);
                living.removePotionEffect(PotionEffectType.CONDUIT_POWER);
            }
        }
        Location at = bomb.getLocation().add(0, 0.2, 0);
        bomb.remove();
        at.getWorld().spawnParticle(Particle.ITEM_SLIME, at, 8, 0.1, 0.1, 0.1, 0.0);
        at.getWorld().spawn(at, AreaEffectCloud.class, cloud -> {
            cloud.setParticle(glowing ? Particle.GLOW_SQUID_INK : Particle.SQUID_INK);
            cloud.setWaitTime(0);
            cloud.addCustomEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0), true);
            if (glowing) {
                cloud.addCustomEffect(new PotionEffect(PotionEffectType.GLOWING, 300, 0), true);
            }
            cloud.setRadius(2.0F);
            cloud.setDuration(60);
            cloud.setRadiusPerTick(-2.0F / 60);
        });
    }

    // --- gazing pearl -------------------------------------------------------------------------------------------

    /** Mod {@code GazingPearlItem.inventoryTick}: the pearl remembers its carrier's reputation (checked every 100 ticks). */
    @Override
    public void run() {
        tick++;
        DeepOneReputation store = DeepOneReputation.of(mobs.plugin());
        if (tick % 200 == 0 && store.dirty()) {
            store.save(mobs.plugin().getLogger());
        }
        if (tick % 100 != 0) {
            return;
        }
        for (Player player : mobs.plugin().getServer().getOnlinePlayers()) {
            DeepOneReaction reaction = store.reactionOf(player.getUniqueId());
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = contents[slot];
                if (stack != null && !stack.isEmpty() && mobs.itemId(stack).filter(GAZING_PEARL::equals).isPresent()
                    && !reaction.name().equals(stack.getItemMeta().getPersistentDataContainer().get(pearlKey, PersistentDataType.STRING))) {
                    stack.editMeta(meta -> {
                        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
                        if (meta.getPersistentDataContainer().has(pearlKey, PersistentDataType.STRING) && !lore.isEmpty()) {
                            lore.remove(lore.size() - 1);
                        }
                        lore.add(Component.text(pearlDescription(reaction), NamedTextColor.GRAY, TextDecoration.ITALIC)
                            .decoration(TextDecoration.ITALIC, true));
                        meta.lore(lore);
                        meta.getPersistentDataContainer().set(pearlKey, PersistentDataType.STRING, reaction.name());
                    });
                    player.getInventory().setItem(slot, stack);
                }
            }
        }
    }

    static String pearlDescription(DeepOneReaction reaction) {
        return switch (reaction) {
            case STALKING -> "El destello de la perla declina cautelosamente...";
            case AGGRESSIVE -> "La perla reluce con malevolencia...";
            case NEUTRAL -> "La perla refleja una luz neutral...";
            case HELPFUL -> "La perla deslumbra con un alegre resplandor...";
        };
    }

    @Override
    public void close() {
        DeepOneReputation.release(mobs.plugin());
    }
}
