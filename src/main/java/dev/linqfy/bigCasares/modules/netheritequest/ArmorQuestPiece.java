package dev.linqfy.bigCasares.modules.netheritequest;

import java.util.Locale;
import java.util.Optional;

/**
 * The four netherite armor quests. Kill quests name the vanilla entity key they count; the boots quest counts
 * blocks walked instead ({@link #killTarget()} is empty).
 */
public enum ArmorQuestPiece {

    HELMET("casco", "Casco de Netherita", "elder_guardian", "Guardianes Ancianos", "respiration", 3, 3),
    CHESTPLATE("peto", "Peto de Netherita", "creeper", "Creepers", "blast_protection", 4, 500),
    LEGGINGS("pantalones", "Pantalones de Netherita", "warden", "Wardens", "swift_sneak", 3, 3),
    BOOTS("botas", "Botas de Netherita", null, "bloques caminados", "unbreaking", 2, 40_000);

    private final String id;
    private final String displayName;
    private final String killTarget;
    private final String objectiveName;
    private final String enchantmentKey;
    private final int enchantmentLevel;
    private final int defaultTarget;

    ArmorQuestPiece(
        String id,
        String displayName,
        String killTarget,
        String objectiveName,
        String enchantmentKey,
        int enchantmentLevel,
        int defaultTarget
    ) {
        this.id = id;
        this.displayName = displayName;
        this.killTarget = killTarget;
        this.objectiveName = objectiveName;
        this.enchantmentKey = enchantmentKey;
        this.enchantmentLevel = enchantmentLevel;
        this.defaultTarget = defaultTarget;
    }

    /** Stable id used in commands, config and the data file. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Vanilla entity key (without namespace) whose kills count, or empty for the walking quest. */
    public Optional<String> killTarget() {
        return Optional.ofNullable(killTarget);
    }

    public String objectiveName() {
        return objectiveName;
    }

    public String enchantmentKey() {
        return enchantmentKey;
    }

    public int enchantmentLevel() {
        return enchantmentLevel;
    }

    public int defaultTarget() {
        return defaultTarget;
    }

    public static Optional<ArmorQuestPiece> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ArmorQuestPiece piece : values()) {
            if (piece.id.equals(normalized) || piece.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(piece);
            }
        }
        return Optional.empty();
    }
}
