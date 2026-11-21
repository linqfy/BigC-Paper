package dev.linqfy.bigCasares.modules.caichet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Puertas abiertas con la llave: en el marco aparece un cuadrado negro y arriba un cartel con la cuenta regresiva
 * (20 segundos). Quien se mete en el cuadrado aparece en el Sueño. Al terminar el tiempo todo se cierra.
 * Corre una vez por tick.
 */
final class KeyDoors implements Runnable {

    static final int SECONDS = 20;
    private static final Particle.DustOptions BLACK =
            new Particle.DustOptions(Color.fromRGB(1, 1, 1), 3f);

    private static final class Portal {
        final Block bottom;
        final BlockDisplay hole;
        final TextDisplay label;
        int ticksLeft = SECONDS * 20;

        Portal(Block bottom, BlockDisplay hole, TextDisplay label) {
            this.bottom = bottom;
            this.hole = hole;
            this.label = label;
        }
    }

    private final Logger log;
    private final List<Portal> portals = new ArrayList<>();

    KeyDoors(Logger log) {
        this.log = log;
    }

    /** @return false si esa puerta ya tiene un portal abierto (la llave no se gasta) */
    boolean open(Block clicked) {
        Block bottom = clicked.getBlockData() instanceof Door door && door.getHalf() == Bisected.Half.TOP
                ? clicked.getRelative(BlockFace.DOWN) : clicked;
        for (Portal portal : portals) {
            if (portal.bottom.equals(bottom)) {
                return false;
            }
        }
        if (!(bottom.getBlockData() instanceof Door door)) {
            return false;
        }
        setOpen(bottom, true);
        World world = bottom.getWorld();
        // cuadrado negro en el medio del marco (2 de alto), sin luz: negro puro
        BlockDisplay hole = world.spawn(bottom.getLocation(), BlockDisplay.class, d -> {
            d.setBlock(Material.BLACK_CONCRETE.createBlockData());
            d.setTransformation(DoorHole.panel(door.getFacing())); // justo donde iria la puerta cerrada
            d.setBrightness(new Display.Brightness(0, 0));
            d.setPersistent(false);
        });
        TextDisplay label = world.spawn(bottom.getLocation().add(0.5, 2.4, 0.5), TextDisplay.class, d -> {
            d.text(text(SECONDS));
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(false);
            d.setPersistent(false);
        });
        portals.add(new Portal(bottom, hole, label));
        world.playSound(bottom.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.5f);
        CaiChetSounds.play(world, bottom.getLocation(), CaiChetSounds.DOOR_KEY, SoundCategory.BLOCKS, 1.5f, 1f);
        return true;
    }

    @Override
    public void run() {
        for (Iterator<Portal> it = portals.iterator(); it.hasNext(); ) {
            Portal portal = it.next();
            portal.ticksLeft--;
            if (portal.ticksLeft <= 0 || !portal.bottom.getWorld().isChunkLoaded(
                    portal.bottom.getX() >> 4, portal.bottom.getZ() >> 4)) {
                close(portal);
                it.remove();
                continue;
            }
            // Bedrock no ve el cuadrado negro ni el cartel: particulas negras en el hueco y la cuenta en la barra de accion
            boolean second = portal.ticksLeft % 20 == 0;
            for (Player viewer : Bedrock.in(portal.bottom.getWorld())) {
                Location center = portal.bottom.getLocation().add(0.5, 1.0, 0.5);
                if (viewer.getLocation().distanceSquared(center) > 15 * 15) {
                    continue;
                }
                if (portal.ticksLeft % 2 == 0) {
                    viewer.spawnParticle(Particle.DUST, center, 14, 0.4, 0.9, 0.4, 0, BLACK);
                }
                if (second) {
                    viewer.sendActionBar(text(portal.ticksLeft / 20));
                }
            }
            if (portal.ticksLeft % 20 == 0) {
                int seconds = portal.ticksLeft / 20;
                portal.label.text(text(seconds));
                if (seconds <= 5) {
                    portal.bottom.getWorld().playSound(portal.bottom.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 0.8f);
                }
            }
            // quien se mete en el cuadrado negro cae en el Sueño
            BoundingBox box = BoundingBox.of(portal.bottom, portal.bottom.getRelative(BlockFace.UP));
            for (Player player : portal.bottom.getWorld().getPlayers()) {
                if (box.overlaps(player.getBoundingBox())) {
                    toDream(player);
                }
            }
        }
    }

    void closeAll() {
        portals.forEach(this::close);
        portals.clear();
    }

    private void close(Portal portal) {
        portal.hole.remove();
        portal.label.remove();
        if (portal.bottom.getBlockData() instanceof Door) {
            setOpen(portal.bottom, false);
            portal.bottom.getWorld().playSound(portal.bottom.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.4f, 0.6f);
            CaiChetSounds.play(portal.bottom.getWorld(), portal.bottom.getLocation(), CaiChetSounds.DOOR_CREAK,
                    SoundCategory.BLOCKS, 1.5f, 1f);
        }
    }

    private void toDream(Player player) {
        World dream = DreamWorld.get();
        Location target = dream != null ? DreamWorld.spawn(dream) : Bukkit.getWorlds().getFirst().getSpawnLocation();
        player.teleport(target);
        player.playSound(target, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.5f);
        log.info("Puerta: " + player.getName() + " entro al Sueño");
    }

    private static void setOpen(Block bottom, boolean open) {
        for (Block half : new Block[]{bottom, bottom.getRelative(BlockFace.UP)}) {
            if (half.getBlockData() instanceof Door door) {
                door.setOpen(open);
                half.setBlockData(door, false);
            }
        }
    }

    private static Component text(int seconds) {
        return Component.text("Quedan " + seconds + (seconds == 1 ? " segundo" : " segundos"), NamedTextColor.RED);
    }
}
