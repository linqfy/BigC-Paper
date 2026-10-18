package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * The imported loot tables whose drops do not vary: pools with a constant number of rolls of single
 * {@code minecraft:item} entries, optionally behind {@code survives_explosion}, {@code match_block}
 * (block id and state properties) and {@code all_of} conditions. That covers the Dark Sniffer's death and
 * digging tables and the Sculkflower's block tables. The table is read from the imported mod data so the
 * numbers cannot drift from it; anything else (functions, weights, other conditions) is rejected
 * instead of being half understood. The mod spells the pool key {@code condition}; vanilla's
 * {@code conditions} is accepted too.
 */
final class SiftFixedLoot {

    /** One generated stack. */
    record Drop(String item, int count) {
    }

    /**
     * What the conditions can see.
     *
     * @param explosionYield the share of blocks that survive the explosion that destroys the block
     *                       (1 / radius), or null when it was not an explosion
     * @param blockId        the block that dropped, e.g. {@code the_sift:sculkflower_crop}
     * @param state          its state properties as text
     */
    record Context(Float explosionYield, String blockId, Map<String, String> state) {

        static Context none() {
            return new Context(null, "", Map.of());
        }
    }

    private record Pool(int rolls, String item, Predicate<ContextAndRandom> condition) {
    }

    private record ContextAndRandom(Context context, Random random) {
    }

    private final List<Pool> pools;

    private SiftFixedLoot(List<Pool> pools) {
        this.pools = pools;
    }

    /** @throws IllegalArgumentException when the table has a shape this class does not evaluate */
    static SiftFixedLoot parse(JsonObject table) {
        List<Pool> pools = new ArrayList<>();
        JsonArray array = table.getAsJsonArray("pools");
        if (array == null) {
            throw new IllegalArgumentException("A loot table needs pools");
        }
        for (JsonElement element : array) {
            JsonObject pool = element.getAsJsonObject();
            JsonElement rolls = pool.get("rolls");
            if (rolls == null || !rolls.isJsonPrimitive() || !rolls.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Expected constant rolls, got " + rolls);
            }
            JsonArray entries = pool.getAsJsonArray("entries");
            if (entries == null || entries.size() != 1) {
                throw new IllegalArgumentException("Expected one entry per pool");
            }
            JsonObject entry = entries.get(0).getAsJsonObject();
            if (!"minecraft:item".equals(entry.get("type").getAsString())
                || entry.has("modifier") || entry.has("functions") || entry.has("condition") || entry.has("conditions")) {
                throw new IllegalArgumentException("Expected a plain item entry, got " + entry);
            }
            JsonElement conditions = pool.has("condition") ? pool.get("condition") : pool.get("conditions");
            pools.add(new Pool(rolls.getAsInt(), entry.get("name").getAsString(), conditions(conditions)));
        }
        return new SiftFixedLoot(List.copyOf(pools));
    }

    private static Predicate<ContextAndRandom> conditions(JsonElement element) {
        if (element == null) {
            return state -> true;
        }
        if (element.isJsonArray()) {
            List<Predicate<ContextAndRandom>> all = new ArrayList<>();
            element.getAsJsonArray().forEach(condition -> all.add(condition(condition.getAsJsonObject())));
            return state -> all.stream().allMatch(condition -> condition.test(state));
        }
        return condition(element.getAsJsonObject());
    }

    private static Predicate<ContextAndRandom> condition(JsonObject condition) {
        String type = condition.get("type").getAsString();
        return switch (type) {
            case "minecraft:survives_explosion" -> state -> {
                Float yield = state.context().explosionYield();
                return yield == null || state.random().nextFloat() <= yield;
            };
            case "minecraft:match_block" -> {
                String block = condition.get("blocks").getAsString();
                Map<String, String> required = new java.util.HashMap<>();
                if (condition.has("state")) {
                    condition.getAsJsonObject("state").entrySet()
                        .forEach(property -> required.put(property.getKey(), property.getValue().getAsString()));
                }
                yield state -> state.context().blockId().equals(block)
                    && required.entrySet().stream().allMatch(property ->
                        property.getValue().equals(state.context().state().get(property.getKey())));
            }
            case "minecraft:all_of" -> {
                List<Predicate<ContextAndRandom>> terms = new ArrayList<>();
                condition.getAsJsonArray("terms").forEach(term -> terms.add(condition(term.getAsJsonObject())));
                yield state -> terms.stream().allMatch(term -> term.test(state));
            }
            default -> throw new IllegalArgumentException("Unsupported loot condition " + type);
        };
    }

    /** Every pool's item with its roll count, ignoring conditions (what the table can drop at most). */
    List<Drop> possibleDrops() {
        return pools.stream().map(pool -> new Drop(pool.item(), pool.rolls())).toList();
    }

    /** Rolls the table: a pool whose conditions hold gives one item per roll. */
    List<Drop> roll(Random random, Context context) {
        List<Drop> drops = new ArrayList<>();
        ContextAndRandom state = new ContextAndRandom(context, random);
        for (Pool pool : pools) {
            // Pool conditions are decided once per pool, like the mod's LootPool.
            if (!pool.condition().test(state)) {
                continue;
            }
            for (int roll = 0; roll < pool.rolls(); roll++) {
                drops.add(new Drop(pool.item(), 1));
            }
        }
        return drops;
    }
}
