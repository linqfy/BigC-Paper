package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Sifter's loot table ({@code loot_table/entities/sifter.json}): one roll of raw
 * Sifter meat with {@code set_count} uniform, cooked when the Sifter dies on fire
 * ({@code furnace_smelt}) and increased by Looting ({@code enchanted_count_increase}).
 * The table is read from the imported mod data so the numbers cannot drift from it; a
 * table of a different shape is rejected instead of being half understood.
 *
 * <p>Functions apply in the order the table lists them, with the vanilla rounding of the
 * Looting bonus ({@code Math.round(level * uniform)}). The smelted item is the result of
 * the mod's smelting recipe ({@link #COOKED_ITEM}).</p>
 */
final class SifterLoot {

    static final String RAW_ITEM = "the_sift:raw_sifter_meat";
    /** Result of {@code recipe/sifter_meat_smelting.json}, what {@code furnace_smelt} produces from the raw meat. */
    static final String COOKED_ITEM = "the_sift:cooked_sifter_meat";

    /** One generated stack. */
    record Drop(String item, int count) {
    }

    private final String item;
    private final int rolls;
    private final int minimumCount;
    private final int maximumCount;
    private final boolean smeltsWhenBurning;
    private final float lootingMinimum;
    private final float lootingMaximum;

    private SifterLoot(String item, int rolls, int minimumCount, int maximumCount, boolean smeltsWhenBurning,
                       float lootingMinimum, float lootingMaximum) {
        this.item = item;
        this.rolls = rolls;
        this.minimumCount = minimumCount;
        this.maximumCount = maximumCount;
        this.smeltsWhenBurning = smeltsWhenBurning;
        this.lootingMinimum = lootingMinimum;
        this.lootingMaximum = lootingMaximum;
    }

    /**
     * Reads the mod's table.
     *
     * @throws IllegalArgumentException when the table is not one constant-roll pool with one item entry
     *     whose functions are {@code set_count}, {@code furnace_smelt} and Looting {@code enchanted_count_increase}
     */
    static SifterLoot parse(JsonObject table) {
        JsonArray pools = table.getAsJsonArray("pools");
        if (pools == null || pools.size() != 1) {
            throw new IllegalArgumentException("Expected one pool in the Sifter loot table");
        }
        JsonObject pool = pools.get(0).getAsJsonObject();
        int rolls = pool.get("rolls").getAsInt();
        JsonArray entries = pool.getAsJsonArray("entries");
        if (entries == null || entries.size() != 1) {
            throw new IllegalArgumentException("Expected one entry in the Sifter loot pool");
        }
        JsonObject entry = entries.get(0).getAsJsonObject();
        if (!"minecraft:item".equals(entry.get("type").getAsString())) {
            throw new IllegalArgumentException("Expected an item entry, got " + entry.get("type"));
        }
        int minimum = 1;
        int maximum = 1;
        boolean smelts = false;
        float lootingMinimum = 0.0F;
        float lootingMaximum = 0.0F;
        JsonArray functions = entry.has("modifier") ? entry.getAsJsonArray("modifier") : entry.getAsJsonArray("functions");
        for (JsonElement element : functions == null ? new JsonArray() : functions) {
            JsonObject function = element.getAsJsonObject();
            switch (function.get("type").getAsString()) {
                case "minecraft:set_count" -> {
                    JsonObject count = function.getAsJsonObject("count");
                    if (!"minecraft:uniform".equals(count.get("type").getAsString())) {
                        throw new IllegalArgumentException("Expected a uniform count, got " + count);
                    }
                    minimum = count.get("min").getAsInt();
                    maximum = count.get("max").getAsInt();
                }
                case "minecraft:furnace_smelt" -> smelts = true;
                case "minecraft:enchanted_count_increase" -> {
                    if (!"minecraft:looting".equals(function.get("enchantment").getAsString())) {
                        throw new IllegalArgumentException("Expected Looting, got " + function.get("enchantment"));
                    }
                    JsonObject count = function.getAsJsonObject("count");
                    lootingMinimum = count.get("min").getAsFloat();
                    lootingMaximum = count.get("max").getAsFloat();
                }
                default -> throw new IllegalArgumentException("Unsupported loot function " + function.get("type"));
            }
        }
        return new SifterLoot(entry.get("name").getAsString(), rolls, minimum, maximum, smelts, lootingMinimum, lootingMaximum);
    }

    String item() {
        return item;
    }

    int minimumCount() {
        return minimumCount;
    }

    int maximumCount() {
        return maximumCount;
    }

    /**
     * Rolls the drops of one death.
     *
     * @param lootingLevel the killer's Looting level (0 when there is none)
     * @param burning      whether the Sifter died on fire, which cooks the meat
     */
    List<Drop> roll(Random random, int lootingLevel, boolean burning) {
        List<Drop> drops = new ArrayList<>();
        for (int roll = 0; roll < rolls; roll++) {
            // Mth.nextInt: uniform over the inclusive range.
            int count = minimumCount + random.nextInt(maximumCount - minimumCount + 1);
            String dropped = smeltsWhenBurning && burning ? COOKED_ITEM : item;
            if (lootingLevel > 0) {
                // Mth.randomBetween, then the vanilla rounding of the bonus.
                float bonus = lootingLevel * (random.nextFloat() * (lootingMaximum - lootingMinimum) + lootingMinimum);
                count += Math.round(bonus);
            }
            if (count > 0) {
                drops.add(new Drop(dropped, count));
            }
        }
        return drops;
    }
}
