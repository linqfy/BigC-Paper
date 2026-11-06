package dev.linqfy.bigCasares.modules.legendary;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Captures weapon at damage time; swapping equipment before death cannot steal XP. */
final class LegendaryKillCredits {
    record Credit(UUID killer, LegendaryWeapon weapon, long at) {}

    private final Map<UUID, Credit> credits = new HashMap<>();
    private final Map<UUID, Long> lives = new HashMap<>();

    long generation(UUID victim) {
        return lives.getOrDefault(victim, 0L);
    }

    void record(UUID victim, UUID killer, LegendaryWeapon weapon, long now) {
        record(victim, killer, weapon, now, generation(victim));
    }

    void record(UUID victim, UUID killer, LegendaryWeapon weapon, long now, long capturedLife) {
        if (capturedLife != generation(victim)) return;
        credits.entrySet().removeIf(entry -> now - entry.getValue().at() > 10_000);
        if (victim.equals(killer) || weapon == null || !weapon.isAvailable()) {
            clear(victim);
            return;
        }
        credits.put(victim, new Credit(killer, weapon, now));
    }

    Optional<Credit> consume(UUID victim, UUID nativeKiller, long now) {
        Credit credit = credits.remove(victim);
        lives.put(victim, generation(victim) + 1);
        return credit != null
                        && credit.killer().equals(nativeKiller)
                        && now - credit.at() >= 0
                        && now - credit.at() <= 10_000
                ? Optional.of(credit)
                : Optional.empty();
    }

    void clear(UUID victim) {
        credits.remove(victim);
    }
}
