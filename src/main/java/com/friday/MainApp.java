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

    private static final double DEFAULT_TARGET_WIDTH = 1180.0;
    private static final double DEFAULT_TARGET_HEIGHT = 740.0;
    private static final double MIN_WIDTH = 900.0;
    private static final double MIN_HEIGHT = 580.0;

    @Override
    public void start(Stage primaryStage) {
        try {
            Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
            double screenW = visualBounds.getWidth();
            double screenH = visualBounds.getHeight();

            // Sizing: adapt to current screen visual bounds so it never opens overflowing or clipped
            double initialWidth = Math.min(DEFAULT_TARGET_WIDTH, Math.max(880.0, screenW * 0.90));
            double initialHeight = Math.min(DEFAULT_TARGET_HEIGHT, Math.max(580.0, screenH * 0.88));

            double minW = Math.min(MIN_WIDTH, screenW * 0.75);
            double minH = Math.min(MIN_HEIGHT, screenH * 0.75);

            // Configure window properties
            primaryStage.setTitle("F.R.I.D.A.Y.");
            primaryStage.initStyle(StageStyle.TRANSPARENT);
            primaryStage.setMinWidth(minW);
            primaryStage.setMinHeight(minH);
            primaryStage.setMaxWidth(screenW);
            primaryStage.setMaxHeight(screenH);
            primaryStage.setAlwaysOnTop(false);

            // Create root layout and scene
            MainWindow root = new MainWindow(primaryStage);
            Scene scene = new Scene(root, initialWidth, initialHeight);
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
            centerOnPrimaryScreen(primaryStage, visualBounds, initialWidth, initialHeight);

            primaryStage.show();
        } catch (Exception ex) {
            System.err.println("Fatal error during F.R.I.D.A.Y. startup: " + ex.getMessage());
            ex.printStackTrace();
            throw ex;
        }
    }

    private void centerOnPrimaryScreen(Stage stage, Rectangle2D visualBounds, double width, double height) {
        double targetX = visualBounds.getMinX() + (visualBounds.getWidth() - width) / 2.0;
        double targetY = visualBounds.getMinY() + (visualBounds.getHeight() - height) / 2.0;

        // Ensure strictly within visible screen bounds
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
