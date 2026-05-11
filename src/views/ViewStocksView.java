package views;

import controllers.AppController;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import models.Candlestick;
import models.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ViewStocksView {

    private final AppController controller;
    private VBox root;
    private ListView<String> leftList;
    private TextField searchField;
    private LineChart<Number, Number> chart;
    private NumberAxis yAxis;
    private Label statusLabel;
    private Label recommendationLabel;
    private Label probabilitiesLabel;
    private Button buyButton;
    private Button sellButton;
    private Timeline refreshTimeline;
    private List<Product> products;
    private String selectedProductId;
    private double lastClosePrice;
    private final DateTimeFormatter refreshFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    public ViewStocksView(AppController controller) {
        this.controller = controller;
        setup();
    }

    private void setup() {
        root = new VBox(8);
        root.setPadding(new Insets(8));

        HBox center = new HBox(10);

        leftList = new ListView<>();
        leftList.setPrefWidth(180);
        leftList.setOnMouseClicked(this::onSelectItem);

        searchField = new TextField();
        searchField.setPromptText("Search product id...");
        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter(newV));

        VBox leftPane = new VBox(6, searchField, leftList);

        NumberAxis xAxis = new NumberAxis();
        yAxis = new NumberAxis();
        xAxis.setLabel("Most Recent Candlesticks");
        yAxis.setLabel("Close Price");
        chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Latest 50 Closing Prices");
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.setMinHeight(420);

        statusLabel = new Label("Select a product to begin live tracking.");
        recommendationLabel = new Label("Recommendation: N/A");
        probabilitiesLabel = new Label("Buy: 0.00%   Hold: 0.00%   Sell: 0.00%");

        buyButton = new Button("Buy");
        buyButton.setDisable(true);
        buyButton.setOnAction(e -> onBuy());

        sellButton = new Button("Sell");
        sellButton.setDisable(true);
        sellButton.setOnAction(e -> onSell());

        HBox actionBox = new HBox(8, buyButton, sellButton);
        VBox rightPane = new VBox(8, statusLabel, chart, recommendationLabel, probabilitiesLabel, actionBox);
        HBox.setHgrow(rightPane, Priority.ALWAYS);
        VBox.setVgrow(chart, Priority.ALWAYS);

        center.getChildren().addAll(leftPane, rightPane);
        HBox.setHgrow(center, Priority.ALWAYS);
        root.getChildren().add(center);
        VBox.setVgrow(center, Priority.ALWAYS);

        loadIntoView();
        setupLifecycle();
    }

    private void loadIntoView() {
        products = controller.getProducts();
        ObservableList<String> items = FXCollections.observableArrayList();
        for (Product p : products) items.add(p.getProductId());
        leftList.setItems(items);
    }

    private void applyFilter(String query) {
        if (products == null) return;
        String q = query == null ? "" : query.trim().toLowerCase();
        ObservableList<String> items = FXCollections.observableArrayList();
        for (Product p : products) {
            String id = p.getProductId();
            if (q.isEmpty() || id.toLowerCase().contains(q)) items.add(id);
        }
        leftList.setItems(items);
    }

    private void onSelectItem(MouseEvent event) {
        String sel = leftList.getSelectionModel().getSelectedItem();
        if (sel == null || sel.equals(selectedProductId)) return;
        selectedProductId = sel;
        buyButton.setDisable(false);
        sellButton.setDisable(false);
        refreshSnapshot();
        startRefreshTimeline();
    }

    private void refreshSnapshot() {
        if (selectedProductId == null || selectedProductId.isEmpty()) return;
        statusLabel.setText("Loading live Coinbase candlesticks for " + selectedProductId + "...");
        controller.fetchLiveViewSnapshotAsync(selectedProductId, (snapshot) -> Platform.runLater(() -> {
            lastClosePrice = snapshot.getLastClose();
            updateChart(snapshot.getChartCandles());
            updateProbabilities(snapshot.getProbabilities());
            statusLabel.setText("Last refresh for " + selectedProductId + ": " + LocalDateTime.now().format(refreshFormatter));
        }), (status) -> Platform.runLater(() -> statusLabel.setText(status)));
    }

    private void updateChart(List<Candlestick> candles) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        List<Candlestick> safeCandles = candles == null ? new ArrayList<>() : candles;
        long minLow = Long.MAX_VALUE;
        long maxHigh = Long.MIN_VALUE;
        for (int i = 0; i < safeCandles.size(); i++) {
            Candlestick candle = safeCandles.get(i);
            series.getData().add(new XYChart.Data<>(i + 1, candle.getClose()));
            if (candle.getLow() < minLow) minLow = candle.getLow();
            if (candle.getHigh() > maxHigh) maxHigh = candle.getHigh();
        }

        if (!safeCandles.isEmpty() && minLow < Long.MAX_VALUE && maxHigh > Long.MIN_VALUE) {
            double range = Math.max(1.0, (double) (maxHigh - minLow));
            double padding = range * 0.05;
            double lowerBound = Math.max(0.0, minLow - padding);
            double upperBound = maxHigh + padding;
            double tickUnit = Math.max(0.01, (upperBound - lowerBound) / 8.0);
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(lowerBound);
            yAxis.setUpperBound(upperBound);
            yAxis.setTickUnit(tickUnit);
        } else {
            yAxis.setAutoRanging(true);
        }
        chart.getData().setAll(series);
    }

    private void updateProbabilities(double[] probs) {
        double buyPct = probs.length > 0 ? probs[0] * 100.0 * 3.0 : 0.0;
        double holdPct = probs.length > 1 ? probs[1] * 100.0 * 0.5 : 0.0;
        double sellPct = probs.length > 2 ? probs[2] * 100.0 * 3.0 : 0.0;
        probabilitiesLabel.setText(String.format("Buy: %.2f%%   Hold: %.2f%%   Sell: %.2f%%", buyPct, holdPct, sellPct));
        recommendationLabel.setText("Recommendation: " + recommendationText(probs));
    }

    private String recommendationText(double[] probs) {
        if (probs == null || probs.length < 3) return "Model unavailable";
        if (probs[0] >= probs[1] && probs[0] >= probs[2]) return "Buy";
        if (probs[2] >= probs[0] && probs[2] >= probs[1]) return "Sell";
        return "Hold";
    }

    private void startRefreshTimeline() {
        stopRefreshTimeline();
        refreshTimeline = new Timeline(new KeyFrame(Duration.minutes(1), e -> refreshSnapshot()));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
    }

    private void stopRefreshTimeline() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
    }

    private void setupLifecycle() {
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                stopRefreshTimeline();
                return;
            }
            newScene.windowProperty().addListener((wObs, oldWindow, newWindow) -> {
                if (newWindow == null) {
                    stopRefreshTimeline();
                } else {
                    newWindow.setOnHidden(e -> stopRefreshTimeline());
                }
            });
        });
    }

    private void onBuy() {
        if (selectedProductId == null || selectedProductId.isEmpty() || lastClosePrice <= 0.0) {
            showMessage("Buy", "Select a product and wait for live pricing before buying.");
            return;
        }

        double availableFunds = controller.getTradeSnapshot().getFunds();
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Buy " + selectedProductId);
        dialog.setHeaderText("Buy at last close: $" + String.format("%.2f", lastClosePrice));

        ButtonType maxButtonType = new ButtonType("Max", ButtonBar.ButtonData.LEFT);
        ButtonType buyButtonType = new ButtonType("Buy", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(maxButtonType, buyButtonType, ButtonType.CANCEL);

        TextField amountField = new TextField();
        amountField.setPromptText("Dollar amount");
        Label fundsLabel = new Label(String.format("Available funds: $%.2f", availableFunds));
        dialog.getDialogPane().setContent(new VBox(8, fundsLabel, amountField));

        Button maxButton = (Button) dialog.getDialogPane().lookupButton(maxButtonType);
        maxButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            double wholeDollarMax = Math.floor(availableFunds);
            amountField.setText(String.format("%.0f", wholeDollarMax));
            event.consume();
        });

        dialog.setResultConverter(buttonType -> buttonType == buyButtonType ? amountField.getText() : null);
        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent()) return;

        try {
            double amount = Double.parseDouble(result.get().trim());
            if (amount <= 0.0) throw new NumberFormatException();
            controller.buyProduct(selectedProductId, amount, lastClosePrice);
            showMessage("Buy Complete", String.format("Bought $%.2f of %s at $%.2f.", amount, selectedProductId, lastClosePrice));
        } catch (NumberFormatException ex) {
            showMessage("Invalid Amount", "Enter a positive dollar amount.");
        } catch (Exception ex) {
            showMessage("Buy Failed", ex.getMessage());
        }
    }

    private void onSell() {
        if (selectedProductId == null || selectedProductId.isEmpty() || lastClosePrice <= 0.0) {
            showMessage("Sell", "Select a product and wait for live pricing before selling.");
            return;
        }

        try {
            double proceeds = controller.sellAllProduct(selectedProductId, lastClosePrice);
            showMessage("Sell Complete", String.format("Sold all %s holdings for $%.2f at $%.2f.", selectedProductId, proceeds, lastClosePrice));
        } catch (Exception ex) {
            showMessage("Sell Failed", ex.getMessage());
        }
    }

    private void showMessage(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public VBox getView() {
        return root;
    }
}
