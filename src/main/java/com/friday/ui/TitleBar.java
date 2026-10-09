package com.friday.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * Custom window title bar providing window drag handling and window controls (minimize, close).
 */
public final class TitleBar extends HBox {

    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

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

        Button btnClose = createControlButton("✕", "Close", "control-btn-close");
        btnClose.setAccessibleText("Close application");
        btnClose.setOnAction(e -> {
            stage.close();
            Platform.exit();
        });

        // Ensure button clicks do not trigger window dragging
        btnMinimize.setOnMousePressed(e -> e.consume());
        btnClose.setOnMousePressed(e -> e.consume());

        HBox controlsGroup = new HBox(6, btnMinimize, btnClose);
        controlsGroup.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(titleGroup, spacer, controlsGroup);

        // Dragging logic for frameless stage
        setupDragHandlers(stage);
    }

    private Button createControlButton(String text, String tooltipText, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("window-control-btn", styleClass);
        button.setFocusTraversable(false);
        Tooltip tooltip = new Tooltip(tooltipText);
        Tooltip.install(button, tooltip);
        return button;
    }

    private void setupDragHandlers(Stage stage) {
        setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown()) {
                dragOffsetX = stage.getX() - event.getScreenX();
                dragOffsetY = stage.getY() - event.getScreenY();
            }
        });

        setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown()) {
                stage.setX(event.getScreenX() + dragOffsetX);
                stage.setY(event.getScreenY() + dragOffsetY);
            }
        });
    }
}

