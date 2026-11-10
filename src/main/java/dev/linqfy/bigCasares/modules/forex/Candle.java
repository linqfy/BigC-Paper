package dev.linqfy.bigCasares.modules.forex;

/** One market cycle of an asset: open, high, low and close prices plus the money traded. */
public record Candle(double open, double high, double low, double close, long volume) {

    public static Candle flat(double price) {
        return new Candle(price, price, price, price, 0);
    }

    public boolean rising() {
        return close >= open;
    }

    Candle observe(double price) {
        return new Candle(open, Math.max(high, price), Math.min(low, price), price, volume);
    }

    Candle withVolume(long amount) {
        return new Candle(open, high, low, close, volume + amount);
    }

    /** {@code open;high;low;close;volume}, for the market file. */
    String encode() {
        return open + ";" + high + ";" + low + ";" + close + ";" + volume;
    }

    static Candle decode(String raw) {
        String[] parts = raw.split(";");
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid candle " + raw);
        }
        return new Candle(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
            Double.parseDouble(parts[3]), Long.parseLong(parts[4]));
    }
}
