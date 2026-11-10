package dev.linqfy.bigCasares.modules.forex;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartPainterTest {

    private static int pixel(int[] pixels, int x, int y) {
        return pixels[y * ChartPainter.SIZE + x];
    }

    private static int count(int[] pixels, int color, int fromY, int toY) {
        int found = 0;
        for (int y = fromY; y <= toY; y++) {
            for (int x = ChartPainter.CHART_LEFT; x <= ChartPainter.CHART_RIGHT; x++) {
                if (pixel(pixels, x, y) == color) {
                    found++;
                }
            }
        }
        return found;
    }

    @Test
    void risingCandlesAreGreenFallingOnesRedAndTheNewestSitsAtTheRightEdge() {
        List<Candle> candles = List.of(
            new Candle(10, 12, 9, 11, 100),
            new Candle(11, 11.5, 7, 8, 300));
        int[] pixels = ChartPainter.paint(new ChartPainter.Chart("$MOON", "$8", candles, "10m", "", 0, null));

        int lastLeft = ChartPainter.CHART_RIGHT + 1 - ChartPainter.SLOT;
        int previousLeft = lastLeft - ChartPainter.SLOT;
        assertTrue(columnHas(pixels, lastLeft, ChartPainter.DOWN), "falling candle body is red");
        assertTrue(columnHas(pixels, previousLeft, ChartPainter.UP), "rising candle body is green");
        assertTrue(count(pixels, ChartPainter.DOWN_VOLUME, ChartPainter.VOLUME_TOP, ChartPainter.VOLUME_BOTTOM)
            > count(pixels, ChartPainter.UP_VOLUME, ChartPainter.VOLUME_TOP, ChartPainter.VOLUME_BOTTOM),
            "the bigger volume gets the taller bar");
        assertTrue(lowestPixel(pixels, lastLeft + 1, ChartPainter.DOWN)
            > lowestPixel(pixels, previousLeft + 1, ChartPainter.UP), "the lower low reaches further down");
    }

    @Test
    void onlyTheLatestCandlesThatFitAreDrawn() {
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            candles.add(new Candle(i, i + 1, i, i + 1, 0));
        }
        int[] pixels = ChartPainter.paint(new ChartPainter.Chart("$UP", "$48", candles, "10m", "", 0, null));
        assertTrue(count(pixels, ChartPainter.UP, ChartPainter.CHART_TOP, ChartPainter.CHART_BOTTOM) > 0);
        assertEquals(25, ChartPainter.MAX_CANDLES);
    }

    @Test
    void anEmptyOrDeadChartStillRenders() {
        int[] empty = ChartPainter.paint(new ChartPainter.Chart("$NEW", "$1", List.of(), "10m", "POZO 0", 0, null));
        assertEquals(ChartPainter.SIZE * ChartPainter.SIZE, empty.length);
        int[] dead = ChartPainter.paint(new ChartPainter.Chart("$SCAM", "DELISTED",
            List.of(Candle.flat(5)), "10m", "", ChartPainter.DOWN, "RUG PULL"));
        assertEquals(ChartPainter.DOWN, pixel(dead, 64, 52), "the red banner crosses the middle");
    }

    @Test
    void axisLabelsFitInFourCharacters() {
        assertEquals("1.2M", ChartPainter.compact(1_234_567));
        assertEquals("12k", ChartPainter.compact(12_345));
        assertEquals("123", ChartPainter.compact(123.4));
        assertEquals("1.23", ChartPainter.compact(1.234));
        assertEquals(".012", ChartPainter.compact(0.0123));
        assertEquals("1e-5", ChartPainter.compact(0.00001));
        assertTrue(ChartPainter.width("1.2M") <= ChartPainter.SIZE - ChartPainter.CHART_RIGHT - 3);
    }

    private static boolean columnHas(int[] pixels, int x, int color) {
        for (int y = ChartPainter.CHART_TOP; y <= ChartPainter.CHART_BOTTOM; y++) {
            if (pixel(pixels, x, y) == color) {
                return true;
            }
        }
        return false;
    }

    private static int lowestPixel(int[] pixels, int x, int color) {
        int lowest = -1;
        for (int y = ChartPainter.CHART_TOP; y <= ChartPainter.CHART_BOTTOM; y++) {
            if (pixel(pixels, x, y) == color) {
                lowest = y;
            }
        }
        return lowest;
    }
}
