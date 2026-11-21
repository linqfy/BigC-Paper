package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.core.damage.ModDamageTypes;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The drum a Brainiac throws (mod {@code ThrownWasteDrumEntity}): gravity 0.08, drag 0.9, 0.7 friction on the ground,
 * 2 acid damage to anything but a Brainiac while it still moves faster than 0.03 (and it slows them), and after 20
 * ticks on the ground it leaves acid until the 35th. A block display with its own motion, one task per drum, bounded
 * by {@link #MAX_AGE}; the acid needs {@code mobGriefing} and the block change goes through {@code EntityChangeBlockEvent}.
 */
final class ThrownWasteDrum extends BukkitRunnable {
    static final int MAX_AGE = 200;
    static final int MAX_ACTIVE = 32;
    private static final int ACID_TICK = 20;
    private static final int END_TICK = 35;
    private static final Set<ThrownWasteDrum> ACTIVE = new HashSet<>();

    private final ModMobService mobs;
    private final LivingEntity thrower;
    private final BlockDisplay display;
    private final Vector position;
    private final Vector velocity;
    private int age;
    private int onGroundFor;
    private Block acid;

    private ThrownWasteDrum(ModMobService mobs, LivingEntity thrower, BlockDisplay display, Vector velocity) {
        this.mobs = mobs;
        this.thrower = thrower;
        this.display = display;
        this.position = display.getLocation().toVector();
        this.velocity = velocity;
    }

    static void launch(ModMobService mobs, LivingEntity thrower, Location at, Vector velocity, BlockData drum) {
        if (ACTIVE.size() >= MAX_ACTIVE) {
            return;
        }
        BlockDisplay display = at.getWorld().spawn(at, BlockDisplay.class, spawned -> {
            spawned.setBlock(drum);
            spawned.setPersistent(false);
            spawned.setTeleportDuration(1);
            spawned.setTransformation(new Transformation(new Vector3f(-0.5f, 0f, -0.5f), new AxisAngle4f(),
                new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
        });
        ThrownWasteDrum thrown = new ThrownWasteDrum(mobs, thrower, display, velocity);
        ACTIVE.add(thrown);
        thrown.runTaskTimer(mobs.plugin(), 1L, 1L);
    }

    static void cancelAll() {
        for (ThrownWasteDrum drum : List.copyOf(ACTIVE)) {
            drum.finish();
        }
    }

    @Override
    public void run() {
        World world = display.getWorld();
        if (!display.isValid() || ++age > MAX_AGE || !world.isChunkLoaded(position.getBlockX() >> 4, position.getBlockZ() >> 4)) {
            finish();
            return;
        }
        velocity.setY(velocity.getY() - 0.08);
        boolean grounded = move(world);
        velocity.multiply(0.9);
        if (grounded) {
            onGroundFor++;
            velocity.setX(velocity.getX() * 0.7).setZ(velocity.getZ() * 0.7);
        }
        display.teleport(position.toLocation(world));
        if (velocity.length() > 0.03) {
            boolean hit = false;
            for (Entity nearby : world.getNearbyEntities(display.getLocation().add(0, 0.5, 0), 0.5, 0.5, 0.5)) {
                if (nearby instanceof LivingEntity living && !isBrainiac(living)) {
                    hit = true;
                    living.damage(2.0, thrower.isValid() ? mobs.damageTypes().source(ModDamageTypes.ACID, thrower)
                        : mobs.damageTypes().source(ModDamageTypes.ACID));
                }
            }
            if (hit) {
                velocity.setX(velocity.getX() * 0.2).setZ(velocity.getZ() * 0.2);
            }
        }
        if (onGroundFor == ACID_TICK) {
            placeAcid(world);
        }
        if (onGroundFor >= END_TICK) {
            finish();
        }
    }

    private boolean isBrainiac(LivingEntity entity) {
        return mobs.typeOf(entity).filter(type -> type == ToxicMobs.BRAINIAC).isPresent();
    }

    /** Moves along each axis, stopping against solid blocks; true when it landed. */
    private boolean move(World world) {
        boolean landed = false;
        double[] step = {velocity.getX(), velocity.getY(), velocity.getZ()};
        for (int axis = 0; axis < 3; axis++) {
            if (step[axis] == 0) {
                continue;
            }
            Vector next = position.clone();
            switch (axis) {
                case 0 -> next.setX(next.getX() + step[0]);
                case 1 -> next.setY(next.getY() + step[1]);
                default -> next.setZ(next.getZ() + step[2]);
            }
            Block block = world.getBlockAt(next.getBlockX(), (int) Math.floor(next.getY() + 0.4), next.getBlockZ());
            if (!world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4) || !block.isPassable()) {
                if (axis == 1 && step[1] < 0) {
                    landed = true;
                }
                switch (axis) {
                    case 0 -> velocity.setX(0);
                    case 1 -> velocity.setY(0);
                    default -> velocity.setZ(0);
                }
            } else {
                position.copy(next);
            }
        }
        return landed;
    }

    /** Mod: the first block up from the drum that is air (or acid already) gets acid, until tick 35. */
    private void placeAcid(World world) {
        if (Boolean.FALSE.equals(world.getGameRuleValue(GameRules.MOB_GRIEFING))) {
            return;
        }
        Block block = world.getBlockAt(position.getBlockX(), position.getBlockY(), position.getBlockZ());
        for (int i = 0; i < 3 && !block.getType().isAir(); i++) {
            block = block.getRelative(0, 1, 0);
        }
        if (!block.getType().isAir()) {
            return;
        }
        BlockData water = org.bukkit.Bukkit.createBlockData(Material.WATER);
        if (new EntityChangeBlockEvent(thrower, block, water).callEvent()) {
            block.setBlockData(water, false);
            acid = block;
        }
    }

    private void finish() {
        if (ACTIVE.remove(this)) {
            // The acid goes even if its chunk unloaded meanwhile (loading it here keeps it from staying as real water).
            if (acid != null && acid.getWorld().getBlockAt(acid.getX(), acid.getY(), acid.getZ()).getType() == Material.WATER) {
                acid.getWorld().getBlockAt(acid.getX(), acid.getY(), acid.getZ()).setType(Material.AIR, false);
            }
            display.remove();
            cancel();
        }
    }
}
