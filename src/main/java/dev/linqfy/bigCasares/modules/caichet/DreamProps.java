package dev.linqfy.bigCasares.modules.caichet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que tienen adentro las puertas sueltas del Sueño: un hueco negro con un "?" rojo (dc2), o rojo brillante en el
 * Collage. Son displays (no se guardan): se ponen al cargar el chunk y se sacan al descargarlo.
 */
public final class DreamProps implements Listener {

    private final Map<Long, List<Entity>> byChunk = new HashMap<>();

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        if (DreamWorld.isDream(event.getWorld())) {
            add(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
        }
    }

    /** Arma lo de los chunks que ya estaban cargados cuando arranco el plugin (despues de un reload). */
    void loadExisting() {
        World world = DreamWorld.get();
        if (world == null) {
            return;
        }
        for (Chunk chunk : world.getLoadedChunks()) {
            add(world, chunk.getX(), chunk.getZ());
        }
    }

    private void add(World world, int cx, int cz) {
        if (byChunk.containsKey(key(cx, cz))) {
            return;
        }
        List<Entity> spawned = new ArrayList<>();
        for (DreamLayout.DoorSpot door : DreamLayout.doorsInChunk(world.getSeed(), cx, cz)) {
            spawned.addAll(dress(world, door));
        }
        if (!spawned.isEmpty()) {
            byChunk.put(key(cx, cz), spawned);
        }
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        if (!DreamWorld.isDream(event.getWorld())) {
            return;
        }
        List<Entity> spawned = byChunk.remove(key(event.getChunk().getX(), event.getChunk().getZ()));
        if (spawned != null) {
            spawned.forEach(Entity::remove);
        }
    }

    void removeAll() {
        byChunk.values().forEach(list -> list.forEach(Entity::remove));
        byChunk.clear();
    }

    private static List<Entity> dress(World world, DreamLayout.DoorSpot door) {
        List<Entity> out = new ArrayList<>();
        BlockFace facing = DoorHole.fromIndex(door.facing());
        Location corner = new Location(world, door.x(), door.y(), door.z());
        out.add(world.spawn(corner, BlockDisplay.class, d -> {
            d.setBlock((door.red() ? Material.RED_CONCRETE : Material.BLACK_CONCRETE).createBlockData());
            d.setTransformation(DoorHole.panel(facing)); // justo donde iria la puerta cerrada
            // el negro sin luz; el rojo brillando, como la puerta del collage
            d.setBrightness(door.red() ? new Display.Brightness(15, 15) : new Display.Brightness(0, 0));
            d.setPersistent(false);
        }));
        if (!door.red()) {
            // el "?" rojo pintado en el negro, de los dos lados
            for (double[] face : DoorHole.faces(facing)) {
                Location at = new Location(world, door.x() + face[0], door.y() + 0.35, door.z() + face[1], (float) face[2], 0f);
                out.add(world.spawn(at, TextDisplay.class, t -> {
                    t.text(Component.text("?").color(TextColor.color(0xE0, 0x10, 0x20)));
                    t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    t.setBillboard(Display.Billboard.FIXED);
                    t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                            new Vector3f(4.5f, 4.5f, 4.5f), new AxisAngle4f()));
                    t.setBrightness(new Display.Brightness(15, 15));
                    t.setPersistent(false);
                }));
            }
        }
        return out;
    }
}
