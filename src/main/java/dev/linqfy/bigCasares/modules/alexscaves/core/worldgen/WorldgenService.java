package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import dev.linqfy.bigCasares.modules.alexscaves.config.AlexsCavesSettings.WorldgenSettings;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeResolver.SurfaceKind;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Installs the datapack, checks whether its biomes are loaded and attaches the
 * populator to the configured worlds. Every problem here only switches world
 * generation off; it never fails the module.
 */
public final class WorldgenService {
    private static final int SURFACE_PROBE_Y = 64;
    private static final int SURFACE_CACHE_LIMIT = 4096;

    private final Plugin plugin;
    private final WorldgenSettings settings;
    private final List<ChunkFeature> features;
    private final AtomicBoolean active = new AtomicBoolean();
    // Read by the async biome search of /alexscaves buscar.
    private final List<World> attached = new CopyOnWriteArrayList<>();
    private final List<String> status = new ArrayList<>();
    /** One resolver per world seed, shared by the populator and {@link #nearest}. */
    private final Map<Long, CaveBiomeResolver> resolvers = new ConcurrentHashMap<>();
    private CaveBiomePopulator populator;

    public WorldgenService(Plugin plugin, WorldgenSettings settings, List<ChunkFeature> features) {
        this.plugin = plugin;
        this.settings = settings;
        this.features = List.copyOf(features);
    }

    public void start(DatapackInstaller installer) {
        // The datapack also carries damage types, so it is installed even when generation is off.
        if (settings.installDatapack()) {
            installDatapack(installer);
        }
        if (!settings.enabled()) {
            status.add("Generación desactivada en alexs-caves.yml");
            return;
        }
        List<World> worlds = new ArrayList<>();
        for (String name : settings.worlds()) {
            World world = plugin.getServer().getWorld(name);
            if (world == null) {
                warn("El mundo '" + name + "' no existe; se omite");
            } else if (world.getEnvironment() != World.Environment.NORMAL) {
                warn("El mundo '" + name + "' no es del Overworld; se omite");
            } else {
                worlds.add(world);
            }
        }
        Map<CaveBiomeId, Biome> loaded = loadedBiomes();
        if (loaded.size() < CaveBiomeId.count()) {
            warn("Los biomas de Alex's Caves todavía no están en el registro (faltan "
                + (CaveBiomeId.count() - loaded.size()) + "). Reiniciá el servidor para cargar el datapack; "
                + "mientras tanto la generación queda apagada.");
            return;
        }
        if (worlds.isEmpty()) {
            warn("Ningún mundo configurado está disponible; la generación queda apagada");
            return;
        }
        Map<CaveBiomeId, Biome> enabled = new EnumMap<>(CaveBiomeId.class);
        for (CaveBiomeId biome : settings.enabledBiomes()) {
            enabled.put(biome, loaded.get(biome));
        }
        populator = new CaveBiomePopulator(enabled, features, this::resolver,
            active::get, plugin.getLogger());
        active.set(true);
        for (World world : worlds) {
            PopulatorSlot.of(world).set(populator);
            attached.add(world);
            status.add("Generando biomas en chunks nuevos de '" + world.getName() + "'");
        }
        plugin.getLogger().info("[AlexsCaves] Generación de biomas activa en " + attached.size() + " mundo(s).");
    }

    public void stop() {
        active.set(false);
        for (World world : attached) {
            PopulatorSlot.of(world).clear(populator);
        }
        attached.clear();
        populator = null;
    }

    public boolean active() {
        return active.get();
    }

    /** Whether new chunks of this world get cave biomes. */
    public boolean generates(World world) {
        return active.get() && attached.contains(world);
    }

    /**
     * Nearest column of a biome in a world, as {@code {x, z}}; searches every 32 blocks up to 6000 blocks away.
     * Pure lookup plus the vanilla biome provider, so it may run off the main thread.
     */
    public Optional<int[]> nearest(World world, CaveBiomeId biome, int x, int z) {
        if (!settings.enabledBiomes().contains(biome) || !generates(world)) {
            return Optional.empty();
        }
        CaveBiomeResolver resolver = resolver(world);
        // The nearest column is on the edge of the biome, where its cave is thinnest: go to the middle instead.
        return resolver.nearest(biome, x, z, 6000, 32).map(found -> resolver.towardsCentre(biome, found[0], found[1]));
    }

    private CaveBiomeResolver resolver(WorldInfo worldInfo) {
        return resolvers.computeIfAbsent(worldInfo.getSeed(), seed -> createResolver(worldInfo));
    }

    public List<String> status() {
        return List.copyOf(status);
    }

    private void installDatapack(DatapackInstaller installer) {
        List<World> all = plugin.getServer().getWorlds();
        if (all.isEmpty()) {
            warn("No hay mundos cargados; no se puede instalar el datapack");
            return;
        }
        Optional<Path> levelRoot = DatapackInstaller.findLevelRoot(all.get(0).getWorldPath());
        if (levelRoot.isEmpty()) {
            warn("No se encontró level.dat del mundo principal; datapack sin instalar");
            return;
        }
        try {
            DatapackInstaller.InstallResult result = installer.install(levelRoot.get().resolve("datapacks"));
            if (result.changed()) {
                warn("Datapack instalado/actualizado en " + result.folder()
                    + ". Hace falta reiniciar el servidor para que los cambios de biomas se apliquen.");
            } else {
                status.add("Datapack al día en " + result.folder());
            }
        } catch (IOException | RuntimeException failure) {
            warn("No se pudo instalar el datapack: " + failure.getMessage());
        }
    }

    private CaveBiomeResolver createResolver(WorldInfo worldInfo) {
        BiomeProvider vanilla = worldInfo.vanillaBiomeProvider();
        // Generation threads share this cache; the vanilla lookup runs outside any lock.
        Map<Long, SurfaceKind> cache = new ConcurrentHashMap<>();
        CaveBiomeResolver.SurfaceProbe probe = (x, z) -> {
            long key = ((long) x << 32) ^ (z & 0xffffffffL);
            SurfaceKind cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            SurfaceKind kind = CaveBiomePopulator.classify(vanilla.getBiome(worldInfo, x, SURFACE_PROBE_Y, z).getKey());
            if (cache.size() >= SURFACE_CACHE_LIMIT) {
                cache.clear();
            }
            cache.put(key, kind);
            return kind;
        };
        return new CaveBiomeResolver(new VoronoiRarity(worldInfo.getSeed(), settings.rarity()),
            settings.enabledBiomes(), settings.minSpawnDistance(), probe);
    }

    /** Looks up the six biomes of the datapack; missing ones mean the server has not restarted yet. */
    public static Map<CaveBiomeId, Biome> loadedBiomes() {
        Registry<Biome> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        Map<CaveBiomeId, Biome> found = new EnumMap<>(CaveBiomeId.class);
        for (CaveBiomeId biome : CaveBiomeId.values()) {
            Biome value = registry.get(new NamespacedKey(CaveBiomeId.NAMESPACE, biome.path()));
            if (value != null) {
                found.put(biome, value);
            }
        }
        return found;
    }

    private void warn(String message) {
        status.add(message);
        plugin.getLogger().warning("[AlexsCaves] " + message);
    }
}
