package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The mod's sounds.json is for 26.3 and may name vanilla sound events a 26.2 client does not have; such an event
 * plays nothing (found with a real client: the Echo Golem's pickup sound). Every vanilla event the Sift's sounds
 * reuse is listed here after being checked against the 26.2 client's sounds.json; a new one needs the same check.
 */
class SiftSoundEventsTest {

    private static final Set<String> CHECKED_ON_26_2 = Set.of(
        "minecraft:block.portal.ambient", "minecraft:block.portal.trigger", "minecraft:block.sculk_catalyst.bloom",
        "minecraft:entity.axolotl.attack", "minecraft:entity.axolotl.death", "minecraft:entity.axolotl.hurt",
        "minecraft:entity.axolotl.idle_air", "minecraft:entity.axolotl.idle_water", "minecraft:entity.axolotl.splash",
        "minecraft:entity.axolotl.swim", "minecraft:entity.copper_golem.death", "minecraft:entity.copper_golem.hurt",
        "minecraft:entity.copper_golem.item_drop", "minecraft:entity.copper_golem.no_item_get",
        "minecraft:entity.copper_golem.spawn", "minecraft:entity.copper_golem.step", "minecraft:entity.evoker_fangs.attack",
        "minecraft:entity.parrot.ambient", "minecraft:entity.parrot.death", "minecraft:entity.parrot.hurt",
        "minecraft:entity.parrot.step", "minecraft:entity.sniffer.sniffing", "minecraft:entity.warden.dig",
        "minecraft:entity.warden.emerge", "minecraft:item.armor.equip_netherite", "minecraft:item.bottle.empty",
        "minecraft:item.bottle.fill", "minecraft:item.bucket.empty", "minecraft:item.bucket.fill");

    @Test
    void everyReusedVanillaSoundEventExistsOn26Point2() throws IOException {
        JsonObject sounds = JsonParser.parseString(Files.readString(
            Path.of("resourcepack/java/assets/the_sift/sounds.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        Set<String> unchecked = new TreeSet<>();
        for (String event : sounds.keySet()) {
            for (JsonElement sound : sounds.getAsJsonObject(event).getAsJsonArray("sounds")) {
                if (!sound.isJsonObject() || !"event".equals(textOf(sound.getAsJsonObject(), "type"))) {
                    continue;
                }
                String name = textOf(sound.getAsJsonObject(), "name");
                if (name.startsWith("minecraft:") && !CHECKED_ON_26_2.contains(name)) {
                    unchecked.add(event + " -> " + name);
                }
            }
        }
        assertEquals(Set.of(), unchecked);
    }

    private static String textOf(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }
}
