package dev.linqfy.bigCasares.modules.forex;

import java.util.Locale;
import java.util.function.DoubleUnaryOperator;

/** Parses the amounts players type: {@code 1.000}, {@code 10k}, {@code 2,5m}, {@code todo}, {@code 50%}. */
final class ForexAmounts {

    private ForexAmounts() {
    }

    /** Whole money. Dots are thousands separators unless a {@code k}/{@code m} suffix makes them decimals. */
    static long money(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace("$", "").replace("_", "");
        double multiplier = 1;
        if (text.endsWith("k")) {
            multiplier = 1_000;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("m")) {
            multiplier = 1_000_000;
            text = text.substring(0, text.length() - 1);
        }
        try {
            double value = multiplier == 1
                ? Long.parseLong(text.replace(".", "").replace(",", ""))
                : Double.parseDouble(text.replace(",", "."));
            long amount = (long) Math.floor(value * multiplier);
            if (amount <= 0 || !Double.isFinite(value)) {
                throw new ForexException("La plata tiene que ser positiva.");
            }
            return amount;
        } catch (NumberFormatException invalid) {
            throw new ForexException("No entiendo \"" + raw + "\". Probá con 1000, 1.000 o 10k.");
        }
    }

    /** A function of the available balance: {@code todo}, a percentage or a plain quantity. */
    static DoubleUnaryOperator quantity(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (text.equals("todo") || text.equals("all") || text.equals("max")) {
            return available -> available;
        }
        try {
            if (text.endsWith("%")) {
                double percent = Double.parseDouble(text.substring(0, text.length() - 1).replace(",", "."));
                if (!(percent > 0) || percent > 100) {
                    throw new ForexException("El porcentaje va de 0 a 100.");
                }
                return available -> available * percent / 100.0;
            }
            double value = Double.parseDouble(text.replace(",", "."));
            if (!(value > 0) || !Double.isFinite(value)) {
                throw new ForexException("La cantidad tiene que ser positiva.");
            }
            return available -> value;
        } catch (NumberFormatException invalid) {
            throw new ForexException("No entiendo \"" + raw + "\". Probá con 250, 50% o todo.");
        }
    }

    /** A percentage typed as {@code 4}, {@code 4%} or {@code 4,5}, as a fraction. */
    static double percent(String raw) {
        String text = raw == null ? "" : raw.trim().replace("%", "").replace(",", ".");
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException();
            }
            return value / 100.0;
        } catch (NumberFormatException invalid) {
            throw new ForexException("No entiendo \"" + raw + "\". Usá un porcentaje, por ejemplo 4.");
        }
    }
}
