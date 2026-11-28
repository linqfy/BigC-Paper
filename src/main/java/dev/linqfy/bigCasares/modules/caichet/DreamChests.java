package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Los cofres de cuadros de las casas del Sueño. Cuando se genera un chunk nuevo, {@link
 * DreamLayout#chestSpotsInChunk} dice en que casas (cerca del 30 %) y en que celda del interior va el cofre, y un tick
 * despues se pone, con un cuadro al azar y a veces una botella del hotel. Todo sale de la semilla.
 */
public final class DreamChests implements Listener {

    private static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    /** Porcentaje de cofres con botellas de relleno. */
    static final int BOTTLE_PERCENT = 40;

    /** Lo que lleva un cofre: el id del cuadro y cuantas botellas de relleno. */
    record Loot(String painting, int bottles) {
    }

    static Loot loot(Random random) {
        String painting = DreamPaintings.randomId(random);
        int bottles = random.nextInt(100) < BOTTLE_PERCENT ? 1 + random.nextInt(2) : 0;
        return new Loot(painting, bottles);
    }

    @EventHandler
    public void onChunk(ChunkLoadEvent event) {
        if (!event.isNewChunk() || !DreamWorld.isDream(event.getWorld()) || !CaiChetRuntime.active()) {
            return;
        }
        Chunk chunk = event.getChunk();
        List<DreamLayout.ChestSpot> spots = DreamLayout.chestSpotsInChunk(chunk.getWorld().getSeed(), chunk.getX(),
            chunk.getZ());
        if (spots.isEmpty()) {
            return;
        }
        // el chunk recien generado se toca un tick despues, cuando ya termino de cargarse
        CaiChetRuntime.tasks().next(() -> {
            if (!chunk.isLoaded()) {
                return;
            }
            for (DreamLayout.ChestSpot spot : spots) {
                place(chunk.getWorld(), spot);
            }
        });
    }

    /** Pone el cofre si la celda sigue como se calculo: aire con aire arriba y el piso de la casa abajo. */
    private static void place(World world, DreamLayout.ChestSpot spot) {
        Block block = world.getBlockAt(spot.x(), spot.y(), spot.z());
        if (!block.getType().isAir() || !block.getRelative(BlockFace.UP).getType().isAir()
            || block.getRelative(BlockFace.DOWN).getType() != spot.floor()) {
            return;
        }
        Random random = new Random(HotelLayout.hash(world.getSeed(), spot.x(), spot.z(), 710));
        fill(block, FACES[spot.facing()], loot(random), random);
    }

    /** Un cofre nuevo es simple (no doble); solo hay que girarlo. */
    private static void setFacing(Block block, BlockFace facing) {
        if (block.getBlockData() instanceof Directional data) {
            data.setFacing(facing);
            block.setBlockData(data, false);
        }
    }

    /** Arma un cofre en el bloque (que tiene que estar libre) y lo llena. Devuelve false si no se pudo. */
    private static boolean fill(Block block, BlockFace facing, Loot loot, Random random) {
        ItemStack painting = DreamPaintings.create(loot.painting());
        if (painting == null) {
            return false; // sin la variante cargada no hay nada que poner
        }
        block.setType(Material.CHEST, false);
        setFacing(block, facing);
        if (!(block.getState() instanceof Chest chest)) {
            return false;
        }
        List<ItemStack> items = new ArrayList<>();
        items.add(painting);
        if (loot.bottles() > 0) {
            ItemStack bottle = HotelLoot.bottle();
            bottle.setAmount(loot.bottles());
            items.add(bottle);
        }
        Inventory inventory = chest.getBlockInventory();
        for (ItemStack item : items) {
            int slot;
            do {
                slot = random.nextInt(inventory.getSize());
            } while (inventory.getItem(slot) != null);
            inventory.setItem(slot, item);
        }
        return true;
    }

    /** Para probar: un cofre con los siete cuadros delante del jugador. Devuelve el mensaje para mostrarle. */
    static String buildTestChest(Player player) {
        Block block = player.getLocation().getBlock().getRelative(player.getFacing());
        if (!block.getType().isAir()) {
            return "Ahi no hay lugar para el cofre: mira hacia un bloque de aire.";
        }
        List<ItemStack> items = new ArrayList<>();
        for (String id : DreamPaintings.ids()) {
            ItemStack painting = DreamPaintings.create(id);
            if (painting == null) {
                return "Los cuadros todavia no estan cargados: reinicia el servidor para que lea el datapack.";
            }
            items.add(painting);
        }
        block.setType(Material.CHEST, false);
        setFacing(block, player.getFacing().getOppositeFace());
        if (!(block.getState() instanceof Chest chest)) {
            return "No se pudo armar el cofre.";
        }
        items.forEach(chest.getBlockInventory()::addItem);
        return "Cofre con los " + items.size() + " cuadros puesto en " + block.getX() + " " + block.getY() + " "
            + block.getZ() + ".";
    }
}
