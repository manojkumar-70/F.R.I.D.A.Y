package com.friday.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

/**
 * Main window container layout providing the dark navy backdrop, title bar, content area, and status footer.
 */
public final class MainWindow extends BorderPane {

    public MainWindow(Stage stage) {
        getStyleClass().add("window-root");

        // Top: Custom Title Bar
        TitleBar titleBar = new TitleBar(stage);
        setTop(titleBar);

        // Center: Primary Content Area (Glass HUD foundation)
        setCenter(createCenterContent());

        // Bottom: Status Footer
        setBottom(createFooter());
    }

    private StackPane createCenterContent() {
        StackPane centerPane = new StackPane();
        centerPane.getStyleClass().add("center-content-pane");
        centerPane.setPadding(new Insets(24));

        VBox glassCard = new VBox(14);
        glassCard.getStyleClass().addAll("glass-panel", "glass-card", "status-card");
        glassCard.setAlignment(Pos.CENTER);
        glassCard.setMaxWidth(580);
        glassCard.setMaxHeight(270);

        Label systemBadge = new Label("F.R.I.D.A.Y. HUD ARCHITECTURE");
        systemBadge.getStyleClass().addAll("glass-badge", "system-badge");

        Label headlineLabel = new Label("F.R.I.D.A.Y. System Online");
        headlineLabel.getStyleClass().add("headline-label");

        Label subLabel = new Label("Phase 2.2 — Translucent Glass Panel Foundation");
        subLabel.getStyleClass().add("sub-label");

        HBox statusRow = new HBox(8);
        statusRow.setAlignment(Pos.CENTER);

        Circle pulseDot = new Circle(4);
        pulseDot.getStyleClass().add("status-pulse-dot");

        Label statusState = new Label("Glass Styling & Design Tokens Active");
        statusState.getStyleClass().add("status-state-text");

        statusRow.getChildren().addAll(pulseDot, statusState);

        glassCard.getChildren().addAll(systemBadge, headlineLabel, subLabel, statusRow);
        centerPane.getChildren().add(glassCard);

        return centerPane;
    }

    private HBox createFooter() {
        HBox footer = new HBox(12);
        footer.getStyleClass().add("status-footer");
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(6, 16, 8, 16));

        Label leftStatus = new Label("FRAMELESS HUD BASE");
        leftStatus.getStyleClass().add("footer-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label rightStatus = new Label("PHASE 2.2 — GLASS STYLING READY");
        rightStatus.getStyleClass().add("footer-text");

        footer.getChildren().addAll(leftStatus, spacer, rightStatus);
        return footer;
    }
}
