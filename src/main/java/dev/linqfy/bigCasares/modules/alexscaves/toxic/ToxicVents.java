package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Geothermal vents (mod {@code GeothermalVentBlock}). A vent column smokes from its top when it stands on a
 * liquid: acid (water of the Toxic Caves, see {@link ToxicAcid}) gives the green radon smoke, water steam,
 * lava black smoke. A glass bottle used on the top of an acid vent fills with radon.
 *
 * <p>The mod draws the smoke on the client; here the server sends it to players near vents. Only players inside
 * the Toxic Caves are scanned, one player per tick, in a small box; each one is rescanned about every
 * {@link #SCAN_TICKS} ticks. Smoke is sent every {@link #SMOKE_TICKS} ticks, once per vent however many players
 * are near it.
 */
public final class ToxicVents implements Listener, Runnable {
    static final int SCAN_TICKS = 40;
    static final int SMOKE_TICKS = 4;
    private static final int SCAN_RADIUS = 10;
    private static final int SCAN_HEIGHT = 6;
    private static final int MAX_COLUMN = 8;

    /** Mod {@code SMOKE_TYPE}. */
    enum Smoke { NONE, WATER, LAVA, ACID }

    private final ModMobService mobs;
    private final Set<BlockData> vents;
    private final Map<UUID, Map<Location, Smoke>> nearby = new HashMap<>();
    private final Map<UUID, Long> scannedAt = new HashMap<>();
    private long tick;

    public ToxicVents(ModMobService mobs, Set<BlockData> vents) {
        this.mobs = mobs;
        this.vents = Set.copyOf(vents);
    }

    /** Not ignoreCancelled: module blocks deny the block use (see NoteBlockListener), which reads as cancelled. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBottle(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null
            || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(event.getHand());
        Block block = event.getClickedBlock();
        if (held.getType() != Material.GLASS_BOTTLE || mobs.itemId(held).isPresent() || !isVent(block)
            || !smokes(block) || smoke(block) != Smoke.ACID) {
            return;
        }
        event.setCancelled(true);
        mobs.createItem("ac_radon_bottle", 1).ifPresent(bottle -> {
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.setAmount(held.getAmount() - 1);
                player.getInventory().setItem(event.getHand(), held.getAmount() <= 0 ? null : held);
            }
            player.getInventory().addItem(bottle).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            player.playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1.0f, 1.0f);
        });
    }

    @Override
    public void run() {
        tick++;
        scanOne();
        if (tick % SMOKE_TICKS != 0) {
            return;
        }
        Map<Location, Smoke> tops = new HashMap<>();
        nearby.values().forEach(tops::putAll);
        tops.forEach(this::puff);
    }

    /** Rescans the player who has waited longest, if that is at least {@link #SCAN_TICKS} ticks. */
    private void scanOne() {
        Player oldest = null;
        long oldestAt = Long.MAX_VALUE;
        for (Player player : mobs.plugin().getServer().getOnlinePlayers()) {
            long at = scannedAt.getOrDefault(player.getUniqueId(), Long.MIN_VALUE);
            if (at < oldestAt) {
                oldestAt = at;
                oldest = player;
            }
        }
        nearby.keySet().removeIf(id -> mobs.plugin().getServer().getPlayer(id) == null);
        scannedAt.keySet().removeIf(id -> mobs.plugin().getServer().getPlayer(id) == null);
        if (oldest == null || tick - oldestAt < SCAN_TICKS) {
            return;
        }
        scannedAt.put(oldest.getUniqueId(), tick);
        Location location = oldest.getLocation();
        Map<Location, Smoke> found = ToxicAcid.isToxicBiome(location.getBlock().getBiome().getKey()) ? scan(location) : Map.of();
        if (found.isEmpty()) {
            nearby.remove(oldest.getUniqueId());
        } else {
            nearby.put(oldest.getUniqueId(), found);
        }
    }

    /** Smoking vent tops around a location. */
    private Map<Location, Smoke> scan(Location center) {
        Map<Location, Smoke> found = new HashMap<>();
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int x = cx - SCAN_RADIUS; x <= cx + SCAN_RADIUS; x++) {
            for (int z = cz - SCAN_RADIUS; z <= cz + SCAN_RADIUS; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = Math.max(world.getMinHeight(), cy - SCAN_HEIGHT); y <= cy + SCAN_HEIGHT; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.NOTE_BLOCK && isVent(block) && smokes(block)) {
                        Smoke smoke = smoke(block);
                        if (smoke != Smoke.NONE) {
                            found.put(block.getLocation(), smoke);
                        }
                    }
                }
            }
        }
        return found;
    }

    private void puff(Location top, Smoke smoke) {
        World world = top.getWorld();
        if (!top.isChunkLoaded()) {
            return;
        }
        Location at = top.clone().add(0.5, 1.1, 0.5);
        switch (smoke) {
            case ACID -> {
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 1, 0.1, 0.1, 0.1, 0.01);
                world.spawnParticle(Particle.DUST, at, 3, 0.2, 0.3, 0.2, 0,
                    new Particle.DustOptions(Color.fromRGB(0x7F, 0xD1, 0x1B), 1.6f));
            }
            case WATER -> world.spawnParticle(Particle.CLOUD, at, 1, 0.1, 0.1, 0.1, 0.02);
            case LAVA -> world.spawnParticle(Particle.LARGE_SMOKE, at, 1, 0.1, 0.1, 0.1, 0.01);
            case NONE -> {
            }
        }
    }

    private boolean isVent(Block block) {
        return block.getType() == Material.NOTE_BLOCK && vents.contains(block.getBlockData());
    }

    /** Mod {@code isSpawningParticles}: the top of the column, open above. */
    private static boolean smokes(Block vent) {
        Block above = vent.getRelative(0, 1, 0);
        return above.getType().isAir() || !above.getType().isSolid();
    }

    /** Mod {@code getSmokeType}: what the vent column stands on. */
    Smoke smoke(Block vent) {
        Block below = vent;
        for (int i = 0; i < MAX_COLUMN && isVent(below); i++) {
            below = below.getRelative(0, -1, 0);
        }
        return smokeOf(below.getType(), ToxicAcid.isToxicBiome(below.getBiome().getKey()));
    }

    static Smoke smokeOf(Material base, boolean toxicBiome) {
        if (base == Material.WATER) {
            return toxicBiome ? Smoke.ACID : Smoke.WATER;
        }
        return base == Material.LAVA ? Smoke.LAVA : Smoke.NONE;
    }
}
