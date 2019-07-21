package com.chairbender.mahjongeye;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.opencv.core.Core;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;



@SpringBootApplication
public class MahjongeyeApplication extends Application {
	private ConfigurableApplicationContext springContext;
	private Parent rootNode;
	private FXMLLoader fxmlLoader;


	public static void main(String[] args) {
		// load the native OpenCV library
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		launch(args);
	}

	@Override
	public void init() throws Exception {
		springContext = SpringApplication.run(MahjongeyeApplication.class);
		fxmlLoader = new FXMLLoader();
		fxmlLoader.setControllerFactory(springContext::getBean);
	}

	/**
	 * The main entry point for all JavaFX applications.
	 * The start method is called after the init method has returned,
	 * and after the system is ready for the application to begin running.
	 *
	 * <p>
	 * NOTE: This method is called on the JavaFX Application Thread.
	 * </p>
	 *
	 * @param primaryStage the primary stage for this application, onto which
	 *                     the application scene can be set.
	 *                     Applications may create other stages, if needed, but they will not be
	 *                     primary stages.
	 * @throws Exception if something goes wrong
	 */
	@Override
	public void start(Stage primaryStage) throws Exception {
		MainController.loadSettings();
		fxmlLoader.setLocation(getClass().getResource("/fxml/main.fxml"));
		rootNode = fxmlLoader.load();

		primaryStage.setTitle("Hello World");
		Scene scene = new Scene(rootNode, 1024, 768);
		primaryStage.setScene(scene);

		//cleanup controller when window closes
		MainController controller = fxmlLoader.getController();
		primaryStage.setOnHidden(e -> controller.shutdown());

		primaryStage.show();

	}

	@Override
	public void stop() {
		springContext.stop();
	}

}
