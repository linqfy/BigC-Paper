package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.enchant.ModEnchantment;
import dev.linqfy.bigCasares.modules.alexscaves.core.enchant.ModEnchantments;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Desolate Dagger (mod {@code DesolateDaggerItem}, {@code DesolateDaggerEntity}): every hit with it summons a ghostly
 * blade that circles the victim for 20 ticks (plus up to 9) and then stabs it for 2 damage. Double Stab summons two,
 * Impending Stab waits 40 ticks and stabs for 2 more per level, Sated Blade feeds a hungry wielder.
 */
public final class DesolateDagger implements Listener, Runnable, AutoCloseable {
    static final String DAGGER = "ac_desolate_dagger";
    /** Mod: the stab progress grows by 0.2 a tick once the orbit is over. */
    private static final float STAB_STEP = 0.2F;
    static final int MAX_BLADES_PER_PLAYER = 8;
    static final int MAX_BLADES_GLOBAL = 64;

    private final ModMobService mobs;
    private final ModEnchantments enchantments;
    private final List<Blade> blades = new ArrayList<>();
    private boolean stabbing;
    private LivingEntity stabTarget;
    private boolean stabLanded;
    private int serial;

    public DesolateDagger(ModMobService mobs) {
        this.mobs = mobs;
        this.enchantments = ModEnchantments.lookup();
    }

    /** Mod {@code hurtEnemy}: a landed hit with the dagger in hand. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent event) {
        if (stabbing || !(event.getDamager() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
            || !(event.getEntity() instanceof LivingEntity target) || target.isDead()) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (mobs.itemId(held).filter(DAGGER::equals).isEmpty()) {
            return;
        }
        int impending = enchantments.level(held, ModEnchantment.IMPENDING_STAB);
        int doubleStab = enchantments.level(held, ModEnchantment.DOUBLE_STAB);
        int sated = enchantments.level(held, ModEnchantment.SATED_BLADE);
        for (int i = 0; i < ForlornItemRules.daggerCount(doubleStab); i++) {
            summon(player, target, held, impending, sated);
        }
    }

    /** Mod {@code hurt} returning true: the stab counts when the damage event went through, even if armor or absorption ate it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStab(EntityDamageEvent event) {
        if (stabbing && event.getEntity().equals(stabTarget) && !event.isCancelled() && event.getDamage() > 0) {
            stabLanded = true;
        }
    }

    static boolean canSummon(int playerBlades, int totalBlades) {
        return playerBlades < MAX_BLADES_PER_PLAYER && totalBlades < MAX_BLADES_GLOBAL;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        blades.removeIf(blade -> {
            if (blade.owner.equals(id)) {
                blade.display.remove();
                return true;
            }
            return false;
        });
    }

    private void summon(Player player, LivingEntity target, ItemStack held, int impending, int sated) {
        UUID id = player.getUniqueId();
        int own = (int) blades.stream().filter(blade -> blade.owner.equals(id)).count();
        if (!canSummon(own, blades.size())) {
            return;
        }
        ItemStack look = held.clone();
        look.setAmount(1);
        Location start = target.getLocation().add(0, target.getHeight() * 0.25, 0);
        ItemDisplay display = start.getWorld().spawn(start, ItemDisplay.class, spawned -> {
            spawned.setItemStack(look);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            // The sprite's tip points up and to the right; turn it to lead along the display's forward axis.
            Quaternionf lean = new Quaternionf(new AxisAngle4f((float) Math.toRadians(90), 1, 0, 0))
                .mul(new Quaternionf(new AxisAngle4f((float) Math.toRadians(45), 0, 0, 1)));
            spawned.setTransformation(new Transformation(new Vector3f(), lean, new Vector3f(1.0F, 1.0F, 1.0F), new Quaternionf()));
            spawned.setTeleportDuration(2);
            spawned.setInterpolationDuration(2);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
        });
        int roll = ThreadLocalRandom.current().nextInt(10);
        blades.add(new Blade(display, target.getUniqueId(), player.getUniqueId(), serial++, ForlornItemRules.orbitTicks(impending, roll),
            impending, sated));
        start.getWorld().playSound(start, "alexscaves:desolate_dagger_summon", SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void run() {
        Iterator<Blade> iterator = blades.iterator();
        while (iterator.hasNext()) {
            Blade blade = iterator.next();
            Entity target = mobs.plugin().getServer().getEntity(blade.target);
            if (!blade.display.isValid() || !(target instanceof LivingEntity living)) {
                blade.display.remove();
                iterator.remove();
                continue;
            }
            blade.age++;
            float inverse = 1.0F - blade.stab;
            Location around = living.getLocation().add(0, living.getHeight() * 0.25, 0);
            // Mod: a seeded spot around the target that closes in as the stab nears (fixed per blade).
            Random orbit = new Random(blade.serial * 31L + 17);
            double angle = Math.toRadians(orbit.nextInt(360));
            double up = (orbit.nextFloat() + living.getHeight()) * inverse;
            double out = (orbit.nextFloat() + living.getWidth()) * inverse;
            Vector offset = new Vector(out * Math.sin(angle), up, out * Math.cos(angle));
            Location at = around.clone().add(offset);
            Vector aim = living.getLocation().add(0, living.getHeight() * 0.5, 0).toVector().subtract(at.toVector());
            if (aim.lengthSquared() > 1.0e-6) {
                at.setDirection(aim);
            }
            blade.display.teleport(at);
            at.getWorld().spawnParticle(Particle.DUST, at, 1, 0.3, 0.3, 0.3, 0.0, new Particle.DustOptions(Color.RED, 1.0F));
            if (blade.orbit > 0 && !living.isDead()) {
                blade.orbit--;
                continue;
            }
            blade.stab = Math.min(blade.stab + STAB_STEP, 1.0F);
            if (blade.stab >= 1.0F) {
                stab(blade, living);
                blade.display.remove();
                iterator.remove();
            }
        }
    }

    /** Mod: 2 damage (plus 2 per Impending Stab level) in the dagger's own damage type, caused by its wielder. */
    private void stab(Blade blade, LivingEntity target) {
        Player owner = mobs.plugin().getServer().getPlayer(blade.owner);
        DamageSource source = owner == null ? mobs.damageTypes().source("desolate_dagger") : mobs.damageTypes().source("desolate_dagger", owner);
        stabTarget = target;
        stabLanded = false;
        stabbing = true;
        try {
            target.damage(ForlornItemRules.stabDamage(blade.impending), source);
        } finally {
            stabbing = false;
            stabTarget = null;
        }
        if (stabLanded) {
            target.getWorld().playSound(target.getLocation(), "alexscaves:desolate_dagger_hit", SoundCategory.PLAYERS, 1.0F, 1.0F);
            if (owner != null && blade.sated > 0) {
                owner.setSaturation(ForlornItemRules.satedSaturation(owner.getSaturation(), blade.sated));
            }
        }
    }

    @Override
    public void close() {
        blades.forEach(blade -> blade.display.remove());
        blades.clear();
    }

    private static final class Blade {
        private final ItemDisplay display;
        private final UUID target;
        private final UUID owner;
        private final int serial;
        private final int impending;
        private final int sated;
        private int orbit;
        private int age;
        private float stab;

        private Blade(ItemDisplay display, UUID target, UUID owner, int serial, int orbit, int impending, int sated) {
            this.display = display;
            this.target = target;
            this.owner = owner;
            this.serial = serial;
            this.orbit = orbit;
            this.impending = impending;
            this.sated = sated;
        }
    }
}
