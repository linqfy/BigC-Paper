package dev.linqfy.bigCasares.modules.sift.feature;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.sift.SiftData;
import dev.linqfy.bigCasares.modules.sift.feature.SiftLootTable.Stack;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftLootTableTest {

    private static final SiftTemplates TEMPLATES = new SiftTemplates(SiftData.fromClasspath());

    private static SiftLootTable parse(String json) {
        return SiftLootTable.parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static SiftLootTable portalTable() {
        JsonObject json = TEMPLATES.lootTableJson(AbandonedMainPortalFeature.LOOT_TABLE).orElseThrow();
        return SiftLootTable.parse(json);
    }

    @Test
    void constantRollsAndSetCountProduceTheDeclaredStacks() {
        SiftLootTable table = parse("""
            {"pools":[{"rolls":3,"entries":[{"type":"minecraft:item","name":"minecraft:emerald",
              "modifier":[{"type":"minecraft:set_count","count":2}]}]}]}""");
        assertEquals(List.of(new Stack("minecraft:emerald", 2), new Stack("minecraft:emerald", 2),
            new Stack("minecraft:emerald", 2)), table.roll(new Random(1)));
        assertTrue(table.unsupported().isEmpty());
    }

    @Test
    void uniformRollsAndCountsCoverTheirWholeInclusiveRange() {
        SiftLootTable table = parse("""
            {"pools":[{"rolls":{"type":"minecraft:uniform","min":4,"max":7},"entries":[
              {"type":"minecraft:item","name":"minecraft:diamond",
               "functions":[{"type":"minecraft:set_count","count":{"type":"minecraft:uniform","min":1,"max":3}}]}]}]}""");
        Random random = new Random(7);
        Set<Integer> rolls = new HashSet<>();
        Set<Integer> counts = new HashSet<>();
        for (int run = 0; run < 400; run++) {
            List<Stack> stacks = table.roll(random);
            rolls.add(stacks.size());
            stacks.forEach(stack -> counts.add(stack.count()));
        }
        assertEquals(Set.of(4, 5, 6, 7), rolls);
        assertEquals(Set.of(1, 2, 3), counts);
    }

    @Test
    void entriesAreChosenInProportionToTheirWeight() {
        SiftLootTable table = parse("""
            {"pools":[{"rolls":1,"entries":[
              {"type":"minecraft:item","name":"minecraft:stick","weight":1},
              {"type":"minecraft:item","name":"minecraft:stone","weight":3},
              {"type":"minecraft:item","name":"minecraft:dirt","weight":0}]}]}""");
        Random random = new Random(11);
        Map<String, Integer> seen = new HashMap<>();
        int runs = 40_000;
        for (int run = 0; run < runs; run++) {
            seen.merge(table.roll(random).getFirst().item(), 1, Integer::sum);
        }
        assertEquals(0.25, seen.get("minecraft:stick") / (double) runs, 0.02);
        assertEquals(0.75, seen.get("minecraft:stone") / (double) runs, 0.02);
        assertFalse(seen.containsKey("minecraft:dirt"), "zero-weight entries are never chosen");
    }

    @Test
    void randomChanceGatesPoolsAndEntries() {
        SiftLootTable never = parse("""
            {"pools":[{"rolls":1,"condition":{"type":"minecraft:random_chance","chance":0.0},
              "entries":[{"type":"minecraft:item","name":"minecraft:stick"}]}]}""");
        SiftLootTable always = parse("""
            {"pools":[{"rolls":1,"conditions":[{"type":"minecraft:random_chance","chance":1.0}],
              "entries":[{"type":"minecraft:item","name":"minecraft:stick"}]}]}""");
        SiftLootTable half = parse("""
            {"pools":[{"rolls":1,"condition":{"type":"minecraft:random_chance","chance":0.5},
              "entries":[{"type":"minecraft:item","name":"minecraft:stick"}]}]}""");
        Random random = new Random(3);
        int hits = 0;
        for (int run = 0; run < 20_000; run++) {
            assertTrue(never.roll(random).isEmpty());
            assertEquals(1, always.roll(random).size());
            hits += half.roll(random).size();
        }
        assertEquals(0.5, hits / 20_000.0, 0.02);
    }

    @Test
    void alternativesUseTheFirstChildWhoseConditionsPass() {
        String json = """
            {"pools":[{"rolls":1,"entries":[{"type":"minecraft:alternatives","children":[
              {"type":"minecraft:item","name":"minecraft:gold_nugget","condition":{"type":"minecraft:random_chance","chance":%s},
               "modifier":[{"type":"minecraft:set_count","count":2}]},
              {"type":"minecraft:item","name":"minecraft:iron_nugget"}]}]}]}""";
        assertEquals(List.of(new Stack("minecraft:gold_nugget", 2)), parse(json.formatted("1.0")).roll(new Random(5)));
        assertEquals(List.of(new Stack("minecraft:iron_nugget", 1)), parse(json.formatted("0.0")).roll(new Random(5)));
    }

    @Test
    void unsupportedPartsAreSkippedAndReported() {
        SiftLootTable table = parse("""
            {"pools":[
              {"rolls":1,"entries":[
                {"type":"minecraft:tag","name":"minecraft:logs","expand":true},
                {"type":"minecraft:item","name":"minecraft:stick",
                 "modifier":[{"type":"minecraft:enchant_randomly"},{"type":"minecraft:set_count","count":4}]}]},
              {"rolls":1,"condition":{"type":"minecraft:time_check","value":1},
               "entries":[{"type":"minecraft:item","name":"minecraft:dirt"}]},
              {"rolls":{"type":"minecraft:binomial","n":3,"p":0.5},
               "entries":[{"type":"minecraft:item","name":"minecraft:stone"}]}]}""");
        assertEquals(List.of(new Stack("minecraft:stick", 4)), table.roll(new Random(2)));
        String report = String.join("\n", table.unsupported());
        assertTrue(report.contains("entry type minecraft:tag"), report);
        assertTrue(report.contains("function minecraft:enchant_randomly"), report);
        assertTrue(report.contains("condition minecraft:time_check"), report);
        assertTrue(report.contains("amount provider minecraft:binomial"), report);
    }

    @Test
    void theAbandonedPortalTableIsFullySupportedAndRollsItsDocumentedShape() {
        SiftLootTable table = portalTable();
        assertEquals(List.of(), table.unsupported());
        Random random = new Random(99);
        int discs = 0;
        int templates = 0;
        int nuggets = 0;
        int runs = 4000;
        for (int run = 0; run < runs; run++) {
            List<Stack> stacks = table.roll(random);
            // 4-7 general + 1-2 rare, plus the three optional single-roll pools.
            assertTrue(stacks.size() >= 5 && stacks.size() <= 12, "unexpected stack count " + stacks.size());
            for (Stack stack : stacks) {
                assertTrue(stack.count() >= 1 && stack.count() <= 7, stack.toString());
                if (stack.item().equals("the_sift:music_disc_rift")) {
                    discs++;
                } else if (stack.item().equals("the_sift:siftite_upgrade_smithing_template")) {
                    templates++;
                } else if (stack.item().equals("the_sift:siftite_nugget")) {
                    nuggets++;
                    assertTrue(stack.count() == 1 || stack.count() == 2);
                }
            }
        }
        assertEquals(0.5, discs / (double) runs, 0.04);
        assertEquals(0.4, templates / (double) runs, 0.04);
        assertEquals(0.2, nuggets / (double) runs, 0.04);
    }

    @Test
    void rollingIsDeterministicPerRandom() {
        SiftLootTable table = portalTable();
        assertEquals(table.roll(new Random(5)), table.roll(new Random(5)));
        assertFalse(table.roll(new Random(5)).equals(table.roll(new Random(6))));
    }

    @Test
    void distributionUsesDistinctSlotsKeepsEveryItemAndSplitsStacks() {
        List<Stack> stacks = List.of(new Stack("minecraft:emerald", 6), new Stack("minecraft:diamond", 1),
            new Stack("minecraft:stick", 5));
        for (long seed = 0; seed < 50; seed++) {
            List<WorldRegion.ItemSpec> specs = SiftLootTable.distribute(stacks, 27, new Random(seed));
            Set<Integer> slots = new HashSet<>();
            Map<String, Integer> totals = new HashMap<>();
            for (WorldRegion.ItemSpec spec : specs) {
                assertTrue(slots.add(spec.slot()), "slot used twice");
                assertTrue(spec.slot() >= 0 && spec.slot() < 27);
                totals.merge(spec.item(), spec.count(), Integer::sum);
            }
            assertEquals(Map.of("minecraft:emerald", 6, "minecraft:diamond", 1, "minecraft:stick", 5), totals);
            assertTrue(specs.size() > stacks.size(), "free slots are used to split multi-item stacks");
        }
    }

    @Test
    void distributionNeverOverfillsASmallContainer() {
        List<Stack> stacks = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            stacks.add(new Stack("minecraft:stick", 1 + index));
        }
        List<WorldRegion.ItemSpec> specs = SiftLootTable.distribute(stacks, 3, new Random(1));
        assertEquals(3, specs.size());
        assertEquals(3, specs.stream().map(WorldRegion.ItemSpec::slot).distinct().count());
    }

    @Test
    void everyItemOfTheTableResolvesToAContentItemOrAValidVanillaItem() {
        JsonObject json = TEMPLATES.lootTableJson(AbandonedMainPortalFeature.LOOT_TABLE).orElseThrow();
        Set<String> names = new HashSet<>();
        collectNames(json, names);
        assertTrue(names.size() >= 20, "expected the table's items, found " + names);
        Set<String> dropped = new HashSet<>();
        for (String name : names) {
            Optional<String> resolved = SiftLootItems.resolve(name);
            if (resolved.isEmpty()) {
                dropped.add(name);
                continue;
            }
            String id = resolved.get();
            if (id.startsWith("the_sift:")) {
                assertTrue(SiftLootItems.isContentItem(id), name + " -> " + id + " is not a content item");
            } else {
                assertNotNull(Material.matchMaterial(id), name + " -> " + id + " is not a vanilla item");
            }
        }
        assertEquals(Set.of(), dropped);
    }

    @Test
    void itemMappingFollowsTheDocumentedRules() {
        assertEquals(Optional.of("minecraft:diamond"), SiftLootItems.resolve("minecraft:diamond"));
        assertEquals(Optional.of("the_sift:charoite"), SiftLootItems.resolve("the_sift:charoite"));
        assertEquals(Optional.of("the_sift:siftslate_growth"), SiftLootItems.resolve("the_sift:siftslate_growth"));
        assertEquals(Optional.of("the_sift:overgrown_willow_log"), SiftLootItems.resolve("the_sift:overgrown_willow_log"));
        assertEquals(Optional.of("the_sift:overgrown_fronds"), SiftLootItems.resolve("the_sift:overgrown_fronds"));
        assertEquals(Optional.of("the_sift:overgrown_chard"), SiftLootItems.resolve("the_sift:overgrown_chard"));
        assertEquals(Optional.of("the_sift:overgrown_stalks"), SiftLootItems.resolve("the_sift:overgrown_stalks"));
        assertEquals(Optional.of("the_sift:siftslate_stalks"), SiftLootItems.resolve("the_sift:siftslate_stalks"));
        assertEquals(Optional.of("the_sift:healthy_sculk_sprouts"), SiftLootItems.resolve("the_sift:healthy_sculk_sprouts"));
        assertEquals(Optional.of("the_sift:dry_healthy_sculk_sprouts"), SiftLootItems.resolve("the_sift:dry_healthy_sculk_sprouts"));
        assertEquals(Optional.of("the_sift:overgrown_lotus"), SiftLootItems.resolve("the_sift:overgrown_lotus"));
        assertEquals(Optional.of("the_sift:sunburst_plant"), SiftLootItems.resolve("the_sift:sunburst_plant"));
        assertEquals(Optional.of("the_sift:whisperbloom"), SiftLootItems.resolve("the_sift:whisperbloom"));
        // The stand-in disc is a content item now, so it keeps its id.
        assertEquals(Optional.of("the_sift:music_disc_rift"), SiftLootItems.resolve("the_sift:music_disc_rift"));
        assertTrue(SiftLootItems.REPLACEMENTS.isEmpty(), "every mod item of the loot tables is a content item or a plant");
        assertEquals(Optional.of("the_sift:siftite_upgrade_smithing_template"),
            SiftLootItems.resolve("the_sift:siftite_upgrade_smithing_template"));
        assertEquals(Optional.empty(), SiftLootItems.resolve("the_sift:not_a_real_item"));
    }

    private static void collectNames(com.google.gson.JsonElement element, Set<String> names) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("name") && object.get("name").isJsonPrimitive()) {
                names.add(object.get("name").getAsString());
            }
            object.entrySet().forEach(entry -> collectNames(entry.getValue(), names));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectNames(child, names));
        }
    }
}
