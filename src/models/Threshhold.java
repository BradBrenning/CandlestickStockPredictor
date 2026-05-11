package models;

public class Threshhold {

    // Field lengths
    public static final int LEN_TIMESTAMP = 13;
    public static final int LEN_ACTUAL_OUTCOME = 1;
    public static final int LEN_HAS_TRAINED = 1;
    public static final int LEN_HAS_TESTED = 1;
    public static final int LEN_PRED_OUTCOME = 1;

    // Total record length
    public static final int RECORD_LENGTH =
            LEN_TIMESTAMP +
            LEN_ACTUAL_OUTCOME +
            LEN_HAS_TRAINED +
            LEN_HAS_TESTED +
            LEN_PRED_OUTCOME;

    private long timestamp;
    private boolean actual_outcome;
    private boolean has_trained;
    private boolean has_tested;
    private boolean pred_outcome;

    // Empty constructor
    public Threshhold() {}

    // Full constructor
    public Threshhold(long timestamp, boolean actual_outcome,
                      boolean has_trained, boolean has_tested,
                      boolean pred_outcome) {

        this.timestamp = timestamp;
        this.actual_outcome = actual_outcome;
        this.has_trained = has_trained;
        this.has_tested = has_tested;
        this.pred_outcome = pred_outcome;
    }

    // Record constructor
    public Threshhold(String record) {

        if (record.length() < RECORD_LENGTH) {
            throw new IllegalArgumentException("Invalid threshhold record length");
        }

        int pos = 0;

        timestamp = Long.parseLong(record.substring(pos, pos += LEN_TIMESTAMP).trim());
        actual_outcome = record.substring(pos, pos += LEN_ACTUAL_OUTCOME).equals("1");
        has_trained = record.substring(pos, pos += LEN_HAS_TRAINED).equals("1");
        has_tested = record.substring(pos, pos += LEN_HAS_TESTED).equals("1");
        pred_outcome = record.substring(pos, pos += LEN_PRED_OUTCOME).equals("1");
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
            (actual_outcome ? "1" : "0") +
            (has_trained ? "1" : "0") +
            (has_tested ? "1" : "0") +
            (pred_outcome ? "1" : "0");
    }

    // Getters & setters

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean getActualOutcome() { return actual_outcome; }
    public void setActualOutcome(boolean actual_outcome) { this.actual_outcome = actual_outcome; }

    public boolean getHasTrained() { return has_trained; }
    public void setHasTrained(boolean has_trained) { this.has_trained = has_trained; }

    public boolean getHasTested() { return has_tested; }
    public void setHasTested(boolean has_tested) { this.has_tested = has_tested; }

    public boolean getPredOutcome() { return pred_outcome; }
    public void setPredOutcome(boolean pred_outcome) { this.pred_outcome = pred_outcome; }
}