package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.blocks.CustomBlockService;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Paneles de luz fallados del hotel: algunos (siempre los mismos, salen de la semilla) titilan cuando hay alguien
 * cerca. Titilar es cambiar el panel por {@link HotelBlock#LIGHT_PANEL_OFF} y sacar el bloque de luz que el generador
 * puso en el aire de abajo; volver es lo inverso, con el mismo nivel de luz. Al descargarse el chunk o apagarse el
 * plugin quedan encendidos, asi el mundo nunca se guarda con luces apagadas. Corre cada {@link #PERIOD_TICKS}.
 */
final class FlickerLights implements Listener, Runnable {

    static final long PERIOD_TICKS = 2;
    /** De cada 100 paneles, cuantos estan fallados. */
    private static final int FAULTY_PERCENT = 12;
    /** Solo titilan los que estan a esta distancia de algun jugador. */
    private static final double ACTIVE_RADIUS = 40;
    /** El chisporroteo solo se oye si la luz esta a esta distancia, y a cada jugador como mucho cada 3 segundos. */
    private static final double BUZZ_RADIUS = 12;
    private static final long BUZZ_COOLDOWN_TICKS = 60;

    private static final class Light {
        final int x;
        final int y;
        final int z;
        /** Nivel del bloque de luz de abajo (0 si el panel no tenia aire debajo y no alumbraba). */
        final int level;
        /** Pasos que le quedan apagado (0 = prendido). */
        int off;

        Light(int x, int y, int z, int level) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
        }
    }

    /** Por chunk (clave x,z empaquetada), sus paneles fallados. */
    private final Map<Long, List<Light>> byChunk = new HashMap<>();

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    /** Que bloque del hotel es este dato (vacio si no es de los propios). */
    private static Optional<HotelBlock> hotelBlock(BlockData data) {
        return CustomBlockService.stateOf(data).flatMap(HotelBlock::of);
    }

    private static boolean is(BlockData data, HotelBlock expected) {
        return hotelBlock(data).filter(expected::equals).isPresent();
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        if (HotelWorld.isHotel(event.getWorld())) {
            scan(event.getWorld(), event.getChunk());
        }
    }

    /** Busca los paneles de los chunks que ya estaban cargados cuando arranco el plugin (despues de un reload). */
    void loadExisting() {
        World hotel = HotelWorld.get();
        if (hotel == null) {
            return;
        }
        for (Chunk chunk : hotel.getLoadedChunks()) {
            scan(hotel, chunk);
        }
    }

    private void scan(World world, Chunk chunk) {
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        long seed = world.getSeed();
        List<Light> lights = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = HotelLayout.FLOOR + 3; y <= HotelLayout.FLOOR + 31; y++) {
                    // los paneles son hongos rojos: el tipo descarta casi todo antes de leer el estado
                    if (snapshot.getBlockType(x, y, z) != Material.RED_MUSHROOM_BLOCK
                            || !is(snapshot.getBlockData(x, y, z), HotelBlock.LIGHT_PANEL)) {
                        continue;
                    }
                    int wx = (chunk.getX() << 4) + x;
                    int wz = (chunk.getZ() << 4) + z;
                    if (Math.floorMod(HotelLayout.hash(seed, wx, wz, 800 + y), 100) < FAULTY_PERCENT) {
                        lights.add(new Light(wx, y, wz, lightBelow(snapshot, x, y, z)));
                    }
                }
            }
        }
        if (!lights.isEmpty()) {
            byChunk.put(key(chunk.getX(), chunk.getZ()), lights);
        }
    }

    /** El nivel del bloque de luz justo debajo del panel, o 0 si ahi no hay luz. */
    private static int lightBelow(ChunkSnapshot snapshot, int x, int y, int z) {
        BlockData below = snapshot.getBlockData(x, y - 1, z);
        return below.getMaterial() == Material.LIGHT && below instanceof Levelled levelled ? levelled.getLevel() : 0;
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        if (!HotelWorld.isHotel(event.getWorld())) {
            return;
        }
        List<Light> lights = byChunk.remove(key(event.getChunk().getX(), event.getChunk().getZ()));
        if (lights != null) {
            for (Light light : lights) {
                setLit(event.getWorld(), light, true);
            }
        }
    }

    @Override
    public void run() {
        World hotel = HotelWorld.get();
        if (hotel == null || hotel.getPlayers().isEmpty()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Player> players = hotel.getPlayers();
        for (List<Light> lights : byChunk.values()) {
            // todos los paneles de un chunk estan a menos de ~23 bloques del primero: si ese esta lejos, ninguno titila
            if (!near(players, lights.get(0), ACTIVE_RADIUS + 24)) {
                for (Light light : lights) {
                    if (light.off > 0) {
                        light.off = 0;
                        setLit(hotel, light, true);
                    }
                }
                continue;
            }
            for (Light light : lights) {
                if (!near(players, light, ACTIVE_RADIUS)) {
                    if (light.off > 0) {
                        light.off = 0;
                        setLit(hotel, light, true);
                    }
                    continue;
                }
                if (light.off > 0) {
                    light.off--;
                    if (light.off == 0) {
                        setLit(hotel, light, true);
                    }
                } else if (random.nextInt(100) < 9) {
                    // parpadeo corto casi siempre; a veces se queda apagado un rato
                    light.off = random.nextInt(100) < 85 ? 1 + random.nextInt(3) : 8 + random.nextInt(20);
                    setLit(hotel, light, false);
                    buzz(players, light, random);
                }
            }
        }
    }

    /** Deja todo prendido (al apagar el plugin). */
    void restoreAll() {
        World hotel = HotelWorld.get();
        if (hotel == null) {
            return;
        }
        for (List<Light> lights : byChunk.values()) {
            for (Light light : lights) {
                if (light.off > 0) {
                    light.off = 0;
                    setLit(hotel, light, true);
                }
            }
        }
    }

    /** Chisporroteo bajito para el jugador mas cercano (si esta a tiro), con tope por jugador. */
    private static void buzz(List<Player> players, Light light, ThreadLocalRandom random) {
        Player nearest = null;
        double best = BUZZ_RADIUS * BUZZ_RADIUS;
        for (Player player : players) {
            double dx = player.getX() - light.x;
            double dy = player.getY() - light.y;
            double dz = player.getZ() - light.z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < best) {
                best = d;
                nearest = player;
            }
        }
        if (nearest == null
                || !CaiChetSounds.allow(nearest.getUniqueId(), "flicker", Bukkit.getCurrentTick(), BUZZ_COOLDOWN_TICKS)) {
            return;
        }
        Location at = new Location(nearest.getWorld(), light.x + 0.5, light.y + 0.5, light.z + 0.5);
        CaiChetSounds.playTo(nearest, at, CaiChetSounds.HOTEL_FLICKER, SoundCategory.BLOCKS, 0.6f,
                0.9f + random.nextFloat() * 0.2f);
    }

    private static boolean near(List<Player> players, Light light, double radius) {
        for (Player player : players) {
            double dx = player.getX() - light.x;
            double dz = player.getZ() - light.z;
            if (dx * dx + dz * dz < radius * radius) {
                return true;
            }
        }
        return false;
    }

    private static void setLit(World world, Light light, boolean lit) {
        if (!world.isChunkLoaded(light.x >> 4, light.z >> 4)) {
            return;
        }
        Block panel = world.getBlockAt(light.x, light.y, light.z);
        Block below = panel.getRelative(0, -1, 0);
        BlockData data = panel.getBlockData();
        if (lit && is(data, HotelBlock.LIGHT_PANEL_OFF)) {
            panel.setBlockData(Bukkit.createBlockData(HotelBlock.LIGHT_PANEL.state().asString()), false);
            if (light.level > 0 && below.getType().isAir()) {
                below.setBlockData(Bukkit.createBlockData("minecraft:light[level=" + light.level + "]"), false);
            }
        } else if (!lit && is(data, HotelBlock.LIGHT_PANEL)) {
            panel.setBlockData(Bukkit.createBlockData(HotelBlock.LIGHT_PANEL_OFF.state().asString()), false);
            if (below.getType() == Material.LIGHT) {
                below.setType(Material.AIR, false);
            }
        }
    }
}
