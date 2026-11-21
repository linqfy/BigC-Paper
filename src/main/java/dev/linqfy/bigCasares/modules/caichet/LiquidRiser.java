package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Un liquido puesto en la dimension no cae ni se esparce abajo: sube hasta 5 bloques (mientras haya aire) y
 * arriba se esparce a lo ancho, en el aire. Como el flujo normal esta cancelado en esta dimension
 * (ver {@link LiquidListener}), la capa queda quieta.
 */
final class LiquidRiser implements Runnable {

    static final int MAX_RISE = 5;
    /** Capas de agua moviendose a la vez en todo el servidor. */
    static final int MAX_ACTIVE = 24;
    private static int active;
    private boolean done;
    private BukkitTask task;
    private static final int PERIOD_TICKS = 2;

    private final Material fluid;
    private final int dropoff;
    private Block current;
    private int rises;
    private boolean spreading;
    private int ring;
    private final Set<Block> visited = new HashSet<>();
    private List<Block> frontier = new ArrayList<>();

    private LiquidRiser(Block origin, Material fluid) {
        this.current = origin;
        this.fluid = fluid;
        // el agua baja un nivel por bloque (7 bloques de alcance); la lava del overworld baja dos (3 bloques)
        this.dropoff = fluid == Material.LAVA ? 2 : 1;
    }

    static void start(Block origin, Material fluid) {
        origin.setBlockData(fluid.createBlockData(), false);
        active++;
        LiquidRiser riser = new LiquidRiser(origin, fluid);
        riser.task = CaiChetRuntime.tasks().repeating("liquid", riser, PERIOD_TICKS, PERIOD_TICKS);
    }

    /** Al apagar el modulo: las tareas ya se cancelaron con el scope, queda poner la cuenta en cero. */
    static void reset() {
        active = 0;
    }

    @Override
    public void run() {
        if (!spreading) {
            Block next = current.getRelative(BlockFace.UP);
            if (rises < MAX_RISE && next.getType().isAir()) {
                current.setType(Material.AIR, false);
                next.setBlockData(fluid.createBlockData(), false);
                current = next;
                rises++;
                return;
            }
            spreading = true;
            visited.add(current);
            frontier.add(current);
            return;
        }
        int level = (ring + 1) * dropoff;
        if (level > 7) {
            cancel();
            return;
        }
        List<Block> next = new ArrayList<>();
        for (Block block : frontier) {
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block neighbour = block.getRelative(face);
                if (visited.contains(neighbour) || !neighbour.getType().isAir()) {
                    continue;
                }
                visited.add(neighbour);
                Levelled data = (Levelled) fluid.createBlockData();
                data.setLevel(level);
                neighbour.setBlockData(data, false);
                next.add(neighbour);
            }
        }
        frontier = next;
        ring++;
        if (next.isEmpty()) {
            cancel();
        }
    }

    static boolean full() {
        return active >= MAX_ACTIVE;
    }

    private void cancel() {
        if (!done) {
            done = true;
            active = Math.max(0, active - 1);
        }
        if (task != null) {
            task.cancel();
        }
    }

    static Sound placeSound(Material fluid) {
        return fluid == Material.LAVA ? Sound.ITEM_BUCKET_EMPTY_LAVA : Sound.ITEM_BUCKET_EMPTY;
    }
}
