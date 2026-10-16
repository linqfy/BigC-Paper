package dev.linqfy.bigCasares.modules.sift.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Evaluates vanilla placement modifiers for Java-side features, mirroring
 * {@code PlacedFeature#placeWithBiomeCheck}: start at the chunk origin
 * {@code (chunkMinX, minY, chunkMinZ)} and let each modifier map every position
 * to zero or more positions. Supports the modifiers the Sift mod uses.
 */
public final class PlacementInterpreter {

    private PlacementInterpreter() {
    }

    public record Position(int x, int y, int z) {
    }

    public static List<Position> positions(
        JsonArray modifiers, WorldRegion region, Random random, int chunkX, int chunkZ, Set<String> biomes
    ) {
        List<Position> current = new ArrayList<>();
        current.add(new Position(chunkX << 4, region.minY(), chunkZ << 4));
        for (JsonElement element : modifiers) {
            JsonObject modifier = element.getAsJsonObject();
            String type = modifier.get("type").getAsString();
            List<Position> next = new ArrayList<>();
            for (Position position : current) {
                apply(type, modifier, position, region, random, biomes, next);
            }
            current = next;
            if (current.isEmpty()) {
                break;
            }
        }
        return current;
    }

    private static void apply(
        String type, JsonObject modifier, Position at, WorldRegion region, Random random, Set<String> biomes,
        List<Position> out
    ) {
        switch (type) {
            case "minecraft:count" -> {
                int count = sampleInt(modifier.get("count"), random);
                for (int index = 0; index < count; index++) {
                    out.add(at);
                }
            }
            case "minecraft:rarity_filter" -> {
                int chance = modifier.get("chance").getAsInt();
                if (random.nextFloat() < 1.0F / chance) {
                    out.add(at);
                }
            }
            case "minecraft:in_square" -> out.add(new Position(at.x() + random.nextInt(16), at.y(), at.z() + random.nextInt(16)));
            case "minecraft:heightmap" -> {
                WorldRegion.Heightmap heightmap = WorldRegion.Heightmap.valueOf(
                    modifier.get("heightmap").getAsString().toUpperCase(Locale.ROOT));
                int y = region.height(heightmap, at.x(), at.z());
                if (y > region.minY()) {
                    out.add(new Position(at.x(), y, at.z()));
                }
            }
            case "minecraft:height_range" -> {
                JsonObject height = modifier.getAsJsonObject("height");
                int min = anchor(height.getAsJsonObject("min_inclusive"), region);
                int max = anchor(height.getAsJsonObject("max_inclusive"), region);
                if (max >= min) {
                    out.add(new Position(at.x(), min + random.nextInt(max - min + 1), at.z()));
                }
            }
            case "minecraft:surface_relative_threshold_filter" -> {
                WorldRegion.Heightmap heightmap = WorldRegion.Heightmap.valueOf(
                    modifier.get("heightmap").getAsString().toUpperCase(Locale.ROOT));
                int surface = region.height(heightmap, at.x(), at.z());
                long min = modifier.has("min_inclusive") ? surface + (long) modifier.get("min_inclusive").getAsInt() : Long.MIN_VALUE;
                long max = modifier.has("max_inclusive") ? surface + (long) modifier.get("max_inclusive").getAsInt() : Long.MAX_VALUE;
                if (at.y() >= min && at.y() <= max) {
                    out.add(at);
                }
            }
            case "minecraft:biome" -> {
                if (biomes.contains(region.biome(at.x(), at.y(), at.z()))) {
                    out.add(at);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported placement modifier for Java features: " + type);
        }
    }

    private static int anchor(JsonObject anchor, WorldRegion region) {
        if (anchor.has("absolute")) {
            return anchor.get("absolute").getAsInt();
        }
        if (anchor.has("above_bottom")) {
            return region.minY() + anchor.get("above_bottom").getAsInt();
        }
        return region.maxY() - 1 - anchor.get("below_top").getAsInt();
    }

    static int sampleInt(JsonElement provider, Random random) {
        if (provider.isJsonPrimitive()) {
            return provider.getAsInt();
        }
        JsonObject object = provider.getAsJsonObject();
        String type = object.get("type").getAsString();
        if (type.equals("minecraft:uniform")) {
            int min = object.get("min_inclusive").getAsInt();
            int max = object.get("max_inclusive").getAsInt();
            return min + random.nextInt(max - min + 1);
        }
        if (type.equals("minecraft:constant")) {
            return object.get("value").getAsInt();
        }
        throw new IllegalArgumentException("Unsupported int provider for Java features: " + type);
    }
}
