package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Event side of the Candy Cavity mobs: the Gumbeeper bursting into gumballs instead of exploding (mod
 * {@code GumbeeperEntity}: 15-19 gumballs, 30-34 when charged) and bursting at once when lit with flint and steel,
 * gumballs that bounce off blocks and mobs a few times and hurt what they hit ({@code GumballEntity}), Caramel Cubes
 * splitting like the mod's (large into 2 medium, medium into 2-3 small) and dropping caramel only when the smallest
 * dies, the Caniac going only for players and whatever hurt it, and the Candicorn tamed with a caramel apple (1 in 3)
 * instead of by riding.
 */
public final class CandyMobListener implements Listener, AutoCloseable {
    static final NamespacedKey GUMBALL_DAMAGE = new NamespacedKey("bigcasares", "ac_gumball_damage");
    static final NamespacedKey GUMBALL_BOUNCES = new NamespacedKey("bigcasares", "ac_gumball_bounces");
    private final ModMobService mobs;

    public CandyMobListener(ModMobService mobs) {
        this.mobs = mobs;
    }

    private boolean is(Entity entity, ModMobType type) {
        return mobs.typeOf(entity).filter(found -> found == type).isPresent();
    }

    /** Fires a gumball: a snowball that looks like gumballs and carries its damage and bounces left. */
    static void launchGumball(ModMobContext context, LivingEntity shooter, Location from, Vector velocity, double damage,
                              int bounces) {
        launch(shooter, from, velocity, CandyMobs.gumballItem(context), damage, bounces);
    }

    private static void launch(LivingEntity shooter, Location from, Vector velocity, ItemStack item, double damage, int bounces) {
        Snowball gumball = from.getWorld().spawn(from, Snowball.class, ball -> {
            ball.setShooter(shooter);
            ball.setItem(item);
            ball.setVelocity(velocity);
            PersistentDataContainer data = ball.getPersistentDataContainer();
            data.set(GUMBALL_DAMAGE, PersistentDataType.DOUBLE, damage);
            data.set(GUMBALL_BOUNCES, PersistentDataType.INTEGER, bounces);
        });
        gumball.setVelocity(velocity);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper) || !is(creeper, CandyMobs.GUMBEEPER)) {
            return;
        }
        event.setCancelled(true);
        boolean charged = creeper.isPowered();
        double damage = CandyMobs.attribute(creeper, org.bukkit.attribute.Attribute.ATTACK_DAMAGE, 4.0) + (charged ? 2.0 : 0.0);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = (charged ? 30 : 15) + random.nextInt(5);
        Location center = creeper.getLocation();
        for (int i = 0; i < count; i++) {
            Location at = center.clone().add((random.nextDouble() - 0.5) * 0.6, 0.7 + random.nextFloat() * 0.5,
                (random.nextDouble() - 0.5) * 0.6);
            Vector velocity = new Vector(random.nextFloat() - 0.5F, random.nextFloat() - 0.25F, random.nextFloat() - 0.5F)
                .normalize().multiply(random.nextFloat() * 0.25F + 0.75F);
            launchGumball(mobs, creeper, at, velocity, damage, charged ? 10 : 5);
        }
        creeper.getWorld().playSound(center, "alexscaves:gumbeeper_explode", 1.5f, 1.0f);
        // Mod: the Gumbeeper is discarded, so nothing drops.
        creeper.remove();
    }

    /** Mod {@code GumbeeperEntity.mobInteract}: creeper igniters make it burst at once, gumballs or not. */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        ItemStack held = event.getHand() == EquipmentSlot.OFF_HAND ? event.getPlayer().getInventory().getItemInOffHand()
            : event.getPlayer().getInventory().getItemInMainHand();
        if (is(clicked, CandyMobs.GUMBEEPER)
            && (held.getType() == Material.FLINT_AND_STEEL || held.getType() == Material.FIRE_CHARGE)) {
            clicked.getPersistentDataContainer().set(CandyMobs.Gumbeeper.LEFT, PersistentDataType.INTEGER, 0);
            return;
        }
        if (is(clicked, CandyMobs.GUMMY_BEAR) && feedPotion(event.getPlayer(), clicked, held, event.getHand())) {
            event.setCancelled(true);
            return;
        }
        if (is(clicked, CandyMobs.CANDICORN) && clicked instanceof AbstractHorse horse && !horse.isTamed()
            && mobs.itemId(held).filter("ac_caramel_apple"::equals).isPresent()) {
            // Mod: a caramel apple tames it 1 time in 3.
            event.setCancelled(true);
            if (event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
                held.setAmount(held.getAmount() - 1);
            }
            if (ThreadLocalRandom.current().nextInt(3) == 0) {
                horse.setTamed(true);
                horse.setOwner(event.getPlayer());
                horse.getWorld().spawnParticle(org.bukkit.Particle.HEART, horse.getLocation().add(0, 1.5, 0), 6, 0.4, 0.4, 0.4);
            } else {
                horse.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, horse.getLocation().add(0, 1.5, 0), 6, 0.4, 0.4, 0.4, 0.01);
            }
        }
    }

    /**
     * Mod {@code GummyBearEntity.mobInteract}: a potion with lasting effects makes the bear digest it (it hands back the
     * bottle); it then scratches its back on a licoroot ({@link CandyMobs.GummyBear}).
     */
    private boolean feedPotion(org.bukkit.entity.Player player, Entity bear, ItemStack held, EquipmentSlot hand) {
        if (held.getType() != Material.POTION
            || bear.getPersistentDataContainer().has(CandyMobs.GummyBear.DIGESTING, PersistentDataType.BYTE)) {
            return false;
        }
        boolean instant = false;
        boolean effects = false;
        if (held.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta meta) {
            var base = meta.getBasePotionType();
            var all = new java.util.ArrayList<>(meta.getCustomEffects());
            if (base != null) {
                all.addAll(base.getPotionEffects());
            }
            effects = !all.isEmpty();
            instant = all.stream().anyMatch(effect -> effect.getType().isInstant());
        }
        if (!CandyAnimations.digestible(true, instant, effects)) {
            return false;
        }
        bear.getPersistentDataContainer().set(CandyMobs.GummyBear.DIGESTING, PersistentDataType.BYTE, (byte) 1);
        bear.getWorld().playSound(bear.getLocation(), "alexscaves:gummy_bear_eat", 1.0f, 1.0f);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            ItemStack bottle = new ItemStack(Material.GLASS_BOTTLE);
            if (held.getAmount() <= 1) {
                player.getInventory().setItem(hand, bottle);
            } else {
                held.setAmount(held.getAmount() - 1);
                player.getInventory().addItem(bottle).values().forEach(
                    left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            }
        }
        return true;
    }

    /** The Candicorn is tamed with caramel apples, not by riding it; it does not breed like a horse. */
    @EventHandler(ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (is(event.getEntity(), CandyMobs.CANDICORN)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (is(event.getMother(), CandyMobs.CANDICORN) || is(event.getFather(), CandyMobs.CANDICORN)
            || is(event.getMother(), CandyFolk.GINGERBREAD_MAN) || is(event.getFather(), CandyFolk.GINGERBREAD_MAN)) {
            event.setCancelled(true);
        }
    }

    /** Mod targeting: the Caniac goes for players and whatever hurt it, not the vindicator's villagers and golems. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!is(event.getEntity(), CandyMobs.CANIAC) || event.getTarget() == null || event.getTarget() instanceof Player) {
            return;
        }
        if (event.getReason() != EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY
            && event.getReason() != EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY) {
            event.setCancelled(true);
        }
    }

    /** The Caniac hurts only with its arms and lunge: the vindicator's own melee hit is dropped. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK && is(event.getDamager(), CandyMobs.CANIAC)
            && !CandyMobs.ABILITY_HIT.get()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGumballHit(ProjectileHitEvent event) {
        if (!CandyMobs.isGumball(event.getEntity())) {
            return;
        }
        Snowball gumball = (Snowball) event.getEntity();
        PersistentDataContainer data = gumball.getPersistentDataContainer();
        double damage = data.getOrDefault(GUMBALL_DAMAGE, PersistentDataType.DOUBLE, 4.0);
        int bounces = data.getOrDefault(GUMBALL_BOUNCES, PersistentDataType.INTEGER, 0);
        Entity shooter = gumball.getShooter() instanceof Entity entity ? entity : null;
        Vector normal;
        if (event.getHitEntity() != null) {
            if (event.getHitEntity() == shooter) {
                event.setCancelled(true);
                return;
            }
            if (event.getHitEntity() instanceof LivingEntity hit) {
                hit.damage(damage, shooter != null ? shooter : gumball);
                gumball.getWorld().playSound(gumball.getLocation(), "alexscaves:gumball_hit", 1.0f, 1.0f);
            }
            // Mod: gumballs bounce off what they hit too.
            normal = gumball.getLocation().toVector().subtract(event.getHitEntity().getLocation().toVector()).setY(0);
            normal = normal.lengthSquared() < 1.0E-6 ? new Vector(0, 1, 0) : normal.normalize();
        } else if (event.getHitBlockFace() != null) {
            normal = event.getHitBlockFace().getDirection();
        } else {
            return;
        }
        if (bounces <= 0) {
            return;
        }
        // Mod bounceFromDirection: the velocity reflected off the face, X and Z kept at 0.8 and Y at 0.5.
        Vector velocity = gumball.getVelocity();
        Vector reflected = velocity.clone().subtract(normal.clone().multiply(2.0 * velocity.dot(normal)));
        reflected.setX(reflected.getX() * 0.8).setY(reflected.getY() * 0.5).setZ(reflected.getZ() * 0.8);
        if (reflected.lengthSquared() < 0.01) {
            return;
        }
        Location from = gumball.getLocation().add(normal.clone().multiply(0.15));
        LivingEntity owner = shooter instanceof LivingEntity living ? living : null;
        gumball.getWorld().playSound(from, "alexscaves:gumball_bounce", 0.6f, 1.0f);
        launch(owner, from, reflected, gumball.getItem(), damage, bounces - 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplit(SlimeSplitEvent event) {
        if (!is(event.getEntity(), CandyMobs.CARAMEL_CUBE)) {
            return;
        }
        event.setCancelled(true);
        LivingEntity cube = event.getEntity();
        int size = CandyMobs.CaramelCube.size(cube).orElse(0);
        if (size <= 0) {
            return;
        }
        int children = size >= 2 ? 2 : 2 + ThreadLocalRandom.current().nextInt(2);
        double spread = size / 4.0;
        for (int i = 0; i < children; i++) {
            Location at = cube.getLocation().add((i % 2 - 0.5) * spread, 0.5, (i / 2 - 0.5) * spread);
            mobs.spawn(CandyMobs.CARAMEL_CUBE.id(), at).ifPresent(child -> {
                CandyMobs.CaramelCube.applySize(child, size - 1, true);
                // Mod: the children keep the parent's name, persistence, AI and invulnerability.
                child.customName(cube.customName());
                child.setInvulnerable(cube.isInvulnerable());
                if (child instanceof Mob childMob && cube instanceof Mob parentMob) {
                    childMob.setAware(parentMob.isAware());
                    childMob.setRemoveWhenFarAway(parentMob.getRemoveWhenFarAway());
                    childMob.setPersistent(parentMob.isPersistent());
                }
            });
        }
    }

    /** The Licowitch's potions are Hunger V splashes (mod {@code STRONG_HUNGER_POTION}: 1800 ticks), never at the hungry. */
    @EventHandler(ignoreCancelled = true)
    public void onPotion(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.ThrownPotion potion)
            || !(potion.getShooter() instanceof org.bukkit.entity.Witch witch) || !is(witch, CandyFolk.LICOWITCH)) {
            return;
        }
        LivingEntity target = witch.getTarget();
        if (target == null || target.hasPotionEffect(org.bukkit.potion.PotionEffectType.HUNGER)) {
            event.setCancelled(true);
            return;
        }
        mobs.behaviourOf(witch).ifPresent(behaviour -> {
            if (behaviour instanceof CandyFolk.Licowitch licowitch) {
                licowitch.potionSwing(witch, mobs);
            }
        });
        ItemStack hunger = new ItemStack(Material.SPLASH_POTION);
        if (hunger.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta meta) {
            meta.addCustomEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.HUNGER, 1800, 4), true);
            meta.setColor(org.bukkit.Color.fromRGB(0x58, 0x76, 0x53));
            hunger.setItemMeta(meta);
        }
        potion.setItem(hunger);
    }

    /**
     * The Gingerbread Man's own AI decides what it goes for (none of the fox's prey), except the target a Licowitch
     * gives it when possessed; Gummy Bears (polar bears) leave Gingerbread Men (foxes) alone.
     */
    @EventHandler(ignoreCancelled = true)
    public void onGingerbreadTarget(EntityTargetLivingEntityEvent event) {
        if (is(event.getEntity(), CandyFolk.GINGERBREAD_MAN)) {
            boolean possessed = event.getEntity().getPersistentDataContainer().has(CandyFolk.POSSESSED_BY);
            if (!possessed || event.getReason() != EntityTargetEvent.TargetReason.CUSTOM) {
                event.setCancelled(true);
            }
        } else if (event.getTarget() != null && is(event.getTarget(), CandyFolk.GINGERBREAD_MAN)
            && is(event.getEntity(), CandyMobs.GUMMY_BEAR)) {
            event.setCancelled(true);
        }
    }

    /** Peppermints still flying go with the module. */
    @Override
    public void close() {
        CandyFolk.removePeppermints();
    }

    @EventHandler(ignoreCancelled = true)
    public void onLove(org.bukkit.event.entity.EntityEnterLoveModeEvent event) {
        if (is(event.getEntity(), CandyFolk.GINGERBREAD_MAN) || is(event.getEntity(), CandyMobs.CANDICORN)) {
            event.setCancelled(true);
        }
    }

    /**
     * After the module's own drops (HIGH): caramel from the smallest cube, the Candicorn's saddle and armour, what a
     * Gingerbread Man was carrying, and the Licowitch's ice cream scoop (one of three flavours, 0-1 plus Looting).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (is(entity, CandyFolk.GINGERBREAD_MAN)) {
            ItemStack carried = CandyFolk.carried(entity);
            if (carried != null) {
                event.getDrops().add(carried);
                CandyFolk.carry(entity, null);
            }
            return;
        }
        if (is(entity, CandyFolk.LICOWITCH)) {
            Player killer = entity.getKiller();
            Enchantment looting = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("looting"));
            int level = killer == null || looting == null ? 0 : killer.getInventory().getItemInMainHand().getEnchantmentLevel(looting);
            String scoop = CandyFolk.SCOOPS[ThreadLocalRandom.current().nextInt(CandyFolk.SCOOPS.length)];
            int count = dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop.of(scoop, 0, 1)
                .roll(ThreadLocalRandom.current(), level, killer != null);
            if (count > 0) {
                mobs.createItem(scoop, count).ifPresent(event.getDrops()::add);
            }
            return;
        }
        if (is(entity, CandyMobs.CANDICORN) && entity instanceof AbstractHorse horse) {
            for (ItemStack item : horse.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    event.getDrops().add(item);
                }
            }
            return;
        }
        if (!is(entity, CandyMobs.CARAMEL_CUBE) || CandyMobs.CaramelCube.size(entity).orElse(0) != 0) {
            return;
        }
        Player killer = entity.getKiller();
        Enchantment looting = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("looting"));
        int level = killer == null || looting == null ? 0 : killer.getInventory().getItemInMainHand().getEnchantmentLevel(looting);
        int count = CandyMobs.CARAMEL_DROP.roll(ThreadLocalRandom.current(), level, killer != null);
        if (count > 0) {
            mobs.createItem(CandyMobs.CARAMEL_DROP.item(), count).ifPresent(event.getDrops()::add);
        }
    }
}
