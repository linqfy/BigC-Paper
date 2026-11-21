package dev.linqfy.bigCasares.modules.caichet;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * El pozo en el mundo: aparece solo en chunks nuevos del mundo principal (raro y siempre en el mismo lugar para una
 * semilla), se anota en el chunk, y su fondo negro lleva al desierto. La forma vive en {@link PitLayout}.
 */
final class PitStructure implements Listener {

    private static final NamespacedKey KEY = new NamespacedKey("caichet", "pozo");
    /** Cada cuanto mira a los jugadores del mundo principal (ticks). */
    static final long STEP_PERIOD_TICKS = 5L;
    /** Despues de viajar, el mismo jugador no vuelve a disparar el viaje hasta pasado esto (el viaje es asincrono). */
    private static final int TRAVEL_COOLDOWN_TICKS = 100;
    /** Si el desierto no esta cargado, el aviso se repite como mucho cada tanto. */
    private static final int NOTICE_COOLDOWN_TICKS = 200;
    private static final int MIN_SURFACE_Y = 50;
    private static final int MAX_SURFACE_Y = 140;
    private static final int MAX_SURFACE_DIFFERENCE = 4;
    private static final List<String> WATER_BIOMES = List.of("ocean", "river", "beach");

    private final PitZoneRules zones;
    /** Hasta que tick (de Bukkit.getCurrentTick) cada jugador no puede volver a disparar el viaje o el aviso. */
    private final Map<UUID, Integer> quiet = new HashMap<>();
    /**
     * El pozo anotado en cada chunk cargado del mundo principal (vacio si no hay). El agua y la lava preguntan en cada
     * bloque que fluyen, asi que el PDC del chunk se lee una sola vez hasta que el chunk se descarga.
     */
    private final Map<Long, Optional<PitLayout.Pit>> byChunk = new HashMap<>();

    PitStructure(PitZoneRules zones) {
        this.zones = zones;
    }

    void clear() {
        quiet.clear();
        byChunk.clear();
    }

    private static World overworld() {
        return Bukkit.getWorlds().getFirst();
    }

    // ------------------------------------------------------------------ aparicion

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() || !event.getWorld().equals(overworld())) {
            return;
        }
        Chunk chunk = event.getChunk();
        if (!PitLayout.isCandidate(chunk.getWorld().getSeed(), chunk.getX(), chunk.getZ())) {
            return;
        }
        if (!CaiChetRuntime.active()) {
            return;
        }
        // el chunk recien generado se toca un tick despues, cuando ya termino de cargarse
        CaiChetRuntime.tasks().next(() -> {
            if (!chunk.isLoaded() || chunk.getPersistentDataContainer().has(KEY)) {
                return;
            }
            Siting siting = site(chunk);
            if (siting.pit() != null) {
                place(chunk, siting.pit());
            }
        });
    }

    /** Para el comando: construye en el chunk del jugador (sin exigir chunk nuevo ni rareza) y devuelve que paso. */
    String buildAt(Chunk chunk) {
        if (!chunk.getWorld().equals(overworld())) {
            return "El pozo solo se construye en el mundo principal.";
        }
        if (chunk.getPersistentDataContainer().has(KEY)) {
            return "Ya hay un pozo en este chunk.";
        }
        Siting siting = site(chunk);
        if (siting.pit() == null) {
            return "No se pudo construir el pozo: " + siting.problem() + ".";
        }
        place(chunk, siting.pit());
        PitLayout.Pit pit = siting.pit();
        return "Pozo construido en " + pit.interiorMinX() + " " + pit.rimY() + " " + pit.interiorMinZ() + ".";
    }

    private record Siting(PitLayout.Pit pit, String problem) {
        static Siting rejected(String problem) {
            return new Siting(null, problem);
        }
    }

    /** Mira el terreno del chunk: o el pozo que entra, o por que no. */
    private Siting site(Chunk chunk) {
        World world = chunk.getWorld();
        PitLayout.Pit probe = PitLayout.create(world.getSeed(), chunk.getX(), chunk.getZ(), 0);
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int x = probe.footprintMinX(); x <= probe.footprintMaxX(); x++) {
            for (int z = probe.footprintMinZ(); z <= probe.footprintMaxZ(); z++) {
                int ground = ground(world, x, z);
                if (ground == Integer.MIN_VALUE) {
                    return Siting.rejected("hay agua, lava o hielo en la superficie");
                }
                if (isWaterBiome(world, x, ground, z)) {
                    return Siting.rejected("es un oceano, rio o playa");
                }
                lowest = Math.min(lowest, ground);
                highest = Math.max(highest, ground);
            }
        }
        if (lowest < MIN_SURFACE_Y || highest > MAX_SURFACE_Y) {
            return Siting.rejected("la superficie esta fuera de Y " + MIN_SURFACE_Y + " a " + MAX_SURFACE_Y);
        }
        if (highest - lowest > MAX_SURFACE_DIFFERENCE) {
            return Siting.rejected("el terreno es muy desparejo");
        }
        // el borde a la altura minima: asi no queda un muro flotando
        PitLayout.Pit pit = PitLayout.create(world.getSeed(), chunk.getX(), chunk.getZ(), lowest);
        if (zones.isProtected(world, pit.footprintMinX(), pit.footprintMaxX(), pit.floorY() - 1,
            pit.rimY() + PitLayout.CLEAR_ABOVE, pit.footprintMinZ(), pit.footprintMaxZ())) {
            return Siting.rejected("la zona esta protegida");
        }
        return new Siting(pit, "");
    }

    /**
     * Y del suelo de esa columna, sin contar arboles ni pasto; MIN_VALUE si en la superficie hay agua, lava o hielo.
     */
    private static int ground(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        Block block = world.getBlockAt(x, y, z);
        while (y > world.getMinHeight() && !block.isLiquid() && isCover(block.getType())) {
            y--;
            block = world.getBlockAt(x, y, z);
        }
        if (block.isLiquid() || Tag.ICE.isTagged(block.getType())) {
            return Integer.MIN_VALUE;
        }
        return y;
    }

    /** Lo que esta sobre el suelo y se despeja: aire, plantas, hojas y troncos. */
    private static boolean isCover(Material material) {
        return !material.isSolid() || Tag.LEAVES.isTagged(material) || Tag.LOGS.isTagged(material);
    }

    private static boolean isWaterBiome(World world, int x, int y, int z) {
        String biome = world.getBiome(x, y, z).getKey().getKey();
        return WATER_BIOMES.stream().anyMatch(biome::contains);
    }

    private void place(Chunk chunk, PitLayout.Pit pit) {
        World world = chunk.getWorld();
        PitLayout.build(pit, new PitLayout.Sink() {
            @Override
            public void set(int x, int y, int z, Material material) {
                Block block = world.getBlockAt(x, y, z);
                if (!(material.isAir() && block.getType().isAir())) {
                    block.setBlockData(material.createBlockData(), false);
                }
            }

            @Override
            public void stairs(int x, int y, int z, Material material, BlockFace facing) {
                Stairs data = (Stairs) material.createBlockData();
                data.setFacing(facing);
                world.getBlockAt(x, y, z).setBlockData(data, false);
            }
        });
        chunk.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, pit.encode());
        byChunk.put(chunkKey(chunk.getX(), chunk.getZ()), Optional.of(pit));
        CaiChetRuntime.logger().info("[CaiChet] Pozo construido en " + pit.interiorMinX() + " " + pit.rimY() + " "
            + pit.interiorMinZ());
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    /** El pozo anotado en el chunk de ese bloque, o null (basura anotada tambien da null). */
    private PitLayout.Pit pitOf(Block block) {
        if (!block.getWorld().equals(overworld())) {
            return null;
        }
        long key = chunkKey(block.getX() >> 4, block.getZ() >> 4);
        Optional<PitLayout.Pit> known = byChunk.get(key);
        if (known == null) {
            String raw = block.getChunk().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
            known = raw == null ? Optional.empty() : PitLayout.Pit.decode(raw);
            byChunk.put(key, known);
        }
        return known.orElse(null);
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (event.getWorld().equals(overworld())) {
            byChunk.remove(chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
        }
    }

    private boolean isFloor(Block block) {
        if (block.getType() != PitLayout.FLOOR) {
            return false;
        }
        PitLayout.Pit pit = pitOf(block);
        return pit != null && pit.floorContains(block.getX(), block.getY(), block.getZ());
    }

    private boolean isHollow(Block block) {
        PitLayout.Pit pit = pitOf(block);
        return pit != null && pit.interiorContains(block.getX(), block.getY(), block.getZ());
    }

    // ------------------------------------------------------------------ viaje

    /** Cada pocos ticks: quien esta parado en el fondo negro viaja al desierto. */
    void step() {
        for (Player player : overworld().getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                continue;
            }
            Location at = player.getLocation();
            Block below = player.getWorld().getBlockAt(at.getBlockX(), (int) Math.floor(at.getY() - 0.01),
                at.getBlockZ());
            if (below.getType() == PitLayout.FLOOR) {
                PitLayout.Pit pit = pitOf(below);
                if (pit != null && pit.floorContains(below.getX(), below.getY(), below.getZ())) {
                    travel(player, pit);
                }
            }
        }
    }

    private void travel(Player player, PitLayout.Pit pit) {
        int now = Bukkit.getCurrentTick();
        Integer until = quiet.get(player.getUniqueId());
        if (until != null && now < until) {
            return;
        }
        if (PortalListener.dimension() == null) {
            quiet.put(player.getUniqueId(), now + NOTICE_COOLDOWN_TICKS);
            player.sendActionBar(Component.text("El fondo todavía no responde (reiniciá el servidor)."));
            return;
        }
        quiet.put(player.getUniqueId(), now + TRAVEL_COOLDOWN_TICKS);
        CaiChetSounds.playTo(player, player.getLocation(), CaiChetSounds.PIT_TRAVEL, SoundCategory.AMBIENT, 1f, 1f);
        // se vuelve sobre el borde, no en el fondo: si no, al volver caeria otra vez en el negro
        Location back = new Location(player.getWorld(), pit.returnX(), pit.returnY(), pit.returnZ());
        back.setDirection(pit.outward().getDirection());
        PortalListener.enter(player, back);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if ((action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK)
            || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != PitLayout.FLOOR) {
            return;
        }
        PitLayout.Pit pit = pitOf(block);
        if (pit != null && pit.floorContains(block.getX(), block.getY(), block.getZ())) {
            travel(event.getPlayer(), pit);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        quiet.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ proteccion

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isFloor(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isHollow(event.getBlockPlaced())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (isHollow(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** El agua o la lava de afuera no inundan el hueco (taparian el fondo negro). */
    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (isHollow(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    /** Un dispensador no vacia baldes adentro del hueco. */
    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (!event.getItem().getType().name().endsWith("_BUCKET")
            || !(event.getBlock().getBlockData() instanceof Directional directional)) {
            return;
        }
        if (isHollow(event.getBlock().getRelative(directional.getFacing()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isFloor);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isFloor);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (disturbs(event.getBlocks(), event.getDirection())
            || isHollow(event.getBlock().getRelative(event.getDirection()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (disturbs(event.getBlocks(), event.getDirection().getOppositeFace())) {
            event.setCancelled(true);
        }
    }

    /** Un piston mueve el piso negro, o empuja algo hacia adentro del hueco. */
    private boolean disturbs(List<Block> moved, BlockFace direction) {
        for (Block block : moved) {
            if (isFloor(block) || isHollow(block.getRelative(direction))) {
                return true;
            }
        }
        return false;
    }
}
