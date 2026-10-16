package com.friday.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Custom window title bar providing window drag handling, double-click maximize toggle,
 * and prominent window controls (minimize, maximize/restore, close).
 */
public final class TitleBar extends HBox {

    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

    private boolean isMaximized = false;
    private double prevX;
    private double prevY;
    private double prevWidth;
    private double prevHeight;

    private final Button btnMaximize;

    public TitleBar(Stage stage) {
        getStyleClass().add("title-bar");
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(8, 12, 8, 16));
        setSpacing(10);

        // Title and branding labels
        Label titleLabel = new Label("F.R.I.D.A.Y.");
        titleLabel.getStyleClass().add("title-bar-text");

        Label subtitleLabel = new Label("HUD WORKSPACE");
        subtitleLabel.getStyleClass().add("title-bar-subtext");

        HBox titleGroup = new HBox(8, titleLabel, subtitleLabel);
        titleGroup.setAlignment(Pos.CENTER_LEFT);

        // Flexible spacer between title and control buttons
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Window control buttons
        Button btnMinimize = createControlButton("—", "Minimize", "control-btn-minimize");
        btnMinimize.setAccessibleText("Minimize window");
        btnMinimize.setOnAction(e -> stage.setIconified(true));

        btnMaximize = createControlButton("□", "Maximize", "control-btn-maximize");
        btnMaximize.setAccessibleText("Maximize window");
        btnMaximize.setOnAction(e -> toggleMaximize(stage));

        Button btnClose = createControlButton("✕", "Close", "control-btn-close");
        btnClose.setAccessibleText("Close application");
        btnClose.setOnAction(e -> {
            stage.close();
            Platform.exit();
        });

        // Ensure button clicks do not trigger window dragging
        btnMinimize.setOnMousePressed(e -> e.consume());
        btnMaximize.setOnMousePressed(e -> e.consume());
        btnClose.setOnMousePressed(e -> e.consume());

        HBox controlsGroup = new HBox(6, btnMinimize, btnMaximize, btnClose);
        controlsGroup.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(titleGroup, spacer, controlsGroup);

        // Dragging and double-click maximize logic for frameless stage
        setupWindowInteractions(stage);
    }

    private Button createControlButton(String text, String tooltipText, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("window-control-btn", styleClass);
        button.setFocusTraversable(false);
        Tooltip tooltip = new Tooltip(tooltipText);
        Tooltip.install(button, tooltip);
        return button;
    }

    private void setupWindowInteractions(Stage stage) {
        setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                if (isMaximized) {
                    // Restore before dragging from maximized state
                    double ratioX = event.getX() / Math.max(1.0, getWidth());
                    toggleMaximize(stage);
                    stage.setX(event.getScreenX() - (prevWidth * ratioX));
                    stage.setY(event.getScreenY() - event.getY());
                }
                dragOffsetX = stage.getX() - event.getScreenX();
                dragOffsetY = stage.getY() - event.getScreenY();
            }
        });

        setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown() && !isMaximized) {
                stage.setX(event.getScreenX() + dragOffsetX);
                stage.setY(event.getScreenY() + dragOffsetY);
            }
        });

        setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                toggleMaximize(stage);
            }
        });
    }

    /**
     * Toggles between maximized (screen visual bounds, excluding taskbar) and restored window bounds.
     */
    public void toggleMaximize(Stage stage) {
        if (!isMaximized) {
            prevX = stage.getX();
            prevY = stage.getY();
            prevWidth = stage.getWidth();
            prevHeight = stage.getHeight();

            Screen currentScreen = getScreenForStage(stage);
            Rectangle2D bounds = currentScreen.getVisualBounds();

            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());

            isMaximized = true;
            btnMaximize.setText("❐");
            Tooltip.install(btnMaximize, new Tooltip("Restore"));
            btnMaximize.setAccessibleText("Restore window");
        } else {
            stage.setX(prevX);
            stage.setY(prevY);
            stage.setWidth(prevWidth);
            stage.setHeight(prevHeight);

            isMaximized = false;
            btnMaximize.setText("□");
            Tooltip.install(btnMaximize, new Tooltip("Maximize"));
            btnMaximize.setAccessibleText("Maximize window");
        }
    }

    private static Screen getScreenForStage(Stage stage) {
        double stageCenterX = stage.getX() + stage.getWidth() / 2.0;
        double stageCenterY = stage.getY() + stage.getHeight() / 2.0;
        for (Screen screen : Screen.getScreens()) {
            if (screen.getVisualBounds().contains(stageCenterX, stageCenterY)) {
                return screen;
            }
        }
        return Screen.getPrimary();
    }
}
