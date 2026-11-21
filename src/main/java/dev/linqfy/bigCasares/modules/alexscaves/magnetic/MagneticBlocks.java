package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockRegistry;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.NoteBlockService;
import dev.linqfy.bigCasares.modules.alexscaves.core.magnetism.MagnetForce.Polarity;
import dev.linqfy.bigCasares.modules.alexscaves.core.magnetism.MagnetIndex;
import dev.linqfy.bigCasares.modules.alexscaves.core.magnetism.MagnetismService;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Functional Magnetic Caves blocks. Tesla bulbs zap around themselves and, when
 * struck, discharge for a second and explode (mod: power 7). Powered magnets
 * pull or push like neodymium.
 */
public final class MagneticBlocks implements Listener {
    private static final int ZAP_PERIOD = 15;
    private static final int ZAP_DELAY = 5;
    private static final int BURST_TICKS = 20;
    private static final float EXPLOSION_POWER = 7.0f;
    static final int MAX_PENDING_ZAPS = 200;

    private final Plugin plugin;
    private final NoteBlockService blocks;
    private final MagnetismService magnetism;
    private final Map<BlockKey, Burst> bursting = new HashMap<>();
    private final ArrayDeque<PendingZap> pendingZaps = new ArrayDeque<>();
    private long tick;

    public MagneticBlocks(Plugin plugin, NoteBlockService blocks, MagnetismService magnetism) {
        this.plugin = plugin;
        this.blocks = blocks;
        this.magnetism = magnetism;
    }

    public void tick() {
        tick++;
        tickPendingZaps();
        tickBursts();
        if (tick % ZAP_PERIOD != 0) {
            return;
        }
        // Each bulb rolls once per period, however many players stand near it.
        Set<BlockKey> rolled = new HashSet<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            World world = player.getWorld();
            for (MagnetIndex.Magnet tesla : magnetism.teslaIndex().near(world.getUID(), player.getX(), player.getY(), player.getZ(), 24)) {
                if (!rolled.add(new BlockKey(world.getUID(), tesla.x(), tesla.y(), tesla.z()))) {
                    continue;
                }
                ThreadLocalRandom random = ThreadLocalRandom.current();
                if (random.nextDouble() < 0.4) {
                    Location from = new Location(world, tesla.x() + 0.5, tesla.y() + 0.5, tesla.z() + 0.5);
                    Location to = from.clone().add(random.nextInt(-5, 6), random.nextInt(-5, 6), random.nextInt(-5, 6));
                    zap(from, to, 1.0, null);
                }
            }
        }
    }

    private void tickBursts() {
        Iterator<Map.Entry<BlockKey, Burst>> iterator = bursting.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockKey, Burst> entry = iterator.next();
            BlockKey key = entry.getKey();
            World world = plugin.getServer().getWorld(key.world());
            // Never load a chunk just to finish a burst.
            if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                iterator.remove();
                continue;
            }
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (!isTesla(block)) {
                iterator.remove();
                continue;
            }
            Burst burst = entry.getValue();
            burst.ticksLeft--;
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            for (Entity nearby : world.getNearbyEntities(center, 5, 5, 5)) {
                if (nearby instanceof LivingEntity living) {
                    zap(center, living.getLocation().add(0, living.getHeight() / 2, 0), 1.0, burst.source);
                }
            }
            if (burst.ticksLeft <= 0) {
                iterator.remove();
                block.setType(Material.AIR, false);
                magnetism.onBlockRemoved(block);
                world.createExplosion(center, EXPLOSION_POWER, false, true, burst.source);
            }
        }
    }

    /** Draws the arc now and hurts what stands at its end {@value #ZAP_DELAY} ticks later, as in the mod. */
    private void zap(Location from, Location to, double damage, Entity source) {
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 0.01) {
            return;
        }
        step.multiply(0.4 / length);
        Location point = from.clone();
        for (double travelled = 0; travelled < length; travelled += 0.4) {
            point.add(step);
            from.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.02, 0.02, 0.02, 0);
        }
        from.getWorld().playSound(from, Sound.BLOCK_COPPER_BULB_TURN_ON, 0.5f, 1.8f);
        if (canQueueZap(pendingZaps.size())) {
            pendingZaps.add(new PendingZap(to, damage, source, ZAP_DELAY));
        }
    }

    static boolean canQueueZap(int queued) {
        return queued < MAX_PENDING_ZAPS;
    }

    /** Adventure players cannot break blocks, and a protection plugin may refuse the break the explosion stands for. */
    private static boolean mayBreak(Block block, Entity source) {
        if (!(source instanceof Player player)) {
            return true;
        }
        if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return new BlockBreakEvent(block, player).callEvent();
    }

    private void tickPendingZaps() {
        Iterator<PendingZap> iterator = pendingZaps.iterator();
        while (iterator.hasNext()) {
            PendingZap zap = iterator.next();
            if (--zap.ticksLeft > 0) {
                continue;
            }
            iterator.remove();
            World world = zap.target.getWorld();
            if (world == null || !world.isChunkLoaded(zap.target.getBlockX() >> 4, zap.target.getBlockZ() >> 4)) {
                continue;
            }
            for (Entity nearby : world.getNearbyEntities(zap.target, 1, 1, 1)) {
                if (nearby instanceof LivingEntity living && !nearby.equals(zap.source)) {
                    if (zap.source == null) {
                        living.damage(zap.damage);
                    } else {
                        living.damage(zap.damage, zap.source);
                    }
                    living.setFireTicks(0);
                }
            }
        }
    }

    private void startBurst(Block block, Entity source) {
        BlockKey key = new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        // Ask protections once per burst: every punch would otherwise raise a break that never happens.
        if (!bursting.containsKey(key) && isTesla(block) && mayBreak(block, source)) {
            bursting.put(key, new Burst(source));
        }
    }

    private boolean isTesla(Block block) {
        return blocks.placedAt(block).filter(placed -> placed.blockId().equals("tesla_bulb")).isPresent();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHit(ProjectileHitEvent event) {
        if (event.getHitBlock() != null) {
            Entity source = event.getEntity().getShooter() instanceof Entity shooter ? shooter : event.getEntity();
            if (source instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
                return;
            }
            startBurst(event.getHitBlock(), source);
        }
    }

    /** Protection plugins deny the interaction, which the checks below honour. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPunch(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null
            && event.useInteractedBlock() != Event.Result.DENY
            && event.getPlayer().getGameMode() != GameMode.SPECTATOR) {
            startBurst(event.getClickedBlock(), event.getPlayer());
        }
    }

    /** Note block physics is cancelled by the block service; this only reads the redstone signal of magnets. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK) {
            return;
        }
        blocks.placedAt(block).ifPresent(placed -> {
            Polarity polarity = polarityOfMagnet(placed);
            if (polarity != null) {
                magnetism.setPoweredMagnet(block, polarity, block.isBlockIndirectlyPowered());
            }
        });
    }

    static Polarity polarityOfMagnet(ModBlockRegistry.Placed placed) {
        return switch (placed.blockId()) {
            case "scarlet_magnet" -> Polarity.SCARLET;
            case "azure_magnet" -> Polarity.AZURE;
            default -> null;
        };
    }

    public void shutdown() {
        bursting.clear();
        pendingZaps.clear();
    }

    private record BlockKey(UUID world, int x, int y, int z) {
    }

    private static final class Burst {
        private final Entity source;
        private int ticksLeft = BURST_TICKS;

        private Burst(Entity source) {
            this.source = source;
        }
    }

    private static final class PendingZap {
        private final Location target;
        private final double damage;
        private final Entity source;
        private int ticksLeft;

        private PendingZap(Location target, double damage, Entity source, int ticksLeft) {
            this.target = target;
            this.damage = damage;
            this.source = source;
            this.ticksLeft = ticksLeft;
        }
    }
}
