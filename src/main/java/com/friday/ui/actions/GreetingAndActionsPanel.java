package com.friday.ui.actions;

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
 * Left-side Greeting Card & Quick Actions HUD Panel.
 */
public final class GreetingAndActionsPanel extends VBox {

    public GreetingAndActionsPanel() {
        getStyleClass().add("greeting-actions-container");
        setSpacing(14);
        setMinWidth(220);
        setMaxWidth(260);

        // 1. Greeting Card
        VBox greetingCard = createGreetingCard();

        // 2. Quick Actions Glass Card
        VBox actionsCard = createQuickActionsCard();

        getChildren().addAll(greetingCard, actionsCard);
    }

    private VBox createGreetingCard() {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("glass-panel", "greeting-card");
        card.setPadding(new Insets(14, 16, 14, 16));

        String greetingText = getDynamicGreeting();
        Label greetingLabel = new Label(greetingText + ", Manoj.");
        greetingLabel.getStyleClass().add("greeting-title");

        Label subPrompt = new Label("How can I help you today?");
        subPrompt.getStyleClass().add("greeting-subtitle");

        Button micPromptBtn = new Button("🎤");
        micPromptBtn.getStyleClass().add("greeting-mic-btn");
        Tooltip.install(micPromptBtn, new Tooltip("Voice command standby"));
        micPromptBtn.setOnAction(e -> showActionFeedback("Voice Command", "Voice AI integration is on standby."));

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
                createActionButton("◬  Open VS Code", () -> launchVsCode()),
                createActionButton("🔍  Search the web", () -> openWebBrowser("https://google.com")),
                createActionButton("📄  Summarize this", () -> showActionFeedback("Summarize", "Ready to summarize active buffer.")),
                createActionButton("💡  Explain code", () -> showActionFeedback("Explain Code", "AI Code Explanation module ready.")),
                createActionButton("📝  Create a plan", () -> showActionFeedback("Create Plan", "Planning assistant module ready.")),
                createActionButton("📸  Take a screenshot", () -> captureScreenshot())
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
            try {
                action.run();
            } catch (Exception ex) {
                showActionFeedback("Action Error", ex.getMessage());
            }
        });
        return btn;
    }

    private String getDynamicGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour >= 5 && hour < 12) return "Good Morning";
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
            BufferedImage capture = robot.createScreenCapture(screenRect);
            File out = new File(System.getProperty("user.home"), "FRIDAY_Screenshot_" + System.currentTimeMillis() + ".png");
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

