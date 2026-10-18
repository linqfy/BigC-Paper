package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftAdvancementsTest {

    private static final SiftData DATA = SiftData.fromClasspath();

    @Test
    void everyImportedAdvancementIsKnown() throws IOException, URISyntaxException {
        URL root = SiftAdvancementsTest.class.getClassLoader().getResource(SiftData.ROOT + "advancement");
        assertNotNull(root);
        Path directory = Path.of(root.toURI());
        Set<String> imported;
        try (Stream<Path> files = Files.walk(directory)) {
            imported = files.filter(file -> file.toString().endsWith(".json"))
                .map(file -> directory.relativize(file).toString().replace('\\', '/').replaceFirst("\\.json$", ""))
                .collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals(new TreeSet<>(SiftAdvancements.MOD_ADVANCEMENTS), imported);
    }

    @Test
    void onlyGrantableAdvancementsAreWritten() {
        Map<String, JsonObject> translated = translate(SiftSettings.defaults());
        assertEquals(SiftAdvancements.granted(SiftSettings.defaults()), translated.keySet());
        assertTrue(translated.containsKey("the_sift/friend_of_the_sift"), "the Echo Golem is ported");
        assertTrue(translated.containsKey(SiftAdvancements.PLANTING_THE_DARK), "the Sculkflower is ported");
        assertTrue(translated.containsKey(SiftAdvancements.FOOD_CHAIN), "the Dark Sniffer is ported");
        assertTrue(translated.containsKey(SiftAdvancements.SMELLS_CREEPY));
    }

    @Test
    void theDarkSnifferAdvancementsFollowTheCreatureAndCropSwitches() {
        SiftSettings defaults = SiftSettings.defaults();
        SiftSettings noDarkSniffers = new SiftSettings(defaults.seedSalt(), defaults.worldBorderDiameter(),
            defaults.entryPermission(), defaults.travelCooldownSeconds(), defaults.craftedPortal(),
            defaults.ancientCityRitual(), defaults.tides(), defaults.ichor(), true, true, true, true, true, false, true, true, List.of());
        Set<String> without = SiftAdvancements.granted(noDarkSniffers);
        assertFalse(without.contains(SiftAdvancements.SMELLS_CREEPY), "no source of seeds");
        assertFalse(without.contains(SiftAdvancements.FOOD_CHAIN));
        assertFalse(without.contains(SiftAdvancements.PLANTING_THE_DARK));

        SiftSettings noCrop = new SiftSettings(defaults.seedSalt(), defaults.worldBorderDiameter(),
            defaults.entryPermission(), defaults.travelCooldownSeconds(), defaults.craftedPortal(),
            defaults.ancientCityRitual(), defaults.tides(), defaults.ichor(), true, true, true, true, true, true, false, true, List.of());
        Set<String> noPlanting = SiftAdvancements.granted(noCrop);
        assertTrue(noPlanting.contains(SiftAdvancements.SMELLS_CREEPY));
        assertTrue(noPlanting.contains(SiftAdvancements.FOOD_CHAIN));
        assertFalse(noPlanting.contains(SiftAdvancements.PLANTING_THE_DARK));

        SiftSettings noBlubs = new SiftSettings(defaults.seedSalt(), defaults.worldBorderDiameter(),
            defaults.entryPermission(), defaults.travelCooldownSeconds(), defaults.craftedPortal(),
            defaults.ancientCityRitual(), defaults.tides(), defaults.ichor(), false, true, true, true, true, true, true, true, List.of());
        assertFalse(SiftAdvancements.granted(noBlubs).contains(SiftAdvancements.FOOD_CHAIN), "a tamed Blub kills the sniffer");
    }

    @Test
    void theDarkSnifferAdvancementsKeepTheirModsTriggersAndIcons() {
        Map<String, JsonObject> translated = translate(SiftSettings.defaults());
        // smells_creepy is the mod's inventory criterion on the seeds, matched by content id.
        JsonObject seeds = translated.get(SiftAdvancements.SMELLS_CREEPY).getAsJsonObject("criteria").getAsJsonObject("seeds");
        assertEquals("minecraft:inventory_changed", seeds.get("trigger").getAsString());
        JsonObject predicate = seeds.getAsJsonObject("conditions").getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("minecraft:torchflower_seeds", predicate.get("items").getAsString());
        assertEquals("{PublicBukkitValues:{\"bigcasares:content_id\":\"the_sift:sculkflower_seeds\"}}",
            predicate.getAsJsonObject("predicates").get("minecraft:custom_data").getAsString());
        assertEquals("minecraft:warped_fungus", translated.get(SiftAdvancements.PLANTING_THE_DARK)
            .getAsJsonObject("display").getAsJsonObject("icon").get("id").getAsString());
        // The spawn egg is a content item on the sniffer egg: the icon is that egg wearing the mod's item model.
        JsonObject egg = translated.get(SiftAdvancements.FOOD_CHAIN).getAsJsonObject("display").getAsJsonObject("icon");
        assertEquals("minecraft:sniffer_spawn_egg", egg.get("id").getAsString());
        assertEquals("the_sift:dark_sniffer_spawn_egg", egg.getAsJsonObject("components").get("minecraft:item_model").getAsString());
        assertEquals("the_sift:the_sift/blub", parent(translated, SiftAdvancements.FOOD_CHAIN));
        assertEquals("the_sift:the_sift/smells_creepy", parent(translated, SiftAdvancements.PLANTING_THE_DARK));
    }

    @Test
    void riftResonanceNeedsRiftsAndUsesTheStandInDiscAsItsIcon() {
        Map<String, JsonObject> translated = translate(SiftSettings.defaults());
        assertTrue(translated.containsKey(SiftAdvancements.RIFT_RESONANCE));
        assertTrue(SiftAdvancements.AWARDED_FROM_CODE.contains(SiftAdvancements.RIFT_RESONANCE));
        assertEquals("the_sift:the_sift/rifter", parent(translated, SiftAdvancements.RIFT_RESONANCE));
        JsonObject icon = translated.get(SiftAdvancements.RIFT_RESONANCE).getAsJsonObject("display").getAsJsonObject("icon");
        assertEquals("minecraft:" + SiftItems.RIFT_DISC_BASE.toLowerCase(java.util.Locale.ROOT), icon.get("id").getAsString());
        assertEquals("the_sift:music_disc_rift", icon.getAsJsonObject("components").get("minecraft:item_model").getAsString());

        SiftSettings defaults = SiftSettings.defaults();
        SiftSettings noRifts = new SiftSettings(defaults.seedSalt(), defaults.worldBorderDiameter(), defaults.entryPermission(),
            defaults.travelCooldownSeconds(), defaults.craftedPortal(), defaults.ancientCityRitual(), defaults.tides(),
            defaults.ichor(), defaults.blubs(), false, defaults.sifters(), defaults.singers(), defaults.echoGolems(),
            defaults.darkSniffers(), defaults.sculkflowers(), defaults.mainPortal(), List.of());
        assertFalse(SiftAdvancements.granted(noRifts).contains(SiftAdvancements.RIFT_RESONANCE));
        assertFalse(SiftAdvancements.granted(noRifts).contains(SiftAdvancements.RIFTER));
    }

    @Test
    void impossibleCriteriaAreExactlyTheOnesAwardedFromCode() {
        for (Map.Entry<String, JsonObject> entry : translate(SiftSettings.defaults()).entrySet()) {
            boolean impossible = entry.getValue().getAsJsonObject("criteria").entrySet().stream()
                .anyMatch(criterion -> criterion.getValue().getAsJsonObject().get("trigger").getAsString()
                    .equals("minecraft:impossible"));
            assertEquals(SiftAdvancements.AWARDED_FROM_CODE.contains(entry.getKey()), impossible, entry.getKey());
        }
    }

    @Test
    void childrenOfLeftOutAdvancementsHangFromTheNearestWrittenAncestor() {
        Map<String, JsonObject> translated = translate(SiftSettings.defaults());
        // root -> like_father_and_son -> friend_of_the_sift -> blub, pitch_perfect
        assertEquals("the_sift:the_sift/friend_of_the_sift", parent(translated, "the_sift/blub"));
        assertEquals("the_sift:the_sift/friend_of_the_sift", parent(translated, "the_sift/pitch_perfect"));
        assertEquals("the_sift:the_sift/like_father_and_son", parent(translated, "the_sift/friend_of_the_sift"));
        assertEquals("the_sift:the_sift/root", parent(translated, "the_sift/like_father_and_son"));
        assertEquals("minecraft:story/smelt_iron", parent(translated, "story/fallen_civilization"));
        assertEquals("the_sift:story/song_of_the_past", parent(translated, "story/brave_the_unknown"));
        assertFalse(translated.get("the_sift/root").has("parent"));

        SiftSettings defaults = SiftSettings.defaults();
        SiftSettings noRitual = new SiftSettings(defaults.seedSalt(), defaults.worldBorderDiameter(), defaults.entryPermission(),
            defaults.travelCooldownSeconds(), defaults.craftedPortal(), false, defaults.tides(), defaults.ichor(),
            false, false, defaults.sifters(), false, false, defaults.darkSniffers(), defaults.sculkflowers(),
            defaults.mainPortal(), List.of());
        Map<String, JsonObject> reduced = translate(noRitual);
        assertFalse(reduced.containsKey(SiftAdvancements.SONG_OF_THE_PAST));
        assertFalse(reduced.containsKey(SiftAdvancements.BLUB));
        assertFalse(reduced.containsKey(SiftAdvancements.RIFTER));
        assertFalse(reduced.containsKey(SiftAdvancements.FRIEND_OF_THE_SIFT));
        assertFalse(reduced.containsKey(SiftAdvancements.LIKE_FATHER_AND_SON));
        assertFalse(reduced.containsKey(SiftAdvancements.PITCH_PERFECT));
        assertEquals("the_sift:story/fallen_civilization", parent(reduced, "story/brave_the_unknown"));
    }

    @Test
    void creatureAdvancementsFollowTheirSwitches() {
        SiftSettings defaults = SiftSettings.defaults();
        SiftSettings golemsOnly = new SiftSettings(defaults.seedSalt(), defaults.worldBorderDiameter(), defaults.entryPermission(),
            defaults.travelCooldownSeconds(), defaults.craftedPortal(), true, defaults.tides(), defaults.ichor(), false, false,
            false, false, true, true, true, true, List.of());
        Set<String> golems = SiftAdvancements.granted(golemsOnly);
        assertTrue(golems.contains(SiftAdvancements.FRIEND_OF_THE_SIFT));
        assertFalse(golems.contains(SiftAdvancements.LIKE_FATHER_AND_SON), "needs a Singer to hand the soul to");
        assertFalse(golems.contains(SiftAdvancements.PITCH_PERFECT));
        // With no Singer the chain skips the missing step.
        assertEquals("the_sift:the_sift/root", parent(translate(golemsOnly), SiftAdvancements.FRIEND_OF_THE_SIFT));

        SiftSettings singersOnly = new SiftSettings(defaults.seedSalt(), defaults.worldBorderDiameter(), defaults.entryPermission(),
            defaults.travelCooldownSeconds(), defaults.craftedPortal(), true, defaults.tides(), defaults.ichor(), false, false,
            false, true, false, true, true, true, List.of());
        Set<String> singers = SiftAdvancements.granted(singersOnly);
        assertTrue(singers.contains(SiftAdvancements.PITCH_PERFECT));
        assertFalse(singers.contains(SiftAdvancements.FRIEND_OF_THE_SIFT));
        assertFalse(singers.contains(SiftAdvancements.LIKE_FATHER_AND_SON));
    }

    @Test
    void itemCriteriaMatchTheStoredContentId() {
        JsonObject predicate = translate(SiftSettings.defaults()).get("the_sift/blood_of_the_gods")
            .getAsJsonObject("criteria").getAsJsonObject("ichor_bucket").getAsJsonObject("conditions")
            .getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("minecraft:water_bucket", predicate.get("items").getAsString());
        // Where Paper keeps the persistent data container inside the custom_data component.
        assertEquals("{PublicBukkitValues:{\"bigcasares:content_id\":\"the_sift:ichor_bucket\"}}",
            predicate.getAsJsonObject("predicates").get("minecraft:custom_data").getAsString());
    }

    @Test
    void iconsAndTextsWorkOnVanillaClients() {
        for (Map.Entry<String, JsonObject> entry : translate(SiftSettings.defaults()).entrySet()) {
            JsonObject display = entry.getValue().getAsJsonObject("display");
            JsonObject icon = display.getAsJsonObject("icon");
            assertTrue(icon.get("id").getAsString().startsWith("minecraft:"), entry.getKey());
            if (icon.has("components")) {
                String model = icon.getAsJsonObject("components").get("minecraft:item_model").getAsString();
                String path = model.substring(model.indexOf(':') + 1);
                assertTrue(Files.isRegularFile(Path.of("resourcepack/java/assets/the_sift/items/" + path + ".json")),
                    entry.getKey() + " icon model " + model + " is not in the resource pack");
            }
            for (String text : List.of("title", "description")) {
                JsonElement fallback = display.getAsJsonObject(text).get("fallback");
                assertNotNull(fallback, entry.getKey() + " " + text);
                assertFalse(fallback.getAsString().isBlank());
            }
        }
        JsonObject title = translate(SiftSettings.defaults()).get(SiftAdvancements.KINDA_PURPLEISH)
            .getAsJsonObject("display").getAsJsonObject("title");
        assertEquals("advancement.the_sift.kinda_purpleish.title", title.get("translate").getAsString());
        assertEquals("Kinda Purpleish", title.get("fallback").getAsString());
    }

    @Test
    void locationCriteriaUseTheListOfLootConditionsThat26Point2Reads() {
        Map<String, JsonObject> translated = translate(SiftSettings.defaults());
        for (String path : List.of("story/fallen_civilization", "the_sift/certified_sifter")) {
            for (Map.Entry<String, JsonElement> criterion : translated.get(path).getAsJsonObject("criteria").entrySet()) {
                JsonElement player = criterion.getValue().getAsJsonObject().getAsJsonObject("conditions").get("player");
                assertTrue(player.isJsonArray(), path + " " + criterion.getKey());
                JsonObject condition = player.getAsJsonArray().get(0).getAsJsonObject();
                assertEquals("minecraft:entity_properties", condition.get("condition").getAsString());
                assertEquals("this", condition.get("entity").getAsString());
                assertFalse(condition.has("type"));
                assertTrue(condition.getAsJsonObject("predicate").has("minecraft:location"));
            }
        }
    }

    private static Map<String, JsonObject> translate(SiftSettings settings) {
        return SiftAdvancements.translate(DATA, SiftAdvancements.granted(settings), SiftItems.items());
    }

    private static String parent(Map<String, JsonObject> translated, String path) {
        return translated.get(path).get("parent").getAsString();
    }
}
