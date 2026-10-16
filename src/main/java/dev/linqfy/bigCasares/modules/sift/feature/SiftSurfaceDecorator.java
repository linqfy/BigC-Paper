package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Port of the mod's {@code SiftSurfaceDecorator}: scatters the Sift's plants on
 * freshly exposed terrain (around monoliths, arches, lakes and ruins), with the
 * mod's per-soil chances and plant mixes.
 *
 * <p>Difference: the mod's pitcher oasis species is a pitcher crop kept alive on Sift soil
 * by a mixin; the port places the grown {@code pitcher_plant}, which survives there
 * without one (see {@link SiftSnifferPlantPlacement}).</p>
 */
final class SiftSurfaceDecorator {

    private SiftSurfaceDecorator() {
    }

    /** {@code decorate(level, random, floorPos, densityScale)}. */
    static boolean decorate(WorldRegion region, Random random, Pos floor, float densityScale) {
        Pos plantPos = floor.above();
        if (!region.isWritable(plantPos.x(), plantPos.y(), plantPos.z()) || !region.isAir(plantPos.x(), plantPos.y(), plantPos.z())) {
            return false;
        }
        BlockStateSpec state = region.state(floor.x(), floor.y(), floor.z());
        float roll = random.nextFloat();
        float chance;
        String plant;
        if (SiftTerrain.isGrowth(state)) {
            chance = 0.255F;
            plant = roll < 0.43F ? "overgrown_fronds" : roll < 0.68F ? "overgrown_stalks" : roll < 0.86F ? "overgrown_chard" : "siftslate_stalks";
        } else if (state.equals(SiftTerrain.DRY_HEALTHY_SCULK_GROWTH)) {
            chance = 0.235F;
            plant = roll < 0.34F ? "dry_healthy_sculk_sprouts" : roll < 0.59F ? "overgrown_fronds"
                : roll < 0.8F ? "overgrown_stalks" : roll < 0.93F ? "overgrown_chard" : "siftslate_stalks";
        } else if (state.equals(SiftTerrain.HEALTHY_SCULK)) {
            chance = 0.225F;
            plant = roll < 0.76F ? "healthy_sculk_sprouts" : roll < 0.92F ? "siftslate_stalks" : "overgrown_fronds";
        } else if (state.equals(SiftTerrain.DRY_HEALTHY_SCULK)) {
            chance = 0.145F;
            plant = roll < 0.74F ? "dry_healthy_sculk_sprouts" : roll < 0.94F ? "siftslate_stalks" : "overgrown_stalks";
        } else if (state.equals(SiftTerrain.SIFTSLATE)) {
            chance = 0.045F;
            plant = roll < 0.72F ? "siftslate_stalks" : "overgrown_fronds";
        } else {
            return false;
        }
        if (random.nextFloat() >= chance * densityScale) {
            return false;
        }
        return SiftTerrain.placePlant(region, plantPos.x(), plantPos.y(), plantPos.z(), plant);
    }

    /** {@code decorateLakeShore}: flower batches plus dense shore plants. */
    static void decorateLakeShore(WorldRegion region, Random random, List<Pos> shore) {
        if (shore.isEmpty()) {
            return;
        }
        List<Pos> flowerFloors = new ArrayList<>();
        for (Pos floor : shore) {
            if (SiftTerrain.isLakeFlowerSoil(region.state(floor.x(), floor.y(), floor.z()))) {
                flowerFloors.add(floor);
            }
        }
        int batches = Math.max(2, Math.min(12, 2 + flowerFloors.size() / 30));
        int firstSpecies = random.nextInt(6);
        List<Pos> anchors = new ArrayList<>();
        for (int batch = 0; batch < batches && !flowerFloors.isEmpty(); batch++) {
            Pos anchor = chooseSeparatedAnchor(flowerFloors, anchors, random);
            if (anchor == null) {
                break;
            }
            anchors.add(anchor);
            placeFlowerBatch(region, random, flowerFloors, anchor, (firstSpecies + batch) % 6);
        }
        for (Pos floor : shore) {
            BlockStateSpec state = region.state(floor.x(), floor.y(), floor.z());
            boolean growth = SiftTerrain.isGrowth(state) || state.equals(SiftTerrain.DRY_HEALTHY_SCULK_GROWTH);
            float chance = growth ? 0.68F : state.equals(SiftTerrain.HEALTHY_SCULK) ? 0.6F
                : state.equals(SiftTerrain.DRY_HEALTHY_SCULK) ? 0.5F : state.equals(SiftTerrain.SIFTSLATE) ? 0.45F : 0.0F;
            Pos plantPos = floor.above();
            if (random.nextFloat() >= chance || !region.isAir(plantPos.x(), plantPos.y(), plantPos.z())
                || !region.isWritable(plantPos.x(), plantPos.y(), plantPos.z())) {
                continue;
            }
            float roll = random.nextFloat();
            String plant;
            if (growth) {
                plant = roll < 0.38F ? "overgrown_fronds" : roll < 0.72F ? "overgrown_stalks" : "overgrown_chard";
            } else if (state.equals(SiftTerrain.SIFTSLATE)) {
                plant = roll < 0.82F ? "siftslate_stalks" : "healthy_sculk_sprouts";
            } else if (state.equals(SiftTerrain.HEALTHY_SCULK)) {
                plant = roll < 0.82F ? "healthy_sculk_sprouts" : "siftslate_stalks";
            } else if (state.equals(SiftTerrain.DRY_HEALTHY_SCULK)) {
                plant = "dry_healthy_sculk_sprouts";
            } else {
                continue;
            }
            SiftTerrain.placePlant(region, plantPos.x(), plantPos.y(), plantPos.z(), plant);
        }
    }

    private static Pos chooseSeparatedAnchor(List<Pos> candidates, List<Pos> used, Random random) {
        for (int attempt = 0; attempt < 32; attempt++) {
            Pos candidate = candidates.get(random.nextInt(candidates.size()));
            boolean separated = true;
            for (Pos anchor : used) {
                int dx = candidate.x() - anchor.x();
                int dz = candidate.z() - anchor.z();
                if (dx * dx + dz * dz < 16) {
                    separated = false;
                    break;
                }
            }
            if (separated) {
                return candidate;
            }
        }
        return null;
    }

    private static void placeFlowerBatch(WorldRegion region, Random random, List<Pos> candidates, Pos anchor, int species) {
        int target = 3 + random.nextInt(2);
        List<Pos> nearby = new ArrayList<>();
        for (Pos candidate : candidates) {
            if (Math.abs(candidate.x() - anchor.x()) <= 2 && Math.abs(candidate.z() - anchor.z()) <= 2
                && Math.abs(candidate.y() - anchor.y()) <= 1) {
                nearby.add(candidate);
            }
        }
        int placed = 0;
        while (placed < target && !nearby.isEmpty()) {
            Pos floor = nearby.remove(random.nextInt(nearby.size()));
            if (placeOasisFlower(region, random, floor.above(), species)) {
                placed++;
            }
        }
    }

    private static boolean placeOasisFlower(WorldRegion region, Random random, Pos plantPos, int species) {
        if (!SiftTerrain.isLakeFlowerSoil(region.state(plantPos.x(), plantPos.y() - 1, plantPos.z()))
            || !region.isAir(plantPos.x(), plantPos.y(), plantPos.z())
            || !region.isWritable(plantPos.x(), plantPos.y(), plantPos.z())) {
            return false;
        }
        BlockStateSpec flower = switch (species) {
            case 0 -> SiftTerrain.block("overgrown_lotus");
            case 1 -> SiftTerrain.block("sunburst_plant");
            case 2 -> SiftTerrain.block("whisperbloom");
            case 3 -> BlockStateSpec.of("minecraft:torchflower");
            case 4 -> null;
            default -> SiftTerrain.wildflowers(random);
        };
        if (species == 4) {
            // DoublePlantBlock.placeAt with a pitcher crop aged 3-4 in the mod.
            random.nextInt(2);
            if (!region.isAir(plantPos.x(), plantPos.y() + 1, plantPos.z())
                || !region.isWritable(plantPos.x(), plantPos.y() + 1, plantPos.z())
                || !SiftTerrain.placePlant(region, plantPos.x(), plantPos.y(), plantPos.z(),
                    SiftSnifferPlantPlacement.pitcher("lower"))) {
                return false;
            }
            return region.set(plantPos.x(), plantPos.y() + 1, plantPos.z(), SiftSnifferPlantPlacement.pitcher("upper"));
        }
        return flower != null && SiftTerrain.placePlant(region, plantPos.x(), plantPos.y(), plantPos.z(), flower);
    }

    /** {@code decorateStructureSurface}: denser planting around ruins and landmarks. */
    static boolean decorateStructureSurface(WorldRegion region, Random random, Pos floor, float densityScale) {
        Pos plantPos = floor.above();
        if (!region.isWritable(plantPos.x(), plantPos.y(), plantPos.z()) || !region.isAir(plantPos.x(), plantPos.y(), plantPos.z())) {
            return false;
        }
        BlockStateSpec state = region.state(floor.x(), floor.y(), floor.z());
        float roll = random.nextFloat();
        float chance;
        String plant;
        if (SiftTerrain.isGrowth(state) || state.equals(SiftTerrain.DRY_HEALTHY_SCULK_GROWTH)) {
            chance = 0.72F;
            plant = roll < 0.4F ? "overgrown_fronds" : roll < 0.72F ? "overgrown_stalks" : "overgrown_chard";
        } else if (state.equals(SiftTerrain.SIFTSLATE)) {
            chance = 0.36F;
            plant = "siftslate_stalks";
        } else if (state.equals(SiftTerrain.HEALTHY_SCULK)) {
            chance = 0.42F;
            plant = "healthy_sculk_sprouts";
        } else if (state.equals(SiftTerrain.DRY_HEALTHY_SCULK)) {
            chance = 0.3F;
            plant = "dry_healthy_sculk_sprouts";
        } else {
            return false;
        }
        if (random.nextFloat() >= chance * densityScale) {
            return false;
        }
        return SiftTerrain.placePlant(region, plantPos.x(), plantPos.y(), plantPos.z(), plant);
    }
}
