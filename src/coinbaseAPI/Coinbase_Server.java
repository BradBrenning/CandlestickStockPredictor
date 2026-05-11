package coinbaseAPI;

import java.net.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.io.IOException;

public class Coinbase_Server
{

	private CallURL urlInfo;

	// Constructor for starting up server
	public Coinbase_Server () {
		urlInfo = new CallURL();
	}

	// Used to get candlesticks for specific stock/crypto and at what start time
	public void candlestickRequest (String product_id, long start_time) {
       services.Logger.log("Coinbase_Server.candlestickRequest: product=" + product_id + " start=" + start_time);
		Instant start = Instant.ofEpochMilli(start_time);
		String url = String.format("%s/%s/candles?start=%s&end=%s&granularity=%s",
	                			   "https://api.exchange.coinbase.com/products",
	                			   product_id,
	                			   start,
	                			   start.plus(300, ChronoUnit.MINUTES),
	                			   "60");
       if (!urlInfo.call(url)) {
            services.Logger.log("candlestickRequest: call failed for " + url);
			return;
		}
       services.Logger.log("candlestickRequest: bodyLen=" + (urlInfo.getBody() == null ? 0 : urlInfo.getBody().length()));
		return;
	}

	// Used to get all stock information
	public void stockInformation () {
		String url = "https://api.exchange.coinbase.com/products";
       services.Logger.log("Coinbase_Server.stockInformation: calling " + url);
	   if (!urlInfo.call(url)) {
            services.Logger.log("stockInformation: call failed");
			return;
		}
       services.Logger.log("stockInformation: bodyLen=" + (urlInfo.getBody() == null ? 0 : urlInfo.getBody().length()));
	}

	// Used to get direct info from known url
	// Used for testing purposes
	public void request (String url) {
		if (!urlInfo.call(url)) {
			System.out.println("Exception made");
			return;
		}
	}

	// Used to get body from CallURL
	public String getBody () {
       services.Logger.log("Coinbase_Server.getBody called");
		return urlInfo.getBody();
	}

	// Used to getResponseCode
	public int getResponseCode () {
       services.Logger.log("Coinbase_Server.getResponseCode called");
		return urlInfo.getResponseCode();
	}

	// Get aggregated stats for a single product from Coinbase
	public static class ProductStats {
		public final double high;
		public final double low;
		public final double volume30day;

		public ProductStats(double high, double low, double volume30day) {
			this.high = high;
			this.low = low;
			this.volume30day = volume30day;
		}
	}

	// Calls /products/{product_id}/stats and returns parsed high/low/volume_30day
	public ProductStats getProductStats(String productId) {
		if (productId == null || productId.isEmpty()) return null;
		String url = String.format("https://api.exchange.coinbase.com/products/%s/stats", productId);
		if (!urlInfo.call(url)) return null;
		String body = urlInfo.getBody();
		if (body == null) return null;

		// Simple parse for numeric fields (values are strings)
		double high = parseJsonNumberAsDouble(body, "high");
		double low = parseJsonNumberAsDouble(body, "low");
		double vol30 = parseJsonNumberAsDouble(body, "volume_30day");
		return new ProductStats(high, low, vol30);
	}

	private double parseJsonNumberAsDouble(String json, String key) {
		String k = "\"" + key + "\"";
		int idx = json.indexOf(k);
		if (idx < 0) return 0.0;
		int colon = json.indexOf(':', idx + k.length());
		if (colon < 0) return 0.0;
		int quote1 = json.indexOf('"', colon);
		if (quote1 < 0) return 0.0;
		int quote2 = json.indexOf('"', quote1 + 1);
		if (quote2 < 0) return 0.0;
		String numStr = json.substring(quote1 + 1, quote2);
		try {
			return Double.parseDouble(numStr);
		} catch (NumberFormatException ex) {
			return 0.0;
		}
	}
}