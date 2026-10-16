package dev.linqfy.bigCasares.modules.sift.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A small deterministic evaluator for the data-pack loot tables the Sift's chests use
 * (a port of the relevant parts of Minecraft's {@code LootTable}/{@code LootPool}/
 * {@code LootPoolEntryContainer}), driven by a plain {@link Random}.
 *
 * <p>Supported subset: pools with constant or {@code uniform} {@code rolls}; pool and entry
 * {@code minecraft:random_chance} conditions; {@code minecraft:item}, {@code minecraft:empty}
 * and {@code minecraft:alternatives} entries with weights; {@code minecraft:set_count} functions
 * (constant or {@code uniform}; {@code add}) at entry and pool level. The mod's tables spell
 * the keys {@code condition} and {@code modifier}; the vanilla spellings {@code conditions}
 * and {@code functions} are accepted too. Anything else (other entry types, conditions,
 * functions, amount providers; bonus rolls, luck, loot contexts) is not evaluated: an
 * unsupported entry, pool or condition is skipped together with its owner and an unsupported
 * function is ignored, and each is listed in {@link #unsupported()} so callers and tests
 * can see what was left out. Luck is always zero, like a chest opened by a player without luck.</p>
 *
 * <p>Instances are immutable and thread-safe; only the {@link Random} passed to
 * {@link #roll} carries state.</p>
 */
final class SiftLootTable {

    /** One generated stack, before any item-id mapping. */
    record Stack(String item, int count) {
    }

    private interface Amount {
        int nextInt(Random random);

        float nextFloat(Random random);
    }

    private record Constant(float value) implements Amount {
        @Override
        public int nextInt(Random random) {
            return Math.round(value);
        }

        @Override
        public float nextFloat(Random random) {
            return value;
        }
    }

    private record Uniform(Amount min, Amount max) implements Amount {
        @Override
        public int nextInt(Random random) {
            return nextIntBetween(random, min.nextInt(random), max.nextInt(random));
        }

        @Override
        public float nextFloat(Random random) {
            float low = min.nextFloat(random);
            float high = max.nextFloat(random);
            return random.nextFloat() * (high - low) + low;
        }
    }

    private interface Condition {
        boolean test(Random random);
    }

    private record RandomChance(Amount chance) implements Condition {
        @Override
        public boolean test(Random random) {
            return random.nextFloat() < chance.nextFloat(random);
        }
    }

    private interface Modifier {
        int apply(int count, Random random);
    }

    private record SetCount(Amount value, boolean add) implements Modifier {
        @Override
        public int apply(int count, Random random) {
            return (add ? count : 0) + value.nextInt(random);
        }
    }

    /** A selectable result of an entry: its weight and item ({@code null} for an empty entry). */
    private record Leaf(int weight, String item, List<Modifier> functions) {
    }

    private interface Entry {
        /** Adds the entry's selectable leaves to {@code out}; false when its conditions fail (alternatives then try the next). */
        boolean expand(Random random, List<Leaf> out);
    }

    private record Item(String name, int weight, List<Condition> conditions, List<Modifier> functions) implements Entry {
        @Override
        public boolean expand(Random random, List<Leaf> out) {
            if (!all(conditions, random)) {
                return false;
            }
            out.add(new Leaf(weight, name, functions));
            return true;
        }
    }

    private record Empty(int weight, List<Condition> conditions) implements Entry {
        @Override
        public boolean expand(Random random, List<Leaf> out) {
            if (!all(conditions, random)) {
                return false;
            }
            out.add(new Leaf(weight, null, List.of()));
            return true;
        }
    }

    private record Alternatives(List<Condition> conditions, List<Entry> children) implements Entry {
        @Override
        public boolean expand(Random random, List<Leaf> out) {
            if (!all(conditions, random)) {
                return false;
            }
            for (Entry child : children) {
                if (child.expand(random, out)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record Pool(Amount rolls, List<Condition> conditions, List<Entry> entries, List<Modifier> functions) {
    }

    private final List<Pool> pools;
    private final List<String> unsupported;

    private SiftLootTable(List<Pool> pools, List<String> unsupported) {
        this.pools = List.copyOf(pools);
        this.unsupported = List.copyOf(unsupported);
    }

    /** Parses a loot table JSON object; never throws for unsupported content (see {@link #unsupported()}). */
    static SiftLootTable parse(JsonObject json) {
        List<String> unsupported = new ArrayList<>();
        List<Pool> pools = new ArrayList<>();
        for (JsonElement element : list(json, "pools")) {
            Pool pool = parsePool(element.getAsJsonObject(), unsupported);
            if (pool != null) {
                pools.add(pool);
            }
        }
        return new SiftLootTable(pools, unsupported);
    }

    /** Descriptions of everything the table contains that this evaluator does not run. */
    List<String> unsupported() {
        return unsupported;
    }

    /** Evaluates every pool once: the stacks a vanilla loot table would roll, in roll order, without empty stacks. */
    List<Stack> roll(Random random) {
        List<Stack> out = new ArrayList<>();
        for (Pool pool : pools) {
            if (!all(pool.conditions(), random)) {
                continue;
            }
            int rolls = pool.rolls().nextInt(random);
            for (int roll = 0; roll < rolls; roll++) {
                Leaf leaf = pick(pool, random);
                if (leaf == null || leaf.item() == null) {
                    continue;
                }
                int count = 1;
                for (Modifier function : leaf.functions()) {
                    count = function.apply(count, random);
                }
                for (Modifier function : pool.functions()) {
                    count = function.apply(count, random);
                }
                if (count > 0) {
                    out.add(new Stack(leaf.item(), count));
                }
            }
        }
        return out;
    }

    /** {@code LootPool.addRandomItem}: weighted choice among the entries whose conditions pass. */
    private static Leaf pick(Pool pool, Random random) {
        List<Leaf> leaves = new ArrayList<>();
        for (Entry entry : pool.entries()) {
            entry.expand(random, leaves);
        }
        List<Leaf> weighted = new ArrayList<>();
        int total = 0;
        for (Leaf leaf : leaves) {
            if (leaf.weight() > 0) {
                weighted.add(leaf);
                total += leaf.weight();
            }
        }
        if (weighted.isEmpty()) {
            return null;
        }
        if (weighted.size() == 1) {
            return weighted.getFirst();
        }
        int pick = random.nextInt(total);
        for (Leaf leaf : weighted) {
            pick -= leaf.weight();
            if (pick < 0) {
                return leaf;
            }
        }
        return null;
    }

    /**
     * {@code LootTable.fill}: splits multi-item stacks to use up free slots and scatters the
     * stacks over random distinct slots of a container with {@code slots} empty slots.
     */
    static List<WorldRegion.ItemSpec> distribute(List<Stack> stacks, int slots, Random random) {
        List<Integer> freeSlots = new ArrayList<>(slots);
        for (int slot = 0; slot < slots; slot++) {
            freeSlots.add(slot);
        }
        Collections.shuffle(freeSlots, random);
        List<Stack> results = shuffleAndSplit(stacks, freeSlots.size(), random);
        List<WorldRegion.ItemSpec> specs = new ArrayList<>();
        for (Stack stack : results) {
            if (freeSlots.isEmpty()) {
                break;
            }
            specs.add(new WorldRegion.ItemSpec(stack.item(), stack.count(), freeSlots.removeLast()));
        }
        return specs;
    }

    /** {@code LootTable.shuffleAndSplitItems}. */
    private static List<Stack> shuffleAndSplit(List<Stack> stacks, int freeSlots, Random random) {
        List<Stack> results = new ArrayList<>();
        List<Stack> splittable = new ArrayList<>();
        for (Stack stack : stacks) {
            if (stack.count() > 1) {
                splittable.add(stack);
            } else if (stack.count() == 1) {
                results.add(stack);
            }
        }
        while (freeSlots - results.size() - splittable.size() > 0 && !splittable.isEmpty()) {
            Stack toSplit = splittable.remove(nextIntBetween(random, 0, splittable.size() - 1));
            int splitCount = nextIntBetween(random, 1, toSplit.count() / 2);
            Stack splitOff = new Stack(toSplit.item(), splitCount);
            toSplit = new Stack(toSplit.item(), toSplit.count() - splitCount);
            if (toSplit.count() > 1 && random.nextBoolean()) {
                splittable.add(toSplit);
            } else {
                results.add(toSplit);
            }
            if (splitOff.count() > 1 && random.nextBoolean()) {
                splittable.add(splitOff);
            } else {
                results.add(splitOff);
            }
        }
        results.addAll(splittable);
        Collections.shuffle(results, random);
        return results;
    }

    /** {@code Mth.nextInt}: inclusive bounds, {@code min} when the range is empty. */
    private static int nextIntBetween(Random random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    private static boolean all(List<Condition> conditions, Random random) {
        for (Condition condition : conditions) {
            if (!condition.test(random)) {
                return false;
            }
        }
        return true;
    }

    private static Pool parsePool(JsonObject json, List<String> unsupported) {
        List<Condition> conditions = parseConditions(json, unsupported, "pool");
        Amount rolls = json.has("rolls") ? parseAmount(json.get("rolls"), unsupported) : new Constant(1);
        if (conditions == null || rolls == null) {
            return null;
        }
        if (json.has("bonus_rolls")) {
            unsupported.add("bonus_rolls (luck is always zero)");
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : list(json, "entries")) {
            Entry entry = parseEntry(element.getAsJsonObject(), unsupported);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return new Pool(rolls, conditions, entries, parseFunctions(json, unsupported));
    }

    private static Entry parseEntry(JsonObject json, List<String> unsupported) {
        String type = string(json, "type", "");
        List<Condition> conditions = parseConditions(json, unsupported, "entry " + type);
        if (conditions == null) {
            return null;
        }
        int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
        switch (type) {
            case "minecraft:item" -> {
                if (!json.has("name")) {
                    unsupported.add("item entry without a name");
                    return null;
                }
                return new Item(json.get("name").getAsString(), weight, conditions, parseFunctions(json, unsupported));
            }
            case "minecraft:empty" -> {
                return new Empty(weight, conditions);
            }
            case "minecraft:alternatives" -> {
                List<Entry> children = new ArrayList<>();
                for (JsonElement child : list(json, "children")) {
                    Entry parsed = parseEntry(child.getAsJsonObject(), unsupported);
                    if (parsed != null) {
                        children.add(parsed);
                    }
                }
                return new Alternatives(conditions, children);
            }
            default -> {
                unsupported.add("entry type " + type);
                return null;
            }
        }
    }

    /** The conditions of an owner, or {@code null} when one cannot be evaluated (the owner is then skipped). */
    private static List<Condition> parseConditions(JsonObject json, List<String> unsupported, String owner) {
        List<Condition> conditions = new ArrayList<>();
        boolean supported = true;
        for (JsonElement element : list(json, "conditions", "condition")) {
            JsonObject condition = element.getAsJsonObject();
            String type = string(condition, "type", "");
            Amount chance = null;
            if (type.equals("minecraft:random_chance") && condition.has("chance")) {
                chance = parseAmount(condition.get("chance"), unsupported);
            } else {
                unsupported.add("condition " + type + " on " + owner + " (owner skipped)");
            }
            if (chance == null) {
                supported = false;
            } else {
                conditions.add(new RandomChance(chance));
            }
        }
        return supported ? conditions : null;
    }

    private static List<Modifier> parseFunctions(JsonObject json, List<String> unsupported) {
        List<Modifier> functions = new ArrayList<>();
        for (JsonElement element : list(json, "functions", "modifier")) {
            JsonObject function = element.getAsJsonObject();
            String type = string(function, "type", "");
            Amount count = type.equals("minecraft:set_count") && function.has("count")
                ? parseAmount(function.get("count"), unsupported) : null;
            if (count == null) {
                unsupported.add("function " + type + " (ignored)");
            } else {
                boolean add = function.has("add") && function.get("add").getAsBoolean();
                functions.add(new SetCount(count, add));
            }
        }
        return functions;
    }

    private static Amount parseAmount(JsonElement element, List<String> unsupported) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return new Constant(element.getAsFloat());
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String type = string(object, "type", "");
            if (type.equals("minecraft:constant") && object.has("value")) {
                return new Constant(object.get("value").getAsFloat());
            }
            if (type.equals("minecraft:uniform") && object.has("min") && object.has("max")) {
                Amount min = parseAmount(object.get("min"), unsupported);
                Amount max = parseAmount(object.get("max"), unsupported);
                if (min != null && max != null) {
                    return new Uniform(min, max);
                }
                return null;
            }
            unsupported.add("amount provider " + type);
            return null;
        }
        unsupported.add("amount provider " + element);
        return null;
    }

    /** The first present key as a list: arrays as they are, a single object as a one-element list. */
    private static List<JsonElement> list(JsonObject json, String... keys) {
        for (String key : keys) {
            JsonElement element = json.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                List<JsonElement> out = new ArrayList<>(array.size());
                array.forEach(out::add);
                return out;
            }
            return List.of(element);
        }
        return List.of();
    }

    private static String string(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        return element instanceof JsonPrimitive primitive ? primitive.getAsString() : fallback;
    }
}
