package com.friday.ui;

import com.friday.ui.actions.GreetingAndActionsPanel;
import com.friday.ui.dock.BottomDock;
import com.friday.ui.header.HeaderArea;
import com.friday.ui.hologram.HologramAssistantView;
import com.friday.ui.navigation.LeftNavigationBar;
import com.friday.ui.panels.NotesPanel;
import com.friday.ui.panels.SystemStatusPanel;
import com.friday.ui.panels.TasksPanel;
import com.friday.ui.panels.WaveformMicPanel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Main HUD Window Layout bringing together the full-screen futuristic holographic assistant interface.
 */
public final class MainWindow extends BorderPane {

    public MainWindow(Stage stage) {
        getStyleClass().add("window-root");

        // 1. Top Section: Title Bar (frameless controls) + HUD Header Area
        VBox topContainer = new VBox();
        TitleBar titleBar = new TitleBar(stage);
        HeaderArea headerArea = new HeaderArea();
        topContainer.getChildren().addAll(titleBar, headerArea);
        setTop(topContainer);

        // 2. Left Section: Vertical Navigation Pills
        LeftNavigationBar leftNav = new LeftNavigationBar();
        setLeft(leftNav);

        // 3. Center HUD Workspace
        StackPane centerStack = createCenterHudLayout();
        setCenter(centerStack);

        // 4. Bottom Section: System Status + Bottom Dock + Waveform Mic
        HBox bottomBar = createBottomBar();
        setBottom(bottomBar);
    }

    private StackPane createCenterHudLayout() {
        StackPane stack = new StackPane();
        stack.getStyleClass().add("center-hud-workspace");
        stack.setPadding(new Insets(6, 16, 6, 12));

        // A. Hologram Assistant Visualization (Layer 0 - Center Canvas)
        HologramAssistantView hologramView = new HologramAssistantView();
        StackPane.setAlignment(hologramView, Pos.CENTER);

        // B. HUD Overlay Grid (Layer 1 - Panels)
        BorderPane hudPanelsLayout = new BorderPane();
        hudPanelsLayout.setPickOnBounds(false); // allow clicks through empty spaces

        // Left Side: Greeting & Quick Actions
        GreetingAndActionsPanel actionsPanel = new GreetingAndActionsPanel();
        actionsPanel.setPickOnBounds(true);
        BorderPane.setAlignment(actionsPanel, Pos.TOP_LEFT);
        BorderPane.setMargin(actionsPanel, new Insets(4, 12, 4, 4));
        hudPanelsLayout.setLeft(actionsPanel);

        // Right Side: Tasks & Notes
        VBox rightSide = new VBox(14);
        rightSide.setPickOnBounds(true);
        TasksPanel tasksPanel = new TasksPanel();
        NotesPanel notesPanel = new NotesPanel();
        rightSide.getChildren().addAll(tasksPanel, notesPanel);
        rightSide.setMinWidth(220);
        rightSide.setMaxWidth(260);
        BorderPane.setAlignment(rightSide, Pos.TOP_RIGHT);
        BorderPane.setMargin(rightSide, new Insets(4, 4, 4, 12));
        hudPanelsLayout.setRight(rightSide);

        stack.getChildren().addAll(hologramView, hudPanelsLayout);
        return stack;
    }

    private HBox createBottomBar() {
        HBox bottomBox = new HBox(12);
        bottomBox.getStyleClass().add("hud-bottom-bar");
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.setPadding(new Insets(4, 16, 12, 16));

        // Bottom Left: System Status Gauges & Sparkline
        SystemStatusPanel systemStatusPanel = new SystemStatusPanel();

        // Spacer
        Region spacerLeft = new Region();
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);

        // Center: Futuristic App Dock
        BottomDock bottomDock = new BottomDock();

        // Spacer
        Region spacerRight = new Region();
        HBox.setHgrow(spacerRight, Priority.ALWAYS);

        // Bottom Right: Waveform & Microphone Status
        WaveformMicPanel waveformMicPanel = new WaveformMicPanel();

        bottomBox.getChildren().addAll(systemStatusPanel, spacerLeft, bottomDock, spacerRight, waveformMicPanel);
        return bottomBox;
    }
}
