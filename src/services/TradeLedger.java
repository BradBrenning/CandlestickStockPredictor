package services;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TradeLedger {

    private static final String LOG_DIR = "data/trades";
    private static final String LOG_FILE = "data/trades/userLogs.txt";

    public static class BalancePoint {
        private final long timestamp;
        private final double balance;

        public BalancePoint(long timestamp, double balance) {
            this.timestamp = timestamp;
            this.balance = balance;
        }

        public long getTimestamp() { return timestamp; }
        public double getBalance() { return balance; }
    }

    public static class HoldingsPosition {
        private double quantity;
        private double averageCost;

        public double getQuantity() { return quantity; }
        public double getAverageCost() { return averageCost; }
    }

    public static class LedgerSnapshot {
        private double funds;
        private final Map<String, HoldingsPosition> holdings = new LinkedHashMap<>();
        private final List<BalancePoint> balanceHistory = new ArrayList<>();
        private double totalDeposits;
        private double realizedProfitLoss;

        public double getFunds() { return funds; }
        public Map<String, HoldingsPosition> getHoldings() { return holdings; }
        public List<BalancePoint> getBalanceHistory() { return balanceHistory; }
        public double getTotalDeposits() { return totalDeposits; }
        public double getRealizedProfitLoss() { return realizedProfitLoss; }
        public double getLatestBalance() {
            if (balanceHistory.isEmpty()) return funds;
            return balanceHistory.get(balanceHistory.size() - 1).getBalance();
        }
        public double getGainLossPercent() {
            return totalDeposits == 0.0 ? 0.0 : ((getLatestBalance() - totalDeposits) / totalDeposits) * 100.0;
        }
    }

    public TradeLedger() {
        ensureLogFile();
    }

    public synchronized void addFunds(double amount) throws IOException {
        append("FUND_ADD", "", amount, 0.0, 0.0, amount, "Added funds");
    }

    public synchronized void removeFunds(double amount) throws IOException {
        LedgerSnapshot snapshot = loadSnapshot();
        if (amount > snapshot.getFunds()) throw new IOException("Insufficient available funds.");
        append("FUND_REMOVE", "", amount, 0.0, 0.0, snapshot.getFunds() - amount, "Removed funds");
    }

    public synchronized void buy(String productId, double dollars, double price) throws IOException {
        LedgerSnapshot snapshot = loadSnapshot();
        if (dollars > snapshot.getFunds()) throw new IOException("Insufficient available funds.");
        if (price <= 0.0) throw new IOException("Invalid price.");

        double qty = dollars / price;
        HoldingsPosition position = snapshot.getHoldings().get(productId);
        double existingQty = position == null ? 0.0 : position.getQuantity();
        double existingCost = position == null ? 0.0 : position.getAverageCost();
        double newQty = existingQty + qty;
        double newAverage = newQty == 0.0 ? 0.0 : ((existingQty * existingCost) + dollars) / newQty;
        append("BUY", productId, dollars, qty, price, snapshot.getFunds() - dollars, String.format(Locale.US, "avg=%.8f", newAverage));
    }

    public synchronized double sellAll(String productId, double price) throws IOException {
        LedgerSnapshot snapshot = loadSnapshot();
        HoldingsPosition position = snapshot.getHoldings().get(productId);
        if (position == null || position.getQuantity() <= 0.0) throw new IOException("No holdings to sell for " + productId + ".");
        if (price <= 0.0) throw new IOException("Invalid price.");

        double proceeds = position.getQuantity() * price;
        append("SELL", productId, proceeds, position.getQuantity(), price, snapshot.getFunds() + proceeds, "liquidate");
        return proceeds;
    }

    public synchronized LedgerSnapshot loadSnapshot() throws IOException {
        ensureLogFile();
        LedgerSnapshot snapshot = new LedgerSnapshot();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(LOG_FILE), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|", -1);
                if (parts.length < 7) continue;

                long timestamp = parseLong(parts[0]);
                String type = parts[1];
                String productId = parts[2];
                double amount = parseDouble(parts[3]);
                double quantity = parseDouble(parts[4]);
                double price = parseDouble(parts[5]);

                switch (type) {
                    case "FUND_ADD":
                        snapshot.funds += amount;
                        snapshot.totalDeposits += amount;
                        break;
                    case "FUND_REMOVE":
                        snapshot.funds -= amount;
                        snapshot.totalDeposits -= amount;
                        break;
                    case "BUY":
                        snapshot.funds -= amount;
                        HoldingsPosition buyPosition = snapshot.holdings.computeIfAbsent(productId, key -> new HoldingsPosition());
                        double currentQty = buyPosition.quantity;
                        double newQty = currentQty + quantity;
                        buyPosition.averageCost = newQty == 0.0 ? 0.0 : ((currentQty * buyPosition.averageCost) + amount) / newQty;
                        buyPosition.quantity = newQty;
                        break;
                    case "SELL":
                        snapshot.funds += amount;
                        HoldingsPosition sellPosition = snapshot.holdings.get(productId);
                        if (sellPosition != null) {
                            double costBasis = sellPosition.quantity * sellPosition.averageCost;
                            snapshot.realizedProfitLoss += amount - costBasis;
                            sellPosition.quantity = 0.0;
                            sellPosition.averageCost = 0.0;
                        }
                        break;
                    default:
                        break;
                }

                double holdingsValue = computeHoldingsValue(snapshot.holdings, productId, price, type);
                snapshot.balanceHistory.add(new BalancePoint(timestamp, snapshot.funds + holdingsValue));
            }
        }
        if (snapshot.balanceHistory.isEmpty()) {
            snapshot.balanceHistory.add(new BalancePoint(Instant.now().toEpochMilli(), snapshot.funds));
        }
        return snapshot;
    }

    private double computeHoldingsValue(Map<String, HoldingsPosition> holdings, String eventProductId, double eventPrice, String type) {
        double total = 0.0;
        for (Map.Entry<String, HoldingsPosition> entry : holdings.entrySet()) {
            HoldingsPosition position = entry.getValue();
            if (position.quantity <= 0.0) continue;
            double valuation = position.averageCost;
            if (entry.getKey().equals(eventProductId) && ("BUY".equals(type) || "SELL".equals(type))) {
                valuation = eventPrice;
            }
            total += position.quantity * valuation;
        }
        return total;
    }

    private void ensureLogFile() {
        try {
            Path dir = Paths.get(LOG_DIR);
            if (!Files.exists(dir)) Files.createDirectories(dir);
            Path file = Paths.get(LOG_FILE);
            if (!Files.exists(file)) Files.createFile(file);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to initialize trade ledger", ex);
        }
    }

    private void append(String type, String productId, double amount, double quantity, double price, double balanceAfter, String note) throws IOException {
        ensureLogFile();
        String line = String.format(Locale.US, "%d|%s|%s|%.8f|%.8f|%.8f|%.8f|%s",
                Instant.now().toEpochMilli(),
                type,
                productId == null ? "" : productId,
                amount,
                quantity,
                price,
                balanceAfter,
                note == null ? "" : note.replace("|", "/"));
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            writer.write(line);
            writer.newLine();
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ex) {
            return 0L;
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ex) {
            return 0.0;
        }
    }
}
