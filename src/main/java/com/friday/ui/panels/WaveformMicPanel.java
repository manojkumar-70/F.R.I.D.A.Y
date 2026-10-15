package com.friday.ui.panels;

import com.friday.assistant.AssistantState;
import com.friday.assistant.AssistantStateController;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Bottom-right HUD Audio Waveform visualizer with a central glowing microphone button.
 *
 * When an {@link AssistantStateController} is supplied, the waveform reads real
 * amplitude data from the controller in LISTENING state and reflects state changes
 * in the status label. The mic button transitions between LISTENING and IDLE.
 */
public final class WaveformMicPanel extends VBox {

    private final Canvas waveCanvasLeft;
    private final Canvas waveCanvasRight;
    private final Button micBtn;
    private final Label statusLabel;

    private double phase = 0;
    private AnimationTimer waveTimer;

    /** Optional controller — null means legacy standalone mode. */
    private AssistantStateController controller;

    // ---- Constructors ----

    /** Standalone mode (no controller): simulated waveform, local toggle only. */
    public WaveformMicPanel() {
        this(null);
    }

    /** Wired mode: waveform and button state driven by the controller. */
    public WaveformMicPanel(AssistantStateController controller) {
        this.controller = controller;

        getStyleClass().addAll("glass-panel", "waveform-mic-panel");
        setPadding(new Insets(10, 16, 10, 16));
        setSpacing(6);
        setAlignment(Pos.CENTER);
        setMinWidth(260);
        setMaxWidth(300);

        waveCanvasLeft  = new Canvas(85, 36);
        waveCanvasRight = new Canvas(85, 36);

        micBtn = new Button("🎤");
        micBtn.getStyleClass().add("hud-mic-glow-btn");
        Tooltip.install(micBtn, new Tooltip("Toggle Microphone / Listening Mode"));
        micBtn.setOnAction(e -> handleMicToggle());

        statusLabel = new Label("Standby");
        statusLabel.getStyleClass().add("mic-status-label");

        HBox centerRow = new HBox(8, waveCanvasLeft, micBtn, waveCanvasRight);
        centerRow.setAlignment(Pos.CENTER);

        getChildren().addAll(centerRow, statusLabel);

        // If controller is provided, observe state property for label/button sync
        if (controller != null) {
            syncToState(controller.getState());
            controller.stateProperty().addListener((obs, oldS, newS) -> syncToState(newS));
        } else {
            statusLabel.setText("Standby (Muted)");
        }

        startWaveAnimation();
    }

    // ---- Private ----

    private boolean isListeningLocally = false; // used only in standalone mode

    private void handleMicToggle() {
        if (controller != null) {
            // Toggle LISTENING ↔ IDLE via the controller
            AssistantState current = controller.getState();
            if (current == AssistantState.LISTENING) {
                controller.setState(AssistantState.IDLE);
            } else {
                controller.setState(AssistantState.LISTENING);
            }
        } else {
            // Standalone fallback
            isListeningLocally = !isListeningLocally;
            if (isListeningLocally) {
                statusLabel.setText("Listening...");
                micBtn.getStyleClass().remove("hud-mic-muted");
            } else {
                statusLabel.setText("Standby (Muted)");
                micBtn.getStyleClass().add("hud-mic-muted");
            }
        }
    }

    private void syncToState(AssistantState state) {
        if (state == null) return;
        switch (state) {
            case LISTENING -> {
                statusLabel.setText(controller.isMicAvailable() ? "Listening..." : "Listening... [no mic]");
                micBtn.getStyleClass().remove("hud-mic-muted");
            }
            case SPEAKING  -> {
                statusLabel.setText("Speaking...");
                micBtn.getStyleClass().add("hud-mic-muted");
            }
            case THINKING  -> {
                statusLabel.setText("Processing...");
                micBtn.getStyleClass().add("hud-mic-muted");
            }
            case ALERT     -> {
                statusLabel.setText("⚠ Alert");
                micBtn.getStyleClass().add("hud-mic-muted");
            }
            default        -> {
                statusLabel.setText("Standby");
                micBtn.getStyleClass().remove("hud-mic-muted");
            }
        }
    }

    private boolean effectivelyListening() {
        if (controller != null) {
            return controller.getState() == AssistantState.LISTENING;
        }
        return isListeningLocally;
    }

    private double effectiveAmplitude() {
        if (controller != null && controller.getState() == AssistantState.LISTENING) {
            return controller.getAmplitude();
        }
        return effectivelyListening() ? 0.4 : 0.0; // fallback simulated
    }

    private void startWaveAnimation() {
        waveTimer = new AnimationTimer() {
            private long lastNano = 0;

            @Override
            public void handle(long now) {
                if (lastNano == 0) { lastNano = now; return; }
                double elapsed = (now - lastNano) / 1_000_000_000.0;
                lastNano = now;

                double speedMult = effectivelyListening() ? 6.0 : 1.5;
                phase += elapsed * speedMult;
                renderWaveforms();
            }
        };
        waveTimer.start();
    }

    private void renderWaveforms() {
        double amp = effectiveAmplitude();
        renderWave(waveCanvasLeft.getGraphicsContext2D(),  waveCanvasLeft.getWidth(),  waveCanvasLeft.getHeight(),  true,  amp);
        renderWave(waveCanvasRight.getGraphicsContext2D(), waveCanvasRight.getWidth(), waveCanvasRight.getHeight(), false, amp);
    }

    private void renderWave(GraphicsContext gc, double w, double h, boolean invert, double amp) {
        gc.clearRect(0, 0, w, h);
        boolean active = effectivelyListening();
        gc.setStroke(Color.rgb(0, 229, 255, active ? 0.75 : 0.25));
        gc.setLineWidth(1.5);

        double midY  = h / 2.0;
        int points   = 24;
        double dx    = w / (points - 1);

        gc.beginPath();
        for (int i = 0; i < points; i++) {
            double x        = i * dx;
            double progress = (double) i / (points - 1);
            double envelope = Math.sin(progress * Math.PI);
            // Amplitude drives height: real amp [0..1] × max wave height 14 px
            double waveAmp  = (active ? 6.0 + amp * 14.0 : 2.0) * envelope;
            double mult     = invert ? -1.0 : 1.0;
            double y = midY + Math.sin(phase + i * 0.45 * mult) * waveAmp * Math.cos(phase * 0.5 + i * 0.2);
            if (i == 0) gc.moveTo(x, y); else gc.lineTo(x, y);
        }
        gc.stroke();
    }
}
