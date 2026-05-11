package models;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import coinbaseAPI.Coinbase_Server;
import java.io.FileWriter;

// Simple fixed-record RandomAccessFile store for Product records
public class ProductStore implements Closeable {
    private final RandomAccessFile raf;
    private final File file;

    public ProductStore(String path) throws IOException {
        this.file = new File(path);
        this.raf = new RandomAccessFile(file, "rw");
    }

    // Read candlestick file for product and update product.hist_end to last recorded timestamp if different
    // Returns last timestamp found or -1 if file missing or invalid
    public long updateHistEndFromFile(String productId) throws IOException {
        services.Logger.log("ProductStore.updateHistEndFromFile: " + productId);
        if (productId == null || productId.isEmpty()) return -1L;
        File dir = new File("data/candlesticks");
        File f = new File(dir, productId + "_1minute.dat");
        if (!f.exists()) {
            services.Logger.log("ProductStore.updateHistEndFromFile: file not found " + f.getPath());
            // If product record indicates we previously had history (hist_end > hist_start), but file is missing,
            // reset hist_end back to hist_start to reflect missing data.
            try {
                int idx = findProductIndexById(productId);
                if (idx >= 0) {
                    Product existing = readRecord(idx);
                    if (existing != null) {
                        long hs = existing.getHistStart();
                        long he = existing.getHistEnd();
                        if (hs > 0 && he > hs) {
                            Product updated = new Product(existing.getProductId(), existing.getHigh(), existing.getLow(), existing.getVolume30Day(), existing.getServiceAPI(), existing.isTradeable(), existing.shouldTest(), existing.isOverridden(), hs, hs, System.currentTimeMillis());
                            writeRecord(idx, updated);
                            services.Logger.log("ProductStore.updateHistEndFromFile: candlestick file missing, reset hist_end to hist_start for " + productId);
                            return hs;
                        }
                    }
                }
            } catch (Exception ex) {
                services.Logger.log("ProductStore.updateHistEndFromFile: error while resetting hist_end " + ex.getMessage());
            }
            return -1L;
        }

        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long len = r.length();
            if (len < Candlestick.RECORD_LENGTH) return -1L;
            long lastPos = len - Candlestick.RECORD_LENGTH;
            r.seek(lastPos);
            byte[] buf = new byte[Candlestick.RECORD_LENGTH];
            int rr = r.read(buf);
            if (rr != Candlestick.RECORD_LENGTH) return -1L;
            try {
                Candlestick c = new Candlestick(new String(buf, StandardCharsets.UTF_8));
                long lastTs = c.getTimestamp();
                // find product and update if different
                int idx = findProductIndexById(productId);
                if (idx < 0) return lastTs;
                Product existing = readRecord(idx);
                if (existing == null) return lastTs;
                if (existing.getHistEnd() != lastTs) {
                    Product updated = new Product(existing.getProductId(), existing.getHigh(), existing.getLow(), existing.getVolume30Day(), existing.getServiceAPI(), existing.isTradeable(), existing.shouldTest(), existing.isOverridden(), existing.getHistStart(), lastTs, System.currentTimeMillis());
                    writeRecord(idx, updated);
                    services.Logger.log("ProductStore.updateHistEndFromFile: updated hist_end for " + productId + " to " + lastTs);
                } else {
                    services.Logger.log("ProductStore.updateHistEndFromFile: hist_end already up-to-date for " + productId);
                }
                return lastTs;
            } catch (Exception ex) {
                services.Logger.log("ProductStore.updateHistEndFromFile: parse failed " + ex.getMessage());
                return -1L;
            }
        }
    }

    // Toggle overridden flag and write record
    public void setOverride(String productId, boolean overridden) throws IOException {
        int idx = findProductIndexById(productId);
        if (idx < 0) return;
        Product existing = readRecord(idx);
        if (existing == null) return;
        Product updated = new Product(existing.getProductId(), existing.getHigh(), existing.getLow(), existing.getVolume30Day(), existing.getServiceAPI(), existing.isTradeable(), existing.shouldTest(), overridden, existing.getHistStart(), existing.getHistEnd(), System.currentTimeMillis());
        writeRecord(idx, updated);
        services.Logger.log("ProductStore.setOverride: " + productId + " overridden=" + overridden);
    }

    // Update candlesticks for product and write to data/candlesticks/{product}_1minute.dat
    public void updateCandlesticks(String productId) throws IOException {
        int idx = findProductIndexById(productId);
        if (idx < 0) return;
        Product existing = readRecord(idx);
        if (existing == null) return;
        if (!existing.isOverridden()) {
            services.Logger.log("updateCandlesticks: product not overridden, skipping " + productId);
            return;
        }

        // delegate to ranged fetch up to the last full minute
        long lastFullMinute = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toEpochMilli();
        UpdateResult res = fetchCandlesRange(productId, existing.getHistStart(), lastFullMinute);
        if (res != null) {
            long finalHistEnd = res.lastFetched > 0 ? res.lastFetched : existing.getHistEnd();
            Product updated = new Product(existing.getProductId(), existing.getHigh(), existing.getLow(), existing.getVolume30Day(), existing.getServiceAPI(), existing.isTradeable(), existing.shouldTest(), existing.isOverridden(), existing.getHistStart(), finalHistEnd, System.currentTimeMillis());
            writeRecord(idx, updated);
            services.Logger.log("updateCandlesticks: finished full update, wrote " + res.count + " candles finalHistEnd=" + finalHistEnd);
        }
    }

    // Result holder for fetchCandlesRange
    public static class UpdateResult {
        public final long lastFetched;
        public final long count;
        public final boolean stoppedOnError;
        public UpdateResult(long lastFetched, long count, boolean stoppedOnError) {
            this.lastFetched = lastFetched; this.count = count; this.stoppedOnError = stoppedOnError; }
    }

    // Fetch candles between start and end (inclusive) and append to file. Returns UpdateResult.
    public UpdateResult fetchCandlesRange(String productId, long startMillis, long endMillis) throws IOException {
        services.Logger.log("fetchCandlesRange: product=" + productId + " start=" + startMillis + " end=" + endMillis + " (batch mode)");
        int idx = findProductIndexById(productId);
        if (idx < 0) return null;
        Product existing = readRecord(idx);
        if (existing == null) return null;

        Coinbase_Server cb = new Coinbase_Server();
        File dir = new File("data/candlesticks"); if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, productId + "_1minute.dat");
        long writeCount = 0;
        long lastTs = 0L;
        try (RandomAccessFile r = new RandomAccessFile(out, "rw")) {
            // find last timestamp
            long fileLen = r.length();
            if (fileLen >= Candlestick.RECORD_LENGTH) {
                long lastPos = fileLen - Candlestick.RECORD_LENGTH;
                r.seek(lastPos);
                byte[] buf = new byte[Candlestick.RECORD_LENGTH];
                int rr = r.read(buf);
                if (rr == Candlestick.RECORD_LENGTH) {
                    try { Candlestick c = new Candlestick(new String(buf, StandardCharsets.UTF_8)); lastTs = c.getTimestamp(); } catch (Exception ex) {}
                }
            }

            // remember starting file length for safety checks
            long fileLenBefore = fileLen;

            long current = startMillis;
            while (current <= endMillis) {
                cb.candlestickRequest(productId, current);
                int code = cb.getResponseCode();
                String body = cb.getBody();
                if (code != 200 || body == null) {
                    services.Logger.log("fetchCandlesRange: stopped at " + current + " code=" + code);
                    long newHistEnd = lastTs > 0 ? lastTs : (current - 60000L);
                    return new UpdateResult(newHistEnd, writeCount, true);
                }

                // parse and append candles
                String s = body.trim();
                if (s.startsWith("[")) {
                    int i = 0;
                    while (true) {
                        int a = s.indexOf('[', i);
                        if (a < 0) break;
                        int b = s.indexOf(']', a);
                        if (b < 0) break;
                        String inner = s.substring(a + 1, b);
                        String[] parts = inner.split(",");
                        if (parts.length >= 6) {
                            try {
                                long ts = Long.parseLong(parts[0].trim()) * 1000L;
                                long low = Math.round(Double.parseDouble(parts[1].trim()));
                                long high = Math.round(Double.parseDouble(parts[2].trim()));
                                long open = Math.round(Double.parseDouble(parts[3].trim()));
                                long close = Math.round(Double.parseDouble(parts[4].trim()));
                                long vol = Math.round(Double.parseDouble(parts[5].trim()));
                                Candlestick c = new Candlestick(ts, low, high, open, close, vol);
                                byte[] bytes = c.toString().getBytes(StandardCharsets.UTF_8);
                                if (bytes.length != Candlestick.RECORD_LENGTH) {
                                    byte[] fixed = new byte[Candlestick.RECORD_LENGTH];
                                    int copy = Math.min(bytes.length, fixed.length);
                                    System.arraycopy(bytes, 0, fixed, 0, copy);
                                    for (int j = copy; j < fixed.length; j++) fixed[j] = ' ';
                                    r.seek(r.length()); r.write(fixed);
                                } else { r.seek(r.length()); r.write(bytes); }
                                writeCount++; lastTs = ts;
                            } catch (Exception ex) { }
                        }
                        i = b + 1;
                    }
                }

                current += 60000L;
            }

            try { r.getFD().sync(); } catch (Exception ex) { services.Logger.log("fetchCandlesRange: sync failed " + ex.getMessage()); }
            // Integrity check: ensure we wrote whole fixed-length records and did not leave partial bytes
            try {
                long fileLenAfter = r.length();
                long delta = fileLenAfter - fileLenBefore;
                long expected = writeCount * (long)Candlestick.RECORD_LENGTH;
                if (delta != expected) {
                    services.Logger.log("fetchCandlesRange: length mismatch before=" + fileLenBefore + " after=" + fileLenAfter + " expectedDelta=" + expected + " actualDelta=" + delta);
                    // determine actual full records written
                    long actualWritten = delta / (long)Candlestick.RECORD_LENGTH;
                    long goodLen = fileLenBefore + actualWritten * (long)Candlestick.RECORD_LENGTH;
                    if (fileLenAfter != goodLen) {
                        try {
                            r.setLength(goodLen);
                            services.Logger.log("fetchCandlesRange: truncated file to good length=" + goodLen);
                        } catch (Exception ex) {
                            services.Logger.log("fetchCandlesRange: truncate failed " + ex.getMessage());
                        }
                    }
                    // read last written record to determine accurate lastTs
                    if (actualWritten > 0) {
                        long lastPos = goodLen - Candlestick.RECORD_LENGTH;
                        r.seek(lastPos);
                        byte[] lastBuf = new byte[Candlestick.RECORD_LENGTH];
                        int rr2 = r.read(lastBuf);
                        if (rr2 == Candlestick.RECORD_LENGTH) {
                            try { Candlestick c2 = new Candlestick(new String(lastBuf, StandardCharsets.UTF_8)); lastTs = c2.getTimestamp(); } catch (Exception ex) { }
                        }
                    }
                    writeCount = actualWritten;
                }
            } catch (Exception ex) {
                services.Logger.log("fetchCandlesRange: post-write integrity check failed " + ex.getMessage());
            }
        }
        return new UpdateResult(lastTs, writeCount, false);
    }

    // Ensure CSV file exists; create empty CSV with header if missing
    public void ensureCSV(String csvPath) throws IOException {
        File csv = new File(csvPath);
        if (!csv.exists()) {
            try (FileWriter fw = new FileWriter(csv, false)) {
                fw.write("product_id,high,low,volume_30day,service_api,is_tradeable,should_test,overridden,hist_start,hist_end,modified\n");
            }
        }
    }

    public long recordCount() throws IOException {
        return raf.length() / Product.RECORD_LENGTH;
    }

    // Call Coinbase server, parse product ids from JSON response, populate the fixed-record file
    public void refreshFromCoinbase() throws IOException {
        services.Logger.log("ProductStore.refreshFromCoinbase: start");
        Coinbase_Server cb = new Coinbase_Server();
        cb.stockInformation();
        String body = cb.getBody();
        if (body == null) return;

        // write a simple CSV file alongside for inspection
        try (FileWriter fw = new FileWriter(new File("products.csv"), false)) {
            fw.write("product_id,high,low,volume_30day,service_api,is_tradeable,should_test,overridden,hist_start,hist_end,modified\n");
            // We'll append simple rows with id only
            List<ProductInfo> infos = parseProductInfosFromJson(body);
            for (ProductInfo info : infos) {
                long now = System.currentTimeMillis();
                fw.write(String.format("%s,0,0,0,coinbase,%d,0,0,0,0,%d\n", info.id, info.isTradeable() ? 1 : 0, now));
            }
        }

        // Truncate existing records and write new ones
        services.Logger.log("ProductStore.refreshFromCoinbase: truncating and writing records");
        raf.setLength(0);
        List<ProductInfo> infos2 = parseProductInfosFromJson(body);
        for (ProductInfo info : infos2) {
            // fetch stats for this product to populate high/low/volume_30day
            Coinbase_Server.ProductStats stats = cb.getProductStats(info.id);
            long high = 0L;
            long low = 0L;
            long vol30 = 0L;
            if (stats != null) {
                high = Math.round(stats.high);
                low = Math.round(stats.low);
                vol30 = Math.round(stats.volume30day);
            }
            // hist_start and hist_end default to 0 until findHistory updates them
            Product p = new Product(info.id, high, low, vol30, "coinbase", info.isTradeable(), false, false, 0L, 0L, System.currentTimeMillis());
            appendRecord(p);
            services.Logger.log("ProductStore.refreshFromCoinbase: wrote product " + info.id);
        }
        services.Logger.log("ProductStore.refreshFromCoinbase: finished");
    }

    // Parse product infos (id and status) from JSON returned by /products
    private List<ProductInfo> parseProductInfosFromJson(String json) {
        List<ProductInfo> out = new ArrayList<>();
        String keyId = "\"id\"";
        int idx = 0;
        while (true) {
            int k = json.indexOf(keyId, idx);
            if (k < 0) break;
            int colon = json.indexOf(':', k + keyId.length());
            if (colon < 0) break;
            int quote1 = json.indexOf('"', colon);
            if (quote1 < 0) break;
            int quote2 = json.indexOf('"', quote1 + 1);
            if (quote2 < 0) break;
            String id = json.substring(quote1 + 1, quote2);

            // find status nearby after quote2
            int statusKey = json.indexOf("\"status\"", quote2);
            String status = "";
            if (statusKey >= 0) {
                int col2 = json.indexOf(':', statusKey + 8);
                if (col2 >= 0) {
                    int sqq = json.indexOf('"', col2);
                    if (sqq >= 0) {
                        int sqq2 = json.indexOf('"', sqq + 1);
                        if (sqq2 > sqq) {
                            status = json.substring(sqq + 1, sqq2);
                        }
                    }
                }
            }

            // find quote_currency nearby after quote2
            int quoteKey = json.indexOf("\"quote_currency\"", quote2);
            String quoteCurrency = "";
            if (quoteKey >= 0) {
                int col3 = json.indexOf(':', quoteKey + 16);
                if (col3 >= 0) {
                    int q1 = json.indexOf('"', col3);
                    if (q1 >= 0) {
                        int q2 = json.indexOf('"', q1 + 1);
                        if (q2 > q1) {
                            quoteCurrency = json.substring(q1 + 1, q2);
                        }
                    }
                }
            }

            // Only include USD quote currency
            if (quoteCurrency == null || !quoteCurrency.equalsIgnoreCase("USD")) {
                idx = quote2 + 1;
                continue;
            }

            out.add(new ProductInfo(id, status));
            idx = quote2 + 1;
        }
        return out;
    }

    // Simple container for parsed id and status
    private static class ProductInfo {
        final String id;
        final String status;
        ProductInfo(String id, String status) { this.id = id; this.status = status == null ? "" : status; }
        boolean isTradeable() { return "online".equalsIgnoreCase(status); }
    }

    public Product readRecord(long index) throws IOException {
        services.Logger.log("ProductStore.readRecord: index=" + index);
        long pos = index * Product.RECORD_LENGTH;
        if (pos >= raf.length()) return null;
        raf.seek(pos);
        byte[] buf = new byte[Product.RECORD_LENGTH];
        int read = raf.read(buf);
        if (read != Product.RECORD_LENGTH) return null;
        String record = new String(buf, StandardCharsets.UTF_8);
        return new Product(record);
    }

    public void writeRecord(long index, Product p) throws IOException {
        services.Logger.log("ProductStore.writeRecord: index=" + index + " product=" + p.getProductId());
        long pos = index * Product.RECORD_LENGTH;
        raf.seek(pos);
        String rec = p.toRecordString();
        byte[] bytes = rec.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != Product.RECORD_LENGTH) {
            byte[] fixed = new byte[Product.RECORD_LENGTH];
            int copy = Math.min(bytes.length, fixed.length);
            System.arraycopy(bytes, 0, fixed, 0, copy);
            for (int i = copy; i < fixed.length; i++) fixed[i] = ' ';
            raf.write(fixed);
        } else {
            raf.write(bytes);
        }
        // Ensure data is flushed to disk
        try {
            raf.getFD().sync();
        } catch (SyncFailedException sfe) {
            // ignore sync failures but log
            services.Logger.log("ProductStore.writeRecord: sync failed: " + sfe.getMessage());
        }

        // Read back written record and log for verification
        long curPos = raf.getFilePointer();
        raf.seek(pos);
        byte[] buf = new byte[Product.RECORD_LENGTH];
        int r = raf.read(buf);
        String recBack = r == Product.RECORD_LENGTH ? new String(buf, StandardCharsets.UTF_8) : "";
        services.Logger.log("ProductStore.writeRecord: wrote bytes=" + (r) + " recBack=" + recBack.trim());
        // restore file pointer
        raf.seek(curPos);
    }

    public void appendRecord(Product p) throws IOException {
        long idx = recordCount();
        writeRecord(idx, p);
    }

    public List<Product> readAll() throws IOException {
        services.Logger.log("ProductStore.readAll: start");
        long n = recordCount();
        List<Product> out = new ArrayList<>();
        for (long i = 0; i < n; i++) {
            Product p = readRecord(i);
            if (p != null) out.add(p);
        }
        services.Logger.log("ProductStore.readAll: returned " + out.size() + " products");
        return out;
    }

    // Import from CSV text where first line is header and subsequent lines are records
    public void importFromCSV(String csvText) throws IOException {
        services.Logger.log("ProductStore.importFromCSV: start");
        String[] lines = csvText.split("\r?\n");
        if (lines.length < 1) return;
        // naive split on commas for header
        String headerLine = lines[0];
        String[] headers = headerLine.split(",");

        // truncate file
        raf.setLength(0);

        for (int i = 1; i < lines.length; i++) {
            String ln = lines[i].trim();
            if (ln.isEmpty()) continue;
            // naive CSV parse assuming consistent order matching Product fields
            String[] cols = ln.split(",");
            // Build Product from expected columns if enough
            try {
                String pid = cols.length > 0 ? cols[0] : "";
                long high = cols.length > 1 ? Long.parseLong(cols[1].trim()) : 0L;
                long low = cols.length > 2 ? Long.parseLong(cols[2].trim()) : 0L;
                long vol = cols.length > 3 ? Long.parseLong(cols[3].trim()) : 0L;
                String api = cols.length > 4 ? cols[4] : "";
                boolean trade = cols.length > 5 ? cols[5].trim().equals("1") : false;
                boolean should = cols.length > 6 ? cols[6].trim().equals("1") : false;
                boolean over = cols.length > 7 ? cols[7].trim().equals("1") : false;
                long hs = cols.length > 8 ? Long.parseLong(cols[8].trim()) : 0L;
                long he = cols.length > 9 ? Long.parseLong(cols[9].trim()) : 0L;
                long mod = cols.length > 10 ? Long.parseLong(cols[10].trim()) : System.currentTimeMillis();
                Product p = new Product(pid, high, low, vol, api, trade, should, over, hs, he, mod);
                appendRecord(p);
            } catch (Exception ex) {
                // skip bad line
            }
        }
        services.Logger.log("ProductStore.importFromCSV: finished");
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

    // Find index of product by id, or -1 if not found
    public int findProductIndexById(String productId) throws IOException {
        services.Logger.log("ProductStore.findProductIndexById: " + productId);
        long n = recordCount();
        for (int i = 0; i < n; i++) {
            Product p = readRecord(i);
            if (p != null && p.getProductId().equals(productId)) return i;
        }
        return -1;
    }

    // Find history (earliest available data) for given product id and update its hist_start/hist_end/modified
    public void findAndUpdateHistory(String productId) throws IOException {
        services.Logger.log("ProductStore.findAndUpdateHistory: " + productId);
        if (productId == null || productId.isEmpty()) return;
        int idx = findProductIndexById(productId);
        if (idx < 0) return;

        Coinbase_Server cb = new Coinbase_Server();

        // end = now truncated to last full minute
        java.time.Instant endInstant = java.time.Instant.now().minus(java.time.Duration.ofDays(1)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        long histEnd = endInstant.toEpochMilli();

        // quick check: ensure there's data at end
        boolean endHas = hasCandlesAt(cb, productId, endInstant);
        services.Logger.log("findAndUpdateHistory: endInstant=" + endInstant + " hasData=" + endHas);
        if (!endHas) {
            // no data at current end; nothing to update
            services.Logger.log("findAndUpdateHistory: no data at current end for " + productId);
            return;
        }
        // Use ZonedDateTime for year math and start-of-day rounding
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.ZonedDateTime endZdt = java.time.ZonedDateTime.ofInstant(endInstant, zone);

        // Step back by 1 year until we find an empty response
        java.time.ZonedDateTime prevGood = endZdt;
        java.time.ZonedDateTime candidate = endZdt.minusYears(1).with(java.time.LocalTime.MIDNIGHT);
        java.time.ZonedDateTime emptyAt = null;
        int safety = 0;
        services.Logger.log("findAndUpdateHistory: starting yearly backoff search from " + endZdt.toLocalDate());
        while (true) {
            boolean has = hasCandlesAt(cb, productId, candidate.toInstant());
            services.Logger.logVerbose("findAndUpdateHistory: tested candidate=" + candidate.toLocalDate() + " hasData=" + has);
            if (has) {
                prevGood = candidate;
                candidate = candidate.minusYears(1).with(java.time.LocalTime.MIDNIGHT);
                safety++;
                if (safety > 50) {
                    services.Logger.log("findAndUpdateHistory: safety limit reached during yearly backoff");
                    break;
                }
                continue;
            } else {
                emptyAt = candidate;
            services.Logger.logVerbose("findAndUpdateHistory: found empty at " + emptyAt.toLocalDate());
                break;
            }
        }

        java.time.ZonedDateTime earliestGood = prevGood.with(java.time.LocalTime.MIDNIGHT);
        if (emptyAt != null) {
            // binary search between emptyAt (no data) and prevGood (has data) at day precision
            long low = emptyAt.toInstant().toEpochMilli();
            long high = prevGood.toInstant().toEpochMilli();
            long oneDayMs = java.time.Duration.ofDays(1).toMillis();
            services.Logger.logVerbose("findAndUpdateHistory: starting binary search between " + java.time.Instant.ofEpochMilli(low) + " and " + java.time.Instant.ofEpochMilli(high));
            while (high - low > oneDayMs) {
                long mid = low + (high - low) / 2;
                java.time.ZonedDateTime midZ = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(mid), zone).with(java.time.LocalTime.MIDNIGHT);
                boolean hasMid = hasCandlesAt(cb, productId, midZ.toInstant());
                services.Logger.logVerbose("findAndUpdateHistory: binary test mid=" + midZ.toLocalDate() + " hasData=" + hasMid);
                if (hasMid) {
                    high = midZ.toInstant().toEpochMilli();
                } else {
                    low = midZ.toInstant().toEpochMilli();
                }
            }
            earliestGood = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(high), zone).with(java.time.LocalTime.MIDNIGHT);
        }

        long histStart = earliestGood.toInstant().toEpochMilli();
        services.Logger.log("findAndUpdateHistory: earliestGood=" + earliestGood.toLocalDate() + " histStartMillis=" + histStart);

        // read existing product
        Product existing = readRecord(idx);
        if (existing == null) return;

        long high = existing.getHigh();
        long low = existing.getLow();
        long vol30 = existing.getVolume30Day();

        // If any of these are zero, try to fetch stats at current end
        Coinbase_Server.ProductStats endStats = cb.getProductStats(productId);
        if (endStats != null) {
            if (high == 0L) high = Math.round(endStats.high);
            if (low == 0L) low = Math.round(endStats.low);
            if (vol30 == 0L) vol30 = Math.round(endStats.volume30day);
        }

        // When user clicks Find History, set hist_end to hist_start initially
        long newHistEnd = histStart;
        Product updated = new Product(existing.getProductId(), high, low, vol30, existing.getServiceAPI(), existing.isTradeable(), existing.shouldTest(), existing.isOverridden(), histStart, newHistEnd, System.currentTimeMillis());
        writeRecord(idx, updated); // Explicit writeRecord call to update index
        services.Logger.log("ProductStore.findAndUpdateHistory: updated product " + productId + " histStart=" + histStart + " histEnd=" + newHistEnd + " high=" + high + " low=" + low + " vol30=" + vol30);
    }

    // Helper: call candlestick endpoint and determine if response contains data
    private boolean hasCandlesAt(Coinbase_Server cb, String productId, java.time.Instant start) {
        try {
            cb.candlestickRequest(productId, start.toEpochMilli());
            String body = cb.getBody();
            if (body == null) return false;
            String s = body.trim();
            if (s.equals("[]")) {
                services.Logger.log("hasCandlesAt: empty array response for " + productId + " at " + start);
                return false;
            }

            // count inner arrays (candles) in the response
            int count = 0;
            if (s.startsWith("[")) {
                int i = 0;
                while (true) {
                    int a = s.indexOf('[', i);
                    if (a < 0) break;
                    int b = s.indexOf(']', a);
                    if (b < 0) break;
                    String inner = s.substring(a + 1, b);
                    String[] parts = inner.split(",");
                    if (parts.length >= 6) count++;
                    i = b + 1;
                }
            }
            services.Logger.logVerbose("hasCandlesAt: found " + count + " candles for " + productId + " at " + start);
            // require at least 300 candlesticks to consider this a valid historical block
            return count >= 300;
        } catch (Exception ex) {
            return false;
        }
    }
}
