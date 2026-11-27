package dev.linqfy.bigCasares.modules.alexscaves.core.spawn;

import dev.linqfy.bigCasares.modules.alexscaves.config.AlexsCavesSettings.SpawningSettings;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.Entry;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Natural spawning of module mobs around players standing in a cave biome,
 * with the mod's weights and group sizes and per-category caps.
 */
public final class ModSpawner {
    private static final int MIN_DISTANCE = 16;
    private static final int VERTICAL_SEARCH = 10;

    private final SpawningSettings settings;
    private final Set<String> worlds;
    private final ModMobCatalog catalog;
    private final ModMobService mobs;
    private final SpawnBudget budget;
    private final Map<CaveBiomeId, BiomeSpawnTable> tables = new EnumMap<>(CaveBiomeId.class);
    private final SplittableRandom random = new SplittableRandom();

    public ModSpawner(SpawningSettings settings, List<String> worlds, ModMobCatalog catalog, ModMobService mobs) {
        this.settings = settings;
        this.worlds = Set.copyOf(worlds);
        this.catalog = catalog;
        this.mobs = mobs;
        this.budget = SpawnBudget.modDefaults(settings.caveCreatureMultiplier(), settings.capScale());
        for (CaveBiomeId biome : CaveBiomeId.values()) {
            tables.put(biome, new BiomeSpawnTable(ModSpawnTables.all(biome).stream()
                .filter(entry -> catalog.contains(entry.mobId())).toList()));
        }
    }

    public void tick(Iterable<? extends Player> players) {
        if (!settings.enabled()) {
            return;
        }
        for (Player player : players) {
            if (player.getGameMode() == GameMode.SPECTATOR || !worlds.contains(player.getWorld().getName())) {
                continue;
            }
            Optional<CaveBiomeId> biome = biomeAt(player.getLocation());
            if (biome.isEmpty()) {
                continue;
            }
            BiomeSpawnTable table = tables.get(biome.get());
            for (int attempt = 0; attempt < settings.attemptsPerPlayer() && !table.isEmpty(); attempt++) {
                table.pick(random).ifPresent(entry -> trySpawn(player, biome.get(), entry));
            }
            for (var structure : catalog.structureSpawns(biome.get())) {
                if (structure.water()) {
                    tryWaterStructure(player, biome.get(), structure);
                }
            }
        }
    }

    private void trySpawn(Player player, CaveBiomeId biome, Entry entry) {
        SpawnCategory category = entry.category();
        int nearby = mobs.countNear(player.getLocation(), settings.radius(), type -> type.category() == category);
        int remaining = budget.remaining(category, nearby);
        if (remaining <= 0) {
            return;
        }
        boolean water = isWaterCategory(category) || catalog.inWater(entry.mobId());
        Optional<Location> spot = findSpot(player.getLocation(), biome, category, water);
        if (spot.isEmpty()) {
            return;
        }
        if (category == SpawnCategory.MONSTER) {
            for (var structure : catalog.structureSpawns(biome)) {
                if (structure.water() != water) {
                    continue;
                }
                String mob = structure.spawn().mobAt(spot.get().getBlock(), random);
                if (mob != null) {
                    // An empty id: inside the structure, but its spawn rule said no this time.
                    if (!mob.isEmpty()) {
                        spawnGroup(spot.get(), mob, Math.min(remaining, structure.count()), category, biome,
                            isWaterCategory(category) || catalog.inWater(mob));
                    }
                    return;
                }
            }
        }
        int group = Math.min(remaining, entry.groupSize(random));
        for (int i = 0; i < group; i++) {
            Location location = spot.get().clone().add(random.nextDouble(-1.5, 1.5), 0, random.nextDouble(-1.5, 1.5));
            // Group members may land in a neighbouring chunk: never force-load it.
            if (location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                && fits(location.getBlock(), category, water)
                && biomeAt(location).filter(found -> found == biome).isPresent()
                && catalog.allowsSpawn(entry.mobId(), location.getBlock(), random)) {
                mobs.spawnNatural(entry.mobId(), location);
            }
        }
    }

    /**
     * A monster spawn attempt in water for a structure whose monsters spawn there: the spot must be inside it (vanilla
     * replaces the monster list there), then its own rule picks the mob.
     */
    private void tryWaterStructure(Player player, CaveBiomeId biome, ModMobCatalog.StructureSpawns structure) {
        int nearby = mobs.countNear(player.getLocation(), settings.radius(), type -> type.category() == SpawnCategory.MONSTER);
        int remaining = budget.remaining(SpawnCategory.MONSTER, nearby);
        if (remaining <= 0) {
            return;
        }
        Optional<Location> spot = findSpot(player.getLocation(), biome, SpawnCategory.MONSTER, true);
        if (spot.isEmpty()) {
            return;
        }
        String mob = structure.spawn().mobAt(spot.get().getBlock(), random);
        if (mob != null && !mob.isEmpty()) {
            spawnGroup(spot.get(), mob, Math.min(remaining, 1 + random.nextInt(structure.count())), SpawnCategory.MONSTER, biome, true);
        }
    }

    /** A structure's group: module mobs through the service, vanilla ones ({@code minecraft:witch}) directly. */
    private void spawnGroup(Location spot, String mob, int count, SpawnCategory category, CaveBiomeId biome, boolean water) {
        for (int i = 0; i < count; i++) {
            Location location = spot.clone().add(random.nextDouble(-1.5, 1.5), 0, random.nextDouble(-1.5, 1.5));
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                || !fits(location.getBlock(), category, water) || biomeAt(location).filter(found -> found == biome).isEmpty()) {
                continue;
            }
            if (mob.startsWith("minecraft:")) {
                org.bukkit.entity.EntityType type = org.bukkit.Registry.ENTITY_TYPE.get(
                    org.bukkit.NamespacedKey.minecraft(mob.substring("minecraft:".length())));
                if (type != null && type.isSpawnable()) {
                    location.getWorld().spawnEntity(location, type, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL);
                }
            } else {
                mobs.spawnNatural(mob, location);
            }
        }
    }

    private Optional<Location> findSpot(Location origin, CaveBiomeId biome, SpawnCategory category, boolean water) {
        World world = origin.getWorld();
        double angle = random.nextDouble(Math.PI * 2);
        double distance = MIN_DISTANCE + random.nextDouble(Math.max(1, settings.radius() - MIN_DISTANCE));
        int x = (int) Math.floor(origin.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(origin.getZ() + Math.sin(angle) * distance);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return Optional.empty();
        }
        int startY = origin.getBlockY() + random.nextInt(-VERTICAL_SEARCH, VERTICAL_SEARCH + 1);
        for (int offset = 0; offset <= VERTICAL_SEARCH; offset++) {
            for (int sign : new int[] {1, -1}) {
                int y = startY + offset * sign;
                if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) {
                    continue;
                }
                Block block = world.getBlockAt(x, y, z);
                if (fits(block, category, water) && biomeAt(block.getLocation()).filter(found -> found == biome).isPresent()) {
                    return Optional.of(block.getLocation().add(0.5, 0, 0.5));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * @param water the mob spawns in water (its category, or a monster placed {@code IN_WATER}); monsters keep the
     *              vanilla darkness rule there too
     */
    static boolean fits(Block block, SpawnCategory category, boolean water) {
        if (water) {
            return block.getType() == Material.WATER && block.getRelative(0, 1, 0).getType() == Material.WATER
                && (category != SpawnCategory.MONSTER || block.getLightFromBlocks() <= 0);
        }
        Block below = block.getRelative(0, -1, 0);
        boolean standing = below.getType().isSolid() && block.isPassable() && !block.isLiquid()
            && block.getRelative(0, 1, 0).isPassable() && !block.getRelative(0, 1, 0).isLiquid();
        if (!standing) {
            return false;
        }
        // Hostiles keep the vanilla darkness rule; cave creatures spawn in any light like in the mod.
        return category != SpawnCategory.MONSTER || block.getLightFromBlocks() <= 0;
    }

    private static boolean isWaterCategory(SpawnCategory category) {
        return category == SpawnCategory.WATER_AMBIENT || category == SpawnCategory.DEEP_SEA_CREATURE
            || category == SpawnCategory.UNDERGROUND_WATER_CREATURE;
    }

    public static Optional<CaveBiomeId> biomeAt(Location location) {
        var key = location.getBlock().getBiome().getKey();
        if (!CaveBiomeId.NAMESPACE.equals(key.getNamespace())) {
            return Optional.empty();
        }
        return CaveBiomeId.parse(key.getKey());
    }
}
