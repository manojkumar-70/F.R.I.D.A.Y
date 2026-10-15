package com.friday.ui.actions;

import com.friday.assistant.AssistantState;
import com.friday.assistant.AssistantStateController;
import java.awt.Desktop;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.time.LocalTime;
import javax.imageio.ImageIO;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Left-side Greeting Card &amp; Quick Actions HUD Panel.
 *
 * Accepts an optional {@link AssistantStateController} so the greeting mic
 * button can drive the shared LISTENING state (and thereby start the real
 * microphone service).  When the controller is {@code null} the panel falls
 * back to a standalone stub dialogue.
 */
public final class GreetingAndActionsPanel extends VBox {

    /** May be null (standalone / legacy mode). */
    private final AssistantStateController controller;

    /** Convenience constructor for callers that have no controller. */
    public GreetingAndActionsPanel() {
        this(null);
    }

    public GreetingAndActionsPanel(AssistantStateController controller) {
        this.controller = controller;

        getStyleClass().add("greeting-actions-container");
        setSpacing(14);
        setMinWidth(220);
        setMaxWidth(260);

        getChildren().addAll(createGreetingCard(), createQuickActionsCard());
    }

    // ── Card builders ─────────────────────────────────────────────────────────

    private VBox createGreetingCard() {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("glass-panel", "greeting-card");
        card.setPadding(new Insets(14, 16, 14, 16));

        Label greetingLabel = new Label(getDynamicGreeting() + ", Manoj.");
        greetingLabel.getStyleClass().add("greeting-title");

        Label subPrompt = new Label("How can I help you today?");
        subPrompt.getStyleClass().add("greeting-subtitle");

        Button micPromptBtn = new Button("🎤");
        micPromptBtn.getStyleClass().add("greeting-mic-btn");
        Tooltip.install(micPromptBtn, new Tooltip("Toggle microphone / listening mode"));
        micPromptBtn.setOnAction(e -> handleGreetingMicClick());

        // Keep label in sync if a controller is wired
        if (controller != null) {
            controller.stateProperty().addListener((obs, old, nw) -> {
                if (nw == AssistantState.LISTENING) {
                    micPromptBtn.setText("🔴");
                    subPrompt.setText(controller.isMicAvailable()
                            ? "Listening…"
                            : "Listening… [no mic]");
                } else {
                    micPromptBtn.setText("🎤");
                    subPrompt.setText("How can I help you today?");
                }
            });
        }

        HBox promptRow = new HBox(8, subPrompt, micPromptBtn);
        promptRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(subPrompt, Priority.ALWAYS);

        card.getChildren().addAll(greetingLabel, promptRow);
        return card;
    }

    private VBox createQuickActionsCard() {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("glass-panel", "quick-actions-card");
        card.setPadding(new Insets(12, 14, 12, 14));

        Label header = new Label("Quick Actions");
        header.getStyleClass().add("quick-actions-header");

        VBox buttonList = new VBox(6);
        buttonList.getChildren().addAll(
                createActionButton("◬  Open VS Code",       this::launchVsCode),
                createActionButton("🔍  Search the web",    () -> openWebBrowser("https://google.com")),
                createActionButton("📄  Summarize this",    () -> showActionFeedback("Summarize",   "Ready to summarize active buffer.")),
                createActionButton("💡  Explain code",      () -> showActionFeedback("Explain Code","AI Code Explanation module ready.")),
                createActionButton("📝  Create a plan",     () -> showActionFeedback("Create Plan", "Planning assistant module ready.")),
                createActionButton("📸  Take a screenshot", this::captureScreenshot)
        );

        card.getChildren().addAll(header, buttonList);
        return card;
    }

    private Button createActionButton(String label, Runnable action) {
        Button btn = new Button(label);
        btn.getStyleClass().add("quick-action-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setFocusTraversable(false);
        btn.setOnAction(e -> {
            try { action.run(); }
            catch (Exception ex) { showActionFeedback("Action Error", ex.getMessage()); }
        });
        return btn;
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    private void handleGreetingMicClick() {
        if (controller != null) {
            AssistantState current = controller.getState();
            controller.setState(current == AssistantState.LISTENING
                    ? AssistantState.IDLE
                    : AssistantState.LISTENING);
        } else {
            showActionFeedback("Voice Command", "Voice AI integration is on standby.");
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String getDynamicGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour >= 5  && hour < 12) return "Good Morning";
        if (hour >= 12 && hour < 17) return "Good Afternoon";
        if (hour >= 17 && hour < 22) return "Good Evening";
        return "Good Night";
    }

    private void launchVsCode() {
        try {
            new ProcessBuilder("cmd.exe", "/c", "code .").start();
        } catch (Exception ex) {
            showActionFeedback("VS Code", "Attempted to launch VS Code (`code .`). Error: " + ex.getMessage());
        }
    }

    private void openWebBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ex) {
            showActionFeedback("Web Browser", "Could not open browser: " + ex.getMessage());
        }
    }

    private void captureScreenshot() {
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            java.awt.image.BufferedImage capture = robot.createScreenCapture(screenRect);
            File out = new File(System.getProperty("user.home"),
                    "FRIDAY_Screenshot_" + System.currentTimeMillis() + ".png");
            ImageIO.write(capture, "png", out);
            showActionFeedback("Screenshot Captured", "Saved to: " + out.getAbsolutePath());
        } catch (Exception ex) {
            showActionFeedback("Screenshot", "Error taking screenshot: " + ex.getMessage());
        }
    }

    private void showActionFeedback(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("F.R.I.D.A.Y. — " + title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.show();
    }
}
