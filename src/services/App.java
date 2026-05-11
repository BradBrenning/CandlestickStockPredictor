package services;

import controllers.AppController;
import services.Logger;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;


// App is the base application for my project
public class App extends Application {

	@Override
	public void start(Stage stage) {
        // initialize logger
		Logger.init("candlestick");
		Logger.log("Application start");
		AppController controller = new AppController(stage);
	}

	@Override
	public void stop() throws Exception {
		Logger.log("Application stopping");
		Logger.close();
		super.stop();
	}

	// Starts up application
	public static void main(String[] args) {
		launch(args);
	}
}