package com.friday.ui.panels;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.friday.system.MetricReading;
import com.friday.system.SystemMetricsService;
import com.friday.system.SystemTelemetrySnapshot;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;

/**
 * Bottom-left HUD System Status Panel with 4 circular metric gauges (CPU, GPU, RAM, Disk)
 * and an animated real-time activity sparkline wave.
 *
 * Polling is performed off the JavaFX Application Thread using a bounded daemon scheduler.
 * Implements {@link #shutdown()} to prevent resource leaks when the window is closed.
 */
public final class SystemStatusPanel extends VBox {

    private final SystemMetricsService metricsService;
    private final GaugeWidget cpuGauge;
    private final GaugeWidget gpuGauge;
    private final GaugeWidget ramGauge;
    private final GaugeWidget diskGauge;

    private final Canvas sparklineCanvas;
    private final Deque<Double> history = new ArrayDeque<>();
    private static final int MAX_HISTORY = 36;
    private static final long POLL_INTERVAL_MS = 1200L;

    private ScheduledExecutorService pollScheduler;
    private volatile boolean isShutdown = false;
    private volatile String lastLoggedErrorMessage = null;
    private volatile long lastLoggedErrorTimeMs = 0L;
    private static final long ERROR_LOG_SUPPRESS_INTERVAL_MS = 60_000L; // Throttle duplicate error logs to at most once per minute

    public SystemStatusPanel() {
        this(new SystemMetricsService());
    }

    public SystemStatusPanel(SystemMetricsService metricsService) {
        this.metricsService = (metricsService != null) ? metricsService : new SystemMetricsService();

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

        // Initial empty baseline render
        renderSparkline();

        startMetricsPolling();
    }

    /**
     * Shuts down background polling. Safe and idempotent.
     */
    public synchronized void shutdown() {
        if (isShutdown) return;
        isShutdown = true;

        if (pollScheduler != null && !pollScheduler.isShutdown()) {
            pollScheduler.shutdownNow();
            pollScheduler = null;
        }
    }

    public boolean isShutdown() {
        return isShutdown;
    }

    // ── Background Polling ───────────────────────────────────────────────────

    private synchronized void startMetricsPolling() {
        if (isShutdown || pollScheduler != null) return;

        pollScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "friday-telemetry-poll");
            t.setDaemon(true);
            return t;
        });

        pollScheduler.scheduleAtFixedRate(this::pollTelemetryTask, 0L, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void pollTelemetryTask() {
        if (isShutdown) return;
        try {
            // Collect metrics off the JavaFX thread
            SystemTelemetrySnapshot snapshot = metricsService.querySnapshot();

            // Reset error state on successful poll
            lastLoggedErrorMessage = null;

            if (isShutdown) return;
            Platform.runLater(() -> {
                if (!isShutdown) {
                    applySnapshot(snapshot);
                }
            });
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            String errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (!errorMsg.equals(lastLoggedErrorMessage) || (now - lastLoggedErrorTimeMs) > ERROR_LOG_SUPPRESS_INTERVAL_MS) {
                lastLoggedErrorMessage = errorMsg;
                lastLoggedErrorTimeMs = now;
                System.err.println("[SystemStatusPanel] Telemetry polling failed: " + errorMsg);
            }
        }
    }

    private void applySnapshot(SystemTelemetrySnapshot snapshot) {
        cpuGauge.setReading(snapshot.cpu());
        gpuGauge.setReading(snapshot.gpu());
        ramGauge.setReading(snapshot.ram());
        diskGauge.setReading(snapshot.disk());

        // Append to sparkline history only if CPU reading is genuine
        if (snapshot.cpu().isAvailable()) {
            if (history.size() >= MAX_HISTORY) {
                history.pollFirst();
            }
            history.addLast(snapshot.cpu().percentage());
        }

        renderSparkline();
    }

    // ── Sparkline Rendering ──────────────────────────────────────────────────

    private void renderSparkline() {
        GraphicsContext gc = sparklineCanvas.getGraphicsContext2D();
        double w = sparklineCanvas.getWidth();
        double h = sparklineCanvas.getHeight();

        gc.clearRect(0, 0, w, h);

        // Subtle baseline
        gc.setStroke(Color.rgb(0, 229, 255, 0.15));
        gc.setLineWidth(0.8);
        gc.strokeLine(0, h - 1, w, h - 1);

        Double[] values = history.toArray(new Double[0]);
        if (values.length < 2) {
            // If only 1 sample, render a subtle point at that level
            if (values.length == 1) {
                double norm = Math.min(100.0, Math.max(0.0, values[0])) / 100.0;
                double y = h - (norm * (h - 4)) - 2;
                gc.setFill(Color.rgb(0, 229, 255, 0.6));
                gc.fillOval(w - 4, y - 2, 4, 4);
            }
            return;
        }

        gc.setStroke(Color.rgb(0, 229, 255, 0.5));
        gc.setLineWidth(1.2);

        double stepX = w / (MAX_HISTORY - 1);

        // Align sparkline to right side as history accumulates
        int offsetIndex = MAX_HISTORY - values.length;
        double startX = Math.max(0.0, offsetIndex * stepX);

        gc.beginPath();
        for (int i = 0; i < values.length; i++) {
            double x = startX + i * stepX;
            double norm = Math.min(100.0, Math.max(0.0, values[i])) / 100.0;
            double y = h - (norm * (h - 4)) - 2;

            if (i == 0) {
                gc.moveTo(x, y);
            } else {
                gc.lineTo(x, y);
            }
        }
        gc.stroke();
    }

    // ── Circular Gauge Widget ────────────────────────────────────────────────

    static final class GaugeWidget extends VBox {
        private final Canvas canvas;
        private final Label nameLabel;
        private final Label valLabel;

        GaugeWidget(String name) {
            setAlignment(Pos.CENTER);
            setSpacing(2);

            canvas = new Canvas(48, 48);
            nameLabel = new Label(name);
            nameLabel.getStyleClass().add("gauge-name-label");

            valLabel = new Label("--");
            valLabel.getStyleClass().add("gauge-val-label");

            getChildren().addAll(canvas, nameLabel);
            renderGauge(MetricReading.UNAVAILABLE);
        }

        void setReading(MetricReading reading) {
            MetricReading safeReading = (reading != null) ? reading : MetricReading.UNAVAILABLE;
            valLabel.setText(safeReading.formattedPercentage());
            renderGauge(safeReading);
        }

        void setValue(double percentage) {
            if (percentage < 0.0 || Double.isNaN(percentage) || Double.isInfinite(percentage)) {
                setReading(MetricReading.UNAVAILABLE);
            } else {
                setReading(MetricReading.of(percentage));
            }
        }

        private void renderGauge(MetricReading reading) {
            GraphicsContext gc = canvas.getGraphicsContext2D();
            double w = 48, h = 48;
            double cx = w / 2.0, cy = h / 2.0;
            double r = 18;

            gc.clearRect(0, 0, w, h);

            boolean isAvail = reading.isAvailable();

            // Track arc
            gc.setStroke(Color.rgb(0, 229, 255, isAvail ? 0.18 : 0.10));
            gc.setLineWidth(3.0);
            gc.strokeArc(cx - r, cy - r, r * 2, r * 2, 0, 360, ArcType.OPEN);

            // Active fill arc (only if reading is genuine and available)
            if (isAvail) {
                double pct = reading.percentage();
                double fillDeg = -(pct / 100.0) * 360.0;
                gc.setStroke(Color.rgb(0, 229, 255, 0.85));
                gc.setLineWidth(3.0);
                gc.strokeArc(cx - r, cy - r, r * 2, r * 2, 90, fillDeg, ArcType.OPEN);
            }

            // Percentage Text in center
            String txt = reading.formattedPercentage();
            gc.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 9));
            if (isAvail) {
                gc.setFill(Color.rgb(240, 246, 252, 0.95));
            } else {
                gc.setFill(Color.rgb(111, 168, 192, 0.70));
            }

            double txtW = txt.length() * 5.2;
            gc.fillText(txt, cx - (txtW / 2.0) + 1, cy + 3);
        }
    }
}
