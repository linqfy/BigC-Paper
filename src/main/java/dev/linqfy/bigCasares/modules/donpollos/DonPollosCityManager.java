package dev.linqfy.bigCasares.modules.donpollos;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/** Pone ciudades de Don Pollo en el terreno nuevo y las llena de Don Pollos. */
final class DonPollosCityManager implements Listener {

    private static final StructureRotation[] ROTATIONS = {
        StructureRotation.NONE, StructureRotation.CLOCKWISE_90,
        StructureRotation.CLOCKWISE_180, StructureRotation.COUNTERCLOCKWISE_90
    };

    private final JavaPlugin plugin;
    private final java.util.function.BiConsumer<Location, DonPolloVariant> spawner;
    private final DonPollosCitySettings settings;
    private final DonPollosCityTemplate template;
    private final File storageFile;
    private final Map<String, String> status = new LinkedHashMap<>();
    private final Map<String, DonPollosCityLayout> layouts = new HashMap<>();
    /** Ciudades hechas con /donpollo ciudad aqui en esta sesion (las del mundo salen de la semilla). */
    private final Map<String, DonPollosCityLayout.CityPlan> manualPlans = new HashMap<>();
    private final BlockData[][] palette = new BlockData[4][];
    private final Deque<Job> queue = new ArrayDeque<>();
    private BukkitTask task;

    private DonPollosCityManager(JavaPlugin plugin, java.util.function.BiConsumer<Location, DonPolloVariant> spawner,
                                 DonPollosCitySettings settings, DonPollosCityTemplate template) {
        this.plugin = plugin;
        this.spawner = spawner;
        this.settings = settings;
        this.template = template;
        this.storageFile = new File(plugin.getDataFolder(), "data/don-pollos/ciudades.yml");
    }

    static DonPollosCityManager create(JavaPlugin plugin, java.util.function.BiConsumer<Location, DonPolloVariant> spawner,
                                       DonPollosCitySettings settings) throws IOException {
        try (InputStream stream = plugin.getResource("donpollos/structures/ciudad.json.gz")) {
            if (stream == null) {
                throw new IOException("falta structures/ciudad.json.gz en el plugin");
            }
            DonPollosCityManager manager = new DonPollosCityManager(plugin, spawner, settings, DonPollosCityTemplate.load(stream));
            manager.loadStatus();
            return manager;
        }
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::work, 1L, 1L);
        // Ciudades que quedaron a medio hacer (se apago el servidor mientras se construian).
        for (Map.Entry<String, String> entry : new ArrayList<>(status.entrySet())) {
            if (entry.getValue().equals("pendiente")) {
                String[] parts = entry.getKey().split(";");
                World world = parts.length == 3 ? worldByUid(parts[0]) : null;
                if (world != null) {
                    layout(world).cityInCell(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]))
                        .ifPresent(plan -> prepare(world, plan, entry.getKey(), null));
                }
            }
        }
    }

    // ------------------------------------------------------------ generacion del mundo

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!settings.enabled() || !event.isNewChunk()) {
            return;
        }
        World world = event.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL || !settings.worlds().contains(world.getName())) {
            return;
        }
        Chunk chunk = event.getChunk();
        layout(world).cityAnchoredAt(chunk.getX(), chunk.getZ()).ifPresent(plan -> {
            String key = key(world, plan);
            if (!status.containsKey(key)) {
                prepare(world, plan, key, null);
            }
        });
    }

    private DonPollosCityLayout layout(World world) {
        return layouts.computeIfAbsent(world.getName(), name -> new DonPollosCityLayout(world.getSeed(),
            settings.spacing(), settings.chance(), settings.minDistanceFromSpawn(), template.width(), template.length()));
    }

    /** Clave por identificador unico del mundo: si borras el mundo y creas otro con el mismo
     *  nombre, el registro viejo no le impide tener sus ciudades. */
    private static String key(World world, DonPollosCityLayout.CityPlan plan) {
        return world.getUID() + ";" + plan.key();
    }

    private static World worldByUid(String uid) {
        try {
            return Bukkit.getWorld(UUID.fromString(uid));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // ------------------------------------------------------------ preparacion

    /** Carga (genera) los chunks de la ciudad sin trabar el servidor y despues la encola. */
    private void prepare(World world, DonPollosCityLayout.CityPlan plan, String key, Player notify) {
        status.put(key, "pendiente");
        saveStatus();
        List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
        for (int cx = Math.floorDiv(plan.x() - 2, 16); cx <= Math.floorDiv(plan.x() + plan.sizeX() + 1, 16); cx++) {
            for (int cz = Math.floorDiv(plan.z() - 2, 16); cz <= Math.floorDiv(plan.z() + plan.sizeZ() + 1, 16); cz++) {
                chunks.add(world.getChunkAtAsync(cx, cz, true));
            }
        }
        CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    plugin.getLogger().warning("No se pudo cargar el terreno de la ciudad " + key + ": " + error);
                    return;
                }
                enqueue(world, plan, key, notify, chunks.stream().map(CompletableFuture::join).toList());
            }));
    }

    private void enqueue(World world, DonPollosCityLayout.CityPlan plan, String key, Player notify, List<Chunk> chunks) {
        List<Integer> heights = new ArrayList<>();
        int water = 0;
        int samples = 0;
        for (int x = plan.x(); x < plan.x() + plan.sizeX(); x += 4) {
            for (int z = plan.z(); z < plan.z() + plan.sizeZ(); z += 4) {
                samples++;
                Block top = world.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);
                if (top.isLiquid() || top.getType() == Material.ICE || top.getType() == Material.KELP_PLANT) {
                    water++;
                }
                heights.add(world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES));
            }
        }
        if (notify == null && water > samples * 0.3) {
            status.put(key, "agua");
            saveStatus();
            plugin.getLogger().info("Ciudad " + key + " salteada: cae sobre agua.");
            return;
        }
        Collections.sort(heights);
        int ground = heights.get(heights.size() / 2);
        if (ground + template.height() >= world.getMaxHeight()) {
            ground = world.getMaxHeight() - template.height() - 1;
        }
        chunks.forEach(chunk -> chunk.addPluginChunkTicket(plugin));
        queue.add(new Job(world, plan, key, ground, notify == null ? null : notify.getUniqueId(), chunks));
    }

    // ------------------------------------------------------------ construccion por tandas

    private void work() {
        int budget = settings.blocksPerTick();
        while (budget > 0 && !queue.isEmpty()) {
            Job job = queue.peek();
            budget = job.step(budget);
            if (job.done()) {
                queue.poll();
                finish(job);
            }
        }
    }

    private void finish(Job job) {
        job.chunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        status.put(job.key, "hecha");
        saveStatus();
        int spawned = spawnPollos(job);
        String where = (job.plan.centerX()) + " " + (job.ground + 1) + " " + (job.plan.centerZ());
        plugin.getLogger().info("Ciudad de Don Pollo construida en " + job.world.getName() + " " + where
            + " con " + spawned + " Don Pollos.");
        if (job.notify != null) {
            Player player = Bukkit.getPlayer(job.notify);
            if (player != null) {
                player.sendMessage("§a¡Ciudad de Don Pollo lista! §7(" + where + ", " + spawned + " Don Pollos)");
            }
        }
    }

    private BlockData data(int rotation, int state) {
        if (palette[rotation] == null) {
            BlockData[] datas = new BlockData[template.palette().size()];
            for (int i = 0; i < datas.length; i++) {
                BlockData data = Bukkit.createBlockData(template.palette().get(i));
                data.rotate(ROTATIONS[rotation]);
                datas[i] = data;
            }
            palette[rotation] = datas;
        }
        return palette[rotation][state];
    }

    /** Desplazamiento en el mundo de un bloque (tx, tz) de la plantilla segun el giro. */
    private int[] offset(int rotation, int tx, int tz) {
        int w = template.width();
        int l = template.length();
        return switch (rotation) {
            case 1 -> new int[]{l - 1 - tz, tx};
            case 2 -> new int[]{w - 1 - tx, l - 1 - tz};
            case 3 -> new int[]{tz, w - 1 - tx};
            default -> new int[]{tx, tz};
        };
    }

    private boolean isGround(Material material) {
        return material.isSolid() && !Tag.LEAVES.isTagged(material) && !Tag.LOGS.isTagged(material);
    }

    private final class Job {
        private final World world;
        private final DonPollosCityLayout.CityPlan plan;
        private final String key;
        private final int ground;
        private final UUID notify;
        private final List<Chunk> chunks;
        private int column;
        private int index;

        private Job(World world, DonPollosCityLayout.CityPlan plan, String key, int ground, UUID notify, List<Chunk> chunks) {
            this.world = world;
            this.plan = plan;
            this.key = key;
            this.ground = ground;
            this.notify = notify;
            this.chunks = chunks;
        }

        boolean done() {
            return index >= template.width() * template.height() * template.length();
        }

        /** Hace hasta budget operaciones; devuelve el presupuesto que sobra. */
        int step(int budget) {
            int columns = plan.sizeX() * plan.sizeZ();
            // 1) cimientos: cada columna se rellena hacia abajo hasta tocar suelo firme, sin limite
            //    de profundidad, para que nunca quede aire (ni agua) debajo de la base.
            while (budget > 0 && column < columns) {
                int x = plan.x() + column % plan.sizeX();
                int z = plan.z() + column / plan.sizeX();
                for (int y = ground - 1; y > world.getMinHeight(); y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isGround(block.getType())) {
                        break;
                    }
                    block.setType(y == ground - 1 ? Material.DIRT : Material.STONE, false);
                    budget--;
                }
                column++;
                budget--;
            }
            // 2) la ciudad, incluido el aire (asi limpia arboles y lomas que queden adentro)
            int w = template.width();
            int l = template.length();
            int total = w * template.height() * l;
            while (budget > 0 && index < total) {
                int tx = index % w;
                int tz = (index / w) % l;
                int ty = index / (w * l);
                int state = template.stateAt(tx, ty, tz);
                int[] off = offset(plan.rotation(), tx, tz);
                Block block = world.getBlockAt(plan.x() + off[0], ground + ty, plan.z() + off[1]);
                if (!(template.isAir(state) && block.getType().isAir())) {
                    block.setBlockData(data(plan.rotation(), state), false);
                    budget -= 2;
                } else {
                    budget--;
                }
                index++;
            }
            return budget;
        }
    }

    // ------------------------------------------------------------ Don Pollos

    private int spawnPollos(Job job) {
        List<Location> spots = new ArrayList<>();
        World world = job.world;
        DonPollosCityLayout.CityPlan plan = job.plan;
        for (int x = plan.x() + 1; x < plan.x() + plan.sizeX() - 1; x++) {
            for (int z = plan.z() + 1; z < plan.z() + plan.sizeZ() - 1; z++) {
                for (int y = job.ground + 1; y <= job.ground + 4; y++) {
                    if (world.getBlockAt(x, y, z).getType().isAir() && world.getBlockAt(x, y + 1, z).getType().isAir()
                        && world.getBlockAt(x, y - 1, z).getType().isSolid()) {
                        spots.add(new Location(world, x + 0.5, y, z + 0.5));
                        break;
                    }
                }
            }
        }
        if (spots.isEmpty()) {
            spots.add(new Location(world, plan.centerX() + 0.5, job.ground + 1, plan.z() - 2.5));
        }
        Collections.shuffle(spots, ThreadLocalRandom.current());
        int spawned = 0;
        for (DonPolloVariant variant : DonPolloVariant.values()) {
            for (int i = 0; i < settings.count(variant); i++) {
                Location spot = spots.get(spawned % spots.size());
                spot.setYaw(ThreadLocalRandom.current().nextFloat() * 360f);
                spawner.accept(spot, variant);
                spawned++;
            }
        }
        return spawned;
    }

    // ------------------------------------------------------------ comandos

    void buildHere(Player player) {
        Location at = player.getLocation();
        int rotation = Math.floorMod(Math.round(at.getYaw() / 90f), 4);
        int sizeX = rotation % 2 == 0 ? template.width() : template.length();
        int sizeZ = rotation % 2 == 0 ? template.length() : template.width();
        org.bukkit.util.Vector ahead = at.getDirection().setY(0);
        if (ahead.lengthSquared() < 1e-6) {
            ahead = new org.bukkit.util.Vector(0, 0, 1);
        }
        ahead.normalize().multiply(Math.max(sizeX, sizeZ) / 2.0 + 4);
        int centerX = (int) Math.floor(at.getX() + ahead.getX());
        int centerZ = (int) Math.floor(at.getZ() + ahead.getZ());
        DonPollosCityLayout.CityPlan plan = new DonPollosCityLayout.CityPlan(Integer.MIN_VALUE, Integer.MIN_VALUE,
            centerX - sizeX / 2, centerZ - sizeZ / 2, rotation, sizeX, sizeZ);
        String key = player.getWorld().getUID() + ";manual;" + System.currentTimeMillis();
        manualPlans.put(key, plan);
        player.sendMessage("§eConstruyendo una ciudad de Don Pollo delante tuyo...");
        prepare(player.getWorld(), plan, key, player);
    }

    /** Ciudad ya construida en la que esta parado (su clave), si hay alguna. */
    Optional<String> cityAt(Location at) {
        World world = at.getWorld();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        Optional<DonPollosCityLayout.CityPlan> seeded = layout(world).nearest(x, z, 1);
        if (seeded.isPresent() && inside(seeded.get(), x, z) && "hecha".equals(status.get(key(world, seeded.get())))) {
            return Optional.of(key(world, seeded.get()));
        }
        String prefix = world.getUID() + ";";
        for (Map.Entry<String, DonPollosCityLayout.CityPlan> manual : manualPlans.entrySet()) {
            if (manual.getKey().startsWith(prefix) && inside(manual.getValue(), x, z)
                && "hecha".equals(status.get(manual.getKey()))) {
                return Optional.of(manual.getKey());
            }
        }
        return Optional.empty();
    }

    private static boolean inside(DonPollosCityLayout.CityPlan plan, int x, int z) {
        return x >= plan.x() && x < plan.x() + plan.sizeX() && z >= plan.z() && z < plan.z() + plan.sizeZ();
    }

    String nearest(Player player) {
        return nearest(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
    }

    String nearest(World world, int x, int z) {
        Optional<DonPollosCityLayout.CityPlan> plan = layout(world).nearest(x, z, 3);
        if (plan.isEmpty()) {
            return "§cNo hay ciudades planeadas cerca.";
        }
        DonPollosCityLayout.CityPlan city = plan.get();
        double distance = Math.hypot(city.centerX() - x, city.centerZ() - z);
        String state = status.getOrDefault(key(world, city), "sin explorar");
        return "§eCiudad de Don Pollo mas cercana: §f" + city.centerX() + ", " + city.centerZ()
            + " §7(" + Math.round(distance) + " bloques, " + state + ", chunk ancla "
            + city.anchorChunkX() + " " + city.anchorChunkZ() + ")";
    }

    // ------------------------------------------------------------ guardado

    private void loadStatus() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        for (String line : yaml.getStringList("ciudades")) {
            int split = line.lastIndexOf('=');
            if (split > 0) {
                String key = line.substring(0, split);
                // Registros viejos guardados por nombre de mundo: se pasan al identificador unico.
                World legacy = Bukkit.getWorld(key.substring(0, Math.max(0, key.indexOf(';'))));
                if (legacy != null) {
                    key = legacy.getUID() + key.substring(key.indexOf(';'));
                }
                status.put(key, line.substring(split + 1));
            }
        }
    }

    private void saveStatus() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> lines = new ArrayList<>();
        status.forEach((key, value) -> lines.add(key + "=" + value));
        yaml.set("ciudades", lines);
        try {
            storageFile.getParentFile().mkdirs();
            yaml.save(storageFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("No se pudo guardar data/don-pollos/ciudades.yml: " + exception.getMessage());
        }
    }

    /** Al apagar: termina de una lo que se estaba construyendo para no dejar ciudades por la mitad. */
    void shutdown() {
        if (task != null) {
            task.cancel();
        }
        while (!queue.isEmpty()) {
            Job job = queue.poll();
            while (!job.done()) {
                job.step(Integer.MAX_VALUE / 4);
            }
            finish(job);
        }
    }
}
