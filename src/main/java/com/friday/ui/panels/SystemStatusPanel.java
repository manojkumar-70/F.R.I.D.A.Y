package com.friday.ui.panels;

import java.util.ArrayDeque;
import java.util.Deque;
import com.friday.system.SystemMetricsService;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.util.Duration;

/**
 * Bottom-left HUD System Status Panel with 4 circular metric gauges (CPU, GPU, RAM, Disk)
 * and an animated real-time activity sparkline wave.
 */
public final class SystemStatusPanel extends VBox {

    private final GaugeWidget cpuGauge;
    private final GaugeWidget gpuGauge;
    private final GaugeWidget ramGauge;
    private final GaugeWidget diskGauge;

    private final Canvas sparklineCanvas;
    private final Deque<Double> history = new ArrayDeque<>();
    private static final int MAX_HISTORY = 36;

    public SystemStatusPanel() {
        getStyleClass().addAll("glass-panel", "system-status-panel");
        setPadding(new Insets(10, 16, 10, 16));
        setSpacing(6);
        setMinWidth(260);
        setMaxWidth(300);

        Label header = new Label("System Status");
        header.getStyleClass().add("panel-section-title");

        cpuGauge = new GaugeWidget("CPU");
        gpuGauge = new GaugeWidget("GPU");
        ramGauge = new GaugeWidget("RAM");
        diskGauge = new GaugeWidget("Disk");

        HBox gaugesRow = new HBox(8, cpuGauge, gpuGauge, ramGauge, diskGauge);
        gaugesRow.setAlignment(Pos.CENTER);

        sparklineCanvas = new Canvas(250, 26);

        getChildren().addAll(header, gaugesRow, sparklineCanvas);

        for (int i = 0; i < MAX_HISTORY; i++) {
            history.add(20.0 + Math.random() * 10.0);
        }

        startMetricsPolling();
    }

    private void startMetricsPolling() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(1200), e -> {
            double cpu = SystemMetricsService.getCpuUsagePercentage();
            double gpu = SystemMetricsService.getGpuUsagePercentage();
            double ram = SystemMetricsService.getRamUsagePercentage();
            double disk = SystemMetricsService.getDiskUsagePercentage();

            cpuGauge.setValue(cpu);
            gpuGauge.setValue(gpu);
            ramGauge.setValue(ram);
            diskGauge.setValue(disk);

            if (history.size() >= MAX_HISTORY) {
                history.pollFirst();
            }
            history.addLast(cpu);

            renderSparkline();
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        // Initial render
        cpuGauge.setValue(SystemMetricsService.getCpuUsagePercentage());
        gpuGauge.setValue(SystemMetricsService.getGpuUsagePercentage());
        ramGauge.setValue(SystemMetricsService.getRamUsagePercentage());
        diskGauge.setValue(SystemMetricsService.getDiskUsagePercentage());
        renderSparkline();
    }

    private void renderSparkline() {
        GraphicsContext gc = sparklineCanvas.getGraphicsContext2D();
        double w = sparklineCanvas.getWidth();
        double h = sparklineCanvas.getHeight();

        gc.clearRect(0, 0, w, h);

        gc.setStroke(Color.rgb(0, 229, 255, 0.5));
        gc.setLineWidth(1.2);

        Double[] values = history.toArray(new Double[0]);
        if (values.length < 2) return;

        double stepX = w / (MAX_HISTORY - 1);

        gc.beginPath();
        for (int i = 0; i < values.length; i++) {
            double x = i * stepX;
            double norm = Math.min(100.0, Math.max(0.0, values[i])) / 100.0;
            double y = h - (norm * (h - 4)) - 2;

            if (i == 0) {
                gc.moveTo(x, y);
            } else {
                gc.lineTo(x, y);
            }
        }
        gc.stroke();

        // Base line
        gc.setStroke(Color.rgb(0, 229, 255, 0.15));
        gc.setLineWidth(0.8);
        gc.strokeLine(0, h - 1, w, h - 1);
    }

    private static final class GaugeWidget extends VBox {
        private final Canvas canvas;
        private final Label nameLabel;
        private final Label valLabel;

        GaugeWidget(String name) {
            setAlignment(Pos.CENTER);
            setSpacing(2);

            canvas = new Canvas(48, 48);
            nameLabel = new Label(name);
            nameLabel.getStyleClass().add("gauge-name-label");

            valLabel = new Label("0%");
            valLabel.getStyleClass().add("gauge-val-label");

            getChildren().addAll(canvas, nameLabel);
            renderGauge(0.0);
        }

        void setValue(double percentage) {
            valLabel.setText(String.format("%.0f%%", percentage));
            renderGauge(percentage);
        }

        private void renderGauge(double pct) {
            GraphicsContext gc = canvas.getGraphicsContext2D();
            double w = 48, h = 48;
            double cx = w / 2.0, cy = h / 2.0;
            double r = 18;

            gc.clearRect(0, 0, w, h);

            // Track arc
            gc.setStroke(Color.rgb(0, 229, 255, 0.18));
            gc.setLineWidth(3.0);
            gc.strokeArc(cx - r, cy - r, r * 2, r * 2, 0, 360, ArcType.OPEN);

            // Active fill arc
            double fillDeg = -(pct / 100.0) * 360.0;
            gc.setStroke(Color.rgb(0, 229, 255, 0.85));
            gc.setLineWidth(3.0);
            gc.strokeArc(cx - r, cy - r, r * 2, r * 2, 90, fillDeg, ArcType.OPEN);

            // Percentage Text in center
            gc.setFill(Color.rgb(240, 246, 252, 0.95));
            gc.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 9));
            String txt = String.format("%.0f%%", pct);
            double txtW = txt.length() * 5.0;
            gc.fillText(txt, cx - (txtW / 2.0) + 1, cy + 3);
        }
    }
}

