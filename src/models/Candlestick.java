package models;

public class Candlestick {

    // Field lengths
    public static final int LEN_TIMESTAMP = 13;
    public static final int LEN_LOW = 10;
    public static final int LEN_HIGH = 10;
    public static final int LEN_OPEN = 10;
    public static final int LEN_CLOSE = 10;
    public static final int LEN_VOLUME = 12;

    // Total record length
    public static final int RECORD_LENGTH =
            LEN_TIMESTAMP +
            LEN_LOW +
            LEN_HIGH +
            LEN_OPEN +
            LEN_CLOSE +
            LEN_VOLUME;

    private long timestamp;
    private long low;
    private long high;
    private long open;
    private long close;
    private long volume;

    // Empty constructor
    public Candlestick() {}

    // Full constructor
    public Candlestick(long timestamp, long low, long high,
                       long open, long close, long volume) {

        this.timestamp = timestamp;
        this.low = low;
        this.high = high;
        this.open = open;
        this.close = close;
        this.volume = volume;
    }

    // Record constructor
    public Candlestick(String record) {

        if (record.length() < RECORD_LENGTH) {
            throw new IllegalArgumentException("Invalid candlestick record length");
        }

        int pos = 0;

        timestamp = Long.parseLong(record.substring(pos, pos += LEN_TIMESTAMP).trim());
        low = Long.parseLong(record.substring(pos, pos += LEN_LOW).trim());
        high = Long.parseLong(record.substring(pos, pos += LEN_HIGH).trim());
        open = Long.parseLong(record.substring(pos, pos += LEN_OPEN).trim());
        close = Long.parseLong(record.substring(pos, pos += LEN_CLOSE).trim());
        volume = Long.parseLong(record.substring(pos, pos += LEN_VOLUME).trim());
    }

    private String fixed(String value, int length) {
        if (value == null) value = "";
        if (value.length() > length) {
            return value.substring(0, length);
        }
        return String.format("%-" + length + "s", value);
    }

    @Override
    public String toString() {

        return
            fixed(String.valueOf(timestamp), LEN_TIMESTAMP) +
            fixed(String.valueOf(low), LEN_LOW) +
            fixed(String.valueOf(high), LEN_HIGH) +
            fixed(String.valueOf(open), LEN_OPEN) +
            fixed(String.valueOf(close), LEN_CLOSE) +
            fixed(String.valueOf(volume), LEN_VOLUME);
    }

    // Getters & setters

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public long getLow() { return low; }
    public void setLow(long low) { this.low = low; }

    public long getHigh() { return high; }
    public void setHigh(long high) { this.high = high; }

    public long getOpen() { return open; }
    public void setOpen(long open) { this.open = open; }

    public long getClose() { return close; }
    public void setClose(long close) { this.close = close; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }
}