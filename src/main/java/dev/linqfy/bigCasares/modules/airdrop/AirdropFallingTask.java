package dev.linqfy.bigCasares.modules.airdrop;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.function.Consumer;

public final class AirdropFallingTask extends BukkitRunnable {

    private static final double FALL_SPEED = 0.5;

    private final World world;
    private final Consumer<AirdropPosition> onLand;

    private double currentY;
    private final int targetX;
    private final int targetZ;
    private final int groundY;

    public AirdropFallingTask(World world, AirdropPosition position, Consumer<AirdropPosition> onLand) {
        this.world = world;
        this.targetX = position.x();
        this.targetZ = position.z();
        this.groundY = world.getHighestBlockYAt(targetX, targetZ);
        this.currentY = position.y();
        this.onLand = onLand;
    }

    @Override
    public void run() {
        currentY -= FALL_SPEED;

        Location particleLoc = new Location(world, targetX + 0.5, currentY, targetZ + 0.5);
        world.spawnParticle(Particle.CLOUD, particleLoc, 5, 0.3, 0.3, 0.3, 0.01);

        if (currentY <= groundY + 1) {
            land();
            cancel();
        }
    }

    private void land() {
        Location chestLoc = new Location(world, targetX, groundY + 1, targetZ);
        chestLoc.getBlock().setType(Material.CHEST);
        world.playSound(chestLoc, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
        onLand.accept(new AirdropPosition(targetX, groundY + 1, targetZ));
    }
}
