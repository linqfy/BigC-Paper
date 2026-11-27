package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.effect.DarknessIncarnate;
import dev.linqfy.bigCasares.modules.alexscaves.core.enchant.ModEnchantment;
import dev.linqfy.bigCasares.modules.alexscaves.core.enchant.ModEnchantments;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * The Dreadbow (mod {@code DreadbowItem}, {@code DarkArrowEntity}). A bow that takes 40 ticks to draw and then, instead
 * of shooting, calls a volley down from the sky onto the spot it points at: up to 30 dark arrows for an ordinary
 * arrow (3 damage each, no armor to speak of, gone as they land), or 8 of any other kind. Its five enchantments are
 * Dark Nock (faster draw), Precise Volley (no scatter, 2 damage), Relentless Darkness (a stream of arrows while held),
 * Twilight Perfection (a window of double damage at full draw) and Shaded Respite (free in the dark).
 *
 * <p>The base item is a vanilla bow, so the client draws it and the server starts the shot; the draw length is the
 * mod's, counted here from the moment the player started to use it.
 */
public final class Dreadbow implements Listener, Runnable, AutoCloseable {
    static final String BOW = "ac_dreadbow";
    /** Mod {@code DarknessIncarnateEffect.isInLight(player, 11)} for Shaded Respite. */
    private static final int RESPITE_LIGHT = DarknessIncarnate.LIGHT_THRESHOLD;
    /** Mod {@code DarkArrowEntity}: fadeOut counts up from the landing and the arrow goes after more than 5. */
    private static final int FADE_TICKS = 6;
    private static final int MAX_ARROW_AGE = 400;
    private static final Color DARK_ARROW_COLOR = Color.fromRGB(0x15151F);

    private final ModMobService mobs;
    private final ModEnchantments enchantments;
    private final NamespacedKey damageKey;
    private final NamespacedKey perfectKey;
    private final Map<UUID, Draw> draws = new HashMap<>();
    private final Map<UUID, Integer> darkArrows = new HashMap<>();
    private long tick;

    public Dreadbow(ModMobService mobs) {
        this.mobs = mobs;
        this.enchantments = ModEnchantments.lookup();
        this.damageKey = new NamespacedKey(mobs.plugin(), "alexscaves_dark_arrow");
        this.perfectKey = new NamespacedKey(mobs.plugin(), "alexscaves_dark_arrow_perfect");
    }

    private boolean isBow(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && mobs.itemId(stack).filter(BOW::equals).isPresent();
    }

    // --- drawing -------------------------------------------------------------------------------------------

    /** The draw starts with the click; the server's use of the bow only reports it on release. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onUse(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getHand() == null || !isBow(event.getItem())) {
            return;
        }
        draws.put(event.getPlayer().getUniqueId(), new Draw(tick, event.getHand()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        draws.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void run() {
        tick++;
        Iterator<Map.Entry<UUID, Draw>> iterator = draws.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Draw> entry = iterator.next();
            Player player = mobs.plugin().getServer().getPlayer(entry.getKey());
            if (player == null || !player.isHandRaised() || !isBow(player.getInventory().getItem(entry.getValue().hand))) {
                // Released or switched: the shot event (if any) already ran; a stale draw would only mislead the next one.
                if (player == null || tick - entry.getValue().start > 2) {
                    iterator.remove();
                }
                continue;
            }
            ItemStack bow = player.getInventory().getItem(entry.getValue().hand);
            drawingEffects(player);
            if (enchantments.level(bow, ModEnchantment.RELENTLESS_DARKNESS) > 0
                && ForlornItemRules.relentlessBurst((int) (tick - entry.getValue().start))) {
                relentlessBurst(player, bow, entry.getValue().hand);
            }
        }
        Iterator<Map.Entry<UUID, Integer>> arrows = darkArrows.entrySet().iterator();
        while (arrows.hasNext()) {
            Map.Entry<UUID, Integer> entry = arrows.next();
            if (!(mobs.plugin().getServer().getEntity(entry.getKey()) instanceof AbstractArrow arrow) || !arrow.isValid()) {
                arrows.remove();
                continue;
            }
            int faded = arrow.isInBlock() ? entry.getValue() + 1 : entry.getValue();
            if (faded > FADE_TICKS || arrow.getTicksLived() > MAX_ARROW_AGE) {
                arrow.remove();
                arrows.remove();
            } else {
                entry.setValue(faded);
            }
        }
    }

    /** Mod {@code inventoryTick}: dark magic gathers round the archer while the bow is drawn. */
    private void drawingEffects(Player player) {
        RandomGenerator random = ThreadLocalRandom.current();
        Location at = player.getLocation().add((random.nextFloat() - 0.5) * 2.5, 0.1, (random.nextFloat() - 0.5) * 2.5);
        player.getWorld().spawnParticle(Particle.WITCH, at, 1, 0.0, 0.0, 0.0, 0.0);
    }

    // --- the volley ----------------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isBow(event.getBow())) {
            return;
        }
        event.setCancelled(true);
        ItemStack bow = player.getInventory().getItem(event.getHand());
        if (!isBow(bow)) {
            return;
        }
        boolean relentless = enchantments.level(bow, ModEnchantment.RELENTLESS_DARKNESS) > 0;
        Draw draw = draws.remove(player.getUniqueId());
        if (relentless) {
            // Mod: releasing does nothing; the stream already fired while it was held.
            return;
        }
        int maxLoad = ForlornItemRules.maxLoadTime(false, enchantments.level(bow, ModEnchantment.DARK_NOCK));
        int ticks = draw == null ? (int) (event.getForce() * maxLoad) : (int) (tick - draw.start);
        float power = ForlornItemRules.power(ticks, maxLoad);
        if (!ForlornItemRules.firesAt(power)) {
            return;
        }
        ItemStack ammo = event.getConsumable() == null ? new ItemStack(Material.ARROW) : event.getConsumable().clone();
        ammo.setAmount(1);
        boolean precise = enchantments.level(bow, ModEnchantment.PRECISE_VOLLEY) > 0;
        boolean respite = respite(player, bow);
        int perfectLevel = enchantments.level(bow, ModEnchantment.TWILIGHT_PERFECTION);
        boolean perfect = ForlornItemRules.perfectShot(perfectLevel, ticks, maxLoad);
        volley(player, ammo, power, precise, perfect);
        spend(player, bow, event.getHand(), ammo, respite);
    }

    private boolean respite(Player player, ItemStack bow) {
        return enchantments.level(bow, ModEnchantment.SHADED_RESPITE) > 0 && !DarknessIncarnate.isInLight(player, RESPITE_LIGHT);
    }

    /** Mod {@code isConvertibleArrow}: only a plain arrow (not tipped, spectral or one of the module's) turns dark. */
    private boolean convertible(ItemStack ammo) {
        return ammo.getType() == Material.ARROW && mobs.itemId(ammo).isEmpty();
    }

    private void volley(Player player, ItemStack ammo, float power, boolean precise, boolean perfect) {
        World world = player.getWorld();
        RandomGenerator random = ThreadLocalRandom.current();
        world.playSound(player.getLocation(), "alexscaves:dreadbow_release", SoundCategory.PLAYERS, 1.0F, 1.0F);
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection();
        Vector target = target(player, eye, look, power);
        Location sky = skyOf(world, target);
        boolean dark = convertible(ammo);
        int count = ForlornItemRules.volleySize(dark, power);
        for (int i = 0; i < count; i++) {
            Location from = spawnPoint(world, sky, random);
            if (!loaded(world, from.getX(), from.getZ())) {
                continue;
            }
            Vector toTarget = target.clone().subtract(from.toVector());
            float spread = ForlornItemRules.volleySpread(precise, dark, random.nextFloat(), random.nextFloat(), random.nextFloat());
            double speed = 0.5 + 1.5 * random.nextFloat();
            Vector velocity = aim(toTarget, speed, spread, random);
            if (dark) {
                darkArrow(player, from, velocity, ForlornItemRules.shadowArrowDamage(precise), perfect);
            } else {
                AbstractArrow arrow = vanillaArrow(player, from, velocity, ammo, i == 0);
                if (perfect) {
                    arrow.setDamage(arrow.getDamage() * 2.0);
                }
            }
        }
        if (dark) {
            world.playSound(target.toLocation(world), "alexscaves:dreadbow_rain", SoundCategory.PLAYERS, 12.0F, 1.0F);
        }
    }

    /** Mod: where the player's sight ends, within 128 blocks scaled by the power, else within 42. */
    private Vector target(Player player, Location eye, Vector look, float power) {
        World world = eye.getWorld();
        double reach = ForlornItemRules.volleyReach(power, false);
        RayTraceResult hit = world.rayTrace(eye, look, reach, FluidCollisionMode.NEVER, true, 0.3,
            entity -> entity != player && entity instanceof LivingEntity && !(entity instanceof Player other && other.getGameMode() == GameMode.SPECTATOR));
        if (hit != null) {
            return hit.getHitPosition();
        }
        return eye.toVector().add(look.clone().multiply(ForlornItemRules.volleyReach(power, true)));
    }

    /** Mod: 1.5 above the target, then up through the air for at most 15 blocks. */
    private Location skyOf(World world, Vector target) {
        if (!loaded(world, target.getX(), target.getZ())) {
            return target.toLocation(world);
        }
        Block block = world.getBlockAt((int) Math.floor(target.getX()), (int) Math.floor(target.getY() + 1.5), (int) Math.floor(target.getZ()));
        for (int k = 0; block.getY() < world.getMaxHeight() && block.getType().isAir() && k < 15; k++) {
            block = block.getRelative(0, 1, 0);
        }
        return block.getLocation().add(0.5, 0.5, 0.5);
    }

    /** Reading a block in a chunk nobody has loaded would load it on the main thread. */
    private static boolean loaded(World world, double x, double z) {
        return world.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }

    /** Mod: within a box of 16 by 4 by 16 around the sky point, clear of blocks (six tries, else the point itself). */
    private Location spawnPoint(World world, Location sky, RandomGenerator random) {
        Location at = null;
        for (int tries = 0; tries <= 6; tries++) {
            at = sky.clone().add(random.nextFloat() * 16.0F - 8.0F, random.nextFloat() * 4.0F - 2.0F, random.nextFloat() * 16.0F - 8.0F);
            if (!loaded(world, at.getX(), at.getZ())) {
                continue;
            }
            Block block = at.getBlock();
            if (block.getType().isAir() || block.isLiquid()) {
                return at;
            }
        }
        return sky.clone();
    }

    /** Vanilla {@code Projectile.shoot}: the direction plus a triangular scatter of 0.0172275 per degree, times the speed. */
    static Vector aim(Vector direction, double speed, double spread, RandomGenerator random) {
        Vector unit = direction.lengthSquared() < 1.0e-8 ? new Vector(0, -1, 0) : direction.clone().normalize();
        double scatter = 0.0172275 * spread;
        unit.add(new Vector((random.nextDouble() - random.nextDouble()) * scatter, (random.nextDouble() - random.nextDouble()) * scatter,
            (random.nextDouble() - random.nextDouble()) * scatter));
        return unit.multiply(speed);
    }

    private Arrow darkArrow(Player shooter, Location at, Vector velocity, float damage, boolean perfect) {
        Arrow arrow = at.getWorld().spawn(at, Arrow.class, spawned -> {
            spawned.setShooter(shooter);
            // Never saved: a volley that outlives a restart or an unload is gone, as it fades in the mod.
            spawned.setPersistent(false);
            spawned.setColor(DARK_ARROW_COLOR);
            spawned.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            spawned.setVelocity(velocity);
            spawned.getPersistentDataContainer().set(damageKey, PersistentDataType.FLOAT, damage);
            spawned.getPersistentDataContainer().set(perfectKey, PersistentDataType.BYTE, (byte) (perfect ? 1 : 0));
        });
        darkArrows.put(arrow.getUniqueId(), 0);
        return arrow;
    }

    /** The other arrows of the volley: the ammo's own kind. Only the first can be picked up again (mod: others in creative). */
    private AbstractArrow vanillaArrow(Player shooter, Location at, Vector velocity, ItemStack ammo, boolean pickable) {
        AbstractArrow.PickupStatus status = pickable ? AbstractArrow.PickupStatus.ALLOWED : AbstractArrow.PickupStatus.CREATIVE_ONLY;
        if (ammo.getType() == Material.SPECTRAL_ARROW) {
            return at.getWorld().spawn(at, SpectralArrow.class, spawned -> {
                spawned.setShooter(shooter);
                spawned.setPickupStatus(status);
                spawned.setVelocity(velocity);
            });
        }
        return at.getWorld().spawn(at, Arrow.class, spawned -> {
            spawned.setShooter(shooter);
            spawned.setPickupStatus(status);
            spawned.setItemStack(ammo);
            if (ammo.getItemMeta() instanceof PotionMeta potion) {
                if (potion.getBasePotionType() != null) {
                    spawned.setBasePotionType(potion.getBasePotionType());
                }
                for (PotionEffect effect : potion.getCustomEffects()) {
                    spawned.addCustomEffect(effect, true);
                }
                if (potion.hasColor()) {
                    spawned.setColor(potion.getColor());
                }
            }
            spawned.setVelocity(velocity);
        });
    }

    /** Mod: one point of durability (none under Shaded Respite) and one arrow (not a plain one under Shaded Respite). */
    private void spend(Player player, ItemStack bow, EquipmentSlot hand, ItemStack ammo, boolean respite) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (!respite) {
            bow.damage(1, player);
        }
        if (!respite || ammo.getType() != Material.ARROW) {
            consume(player, ammo);
        }
    }

    private void consume(Player player, ItemStack ammo) {
        PlayerInventory inventory = player.getInventory();
        ItemStack off = inventory.getItemInOffHand();
        if (off.isSimilar(ammo)) {
            off.setAmount(off.getAmount() - 1);
            return;
        }
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.isSimilar(ammo)) {
                stack.setAmount(stack.getAmount() - 1);
                return;
            }
        }
    }

    // --- Relentless Darkness -------------------------------------------------------------------------------

    /** Mod {@code onUseTick}: every third tick of use, a burst of one or two dark arrows straight ahead, fast and loose. */
    private void relentlessBurst(Player player, ItemStack bow, EquipmentSlot hand) {
        ItemStack ammo = firstAmmo(player);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        boolean respite = respite(player, bow);
        if (ammo == null && !(creative || respite)) {
            return;
        }
        if (ammo == null) {
            ammo = new ItemStack(Material.ARROW);
        }
        player.getWorld().playSound(player.getLocation(), "alexscaves:dreadbow_release", SoundCategory.PLAYERS, 1.0F, 1.0F);
        RandomGenerator random = ThreadLocalRandom.current();
        boolean dark = convertible(ammo);
        int count = ForlornItemRules.relentlessBurstSize(dark, random.nextInt(2));
        Vector look = player.getEyeLocation().getDirection();
        float spread = 0.5F;
        for (int i = 0; i < count; i++) {
            Location at = player.getEyeLocation().add(random.nextFloat() - 0.5, random.nextFloat() - 0.5 - 0.1, random.nextFloat() - 0.5);
            Vector velocity = aim(look, 4.0 + 3.0 * random.nextFloat(), spread, random);
            if (dark) {
                darkArrow(player, at, velocity, 2.0F, false);
            } else {
                vanillaArrow(player, at, velocity, ammo, i == 0);
            }
            spread += 2.0F;
        }
        spend(player, bow, hand, ammo, respite);
    }

    private ItemStack firstAmmo(Player player) {
        PlayerInventory inventory = player.getInventory();
        if (isAmmo(inventory.getItemInOffHand())) {
            return inventory.getItemInOffHand().asOne();
        }
        for (ItemStack stack : inventory.getStorageContents()) {
            if (isAmmo(stack)) {
                return stack.asOne();
            }
        }
        return null;
    }

    private boolean isAmmo(ItemStack stack) {
        return stack != null && (stack.getType() == Material.ARROW || stack.getType() == Material.SPECTRAL_ARROW
            || stack.getType() == Material.TIPPED_ARROW);
    }

    // --- dark arrow damage ---------------------------------------------------------------------------------

    /**
     * Mod {@code DarkArrowEntity.onHitEntity}: a fixed hit of dark-arrow damage (not scaled by speed, no knockback),
     * once, to anything but the archer and what the archer owns; then the arrow fades.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow) || !arrow.getPersistentDataContainer().has(damageKey, PersistentDataType.FLOAT)) {
            return;
        }
        Entity target = event.getEntity();
        if (!(target instanceof Damageable)) {
            // Boats, minecarts and the like: the arrow hurts them the way any arrow does.
            darkArrows.remove(arrow.getUniqueId());
            return;
        }
        event.setCancelled(true);
        Object shooter = arrow.getShooter();
        if (shooter instanceof Entity owner && (owner.equals(target) || target instanceof Tameable tame && owner.equals(tame.getOwner()))) {
            return;
        }
        float damage = arrow.getPersistentDataContainer().get(damageKey, PersistentDataType.FLOAT);
        boolean perfect = arrow.getPersistentDataContainer().getOrDefault(perfectKey, PersistentDataType.BYTE, (byte) 0) == 1;
        if (target instanceof Damageable living) {
            var source = shooter instanceof Entity owner
                ? mobs.damageTypes().source("dark_arrow", owner) : mobs.damageTypes().source("dark_arrow");
            living.damage(ForlornItemRules.darkArrowHit(damage, perfect), source);
        }
        darkArrows.remove(arrow.getUniqueId());
        arrow.remove();
    }

    @Override
    public void close() {
        draws.clear();
        for (UUID id : darkArrows.keySet()) {
            Entity arrow = mobs.plugin().getServer().getEntity(id);
            if (arrow != null) {
                arrow.remove();
            }
        }
        darkArrows.clear();
    }

    private record Draw(long start, EquipmentSlot hand) {
    }
}
