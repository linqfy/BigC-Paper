package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobAnimator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-mob driver of the model animations of the Primordial mobs: it keeps exactly one base loop playing (switching it
 * only when the state changes), plays the mod's actions on top, and tracks the walking speed and the jukebox dance the
 * mod's {@code DinosaurEntity} has.
 */
final class PrimordialAnimator {
    /** Citadel {@code IDancesToJukebox}: a jukebox starts the dance within 3.46 blocks and keeps it within 15. */
    static final double DANCE_START = 3.46;
    static final double DANCE_KEEP = 15.0;
    private static final int DANCE_PERIOD = 20;
    private static final int SCAN_LIMIT = 5;

    private final MobAnimator driver = new MobAnimator();
    private boolean closed;
    private double lastX;
    private double lastZ;
    private boolean hasLast;
    private double speed;
    private boolean dancing;
    private Location jukebox;
    private int danceClock = ThreadLocalRandom.current().nextInt(DANCE_PERIOD);

    /** Updates the horizontal speed (blocks per tick) from the position change. */
    void track(LivingEntity entity, int ticks) {
        Location at = entity.getLocation();
        if (hasLast) {
            speed = Math.hypot(at.getX() - lastX, at.getZ() - lastZ) / Math.max(1, ticks);
        }
        lastX = at.getX();
        lastZ = at.getZ();
        hasLast = true;
    }

    double speed() {
        return speed;
    }

    boolean moving() {
        return speed > PrimordialPose.WALK_SPEED;
    }

    boolean still() {
        return speed < PrimordialPose.STILL_SPEED;
    }

    /** Starts {@code name} as the base loop unless it already is (deferred while an action runs). */
    void base(ModMobContext context, LivingEntity entity, String name) {
        if (!closed) {
            driver.base(entity, context, name);
        }
    }

    /** Plays an action once on top of the base loop. */
    boolean action(ModMobContext context, LivingEntity entity, String name) {
        if (closed || context.model(entity.getUniqueId()).isEmpty()) {
            return false;
        }
        driver.action(entity, context, name);
        return true;
    }

    /** After this the animator no longer touches the model (the mob died or unloaded). */
    void close() {
        closed = true;
    }

    /**
     * Jukebox dance (mod {@code DinosaurEntity}): starts next to a playing jukebox, goes on while it is within 15 blocks
     * and still plays. Checked once a second.
     */
    boolean dancing(LivingEntity entity, int ticks) {
        danceClock += ticks;
        if (danceClock < DANCE_PERIOD) {
            return dancing;
        }
        danceClock = 0;
        Location at = entity.getLocation();
        if (jukebox != null) {
            if (!stillPlays(at)) {
                jukebox = null;
            }
        }
        if (jukebox == null) {
            jukebox = findJukebox(entity);
        }
        dancing = jukebox != null;
        return dancing;
    }

    private boolean stillPlays(Location at) {
        if (!jukebox.getWorld().equals(at.getWorld()) || jukebox.distanceSquared(at) > DANCE_KEEP * DANCE_KEEP) {
            return false;
        }
        return playing(jukebox.getBlock());
    }

    private static boolean playing(Block block) {
        return block.getType() == Material.JUKEBOX && block.getState() instanceof Jukebox state && state.isPlaying();
    }

    private static Location findJukebox(LivingEntity entity) {
        World world = entity.getWorld();
        Location at = entity.getLocation();
        double reach = DANCE_START + entity.getWidth() / 2.0;
        int radius = (int) Math.min(SCAN_LIMIT, Math.ceil(reach));
        int cx = at.getBlockX();
        int cy = at.getBlockY();
        int cz = at.getBlockZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!world.isChunkLoaded((cx + dx) >> 4, (cz + dz) >> 4)) {
                    continue;
                }
                for (int dy = -3; dy <= 3; dy++) {
                    int y = cy + dy;
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                        continue;
                    }
                    Block block = world.getBlockAt(cx + dx, y, cz + dz);
                    if (block.getType() == Material.JUKEBOX && block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(at) <= reach * reach
                        && playing(block)) {
                        return block.getLocation().add(0.5, 0.5, 0.5);
                    }
                }
            }
        }
        return null;
    }
}
