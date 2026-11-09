package dev.linqfy.bigCasares.modules.staffevents;

import java.util.Locale;
import java.util.Optional;

/** A reward entry {@code id[:amount]}: a catalog id ({@code nuke_shot}) or a vanilla material ({@code minecraft:diamond}). */
public record RewardItem(String id, int amount) {

    public RewardItem {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (amount <= 0 || amount > 64 * 36) {
            throw new IllegalArgumentException("amount out of range");
        }
    }

    public boolean vanilla() {
        return id.startsWith("minecraft:");
    }

    public static Optional<RewardItem> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int separator = value.lastIndexOf(':');
        String id = value;
        int amount = 1;
        // "minecraft:diamond" has a namespace colon; only a trailing number is an amount.
        if (separator > 0 && separator < value.length() - 1) {
            String tail = value.substring(separator + 1);
            if (tail.chars().allMatch(Character::isDigit)) {
                id = value.substring(0, separator);
                try {
                    amount = Integer.parseInt(tail);
                } catch (NumberFormatException overflow) {
                    return Optional.empty();
                }
            }
        }
        if (id.isBlank() || !id.matches("[a-z0-9_.:/-]+")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new RewardItem(id, amount));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
