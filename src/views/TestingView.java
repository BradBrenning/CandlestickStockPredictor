package views;

import controllers.AppController;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.*;
import javafx.scene.chart.*;
import models.Product;
import models.Candlestick;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TestingView {

    private AppController controller;
    private VBox root;
    private ListView<String> leftList;
    private Button testButton;
    private LineChart<Number, Number> chart;
    private Label statusLabel;

    public TestingView(AppController controller) {
        this.controller = controller;
        setup();
    }

    private void setup() {
        root = new VBox(8);
        root.setPadding(new Insets(8));

        HBox center = new HBox(10);

        leftList = new ListView<>();
        leftList.setPrefWidth(200);

        VBox leftBox = new VBox(8);
        testButton = new Button("Test");
        testButton.setOnAction(e -> onTest());
        leftBox.getChildren().addAll(new Label("Overridden Products"), leftList, testButton);

        // chart on right
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis(0, 100, 10);
        xAxis.setLabel("Sample #");
        yAxis.setLabel("Accuracy (%)");
        chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Test Accuracy over Samples");
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setPrefWidth(600);

        VBox rightBox = new VBox(8);
        statusLabel = new Label("Ready");
        rightBox.getChildren().addAll(statusLabel, chart);

        center.getChildren().addAll(leftBox, rightBox);
        root.getChildren().add(center);

        loadProducts();
    }

    private void loadProducts() {
        List<Product> products = controller.getProducts();
        ObservableList<String> items = FXCollections.observableArrayList();
        for (Product p : products) if (p.isOverridden()) items.add(p.getProductId());
        leftList.setItems(items);
    }

    private void onTest() {
        String sel = leftList.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        testButton.setDisable(true);
        statusLabel.setText("Starting test...");
        chart.getData().clear();

        controller.runTestAsync(sel, (results) -> {
            // results: list of running accuracy percentages
            javafx.application.Platform.runLater(() -> {
                XYChart.Series<Number, Number> series = new XYChart.Series<>();
                series.setName("Accuracy");
                for (int i = 0; i < results.size(); i++) {
                    series.getData().add(new XYChart.Data<>(i + 1, results.get(i)));
                }
                chart.getData().add(series);
                statusLabel.setText("Finished. Final accuracy: " + (results.isEmpty() ? "0" : String.format("%.2f%%", results.get(results.size()-1))));
                testButton.setDisable(false);
            });
        }, (status) -> {
            javafx.application.Platform.runLater(() -> statusLabel.setText(status));
        });
    }

    public VBox getView() { return root; }
}
