package dev.linqfy.bigCasares.modules.cosmicniv;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmicNivSettingsLoaderTest {

    static YamlConfiguration bundledMain() {
        return YamlConfiguration.loadConfiguration(new File("src/main/resources/cosmicniv_lib.yml"));
    }

    static YamlConfiguration bundledValues() {
        return YamlConfiguration.loadConfiguration(new File("src/main/resources/cosmicniv_valores.yml"));
    }

    static CosmicNivSettings bundledSettings() {
        CosmicNivConfigLoadResult result = CosmicNivSettingsLoader.load(bundledMain(), bundledValues());
        assertTrue(result.valid(), () -> String.join("\n", result.errors()));
        return result.settings();
    }

    private static CosmicNivConfigLoadResult load(YamlConfiguration main, YamlConfiguration values) {
        return CosmicNivSettingsLoader.load(main, values);
    }

    private static void assertValid(YamlConfiguration main, YamlConfiguration values) {
        CosmicNivConfigLoadResult result = load(main, values);
        assertTrue(result.valid(), () -> String.join("\n", result.errors()));
    }

    private static void assertErrorMentions(YamlConfiguration main, YamlConfiguration values, String... fragments) {
        CosmicNivConfigLoadResult result = load(main, values);
        assertFalse(result.valid(), "expected an invalid configuration");
        assertNull(result.settings());
        String errors = String.join("\n", result.errors());
        for (String fragment : fragments) {
            assertTrue(errors.contains(fragment), () -> "missing '" + fragment + "' in:\n" + errors);
        }
    }

    @Test
    void bundledFilesLoadWithoutErrorsOrWarnings() {
        CosmicNivConfigLoadResult result = load(bundledMain(), bundledValues());

        assertTrue(result.valid(), () -> String.join("\n", result.errors()));
        assertEquals(List.of(), result.warnings());
        CosmicNivSettings settings = result.settings();
        assertEquals(200.0, settings.maximum());
        assertEquals(1, settings.decimals());
        assertEquals(0.6, settings.armorWeight());
        assertEquals(0.4, settings.weaponWeight());
        assertEquals(0.0, settings.toolWeight());
        assertEquals(new CosmicNivSearch(CosmicNivArmorSearch.INVENTORY, CosmicNivHeldSearch.HOTBAR,
            CosmicNivHeldSearch.INVENTORY), settings.search());
        assertEquals(CosmicNivCurveType.LINEAR, settings.curve().type());
        assertEquals(List.of("sword", "axe", "mace", "spear", "trident"), List.copyOf(settings.weaponFamilies().keySet()));
        assertEquals(List.of("pickaxe", "shovel", "hoe", "shears", "fishing_rod"),
            List.copyOf(settings.toolFamilies().keySet()));
        assertEquals(29, settings.armorValues().size());
        assertEquals(4, settings.maxReference().armor().size());
        assertEquals(5, settings.maxReference().weapons().size());
        assertEquals(5, settings.maxReference().tools().size());
        assertTrue(settings.zeroReference().armor().isEmpty());
    }

    @Test
    void theBundledCustomItemIsTheSahurBat() {
        List<CosmicNivCustomItem> custom = bundledSettings().customItems();

        assertEquals(1, custom.size());
        CosmicNivCustomItem bat = custom.getFirst();
        assertEquals("custom:sahurs_bat", bat.key());
        assertEquals(CosmicNivItemCategory.WEAPON, bat.category());
        assertEquals("sword", bat.family());
        assertEquals(9.0, bat.value());
        assertEquals("sahurs_bat", bat.match().catalogId());
    }

    @Test
    void keysAreNormalizedToNamespacedLowercase() {
        CosmicNivSettings settings = bundledSettings();

        assertEquals(7.0, settings.armorValues().get("minecraft:netherite_helmet"));
        assertEquals(8.0, settings.weaponFamilies().get("sword").get("minecraft:netherite_sword"));
        assertEquals(5.0, settings.toolFamilies().get("pickaxe").get("minecraft:netherite_pickaxe"));
        assertEquals(new CosmicNivEnchantmentValue(1.5, 4), settings.enchantmentValues().get("minecraft:protection"));
        CosmicNivItem sword = settings.maxReference().weapons().get("sword");
        assertEquals("minecraft:netherite_sword", sword.key());
        assertEquals(5, sword.enchantments().get("minecraft:sharpness"));
    }

    @Test
    void missingOptionalScalarsFallBackToDefaults() {
        YamlConfiguration main = bundledMain();
        main.set("output", null);
        main.set("weights", null);
        main.set("armor-search", null);
        main.set("weapon-search", null);
        main.set("tool-search", null);
        main.set("curve", null);

        CosmicNivConfigLoadResult result = load(main, bundledValues());

        assertTrue(result.valid(), () -> String.join("\n", result.errors()));
        assertEquals(200.0, result.settings().maximum());
        assertEquals(1, result.settings().decimals());
        assertEquals(0.6, result.settings().armorWeight());
        assertEquals(0.4, result.settings().weaponWeight());
        assertEquals(0.0, result.settings().toolWeight());
        assertEquals(CosmicNivSearch.DEFAULT, result.settings().search());
        assertEquals(CosmicNivCurveType.LINEAR, result.settings().curve().type());
    }

    @Test
    void theMemorySectionIsReadWithDefaults() {
        assertEquals(CosmicNivMemorySettings.DEFAULT, bundledSettings().memory());

        YamlConfiguration main = bundledMain();
        main.set("memory", null);
        assertEquals(CosmicNivMemorySettings.DEFAULT, load(main, bundledValues()).settings().memory());

        main.set("memory.enabled", false);
        main.set("memory.forget-after-minutes", 0);
        main.set("memory.forget-on-death", false);
        main.set("memory.check-interval-ticks", 100);
        assertEquals(new CosmicNivMemorySettings(false, 0L, false, 100), load(main, bundledValues()).settings().memory());

        main.set("memory.forget-after-minutes", 1.5);
        assertEquals(90_000L, load(main, bundledValues()).settings().memory().forgetAfterMillis());
    }

    @Test
    void invalidMemoryValuesAreReported() {
        YamlConfiguration main = bundledMain();
        main.set("memory.enabled", "si");
        main.set("memory.forget-after-minutes", -5);
        main.set("memory.check-interval-ticks", 0);
        main.set("memory.forget-on-dead", true);
        assertErrorMentions(main, bundledValues(), "memory.enabled: must be true or false",
            "memory.forget-after-minutes", "memory.check-interval-ticks", "memory.forget-on-dead: unknown key");
    }

    @Test
    void eachSearchIsReadOnItsOwn() {
        YamlConfiguration main = bundledMain();
        main.set("armor-search", "worn");
        main.set("weapon-search", "HANDS");
        main.set("tool-search", "MAIN_HAND");

        CosmicNivConfigLoadResult result = load(main, bundledValues());

        assertTrue(result.valid(), () -> String.join("\n", result.errors()));
        assertEquals(new CosmicNivSearch(CosmicNivArmorSearch.WORN, CosmicNivHeldSearch.HANDS,
            CosmicNivHeldSearch.MAIN_HAND), result.settings().search());
    }

    @Test
    void everyCurveTypeIsReadWithItsParameters() {
        YamlConfiguration main = bundledMain();
        main.set("curve.type", "exponential");
        main.set("curve.strength", 4.0);
        assertEquals(new CosmicNivCurve.Exponential(4.0), load(main, bundledValues()).settings().curve());

        main.set("curve.type", "LOGARITHMIC");
        assertEquals(new CosmicNivCurve.Logarithmic(4.0), load(main, bundledValues()).settings().curve());

        main.set("curve.type", "POWER");
        main.set("curve.exponent", 0.5);
        assertEquals(new CosmicNivCurve.Power(0.5), load(main, bundledValues()).settings().curve());

        main.set("curve.type", "SIGMOID");
        main.set("curve.midpoint", 0.4);
        assertEquals(new CosmicNivCurve.Sigmoid(4.0, 0.4), load(main, bundledValues()).settings().curve());

        main.set("curve.type", "POINTS");
        CosmicNivCurve points = load(main, bundledValues()).settings().curve();
        assertInstanceOf(CosmicNivCurve.Points.class, points);
        assertEquals(0.3, points.apply(0.5), 1e-9);
    }

    @Test
    void emptyFilesReportTheMissingMaxReferenceWithItsFile() {
        assertErrorMentions(new YamlConfiguration(), new YamlConfiguration(), "cosmicniv_lib.yml > references.max");
    }

    @Test
    void armorWithWeightNeedsArmorValues() {
        YamlConfiguration values = bundledValues();
        values.set("armor", null);
        assertErrorMentions(bundledMain(), values, "cosmicniv_valores.yml > armor: at least one armor value");
    }

    @Test
    void misspelledKeysAreErrorsInsteadOfBeingIgnored() {
        YamlConfiguration main = bundledMain();
        main.set("weights.wepon", 0.4);
        main.set("references.max.weapons.sword.enchants", Map.of("sharpness", 5));
        main.set("held-search", "HOTBAR");
        YamlConfiguration values = bundledValues();
        values.set("custom-items.sahurs_bat.match.bigcasares_item", "sahurs_bat");
        values.set("custom-items.sahurs_bat.valor", 9);
        values.set("enchantments.protection.points", 1);
        values.set("herramientas", Map.of());

        assertErrorMentions(main, values,
            "cosmicniv_lib.yml > weights.wepon: unknown key",
            "references.max.weapons.sword.enchants: unknown key",
            "cosmicniv_lib.yml > held-search: unknown key",
            "custom-items.sahurs_bat.match.bigcasares_item: unknown key",
            "custom-items.sahurs_bat.valor: unknown key",
            "enchantments.protection.points: unknown key",
            "cosmicniv_valores.yml > herramientas: unknown key");
    }

    @Test
    void customMatchTextsMustBeText() {
        YamlConfiguration values = bundledValues();
        values.set("custom-items.sahurs_bat.match.bigcasares-item", List.of("a", "b"));
        values.set("custom-items.flagged.match.persistent-data.key", "otroplugin:nivel");
        values.set("custom-items.flagged.match.persistent-data.value", 1.0);
        values.set("custom-items.flagged.category", "armor");
        values.set("custom-items.flagged.value", 1);
        values.set("custom-items.bad_family.match.bigcasares-item", "x");
        values.set("custom-items.bad_family.category", "weapon");
        values.set("custom-items.bad_family.family", List.of("sword"));
        values.set("custom-items.bad_family.value", 1);

        assertErrorMentions(bundledMain(), values, "custom-items.sahurs_bat.match.bigcasares-item: must be text",
            "custom-items.flagged.match.persistent-data.value: must be text in quotes",
            "custom-items.bad_family.family: must be a family name");
    }

    @Test
    void partsWithoutWeightDoNotNeedValidReferences() {
        YamlConfiguration values = bundledValues();
        values.set("tools", null);
        assertValid(bundledMain(), values);

        YamlConfiguration main = bundledMain();
        main.set("weights.armor", 0);
        YamlConfiguration noArmor = bundledValues();
        noArmor.set("armor", null);
        assertValid(main, noArmor);
    }

    @Test
    void mainHandWithWeaponAndToolWeightsWarns() {
        YamlConfiguration main = bundledMain();
        main.set("weapon-search", "MAIN_HAND");
        main.set("tool-search", "MAIN_HAND");
        main.set("weights.tool", 0.1);

        CosmicNivConfigLoadResult result = load(main, bundledValues());

        assertTrue(result.valid(), () -> String.join("\n", result.errors()));
        assertTrue(String.join("\n", result.warnings()).contains("MAIN_HAND"));
    }

    @Test
    void referenceEnchantmentsWithoutAValueWarn() {
        YamlConfiguration main = bundledMain();
        main.set("references.max.armor.chestplate.enchantments.thorns", 3);
        YamlConfiguration values = bundledValues();
        values.set("enchantments.thorns", null);

        CosmicNivConfigLoadResult result = load(main, values);

        assertTrue(result.valid(), () -> String.join("\n", result.errors()));
        assertTrue(String.join("\n", result.warnings()).contains("minecraft:thorns"));
    }

    @Test
    void invalidScalarsAreReportedWithTheirPath() {
        YamlConfiguration main = bundledMain();
        main.set("output.maximum", -1);
        main.set("output.decimals", 12);
        main.set("weapon-search", "POCKETS");
        main.set("tool-search", "BACKPACK");
        main.set("armor-search", "HOTBAR");
        main.set("weights.tool", -1);
        assertErrorMentions(main, bundledValues(), "cosmicniv_lib.yml > output.maximum", "output.decimals",
            "weapon-search: must be MAIN_HAND, HANDS, HOTBAR, INVENTORY", "tool-search", "armor-search: must be WORN",
            "weights.tool");
    }

    @Test
    void weightsThatAddUpToZeroAreRejected() {
        YamlConfiguration main = bundledMain();
        main.set("weights.armor", 0);
        main.set("weights.weapon", 0);
        main.set("weights.tool", 0);
        assertErrorMentions(main, bundledValues(), "weights");
    }

    @Test
    void unknownOrInvalidCurvesAreRejected() {
        YamlConfiguration unknown = bundledMain();
        unknown.set("curve.type", "ZIGZAG");
        assertErrorMentions(unknown, bundledValues(), "curve.type");

        YamlConfiguration zeroStrength = bundledMain();
        zeroStrength.set("curve.type", "EXPONENTIAL");
        zeroStrength.set("curve.strength", 0.0);
        assertErrorMentions(zeroStrength, bundledValues(), "curve", "EXPONENTIAL");

        YamlConfiguration badPoints = bundledMain();
        badPoints.set("curve.type", "POINTS");
        badPoints.set("curve.points", List.of(List.of(0.0, 0.0), List.of(0.5, 0.9), List.of(1.0, 0.5)));
        assertErrorMentions(badPoints, bundledValues(), "curve.points");

        YamlConfiguration malformedPoints = bundledMain();
        malformedPoints.set("curve.type", "POINTS");
        malformedPoints.set("curve.points", List.of(List.of(0.0), "x"));
        assertErrorMentions(malformedPoints, bundledValues(), "curve.points[0]", "curve.points[1]");
    }

    @Test
    void invalidValuesAreReportedWithTheValuesFile() {
        YamlConfiguration values = bundledValues();
        values.set("armor.netherite_helmet", "mucho");
        values.set("weapons.mace.mace", "pesado");
        values.set("tools.pickaxe", "pico");
        values.set("enchantments.protection.max-level", 0);
        values.set("enchantments.sharpness", 3);
        assertErrorMentions(bundledMain(), values, "cosmicniv_valores.yml > armor.netherite_helmet",
            "weapons.mace.mace", "tools.pickaxe", "enchantments.protection", "enchantments.sharpness");
    }

    @Test
    void hugeValuesAreRejectedBeforeTheyOverflow() {
        YamlConfiguration values = bundledValues();
        values.set("armor.netherite_helmet", 1e308);
        values.set("enchantments.mending.points-per-level", -1e9);
        assertErrorMentions(bundledMain(), values, "armor.netherite_helmet", "enchantments.mending");
    }

    @Test
    void unknownArmorSlotsAndMalformedReferencesAreReported() {
        YamlConfiguration main = bundledMain();
        main.set("references.max.armor.hat", "netherite_helmet");
        main.set("references.max.weapons.sword.enchantments.sharpness", "cinco");
        main.set("references.max.weapons.axe.item", null);
        assertErrorMentions(main, bundledValues(), "references.max.armor.hat",
            "references.max.weapons.sword.enchantments.sharpness", "references.max.weapons.axe.item");
    }

    @Test
    void armorReferencesMustHaveAnArmorValue() {
        YamlConfiguration main = bundledMain();
        main.set("references.max.armor.helmet.item", "netherite_helmett");
        assertErrorMentions(main, bundledValues(), "references.max.armor.helmet", "minecraft:netherite_helmett",
            "no armor value");
    }

    @Test
    void weaponsAndToolsCannotBeUsedAsArmorReferences() {
        YamlConfiguration main = bundledMain();
        main.set("references.max.armor.helmet", "netherite_sword");
        assertErrorMentions(main, bundledValues(), "references.max.armor.helmet", "is a weapon");
    }

    @Test
    void heldReferencesMustBelongToTheirFamily() {
        YamlConfiguration wrongFamily = bundledMain();
        wrongFamily.set("references.max.weapons.sword.item", "netherite_axe");
        assertErrorMentions(wrongFamily, bundledValues(), "references.max.weapons.sword", "weapons.axe");

        YamlConfiguration wrongCategory = bundledMain();
        wrongCategory.set("references.max.weapons.sword.item", "netherite_pickaxe");
        assertErrorMentions(wrongCategory, bundledValues(), "references.max.weapons.sword", "tools.pickaxe");

        YamlConfiguration notAWeapon = bundledMain();
        notAWeapon.set("references.max.weapons.sword.item", "stick");
        assertErrorMentions(notAWeapon, bundledValues(), "references.max.weapons.sword", "minecraft:stick");
    }

    @Test
    void everyWeightedFamilyNeedsAMaxReferenceAndReferencesNeedAKnownFamily() {
        YamlConfiguration missing = bundledMain();
        missing.set("references.max.weapons.trident", null);
        assertErrorMentions(missing, bundledValues(), "references.max.weapons.trident");

        YamlConfiguration unknown = bundledMain();
        unknown.set("references.zero.weapons.bow", "bow");
        assertErrorMentions(unknown, bundledValues(), "references.zero.weapons.bow", "unknown weapon family");
    }

    @Test
    void toolReferencesAreOnlyRequiredWhenToolsHaveWeight() {
        YamlConfiguration withoutTools = bundledMain();
        withoutTools.set("references.max.tools", null);
        assertValid(withoutTools, bundledValues());

        withoutTools.set("weights.tool", 0.1);
        assertErrorMentions(withoutTools, bundledValues(), "references.max.tools.pickaxe",
            "references.max.tools.fishing_rod");
    }

    @Test
    void anItemCanOnlyBeInOnePlace() {
        YamlConfiguration twoFamilies = bundledValues();
        twoFamilies.set("weapons.axe.netherite_sword", 8.0);
        assertErrorMentions(bundledMain(), twoFamilies, "weapons.axe.minecraft:netherite_sword",
            "already listed under weapons.sword", "one place");

        YamlConfiguration armorAndTool = bundledValues();
        armorAndTool.set("armor.shears", 1.0);
        assertErrorMentions(bundledMain(), armorAndTool, "tools.shears.minecraft:shears", "already listed under armor");

        YamlConfiguration sameFamilyName = bundledValues();
        sameFamilyName.set("tools.sword.wooden_hoe", 1.0);
        assertErrorMentions(bundledMain(), sameFamilyName, "tools.sword", "already used under weapons");
    }

    @Test
    void keysThatCollideAfterNormalizationAreRejected() {
        YamlConfiguration items = bundledValues();
        items.set("weapons.sword.minecraft:netherite_sword", 9.0);
        assertErrorMentions(bundledMain(), items, "weapons.sword.minecraft:netherite_sword", "listed twice");

        YamlConfiguration families = bundledValues();
        families.set("weapons.SWORD.bow", 6.0);
        assertErrorMentions(bundledMain(), families, "weapons.SWORD", "declared twice");

        YamlConfiguration enchantments = bundledMain();
        enchantments.set("references.max.weapons.sword.enchantments.minecraft:sharpness", 4);
        assertErrorMentions(enchantments, bundledValues(),
            "references.max.weapons.sword.enchantments.minecraft:sharpness", "listed twice");
    }

    @Test
    void theMaxReferenceMustBeAboveTheZeroReference() {
        YamlConfiguration main = bundledMain();
        ConfigurationSection maxArmor = main.getConfigurationSection("references.max.armor");
        for (String slot : maxArmor.getKeys(false)) {
            main.set("references.zero.armor." + slot + ".item", maxArmor.getString(slot + ".item"));
            main.set("references.zero.armor." + slot + ".enchantments",
                maxArmor.getConfigurationSection(slot + ".enchantments").getValues(false));
        }
        assertErrorMentions(main, bundledValues(), "references.max.armor", "references.zero.armor");

        YamlConfiguration weapons = bundledMain();
        weapons.set("references.zero.weapons.mace", Map.of("item", "mace",
            "enchantments", Map.of("density", 5, "wind_burst", 3, "fire_aspect", 2, "unbreaking", 3, "mending", 1)));
        assertErrorMentions(weapons, bundledValues(), "references.max.weapons.mace", "references.zero.weapons.mace");
    }

    @Test
    void customItemsAreReadWithEveryKindOfMatch() {
        YamlConfiguration values = bundledValues();
        values.set("custom-items.dragon_helmet.match.item-model", "otroplugin:casco_dragon");
        values.set("custom-items.dragon_helmet.category", "armor");
        values.set("custom-items.dragon_helmet.value", 9.0);
        values.set("custom-items.magic_pick.match.material", "diamond_pickaxe");
        values.set("custom-items.magic_pick.match.persistent-data.key", "otroplugin:tipo");
        values.set("custom-items.magic_pick.match.persistent-data.value", "magico");
        values.set("custom-items.magic_pick.category", "TOOL");
        values.set("custom-items.magic_pick.family", "pickaxe");
        values.set("custom-items.magic_pick.value", 6);
        values.set("custom-items.tagged.match.persistent-data", "otroplugin:marca");
        values.set("custom-items.tagged.category", "weapon");
        values.set("custom-items.tagged.family", "axe");
        values.set("custom-items.tagged.value", 1);

        CosmicNivConfigLoadResult result = load(bundledMain(), values);

        assertTrue(result.valid(), () -> String.join("\n", result.errors()));
        List<CosmicNivCustomItem> custom = result.settings().customItems();
        assertEquals(List.of("sahurs_bat", "dragon_helmet", "magic_pick", "tagged"),
            custom.stream().map(CosmicNivCustomItem::id).toList());
        assertEquals(new CosmicNivCustomMatch(null, null, "otroplugin:casco_dragon", null, null), custom.get(1).match());
        assertEquals(new CosmicNivCustomMatch("minecraft:diamond_pickaxe", null, null, "otroplugin:tipo", "magico"),
            custom.get(2).match());
        assertEquals(CosmicNivItemCategory.TOOL, custom.get(2).category());
        assertEquals(new CosmicNivCustomMatch(null, null, null, "otroplugin:marca", null), custom.get(3).match());
    }

    @Test
    void invalidCustomItemsAreReported() {
        YamlConfiguration values = bundledValues();
        values.set("custom-items.no_match.category", "weapon");
        values.set("custom-items.no_match.family", "sword");
        values.set("custom-items.no_match.value", 1);
        values.set("custom-items.bad_category.match.bigcasares-item", "x");
        values.set("custom-items.bad_category.category", "potion");
        values.set("custom-items.bad_category.value", 1);
        values.set("custom-items.no_family.match.bigcasares-item", "y");
        values.set("custom-items.no_family.category", "weapon");
        values.set("custom-items.no_family.value", 1);
        values.set("custom-items.armor_family.match.bigcasares-item", "z");
        values.set("custom-items.armor_family.category", "armor");
        values.set("custom-items.armor_family.family", "sword");
        values.set("custom-items.armor_family.value", 1);
        values.set("custom-items.no_value.match.bigcasares-item", "w");
        values.set("custom-items.no_value.category", "armor");
        values.set("custom-items.empty_match.match.nothing", "here");
        values.set("custom-items.empty_match.category", "armor");
        values.set("custom-items.empty_match.value", 1);

        assertErrorMentions(bundledMain(), values,
            "custom-items.no_match.match", "custom-items.bad_category.category",
            "custom-items.no_family: weapon custom items need a family",
            "custom-items.armor_family: armor custom items do not have a family",
            "custom-items.no_value.value", "custom-items.empty_match.match");
    }

    @Test
    void customItemsMustUseAnExistingFamily() {
        YamlConfiguration values = bundledValues();
        values.set("custom-items.sahurs_bat.family", "bat");
        assertErrorMentions(bundledMain(), values, "custom-items.sahurs_bat.family", "unknown weapon family bat");
    }

    @Test
    void customItemsCanBeUsedAsReferences() {
        YamlConfiguration main = bundledMain();
        main.set("references.max.weapons.sword", "custom:sahurs_bat");
        assertValid(main, bundledValues());
    }

    @Test
    void armorReferencesMustGoInTheirOwnSlot() {
        YamlConfiguration main = bundledMain();
        main.set("references.max.armor.helmet.item", "netherite_chestplate");
        assertErrorMentions(main, bundledValues(), "references.max.armor.helmet", "worn as chestplate, not helmet");
    }

    @Test
    void customArmorCanDeclareItsSlot() {
        YamlConfiguration values = bundledValues();
        values.set("custom-items.dragon_helmet.match.item-model", "otroplugin:casco_dragon");
        values.set("custom-items.dragon_helmet.category", "armor");
        values.set("custom-items.dragon_helmet.slot", "helmet");
        values.set("custom-items.dragon_helmet.value", 9.0);

        CosmicNivSettings settings = load(bundledMain(), values).settings();

        assertEquals(CosmicNivArmorSlot.HELMET, settings.customItems().get(1).armorSlot());

        values.set("custom-items.dragon_helmet.slot", "head");
        values.set("custom-items.sahurs_bat.slot", "helmet");
        assertErrorMentions(bundledMain(), values, "custom-items.dragon_helmet.slot",
            "custom-items.sahurs_bat: only armor custom items have a slot");
    }

    @Test
    void aMissingEnchantmentSectionOnlyWarns() {
        YamlConfiguration values = bundledValues();
        values.set("enchantments", null);

        CosmicNivConfigLoadResult result = load(bundledMain(), values);

        assertTrue(result.valid(), () -> String.join("\n", result.errors()));
        assertTrue(String.join("\n", result.warnings()).contains("enchantments"));
    }
}
