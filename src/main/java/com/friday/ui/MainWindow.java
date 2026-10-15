package com.friday.ui;

import com.friday.assistant.AssistantState;
import com.friday.assistant.AssistantStateController;
import com.friday.ui.actions.GreetingAndActionsPanel;
import com.friday.ui.dock.BottomDock;
import com.friday.ui.header.HeaderArea;
import com.friday.ui.hologram.HologramAssistantView;
import com.friday.ui.navigation.LeftNavigationBar;
import com.friday.ui.panels.NotesPanel;
import com.friday.ui.panels.StateControlBar;
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
 * Main HUD Window Layout bringing together the full-screen futuristic holographic
 * assistant interface.
 *
 * The {@link AssistantStateController} is created here and passed down to all
 * components that need real state/amplitude data.  Call {@link #shutdown()} when
 * the stage is closing so audio resources are released cleanly.
 */
public final class MainWindow extends BorderPane {

    /** Shared controller — owns the state machine and audio services. */
    private final AssistantStateController stateController;

    /** Hold a reference so we can call shutdown(). */
    private final HologramAssistantView hologramView;

    public MainWindow(Stage stage) {
        getStyleClass().add("window-root");

        // ── Create the shared controller first ──────────────────────────────
        stateController = new AssistantStateController();

        // ── 1. Top Section: Title Bar + HUD Header ───────────────────────────
        VBox topContainer = new VBox();
        TitleBar titleBar = new TitleBar(stage);
        HeaderArea headerArea = new HeaderArea();
        topContainer.getChildren().addAll(titleBar, headerArea);
        setTop(topContainer);

        // ── 2. Left Section: Vertical Navigation Pills ───────────────────────
        LeftNavigationBar leftNav = new LeftNavigationBar();
        setLeft(leftNav);

        // ── 3. Center HUD Workspace (hologram + side panels) ─────────────────
        hologramView = new HologramAssistantView(stateController);
        StackPane centerStack = createCenterHudLayout(hologramView);
        setCenter(centerStack);

        // ── 4. Bottom Section: System Status + Dock + Waveform Mic ───────────
        HBox bottomBar = createBottomBar();
        setBottom(bottomBar);

        // ── 5. Wire stage close → resource cleanup ────────────────────────────
        stage.setOnCloseRequest(e -> shutdown());
    }

    /** Called on window close. Stops animations and releases audio resources. */
    public void shutdown() {
        hologramView.shutdown();
        stateController.shutdown();
    }

    // ── Private layout builders ───────────────────────────────────────────────

    private StackPane createCenterHudLayout(HologramAssistantView hv) {
        StackPane stack = new StackPane();
        stack.getStyleClass().add("center-hud-workspace");
        stack.setPadding(new Insets(6, 16, 6, 12));

        // Layer 0 — Hologram Canvas
        StackPane.setAlignment(hv, Pos.CENTER);

        // Layer 1 — HUD Panel overlay (picks on specific children only)
        BorderPane hudPanelsLayout = new BorderPane();
        hudPanelsLayout.setPickOnBounds(false);

        // Left Side: Greeting & Quick Actions
        GreetingAndActionsPanel actionsPanel = new GreetingAndActionsPanel(stateController);
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

        // Bottom Centre: State Control Bar (sits inside the center area)
        StateControlBar stateBar = new StateControlBar(stateController);
        BorderPane.setAlignment(stateBar, Pos.BOTTOM_CENTER);
        BorderPane.setMargin(stateBar, new Insets(0, 0, 6, 0));
        hudPanelsLayout.setBottom(stateBar);

        stack.getChildren().addAll(hv, hudPanelsLayout);
        return stack;
    }

    private HBox createBottomBar() {
        HBox bottomBox = new HBox(12);
        bottomBox.getStyleClass().add("hud-bottom-bar");
        bottomBox.setAlignment(Pos.CENTER);
        bottomBox.setPadding(new Insets(4, 16, 12, 16));

        // Bottom Left: System Status Gauges
        SystemStatusPanel systemStatusPanel = new SystemStatusPanel();

        Region spacerLeft = new Region();
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);

        // Center: Futuristic App Dock
        BottomDock bottomDock = new BottomDock();

        Region spacerRight = new Region();
        HBox.setHgrow(spacerRight, Priority.ALWAYS);

        // Bottom Right: Waveform & Microphone Status — wired to the controller
        WaveformMicPanel waveformMicPanel = new WaveformMicPanel(stateController);

        bottomBox.getChildren().addAll(systemStatusPanel, spacerLeft, bottomDock, spacerRight, waveformMicPanel);
        return bottomBox;
    }
}
