package com.friday.ui.header;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.util.Duration;

/**
 * Top HUD Header area containing Arc Reactor branding, live Clock/Date, and marked Weather placeholder.
 */
public final class HeaderArea extends HBox {

    private final Label timeLabel;
    private final Label dateLabel;
    private double reactorAngle = 0;

    public HeaderArea() {
        getStyleClass().add("hud-header-area");
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(10, 24, 8, 20));
        setSpacing(16);

        // 1. Left Arc Reactor Branding Group
        HBox brandGroup = createBrandGroup();

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 2. Right Date, Time, Weather, and Radar Group
        timeLabel = new Label();
        timeLabel.getStyleClass().add("hud-clock-time");

        dateLabel = new Label();
        dateLabel.getStyleClass().add("hud-clock-date");

        HBox rightInfoGroup = createRightInfoGroup();

        getChildren().addAll(brandGroup, spacer, rightInfoGroup);

        updateClock();
        startClockTimeline();
    }

    private HBox createBrandGroup() {
        Canvas reactorCanvas = new Canvas(54, 54);
        renderReactorLogo(reactorCanvas.getGraphicsContext2D(), 0);

        // Continuous subtle reactor rotation
        Timeline reactorTimeline = new Timeline(new KeyFrame(Duration.millis(40), e -> {
            reactorAngle += 1.5;
            renderReactorLogo(reactorCanvas.getGraphicsContext2D(), reactorAngle);
        }));
        reactorTimeline.setCycleCount(Animation.INDEFINITE);
        reactorTimeline.play();

        VBox textGroup = new VBox(2);
        textGroup.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("F . R . I . D . A . Y .");
        title.getStyleClass().add("hud-brand-title");

        Label subtitle1 = new Label("Your AI Assistant");
        subtitle1.getStyleClass().add("hud-brand-sub1");

        Label subtitle2 = new Label("Always here. Always with you.");
        subtitle2.getStyleClass().add("hud-brand-sub2");

        textGroup.getChildren().addAll(title, subtitle1, subtitle2);

        HBox brandBox = new HBox(12, reactorCanvas, textGroup);
        brandBox.setAlignment(Pos.CENTER_LEFT);
        return brandBox;
    }

    private void renderReactorLogo(GraphicsContext gc, double rot) {
        double w = 54;
        double h = 54;
        double cx = w / 2.0;
        double cy = h / 2.0;

        gc.clearRect(0, 0, w, h);

        // Outer Glow
        RadialGradient glow = new RadialGradient(
                0, 0, cx, cy, 26, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(0, 229, 255, 0.4)),
                new Stop(0.8, Color.rgb(0, 119, 182, 0.1)),
                new Stop(1.0, Color.TRANSPARENT)
        );
        gc.setFill(glow);
        gc.fillOval(cx - 26, cy - 26, 52, 52);

        // Outer Ring
        gc.setStroke(Color.rgb(0, 229, 255, 0.8));
        gc.setLineWidth(1.5);
        gc.strokeOval(cx - 22, cy - 22, 44, 44);

        // Segmented Tech Ring
        gc.setStroke(Color.rgb(72, 202, 228, 0.6));
        gc.setLineWidth(2.0);
        gc.setLineDashes(10.0, 6.0);
        gc.strokeArc(cx - 17, cy - 17, 34, 34, rot, 300, javafx.scene.shape.ArcType.OPEN);

        // Inner Triangle (Arc Reactor Core)
        gc.setStroke(Color.rgb(255, 255, 255, 0.95));
        gc.setLineWidth(1.8);
        gc.setLineDashes(null);

        double triR = 10.0;
        double a1 = Math.toRadians(rot - 90);
        double a2 = Math.toRadians(rot + 30);
        double a3 = Math.toRadians(rot + 150);

        gc.beginPath();
        gc.moveTo(cx + triR * Math.cos(a1), cy + triR * Math.sin(a1));
        gc.lineTo(cx + triR * Math.cos(a2), cy + triR * Math.sin(a2));
        gc.lineTo(cx + triR * Math.cos(a3), cy + triR * Math.sin(a3));
        gc.closePath();
        gc.stroke();

        // Inner Center Core
        gc.setFill(Color.rgb(0, 229, 255, 0.9));
        gc.fillOval(cx - 3, cy - 3, 6, 6);
    }

    private HBox createRightInfoGroup() {
        // Clock Box
        VBox clockBox = new VBox(2, timeLabel, dateLabel);
        clockBox.setAlignment(Pos.CENTER_RIGHT);

        // Weather Placeholder (Clearly marked as unavailable until connected)
        VBox weatherBox = new VBox(2);
        weatherBox.setAlignment(Pos.CENTER_RIGHT);
        weatherBox.getStyleClass().add("weather-badge-box");

        Label locationLabel = new Label("Bengaluru  •  24°C");
        locationLabel.getStyleClass().add("weather-city");

        Label conditionLabel = new Label("☁ Partly Cloudy");
        conditionLabel.getStyleClass().add("weather-condition");

        Label statusNotice = new Label("[Weather: Offline / Mock]");
        statusNotice.getStyleClass().add("weather-notice");

        weatherBox.getChildren().addAll(locationLabel, conditionLabel, statusNotice);

        // Mini Radar HUD Widget
        Canvas radarCanvas = new Canvas(48, 48);
        renderMiniRadar(radarCanvas.getGraphicsContext2D());

        HBox container = new HBox(16, clockBox, weatherBox, radarCanvas);
        container.setAlignment(Pos.CENTER_RIGHT);
        return container;
    }

    private void renderMiniRadar(GraphicsContext gc) {
        double cx = 24, cy = 24;
        gc.setStroke(Color.rgb(0, 229, 255, 0.4));
        gc.setLineWidth(1.0);
        gc.strokeOval(4, 4, 40, 40);
        gc.strokeOval(12, 12, 24, 24);
        gc.strokeLine(4, cy, 44, cy);
        gc.strokeLine(cx, 4, cx, 44);

        // Blip
        gc.setFill(Color.rgb(0, 229, 255, 0.8));
        gc.fillOval(cx + 8, cy - 6, 4, 4);
    }

    private void updateClock() {
        LocalDateTime now = LocalDateTime.now();
        timeLabel.setText(now.format(DateTimeFormatter.ofPattern("hh:mm:ss a")));
        dateLabel.setText(now.format(DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy")));
    }

    private void startClockTimeline() {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClock()));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }
}

