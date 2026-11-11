package dev.linqfy.bigCasares.modules.phasearrows;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @param maxThickness   thickest wall, in blocks, a spectral arrow can cross
 * @param maxPhases      walls one arrow can cross in its flight
 * @param playersOnly    only arrows shot by players phase
 * @param unphaseable    material names that stop the arrow normally
 */
record PhaseArrowSettings(double maxThickness, int maxPhases, boolean playersOnly, Set<String> unphaseable) {

    static PhaseArrowSettings defaults() {
        return new PhaseArrowSettings(2.0, 1, true,
            Set.of("BEDROCK", "BARRIER", "END_PORTAL_FRAME", "REINFORCED_DEEPSLATE", "OBSIDIAN", "CRYING_OBSIDIAN"));
    }

    static PhaseArrowSettings load(FileConfiguration config) {
        PhaseArrowSettings defaults = defaults();
        double thickness = config.getDouble("phase-arrows.max-thickness", defaults.maxThickness());
        int phases = config.getInt("phase-arrows.max-phases", defaults.maxPhases());
        List<String> blocked = config.getStringList("phase-arrows.unphaseable-blocks");
        return new PhaseArrowSettings(
            Math.max(0.0, Math.min(thickness, 8.0)),
            Math.max(0, phases),
            config.getBoolean("phase-arrows.players-only", defaults.playersOnly()),
            blocked.isEmpty()
                ? defaults.unphaseable()
                : blocked.stream().map(name -> name.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet())
        );
    }
}
