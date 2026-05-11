package views;

import controllers.AppController;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import services.TradeLedger;

import java.util.Map;
import java.util.Optional;

public class TradesHistoryView {

    private final AppController controller;
    private VBox root;
    private Label fundsLabel;
    private Label holdingsLabel;
    private Label gainLossLabel;
    private LineChart<Number, Number> chart;
    private NumberAxis yAxis;

    public TradesHistoryView(AppController controller) {
        this.controller = controller;
        setup();
    }

    private void setup() {
        root = new VBox(8);
        root.setPadding(new Insets(8));

        HBox center = new HBox(10);

        VBox leftPane = new VBox(10);
        leftPane.setPrefWidth(240);
        fundsLabel = new Label("Funds: $0.00");
        holdingsLabel = new Label("Holdings: None");

        Button addFundsButton = new Button("Add Funds");
        addFundsButton.setOnAction(e -> onAddFunds());
        Button removeFundsButton = new Button("Remove Funds");
        removeFundsButton.setOnAction(e -> onRemoveFunds());
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshView());

        leftPane.getChildren().addAll(new Label("Account"), fundsLabel, holdingsLabel, addFundsButton, removeFundsButton, refreshButton);

        NumberAxis xAxis = new NumberAxis();
        yAxis = new NumberAxis();
        xAxis.setLabel("Log Entry");
        yAxis.setLabel("Balance");
        chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Historical Balance");
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        VBox.setVgrow(chart, Priority.ALWAYS);

        gainLossLabel = new Label("Gain/Loss: 0.00%");

        VBox rightPane = new VBox(8, chart, gainLossLabel);
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        center.getChildren().addAll(leftPane, rightPane);
        HBox.setHgrow(center, Priority.ALWAYS);
        VBox.setVgrow(center, Priority.ALWAYS);
        root.getChildren().add(center);

        refreshView();
    }

    private void refreshView() {
        TradeLedger.LedgerSnapshot snapshot = controller.getTradeSnapshot();
        fundsLabel.setText(String.format("Funds: $%.2f", snapshot.getFunds()));
        holdingsLabel.setText("Holdings: " + holdingsText(snapshot));
        gainLossLabel.setText(String.format("Gain/Loss: %.2f%%", snapshot.getGainLossPercent()));
        updateChart(snapshot);
    }

    private void updateChart(TradeLedger.LedgerSnapshot snapshot) {
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        double minBalance = Double.MAX_VALUE;
        double maxBalance = -Double.MAX_VALUE;
        for (int i = 0; i < snapshot.getBalanceHistory().size(); i++) {
            TradeLedger.BalancePoint point = snapshot.getBalanceHistory().get(i);
            series.getData().add(new XYChart.Data<>(i + 1, point.getBalance()));
            minBalance = Math.min(minBalance, point.getBalance());
            maxBalance = Math.max(maxBalance, point.getBalance());
        }

        if (!snapshot.getBalanceHistory().isEmpty()) {
            double range = Math.max(1.0, maxBalance - minBalance);
            double padding = range * 0.10;
            double lowerBound = minBalance - padding;
            double upperBound = maxBalance + padding;
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

    private String holdingsText(TradeLedger.LedgerSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, TradeLedger.HoldingsPosition> entry : snapshot.getHoldings().entrySet()) {
            if (entry.getValue().getQuantity() <= 0.0) continue;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(entry.getKey()).append(": ").append(String.format("%.6f", entry.getValue().getQuantity()));
        }
        return sb.length() == 0 ? "None" : sb.toString();
    }

    private void onAddFunds() {
        Optional<Double> amount = requestAmount("Add Funds", "Dollar amount to add:");
        if (!amount.isPresent()) return;
        try {
            controller.addFunds(amount.get());
            refreshView();
        } catch (Exception ex) {
            gainLossLabel.setText("Add funds failed: " + ex.getMessage());
        }
    }

    private void onRemoveFunds() {
        Optional<Double> amount = requestAmount("Remove Funds", "Dollar amount to withdraw:");
        if (!amount.isPresent()) return;
        try {
            controller.removeFunds(amount.get());
            refreshView();
        } catch (Exception ex) {
            gainLossLabel.setText("Withdraw failed: " + ex.getMessage());
        }
    }

    private Optional<Double> requestAmount(String title, String prompt) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(prompt);
        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent()) return Optional.empty();
        try {
            double value = Double.parseDouble(result.get().trim());
            if (value <= 0.0) return Optional.empty();
            return Optional.of(value);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public VBox getView() {
        return root;
    }
}
