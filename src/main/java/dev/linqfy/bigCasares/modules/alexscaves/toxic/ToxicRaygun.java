package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.core.damage.ModDamageTypes;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectType;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Raygun (mod {@code RaygunItem}): hold right click to fire a radioactive ray that reaches 25 blocks after
 * a 15-tick warm-up and, every 3 ticks, hurts (1.5) and irradiates (800 ticks) what is within a block of where it
 * lands. Every 2 ticks of full beam uses 1 of its 1000 charge (shown as the durability bar); right click when
 * it is empty reloads it with a uranium rod from the inventory. Holding right click repeats the use every
 * 4 ticks on the client, so a player counts as firing while the last use is at most {@link #HOLD_TICKS} old.
 */
public final class ToxicRaygun implements Listener, Runnable {
    static final String ITEM = "ac_raygun";
    static final String AMMO = "ac_uranium_rod";
    static final int MAX_CHARGE = 1000;
    static final int WARMUP_TICKS = 15;
    static final float MAX_DISTANCE = 25.0F;
    /** Client repeats every 4 ticks; extra room for lag before the beam counts as released. */
    static final int HOLD_TICKS = 9;
    /** Charge is written to the item this often while firing (each write resends the item). */
    static final int SAVE_TICKS = 10;

    private final ModMobService mobs;
    private final NamespacedKey chargeKey;
    private final Map<UUID, Firing> firing = new HashMap<>();
    private long tick;

    public ToxicRaygun(ModMobService mobs) {
        this.mobs = mobs;
        this.chargeKey = new NamespacedKey(mobs.plugin(), "raygun_charge_used");
    }

    /** {@code slot}: inventory slot of the raygun when the beam started, where its charge is written back. */
    private record Firing(long startedAt, long lastUse, int slot) {
    }

    /** Charge used during the current beam, not yet written to the item. */
    private final Map<UUID, Integer> pending = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.useItemInHand() == Event.Result.DENY
            || event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isRaygun(held)) {
            return;
        }
        // As with any item, clicking a chest, door or button uses the block unless sneaking.
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
            && event.getClickedBlock().getType().isInteractable() && !player.isSneaking()) {
            return;
        }
        event.setCancelled(true);
        if (chargeUsed(held) >= MAX_CHARGE) {
            firing.remove(player.getUniqueId());
            reload(player, held);
            return;
        }
        Firing current = firing.get(player.getUniqueId());
        if (current == null || tick - current.lastUse() > HOLD_TICKS) {
            firing.put(player.getUniqueId(), new Firing(tick, tick, player.getInventory().getHeldItemSlot()));
            player.getWorld().playSound(player.getLocation(), "alexscaves:raygun_start", SoundCategory.PLAYERS, 1.0f, 1.0f);
        } else {
            firing.put(player.getUniqueId(), new Firing(current.startedAt(), tick, current.slot()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
        pending.remove(event.getPlayer().getUniqueId());
    }

    /** Changing slot ends the beam; what it used so far is written to the slot it was fired from. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        stop(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (isRaygun(dropped) && firing.remove(event.getPlayer().getUniqueId()) != null) {
            ItemStack copy = dropped.clone();
            flushTo(event.getPlayer().getUniqueId(), copy);
            event.getItemDrop().setItemStack(copy);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        Firing current = firing.remove(id);
        if (current == null) {
            pending.remove(id);
            return;
        }
        if (event.getKeepInventory()) {
            flushTo(id, slotItem(event.getEntity(), current.slot()));
            return;
        }
        for (ItemStack drop : event.getDrops()) {
            if (isRaygun(drop)) {
                flushTo(id, drop);
                return;
            }
        }
        pending.remove(id);
    }

    /** Ends the beam of a player and never loses the charge it used. */
    private void stop(Player player) {
        Firing current = firing.remove(player.getUniqueId());
        flushTo(player.getUniqueId(), current == null ? null : slotItem(player, current.slot()));
    }

    private ItemStack slotItem(Player player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        return isRaygun(stack) ? stack : null;
    }

    @Override
    public void run() {
        tick++;
        // A copy: a beam hit can kill its own user, whose death handler edits the map.
        for (UUID id : java.util.List.copyOf(firing.keySet())) {
            Firing state = firing.get(id);
            if (state == null) {
                continue;
            }
            Player player = mobs.plugin().getServer().getPlayer(id);
            ItemStack held = player == null ? null : player.getInventory().getItemInMainHand();
            boolean stop = player == null || !player.isValid() || tick - state.lastUse() > HOLD_TICKS || !isRaygun(held)
                || player.getInventory().getHeldItemSlot() != state.slot()
                || !fire(player, held, (int) (tick - state.startedAt()));
            if (stop || (tick - state.startedAt()) % SAVE_TICKS == 0) {
                flushTo(id, player == null ? null : slotItem(player, state.slot()));
            }
            if (stop) {
                firing.remove(id, state);
            }
        }
    }

    /** Writes the charge used since the last flush to the raygun it was fired from. */
    private void flushTo(UUID player, ItemStack raygun) {
        if (raygun == null) {
            // The raygun left its slot unseen: keep the debt for the next one rather than give the charge back.
            return;
        }
        Integer used = pending.remove(player);
        if (used != null && used > 0) {
            setChargeUsed(raygun, withPending(chargeUsed(raygun), used));
        }
    }

    /** Charge used after adding what a beam used since the last write, never above the capacity. */
    static int withPending(int stored, int pending) {
        return Math.max(0, Math.min(MAX_CHARGE, stored + pending));
    }

    /** A full-power beam uses 1 charge every second tick, unless its user is in creative. */
    static boolean usesCharge(float warmup, int useTicks, boolean creative) {
        return warmup >= 1.0F && useTicks % 2 == 0 && !creative;
    }

    /** One tick of the beam; false when it ran out of charge. */
    private boolean fire(Player player, ItemStack raygun, int useTicks) {
        int used = chargeUsed(raygun) + pending.getOrDefault(player.getUniqueId(), 0);
        World world = player.getWorld();
        if (used >= MAX_CHARGE) {
            world.playSound(player.getLocation(), "alexscaves:raygun_empty", SoundCategory.PLAYERS, 1.0f, 1.0f);
            return false;
        }
        float time = useTicks < WARMUP_TICKS ? (float) useTicks / WARMUP_TICKS : 1.0F;
        double reach = Math.max(0.5, MAX_DISTANCE * time);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        RayTraceResult hit = world.rayTrace(eye, direction, reach, FluidCollisionMode.NEVER, true, 0.2,
            entity -> entity != player && entity instanceof LivingEntity && !(entity instanceof Player other && other.getGameMode() == GameMode.SPECTATOR));
        Vector end = hit == null ? eye.toVector().add(direction.clone().multiply(reach)) : hit.getHitPosition();
        beam(world, eye.toVector(), end);
        if (useTicks % 20 == 0) {
            world.playSound(player.getLocation(), "alexscaves:raygun_loop", SoundCategory.PLAYERS, 0.8f, 1.0f);
        }
        if (useTicks >= WARMUP_TICKS && (useTicks - WARMUP_TICKS) % 3 == 0) {
            Location at = end.toLocation(world);
            for (Entity near : world.getNearbyEntities(at, 1.0, 1.0, 1.0)) {
                if (near != player && near instanceof LivingEntity living) {
                    // Mod: only a hit that lands (outside the hurt cooldown) irradiates.
                    double before = DamageProbe.pool(living);
                    living.damage(1.5, mobs.damageTypes().source(ModDamageTypes.RAYGUN, player));
                    boolean lands = DamageProbe.landed(before, DamageProbe.pool(living), living.isDead());
                    if (lands && !NukeMath.RESISTS_RADIATION.contains(mobs.typeOf(living).map(type -> "alexscaves:" + type.id()).orElse(""))) {
                        mobs.effects().apply(living, ModEffectType.IRRADIATED, 0, 800);
                    }
                }
            }
        }
        if (usesCharge(time, useTicks, player.getGameMode() == GameMode.CREATIVE)) {
            pending.merge(player.getUniqueId(), 1, Integer::sum);
        }
        return true;
    }

    private static void beam(World world, Vector from, Vector to) {
        Vector step = to.clone().subtract(from);
        double length = step.length();
        if (length < 1.0E-3) {
            return;
        }
        step.multiply(0.5 / length);
        Vector point = from.clone().add(step.clone().multiply(2));
        Particle.DustOptions green = new Particle.DustOptions(Color.fromRGB(0x7F, 0xF0, 0x2B), 0.9f);
        for (double d = 1.0; d < length; d += 0.5) {
            world.spawnParticle(Particle.DUST, point.getX(), point.getY(), point.getZ(), 1, 0, 0, 0, 0, green);
            point.add(step);
        }
        world.spawnParticle(Particle.DUST, to.getX(), to.getY(), to.getZ(), 4, 0.15, 0.15, 0.15, 0,
            new Particle.DustOptions(Color.fromRGB(0xC8, 0xFF, 0x6A), 1.6f));
    }

    private void reload(Player player, ItemStack raygun) {
        World world = player.getWorld();
        boolean loaded = player.getGameMode() == GameMode.CREATIVE;
        if (!loaded) {
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack != null && !stack.isEmpty() && mobs.itemId(stack).filter(AMMO::equals).isPresent()) {
                    stack.setAmount(stack.getAmount() - 1);
                    loaded = true;
                    break;
                }
            }
        }
        if (loaded) {
            setChargeUsed(raygun, 0);
            world.playSound(player.getLocation(), "alexscaves:raygun_reload", SoundCategory.PLAYERS, 1.0f, 1.0f);
        } else {
            world.playSound(player.getLocation(), "alexscaves:raygun_empty", SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    private boolean isRaygun(ItemStack stack) {
        return stack != null && !stack.isEmpty() && mobs.itemId(stack).filter(ITEM::equals).isPresent();
    }

    int chargeUsed(ItemStack raygun) {
        ItemMeta meta = raygun.getItemMeta();
        Integer used = meta == null ? null : meta.getPersistentDataContainer().get(chargeKey, PersistentDataType.INTEGER);
        return used == null ? 0 : used;
    }

    private void setChargeUsed(ItemStack raygun, int used) {
        ItemMeta meta = raygun.getItemMeta();
        if (meta == null) {
            return;
        }
        int clamped = Math.max(0, Math.min(MAX_CHARGE, used));
        meta.getPersistentDataContainer().set(chargeKey, PersistentDataType.INTEGER, clamped);
        if (meta instanceof Damageable damageable && damageable.hasMaxDamage()) {
            // The bar shows the charge used; one point short of the maximum so it never reads as broken.
            damageable.setDamage(Math.min(damageable.getMaxDamage() - 1, clamped * damageable.getMaxDamage() / MAX_CHARGE));
        }
        raygun.setItemMeta(meta);
    }
}
