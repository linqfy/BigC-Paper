package dev.linqfy.bigCasares.modules.alexscaves.core.mob;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * One loot entry of a mob, as in the mod's entity loot tables: a uniform count
 * plus Looting (vanilla {@code looting_enchant}: up to +1 per level), an
 * optional chance and the "killed by player" condition.
 *
 * @param item catalog id of a module item or a vanilla material key
 */
public record MobDrop(String item, int min, int max, boolean looting, double chance, double lootingChanceBonus,
                      boolean requiresPlayer, int lootingMax) {
    /** A drop whose Looting bonus tops at +1 a level (the common {@code looting_enchant} of 0 to 1). */
    public MobDrop(String item, int min, int max, boolean looting, double chance, double lootingChanceBonus,
                   boolean requiresPlayer) {
        this(item, min, max, looting, chance, lootingChanceBonus, requiresPlayer, 1);
    }

    /**
     * Mod {@code looting_enchant} with a count of 0 to {@code lootingMax}: vanilla adds {@code round(level * random)}
     * where the random is uniform in that range.
     */
    public static MobDrop withLooting(String item, int min, int max, int lootingMax) {
        return new MobDrop(item, min, max, true, 1.0, 0.0, false, lootingMax);
    }

    public MobDrop {
        Objects.requireNonNull(item, "item");
        if (max < min || max < 0 || chance <= 0 || chance > 1 || lootingChanceBonus < 0) {
            throw new IllegalArgumentException("Invalid drop for " + item);
        }
    }

    public static MobDrop of(String item, int min, int max) {
        return new MobDrop(item, min, max, true, 1.0, 0.0, false);
    }

    /** Rare drop with the vanilla {@code random_chance_with_looting} condition (only when a player kills). */
    public static MobDrop rare(String item, double chance, double perLootingLevel) {
        return new MobDrop(item, 1, 1, false, chance, perLootingLevel, true);
    }

    /** Count to drop; 0 means nothing. Negative minimums (e.g. -2..1) make the drop less frequent, as in the mod. */
    public int roll(RandomGenerator random, int lootingLevel, boolean killedByPlayer) {
        if (requiresPlayer && !killedByPlayer) {
            return 0;
        }
        double effectiveChance = Math.min(1.0, chance + lootingChanceBonus * Math.max(0, lootingLevel));
        if (effectiveChance < 1.0 && random.nextDouble() >= effectiveChance) {
            return 0;
        }
        int count = min + random.nextInt(max - min + 1);
        if (looting && lootingLevel > 0) {
            count += lootingMax > 1 ? Math.round((float) (lootingLevel * random.nextDouble() * lootingMax))
                : random.nextInt(lootingLevel + 1);
        }
        return Math.max(0, count);
    }
}
