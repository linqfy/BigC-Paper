package dev.linqfy.bigCasares.modules.tideframe.engine.core;

/**
 * Where the client-only 3D model of a fixed-camera menu stands (the Mannequin proof of concept). Its centre is on the
 * camera's line of sight, so it lands in the middle of the screen whatever the player's FOV and window shape: those
 * only change how big it looks ({@link #screenShare}). Minecraft's angles: yaw 0 looks to +Z, yaw 90 to -X, pitch 90
 * straight down. Pure: no Bukkit.
 */
public final class ModelPlacement {
    private ModelPlacement() { }
    /** Blocks from the eye to the model's centre. */
    public static final double DISTANCE = 3;
    /** A standing player model: 1.8 blocks tall, 0.6 wide. */
    public static final double HEIGHT = 1.8, HALF_WIDTH = .3;
    /** The FOV the demo's hole is drawn for (the game's default). */
    public static final double DESIGN_FOV = 70;
    /** Sampling step along the sight lines, in blocks. */
    private static final double STEP = .05;

    /** The model's feet and the yaw it faces (towards the camera). */
    public record Spot(double x, double y, double z, float yaw) { }

    /** Answers whether the block at these block coordinates stops sight (solid). */
    @FunctionalInterface public interface Solid { boolean at(int x, int y, int z); }

    /** Unit vector the camera looks along. */
    public static double[] direction(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new double[] { -Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p) };
    }

    /** The model centred {@code distance} blocks along the line of sight, facing the camera. */
    public static Spot centered(double eyeX, double eyeY, double eyeZ, float yaw, float pitch, double distance) {
        double[] d = direction(yaw, pitch);
        return new Spot(eyeX + d[0] * distance, eyeY + d[1] * distance - HEIGHT / 2, eyeZ + d[2] * distance, facing(yaw));
    }

    /** The yaw that looks back at a camera with {@code yaw}, in [-180, 180). */
    public static float facing(float yaw) { return wrap(yaw + 180.0); }

    /** {@code degrees} as a yaw in [-180, 180). */
    public static float wrap(double degrees) {
        double turned = degrees % 360.0;
        if (turned < -180) turned += 360; else if (turned >= 180) turned -= 360;
        return (float) turned;
    }

    /** Share of the screen height a model {@code height} tall fills {@code distance} blocks away with vertical {@code fov}. */
    public static double screenShare(double height, double distance, double fovDegrees) {
        return height / (2 * distance * Math.tan(Math.toRadians(fovDegrees) / 2));
    }

    /**
     * True when nothing solid hides the model: its own box is free and the camera sees its head, centre and feet. A
     * model inside or behind a wall is never shown.
     */
    public static boolean clear(double eyeX, double eyeY, double eyeZ, Spot spot, Solid solid) {
        for (int bx = floor(spot.x() - HALF_WIDTH); bx <= floor(spot.x() + HALF_WIDTH); bx++)
            for (int by = floor(spot.y()); by <= floor(spot.y() + HEIGHT - 1e-6); by++)
                for (int bz = floor(spot.z() - HALF_WIDTH); bz <= floor(spot.z() + HALF_WIDTH); bz++)
                    if (solid.at(bx, by, bz)) return false;
        for (double h : new double[] { HEIGHT - .1, HEIGHT / 2, .1 })
            if (!sees(eyeX, eyeY, eyeZ, spot.x(), spot.y() + h, spot.z(), solid)) return false;
        return true;
    }

    private static boolean sees(double ax, double ay, double az, double bx, double by, double bz, Solid solid) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(length / STEP);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            if (solid.at(floor(ax + dx * t), floor(ay + dy * t), floor(az + dz * t))) return false;
        }
        return true;
    }

    private static int floor(double value) { return (int) Math.floor(value); }
}
