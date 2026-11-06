package dev.linqfy.bigCasares.modules.missions;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Extra pay on claimed mission rewards, off by default: the streak ({@code mission-system.racha}: so much per day of
 * streak, up to a maximum) and the happy hour (its mission multiplier). They add up; the total is capped at +400%.
 *
 * @param perDay  extra per day of streak (0 to 0.2)
 * @param maximum the streak's cap (0 to 1)
 */
record MissionBonus(boolean streakEnabled, double perDay, double maximum) {
    static final MissionBonus OFF = new MissionBonus(false, 0, 0);
    static final double CAP = 4.0;

    MissionBonus {
        perDay = Math.max(0, Math.min(0.2, perDay));
        maximum = Math.max(0, Math.min(1, maximum));
    }

    static MissionBonus load(ConfigurationSection root) {
        ConfigurationSection section = root == null ? null : root.getConfigurationSection("mission-system.racha");
        return section == null || !section.getBoolean("enabled", false) ? OFF
            : new MissionBonus(true, section.getDouble("bono-por-dia", 0.05), section.getDouble("maximo", 0.5));
    }

    /** The extra share of the reward: streak days so far (before today's claim) and the happy hour's multiplier, if on. */
    double rate(int streakDays, double happyMultiplier) {
        double streak = streakEnabled ? Math.min(maximum, perDay * Math.max(0, streakDays)) : 0;
        double happy = Math.max(0, happyMultiplier - 1);
        return Math.min(CAP, streak + happy);
    }
}
