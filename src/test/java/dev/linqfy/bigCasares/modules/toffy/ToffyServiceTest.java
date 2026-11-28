package dev.linqfy.bigCasares.modules.toffy;

import org.bukkit.configuration.file.YamlConfiguration;
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

class ToffyServiceTest {
    private static final double EPSILON = 1e-9;
    private static final double MAX_ARMOR_POWER = 88.25;
    private static final double MAX_SWORD_POWER = 22.5;
    private static final double MAX_PICKAXE_POWER = 12.4;

    private static ToffySettings bundledSettings() {
        return ToffySettingsLoaderTest.bundledSettings();
    }

    private static ToffyService bundledService() {
        return new ToffyService(bundledSettings());
    }

    private static ToffyLoadout.Builder maxArmor(ToffySettings settings) {
        ToffyLoadout.Builder builder = ToffyLoadout.builder();
        settings.maxReference().armor().forEach(builder::armor);
        return builder;
    }

    private static ToffyLoadout.Builder plainArmor(String material) {
        return ToffyLoadout.builder()
            .armor(ToffyArmorSlot.HELMET, ToffyItem.of(material + "_helmet"))
            .armor(ToffyArmorSlot.CHESTPLATE, ToffyItem.of(material + "_chestplate"))
            .armor(ToffyArmorSlot.LEGGINGS, ToffyItem.of(material + "_leggings"))
            .armor(ToffyArmorSlot.BOOTS, ToffyItem.of(material + "_boots"));
    }

    @Test
    void anEntityWithNothingIsLevelZero() {
        ToffyResult result = bundledService().evaluate(ToffyLoadout.empty());

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
        ToffySettings settings = bundledSettings();
        ToffyService service = new ToffyService(settings);

        for (Map.Entry<String, ToffyItem> weapon : settings.maxReference().weapons().entrySet()) {
            ToffyResult result = service.evaluate(maxArmor(settings).held(weapon.getValue()).build());

            assertEquals(200.0, result.value(), weapon.getKey());
            assertEquals(200.0, result.exactValue(), EPSILON, weapon.getKey());
            assertEquals(1.0, result.progress(), EPSILON, weapon.getKey());
            assertEquals(weapon.getKey(), result.weapon().family());
        }
    }

    @Test
    void armorAndWeaponSplitTheLevelByTheirWeights() {
        ToffySettings settings = bundledSettings();
        ToffyService service = new ToffyService(settings);

        ToffyResult armorOnly = service.evaluate(maxArmor(settings).build());
        ToffyResult weaponOnly = service.evaluate(ToffyLoadout.builder()
            .held(settings.maxReference().weapons().get("trident")).build());

        assertEquals(120.0, armorOnly.value());
        assertEquals(MAX_ARMOR_POWER, armorOnly.armorPower(), EPSILON);
        assertEquals(80.0, weaponOnly.value());
    }

    @Test
    void toolsAreScoredButDoNotCountWhileTheirWeightIsZero() {
        ToffySettings settings = bundledSettings();
        ToffyResult result = new ToffyService(settings).evaluate(maxArmor(settings)
            .held(settings.maxReference().tools().get("pickaxe")).build());

        assertTrue(result.hasTool());
        assertEquals("pickaxe", result.tool().family());
        assertEquals(1.0, result.toolScore(), EPSILON);
        assertEquals(120.0, result.value());
    }

    @Test
    void withToolWeightTheMaxNeedsArmorWeaponAndTool() {
        ToffySettings settings = bundledSettings().withWeights(0.55, 0.35, 0.1);
        ToffyService service = new ToffyService(settings);
        ToffyItem sword = settings.maxReference().weapons().get("sword");

        assertEquals(180.0, service.evaluate(maxArmor(settings).held(sword).build()).value());
        for (ToffyItem tool : settings.maxReference().tools().values()) {
            assertEquals(200.0, service.evaluate(maxArmor(settings).held(sword).held(tool).build()).value(),
                tool.key());
        }
        assertEquals(71.5, service.evaluate(plainArmor("netherite")
            .held(ToffyItem.of("diamond_sword"))
            .held(ToffyItem.of("iron_pickaxe")).build()).value());
    }

    @Test
    void itemPowerAddsTheBaseValueAndEveryEnchantment() {
        ToffyService service = bundledService();

        assertEquals(8.0, service.itemPower(ToffyItem.of("netherite_sword")), EPSILON);
        assertEquals(MAX_SWORD_POWER, service.itemPower(bundledSettings().maxReference().weapons().get("sword")),
            EPSILON);
        assertEquals(MAX_PICKAXE_POWER, service.itemPower(bundledSettings().maxReference().tools().get("pickaxe")),
            EPSILON);
        assertEquals(0.0, service.itemPower(ToffyItem.of("stick", Map.of("sharpness", 5))), EPSILON);
    }

    @Test
    void itemsReportWhereTheyCount() {
        ToffyService service = bundledService();

        assertEquals(ToffyItemCategory.ARMOR, service.categoryOf(ToffyItem.of("iron_helmet")));
        assertEquals(ToffyItemCategory.WEAPON, service.categoryOf(ToffyItem.of("trident")));
        assertEquals(ToffyItemCategory.TOOL, service.categoryOf(ToffyItem.of("shears")));
        assertEquals(ToffyItemCategory.WEAPON, service.categoryOf(ToffyItem.of("custom:sahurs_bat")));
        assertNull(service.categoryOf(ToffyItem.of("stick")));
        assertEquals("trident", service.familyOf(ToffyItem.of("trident")));
        assertEquals("fishing_rod", service.familyOf(ToffyItem.of("fishing_rod")));
        assertEquals("sword", service.familyOf(ToffyItem.of("custom:sahurs_bat")));
        assertNull(service.familyOf(ToffyItem.of("iron_helmet")));
    }

    @Test
    void plainNetheriteArmorScoresItsShareOfTheMaxReference() {
        ToffyResult result = bundledService().evaluate(plainArmor("netherite").build());

        assertEquals(36.0, result.armorPower(), EPSILON);
        assertEquals(36.0 / MAX_ARMOR_POWER, result.armorScore(), EPSILON);
        assertEquals(49.0, result.value());
    }

    @Test
    void betterGearAlwaysScoresHigher() {
        ToffyService service = bundledService();
        double previous = -1.0;
        for (String material : List.of("leather", "golden", "chainmail", "iron", "diamond", "netherite")) {
            double value = service.evaluate(plainArmor(material).build()).exactValue();
            assertTrue(value > previous, material + " should beat the previous tier");
            previous = value;
        }
    }

    @Test
    void eachWeaponIsComparedWithItsOwnFamily() {
        ToffyResult sword = bundledService().evaluate(ToffyLoadout.builder()
            .held(ToffyItem.of("netherite_sword")).build());

        assertEquals("sword", sword.weapon().family());
        assertEquals(8.0 / MAX_SWORD_POWER, sword.weaponScore(), EPSILON);
    }

    @Test
    void theBestWeaponAndToolWinAndOtherItemsAreIgnored() {
        ToffySettings settings = bundledSettings();
        ToffyResult result = new ToffyService(settings).evaluate(ToffyLoadout.builder()
            .held(ToffyItem.of("stick", Map.of("sharpness", 5)))
            .held(ToffyItem.of("iron_sword"))
            .held(settings.maxReference().weapons().get("mace"))
            .held(ToffyItem.of("wooden_axe"))
            .held(ToffyItem.of("stone_pickaxe"))
            .held(ToffyItem.of("diamond_shovel"))
            .held(ToffyItem.of("iron_helmet"))
            .build());

        assertEquals("mace", result.weapon().family());
        assertEquals("minecraft:mace", result.weapon().item().key());
        assertEquals(1.0, result.weaponScore(), EPSILON);
        assertEquals("minecraft:diamond_shovel", result.tool().item().key());

        ToffyResult onlyJunk = bundledService().evaluate(ToffyLoadout.builder()
            .held(ToffyItem.of("stick", Map.of("sharpness", 5)))
            .held(ToffyItem.of("iron_helmet")).build());
        assertFalse(onlyJunk.hasWeapon());
        assertFalse(onlyJunk.hasTool());
        assertEquals(0.0, onlyJunk.value());
    }

    @Test
    void weaponsAndToolsWornInAnArmorSlotDoNotCountAsArmor() {
        ToffyResult result = bundledService().evaluate(ToffyLoadout.builder()
            .armor(ToffyArmorSlot.HELMET, ToffyItem.of("netherite_sword", Map.of("sharpness", 5)))
            .armor(ToffyArmorSlot.BOOTS, ToffyItem.of("netherite_pickaxe"))
            .build());

        assertEquals(0.0, result.armorPower(), EPSILON);
        assertEquals(0.0, result.value());
    }

    @Test
    void enchantmentLevelsAboveTheConfiguredMaximumDoNotCountExtra() {
        ToffyService service = bundledService();

        assertEquals(
            service.itemPower(ToffyItem.of("netherite_sword", Map.of("sharpness", 5))),
            service.itemPower(ToffyItem.of("netherite_sword", Map.of("sharpness", 255))),
            EPSILON);
    }

    @Test
    void itemPowerDoesNotDependOnTheEnchantmentOrder() {
        ToffyService service = bundledService();
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

        assertEquals(service.itemPower(ToffyItem.of("netherite_sword", forward)),
            service.itemPower(ToffyItem.of("netherite_sword", backward)));
    }

    @Test
    void godItemsAboveTheMaxReferenceAreCappedAtTwoHundred() {
        ToffySettings settings = bundledSettings();
        ToffyItem godSword = ToffyItem.of("netherite_sword", Map.of("sharpness", 5, "smite", 5,
            "fire_aspect", 2, "knockback", 2, "looting", 3, "sweeping_edge", 3, "unbreaking", 3, "mending", 1));

        ToffyResult result = new ToffyService(settings).evaluate(maxArmor(settings).held(godSword).build());

        assertEquals(200.0, result.value());
        assertEquals(1.0, result.weaponScore(), EPSILON);
    }

    @Test
    void cursesLowerThePowerButNeverBelowZero() {
        ToffyService service = bundledService();

        double cursed = service.itemPower(ToffyItem.of("leather_helmet", Map.of("binding_curse", 1)));
        ToffyResult result = service.evaluate(ToffyLoadout.builder()
            .armor(ToffyArmorSlot.HELMET, ToffyItem.of("leather_helmet", Map.of("binding_curse", 1)))
            .build());

        assertEquals(-1.0, cursed, EPSILON);
        assertEquals(0.0, result.armorScore(), EPSILON);
        assertEquals(0.0, result.value());
    }

    @Test
    void aCustomZeroReferenceMapsToZero() {
        EnumMap<ToffyArmorSlot, ToffyItem> leather = new EnumMap<>(ToffyArmorSlot.class);
        for (ToffyArmorSlot slot : ToffyArmorSlot.values()) {
            leather.put(slot, ToffyItem.of("leather_" + slot.configKey()));
        }
        ToffySettings settings = bundledSettings()
            .withZeroReference(new ToffyReference(leather, Map.of(), Map.of()));
        ToffyService service = new ToffyService(settings);

        assertEquals(0.0, service.evaluate(plainArmor("leather").build()).value());
        assertEquals(0.0, service.evaluate(ToffyLoadout.empty()).value());
        assertEquals(120.0, service.evaluate(maxArmor(settings).build()).value());
        double iron = service.evaluate(plainArmor("iron").build()).armorScore();
        assertEquals((15.0 - 7.0) / (MAX_ARMOR_POWER - 7.0), iron, EPSILON);
    }

    @Test
    void theCurveBendsTheMiddleButKeepsTheEnds() {
        ToffySettings linear = bundledSettings();
        ToffyCurve exponential = new ToffyCurve.Exponential(3.0);
        ToffyService service = new ToffyService(linear.withCurve(exponential));

        ToffyResult armorOnly = service.evaluate(maxArmor(linear).build());
        ToffyResult full = service.evaluate(maxArmor(linear)
            .held(linear.maxReference().weapons().get("sword")).build());

        assertEquals(0.6, armorOnly.progress(), EPSILON);
        assertEquals(exponential.apply(0.6), armorOnly.curvedProgress(), EPSILON);
        assertEquals(200.0 * exponential.apply(0.6), armorOnly.exactValue(), EPSILON);
        assertEquals(ToffyCurveType.EXPONENTIAL, armorOnly.curveType());
        assertEquals(0.0, service.evaluate(ToffyLoadout.empty()).value());
        assertEquals(200.0, full.value());
    }

    @Test
    void theOutputIsRoundedToTheConfiguredDecimals() {
        ToffyService whole = new ToffyService(bundledSettings().withDecimals(0));
        ToffyService precise = new ToffyService(bundledSettings().withDecimals(3));

        assertEquals(49.0, whole.evaluate(plainArmor("netherite").build()).value());
        assertEquals(48.952, precise.evaluate(plainArmor("netherite").build()).value());
    }

    @Test
    void theExamplesInTheUserGuideMatchTheBundledDefaults() {
        ToffySettings settings = bundledSettings();
        ToffyService service = new ToffyService(settings);

        assertEquals(9.5, service.evaluate(plainArmor("leather").build()).value());
        assertEquals(20.4, service.evaluate(plainArmor("iron").build()).value());
        assertEquals(38.1, service.evaluate(plainArmor("diamond").build()).value());
        assertEquals(49.0, service.evaluate(plainArmor("netherite").build()).value());
        assertEquals(73.8, service.evaluate(plainArmor("netherite")
            .held(ToffyItem.of("diamond_sword")).build()).value());
        assertEquals(148.4, service.evaluate(maxArmor(settings)
            .held(ToffyItem.of("netherite_sword")).build()).value());
        assertEquals(32.0, service.evaluate(ToffyLoadout.builder()
            .held(ToffyItem.of("custom:sahurs_bat")).build()).value());
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
        ToffySettings settings = bundledSettings();
        Map<String, Double> armor = new LinkedHashMap<>(settings.armorValues());
        armor.put("minecraft:netherite_helmet", 1e308);
        ToffySettings huge = new ToffySettings(settings.maximum(), settings.decimals(), settings.armorWeight(),
            settings.weaponWeight(), settings.toolWeight(), settings.search(), settings.curve(),
            settings.zeroReference(), settings.maxReference(), armor, settings.weaponFamilies(),
            settings.toolFamilies(), settings.enchantmentValues(), settings.customItems(), settings.memory());

        assertTrue(String.join("\n", ToffyService.problems(huge)).contains("armor.minecraft:netherite_helmet"));
    }

    @Test
    void aPartWithoutWeightNeverAffectsTheLevel() {
        ToffySettings settings = bundledSettings();
        ToffySettings noTools = new ToffySettings(settings.maximum(), settings.decimals(),
            settings.armorWeight(), settings.weaponWeight(), 0.0, settings.search(), settings.curve(),
            settings.zeroReference(), new ToffyReference(settings.maxReference().armor(),
                settings.maxReference().weapons(), Map.of()),
            settings.armorValues(), settings.weaponFamilies(), settings.toolFamilies(), settings.enchantmentValues(),
            settings.customItems(), settings.memory());
        ToffyService service = new ToffyService(noTools);

        ToffyResult result = service.evaluate(maxArmor(noTools)
            .held(noTools.maxReference().weapons().get("sword"))
            .held(ToffyItem.of("netherite_pickaxe")).build());

        assertEquals(200.0, result.value());
        assertEquals(0.0, result.toolScore());
    }

    @Test
    void theBestPieceOfEachSlotCountsAmongWornAndCarriedArmor() {
        ToffySettings settings = bundledSettings();
        ToffyItem enchantedDiamond = ToffyItem.of("diamond_chestplate", Map.of("protection", 4));
        ToffyResult result = new ToffyService(settings).evaluate(ToffyLoadout.builder()
            .armor(ToffyArmorSlot.HELMET, ToffyItem.of("leather_helmet"))
            .armor(ToffyArmorSlot.HELMET, ToffyItem.of("netherite_helmet"))
            .armor(ToffyArmorSlot.HELMET, ToffyItem.of("iron_helmet"))
            .armor(ToffyArmorSlot.CHESTPLATE, ToffyItem.of("netherite_chestplate"))
            .armor(ToffyArmorSlot.CHESTPLATE, enchantedDiamond)
            .armor(ToffyArmorSlot.BOOTS, ToffyItem.of("netherite_sword"))
            .build());

        assertEquals("minecraft:netherite_helmet", result.armorPieces().get(ToffyArmorSlot.HELMET).key());
        assertEquals(enchantedDiamond, result.armorPieces().get(ToffyArmorSlot.CHESTPLATE));
        assertNull(result.armorPieces().get(ToffyArmorSlot.BOOTS));
        assertEquals(7.0 + 16.0, result.armorPower(), EPSILON);
    }

    @Test
    void weaponsAndToolsAreSearchedInTheirOwnLists() {
        ToffySettings settings = bundledSettings();
        ToffyResult result = new ToffyService(settings).evaluate(ToffyLoadout.builder()
            .weapon(ToffyItem.of("netherite_pickaxe"))
            .tool(ToffyItem.of("netherite_sword"))
            .tool(ToffyItem.of("iron_pickaxe"))
            .build());

        assertFalse(result.hasWeapon());
        assertEquals("minecraft:iron_pickaxe", result.tool().item().key());
    }

    @Test
    void aPieceOnlyCountsInItsOwnSlot() {
        ToffySettings settings = bundledSettings();
        ToffyItem maxChestplate = settings.maxReference().armor().get(ToffyArmorSlot.CHESTPLATE);
        ToffyLoadout.Builder fourChestplates = ToffyLoadout.builder();
        for (ToffyArmorSlot slot : ToffyArmorSlot.values()) {
            fourChestplates.armor(slot, maxChestplate);
        }
        ToffyService service = new ToffyService(settings);

        ToffyResult result = service.evaluate(fourChestplates
            .held(settings.maxReference().weapons().get("sword")).build());

        assertEquals(1, result.armorPieces().size());
        assertEquals(service.itemPower(maxChestplate), result.armorPower(), EPSILON);
        assertTrue(result.value() < 200.0);
        assertEquals(ToffyArmorSlot.CHESTPLATE, service.armorSlotOf(maxChestplate));
    }

    @Test
    void twoHundredNeedsTheFullNetheriteKitNotJustAsManyPoints() {
        ToffySettings settings = bundledSettings();
        ToffyService service = new ToffyService(settings);
        ToffyLoadout.Builder mixed = ToffyLoadout.builder();
        settings.maxReference().armor().forEach((slot, piece) -> {
            String material = slot == ToffyArmorSlot.CHESTPLATE ? "netherite" : "diamond";
            mixed.armor(slot, ToffyItem.of(material + "_" + slot.configKey(), piece.enchantments()));
        });

        ToffyResult result = service.evaluate(mixed.held(settings.maxReference().weapons().get("sword")).build());

        assertEquals(MAX_ARMOR_POWER - 3 * 2.0, result.armorPower(), EPSILON);
        assertTrue(result.value() < 200.0);
    }

    @Test
    void aMaximumThatCannotBeShownWithTheDecimalsIsRejected() {
        ToffySettings settings = bundledSettings();
        ToffySettings tooPrecise = new ToffySettings(200.06, 1, settings.armorWeight(), settings.weaponWeight(),
            settings.toolWeight(), settings.search(), settings.curve(), settings.zeroReference(),
            settings.maxReference(), settings.armorValues(), settings.weaponFamilies(), settings.toolFamilies(),
            settings.enchantmentValues(), settings.customItems(), settings.memory());
        ToffySettings tiny = new ToffySettings(0.001, 0, settings.armorWeight(), settings.weaponWeight(),
            settings.toolWeight(), settings.search(), settings.curve(), settings.zeroReference(),
            settings.maxReference(), settings.armorValues(), settings.weaponFamilies(), settings.toolFamilies(),
            settings.enchantmentValues(), settings.customItems(), settings.memory());
        ToffySettings fine = new ToffySettings(200.1, 1, settings.armorWeight(), settings.weaponWeight(),
            settings.toolWeight(), settings.search(), settings.curve(), settings.zeroReference(),
            settings.maxReference(), settings.armorValues(), settings.weaponFamilies(), settings.toolFamilies(),
            settings.enchantmentValues(), settings.customItems(), settings.memory());

        assertTrue(String.join("\n", ToffyService.problems(tooPrecise)).contains("output.maximum"));
        assertTrue(String.join("\n", ToffyService.problems(tiny)).contains("output.maximum"));
        ToffyService service = new ToffyService(fine);
        assertEquals(200.1, service.evaluate(maxArmor(fine).held(fine.maxReference().weapons().get("sword")).build())
            .value());
    }

    @Test
    void invalidSettingsCannotBuildAService() {
        ToffySettings settings = bundledSettings();
        ToffySettings broken = settings.withZeroReference(new ToffyReference(
            settings.maxReference().armor(), Map.of(), Map.of()));

        assertFalse(ToffyService.problems(broken).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ToffyService(broken));
    }

    @Test
    void aCarriedCustomArmorPieceCountsInItsConfiguredSlot() {
        YamlConfiguration values = ToffySettingsLoaderTest.bundledValues();
        values.set("custom-items.tier_chest.match.persistent-data", "otroplugin:tier");
        values.set("custom-items.tier_chest.category", "armor");
        values.set("custom-items.tier_chest.slot", "chestplate");
        values.set("custom-items.tier_chest.value", 12.0);
        ToffyService service = new ToffyService(
            ToffySettingsLoader.load(ToffySettingsLoaderTest.bundledMain(), values).settings());
        ToffyItem tierChest = ToffyItem.of("custom:tier_chest");

        assertEquals(ToffyArmorSlot.CHESTPLATE, service.armorSlotOf(tierChest));
        assertEquals(12.0, service.evaluate(ToffyLoadout.builder()
            .armor(ToffyArmorSlot.CHESTPLATE, tierChest).build()).armorPower(), EPSILON);
        assertEquals(0.0, service.evaluate(ToffyLoadout.builder()
            .armor(ToffyArmorSlot.HELMET, tierChest).build()).armorPower(), EPSILON);
    }

    @Test
    void onlyStacksThatCanCountAreRead() {
        ToffyService service = bundledService();

        assertTrue(service.mayCount("minecraft:iron_sword"));
        assertTrue(service.mayCount("minecraft:netherite_helmet"));
        // The bundled sahurs_bat match has no material, so any stack could be it.
        assertTrue(service.mayCount("minecraft:stone"));
        assertTrue(service.needsCustomData("minecraft:stone"));
    }

    @Test
    void customMatchesOnlyNeedDataForTheirMaterial() {
        YamlConfiguration values = ToffySettingsLoaderTest.bundledValues();
        values.set("custom-items.sahurs_bat.match", Map.of("material", "iron_sword"));
        ToffyService service = new ToffyService(
            ToffySettingsLoader.load(ToffySettingsLoaderTest.bundledMain(), values).settings());

        assertTrue(service.needsCustomData("minecraft:iron_sword"));
        assertFalse(service.needsCustomData("minecraft:stone"));
        assertFalse(service.needsCustomData("minecraft:netherite_sword"));
        assertTrue(service.mayCount("minecraft:iron_sword"));
        assertFalse(service.mayCount("minecraft:stone"));
    }

    @Test
    void withoutCustomItemsNoStackNeedsItsDataRead() {
        YamlConfiguration values = ToffySettingsLoaderTest.bundledValues();
        values.set("custom-items", null);
        ToffyService service = new ToffyService(
            ToffySettingsLoader.load(ToffySettingsLoaderTest.bundledMain(), values).settings());

        assertFalse(service.needsCustomData("minecraft:iron_sword"));
        assertFalse(service.needsCustomData("minecraft:stone"));
    }
}
