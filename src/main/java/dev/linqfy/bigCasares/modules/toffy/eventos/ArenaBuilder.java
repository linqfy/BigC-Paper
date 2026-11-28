package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;

/** Builds a fresh arena for each event from {@link ArenaLayout}. Runs on the main thread. */
final class ArenaBuilder {

    private final World world;
    private final ArenaLayout layout;
    private final Access access;
    private final ArenaBuildPlan plan;
    private final boolean guarded;
    private final int minChunkX, maxChunkX, minChunkZ, maxChunkZ;
    private int chunkX, chunkZ;
    private boolean checking;
    private CompletableFuture<Boolean> loading;
    private ArenaBuildPlan.Cursor cursor;
    private boolean ticketOwned;
    private boolean closed;

    interface Access {
        int minHeight();
        int maxHeight();
        CompletableFuture<Boolean> load(int chunkX, int chunkZ);
        boolean loaded(int chunkX, int chunkZ);
        void removeTicket(int chunkX, int chunkZ);
        Material get(ArenaBuildPlan.Cell cell);
        void set(ArenaBuildPlan.Cell cell, Material material);
    }

    ArenaBuilder(World world, ArenaLayout layout, Plugin plugin,
                 boolean guarded) {
        this(world, layout, ArenaMaterials.defaults(), new BukkitAccess(world, plugin), guarded);
    }

    ArenaBuilder(World world, ArenaLayout layout, ArenaMaterials materials, Plugin plugin, boolean guarded) {
        this(world, layout, materials, new BukkitAccess(world, plugin), guarded);
    }

    ArenaBuilder(World world, ArenaLayout layout, Access access,
                 boolean guarded) {
        this(world, layout, ArenaMaterials.defaults(), access, guarded);
    }

    ArenaBuilder(World world, ArenaLayout layout, ArenaMaterials materials, Access access,
                 boolean guarded) {
        this.world = world;
        this.layout = layout;
        this.access = access;
        this.plan = new ArenaBuildPlan(layout, materials);
        this.guarded = guarded;
        this.checking = guarded;
        if (layout.clearMinY() < access.minHeight() || plan.maxY() >= access.maxHeight()) {
            throw new IllegalArgumentException("La arena excede la altura del mundo.");
        }
        int radius = layout.clearRadius();
        minChunkX = (layout.centerX() - radius) >> 4;
        maxChunkX = (layout.centerX() + radius) >> 4;
        minChunkZ = (layout.centerZ() - radius) >> 4;
        maxChunkZ = (layout.centerZ() + radius) >> 4;
        chunkX = minChunkX;
        chunkZ = minChunkZ;
    }

    /** Loads at most one chunk asynchronously and visits at most budget blocks on the main thread. */
    boolean step(int budget) {
        if (closed) return false;
        if (budget <= 0) throw new IllegalArgumentException("budget must be positive");
        int visited = 0;
        while (visited < budget) {
            if (chunkX > maxChunkX) {
                if (!checking) return true;
                checking = false;
                chunkX = minChunkX;
                chunkZ = minChunkZ;
                return false;
            }
            if (cursor != null && !access.loaded(chunkX, chunkZ)) {
                releaseChunk();
                cursor = null;
                loading = null;
            }
            if (cursor == null) {
                if (loading == null) {
                    loading = access.load(chunkX, chunkZ);
                    return false;
                }
                if (!loading.isDone()) return false;
                ticketOwned = Boolean.TRUE.equals(loading.join());
                loading = null;
                if (!access.loaded(chunkX, chunkZ)) {
                    releaseChunk();
                    return false;
                }
                cursor = new ArenaBuildPlan.Cursor(layout, chunkX, chunkZ);
            }
            while (visited < budget && cursor.hasNext()) {
                ArenaBuildPlan.Cell cell = cursor.next();
                visited++;
                Material wanted = plan.material(cell.x(), cell.y(), cell.z());
                if (wanted == null) continue;
                Material current = access.get(cell);
                // In a fallback world, never replace existing non-air blocks. Matching arena blocks
                // are already correct; this permits safe reuse without assuming block ownership.
                if (guarded && !isAir(current) && current != wanted) {
                    throw new IllegalStateException("Hay bloques ajenos en el mundo de respaldo; no se modificó esa zona.");
                }
                if (!checking && current != wanted) {
                    access.set(cell, wanted);
                }
            }
            if (!cursor.hasNext()) {
                releaseChunk();
                cursor = null;
                if (++chunkZ > maxChunkZ) {
                    chunkZ = minChunkZ;
                    chunkX++;
                }
                return false;
            }
        }
        return false;
    }

    void close() {
        if (closed) return;
        closed = true;
        releaseChunk();
        cursor = null;
        if (loading != null) {
            if (!loading.cancel(false) && !loading.isCompletedExceptionally()
                && Boolean.TRUE.equals(loading.getNow(false))) {
                access.removeTicket(chunkX, chunkZ);
            }
            loading = null;
        }
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private void releaseChunk() {
        if (ticketOwned) access.removeTicket(chunkX, chunkZ);
        ticketOwned = false;
    }

    private record BukkitAccess(World world, Plugin plugin) implements Access {
        public int minHeight() { return world.getMinHeight(); }
        public int maxHeight() { return world.getMaxHeight(); }
        public CompletableFuture<Boolean> load(int x, int z) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            // Paper runs the consumer on the main thread while the requested chunk is still loaded.
            world.getChunkAtAsync(x, z, true, chunk -> {
                if (result.isCancelled()) return;
                try {
                    boolean owned = world.addPluginChunkTicket(x, z, plugin);
                    if (!result.complete(owned) && owned) world.removePluginChunkTicket(x, z, plugin);
                } catch (RuntimeException failure) {
                    result.completeExceptionally(failure);
                }
            });
            return result;
        }
        public boolean loaded(int x, int z) { return world.isChunkLoaded(x, z); }
        public void removeTicket(int x, int z) { world.removePluginChunkTicket(x, z, plugin); }
        public Material get(ArenaBuildPlan.Cell cell) { return world.getBlockAt(cell.x(), cell.y(), cell.z()).getType(); }
        public void set(ArenaBuildPlan.Cell cell, Material material) {
            world.getBlockAt(cell.x(), cell.y(), cell.z()).setType(material, false);
        }
    }

    Location center() {
        return new Location(world, layout.centerX() + 0.5, layout.floorY(), layout.centerZ() + 0.5);
    }

    /** Spread spawn points on a ring inside the arena, facing the centre. */
    List<Location> spawnPoints(int count, Random random) {
        List<Location> points = new ArrayList<>(count);
        double ring = Math.max(2.0, layout.radius() - 3.0);
        double offset = random.nextDouble() * Math.PI * 2;
        for (int index = 0; index < count; index++) {
            double angle = offset + index * (Math.PI * 2 / Math.max(1, count));
            double x = layout.centerX() + 0.5 + Math.cos(angle) * ring;
            double z = layout.centerZ() + 0.5 + Math.sin(angle) * ring;
            Location point = new Location(world, x, layout.floorY(), z);
            point.setDirection(center().toVector().subtract(point.toVector()).setY(0));
            points.add(point);
        }
        return points;
    }

    /** Waiting spot during the lobby: the arena centre, above the hill if there is one. */
    Location lobbySpot() {
        Location spot = center();
        if (layout.type() == ToffyEventType.KOTH) {
            spot.setY(layout.hillTopY());
        }
        return spot;
    }

}
