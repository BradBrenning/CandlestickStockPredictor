package models;

public class Product {

	// Field lengths in bytes for set file length per product entry
    public static final int LEN_PRODUCT_ID = 12;
	public static final int LEN_HIGH = 10;
	public static final int LEN_LOW = 10;
	public static final int LEN_VOLUME_30DAY = 12;
    public static final int LEN_SERVICE_API = 8;
	public static final int LEN_IS_TRADEABLE = 1;
	public static final int LEN_SHOULD_TEST = 1;
	public static final int LEN_OVERRIDDEN = 1;
	public static final int LEN_HIST_START = 13;
	public static final int LEN_HIST_END = 13;
    public static final int LEN_MODIFIED = 13;

	// Total record length for one product
	public static final int RECORD_LENGTH = LEN_PRODUCT_ID +
	    									LEN_HIGH +
	    									LEN_LOW +
	    									LEN_VOLUME_30DAY +
	    									LEN_SERVICE_API +
	    									LEN_IS_TRADEABLE +
	    									LEN_SHOULD_TEST +
	    									LEN_OVERRIDDEN +
	    									LEN_HIST_START +
	    									LEN_HIST_END +
	    									LEN_MODIFIED;

	private String product_id;		// Name of tradeable product
	private long high;				// Highest value hit in past 30 days
	private long low;				// Lowest value hit in past 30 days
	private long volume_30day;		// Volume of trades in past 30 days
	private String service_API;		// The API used to trade stock
	private boolean is_tradeable;	// Is currently tradable on API
	private boolean should_test;	// Determined if worth training into neural network
	private boolean overridden;		// User overriding should_test decision
	private long hist_start;		// Unix time of furthest reachable 1-minute interval back
	private long hist_end;			// Unix time for closest 1-minute interval we have
	private long modified;			// Unix time for when the last modification was made to product

	// Constructor which is used if data is pulled from stored info
	public Product(String record) {
		if (record == null) record = "";
		// Ensure record has expected length by padding with spaces
		if (record.length() < RECORD_LENGTH) {
			record = String.format("%-" + RECORD_LENGTH + "s", record);
		}

		int pos = 0;

		product_id = record.substring(pos, pos + LEN_PRODUCT_ID).trim();
		pos += LEN_PRODUCT_ID;

		high = parseLongSafe(record.substring(pos, pos + LEN_HIGH));
		pos += LEN_HIGH;

		low = parseLongSafe(record.substring(pos, pos + LEN_LOW));
		pos += LEN_LOW;

		volume_30day = parseLongSafe(record.substring(pos, pos + LEN_VOLUME_30DAY));
		pos += LEN_VOLUME_30DAY;

		service_API = record.substring(pos, pos + LEN_SERVICE_API).trim();
		pos += LEN_SERVICE_API;

		String t;
		t = record.substring(pos, pos + LEN_IS_TRADEABLE).trim();
		is_tradeable = "1".equals(t);
		pos += LEN_IS_TRADEABLE;

		t = record.substring(pos, pos + LEN_SHOULD_TEST).trim();
		should_test = "1".equals(t);
		pos += LEN_SHOULD_TEST;

		t = record.substring(pos, pos + LEN_OVERRIDDEN).trim();
		overridden = "1".equals(t);
		pos += LEN_OVERRIDDEN;

		hist_start = parseLongSafe(record.substring(pos, pos + LEN_HIST_START));
		pos += LEN_HIST_START;

		hist_end = parseLongSafe(record.substring(pos, pos + LEN_HIST_END));
		pos += LEN_HIST_END;

		modified = parseLongSafe(record.substring(pos, pos + LEN_MODIFIED));
		pos += LEN_MODIFIED;
	}

	private long parseLongSafe(String s) {
		if (s == null) return 0L;
		String t = s.trim();
		if (t.isEmpty()) return 0L;
		try {
			return Long.parseLong(t);
		} catch (NumberFormatException ex) {
			return 0L;
		}
	}

	// Returns a fixed-length record string exactly RECORD_LENGTH characters long
	public String toRecordString() {
		StringBuilder sb = new StringBuilder();
		sb.append(fixed(product_id, LEN_PRODUCT_ID));
		sb.append(fixed(String.valueOf(high), LEN_HIGH));
		sb.append(fixed(String.valueOf(low), LEN_LOW));
		sb.append(fixed(String.valueOf(volume_30day), LEN_VOLUME_30DAY));
		sb.append(fixed(service_API, LEN_SERVICE_API));
		sb.append(is_tradeable ? "1" : "0");
		sb.append(should_test ? "1" : "0");
		sb.append(overridden ? "1" : "0");
		sb.append(fixed(String.valueOf(hist_start), LEN_HIST_START));
		sb.append(fixed(String.valueOf(hist_end), LEN_HIST_END));
		sb.append(fixed(String.valueOf(modified), LEN_MODIFIED));
		String rec = sb.toString();
		if (rec.length() > RECORD_LENGTH) return rec.substring(0, RECORD_LENGTH);
		return String.format("%-" + RECORD_LENGTH + "s", rec);
	}

	// Constructor from fields
	public Product(String product_id,
				   long high,
				   long low,
				   long volume_30day,
				   String service_API,
				   boolean is_tradeable,
				   boolean should_test,
				   boolean overridden,
				   long hist_start,
				   long hist_end,
				   long modified) {
		this.product_id = product_id;
		this.high = high;
		this.low = low;
		this.volume_30day = volume_30day;
		this.service_API = service_API;
		this.is_tradeable = is_tradeable;
		this.should_test = should_test;
		this.overridden = overridden;
		this.hist_start = hist_start;
		this.hist_end = hist_end;
		this.modified = modified;
	}

	// Helper function to enforce fixed-width fields
	private String fixed(String value, int length) {
		if (value == null) value = "";
		if (value.length() > length) {
			return value.substring(0, length);
		}
		return String.format("%-" + length + "s", value);
	}

	// Converts product to fixed-width record for file storage
	@Override
	public String toString() {
		return fixed(product_id, LEN_PRODUCT_ID) +
			   fixed(String.valueOf(high), LEN_HIGH) +
			   fixed(String.valueOf(low), LEN_LOW) +
			   fixed(String.valueOf(volume_30day), LEN_VOLUME_30DAY) +
			   fixed(service_API, LEN_SERVICE_API) +
			   (is_tradeable ? "1" : "0") +
			   (should_test ? "1" : "0") +
			   (overridden ? "1" : "0") +
			   fixed(String.valueOf(hist_start), LEN_HIST_START) +
			   fixed(String.valueOf(hist_end), LEN_HIST_END) +
			   fixed(String.valueOf(modified), LEN_MODIFIED);
	}

	// Getters for view access
	public String getProductId() { return product_id; }
	public long getHigh() { return high; }
	public long getLow() { return low; }
	public long getVolume30Day() { return volume_30day; }
	public String getServiceAPI() { return service_API; }
	public boolean isTradeable() { return is_tradeable; }
	public boolean shouldTest() { return should_test; }
	public boolean isOverridden() { return overridden; }
	public long getHistStart() { return hist_start; }
	public long getHistEnd() { return hist_end; }
	public long getModified() { return modified; }
}