package views;

import controllers.AppController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.*;
import models.Product;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import models.Candlestick;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class TrainingSettingsView {

    private AppController controller;
    private VBox root;
    private ListView<String> leftList;
    private GridPane rightGrid;
    private TextField epochsField;
    private Button trainButton;
    private Button validateButton;

    public TrainingSettingsView(AppController controller) {
        this.controller = controller;
        setup();
    }

    private void setup() {
        root = new VBox(8);
        root.setPadding(new Insets(8));

        HBox center = new HBox(10);

        leftList = new ListView<>();
        leftList.setPrefWidth(200);
        leftList.setOnMouseClicked(e -> {
            String sel = leftList.getSelectionModel().getSelectedItem();
            if (sel != null) updateDetails(sel);
        });

        rightGrid = new GridPane();
        rightGrid.setHgap(8);
        rightGrid.setVgap(6);

        // prepare detail labels on right
        rightGrid.add(new Label("First Timestamp:"), 0, 0);
        rightGrid.add(new Label(""), 1, 0);
        rightGrid.add(new Label("Last Timestamp:"), 0, 1);
        rightGrid.add(new Label(""), 1, 1);
        rightGrid.add(new Label("Calculated Minutes:"), 0, 2);
        rightGrid.add(new Label(""), 1, 2);

        center.getChildren().addAll(leftList, rightGrid);

        HBox bottom = new HBox(8);
        epochsField = new TextField("1");
        epochsField.setPrefWidth(80);
        trainButton = new Button("Train");
        trainButton.setOnAction(e -> onTrain());
        validateButton = new Button("Validate");
        validateButton.setOnAction(e -> onValidate());
        bottom.getChildren().addAll(new Label("Epochs:"), epochsField, trainButton, validateButton);

        root.getChildren().addAll(center, bottom);

        loadProducts();
    }

    private void loadProducts() {
        List<Product> products = controller.getProducts();
        ObservableList<String> items = FXCollections.observableArrayList();
        for (Product p : products) {
            if (p.isOverridden()) items.add(p.getProductId());
        }
        leftList.setItems(items);
    }

    private void onTrain() {
        String sel = leftList.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        int epochs = 1;
        try { epochs = Integer.parseInt(epochsField.getText().trim()); } catch (Exception ex) { epochs = 1; }
        // show progress dialog
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Training Progress");
        Label status = new Label("Starting...");
        VBox content = new VBox(8, status);
        content.setPadding(new Insets(8));
        dlg.getDialogPane().setContent(content);
        // make dialog wider so long numbers are visible
        dlg.getDialogPane().setPrefWidth(900);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        // start async training with progress updates
        controller.startTrainingAsync(sel, epochs, (epoch, step, total) -> {
            status.setText("Epoch " + epoch + " - " + step + "/" + total);
        });

        dlg.show();
    }

    private void onValidate() {
        String sel = leftList.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        validateButton.setDisable(true);

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Validation Results - " + sel);

        Label statusLabel = new Label("Fetching latest 300 Coinbase candlesticks...");
        Label accuracyLabel = new Label("");

        GridPane matrixGrid = new GridPane();
        matrixGrid.setHgap(8);
        matrixGrid.setVgap(6);
        matrixGrid.setPadding(new Insets(8, 0, 0, 0));

        VBox content = new VBox(10, statusLabel, accuracyLabel, matrixGrid);
        content.setPadding(new Insets(12));

        dialog.setScene(new Scene(content, 420, 260));

        dialog.setOnHidden(e -> validateButton.setDisable(false));
        dialog.show();

        controller.runValidationAsync(sel, (result) -> {
            javafx.application.Platform.runLater(() -> {
                populateDecisionMatrix(matrixGrid, result.getDecisionMatrix());
                accuracyLabel.setText(String.format("Accuracy: %.2f%% (%d/%d)",
                        result.getFinalAccuracy(),
                        result.getCorrect(),
                        result.getTotalSamples()));
                statusLabel.setText("Validation complete");
                validateButton.setDisable(false);
            });
        }, (status) -> {
            javafx.application.Platform.runLater(() -> statusLabel.setText(status));
        });
    }

    private void populateDecisionMatrix(GridPane grid, int[][] matrix) {
        grid.getChildren().clear();
        String[] labels = {"Buy", "Hold", "Sell"};

        Label title = new Label("Decision Matrix");
        title.setStyle("-fx-font-weight: bold;");
        grid.add(title, 0, 0, 4, 1);

        grid.add(new Label("Actual \\ Predicted"), 0, 1);
        for (int col = 0; col < labels.length; col++) {
            Label header = new Label(labels[col]);
            header.setStyle("-fx-font-weight: bold;");
            grid.add(header, col + 1, 1);
        }

        for (int row = 0; row < labels.length; row++) {
            Label rowHeader = new Label(labels[row]);
            rowHeader.setStyle("-fx-font-weight: bold;");
            grid.add(rowHeader, 0, row + 2);
            for (int col = 0; col < labels.length; col++) {
                Label value = new Label(String.valueOf(matrix[row][col]));
                value.setMinWidth(60);
                value.setAlignment(Pos.CENTER);
                value.setStyle("-fx-border-color: lightgray; -fx-padding: 6;");
                grid.add(value, col + 1, row + 2);
            }
        }
    }

    // Update right-side details for selected product
    private void updateDetails(String productId) {
        // locate file
        File dir = new File("data/candlesticks");
        File f = new File(dir, productId + "_1minute.dat");
        // clear previous
        rightGrid.getChildren().removeIf(node -> GridPane.getColumnIndex(node) != null && GridPane.getColumnIndex(node) == 1);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        if (!f.exists()) {
            rightGrid.add(new Label("(file missing)"), 1, 0);
            rightGrid.add(new Label(""), 1, 1);
            rightGrid.add(new Label("0"), 1, 2);
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            long len = raf.length();
            int recLen = Candlestick.RECORD_LENGTH;
            long count = len / recLen;
            // read first
            long firstTs = 0L;
            long lastTs = 0L;
            if (count > 0) {
                raf.seek(0);
                byte[] buf = new byte[recLen];
                int r = raf.read(buf);
                if (r == recLen) {
                    try { Candlestick c = new Candlestick(new String(buf, StandardCharsets.UTF_8)); firstTs = c.getTimestamp(); } catch (Exception ex) { }
                }
                // last
                raf.seek((count - 1) * recLen);
                byte[] buf2 = new byte[recLen];
                int r2 = raf.read(buf2);
                if (r2 == recLen) {
                    try { Candlestick c2 = new Candlestick(new String(buf2, StandardCharsets.UTF_8)); lastTs = c2.getTimestamp(); } catch (Exception ex) { }
                }
            }
            long minutes = 0L;
            if (firstTs > 0 && lastTs > 0 && lastTs >= firstTs) minutes = (lastTs - firstTs) / 60000L + 1L;

            rightGrid.add(new Label(firstTs == 0 ? "" : fmt.format(Instant.ofEpochMilli(firstTs))), 1, 0);
            rightGrid.add(new Label(lastTs == 0 ? "" : fmt.format(Instant.ofEpochMilli(lastTs))), 1, 1);
            rightGrid.add(new Label(String.valueOf(minutes)), 1, 2);
        } catch (Exception ex) {
            rightGrid.add(new Label(""), 1, 0);
            rightGrid.add(new Label("error"), 1, 1);
            rightGrid.add(new Label("0"), 1, 2);
        }
    }

    public VBox getView() { return root; }
}
