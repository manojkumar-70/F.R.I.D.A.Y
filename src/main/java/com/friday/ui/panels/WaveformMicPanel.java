package com.friday.ui.panels;

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
 * Bottom-right HUD Audio Waveform visualizer with central glowing microphone button.
 */
public final class WaveformMicPanel extends VBox {

    private final Canvas waveCanvasLeft;
    private final Canvas waveCanvasRight;
    private final Button micBtn;
    private final Label statusLabel;

    private boolean isListening = true;
    private double phase = 0;
    private AnimationTimer waveTimer;

    public WaveformMicPanel() {
        getStyleClass().addAll("glass-panel", "waveform-mic-panel");
        setPadding(new Insets(10, 16, 10, 16));
        setSpacing(6);
        setAlignment(Pos.CENTER);
        setMinWidth(260);
        setMaxWidth(300);

        waveCanvasLeft = new Canvas(85, 36);
        waveCanvasRight = new Canvas(85, 36);

        micBtn = new Button("🎤");
        micBtn.getStyleClass().add("hud-mic-glow-btn");
        Tooltip.install(micBtn, new Tooltip("Toggle Microphone Listening Mode"));
        micBtn.setOnAction(e -> toggleListening());

        statusLabel = new Label("Listening...");
        statusLabel.getStyleClass().add("mic-status-label");

        HBox centerRow = new HBox(8, waveCanvasLeft, micBtn, waveCanvasRight);
        centerRow.setAlignment(Pos.CENTER);

        getChildren().addAll(centerRow, statusLabel);

        startWaveAnimation();
    }

    private void toggleListening() {
        isListening = !isListening;
        if (isListening) {
            statusLabel.setText("Listening...");
            micBtn.getStyleClass().remove("hud-mic-muted");
        } else {
            statusLabel.setText("Standby (Muted)");
            micBtn.getStyleClass().add("hud-mic-muted");
        }
    }

    private void startWaveAnimation() {
        waveTimer = new AnimationTimer() {
            private long lastNano = 0;

            @Override
            public void handle(long now) {
                if (lastNano == 0) {
                    lastNano = now;
                    return;
                }
                double elapsed = (now - lastNano) / 1_000_000_000.0;
                lastNano = now;

                phase += elapsed * (isListening ? 6.0 : 1.5);
                renderWaveforms();
            }
        };
        waveTimer.start();
    }

    private void renderWaveforms() {
        renderWave(waveCanvasLeft.getGraphicsContext2D(), waveCanvasLeft.getWidth(), waveCanvasLeft.getHeight(), true);
        renderWave(waveCanvasRight.getGraphicsContext2D(), waveCanvasRight.getWidth(), waveCanvasRight.getHeight(), false);
    }

    private void renderWave(GraphicsContext gc, double w, double h, boolean invert) {
        gc.clearRect(0, 0, w, h);
        gc.setStroke(Color.rgb(0, 229, 255, isListening ? 0.75 : 0.25));
        gc.setLineWidth(1.5);

        double midY = h / 2.0;
        int points = 24;
        double dx = w / (points - 1);

        gc.beginPath();
        for (int i = 0; i < points; i++) {
            double x = i * dx;
            double progress = (double) i / (points - 1);
            double envelope = Math.sin(progress * Math.PI); // fade at ends
            double amp = (isListening ? 10.0 : 2.0) * envelope;
            double mult = invert ? -1.0 : 1.0;
            double y = midY + Math.sin(phase + i * 0.45 * mult) * amp * Math.cos(phase * 0.5 + i * 0.2);

            if (i == 0) {
                gc.moveTo(x, y);
            } else {
                gc.lineTo(x, y);
            }
        }
        gc.stroke();
    }
}

