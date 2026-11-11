package dev.linqfy.bigCasares.modules.netheritequest;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Settings of the netherite quests.
 *
 * @param durationHours       time a quest stays open after activation
 * @param levelCostStep       extra XP levels each further activation costs (the first one is free)
 * @param goldBlockRecipe     whether netherite ingots need gold blocks instead of gold ingots
 * @param blockSmithingArmor  whether the vanilla diamond to netherite armor upgrade is disabled
 * @param maxStepBlocks       longest single movement that still counts as walking (larger jumps are teleports)
 * @param targets             amount each quest needs
 */
public record NetheriteQuestSettings(
    long durationHours,
    int levelCostStep,
    boolean goldBlockRecipe,
    boolean blockSmithingArmor,
    double maxStepBlocks,
    Map<ArmorQuestPiece, Integer> targets
) {

    private static final String ROOT = "netherite-quests.";

    public NetheriteQuestSettings {
        if (durationHours < 1L) {
            throw new IllegalArgumentException("duration-hours debe ser al menos 1.");
        }
        if (levelCostStep < 0) {
            throw new IllegalArgumentException("level-cost-step no puede ser negativo.");
        }
        if (!(maxStepBlocks > 0.0)) {
            throw new IllegalArgumentException("max-step-blocks debe ser positivo.");
        }
        EnumMap<ArmorQuestPiece, Integer> copy = new EnumMap<>(ArmorQuestPiece.class);
        for (ArmorQuestPiece piece : ArmorQuestPiece.values()) {
            int target = targets.getOrDefault(piece, piece.defaultTarget());
            if (target < 1) {
                throw new IllegalArgumentException("El objetivo de " + piece.id() + " debe ser al menos 1.");
            }
            copy.put(piece, target);
        }
        targets = Collections.unmodifiableMap(copy);
    }

    public static NetheriteQuestSettings defaults() {
        return new NetheriteQuestSettings(24L, 20, true, true, 10.0, Map.of());
    }

    public static NetheriteQuestSettings load(FileConfiguration config) {
        NetheriteQuestSettings defaults = defaults();
        long hours = config.getLong(ROOT + "duration-hours", defaults.durationHours());
        int step = config.getInt(ROOT + "level-cost-step", defaults.levelCostStep());
        double maxStep = config.getDouble(ROOT + "max-step-blocks", defaults.maxStepBlocks());
        EnumMap<ArmorQuestPiece, Integer> targets = new EnumMap<>(ArmorQuestPiece.class);
        for (ArmorQuestPiece piece : ArmorQuestPiece.values()) {
            int target = config.getInt(ROOT + "targets." + piece.id(), piece.defaultTarget());
            targets.put(piece, target < 1 ? piece.defaultTarget() : target);
        }
        return new NetheriteQuestSettings(
            hours < 1L ? defaults.durationHours() : hours,
            step < 0 ? defaults.levelCostStep() : step,
            config.getBoolean(ROOT + "gold-block-ingot-recipe", defaults.goldBlockRecipe()),
            config.getBoolean(ROOT + "block-smithing-armor", defaults.blockSmithingArmor()),
            maxStep > 0.0 ? maxStep : defaults.maxStepBlocks(),
            targets
        );
    }

    public int target(ArmorQuestPiece piece) {
        return targets.get(piece);
    }

    public long durationMillis() {
        return durationHours * 60L * 60L * 1000L;
    }
}
