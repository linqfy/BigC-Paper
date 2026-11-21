package dev.linqfy.bigCasares.modules.alexscaves.core.effect;

import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Darkness Incarnate (mod {@code DarknessIncarnateEffect}): while it lasts the wearer flies at four times the normal
 * speed and loses it the moment the light around them reaches the threshold (a block light of 11, or open sky in the
 * day). Ending it drops a player back with Slow Falling for 80 ticks.
 */
public final class DarknessIncarnate {
    /** Mod: {@code isInLight(entity, 11)} everywhere it matters (the cloak, the bow and the effect itself). */
    public static final int LIGHT_THRESHOLD = 11;
    /** Mod {@code toggleFlight}: four times the default speed, on the Bukkit scale (default 0.1, twice the raw abilities value). */
    static final float FLIGHT_SPEED = 0.1F * 4.0F;
    /** What the player had before the effect touched it: {@code allowFlight;flying;flySpeed}. */
    private static final NamespacedKey PREVIOUS = new NamespacedKey("bigcasares", "ac_previous_flight");
    static final int SLOW_FALL_TICKS = 80;

    private DarknessIncarnate() {
    }

    /** Mod {@code isInLight}: block light at the entity's vehicle root, or 15 under open sky between 0.74 and 0.259. */
    public static boolean isInLight(LivingEntity living, int threshold) {
        Entity root = living.getVehicle() == null ? living : rootOf(living);
        var block = root.getLocation().getBlock();
        // Vanilla canSeeSky: above the world surface heightmap of the column.
        boolean sky = block.getY() > root.getWorld().getHighestBlockYAt(block.getX(), block.getZ(), HeightMap.WORLD_SURFACE);
        return lit(block.getLightFromBlocks(), sky, root.getWorld().getTime(), threshold);
    }

    private static Entity rootOf(Entity entity) {
        Entity root = entity;
        while (root.getVehicle() != null) {
            root = root.getVehicle();
        }
        return root;
    }

    /** Pure core of {@link #isInLight}. */
    static boolean lit(int blockLight, boolean seesSky, long dayTime, int threshold) {
        int light = blockLight;
        if (seesSky) {
            double angle = celestialAngle(dayTime);
            if (angle < 0.259 || angle > 0.74) {
                light = 15;
            }
        }
        return light >= threshold;
    }

    /** Vanilla {@code DimensionType.timeOfDay}: 0 at noon, 0.5 at midnight, smoothed around dawn and dusk. */
    static double celestialAngle(long dayTime) {
        double fraction = (dayTime % 24000L) / 24000.0 - 0.25;
        if (fraction < 0) {
            fraction += 1.0;
        }
        double smoothed = fraction + (0.5 - Math.cos(fraction * Math.PI) / 2.0 - fraction) / 3.0;
        return smoothed;
    }

    /**
     * Mod {@code toggleFlight}; {@code slowFall} adds the landing cushion that follows the end of the effect. The flight
     * the player had before (permission to fly, flying, speed) is stored when the effect first takes hold and put back
     * exactly when it ends; creative and spectator players are never touched.
     */
    public static void toggleFlight(LivingEntity living, boolean flight, boolean slowFall) {
        living.setFallDistance(0.0F);
        if (!(living instanceof Player player)) {
            return;
        }
        boolean creativeLike = player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
        var data = player.getPersistentDataContainer();
        if (flight) {
            if (creativeLike) {
                return;
            }
            if (!data.has(PREVIOUS, PersistentDataType.STRING)) {
                data.set(PREVIOUS, PersistentDataType.STRING, player.getAllowFlight() + ";" + player.isFlying() + ";" + player.getFlySpeed());
            }
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
            if (!player.isFlying()) {
                player.setFlying(true);
            }
            if (player.getFlySpeed() != FLIGHT_SPEED) {
                player.setFlySpeed(FLIGHT_SPEED);
            }
            return;
        }
        restoreFlight(player);
        if (slowFall && player.getGameMode() != GameMode.SPECTATOR) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false, false));
        }
    }

    /** Puts back the stored flight state, if the effect ever changed it; also run on join after a crash. */
    public static void restoreFlight(Player player) {
        var data = player.getPersistentDataContainer();
        String stored = data.get(PREVIOUS, PersistentDataType.STRING);
        if (stored == null) {
            return;
        }
        data.remove(PREVIOUS);
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        String[] parts = stored.split(";");
        try {
            boolean allow = Boolean.parseBoolean(parts[0]);
            boolean flying = Boolean.parseBoolean(parts[1]);
            float speed = Float.parseFloat(parts[2]);
            player.setFlySpeed(speed);
            player.setAllowFlight(allow);
            player.setFlying(allow && flying);
        } catch (RuntimeException invalid) {
            player.setFlySpeed(0.1F);
            player.setAllowFlight(false);
        }
    }

    /** True when a stored flight state is waiting to be put back. */
    public static boolean hasStoredFlight(Player player) {
        return player.getPersistentDataContainer().has(PREVIOUS, PersistentDataType.STRING);
    }
}
