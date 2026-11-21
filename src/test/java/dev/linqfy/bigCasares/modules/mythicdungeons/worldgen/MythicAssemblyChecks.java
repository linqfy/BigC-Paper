package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Public entry points to the worldgen checks for the third-party tests (the helpers are package-private). */
public final class MythicAssemblyChecks {

    private MythicAssemblyChecks() {
    }

    private static MythicDungeonTheme theme(String culture) {
        return MythicDungeonTheme.all().stream().filter(theme -> theme.culture().equals(culture)).findFirst().orElseThrow();
    }

    /**
     * Every template of the culture: references, vanilla ids and block entities, the altar and arena-footprint
     * rule, and the per-template walkability and clearance rules (returned as problems).
     */
    public static List<String> checkTemplates(GeneratedDatapack pack, String culture) {
        MythicPackIndex index = new MythicPackIndex(pack, culture);
        MythicDungeonDatapackTest.checkReferences(index);
        MythicDungeonDatapackTest.checkTemplates(index);
        MythicDungeonDatapackTest.checkAltarAndFootprints(index, theme(culture));
        List<String> problems = new ArrayList<>();
        for (StructureTemplate template : index.cultureTemplates(culture)) {
            problems.addAll(MythicTemplateRules.violations(template));
        }
        return problems;
    }

    /** Runs the assembly simulation checks; returns the template paths the simulated dungeons used. */
    public static Set<String> simulate(GeneratedDatapack pack, String culture) {
        MythicJigsawAssemblyTest.Stats stats = MythicJigsawAssemblyTest.checkCulture(new MythicPackIndex(pack, culture), theme(culture));
        if (stats.medianPieces() < MythicJigsawAssemblyTest.MIN_MEDIAN_PIECES) {
            throw new AssertionError(culture + " " + stats);
        }
        return stats.used();
    }
}
