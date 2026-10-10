package com.friday;

import com.friday.ui.MainWindow;
import com.friday.ui.WindowResizeHelper;
import java.net.URL;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class MainApp extends Application {

    private static final double INITIAL_WIDTH = 1200.0;
    private static final double INITIAL_HEIGHT = 780.0;
    private static final double MIN_WIDTH = 960.0;
    private static final double MIN_HEIGHT = 640.0;

    @Override
    public void start(Stage primaryStage) {
        try {
            // Configure window properties
            primaryStage.setTitle("F.R.I.D.A.Y.");
            primaryStage.initStyle(StageStyle.TRANSPARENT);
            primaryStage.setMinWidth(MIN_WIDTH);
            primaryStage.setMinHeight(MIN_HEIGHT);
            primaryStage.setAlwaysOnTop(false);

            // Create root layout and scene
            MainWindow root = new MainWindow(primaryStage);
            Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
            scene.setFill(Color.TRANSPARENT);

            // Load stylesheet
            URL cssResource = getClass().getResource("/com/friday/app.css");
            if (cssResource != null) {
                scene.getStylesheets().add(cssResource.toExternalForm());
            }

            // Attach resize handler for frameless window
            WindowResizeHelper.attach(primaryStage, scene);

            primaryStage.setScene(scene);

            // Position within primary screen bounds
            centerOnPrimaryScreen(primaryStage, INITIAL_WIDTH, INITIAL_HEIGHT);

            primaryStage.show();
        } catch (Exception ex) {
            System.err.println("Fatal error during F.R.I.D.A.Y. startup: " + ex.getMessage());
            ex.printStackTrace();
            throw ex;
        }
    }

    private void centerOnPrimaryScreen(Stage stage, double width, double height) {
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double targetX = visualBounds.getMinX() + (visualBounds.getWidth() - width) / 2.0;
        double targetY = visualBounds.getMinY() + (visualBounds.getHeight() - height) / 2.0;

        // Ensure within visible screen bounds
        double clampedX = Math.max(visualBounds.getMinX(), Math.min(targetX, visualBounds.getMaxX() - width));
        double clampedY = Math.max(visualBounds.getMinY(), Math.min(targetY, visualBounds.getMaxY() - height));

        stage.setX(clampedX);
        stage.setY(clampedY);
        stage.setWidth(width);
        stage.setHeight(height);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
