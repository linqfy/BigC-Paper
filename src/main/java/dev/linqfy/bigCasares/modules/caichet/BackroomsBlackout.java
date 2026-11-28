package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.blocks.CustomBlockService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mientras una entidad te persigue, las luces del hotel alrededor tuyo se apagan: solo para vos. No se toca el mundo:
 * a tu cliente se le manda cada panel encendido como apagado y su bloque de luz como aire (en las zonas de cobre, la
 * bombilla apagada), asi tu juego recalcula la luz y todo queda casi a oscuras. Al terminar se le vuelve a mandar lo que de verdad hay.
 */
final class BackroomsBlackout {

    /** Hasta donde se apagan las luces (bloques, en horizontal). */
    static final int RADIUS = 28;
    /** Cada cuanto se apagan las que quedaron cerca al moverte. */
    static final int RESCAN_TICKS = 30;
    /** Alto que se revisa sobre el piso del hotel (los techos mas altos son los de las piletas). */
    private static final int HEIGHT = 22;

    private static final BlockData AIR = Material.AIR.createBlockData();
    private static BlockData panelOff;

    private final Player player;
    /** Lo que se le cambio al cliente (paneles y luces), para devolverlo. */
    private final Set<Location> changed = new HashSet<>();
    private boolean on;
    private int sinceScan;

    BackroomsBlackout(Player player) {
        this.player = player;
    }

    boolean on() {
        return on;
    }

    /** Un tick: si lo persiguen, apaga (y sigue apagando al moverse); si no, devuelve las luces. */
    void update(boolean chased) {
        if (!chased) {
            restore();
            return;
        }
        if (!on || ++sinceScan >= RESCAN_TICKS) {
            if (!on) {
                // el chasquido de las luces al cortarse
                CaiChetSounds.playTo(player, player.getLocation(), CaiChetSounds.HOTEL_FLICKER,
                    SoundCategory.AMBIENT, 1.2f, 0.6f);
            }
            on = true;
            sinceScan = 0;
            darken();
        }
    }

    private void darken() {
        World world = player.getWorld();
        if (!HotelWorld.isHotel(world)) {
            return;
        }
        Location at = player.getLocation();
        int cx = at.getBlockX();
        int cz = at.getBlockZ();
        int bottom = HotelLayout.FLOOR + 2;
        BlockData off = panelOff();
        // todo en un solo envio por chunk (sendMultiBlockChange), no un paquete por bloque
        Map<Location, BlockData> batch = new HashMap<>();
        for (int x = cx - RADIUS; x <= cx + RADIUS; x++) {
            for (int z = cz - RADIUS; z <= cz + RADIUS; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                // de abajo hacia arriba hasta el primer bloque que no sea aire: el techo de esa columna
                for (int y = bottom; y <= bottom + HEIGHT; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type.isAir() || type == Material.LIGHT) {
                        continue;
                    }
                    BlockData data = block.getBlockData();
                    if (data instanceof Lightable bulb && bulb.isLit() && isCopperBulb(type)) {
                        // zonas de cobre: la bombilla se le muestra apagada
                        Lightable unlit = (Lightable) bulb.clone();
                        unlit.setLit(false);
                        changed.add(block.getLocation());
                        batch.put(block.getLocation(), unlit);
                        break;
                    }
                    if (!isLitPanel(data)) {
                        break;
                    }
                    // se manda de nuevo en cada repaso: si el panel titilo o el chunk se le volvio a mandar al
                    // cliente, la luz real le habria vuelto
                    Location panel = block.getLocation();
                    changed.add(panel);
                    batch.put(panel, off);
                    Block below = block.getRelative(0, -1, 0);
                    if (below.getType() == Material.LIGHT) {
                        changed.add(below.getLocation());
                        batch.put(below.getLocation(), AIR);
                    }
                }
            }
        }
        if (!batch.isEmpty()) {
            player.sendMultiBlockChange(batch);
        }
    }

    /** Le devuelve al cliente lo que de verdad hay donde se le cambio algo. */
    void restore() {
        if (!on) {
            return;
        }
        on = false;
        if (player.isOnline()) {
            Map<Location, BlockData> batch = new HashMap<>();
            for (Location location : changed) {
                if (location.getWorld().equals(player.getWorld())) {
                    batch.put(location, location.getBlock().getBlockData());
                }
            }
            if (!batch.isEmpty()) {
                player.sendMultiBlockChange(batch);
            }
        }
        changed.clear();
    }

    private static boolean isCopperBulb(Material type) {
        return type == Material.WAXED_COPPER_BULB || type == Material.WAXED_WEATHERED_COPPER_BULB;
    }

    private static boolean isLitPanel(BlockData data) {
        return data.getMaterial() == Material.RED_MUSHROOM_BLOCK && CustomBlockService.stateOf(data)
            .flatMap(HotelBlock::of).filter(HotelBlock.LIGHT_PANEL::equals).isPresent();
    }

    private static BlockData panelOff() {
        if (panelOff == null) {
            panelOff = Bukkit.createBlockData(HotelBlock.LIGHT_PANEL_OFF.state().asString());
        }
        return panelOff;
    }
}
