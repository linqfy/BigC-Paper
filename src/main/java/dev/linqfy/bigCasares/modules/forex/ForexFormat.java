package dev.linqfy.bigCasares.modules.forex;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/** Money, prices and the chat sparkline, in the server's es-AR style. */
public final class ForexFormat {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR"));
    private static final char[] BARS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};

    private ForexFormat() {
    }

    /** {@code 12500} → {@code $12.500}. */
    public static String money(long amount) {
        return "$" + new DecimalFormat("#,##0", SYMBOLS).format(amount);
    }

    /** Prices keep enough decimals to show a collapsed coin is not exactly zero. */
    public static String price(double value) {
        if (value >= 100) {
            return "$" + new DecimalFormat("#,##0.00", SYMBOLS).format(value);
        }
        if (value >= 0.01) {
            return "$" + new DecimalFormat("0.0000", SYMBOLS).format(value);
        }
        return "$" + new DecimalFormat("0.0########", SYMBOLS).format(value);
    }

    public static String tokens(double value) {
        return new DecimalFormat(value >= 100 ? "#,##0" : "#,##0.####", SYMBOLS).format(value);
    }

    public static String percent(double fraction) {
        return new DecimalFormat("0.##", SYMBOLS).format(fraction * 100) + "%";
    }

    /** Colored signed change: {@code §a+3,2%} or {@code §c-12%}. */
    public static String change(double fraction) {
        String sign = fraction >= 0 ? "+" : "";
        String color = fraction > 0.0005 ? "§a" : fraction < -0.0005 ? "§c" : "§7";
        return color + sign + new DecimalFormat("0.#", SYMBOLS).format(fraction * 100) + "%";
    }

    public static String duration(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds % 60 + "s";
        }
        return seconds % 60 + "s";
    }

    /** Block-character chart of a price history, green when it ends above its start. */
    public static String sparkline(List<Double> history) {
        if (history.isEmpty()) {
            return "§8(sin historial)";
        }
        double min = history.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = history.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        StringBuilder line = new StringBuilder(history.getLast() >= history.getFirst() ? "§a" : "§c");
        for (double value : history) {
            int index = max - min < 1e-12 ? BARS.length / 2 : (int) Math.round((value - min) / (max - min) * (BARS.length - 1));
            line.append(BARS[Math.max(0, Math.min(BARS.length - 1, index))]);
        }
        return line.toString();
    }

    /** Change across a price history, or 0 when there is not enough of it. */
    public static double trend(List<Double> history) {
        if (history.size() < 2 || history.getFirst() <= 0) {
            return 0;
        }
        return history.getLast() / history.getFirst() - 1.0;
    }
}
