package dev.linqfy.bigCasares.modules.alexscaves.core.effect;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Text form stored in a player's PDC so module effects survive logout and
 * restarts: {@code type:amplifier:remaining:elapsed;...}. Unknown or broken
 * entries are skipped instead of failing the join.
 */
public final class ModEffectCodec {
    private ModEffectCodec() {
    }

    public static String encode(List<ModEffectLedger.ActiveEffect> effects) {
        StringJoiner joiner = new StringJoiner(";");
        for (ModEffectLedger.ActiveEffect effect : effects) {
            joiner.add(effect.type().id() + ":" + effect.amplifier() + ":" + effect.remainingTicks() + ":" + effect.elapsedTicks());
        }
        return joiner.toString();
    }

    public static List<ModEffectLedger.ActiveEffect> decode(String value) {
        List<ModEffectLedger.ActiveEffect> effects = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return effects;
        }
        for (String entry : value.split(";")) {
            String[] parts = entry.split(":");
            if (parts.length != 4) {
                continue;
            }
            try {
                ModEffectType type = ModEffectType.parse(parts[0]).orElse(null);
                int amplifier = Integer.parseInt(parts[1]);
                int remaining = Integer.parseInt(parts[2]);
                int elapsed = Integer.parseInt(parts[3]);
                if (type != null && amplifier >= 0 && remaining > 0 && elapsed >= 0) {
                    effects.add(new ModEffectLedger.ActiveEffect(type, amplifier, remaining, elapsed));
                }
            } catch (NumberFormatException ignored) {
                // Corrupted entry: drop it, keep the rest.
            }
        }
        return effects;
    }
}
