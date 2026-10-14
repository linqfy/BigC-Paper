package dev.linqfy.bigCasares.modules.cosmicniv;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmicNivServiceTest {
    private static final double EPSILON = 1e-9;
    private static final double MAX_ARMOR_POWER = 88.25;
    private static final double MAX_SWORD_POWER = 22.5;
    private static final double MAX_PICKAXE_POWER = 12.4;

    private static CosmicNivSettings bundledSettings() {
        return CosmicNivSettingsLoaderTest.bundledSettings();
    }

    private static CosmicNivService bundledService() {
        return new CosmicNivService(bundledSettings());
    }

    private static CosmicNivLoadout.Builder maxArmor(CosmicNivSettings settings) {
        CosmicNivLoadout.Builder builder = CosmicNivLoadout.builder();
        settings.maxReference().armor().forEach(builder::armor);
        return builder;
    }

    private static CosmicNivLoadout.Builder plainArmor(String material) {
        return CosmicNivLoadout.builder()
            .armor(CosmicNivArmorSlot.HELMET, CosmicNivItem.of(material + "_helmet"))
            .armor(CosmicNivArmorSlot.CHESTPLATE, CosmicNivItem.of(material + "_chestplate"))
            .armor(CosmicNivArmorSlot.LEGGINGS, CosmicNivItem.of(material + "_leggings"))
            .armor(CosmicNivArmorSlot.BOOTS, CosmicNivItem.of(material + "_boots"));
    }

    @Test
    void anEntityWithNothingIsLevelZero() {
        CosmicNivResult result = bundledService().evaluate(CosmicNivLoadout.empty());

        assertEquals(0.0, result.value());
        assertEquals(0.0, result.armorScore());
        assertEquals(0.0, result.weaponScore());
        assertEquals(0.0, result.toolScore());
        assertFalse(result.hasWeapon());
        assertFalse(result.hasTool());
        assertNull(result.weapon().family());
    }

    @Test
    void fullEnchantedNetheriteWithAnyFullEnchantedWeaponIsLevelTwoHundred() {
        CosmicNivSettings settings = bundledSettings();
        CosmicNivService service = new CosmicNivService(settings);

        for (Map.Entry<String, CosmicNivItem> weapon : settings.maxReference().weapons().entrySet()) {
            CosmicNivResult result = service.evaluate(maxArmor(settings).held(weapon.getValue()).build());

            assertEquals(200.0, result.value(), weapon.getKey());
            assertEquals(200.0, result.exactValue(), EPSILON, weapon.getKey());
            assertEquals(1.0, result.progress(), EPSILON, weapon.getKey());
            assertEquals(weapon.getKey(), result.weapon().family());
        }
    }

    @Test
    void armorAndWeaponSplitTheLevelByTheirWeights() {
        CosmicNivSettings settings = bundledSettings();
        CosmicNivService service = new CosmicNivService(settings);

        CosmicNivResult armorOnly = service.evaluate(maxArmor(settings).build());
        CosmicNivResult weaponOnly = service.evaluate(CosmicNivLoadout.builder()
            .held(settings.maxReference().weapons().get("trident")).build());

        assertEquals(120.0, armorOnly.value());
        assertEquals(MAX_ARMOR_POWER, armorOnly.armorPower(), EPSILON);
        assertEquals(80.0, weaponOnly.value());
    }

    @Test
    void toolsAreScoredButDoNotCountWhileTheirWeightIsZero() {
        CosmicNivSettings settings = bundledSettings();
        CosmicNivResult result = new CosmicNivService(settings).evaluate(maxArmor(settings)
            .held(settings.maxReference().tools().get("pickaxe")).build());

        assertTrue(result.hasTool());
        assertEquals("pickaxe", result.tool().family());
        assertEquals(1.0, result.toolScore(), EPSILON);
        assertEquals(120.0, result.value());
    }

    @Test
    void withToolWeightTheMaxNeedsArmorWeaponAndTool() {
        CosmicNivSettings settings = bundledSettings().withWeights(0.55, 0.35, 0.1);
        CosmicNivService service = new CosmicNivService(settings);
        CosmicNivItem sword = settings.maxReference().weapons().get("sword");

        assertEquals(180.0, service.evaluate(maxArmor(settings).held(sword).build()).value());
        for (CosmicNivItem tool : settings.maxReference().tools().values()) {
            assertEquals(200.0, service.evaluate(maxArmor(settings).held(sword).held(tool).build()).value(),
                tool.key());
        }
        assertEquals(71.5, service.evaluate(plainArmor("netherite")
            .held(CosmicNivItem.of("diamond_sword"))
            .held(CosmicNivItem.of("iron_pickaxe")).build()).value());
    }

    @Test
    void itemPowerAddsTheBaseValueAndEveryEnchantment() {
        CosmicNivService service = bundledService();

        assertEquals(8.0, service.itemPower(CosmicNivItem.of("netherite_sword")), EPSILON);
        assertEquals(MAX_SWORD_POWER, service.itemPower(bundledSettings().maxReference().weapons().get("sword")),
            EPSILON);
        assertEquals(MAX_PICKAXE_POWER, service.itemPower(bundledSettings().maxReference().tools().get("pickaxe")),
            EPSILON);
        assertEquals(0.0, service.itemPower(CosmicNivItem.of("stick", Map.of("sharpness", 5))), EPSILON);
    }

    @Test
    void itemsReportWhereTheyCount() {
        CosmicNivService service = bundledService();

        assertEquals(CosmicNivItemCategory.ARMOR, service.categoryOf(CosmicNivItem.of("iron_helmet")));
        assertEquals(CosmicNivItemCategory.WEAPON, service.categoryOf(CosmicNivItem.of("trident")));
        assertEquals(CosmicNivItemCategory.TOOL, service.categoryOf(CosmicNivItem.of("shears")));
        assertEquals(CosmicNivItemCategory.WEAPON, service.categoryOf(CosmicNivItem.of("custom:sahurs_bat")));
        assertNull(service.categoryOf(CosmicNivItem.of("stick")));
        assertEquals("trident", service.familyOf(CosmicNivItem.of("trident")));
        assertEquals("fishing_rod", service.familyOf(CosmicNivItem.of("fishing_rod")));
        assertEquals("sword", service.familyOf(CosmicNivItem.of("custom:sahurs_bat")));
        assertNull(service.familyOf(CosmicNivItem.of("iron_helmet")));
    }

    @Test
    void plainNetheriteArmorScoresItsShareOfTheMaxReference() {
        CosmicNivResult result = bundledService().evaluate(plainArmor("netherite").build());

        assertEquals(36.0, result.armorPower(), EPSILON);
        assertEquals(36.0 / MAX_ARMOR_POWER, result.armorScore(), EPSILON);
        assertEquals(49.0, result.value());
    }

    @Test
    void betterGearAlwaysScoresHigher() {
        CosmicNivService service = bundledService();
        double previous = -1.0;
        for (String material : List.of("leather", "golden", "chainmail", "iron", "diamond", "netherite")) {
            double value = service.evaluate(plainArmor(material).build()).exactValue();
            assertTrue(value > previous, material + " should beat the previous tier");
            previous = value;
        }
    }

    @Test
    void eachWeaponIsComparedWithItsOwnFamily() {
        CosmicNivResult sword = bundledService().evaluate(CosmicNivLoadout.builder()
            .held(CosmicNivItem.of("netherite_sword")).build());

        assertEquals("sword", sword.weapon().family());
        assertEquals(8.0 / MAX_SWORD_POWER, sword.weaponScore(), EPSILON);
    }

    @Test
    void theBestWeaponAndToolWinAndOtherItemsAreIgnored() {
        CosmicNivSettings settings = bundledSettings();
        CosmicNivResult result = new CosmicNivService(settings).evaluate(CosmicNivLoadout.builder()
            .held(CosmicNivItem.of("stick", Map.of("sharpness", 5)))
            .held(CosmicNivItem.of("iron_sword"))
            .held(settings.maxReference().weapons().get("mace"))
            .held(CosmicNivItem.of("wooden_axe"))
            .held(CosmicNivItem.of("stone_pickaxe"))
            .held(CosmicNivItem.of("diamond_shovel"))
            .held(CosmicNivItem.of("iron_helmet"))
            .build());

        assertEquals("mace", result.weapon().family());
        assertEquals("minecraft:mace", result.weapon().item().key());
        assertEquals(1.0, result.weaponScore(), EPSILON);
        assertEquals("minecraft:diamond_shovel", result.tool().item().key());

        CosmicNivResult onlyJunk = bundledService().evaluate(CosmicNivLoadout.builder()
            .held(CosmicNivItem.of("stick", Map.of("sharpness", 5)))
            .held(CosmicNivItem.of("iron_helmet")).build());
        assertFalse(onlyJunk.hasWeapon());
        assertFalse(onlyJunk.hasTool());
        assertEquals(0.0, onlyJunk.value());
    }

    @Test
    void weaponsAndToolsWornInAnArmorSlotDoNotCountAsArmor() {
        CosmicNivResult result = bundledService().evaluate(CosmicNivLoadout.builder()
            .armor(CosmicNivArmorSlot.HELMET, CosmicNivItem.of("netherite_sword", Map.of("sharpness", 5)))
            .armor(CosmicNivArmorSlot.BOOTS, CosmicNivItem.of("netherite_pickaxe"))
            .build());

        assertEquals(0.0, result.armorPower(), EPSILON);
        assertEquals(0.0, result.value());
    }

    @Test
    void enchantmentLevelsAboveTheConfiguredMaximumDoNotCountExtra() {
        CosmicNivService service = bundledService();

        assertEquals(
            service.itemPower(CosmicNivItem.of("netherite_sword", Map.of("sharpness", 5))),
            service.itemPower(CosmicNivItem.of("netherite_sword", Map.of("sharpness", 255))),
            EPSILON);
    }

    @Test
    void itemPowerDoesNotDependOnTheEnchantmentOrder() {
        CosmicNivService service = bundledService();
        LinkedHashMap<String, Integer> forward = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> backward = new LinkedHashMap<>();
        List<String> keys = List.of("sharpness", "fire_aspect", "knockback", "looting", "sweeping_edge",
            "unbreaking", "mending");
        for (String key : keys) {
            forward.put(key, 3);
        }
        for (int index = keys.size() - 1; index >= 0; index--) {
            backward.put(keys.get(index), 3);
        }

        assertEquals(service.itemPower(CosmicNivItem.of("netherite_sword", forward)),
            service.itemPower(CosmicNivItem.of("netherite_sword", backward)));
    }

    @Test
    void godItemsAboveTheMaxReferenceAreCappedAtTwoHundred() {
        CosmicNivSettings settings = bundledSettings();
        CosmicNivItem godSword = CosmicNivItem.of("netherite_sword", Map.of("sharpness", 5, "smite", 5,
            "fire_aspect", 2, "knockback", 2, "looting", 3, "sweeping_edge", 3, "unbreaking", 3, "mending", 1));

        CosmicNivResult result = new CosmicNivService(settings).evaluate(maxArmor(settings).held(godSword).build());

        assertEquals(200.0, result.value());
        assertEquals(1.0, result.weaponScore(), EPSILON);
    }

    @Test
    void cursesLowerThePowerButNeverBelowZero() {
        CosmicNivService service = bundledService();

        double cursed = service.itemPower(CosmicNivItem.of("leather_helmet", Map.of("binding_curse", 1)));
        CosmicNivResult result = service.evaluate(CosmicNivLoadout.builder()
            .armor(CosmicNivArmorSlot.HELMET, CosmicNivItem.of("leather_helmet", Map.of("binding_curse", 1)))
            .build());

        assertEquals(-1.0, cursed, EPSILON);
        assertEquals(0.0, result.armorScore(), EPSILON);
        assertEquals(0.0, result.value());
    }

    @Test
    void aCustomZeroReferenceMapsToZero() {
        EnumMap<CosmicNivArmorSlot, CosmicNivItem> leather = new EnumMap<>(CosmicNivArmorSlot.class);
        for (CosmicNivArmorSlot slot : CosmicNivArmorSlot.values()) {
            leather.put(slot, CosmicNivItem.of("leather_" + slot.configKey()));
        }
        CosmicNivSettings settings = bundledSettings()
            .withZeroReference(new CosmicNivReference(leather, Map.of(), Map.of()));
        CosmicNivService service = new CosmicNivService(settings);

        assertEquals(0.0, service.evaluate(plainArmor("leather").build()).value());
        assertEquals(0.0, service.evaluate(CosmicNivLoadout.empty()).value());
        assertEquals(120.0, service.evaluate(maxArmor(settings).build()).value());
        double iron = service.evaluate(plainArmor("iron").build()).armorScore();
        assertEquals((15.0 - 7.0) / (MAX_ARMOR_POWER - 7.0), iron, EPSILON);
    }

    @Test
    void theCurveBendsTheMiddleButKeepsTheEnds() {
        CosmicNivSettings linear = bundledSettings();
        CosmicNivCurve exponential = new CosmicNivCurve.Exponential(3.0);
        CosmicNivService service = new CosmicNivService(linear.withCurve(exponential));

        CosmicNivResult armorOnly = service.evaluate(maxArmor(linear).build());
        CosmicNivResult full = service.evaluate(maxArmor(linear)
            .held(linear.maxReference().weapons().get("sword")).build());

        assertEquals(0.6, armorOnly.progress(), EPSILON);
        assertEquals(exponential.apply(0.6), armorOnly.curvedProgress(), EPSILON);
        assertEquals(200.0 * exponential.apply(0.6), armorOnly.exactValue(), EPSILON);
        assertEquals(CosmicNivCurveType.EXPONENTIAL, armorOnly.curveType());
        assertEquals(0.0, service.evaluate(CosmicNivLoadout.empty()).value());
        assertEquals(200.0, full.value());
    }

    @Test
    void theOutputIsRoundedToTheConfiguredDecimals() {
        CosmicNivService whole = new CosmicNivService(bundledSettings().withDecimals(0));
        CosmicNivService precise = new CosmicNivService(bundledSettings().withDecimals(3));

        assertEquals(49.0, whole.evaluate(plainArmor("netherite").build()).value());
        assertEquals(48.952, precise.evaluate(plainArmor("netherite").build()).value());
    }

    @Test
    void theExamplesInTheUserGuideMatchTheBundledDefaults() {
        CosmicNivSettings settings = bundledSettings();
        CosmicNivService service = new CosmicNivService(settings);

        assertEquals(9.5, service.evaluate(plainArmor("leather").build()).value());
        assertEquals(20.4, service.evaluate(plainArmor("iron").build()).value());
        assertEquals(38.1, service.evaluate(plainArmor("diamond").build()).value());
        assertEquals(49.0, service.evaluate(plainArmor("netherite").build()).value());
        assertEquals(73.8, service.evaluate(plainArmor("netherite")
            .held(CosmicNivItem.of("diamond_sword")).build()).value());
        assertEquals(148.4, service.evaluate(maxArmor(settings)
            .held(CosmicNivItem.of("netherite_sword")).build()).value());
        assertEquals(32.0, service.evaluate(CosmicNivLoadout.builder()
            .held(CosmicNivItem.of("custom:sahurs_bat")).build()).value());
        assertEquals(22.4, service.itemPower(settings.maxReference().weapons().get("axe")), EPSILON);
        assertEquals(18.5, service.itemPower(settings.maxReference().weapons().get("mace")), EPSILON);
        assertEquals(23.0, service.itemPower(settings.maxReference().weapons().get("spear")), EPSILON);
        assertEquals(19.0, service.itemPower(settings.maxReference().weapons().get("trident")), EPSILON);
        assertEquals(12.4, service.itemPower(settings.maxReference().tools().get("shovel")), EPSILON);
        assertEquals(12.4, service.itemPower(settings.maxReference().tools().get("hoe")), EPSILON);
        assertEquals(8.5, service.itemPower(settings.maxReference().tools().get("shears")), EPSILON);
        assertEquals(8.5, service.itemPower(settings.maxReference().tools().get("fishing_rod")), EPSILON);
    }

    @Test
    void handBuiltSettingsOutsideTheLimitsAreRejected() {
        CosmicNivSettings settings = bundledSettings();
        Map<String, Double> armor = new LinkedHashMap<>(settings.armorValues());
        armor.put("minecraft:netherite_helmet", 1e308);
        CosmicNivSettings huge = new CosmicNivSettings(settings.maximum(), settings.decimals(), settings.armorWeight(),
            settings.weaponWeight(), settings.toolWeight(), settings.search(), settings.curve(),
            settings.zeroReference(), settings.maxReference(), armor, settings.weaponFamilies(),
            settings.toolFamilies(), settings.enchantmentValues(), settings.customItems(), settings.memory());

        assertTrue(String.join("\n", CosmicNivService.problems(huge)).contains("armor.minecraft:netherite_helmet"));
    }

    @Test
    void aPartWithoutWeightNeverAffectsTheLevel() {
        CosmicNivSettings settings = bundledSettings();
        CosmicNivSettings noTools = new CosmicNivSettings(settings.maximum(), settings.decimals(),
            settings.armorWeight(), settings.weaponWeight(), 0.0, settings.search(), settings.curve(),
            settings.zeroReference(), new CosmicNivReference(settings.maxReference().armor(),
                settings.maxReference().weapons(), Map.of()),
            settings.armorValues(), settings.weaponFamilies(), settings.toolFamilies(), settings.enchantmentValues(),
            settings.customItems(), settings.memory());
        CosmicNivService service = new CosmicNivService(noTools);

        CosmicNivResult result = service.evaluate(maxArmor(noTools)
            .held(noTools.maxReference().weapons().get("sword"))
            .held(CosmicNivItem.of("netherite_pickaxe")).build());

        assertEquals(200.0, result.value());
        assertEquals(0.0, result.toolScore());
    }

    @Test
    void theBestPieceOfEachSlotCountsAmongWornAndCarriedArmor() {
        CosmicNivSettings settings = bundledSettings();
        CosmicNivItem enchantedDiamond = CosmicNivItem.of("diamond_chestplate", Map.of("protection", 4));
        CosmicNivResult result = new CosmicNivService(settings).evaluate(CosmicNivLoadout.builder()
            .armor(CosmicNivArmorSlot.HELMET, CosmicNivItem.of("leather_helmet"))
            .armor(CosmicNivArmorSlot.HELMET, CosmicNivItem.of("netherite_helmet"))
            .armor(CosmicNivArmorSlot.HELMET, CosmicNivItem.of("iron_helmet"))
            .armor(CosmicNivArmorSlot.CHESTPLATE, CosmicNivItem.of("netherite_chestplate"))
            .armor(CosmicNivArmorSlot.CHESTPLATE, enchantedDiamond)
            .armor(CosmicNivArmorSlot.BOOTS, CosmicNivItem.of("netherite_sword"))
            .build());

        assertEquals("minecraft:netherite_helmet", result.armorPieces().get(CosmicNivArmorSlot.HELMET).key());
        assertEquals(enchantedDiamond, result.armorPieces().get(CosmicNivArmorSlot.CHESTPLATE));
        assertNull(result.armorPieces().get(CosmicNivArmorSlot.BOOTS));
        assertEquals(7.0 + 16.0, result.armorPower(), EPSILON);
    }

    @Test
    void weaponsAndToolsAreSearchedInTheirOwnLists() {
        CosmicNivSettings settings = bundledSettings();
        CosmicNivResult result = new CosmicNivService(settings).evaluate(CosmicNivLoadout.builder()
            .weapon(CosmicNivItem.of("netherite_pickaxe"))
            .tool(CosmicNivItem.of("netherite_sword"))
            .tool(CosmicNivItem.of("iron_pickaxe"))
            .build());

        assertFalse(result.hasWeapon());
        assertEquals("minecraft:iron_pickaxe", result.tool().item().key());
    }

    @Test
    void aPieceOnlyCountsInItsOwnSlot() {
        CosmicNivSettings settings = bundledSettings();
        CosmicNivItem maxChestplate = settings.maxReference().armor().get(CosmicNivArmorSlot.CHESTPLATE);
        CosmicNivLoadout.Builder fourChestplates = CosmicNivLoadout.builder();
        for (CosmicNivArmorSlot slot : CosmicNivArmorSlot.values()) {
            fourChestplates.armor(slot, maxChestplate);
        }
        CosmicNivService service = new CosmicNivService(settings);

        CosmicNivResult result = service.evaluate(fourChestplates
            .held(settings.maxReference().weapons().get("sword")).build());

        assertEquals(1, result.armorPieces().size());
        assertEquals(service.itemPower(maxChestplate), result.armorPower(), EPSILON);
        assertTrue(result.value() < 200.0);
        assertEquals(CosmicNivArmorSlot.CHESTPLATE, service.armorSlotOf(maxChestplate));
    }

    @Test
    void twoHundredNeedsTheFullNetheriteKitNotJustAsManyPoints() {
        CosmicNivSettings settings = bundledSettings();
        CosmicNivService service = new CosmicNivService(settings);
        CosmicNivLoadout.Builder mixed = CosmicNivLoadout.builder();
        settings.maxReference().armor().forEach((slot, piece) -> {
            String material = slot == CosmicNivArmorSlot.CHESTPLATE ? "netherite" : "diamond";
            mixed.armor(slot, CosmicNivItem.of(material + "_" + slot.configKey(), piece.enchantments()));
        });

        CosmicNivResult result = service.evaluate(mixed.held(settings.maxReference().weapons().get("sword")).build());

        assertEquals(MAX_ARMOR_POWER - 3 * 2.0, result.armorPower(), EPSILON);
        assertTrue(result.value() < 200.0);
    }

    @Test
    void aMaximumThatCannotBeShownWithTheDecimalsIsRejected() {
        CosmicNivSettings settings = bundledSettings();
        CosmicNivSettings tooPrecise = new CosmicNivSettings(200.06, 1, settings.armorWeight(), settings.weaponWeight(),
            settings.toolWeight(), settings.search(), settings.curve(), settings.zeroReference(),
            settings.maxReference(), settings.armorValues(), settings.weaponFamilies(), settings.toolFamilies(),
            settings.enchantmentValues(), settings.customItems(), settings.memory());
        CosmicNivSettings tiny = new CosmicNivSettings(0.001, 0, settings.armorWeight(), settings.weaponWeight(),
            settings.toolWeight(), settings.search(), settings.curve(), settings.zeroReference(),
            settings.maxReference(), settings.armorValues(), settings.weaponFamilies(), settings.toolFamilies(),
            settings.enchantmentValues(), settings.customItems(), settings.memory());
        CosmicNivSettings fine = new CosmicNivSettings(200.1, 1, settings.armorWeight(), settings.weaponWeight(),
            settings.toolWeight(), settings.search(), settings.curve(), settings.zeroReference(),
            settings.maxReference(), settings.armorValues(), settings.weaponFamilies(), settings.toolFamilies(),
            settings.enchantmentValues(), settings.customItems(), settings.memory());

        assertTrue(String.join("\n", CosmicNivService.problems(tooPrecise)).contains("output.maximum"));
        assertTrue(String.join("\n", CosmicNivService.problems(tiny)).contains("output.maximum"));
        CosmicNivService service = new CosmicNivService(fine);
        assertEquals(200.1, service.evaluate(maxArmor(fine).held(fine.maxReference().weapons().get("sword")).build())
            .value());
    }

    @Test
    void invalidSettingsCannotBuildAService() {
        CosmicNivSettings settings = bundledSettings();
        CosmicNivSettings broken = settings.withZeroReference(new CosmicNivReference(
            settings.maxReference().armor(), Map.of(), Map.of()));

        assertFalse(CosmicNivService.problems(broken).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivService(broken));
    }
}
