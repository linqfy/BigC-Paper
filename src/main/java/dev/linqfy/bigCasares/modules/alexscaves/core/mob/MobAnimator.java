package dev.linqfy.bigCasares.modules.alexscaves.core.mob;

import org.bukkit.entity.LivingEntity;

/**
 * Per-mob animation state: keeps exactly one base loop playing (idle, walk or a state loop such as sit or fly),
 * switching it only when the choice changes, and plays one-shot actions (the mod's {@code Animation} constants) on top
 * of it. BetterModel lets the latest started animation win per bone and the action animations only move their own
 * bones, so the base loop keeps playing underneath; a base switch while an action runs would take the action's bones
 * over, so it waits until the action ends.
 */
public final class MobAnimator {
    /** Horizontal speed, in blocks per tick, above which a mob counts as walking. */
    public static final double MOVING_SPEED = 0.02;
    /** Longest an action holds back base switches if its end callback never arrives. */
    private static final int ACTION_GUARD_TICKS = 100;

    private String current = "idle";
    private String pending;
    private double lastX;
    private double lastZ;
    private boolean hasLast;
    private boolean moving;
    private int actionToken;
    private boolean actionActive;
    private int actionGuard;
    /** Entity age when the running action must be considered over even without its end callback. */
    private int actionUntil;

    /** The base loop for a state: the state loop when there is one, otherwise walk while moving and idle at rest. */
    public static String baseLoop(String state, boolean moving) {
        if (state != null) {
            return state;
        }
        return moving ? "walk" : "idle";
    }

    /** Whether a horizontal displacement over {@code ticks} ticks counts as moving. */
    public static boolean isMoving(double dx, double dz, int ticks) {
        return ticks > 0 && Math.sqrt(dx * dx + dz * dz) / ticks > MOVING_SPEED;
    }

    public boolean moving() {
        return moving;
    }

    public String current() {
        return current;
    }

    /**
     * Tracks movement and plays the base loop for {@code state} (null: walk or idle). Call once per behaviour tick.
     */
    public void update(LivingEntity entity, ModMobContext context, int ticks, String state) {
        track(entity, ticks);
        base(entity, context, baseLoop(state, moving));
    }

    /** Like {@link #update}, but the state loop only plays while the mob is moving (swim, run); at rest it idles. */
    public void updateWhenMoving(LivingEntity entity, ModMobContext context, int ticks, String state) {
        track(entity, ticks);
        base(entity, context, baseLoop(moving ? state : null, moving));
    }

    private void track(LivingEntity entity, int ticks) {
        double x = entity.getLocation().getX();
        double z = entity.getLocation().getZ();
        moving = hasLast && isMoving(x - lastX, z - lastZ, ticks);
        lastX = x;
        lastZ = z;
        hasLast = true;
        if (actionActive && ((actionGuard -= ticks) <= 0 || entity.getTicksLived() >= actionUntil)) {
            actionActive = false;
        }
    }

    /** Switches the base loop when it differs from the one playing (after the running action, if any). */
    public void base(LivingEntity entity, ModMobContext context, String name) {
        if (name.equals(current)) {
            pending = null;
            return;
        }
        if (actionActive && entity.getTicksLived() >= actionUntil) {
            // The guard also runs for callers that never call update() (a lost end callback must not freeze the loop).
            actionActive = false;
        }
        if (actionActive) {
            pending = name;
            return;
        }
        pending = null;
        context.model(entity.getUniqueId()).ifPresent(handle -> {
            if (context.models().animate(handle, name)) {
                current = name;
            }
        });
    }

    /** Plays a one-shot action animation over the base loop. */
    public void action(LivingEntity entity, ModMobContext context, String name) {
        context.model(entity.getUniqueId()).ifPresent(handle -> {
            int token = ++actionToken;
            actionActive = true;
            actionGuard = ACTION_GUARD_TICKS;
            actionUntil = entity.getTicksLived() + ACTION_GUARD_TICKS;
            boolean started = context.models().animateOnce(handle, name, () -> {
                if (token == actionToken) {
                    actionActive = false;
                    if (pending != null) {
                        base(entity, context, pending);
                    }
                }
            });
            if (!started) {
                actionActive = false;
            }
        });
    }
}
