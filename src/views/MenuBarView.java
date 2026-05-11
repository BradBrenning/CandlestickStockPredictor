package views;

import controllers.AppController;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class MenuBarView {

	private AppController controller;
	private VBox vBox;

  public MenuBarView(AppController controller) {
		this(controller, false);
	}

	// Overloaded constructor allows choosing an admin menu layout
	public MenuBarView(AppController controller, boolean isAdmin) {
		this.controller = controller;
		if(isAdmin)
			setupAdmin();
		else
			setupUser();
	}

	// Sets up menu bar for admin users with additional options
	private void setupAdmin() {
		// Different menus
		Menu menuStocks = new Menu("Stocks");
		Menu menuTraining = new Menu("Training");
		Menu menuTesting = new Menu("Testing");

		// Different options in menus
		MenuItem stocksAvailableStocks = new MenuItem("Available Stocks");
		MenuItem trainingSettings = new MenuItem("Settings");
		MenuItem testingReview = new MenuItem("Review");

		// Adding options to menus
		menuStocks.getItems().addAll(stocksAvailableStocks);
		menuTraining.getItems().addAll(trainingSettings);
		menuTesting.getItems().addAll(testingReview);

		// Creating menu bar
		MenuBar menuBar = new MenuBar(menuStocks, menuTraining, menuTesting);

		// Event handlers for each menu option
		stocksAvailableStocks.setOnAction(e -> controller.handleMenuSelection("Stocks - Available Stocks"));
		trainingSettings.setOnAction(e -> controller.handleMenuSelection("Training - Settings"));
		testingReview.setOnAction(e -> controller.handleMenuSelection("Testing - Review"));

		vBox = new VBox(menuBar);
	}

	// Sets up menu bar for regular users with limited options
	private void setupUser() {
		// Different menu
		Menu menuStocks = new Menu("Stocks");
		Menu menuTrades = new Menu("Trades");

		// Different options in menus
		MenuItem stocksViewStocks = new MenuItem("View Stocks");
		MenuItem tradesHistory = new MenuItem("History");

		// Adding options to menus
		menuStocks.getItems().addAll(stocksViewStocks);
		menuTrades.getItems().addAll(tradesHistory);

		// Creating menu bar
		MenuBar menuBar = new MenuBar(menuStocks, menuTrades);

		// Event handlers for each menu option
		stocksViewStocks.setOnAction(e -> controller.handleMenuSelection("Stocks - View Stocks"));
		tradesHistory.setOnAction(e -> controller.handleMenuSelection("Trades - History"));

		vBox = new VBox(menuBar);
	}

	// Getter for menu bar to be added to main application layout
	public VBox getMenuBar() {
		return vBox;
	}
}