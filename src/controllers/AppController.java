package controllers;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;

import java.util.*;
import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import coinbaseAPI.Coinbase_Server;
import models.ProductStore;
import services.Logger;
import models.Product;
import models.Candlestick;
import java.io.RandomAccessFile;
import services.RuntimePerformanceTracker;

import views.*;
import services.NeuralNetwork;
import services.TradeLedger;
import java.util.function.Consumer;
import javafx.application.Platform;

// This controller is used for setting up each view selected from the menu bar
public class AppController {

	private Stage mainStage;
	private BorderPane borderPane;
	private models.ProductStore productStore;
	private final TradeLedger tradeLedger = new TradeLedger();

	// Constructor for setting up windowed application
	public AppController(Stage stage) {
		mainStage = stage;
		setupApplication();
	}

	// Progress callback interface for training updates
	public interface TrainingProgress {
		void update(int epoch, int step, int total);
	}

	public static class EvaluationResult {
		private final List<Double> runningAccuracy;
		private final int[][] decisionMatrix;
		private final int correct;
		private final int totalSamples;

		public EvaluationResult(List<Double> runningAccuracy, int[][] decisionMatrix, int correct, int totalSamples) {
			this.runningAccuracy = runningAccuracy;
			this.decisionMatrix = decisionMatrix;
			this.correct = correct;
			this.totalSamples = totalSamples;
		}

		public List<Double> getRunningAccuracy() { return runningAccuracy; }
		public int[][] getDecisionMatrix() { return decisionMatrix; }
		public int getCorrect() { return correct; }
		public int getTotalSamples() { return totalSamples; }
		public double getFinalAccuracy() {
			return totalSamples <= 0 ? 0.0 : (double) correct / (double) totalSamples * 100.0;
		}
	}

	public static class LiveViewSnapshot {
		private final List<Candlestick> chartCandles;
		private final double[] probabilities;
		private final double lastClose;

		public LiveViewSnapshot(List<Candlestick> chartCandles, double[] probabilities, double lastClose) {
			this.chartCandles = chartCandles;
			this.probabilities = probabilities;
			this.lastClose = lastClose;
		}

		public List<Candlestick> getChartCandles() { return chartCandles; }
		public double[] getProbabilities() { return probabilities; }
		public double getLastClose() { return lastClose; }
	}

	// Async training with progress callbacks (runs on background thread)
	public void startTrainingAsync(String productId, int epochs, TrainingProgress progress) {
		new Thread(() -> {
			RuntimePerformanceTracker tracker = new RuntimePerformanceTracker("Training - " + productId);
			if (productStore == null) {
				try { productStore = new ProductStore("data/products.dat"); } catch (IOException ex) { ex.printStackTrace(); return; }
			}
			File modelDir = new File("data/neuralnetworks");
			if (!modelDir.exists()) modelDir.mkdirs();
			String modelPath = new File(modelDir, "model_" + productId + ".bin").getPath();
			NeuralNetwork net = null;
			try {
				File mf = new File(modelPath);
				if (mf.exists()) net = NeuralNetwork.load(modelPath);
			} catch (Exception ex) {
				services.Logger.log("startTrainingAsync: failed to load model " + ex.getMessage());
			}
			if (net == null) net = new NeuralNetwork();

			File dir = new File("data/candlesticks");
			if (!dir.exists() || !dir.isDirectory()) return;

			File f = new File(dir, productId + "_1minute.dat");
			if (!f.exists()) return;
			try {
				byte[] all = java.nio.file.Files.readAllBytes(f.toPath());
				int recLen = models.Candlestick.RECORD_LENGTH;
				int total = all.length / recLen;
				int samples = Math.max(0, total - 35 + 1);

				for (int e = 1; e <= epochs; e++) {
					final int epoch = e;
					// initial progress 0
					Platform.runLater(() -> progress.update(epoch, 0, samples));
					for (int i = 0; i + 35 <= total; i++) {
						double[][] window = new double[35][5];
						for (int j = 0; j < 35; j++) {
							int off = (i + j) * recLen;
							String rec = new String(all, off, recLen, java.nio.charset.StandardCharsets.UTF_8);
							models.Candlestick c = new models.Candlestick(rec);
							window[j][0] = c.getLow();
							window[j][1] = c.getHigh();
							window[j][2] = c.getOpen();
							window[j][3] = c.getClose();
							window[j][4] = c.getVolume();
						}
						net.train(window);
						final int step = i + 1;
						Platform.runLater(() -> progress.update(epoch, step, samples));
					}
				}
				net.save(modelPath);
				// final progress
				Platform.runLater(() -> progress.update(epochs, samples, samples));
				Map<String, String> details = new LinkedHashMap<>();
				details.put("Product", productId);
				details.put("Epochs", String.valueOf(epochs));
				details.put("Training Samples", String.valueOf(samples));
				details.put("Candlestick Records", String.valueOf(total));
				details.put("Model Path", modelPath);
				tracker.printSummary(details);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}).start();
	}

	// Ensure hist_end for product is synchronized from local candlestick file if present
	public void ensureHistEndFromFile(String productId) {
		if (productStore == null) {
			try { productStore = new ProductStore("data/products.dat"); } catch (IOException ex) { ex.printStackTrace(); return; }
		}
		try {
			long last = productStore.updateHistEndFromFile(productId);
			if (last > 0) {
				Logger.log("ensureHistEndFromFile: synced hist_end for " + productId + " to " + last);
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public void setOverride(String productId, boolean overridden) {
		if (productStore == null) {
			try { productStore = new ProductStore("data/products.dat"); } catch (IOException ex) { ex.printStackTrace(); return; }
		}
		try {
			productStore.setOverride(productId, overridden);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		showStocksAvailableView();
	}

	public void updateData(String productId) {
        if (productStore == null) {
			try { productStore = new ProductStore("data/products.dat"); } catch (IOException ex) { ex.printStackTrace(); return; }
		}
		RuntimePerformanceTracker tracker = new RuntimePerformanceTracker("Update Data - " + productId);
		long totalCandlesFetched = 0L;
		int totalBatchRequests = 0;
		double totalCoinbaseFetchMillis = 0.0;

		try {
         // Ensure any existing candlestick file is used to sync hist_end before starting
			ensureHistEndFromFile(productId);

			int idx = productStore.findProductIndexById(productId);
			if (idx < 0) return;
			Product prod = productStore.readRecord(idx);
			if (prod == null) return;

			long histStart = prod.getHistStart();
			long fileHistEnd = prod.getHistEnd();
			// Determine starting cursor: if file already has data beyond histStart, continue from next minute after file end
			long startMillis;
			if (fileHistEnd > 0 && fileHistEnd >= histStart) {
				startMillis = fileHistEnd + 60000L; // next minute
			} else {
				startMillis = histStart;
			}

			if (startMillis <= 0) {
				Alert a = new Alert(Alert.AlertType.INFORMATION, "No hist_start set for product. Run Find History first.", ButtonType.OK);
				a.showAndWait();
				return;
			}

			java.time.ZoneId zone = java.time.ZoneId.systemDefault();
			java.time.ZonedDateTime cursor = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(startMillis), zone);
			java.time.ZonedDateTime endZ = java.time.ZonedDateTime.ofInstant(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES), zone);
			java.time.format.DateTimeFormatter df = java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd").withZone(zone);

			if (cursor.isAfter(endZ)) {
				Alert a = new Alert(Alert.AlertType.INFORMATION, "No new data to fetch; candlestick file is already up to date.", ButtonType.OK);
				a.showAndWait();
				showStocksAvailableView();
				return;
			}

            while (!cursor.isAfter(endZ)) {
				// fetch in 1-day chunks
				java.time.ZonedDateTime chunkEnd = cursor.plusDays(1).minusMinutes(1).withSecond(0).withNano(0);
				if (chunkEnd.isAfter(endZ)) chunkEnd = endZ;

				services.Logger.log("updateData: fetching day range " + cursor.toLocalDate() + " to " + chunkEnd.toLocalDate() + " (batch)");
				long fetchStart = System.nanoTime();
				models.ProductStore.UpdateResult res = productStore.fetchCandlesRange(productId, cursor.toInstant().toEpochMilli(), chunkEnd.toInstant().toEpochMilli());
				totalCoinbaseFetchMillis += (System.nanoTime() - fetchStart) / 1_000_000.0;
				totalBatchRequests++;
				if (res == null) break;
				totalCandlesFetched += res.count;
				if (res.stoppedOnError) {
					Alert stopAlert = new Alert(Alert.AlertType.WARNING, "Stopped during fetch at " + java.time.Instant.ofEpochMilli(res.lastFetched) + ".", ButtonType.OK);
					stopAlert.showAndWait();
					break;
				}

				// ask user if they want to continue to next day
				Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Fetched up to " + df.format(chunkEnd) + ". Continue to next day?", ButtonType.YES, ButtonType.NO);
				java.util.Optional<ButtonType> opt = confirm.showAndWait();
				if (!opt.isPresent() || opt.get() != ButtonType.YES) break;

				// advance cursor to next minute after chunkEnd
				cursor = chunkEnd.plusMinutes(1).withSecond(0).withNano(0);
			}

		} catch (IOException ex) {
			ex.printStackTrace();
		}
		Map<String, String> details = new LinkedHashMap<>();
		details.put("Product", productId);
		details.put("Coinbase Fetch Time (ms)", String.format(java.util.Locale.US, "%.3f", totalCoinbaseFetchMillis));
		details.put("Coinbase Batch Requests", String.valueOf(totalBatchRequests));
		details.put("Candles Retrieved", String.valueOf(totalCandlesFetched));
		tracker.printSummary(details);

		showStocksAvailableView();
	}

	// Find history for a given product id by delegating to ProductStore
	public void findHistory(String productId) {
		if (productStore == null) {
			try {
                productStore = new ProductStore("data/products.dat");
			} catch (IOException ex) {
				ex.printStackTrace();
				return;
			}
		}
		try {
			productStore.findAndUpdateHistory(productId);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		// Refresh view to show updated timestamps
		showStocksAvailableView();
	}

	// Handles menu bar selections changing between active views
	public void handleMenuSelection(String menuSelection) {
		switch(menuSelection) {
		case "Stocks - Available Stocks":
			showStocksAvailableView();
			break;
		case "Stocks - View Stocks":
			showViewStocksView();
			break;
		case "Trades - History":
			showTradesHistoryView();
			break;
        case "Training - Settings":
			showTrainingSettingsView();
			break;
        case "Testing - Review":
			showTestingView();
			break;
			/*case "Products - All Products":
				showAllProductsView();
				break;*/
			default:
				Label holderLabel = new Label(menuSelection);
				borderPane.setCenter(holderLabel);
				break;
		}
	}

	// Sets up initial stage for main application
	private void setupApplication() {
		mainStage.setTitle("Candlestick Prediction");

		borderPane = new BorderPane();
		Scene scene = new Scene(borderPane, 900 , 600);
		mainStage.setScene(scene);
        // Determine admin flag from system property or environment variable named "admin"
		boolean isAdmin = false;
		String prop = System.getProperty("admin");
		String env = System.getenv("admin");
		if (prop != null) {
			String p = prop.trim().toLowerCase();
			isAdmin = p.equals("true") || p.equals("1") || p.equals("yes");
		}
		if (!isAdmin && env != null) {
			String e = env.trim().toLowerCase();
			isAdmin = e.equals("true") || e.equals("1") || e.equals("yes");
		}

		// Create MenuBarView according to admin flag
		views.MenuBarView menuBarView = new views.MenuBarView(this, isAdmin);
		borderPane.setTop(menuBarView.getMenuBar());

		// Initialize product store (creates file if needed)
        try {
            productStore = new ProductStore("data/products.dat");
			productStore.ensureCSV("data/products.csv");
			Logger.log("Initialized ProductStore at data/products.dat");
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		mainStage.show();
	}

	// Used for setting up all products view
/*	private void showAllProductsView() {
		AllProductsView allProductsView = new AllProductsView(this);
		borderPane.setCenter(allProductsView.getView());
	}*/

	public void showStocksAvailableView() {
		views.StocksAvailableView v = new views.StocksAvailableView(this);
		borderPane.setCenter(v.getView());
	}

	public void showViewStocksView() {
		views.ViewStocksView v = new views.ViewStocksView(this);
		borderPane.setCenter(v.getView());
	}

	public void showTradesHistoryView() {
		views.TradesHistoryView v = new views.TradesHistoryView(this);
		borderPane.setCenter(v.getView());
	}

	public void showTrainingSettingsView() {
		views.TrainingSettingsView v = new views.TrainingSettingsView(this);
		borderPane.setCenter(v.getView());
	}

	public void showTestingView() {
		views.TestingView v = new views.TestingView(this);
		borderPane.setCenter(v.getView());
	}

	public void runValidationAsync(String productId, Consumer<EvaluationResult> resultConsumer, Consumer<String> statusConsumer) {
		runEvaluationAsync(productId, resultConsumer, statusConsumer);
	}

	public void fetchLiveViewSnapshotAsync(String productId, Consumer<LiveViewSnapshot> resultConsumer, Consumer<String> statusConsumer) {
		new Thread(() -> {
			try {
				List<Candlestick> recent = fetchRecentCandles(productId, 300);
				if (recent.size() < 50) {
					statusConsumer.accept("Insufficient candles: " + recent.size());
					return;
				}

				List<Candlestick> chartCandles = recent.size() <= 50
						? new ArrayList<>(recent)
						: new ArrayList<>(recent.subList(recent.size() - 50, recent.size()));
				double lastClose = chartCandles.isEmpty() ? 0.0 : chartCandles.get(chartCandles.size() - 1).getClose();

				double[] probabilities = new double[] {0.0, 0.0, 0.0};
				NeuralNetwork net = loadModel(productId);
				if (net != null && recent.size() >= 30) {
					double[][] window = new double[30][5];
					for (int i = 0; i < 30; i++) {
						Candlestick c = recent.get(recent.size() - 30 + i);
						window[i][0] = c.getLow();
						window[i][1] = c.getHigh();
						window[i][2] = c.getOpen();
						window[i][3] = c.getClose();
						window[i][4] = c.getVolume();
					}
					probabilities = net.predict(window);
				} else {
					statusConsumer.accept("Model load failed");
				}

				resultConsumer.accept(new LiveViewSnapshot(chartCandles, probabilities, lastClose));
			} catch (Exception ex) {
				statusConsumer.accept("Live view failed: " + ex.getMessage());
			}
		}).start();
	}

	public TradeLedger.LedgerSnapshot getTradeSnapshot() {
		try {
			return tradeLedger.loadSnapshot();
		} catch (IOException ex) {
			Logger.log("getTradeSnapshot failed: " + ex.getMessage());
			return new TradeLedger.LedgerSnapshot();
		}
	}

	public void addFunds(double amount) throws IOException {
		tradeLedger.addFunds(amount);
	}

	public void removeFunds(double amount) throws IOException {
		tradeLedger.removeFunds(amount);
	}

	public void buyProduct(String productId, double dollars, double price) throws IOException {
		tradeLedger.buy(productId, dollars, price);
	}

	public double sellAllProduct(String productId, double price) throws IOException {
		return tradeLedger.sellAll(productId, price);
	}

	// Runs the test asynchronously. progress consumer receives status updates (string) and resultConsumer receives final list of accuracies
	public void runTestAsync(String productId, Consumer<java.util.List<Double>> resultConsumer, Consumer<String> statusConsumer) {
		runEvaluationAsync(productId, (evaluation) -> resultConsumer.accept(evaluation.getRunningAccuracy()), statusConsumer);
	}

	private void runEvaluationAsync(String productId, Consumer<EvaluationResult> resultConsumer, Consumer<String> statusConsumer) {
		new Thread(() -> {
			RuntimePerformanceTracker tracker = new RuntimePerformanceTracker("Validation/Test - " + productId);
			try {
				List<Candlestick> recent = fetchRecentCandles(productId);
				if (recent.size() < 300) { statusConsumer.accept("Insufficient candles: " + recent.size()); return; }
				NeuralNetwork net = loadModel(productId);
				if (net == null) { statusConsumer.accept("Model load failed"); return; }
				EvaluationResult result = evaluateRecentCandles(recent, net, statusConsumer);
				Map<String, String> details = new LinkedHashMap<>();
				details.put("Product", productId);
				details.put("Fetched Candlesticks", String.valueOf(recent.size()));
				details.put("Validation Samples", String.valueOf(result.getTotalSamples()));
				details.put("Final Accuracy (%)", String.format(java.util.Locale.US, "%.2f", result.getFinalAccuracy()));
				tracker.printSummary(details);
				resultConsumer.accept(result);
			} catch (Exception ex) {
				statusConsumer.accept("Test failed: " + ex.getMessage());
			}
		}).start();
	}

	private List<Candlestick> fetchRecentCandles(String productId) {
		return fetchRecentCandles(productId, 300);
	}

	private List<Candlestick> fetchRecentCandles(String productId, int count) {
		coinbaseAPI.Coinbase_Server cb = new coinbaseAPI.Coinbase_Server();
		long now = java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.MINUTES).truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toEpochMilli();
		cb.candlestickRequest(productId, now - 60000L * count);
		int code = cb.getResponseCode();
		String body = cb.getBody();
		if (code != 200 || body == null) return Collections.emptyList();

		List<Candlestick> candles = new ArrayList<>();
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
						candles.add(new Candlestick(ts, low, high, open, close, vol));
					} catch (Exception ex) { }
				}
				i = b + 1;
			}
		}

		Collections.sort(candles, Comparator.comparingLong(Candlestick::getTimestamp));
		if (candles.size() <= count) return candles;
		return new ArrayList<>(candles.subList(candles.size() - count, candles.size()));
	}

	private NeuralNetwork loadModel(String productId) {
		String modelPath = new File("data/neuralnetworks", "model_" + productId + ".bin").getPath();
		try {
			return NeuralNetwork.load(modelPath);
		} catch (Exception ex) {
			return null;
		}
	}

	private EvaluationResult evaluateRecentCandles(List<Candlestick> recent, NeuralNetwork net, Consumer<String> statusConsumer) {
		int samples = recent.size() - 35 + 1;
		List<Double> runningAccuracy = new ArrayList<>();
		int[][] decisionMatrix = new int[3][3];
		int correct = 0;

		for (int i = 0; i < samples; i++) {
			double[][] window = new double[30][5];
			for (int j = 0; j < 30; j++) {
				Candlestick c = recent.get(i + j);
				window[j][0] = c.getLow();
				window[j][1] = c.getHigh();
				window[j][2] = c.getOpen();
				window[j][3] = c.getClose();
				window[j][4] = c.getVolume();
			}

			double[] probs = net.predict(window);
			int pred = predictionIndex(probs);
			int actual = actualOutcome(recent, i);

			decisionMatrix[actual][pred]++;
			if (pred == actual) correct++;
			double acc = (double) correct / (double) (i + 1) * 100.0;
			runningAccuracy.add(acc);

			if (i % 10 == 0) statusConsumer.accept("Evaluating sample " + (i + 1) + "/" + samples);
		}

		return new EvaluationResult(runningAccuracy, decisionMatrix, correct, samples);
	}

	private int predictionIndex(double[] probs) {
		if (probs[1] >= probs[0] && probs[1] >= probs[2]) return 1;
		if (probs[0] >= probs[1] && probs[0] >= probs[2]) return 0;
		return 2;
	}

	private int actualOutcome(List<Candlestick> recent, int sampleStart) {
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		for (int j = 30; j < 35; j++) {
			Candlestick c = recent.get(sampleStart + j);
			if (c.getLow() < min) min = c.getLow();
			if (c.getHigh() > max) max = c.getHigh();
		}
		long base = recent.get(sampleStart + 29).getClose();
		double upDiff = (double) (max - base) / (double) base;
		double downDiff = (double) (base - min) / (double) base;
		if (upDiff > downDiff && upDiff > 0.002) return 0;
		if (downDiff > upDiff && downDiff > 0.002) return 2;
		return 1;
	}

	// Start training on a single product for given epochs (blocking simple implementation)
	public void startTraining(String productId, int epochs) {
		if (productStore == null) {
			try { productStore = new ProductStore("data/products.dat"); } catch (IOException ex) { ex.printStackTrace(); return; }
		}
        // load or create model (use new API: save(String) / load(String))
		File modelDir = new File("data/neuralnetworks");
		if (!modelDir.exists()) modelDir.mkdirs();
		String modelPath = new File(modelDir, "model_" + productId + ".bin").getPath();
		NeuralNetwork net = null;
		try {
			File mf = new File(modelPath);
			if (mf.exists()) net = NeuralNetwork.load(modelPath);
		} catch (Exception ex) {
			services.Logger.log("startTraining: failed to load model " + ex.getMessage());
		}
		if (net == null) net = new NeuralNetwork();

		File dir = new File("data/candlesticks");
		if (!dir.exists() || !dir.isDirectory()) return;

		File f = new File(dir, productId + "_1minute.dat");
		if (!f.exists()) return;
		try {
			byte[] all = java.nio.file.Files.readAllBytes(f.toPath());
			int recLen = models.Candlestick.RECORD_LENGTH;
			int total = all.length / recLen;
			// for each sample window of 35 candles
			for (int e = 0; e < epochs; e++) {
				for (int i = 0; i + 35 <= total; i++) {
					double[][] window = new double[35][5];
					for (int j = 0; j < 35; j++) {
						int off = (i + j) * recLen;
						String rec = new String(all, off, recLen, java.nio.charset.StandardCharsets.UTF_8);
						models.Candlestick c = new models.Candlestick(rec);
						// features: low, high, open, close, volume
						window[j][0] = c.getLow();
						window[j][1] = c.getHigh();
						window[j][2] = c.getOpen();
						window[j][3] = c.getClose();
						window[j][4] = c.getVolume();
					}
					// train expects double[][] candles35
					net.train(window);
				}
			}
			// save model
			net.save(modelPath);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	// Controller-level refresh: call API, update storage, and refresh view
	public void refreshProducts() {
        if (productStore == null) {
			try {
                productStore = new ProductStore("data/products.dat");
			} catch (IOException ex) {
				ex.printStackTrace();
				return;
			}
		}

        try {
            Logger.log("refreshProducts: starting productStore.refreshFromCoinbase");
			productStore.refreshFromCoinbase();
			Logger.log("refreshProducts: finished productStore.refreshFromCoinbase");
		} catch (IOException ex) {
            Logger.log("refreshProducts: exception " + ex.getMessage());
			ex.printStackTrace();
		}

		// Re-show stocks view to refresh UI
		showStocksAvailableView();
	}

	// Provide product list to views (view has no storage logic)
	public List<Product> getProducts() {
		if (productStore == null) return Collections.emptyList();
		try {
            Logger.log("getProducts: reading all products");
			return productStore.readAll();
		} catch (IOException ex) {
            Logger.log("getProducts: exception " + ex.getMessage());
			ex.printStackTrace();
			return Collections.emptyList();
		}
	}

    // Resource cleanup moved to Application.stop() in services.App
}
