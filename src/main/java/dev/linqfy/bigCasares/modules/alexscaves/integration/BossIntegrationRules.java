package dev.linqfy.bigCasares.modules.alexscaves.integration;

import java.util.Set;

/** Pure rules for which module mobs count as bosses for the other systems. */
public final class BossIntegrationRules {
    /** Mod bosses: Luxtructosaurus, Forsaken, Hullbreaker and Tremorzilla (the last one only while wild). */
    public static final Set<String> BOSS_IDS = Set.of("luxtructosaurus", "forsaken", "hullbreaker", "tremorzilla");

    private BossIntegrationRules() {
    }

    /** A tamed Tremorzilla is a pet, not a boss: it neither tracks damage nor pays placements. */
    public static boolean counts(String mobId, boolean tamed) {
        return BOSS_IDS.contains(mobId) && !("tremorzilla".equals(mobId) && tamed);
    }
}
