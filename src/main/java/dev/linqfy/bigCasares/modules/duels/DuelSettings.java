package dev.linqfy.bigCasares.modules.duels;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @param worldName             the separate void world that hosts every arena
 * @param requestSeconds        how long a challenge waits for an answer
 * @param draftSeconds          time to ban and to pick in the kit menu (each phase)
 * @param countdownSeconds      frozen countdown before the fight
 * @param maxDurationSeconds    a fight still running after this is a draw
 * @param returnDelaySeconds    pause between the result and sending players home
 * @param maxWager              highest bet allowed (0 disables wagers)
 * @param defaultArena          arena used when the challenge does not name one
 * @param kits                  kits by id, in config order
 */
record DuelSettings(
    String worldName,
    int requestSeconds,
    int draftSeconds,
    int countdownSeconds,
    int maxDurationSeconds,
    int returnDelaySeconds,
    long maxWager,
    DuelArenaSize defaultArena,
    Map<String, DuelKit> kits
) {

    DuelSettings {
        kits = Collections.unmodifiableMap(new LinkedHashMap<>(kits));
    }

    List<String> kitIds() {
        return new ArrayList<>(kits.keySet());
    }

    static DuelSettings load(FileConfiguration config, Consumer<String> warnings) {
        Map<String, DuelKit> kits = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("duels.kits");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection kit = section.getConfigurationSection(id);
                if (kit == null) {
                    continue;
                }
                try {
                    DuelKit loaded = DuelKit.load(id, kit);
                    kits.put(loaded.id(), loaded);
                } catch (IllegalArgumentException invalid) {
                    warnings.accept("[Duelos] Kit '" + id + "' ignorado: " + invalid.getMessage());
                }
            }
        }
        return new DuelSettings(
            config.getString("duels.world", "bigcasares_duels"),
            Math.max(10, config.getInt("duels.request-seconds", 60)),
            Math.max(5, config.getInt("duels.draft-seconds", 20)),
            Math.max(0, config.getInt("duels.countdown-seconds", 5)),
            Math.max(30, config.getInt("duels.max-duration-seconds", 300)),
            Math.max(1, config.getInt("duels.return-delay-seconds", 3)),
            Math.max(0L, config.getLong("duels.max-wager", 1_000_000L)),
            DuelArenaSize.parse(config.getString("duels.default-arena", "media")).orElse(DuelArenaSize.MEDIANA),
            kits
        );
    }
}
