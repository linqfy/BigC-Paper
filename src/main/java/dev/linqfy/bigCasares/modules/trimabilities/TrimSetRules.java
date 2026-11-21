package dev.linqfy.bigCasares.modules.trimabilities;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Decides whether four worn armor pieces form a full trim set of an eligible tier. */
final class TrimSetRules {

    /**
     * One worn armor piece.
     *
     * @param material  Bukkit material name, e.g. {@code DIAMOND_HELMET}; null for an empty slot
     * @param patternId trim pattern key (with or without namespace); null when the piece has no trim
     */
    record Piece(String material, String patternId) {
    }

    private final Set<String> tiers;

    /** @param tiers material prefixes that qualify, e.g. {@code DIAMOND} for the pre-netherite tier */
    TrimSetRules(Set<String> tiers) {
        this.tiers = tiers.stream().map(tier -> tier.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    Optional<TrimAbility> fullSet(List<Piece> pieces) {
        if (pieces == null || pieces.size() != 4) {
            return Optional.empty();
        }
        String pattern = null;
        for (Piece piece : pieces) {
            if (piece == null || piece.material() == null || piece.patternId() == null || !eligible(piece.material())) {
                return Optional.empty();
            }
            String normalized = normalize(piece.patternId());
            if (pattern == null) {
                pattern = normalized;
            } else if (!pattern.equals(normalized)) {
                return Optional.empty();
            }
        }
        return TrimAbility.byPattern(pattern);
    }

    private boolean eligible(String material) {
        String upper = material.toUpperCase(Locale.ROOT);
        int underscore = upper.lastIndexOf('_');
        return underscore > 0 && tiers.contains(upper.substring(0, underscore));
    }

    private static String normalize(String patternId) {
        String lower = patternId.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        return colon >= 0 ? lower.substring(colon + 1) : lower;
    }
}
