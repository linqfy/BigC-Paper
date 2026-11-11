package dev.linqfy.bigCasares.modules.auctionhouse;

import java.time.Duration;
import java.util.Locale;

/** Text helpers for prices and remaining time. */
public final class AuctionFormat {

    private AuctionFormat() {
    }

    public static String money(long amount) {
        return "$" + String.format(Locale.ROOT, "%,d", amount);
    }

    public static String remaining(Duration left) {
        if (left.isNegative() || left.isZero()) {
            return "expirada";
        }
        long days = left.toDays();
        long hours = left.toHoursPart();
        long minutes = left.toMinutesPart();
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1, minutes) + "m";
    }

    /** Parses a positive whole price such as {@code 1500}, {@code 1,500}, {@code 2k} or {@code 1.5m}. */
    public static long parsePrice(String raw) {
        String text = raw.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "").replace("$", "");
        if (text.isEmpty()) {
            throw new NumberFormatException("empty price");
        }
        long multiplier = switch (text.charAt(text.length() - 1)) {
            case 'k' -> 1_000L;
            case 'm' -> 1_000_000L;
            case 'b' -> 1_000_000_000L;
            default -> 1L;
        };
        if (multiplier > 1) {
            text = text.substring(0, text.length() - 1);
        }
        java.math.BigDecimal value = new java.math.BigDecimal(text).multiply(java.math.BigDecimal.valueOf(multiplier));
        if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 0) {
            throw new NumberFormatException("price must be a positive whole number");
        }
        return value.longValueExact();
    }
}
