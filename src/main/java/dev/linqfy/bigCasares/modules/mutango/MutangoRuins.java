package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

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

/**
 * Pone las ruinas (structures/ruinas.json.gz, sacadas de "ruinas modelo/Ruined World Spawn.litematic") en el terreno
 * nuevo de los mundos normales, como las ciudades de Don Pollos:
 * <ul>
 *   <li>una por celda de SPACING bloques (si sale), en un lugar y con un giro al azar fijos segun la semilla;</li>
 *   <li><b>nunca sobre agua</b>: si en el lugar hay agua (o hielo, algas, bloques inundados) no se ponen;</li>
 *   <li>la capa de abajo de las ruinas (pasto y flores) va apoyada sobre el piso, y <b>para abajo se rellena con piedra
 *   y piedra musgosa</b> hasta tocar suelo firme, asi nunca quedan colgando (abajo de las plantas va un bloque de pasto
 *   para que no se rompan);</li>
 *   <li>se construyen por tandas, sin trabar el servidor, y las que ya se hicieron quedan en data/mutango/ruinas.yml;</li>
 *   <li>si abajo hay una cueva o un barranco mas hondo que MAX_FILL_DEPTH no se ponen (no se llena una cueva entera).</li>
 * </ul>
 */
final class MutangoRuins implements Listener {

    private static final int SPACING = 800;
    /** De cada celda, que parte tiene ruinas. */
    private static final double CHANCE = 0.7;
    private static final int MIN_DISTANCE_FROM_SPAWN = 250;
    private static final int BLOCKS_PER_TICK = 4000;
    /** Del relleno, que parte es piedra (el resto, piedra musgosa). */
    private static final int STONE_OUT_OF_10 = 6;
    /** Lo mas que se rellena debajo de una columna: si abajo hay mas vacio que esto (cueva o barranco), no se construye. */
    private static final int MAX_FILL_DEPTH = 12;

    private static final StructureRotation[] ROTATIONS = {
        StructureRotation.NONE, StructureRotation.CLOCKWISE_90,
        StructureRotation.CLOCKWISE_180, StructureRotation.COUNTERCLOCKWISE_90
    };

    private final JavaPlugin plugin;
    private final MutangoTasks tasks;
    private final MutangoStructure template;
    private final MutangoRuinsRecord record;
    private final Map<String, String> status = new LinkedHashMap<>();
    private final Map<String, MutangoRuinsLayout> layouts = new HashMap<>();
    private final BlockData[][] palette = new BlockData[4][];
    private final Deque<Job> queue = new ArrayDeque<>();
    /** Plantas en la capa de abajo de cada columna (x + z * ancho): ahi el relleno de arriba es pasto. */
    private final boolean[] plantOnBottom;
    private BukkitTask task;
    /**
     * Ya se apago (shutdown): lo que llegue despues (chunks que terminaron de cargar) no encola nada ni toca el
     * programador de tareas. Las ruinas que quedaron "pendiente" se terminan en el proximo arranque.
     */
    private volatile boolean stopped;
    /** Donde esta el beacon de las ruinas (el centro de la plataforma): ahi va el agujero del santuario. */
    private int[] beacon;
    private ShrinePlacer shrinePlacer;

    /** Arma el santuario en las ruinas recien hechas: agujero, hacia donde se corre el cubo y hacia donde miran. */
    interface ShrinePlacer {
        void place(Block hole, Vector left, Vector front);
    }

    void setShrinePlacer(ShrinePlacer placer) {
        this.shrinePlacer = placer;
    }

    private MutangoRuins(JavaPlugin plugin, MutangoStructure template, MutangoTasks tasks) {
        this.plugin = plugin;
        this.tasks = tasks;
        this.template = template;
        this.record = new MutangoRuinsRecord(new File(plugin.getDataFolder(), "data/mutango/ruinas.yml"));
        this.plantOnBottom = new boolean[template.width() * template.length()];
        for (int z = 0; z < template.length(); z++) {
            for (int x = 0; x < template.width(); x++) {
                plantOnBottom[x + z * template.width()] = template.isPlant(template.stateAt(x, 0, z));
            }
        }
        for (int y = 0; y < template.height() && beacon == null; y++) {
            for (int z = 0; z < template.length() && beacon == null; z++) {
                for (int x = 0; x < template.width() && beacon == null; x++) {
                    if (template.palette().get(template.stateAt(x, y, z)).equals("minecraft:beacon")) {
                        beacon = new int[]{x, y, z};
                    }
                }
            }
        }
    }

    static MutangoRuins create(JavaPlugin plugin, MutangoTasks tasks) throws IOException {
        try (InputStream stream = plugin.getResource("mutango/structures/ruinas.json.gz")) {
            if (stream == null) {
                throw new IOException("falta mutango/structures/ruinas.json.gz en el plugin");
            }
            MutangoRuins ruins = new MutangoRuins(plugin, MutangoStructure.load(stream), tasks);
            ruins.loadStatus();
            return ruins;
        }
    }

    void start() {
        task = tasks.repeating("ruins-work", this::work, 1L, 1L);
        // las que quedaron a medio hacer (se apago el servidor mientras se construian)
        for (String key : new ArrayList<>(status.keySet())) {
            if (!"pendiente".equals(status.get(key))) {
                continue;
            }
            try {
                recover(key);
            } catch (RuntimeException exception) {
                // un registro roto no puede impedir que el plugin arranque: se descarta y se avisa
                plugin.getLogger().warning("Ruinas " + key + " descartadas, no se pudieron recuperar: " + exception);
                status.put(key, "descartada");
                saveStatus();
            }
        }
    }

    /**
     * Vuelve a encolar unas ruinas que quedaron pendientes. Las del mundo van por celda (uid;celdaX;celdaZ) y las
     * manuales de /mutango ruinas aqui guardan donde y como estaban (uid;manual;x;z;giro;fecha). Se construyen de
     * cero (poner los mismos bloques otra vez da lo mismo). Un registro que no se entiende se descarta.
     */
    private void recover(String key) {
        String[] parts = key.split(";");
        World world = parts.length > 0 ? worldByUid(parts[0]) : null;
        if (world == null) {
            return; // ese mundo no esta cargado ahora: queda pendiente para cuando este
        }
        if (parts.length == 3 && !"manual".equals(parts[1])) {
            layout(world).inCell(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]))
                .ifPresent(plan -> prepare(world, plan, key, null));
        } else if (parts.length == 6 && "manual".equals(parts[1])) {
            int rotation = Math.floorMod(Integer.parseInt(parts[4]), 4);
            int sizeX = rotation % 2 == 0 ? template.width() : template.length();
            int sizeZ = rotation % 2 == 0 ? template.length() : template.width();
            prepare(world, new MutangoRuinsLayout.Plan(Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), rotation, sizeX, sizeZ), key, null);
        } else {
            throw new IllegalArgumentException("formato desconocido");
        }
    }

    // ------------------------------------------------------------ generacion del mundo

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() || event.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }
        World world = event.getWorld();
        Chunk chunk = event.getChunk();
        layout(world).anchoredAt(chunk.getX(), chunk.getZ()).ifPresent(plan -> {
            String key = key(world, plan);
            if (!status.containsKey(key)) {
                prepare(world, plan, key, null);
            }
        });
    }

    private MutangoRuinsLayout layout(World world) {
        return layouts.computeIfAbsent(world.getName(), name -> new MutangoRuinsLayout(world.getSeed(), SPACING,
            CHANCE, MIN_DISTANCE_FROM_SPAWN, template.width(), template.length()));
    }

    private static String key(World world, MutangoRuinsLayout.Plan plan) {
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

    /** Carga (genera) los chunks de las ruinas sin trabar el servidor y despues las encola. */
    private void prepare(World world, MutangoRuinsLayout.Plan plan, String key, Player notify) {
        if (stopped) {
            return;
        }
        status.put(key, "pendiente");
        saveStatus();
        List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
        for (int cx = Math.floorDiv(plan.x() - 2, 16); cx <= Math.floorDiv(plan.x() + plan.sizeX() + 1, 16); cx++) {
            for (int cz = Math.floorDiv(plan.z() - 2, 16); cz <= Math.floorDiv(plan.z() + plan.sizeZ() + 1, 16); cz++) {
                chunks.add(world.getChunkAtAsync(cx, cz, true));
            }
        }
        CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
            if (stopped) {
                return; // el plugin ya se apago: no se agenda nada (queda pendiente para el proximo arranque)
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (stopped) {
                    return;
                }
                if (error != null) {
                    plugin.getLogger().warning("No se pudo cargar el terreno de las ruinas " + key + ": " + error);
                    return;
                }
                enqueue(world, plan, key, notify, chunks.stream().map(CompletableFuture::join).toList());
            });
        });
    }

    private void enqueue(World world, MutangoRuinsLayout.Plan plan, String key, Player notify, List<Chunk> chunks) {
        List<Integer> heights = new ArrayList<>();
        for (int x = plan.x(); x < plan.x() + plan.sizeX(); x += 3) {
            for (int z = plan.z(); z < plan.z() + plan.sizeZ(); z += 3) {
                if (isWater(world.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE))) {
                    // nunca sobre agua: con que haya un poco, no van
                    status.put(key, "agua");
                    saveStatus();
                    plugin.getLogger().info("Ruinas " + key + " salteadas: caen sobre agua.");
                    if (notify != null) {
                        notify.sendMessage("§cAhi hay agua: las ruinas no se pueden poner sobre agua.");
                    }
                    return;
                }
                heights.add(world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES));
            }
        }
        Collections.sort(heights);
        int ground = heights.get(heights.size() / 2);
        if (ground + 1 + template.height() >= world.getMaxHeight()) {
            ground = world.getMaxHeight() - template.height() - 2;
        }
        if (hollowBelow(world, plan, ground)) {
            // abajo hay una cueva o un barranco enorme: no se llena, ahi no van
            status.put(key, "cueva");
            saveStatus();
            plugin.getLogger().info("Ruinas " + key + " salteadas: abajo hay una cueva o un barranco muy hondo.");
            if (notify != null) {
                notify.sendMessage("§cAhi abajo hay una cueva o un barranco muy hondo: las ruinas no se pueden poner ahi.");
            }
            return;
        }
        chunks.forEach(chunk -> chunk.addPluginChunkTicket(plugin));
        queue.add(new Job(world, plan, key, ground, notify == null ? null : notify.getUniqueId(), chunks));
    }

    /** Alguna columna de las ruinas necesitaria mas relleno que MAX_FILL_DEPTH para llegar a suelo firme. */
    private boolean hollowBelow(World world, MutangoRuinsLayout.Plan plan, int ground) {
        for (int x = plan.x(); x < plan.x() + plan.sizeX(); x++) {
            for (int z = plan.z(); z < plan.z() + plan.sizeZ(); z++) {
                int cx = x;
                int cz = z;
                if (MutangoRuinsLayout.fillDepth(ground, world.getMinHeight(), MAX_FILL_DEPTH,
                    y -> isGround(world.getBlockAt(cx, y, cz).getType())) < 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isWater(Block top) {
        Material type = top.getType();
        return top.isLiquid() || type == Material.ICE || type == Material.PACKED_ICE || type == Material.BLUE_ICE
            || type == Material.KELP || type == Material.KELP_PLANT || type == Material.SEAGRASS
            || type == Material.TALL_SEAGRASS || type == Material.BUBBLE_COLUMN || type == Material.LILY_PAD
            || top.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    // ------------------------------------------------------------ construccion por tandas

    private void work() {
        int budget = BLOCKS_PER_TICK;
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
        placeShrine(job);
        job.chunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        status.put(job.key, "hecha");
        saveStatus();
        String where = job.plan.centerX() + " " + (job.ground + 1) + " " + job.plan.centerZ();
        plugin.getLogger().info("Ruinas construidas en " + job.world.getName() + " " + where + ".");
        if (job.notify != null) {
            Player player = Bukkit.getPlayer(job.notify);
            if (player != null) {
                player.sendMessage("§a¡Ruinas listas! §7(" + where + ")");
            }
        }
    }

    /**
     * El santuario va en el beacon (el centro de la plataforma). En la plantilla las estatuas quedan para +Z (miran
     * hacia -Z, de donde llega el jugador) y el cubo se corre hacia +X (la izquierda de quien las mira); se gira igual
     * que las ruinas.
     */
    private void placeShrine(Job job) {
        if (shrinePlacer == null || beacon == null) {
            return;
        }
        int rotation = job.plan.rotation();
        int[] at = offset(rotation, beacon[0], beacon[2]);
        Block hole = job.world.getBlockAt(job.plan.x() + at[0], job.ground + 1 + beacon[1], job.plan.z() + at[1]);
        int[] origin = offset(rotation, 0, 0);
        int[] plusX = offset(rotation, 1, 0);
        int[] plusZ = offset(rotation, 0, 1);
        Vector left = new Vector(plusX[0] - origin[0], 0, plusX[1] - origin[1]);
        Vector front = new Vector(origin[0] - plusZ[0], 0, origin[1] - plusZ[1]);
        shrinePlacer.place(hole, left, front);
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
        return MutangoRotation.toWorld(rotation, template.width(), template.length(), tx, tz);
    }

    /** Al reves que offset: de un lugar en el mundo (relativo a la esquina) a la columna de la plantilla. */
    private int[] templateColumn(int rotation, int dx, int dz) {
        return MutangoRotation.toTemplate(rotation, template.width(), template.length(), dx, dz);
    }

    private static boolean isGround(Material material) {
        return material.isSolid() && !Tag.LEAVES.isTagged(material) && !Tag.LOGS.isTagged(material);
    }

    /** Piedra o piedra musgosa, mezcladas al azar pero siempre igual en el mismo lugar. */
    private static Material filler(int x, int y, int z) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        return Math.floorMod(h, 10) < STONE_OUT_OF_10 ? Material.STONE : Material.MOSSY_COBBLESTONE;
    }

    private final class Job {
        private final World world;
        private final MutangoRuinsLayout.Plan plan;
        private final String key;
        /** Altura del piso: la capa de abajo de las ruinas va en ground + 1. */
        private final int ground;
        private final UUID notify;
        private final List<Chunk> chunks;
        private int column;
        private int index;

        private Job(World world, MutangoRuinsLayout.Plan plan, String key, int ground, UUID notify,
                    List<Chunk> chunks) {
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
            // 1) relleno: cada columna, desde el piso para abajo hasta tocar suelo firme, con piedra y piedra
            //    musgosa (hasta MAX_FILL_DEPTH; si hay mas vacio, hollowBelow ya las descarto). Si arriba va una planta, el
            //    bloque de arriba es pasto para que no se rompa.
            while (budget > 0 && column < columns) {
                int dx = column % plan.sizeX();
                int dz = column / plan.sizeX();
                int x = plan.x() + dx;
                int z = plan.z() + dz;
                int[] t = templateColumn(plan.rotation(), dx, dz);
                boolean plant = plantOnBottom[t[0] + t[1] * template.width()];
                for (int y = ground; y > Math.max(world.getMinHeight(), ground - MAX_FILL_DEPTH); y--) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isGround(block.getType())) {
                        if (y == ground && plant && block.getType() != Material.GRASS_BLOCK
                            && block.getType() != Material.DIRT) {
                            block.setType(Material.GRASS_BLOCK, false);
                        }
                        break;
                    }
                    block.setType(y == ground && plant ? Material.GRASS_BLOCK : filler(x, y, z), false);
                    budget--;
                }
                column++;
                budget--;
            }
            // 2) las ruinas, incluido el aire (asi limpia arboles y lomas que queden adentro)
            int w = template.width();
            int l = template.length();
            int total = w * template.height() * l;
            while (budget > 0 && index < total) {
                int tx = index % w;
                int tz = (index / w) % l;
                int ty = index / (w * l);
                int state = template.stateAt(tx, ty, tz);
                int[] off = offset(plan.rotation(), tx, tz);
                Block block = world.getBlockAt(plan.x() + off[0], ground + 1 + ty, plan.z() + off[1]);
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

    // ------------------------------------------------------------ comandos

    /** /mutango ruinas aqui: unas ruinas delante del jugador (mirando para donde mira). */
    void buildHere(Player player) {
        if (player.getWorld().hasCeiling()) {
            player.sendMessage("§cLas ruinas no se pueden construir en esta dimension (tiene techo).");
            return;
        }
        Location at = player.getLocation();
        int rotation = Math.floorMod(Math.round(at.getYaw() / 90f), 4);
        int sizeX = rotation % 2 == 0 ? template.width() : template.length();
        int sizeZ = rotation % 2 == 0 ? template.length() : template.width();
        Vector ahead = at.getDirection().setY(0);
        if (ahead.lengthSquared() < 1e-6) {
            ahead = new Vector(0, 0, 1);
        }
        ahead.normalize().multiply(Math.max(sizeX, sizeZ) / 2.0 + 4);
        int centerX = (int) Math.floor(at.getX() + ahead.getX());
        int centerZ = (int) Math.floor(at.getZ() + ahead.getZ());
        MutangoRuinsLayout.Plan plan = new MutangoRuinsLayout.Plan(Integer.MIN_VALUE, Integer.MIN_VALUE,
            centerX - sizeX / 2, centerZ - sizeZ / 2, rotation, sizeX, sizeZ);
        // la clave guarda donde y como se arma, para poder terminarla si el servidor se cae a mitad
        String key = player.getWorld().getUID() + ";manual;" + plan.x() + ";" + plan.z() + ";" + rotation + ";"
            + System.currentTimeMillis();
        player.sendMessage("§eConstruyendo ruinas delante tuyo...");
        prepare(player.getWorld(), plan, key, player);
    }

    /** /mutango ruinas cerca: donde quedan las ruinas planeadas mas cercanas. */
    String nearest(Player player) {
        World world = player.getWorld();
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        Optional<MutangoRuinsLayout.Plan> plan = layout(world).nearest(x, z, 3);
        if (plan.isEmpty()) {
            return "§cNo hay ruinas planeadas cerca.";
        }
        MutangoRuinsLayout.Plan ruins = plan.get();
        double distance = Math.hypot(ruins.centerX() - x, ruins.centerZ() - z);
        String state = status.getOrDefault(key(world, ruins), "sin explorar");
        return "§eRuinas mas cercanas: §f" + ruins.centerX() + ", " + ruins.centerZ() + " §7(" + Math.round(distance)
            + " bloques, " + state + ")";
    }

    // ------------------------------------------------------------ guardado

    private void loadStatus() throws IOException {
        status.putAll(record.load());
    }

    private void saveStatus() {
        try {
            record.save(status);
        } catch (IOException exception) {
            plugin.getLogger().warning("No se pudo guardar data/mutango/ruinas.yml: " + exception.getMessage());
        }
    }

    /** Al apagar: termina de una lo que se estaba construyendo para no dejar ruinas por la mitad. */
    void shutdown() {
        stopped = true;
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
