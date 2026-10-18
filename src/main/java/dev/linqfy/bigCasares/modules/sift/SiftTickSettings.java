package dev.linqfy.bigCasares.modules.sift;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * The switches of the server-side substitutes for behaviour the mod gets from random ticks, fall callbacks and its
 * client: {@code the-sift.random-ticks}, {@code the-sift.crop-trampling} and {@code the-sift.dynamic-lights}. They
 * are kept apart from {@link SiftSettings} so that adding one does not touch that record. A value that is not valid
 * falls back to its default with a warning.
 *
 * @param randomTicks                 sample random ticks for Siftslate Growth, Dry Healthy Sculk Growth and willow foliage
 * @param samplesPerSectionPerSecond  the sampler's rate per 16x16x16 section
 * @param cropTrampling               Healthy Sculk destroys crops a player lands on
 * @param dynamicLights               light blocks that follow the Singer and a carried Soul Block
 */
record SiftTickSettings(boolean randomTicks, int samplesPerSectionPerSecond, boolean cropTrampling, boolean dynamicLights,
                        List<String> warnings) {

    static final int MIN_SAMPLES = 1;
    static final int MAX_SAMPLES = 60;

    SiftTickSettings {
        warnings = List.copyOf(warnings);
    }

    static SiftTickSettings defaults() {
        return load(null);
    }

    /** Reads the {@code the-sift} section of the plugin configuration (null reads the defaults). */
    static SiftTickSettings load(ConfigurationSection root) {
        List<String> warnings = new ArrayList<>();
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("the-sift");
        boolean randomTicks = section == null || section.getBoolean("random-ticks.enabled", true);
        int samples = SiftRandomTicks.DEFAULT_SAMPLES_PER_SECTION;
        String key = "random-ticks.samples-per-section-per-second";
        if (section != null && section.isSet(key)) {
            if (!section.isInt(key)) {
                warnings.add("the-sift." + key + " is not a whole number; using " + samples);
            } else if (section.getInt(key) < MIN_SAMPLES || section.getInt(key) > MAX_SAMPLES) {
                warnings.add("the-sift." + key + " must be within [" + MIN_SAMPLES + ", " + MAX_SAMPLES + "]; using " + samples);
            } else {
                samples = section.getInt(key);
            }
        }
        return new SiftTickSettings(randomTicks, samples, section == null || section.getBoolean("crop-trampling.enabled", true),
            section == null || section.getBoolean("dynamic-lights.enabled", true), warnings);
    }
}
