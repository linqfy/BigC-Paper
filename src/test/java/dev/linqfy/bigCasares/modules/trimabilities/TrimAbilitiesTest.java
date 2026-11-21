package dev.linqfy.bigCasares.modules.trimabilities;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrimAbilitiesTest {

    private final TrimSetRules diamondOnly = new TrimSetRules(Set.of("diamond"));

    @Test
    void exposesStableModuleId() {
        assertEquals("trim-abilities", new TrimAbilitiesModule(null).getId());
    }

    @Test
    void fullDiamondSetWithOnePatternGrantsItsAbility() {
        assertEquals(Optional.of(TrimAbility.SENTRY), diamondOnly.fullSet(set("DIAMOND", "minecraft:sentry")));
        assertEquals(Optional.of(TrimAbility.EYE), diamondOnly.fullSet(set("DIAMOND", "eye")));
    }

    @Test
    void mixedPatternsMissingPiecesAndOtherTiersGrantNothing() {
        List<TrimSetRules.Piece> mixed = set("DIAMOND", "minecraft:sentry");
        mixed.set(2, new TrimSetRules.Piece("DIAMOND_LEGGINGS", "minecraft:vex"));
        assertEquals(Optional.empty(), diamondOnly.fullSet(mixed));

        List<TrimSetRules.Piece> untrimmed = set("DIAMOND", "minecraft:sentry");
        untrimmed.set(0, new TrimSetRules.Piece("DIAMOND_HELMET", null));
        assertEquals(Optional.empty(), diamondOnly.fullSet(untrimmed));

        List<TrimSetRules.Piece> missing = set("DIAMOND", "minecraft:sentry");
        missing.set(3, null);
        assertEquals(Optional.empty(), diamondOnly.fullSet(missing));

        assertEquals(Optional.empty(), diamondOnly.fullSet(set("NETHERITE", "minecraft:sentry")));
        List<TrimSetRules.Piece> oneNetherite = set("DIAMOND", "minecraft:sentry");
        oneNetherite.set(1, new TrimSetRules.Piece("NETHERITE_CHESTPLATE", "minecraft:sentry"));
        assertEquals(Optional.empty(), diamondOnly.fullSet(oneNetherite));
    }

    @Test
    void unknownPatternsGrantNothing() {
        assertEquals(Optional.empty(), diamondOnly.fullSet(set("DIAMOND", "custom:mystery")));
    }

    @Test
    void everyVanillaPatternHasOneAbility() {
        List<String> patterns = List.of("sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout", "rib",
            "spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt");
        for (String pattern : patterns) {
            assertTrue(TrimAbility.byPattern(pattern).isPresent(), pattern);
        }
        assertEquals(patterns.size(), TrimAbility.values().length);
        assertEquals(patterns.size(), Arrays.stream(TrimAbility.values()).map(TrimAbility::patternId).distinct().count());
    }

    @Test
    void damagePerksOnlyAffectTheirKind() {
        TrimAbility.Perk sentry = TrimAbility.SENTRY.perk();
        assertEquals(8.5, sentry.adjustDamage(TrimAbility.DamageKind.PROJECTILE, 10.0, 0.5), 1.0E-9);
        assertEquals(10.0, sentry.adjustDamage(TrimAbility.DamageKind.MELEE, 10.0, 0.5), 1.0E-9);
        assertEquals(10.0, sentry.adjustDamage(null, 10.0, 0.5), 1.0E-9);

        TrimAbility.Perk vex = TrimAbility.VEX.perk();
        assertEquals(0.0, vex.adjustDamage(TrimAbility.DamageKind.MELEE, 6.0, 0.01), 1.0E-9);
        assertEquals(6.0, vex.adjustDamage(TrimAbility.DamageKind.MELEE, 6.0, 0.5), 1.0E-9);
        assertEquals(6.0, vex.adjustDamage(TrimAbility.DamageKind.PROJECTILE, 6.0, 0.01), 1.0E-9);
    }

    @Test
    void settingsDefaultToDiamond() {
        TrimAbilitySettings loaded = TrimAbilitySettings.load(new YamlConfiguration());
        assertEquals(Set.of("DIAMOND"), loaded.tiers());
        assertEquals(20L, loaded.checkIntervalTicks());
    }

    private static List<TrimSetRules.Piece> set(String tier, String pattern) {
        return new ArrayList<>(List.of(
            new TrimSetRules.Piece(tier + "_HELMET", pattern),
            new TrimSetRules.Piece(tier + "_CHESTPLATE", pattern),
            new TrimSetRules.Piece(tier + "_LEGGINGS", pattern),
            new TrimSetRules.Piece(tier + "_BOOTS", pattern)));
    }
}
