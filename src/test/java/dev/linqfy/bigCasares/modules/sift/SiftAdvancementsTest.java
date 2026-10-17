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
        assertFalse(translated.containsKey("the_sift/friend_of_the_sift"), "the Echo Golem is not ported");
        assertFalse(translated.containsKey("the_sift/planting_the_dark"), "the Sculkflower is not ported");
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
        // blub -> friend_of_the_sift -> like_father_and_son (Echo Golem, not ported) -> root
        assertEquals("the_sift:the_sift/root", parent(translated, "the_sift/blub"));
        assertEquals("minecraft:story/smelt_iron", parent(translated, "story/fallen_civilization"));
        assertEquals("the_sift:story/song_of_the_past", parent(translated, "story/brave_the_unknown"));
        assertFalse(translated.get("the_sift/root").has("parent"));

        SiftSettings defaults = SiftSettings.defaults();
        SiftSettings noRitual = new SiftSettings(defaults.seedSalt(), defaults.worldBorderDiameter(), defaults.entryPermission(),
            defaults.travelCooldownSeconds(), defaults.craftedPortal(), false, defaults.tides(), defaults.ichor(),
            false, false, defaults.sifters(), List.of());
        Map<String, JsonObject> reduced = translate(noRitual);
        assertFalse(reduced.containsKey(SiftAdvancements.SONG_OF_THE_PAST));
        assertFalse(reduced.containsKey(SiftAdvancements.BLUB));
        assertFalse(reduced.containsKey(SiftAdvancements.RIFTER));
        assertEquals("the_sift:story/fallen_civilization", parent(reduced, "story/brave_the_unknown"));
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

    private static Map<String, JsonObject> translate(SiftSettings settings) {
        return SiftAdvancements.translate(DATA, SiftAdvancements.granted(settings), SiftItems.items());
    }

    private static String parent(Map<String, JsonObject> translated, String path) {
        return translated.get(path).get("parent").getAsString();
    }
}
