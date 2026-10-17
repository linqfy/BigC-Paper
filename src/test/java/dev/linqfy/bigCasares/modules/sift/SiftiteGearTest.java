package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.content.ContentItem;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftiteGearTest {

    private static final Path ASSETS = Path.of("resourcepack/java/assets/the_sift");

    private static Map<SiftiteGear.Attr, SiftiteGear.Modifier> byAttribute(SiftiteGear gear) {
        Map<SiftiteGear.Attr, SiftiteGear.Modifier> modifiers = new EnumMap<>(SiftiteGear.Attr.class);
        gear.modifiers().forEach(modifier -> assertNull(modifiers.put(modifier.attribute(), modifier), "duplicate " + modifier));
        return modifiers;
    }

    @Test
    void toolMaterialIsNetheriteWithTheModsChanges() {
        assertEquals(2031 + 470, SiftiteGear.TOOL_DURABILITY);
        assertEquals(2501, SiftiteGear.TOOL_DURABILITY);
        assertEquals(9.0F * 1.2F, SiftiteGear.TOOL_SPEED);
        assertEquals(10.8F, SiftiteGear.TOOL_SPEED, 1.0e-5F);
        assertEquals(5.0F, SiftiteGear.TOOL_ATTACK_DAMAGE_BONUS);
        assertEquals(15, SiftiteGear.ENCHANTMENT_VALUE);
    }

    @Test
    void everyToolHasTheVanillaFormulaDamageAndSpeed() {
        // attack damage = baseline + bonus (5.0), attack speed = baseline; the player's own 1 and 4 are added by the game
        assertEquals(8.0, SiftiteGear.SWORD.attackDamageModifier());
        assertEquals((double) -2.4F, SiftiteGear.SWORD.attackSpeedModifier());
        assertEquals(6.5, SiftiteGear.SHOVEL.attackDamageModifier());
        assertEquals(-3.0, SiftiteGear.SHOVEL.attackSpeedModifier());
        assertEquals(6.0, SiftiteGear.PICKAXE.attackDamageModifier());
        assertEquals((double) -2.8F, SiftiteGear.PICKAXE.attackSpeedModifier());
        assertEquals(10.0, SiftiteGear.AXE.attackDamageModifier());
        assertEquals(-3.0, SiftiteGear.AXE.attackSpeedModifier());
        assertEquals(1.0, SiftiteGear.HOE.attackDamageModifier());
        assertEquals(0.0, SiftiteGear.HOE.attackSpeedModifier());
        // the spear: the bonus alone, speed from the swing time
        assertEquals(5.0, SiftiteGear.SPEAR.attackDamageModifier());
        assertEquals(1.0F / 1.25F - 4.0F, SiftiteGear.SPEAR.attackSpeedModifier(), 1.0e-6);
        assertEquals(-3.2, SiftiteGear.SPEAR.attackSpeedModifier(), 1.0e-6);
    }

    @Test
    void toolModifiersUseVanillasBaseIdsOnTheMainHand() {
        for (SiftiteGear gear : SiftiteGear.values()) {
            if (gear.kind().isArmor()) {
                continue;
            }
            Map<SiftiteGear.Attr, SiftiteGear.Modifier> modifiers = byAttribute(gear);
            assertEquals(Set.of(SiftiteGear.Attr.ATTACK_DAMAGE, SiftiteGear.Attr.ATTACK_SPEED), modifiers.keySet(), gear.name());
            assertEquals("minecraft:base_attack_damage", modifiers.get(SiftiteGear.Attr.ATTACK_DAMAGE).key());
            assertEquals("minecraft:base_attack_speed", modifiers.get(SiftiteGear.Attr.ATTACK_SPEED).key());
            modifiers.values().forEach(modifier -> assertEquals(SiftiteGear.Slot.MAINHAND, modifier.slot()));
            assertEquals(gear.attackDamageModifier(), modifiers.get(SiftiteGear.Attr.ATTACK_DAMAGE).amount());
            assertEquals(gear.attackSpeedModifier(), modifiers.get(SiftiteGear.Attr.ATTACK_SPEED).amount());
            assertEquals(SiftiteGear.TOOL_DURABILITY, gear.durability(), gear.name());
        }
    }

    @Test
    void miningToolsMineAtTheMaterialSpeedAndOthersDoNot() {
        Map<SiftiteGear, String> tags = Map.of(
            SiftiteGear.SHOVEL, "minecraft:mineable/shovel",
            SiftiteGear.PICKAXE, "minecraft:mineable/pickaxe",
            SiftiteGear.AXE, "minecraft:mineable/axe",
            SiftiteGear.HOE, "minecraft:mineable/hoe");
        for (SiftiteGear gear : SiftiteGear.values()) {
            String mineable = tags.get(gear);
            if (mineable == null) {
                assertEquals(List.of(), gear.toolRules(), gear.name());
                assertFalse(gear.hasToolRules());
                continue;
            }
            assertEquals(List.of(
                new SiftiteGear.ToolRule("minecraft:incorrect_for_netherite_tool", null, false),
                new SiftiteGear.ToolRule(mineable, SiftiteGear.TOOL_SPEED, true)), gear.toolRules(), gear.name());
            assertTrue(gear.hasToolRules());
        }
    }

    @Test
    void armourDurabilityAndDefenceFollowTheSlots() {
        assertEquals(41, SiftiteGear.ARMOR_DURABILITY_FACTOR);
        assertEquals(11 * 41, SiftiteGear.HELMET.durability());
        assertEquals(451, SiftiteGear.HELMET.durability());
        assertEquals(656, SiftiteGear.CHESTPLATE.durability());
        assertEquals(615, SiftiteGear.LEGGINGS.durability());
        assertEquals(533, SiftiteGear.BOOTS.durability());
        assertEquals(3, SiftiteGear.HELMET.defense());
        assertEquals(8, SiftiteGear.CHESTPLATE.defense());
        assertEquals(6, SiftiteGear.LEGGINGS.defense());
        assertEquals(3, SiftiteGear.BOOTS.defense());
        assertEquals(20, SiftiteGear.HELMET.defense() + SiftiteGear.CHESTPLATE.defense()
            + SiftiteGear.LEGGINGS.defense() + SiftiteGear.BOOTS.defense());
    }

    @Test
    void armourModifiersAreKeyedBySlotAndKeepToughnessAndKnockbackResistance() {
        Map<SiftiteGear, SiftiteGear.Slot> slots = Map.of(
            SiftiteGear.HELMET, SiftiteGear.Slot.HEAD, SiftiteGear.CHESTPLATE, SiftiteGear.Slot.CHEST,
            SiftiteGear.LEGGINGS, SiftiteGear.Slot.LEGS, SiftiteGear.BOOTS, SiftiteGear.Slot.FEET);
        Map<SiftiteGear, String> names = Map.of(
            SiftiteGear.HELMET, "helmet", SiftiteGear.CHESTPLATE, "chestplate",
            SiftiteGear.LEGGINGS, "leggings", SiftiteGear.BOOTS, "boots");
        slots.forEach((gear, slot) -> {
            Map<SiftiteGear.Attr, SiftiteGear.Modifier> modifiers = byAttribute(gear);
            assertEquals(Set.of(SiftiteGear.Attr.ARMOR, SiftiteGear.Attr.ARMOR_TOUGHNESS, SiftiteGear.Attr.KNOCKBACK_RESISTANCE),
                modifiers.keySet(), gear.name());
            modifiers.values().forEach(modifier -> {
                assertEquals("minecraft:armor." + names.get(gear), modifier.key());
                assertEquals(slot, modifier.slot());
            });
            assertEquals((double) gear.defense(), modifiers.get(SiftiteGear.Attr.ARMOR).amount());
            assertEquals(3.0, modifiers.get(SiftiteGear.Attr.ARMOR_TOUGHNESS).amount());
            assertEquals((double) 0.15F, modifiers.get(SiftiteGear.Attr.KNOCKBACK_RESISTANCE).amount());
            assertEquals(slot, gear.slot());
            assertEquals(0.0, gear.attackDamageModifier());
            assertFalse(gear.hasToolRules());
        });
        assertEquals("the_sift:siftite", SiftiteGear.EQUIPMENT_ASSET);
        assertEquals("the_sift:item.siftite_armor.equip", SiftiteGear.EQUIP_SOUND);
    }

    @Test
    void theSpearUsesTheModsTimingsConvertedToTicks() {
        SiftiteGear.SpearStats spear = SiftiteGear.SPEAR.spear().orElseThrow();
        assertEquals(25, spear.swingTicks());
        assertEquals(1.2F, spear.damageMultiplier());
        assertEquals(8, spear.delayTicks());
        assertEquals(50, spear.dismountTicks());
        assertEquals(9.0F, spear.dismountMinSpeed());
        assertEquals(110, spear.knockbackTicks());
        assertEquals(5.1F, spear.knockbackMinSpeed());
        assertEquals(175, spear.damageTicks());
        assertEquals(4.6F, spear.damageMinRelativeSpeed());
        for (SiftiteGear gear : SiftiteGear.values()) {
            assertEquals(gear == SiftiteGear.SPEAR, gear.spear().isPresent(), gear.name());
        }
    }

    @Test
    void everyPieceIsBasedOnItsNetheriteCounterpartUnderTheModsId() {
        Map<SiftiteGear, String> expected = new EnumMap<>(SiftiteGear.class);
        expected.put(SiftiteGear.SWORD, "the_sift:siftite_sword=NETHERITE_SWORD");
        expected.put(SiftiteGear.SHOVEL, "the_sift:siftite_shovel=NETHERITE_SHOVEL");
        expected.put(SiftiteGear.PICKAXE, "the_sift:siftite_pickaxe=NETHERITE_PICKAXE");
        expected.put(SiftiteGear.AXE, "the_sift:siftite_axe=NETHERITE_AXE");
        expected.put(SiftiteGear.HOE, "the_sift:siftite_hoe=NETHERITE_HOE");
        expected.put(SiftiteGear.SPEAR, "the_sift:siftite_spear=NETHERITE_SPEAR");
        expected.put(SiftiteGear.HELMET, "the_sift:siftite_helmet=NETHERITE_HELMET");
        expected.put(SiftiteGear.CHESTPLATE, "the_sift:siftite_chestplate=NETHERITE_CHESTPLATE");
        expected.put(SiftiteGear.LEGGINGS, "the_sift:siftite_leggings=NETHERITE_LEGGINGS");
        expected.put(SiftiteGear.BOOTS, "the_sift:siftite_boots=NETHERITE_BOOTS");
        expected.forEach((gear, text) -> {
            assertEquals(text, gear.id() + "=" + gear.baseMaterial());
            assertNotNull(Material.matchMaterial(gear.baseMaterial()), gear.baseMaterial() + " is not a material");
        });
    }

    @Test
    void siftItemsRegistersTheGearAndTheTemplateAsContentItems() {
        Map<String, ContentItem> items = new java.util.LinkedHashMap<>();
        for (ContentItem item : SiftItems.items()) {
            assertNull(items.put(item.id().asString(), item), "duplicate " + item.id());
        }
        for (SiftiteGear gear : SiftiteGear.values()) {
            ContentItem item = items.get(gear.id());
            assertNotNull(item, gear.id());
            assertEquals(gear.baseMaterial(), item.material());
            assertEquals(1, item.maxStackSize(), "gear must not stack: " + gear.id());
            assertEquals(item.id(), item.itemModel());
            assertEquals("item.the_sift." + gear.path(), item.nameKey());
            assertNotNull(item.customizer(), "stats are applied through the customiser: " + gear.id());
        }
        ContentItem template = items.get("the_sift:siftite_upgrade_smithing_template");
        assertNotNull(template);
        assertEquals("NETHERITE_UPGRADE_SMITHING_TEMPLATE", template.material());
        assertEquals("item.the_sift.siftite_upgrade_smithing_template", template.nameKey());
        assertEquals("Siftite Upgrade", template.fallbackName());
        assertEquals(64, template.maxStackSize());
        assertNull(template.customizer());
        assertNotNull(Material.matchMaterial(template.material()));
        // The recovery protects exactly the ids of the mod's tag, all of which exist.
        assertTrue(items.keySet().containsAll(SiftiteKeep.PROTECTED_IDS), "tag item without a content item: "
            + new TreeSet<>(SiftiteKeep.PROTECTED_IDS).stream().filter(id -> !items.containsKey(id)).toList());
    }

    // --- assets ----------------------------------------------------------------------------------

    private static void collectModels(JsonElement element, List<String> models) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("model") && object.get("model").isJsonPrimitive()) {
                models.add(object.get("model").getAsString());
            }
            object.entrySet().forEach(entry -> collectModels(entry.getValue(), models));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectModels(child, models));
        }
    }

    private static JsonObject read(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), "missing " + file);
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static String pathOf(String id) {
        assertTrue(id.startsWith("the_sift:"), id);
        return id.substring("the_sift:".length());
    }

    @Test
    void everyReferencedModelAndTextureExistsInThePack() throws IOException {
        List<String> ids = new ArrayList<>();
        for (SiftiteGear gear : SiftiteGear.values()) {
            ids.add(gear.path());
        }
        ids.add("siftite_upgrade_smithing_template");
        int checkedModels = 0;
        for (String id : ids) {
            List<String> models = new ArrayList<>();
            collectModels(read(ASSETS.resolve("items/" + id + ".json")), models);
            assertFalse(models.isEmpty(), id + " has no model");
            for (String model : models) {
                if (!model.startsWith("the_sift:")) {
                    continue; // vanilla parents are not shipped
                }
                JsonObject json = read(ASSETS.resolve("models/" + pathOf(model) + ".json"));
                checkedModels++;
                JsonElement parent = json.get("parent");
                if (parent != null && parent.getAsString().startsWith("the_sift:")) {
                    assertTrue(Files.isRegularFile(ASSETS.resolve("models/" + pathOf(parent.getAsString()) + ".json")),
                        "missing parent of " + model);
                }
                JsonObject textures = json.has("textures") ? json.getAsJsonObject("textures") : new JsonObject();
                for (var texture : textures.entrySet()) {
                    String value = texture.getValue().getAsString();
                    if (value.startsWith("the_sift:")) {
                        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/" + pathOf(value) + ".png")),
                            "missing texture " + value + " of " + model);
                    }
                }
            }
            assertTrue(models.contains("the_sift:item/" + id) || models.stream().anyMatch(m -> m.startsWith("the_sift:item/" + id)),
                id + " must use its own models: " + models);
        }
        assertTrue(checkedModels >= 11, "expected the models of the pieces, checked " + checkedModels);
    }

    @Test
    void theEquipmentAssetAndItsArmourTexturesExist() throws IOException {
        JsonObject equipment = read(ASSETS.resolve("equipment/siftite.json")).getAsJsonObject("layers");
        for (String layer : List.of("humanoid", "humanoid_leggings")) {
            assertTrue(equipment.has(layer), layer);
            String texture = equipment.getAsJsonArray(layer).get(0).getAsJsonObject().get("texture").getAsString();
            assertEquals(SiftiteGear.EQUIPMENT_ASSET, texture);
            assertTrue(Files.isRegularFile(ASSETS.resolve("textures/entity/equipment/" + layer + "/" + pathOf(texture) + ".png")),
                "missing " + layer + " texture");
        }
    }

    @Test
    void theEquipSoundAndTheNamesAreInThePack() throws IOException {
        String sound = SiftiteGear.EQUIP_SOUND.substring("the_sift:".length());
        assertTrue(read(ASSETS.resolve("sounds.json")).has(sound), sound);
        JsonObject lang = read(ASSETS.resolve("lang/en_us.json"));
        for (SiftiteGear gear : SiftiteGear.values()) {
            assertTrue(lang.has("item.the_sift." + gear.path()), gear.path());
        }
        assertTrue(lang.has("item.the_sift.siftite_upgrade_smithing_template"));
    }
}
