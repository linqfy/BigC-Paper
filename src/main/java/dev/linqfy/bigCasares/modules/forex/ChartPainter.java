package dev.linqfy.bigCasares.modules.forex;

import org.bukkit.map.MapFont;
import org.bukkit.map.MinecraftFont;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Draws a trading-terminal candlestick chart into a 128×128 RGB buffer, the size of a Minecraft map:
 * header with ticker, price and change, candles with wicks, a price axis with the current-price tag,
 * volume bars and a status line. The map palette later snaps every color to its nearest map color.
 */
final class ChartPainter {

    static final int SIZE = 128;
    static final int BACKGROUND = 0x111111;
    static final int GRID = 0x2E2E2E;
    static final int TEXT = 0xFFFFFF;
    static final int MUTED = 0x8F8F8F;
    static final int TITLE = 0xFAEE4D;
    static final int UP = 0x00D93A;
    static final int DOWN = 0xE02020;
    static final int UP_VOLUME = 0x3B6E1F;
    static final int DOWN_VOLUME = 0x6E1F1F;
    static final int BUBBLE = 0xB24CD8;

    static final int CHART_LEFT = 1;
    static final int CHART_RIGHT = 101;
    static final int CHART_TOP = 22;
    static final int CHART_BOTTOM = 97;
    static final int VOLUME_TOP = 101;
    static final int VOLUME_BOTTOM = 115;
    static final int SLOT = 4;
    static final int MAX_CANDLES = (CHART_RIGHT - CHART_LEFT + 1) / SLOT;

    /** What to draw. {@code banner} is written across a dead chart, for example "RUG PULL". */
    record Chart(String title, String price, List<Candle> candles, String timeframe, String status, int statusColor,
                 String banner) {
    }

    private static final MapFont FONT = MinecraftFont.Font;

    private final int[] pixels = new int[SIZE * SIZE];

    static int[] paint(Chart chart) {
        ChartPainter painter = new ChartPainter();
        painter.draw(chart);
        return painter.pixels;
    }

    private void draw(Chart chart) {
        Arrays.fill(pixels, BACKGROUND);
        List<Candle> candles = chart.candles().subList(Math.max(0, chart.candles().size() - MAX_CANDLES),
            chart.candles().size());
        double change = candles.isEmpty() || candles.getFirst().open() <= 0 ? 0
            : candles.getLast().close() / candles.getFirst().open() - 1.0;
        boolean dead = chart.banner() != null && !chart.banner().isBlank();
        int changeColor = change >= 0 && !dead ? UP : DOWN;

        text(2, 1, chart.title(), TITLE);
        String changeText = dead ? "-100%"
            : (change >= 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f%%", change * 100);
        text(SIZE - 2 - width(changeText), 1, changeText, changeColor);
        text(2, 11, chart.price(), TEXT);
        text(SIZE - 2 - width(chart.timeframe()), 11, chart.timeframe(), MUTED);
        horizontal(0, SIZE - 1, 20, GRID);

        if (candles.isEmpty()) {
            text((SIZE - width("SIN DATOS")) / 2, 56, "SIN DATOS", MUTED);
        } else {
            drawCandles(candles);
        }

        horizontal(0, SIZE - 1, 118, GRID);
        text(2, 120, chart.status(), chart.statusColor());

        if (dead) {
            int bannerWidth = width(chart.banner());
            int left = (SIZE - bannerWidth) / 2 - 4;
            fill(left, 52, left + bannerWidth + 7, 64, DOWN);
            text(left + 4, 54, chart.banner(), TEXT);
        }
    }

    private void drawCandles(List<Candle> candles) {
        double high = candles.stream().mapToDouble(Candle::high).max().orElse(1);
        double low = candles.stream().mapToDouble(Candle::low).min().orElse(0);
        double pad = high - low < Math.abs(high) * 1e-6 ? Math.max(Math.abs(high) * 0.01, 1e-12) : (high - low) * 0.08;
        double top = high + pad;
        double bottom = Math.max(0, low - pad);
        if (top <= bottom) {
            top = bottom + 1e-12;
        }

        for (int level = 0; level <= 4; level++) {
            int y = CHART_TOP + (CHART_BOTTOM - CHART_TOP) * level / 4;
            for (int x = CHART_LEFT; x <= CHART_RIGHT; x += 2) {
                set(x, y, GRID);
            }
        }
        int firstSlot = CHART_RIGHT + 1 - candles.size() * SLOT;
        for (int index = 0; index < candles.size(); index += 6) {
            int x = firstSlot + index * SLOT + 1;
            for (int y = CHART_TOP; y <= VOLUME_BOTTOM; y += 2) {
                set(x, y, GRID);
            }
        }

        long maxVolume = Math.max(1, candles.stream().mapToLong(Candle::volume).max().orElse(1));
        for (int index = 0; index < candles.size(); index++) {
            Candle candle = candles.get(index);
            int left = firstSlot + index * SLOT;
            int color = candle.rising() ? UP : DOWN;
            int wickX = left + 1;
            vertical(wickX, y(candle.high(), top, bottom), y(candle.low(), top, bottom), color);
            int bodyTop = y(Math.max(candle.open(), candle.close()), top, bottom);
            int bodyBottom = y(Math.min(candle.open(), candle.close()), top, bottom);
            fill(left, bodyTop, left + SLOT - 2, bodyBottom, color);
            if (candle.volume() > 0) {
                int height = (int) Math.max(1, Math.round((double) candle.volume() / maxVolume
                    * (VOLUME_BOTTOM - VOLUME_TOP)));
                fill(left, VOLUME_BOTTOM - height, left + SLOT - 2, VOLUME_BOTTOM,
                    candle.rising() ? UP_VOLUME : DOWN_VOLUME);
            }
        }

        Candle last = candles.getLast();
        int lastY = y(last.close(), top, bottom);
        int tagColor = last.rising() ? UP : DOWN;
        for (int x = CHART_LEFT; x <= CHART_RIGHT; x += 3) {
            set(x, lastY, tagColor);
        }
        int tagTop = Math.max(CHART_TOP - 1, Math.min(CHART_BOTTOM - 8, lastY - 4));
        // The axis labels give way to the current-price tag when they would overlap.
        if (tagTop > CHART_TOP + 8) {
            text(CHART_RIGHT + 3, CHART_TOP - 1, compact(top), MUTED);
        }
        if (tagTop + 9 < CHART_BOTTOM - 7) {
            text(CHART_RIGHT + 3, CHART_BOTTOM - 7, compact(bottom), MUTED);
        }
        fill(CHART_RIGHT + 1, tagTop, SIZE - 1, tagTop + 9, tagColor);
        text(CHART_RIGHT + 3, tagTop + 1, compact(last.close()), BACKGROUND);
    }

    private static int y(double value, double top, double bottom) {
        double ratio = (top - value) / (top - bottom);
        return CHART_TOP + (int) Math.round(Math.max(0, Math.min(1, ratio)) * (CHART_BOTTOM - CHART_TOP));
    }

    /** At most four characters: {@code 1.2M}, {@code 12k}, {@code 123}, {@code 1.23}, {@code .012}, {@code 1e-5}. */
    static String compact(double value) {
        double abs = Math.abs(value);
        if (abs >= 1e9) {
            return trim(value / 1e9) + "G";
        }
        if (abs >= 1e6) {
            return trim(value / 1e6) + "M";
        }
        if (abs >= 1e3) {
            return trim(value / 1e3) + "k";
        }
        if (abs >= 100) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        if (abs >= 10) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        if (abs >= 1) {
            return String.format(Locale.ROOT, "%.2f", value);
        }
        if (abs >= 0.01) {
            return String.format(Locale.ROOT, "%.3f", value).substring(1);
        }
        if (abs == 0) {
            return "0";
        }
        int exponent = (int) Math.floor(Math.log10(abs));
        return Math.round(value / Math.pow(10, exponent)) + "e" + exponent;
    }

    private static String trim(double scaled) {
        return String.format(Locale.ROOT, Math.abs(scaled) >= 10 ? "%.0f" : "%.1f", scaled);
    }

    static int width(String text) {
        int width = 0;
        for (char character : text.toCharArray()) {
            MapFont.CharacterSprite sprite = FONT.getChar(character);
            if (sprite != null) {
                width += sprite.getWidth() + 1;
            }
        }
        return Math.max(0, width - 1);
    }

    private void text(int x, int y, String text, int color) {
        int cursor = x;
        for (char character : text.toCharArray()) {
            MapFont.CharacterSprite sprite = FONT.getChar(character);
            if (sprite == null) {
                continue;
            }
            for (int row = 0; row < sprite.getHeight(); row++) {
                for (int column = 0; column < sprite.getWidth(); column++) {
                    if (sprite.get(row, column)) {
                        set(cursor + column, y + row, color);
                    }
                }
            }
            cursor += sprite.getWidth() + 1;
        }
    }

    private void horizontal(int fromX, int toX, int y, int color) {
        for (int x = fromX; x <= toX; x++) {
            set(x, y, color);
        }
    }

    private void vertical(int x, int fromY, int toY, int color) {
        for (int y = Math.min(fromY, toY); y <= Math.max(fromY, toY); y++) {
            set(x, y, color);
        }
    }

    private void fill(int fromX, int fromY, int toX, int toY, int color) {
        for (int y = Math.min(fromY, toY); y <= Math.max(fromY, toY); y++) {
            horizontal(fromX, toX, y, color);
        }
    }

    private void set(int x, int y, int color) {
        if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) {
            pixels[y * SIZE + x] = color;
        }
    }
}
