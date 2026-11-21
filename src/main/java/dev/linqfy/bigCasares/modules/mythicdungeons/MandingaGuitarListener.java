package dev.linqfy.bigCasares.modules.mythicdungeons;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * The Mandinga guitar in use, anywhere (not only in the dungeon): right click (use item, which Bedrock sends through
 * Geyser like Java) fires a musical note that flies straight, leaves note particles and hurts the first living
 * thing it touches for {@link MandingaGuitar#NOTE_DAMAGE}; one note per second (item cooldown plus a server check);
 * every shot plays a different guitar note. The note is scripted on the server (no vanilla projectile), so Bedrock
 * sees the same particles and damage. The guitar never breaks (unbreakable, item damage cancelled) and never loads
 * or fires as a crossbow. On join and respawn the heart of the deal is put back if a server version dropped it.
 */
public final class MandingaGuitarListener implements Listener {

    /** Blocks per tick of the note. */
    static final double SPEED = 1.5;
    static final double RANGE = 36.0;
    private static final double STEP = 0.5;
    private static final double HIT_MARGIN = 0.3;

    private final JavaPlugin plugin;
    private final CustomItemRegistry items;
    private final MythicScheduler scheduler;
    private final MandingaGuitarCooldown cooldown = new MandingaGuitarCooldown();
    private final RandomGenerator random = RandomGenerator.of("L64X128MixRandom");
    private long shots;

    public MandingaGuitarListener(JavaPlugin plugin, CustomItemRegistry items, MythicScheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.items = Objects.requireNonNull(items, "items");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** A new (unbreakable) guitar from the catalog, or empty when the catalog does not have it. */
    public Optional<ItemStack> createGuitar() {
        try {
            return Optional.of(MandingaGuitar.unbreakable(items.createItemStack(MandingaGuitar.ITEM_ID, 1)));
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    private boolean guitar(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return items.resolveItemId(stack).map(MandingaGuitar.ITEM_ID::equals).orElse(false)
            || MandingaGuitar.isGuitar(stack);
    }

    // ----------------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!guitar(item) || MythicInteractions.deniedByOthers(event)) {
            return; // not ours, or another plugin already denied using it (and with it the crossbow use)
        }
        // never charge or fire as a crossbow, never use the clicked block
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        Player player = event.getPlayer();
        if (event.getHand() == EquipmentSlot.OFF_HAND && guitar(player.getInventory().getItemInMainHand())) {
            return; // the main hand already played
        }
        if (player.getGameMode() == GameMode.SPECTATOR || player.hasCooldown(item)) {
            return;
        }
        if (!cooldown.tryFire(player.getUniqueId(), plugin.getServer().getCurrentTick())) {
            return;
        }
        MandingaGuitar.unbreakable(item);
        player.setCooldown(item, MandingaGuitarCooldown.TICKS);
        fire(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (guitar(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (guitar(event.getBow())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        MandingaGuitar.restoreHeart(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        scheduler.next("mandinga-heart-" + player.getUniqueId(), () -> {
            if (player.isOnline()) {
                MandingaGuitar.restoreHeart(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldown.forget(event.getPlayer().getUniqueId());
    }

    // ----------------------------------------------------------------------- the note

    private void fire(Player player) {
        UUID id = player.getUniqueId();
        int degree = MandingaGuitarScale.next(cooldown.lastNote(id), random);
        cooldown.note(id, degree);
        Location eye = player.getEyeLocation();
        World world = eye.getWorld();
        world.playSound(eye, Sound.BLOCK_NOTE_BLOCK_GUITAR, SoundCategory.PLAYERS, 1.4f,
            MandingaGuitarScale.pitch(degree));
        world.playSound(eye, Sound.BLOCK_NOTE_BLOCK_GUITAR, SoundCategory.PLAYERS, 0.6f,
            MandingaGuitarScale.pitch(degree + 2));
        Vector direction = eye.getDirection().normalize();
        Location at = eye.clone().add(direction.clone().multiply(0.6));
        double colour = degree / (double) MandingaGuitarScale.size();
        BukkitTask[] task = new BukkitTask[1];
        double[] travelled = {0.0};
        task[0] = scheduler.repeating("mandinga-note-" + (shots++), 0L, 1L, () -> {
            if (!player.isOnline() || !step(player, at, direction, colour, travelled)) {
                task[0].cancel();
            }
        });
    }

    /** Moves the note one tick; {@code false} once it hit something or ran out of range. */
    private boolean step(Player shooter, Location at, Vector direction, double colour, double[] travelled) {
        World world = at.getWorld();
        for (double moved = 0.0; moved < SPEED; moved += STEP) {
            at.add(direction.getX() * STEP, direction.getY() * STEP, direction.getZ() * STEP);
            travelled[0] += STEP;
            if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
                return false;
            }
            Block block = world.getBlockAt(at);
            if (!block.isPassable() && block.getBoundingBox().contains(at.toVector())) {
                burst(at, colour);
                return false;
            }
            LivingEntity target = target(shooter, at);
            if (target != null) {
                MandingaGuitar.strike(() -> target.damage(MandingaGuitar.NOTE_DAMAGE, shooter));
                burst(at, colour);
                world.playSound(at, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 1.0f, 0.6f);
                return false;
            }
            if (travelled[0] >= RANGE) {
                return false;
            }
        }
        world.spawnParticle(Particle.NOTE, at, 0, colour, 0.0, 0.0, 1.0);
        world.spawnParticle(Particle.ENCHANTED_HIT, at, 1, 0.05, 0.05, 0.05, 0.0);
        return true;
    }

    private static LivingEntity target(Player shooter, Location at) {
        BoundingBox probe = BoundingBox.of(at, HIT_MARGIN, HIT_MARGIN, HIT_MARGIN);
        for (Entity entity : at.getWorld().getNearbyEntities(probe)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(shooter) || living.isDead()
                || !living.isValid()) {
                continue;
            }
            if (entity instanceof ArmorStand stand && stand.isMarker()) {
                continue;
            }
            if (entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (living.getBoundingBox().expand(HIT_MARGIN).contains(at.toVector())) {
                return living;
            }
        }
        return null;
    }

    private static void burst(Location at, double colour) {
        World world = at.getWorld();
        for (int index = 0; index < 6; index++) {
            world.spawnParticle(Particle.NOTE, at.clone().add((index % 3 - 1) * 0.3, index / 3 * 0.3, 0.0), 0,
                (colour + index / 12.0) % 1.0, 0.0, 0.0, 1.0);
        }
        world.spawnParticle(Particle.CRIT, at, 8, 0.2, 0.2, 0.2, 0.15);
    }
}
