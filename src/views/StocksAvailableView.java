package views;

import controllers.AppController;
// view must not perform business logic; controller will handle refresh
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.*;
import javafx.scene.input.MouseEvent;
import models.Product;
import models.ProductStore;

import java.util.List;

public class StocksAvailableView {

    private AppController controller;
    private VBox root;
    private ListView<String> leftList;
    private TextField searchField;
    private GridPane rightGrid;
    private Button refreshButton;
    private ProductStore store;
    private List<Product> products;

    private final String DATA_FILE = "products.dat"; // fixed-record file

    public StocksAvailableView(AppController controller) {
        this.controller = controller;
        setup();
    }

    private void setup() {
        root = new VBox();
        root.setSpacing(8);
        root.setPadding(new Insets(8));

        HBox center = new HBox();
        center.setSpacing(10);

        leftList = new ListView<>();
        leftList.setPrefWidth(150);
        leftList.setOnMouseClicked(this::onSelectItem);

        // Search field to filter products
        searchField = new TextField();
        searchField.setPromptText("Search product id...");
        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter(newV));

        rightGrid = new GridPane();
        rightGrid.setHgap(8);
        rightGrid.setVgap(6);

        // Put search field above left list
        VBox leftPane = new VBox(6, searchField, leftList);
        center.getChildren().addAll(leftPane, rightGrid);

        refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshData());

        root.getChildren().addAll(center, refreshButton);

        loadIntoView();
    }

    private void loadIntoView() {
        products = controller.getProducts();
        ObservableList<String> items = FXCollections.observableArrayList();
        for (Product p : products) {
            items.add(p.getProductId());
        }
        leftList.setItems(items);
    }

    // Filter left list by product id (case-insensitive contains)
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

    private void onSelectItem(MouseEvent ev) {
        String sel = leftList.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Product chosen = null;
        for (Product p : products) {
            if (p.getProductId().equals(sel)) {
                chosen = p; break;
            }
        }
        if (chosen == null) return;
        // Ensure hist_end is synced from any existing candlestick file before showing details
        controller.ensureHistEndFromFile(chosen.getProductId());
        // reload product from store to get updated hist_end
        try {
            List<Product> refreshed = controller.getProducts();
            for (Product np : refreshed) { if (np.getProductId().equals(chosen.getProductId())) { chosen = np; break; } }
        } catch (Exception ex) { /* ignore */ }
        populateRight(chosen);
    }

    private void populateRight(Product p) {
        rightGrid.getChildren().clear();
        int r = 0;
        java.time.ZoneId zid = java.time.ZoneId.systemDefault();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withZone(zid);
        rightGrid.add(new Label("Product ID:"), 0, r);
        rightGrid.add(new Label(p.getProductId()), 1, r);
        r++;
        rightGrid.add(new Label("High:"), 0, r);
        rightGrid.add(new Label(String.valueOf(p.getHigh())), 1, r);
        r++;
        rightGrid.add(new Label("Low:"), 0, r);
        rightGrid.add(new Label(String.valueOf(p.getLow())), 1, r);
        r++;
        rightGrid.add(new Label("30d Volume:"), 0, r);
        rightGrid.add(new Label(String.valueOf(p.getVolume30Day())), 1, r);
        r++;
        rightGrid.add(new Label("Service API:"), 0, r);
        rightGrid.add(new Label(p.getServiceAPI()), 1, r);
        r++;
        rightGrid.add(new Label("Tradeable:"), 0, r);
        rightGrid.add(new Label(p.isTradeable() ? "Yes" : "No"), 1, r);
        r++;
        rightGrid.add(new Label("Should Test:"), 0, r);
        rightGrid.add(new Label(p.shouldTest() ? "Yes" : "No"), 1, r);
        r++;
        rightGrid.add(new Label("Overridden:"), 0, r);
        rightGrid.add(new Label(p.isOverridden() ? "Yes" : "No"), 1, r);
        r++;
        rightGrid.add(new Label("Hist Start:"), 0, r);
        java.time.Instant hs = java.time.Instant.ofEpochMilli(p.getHistStart());
        String hsStr = p.getHistStart() == 0 ? "" : fmt.format(hs);
        rightGrid.add(new Label(hsStr), 1, r);
        r++;
        rightGrid.add(new Label("Hist End:"), 0, r);
        java.time.Instant he = java.time.Instant.ofEpochMilli(p.getHistEnd());
        String heStr = p.getHistEnd() == 0 ? "" : fmt.format(he);
        rightGrid.add(new Label(heStr), 1, r);
        r++;
        rightGrid.add(new Label("Modified:"), 0, r);
        // Convert unix millis to readable timestamp
        java.time.Instant mi = java.time.Instant.ofEpochMilli(p.getModified());
        rightGrid.add(new Label(fmt.format(mi)), 1, r);
        r++;
        // Add Find History, Override, and Update Data buttons at the bottom of the right grid
        Button findHistoryBtn = new Button("Find History");
        Button overrideBtn = new Button(p.isOverridden() ? "Un-Override" : "Override");
        Button updateDataBtn = new Button("Update Data");

        findHistoryBtn.setOnAction(e -> controller.findHistory(p.getProductId()));
        overrideBtn.setOnAction(e -> {
            // toggle override
            boolean newVal = !p.isOverridden();
            controller.setOverride(p.getProductId(), newVal);
            // refresh view to reflect change
            controller.showStocksAvailableView();
        });
        updateDataBtn.setOnAction(e -> {
            // only allowed if overridden
            if (p.isOverridden()) {
                controller.updateData(p.getProductId());
            }
        });

        // Only enable updateDataBtn when overridden
        updateDataBtn.setDisable(!p.isOverridden());

        HBox btns = new HBox(8, findHistoryBtn, overrideBtn, updateDataBtn);
        rightGrid.add(btns, 0, r, 2, 1);
    }

    private void refreshData() {
        // Delegate refresh to controller (controller will call API, update storage, and re-show view)
        controller.refreshProducts();
    }

    private void onFindHistory() {
        String sel = leftList.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        // delegate to controller to perform history search
        controller.findHistory(sel);
        // controller.findHistory will refresh the view
    }

    public VBox getView() {
        return root;
    }
}
