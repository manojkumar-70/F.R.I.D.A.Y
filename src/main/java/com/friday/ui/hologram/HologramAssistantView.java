package com.friday.ui.hologram;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * Central animated holographic assistant visualization.
 * Combines particle systems, rotating holographic reticle rings, floating cybernetic hand orb,
 * and holographic character rendering with procedural glow effects.
 */
public final class HologramAssistantView extends StackPane {

    private final Canvas canvas;
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private Image avatarImage = null;

    private double angle = 0;
    private double pulsePhase = 0;
    private AnimationTimer timer;

    private static final class Particle {
        double x, y;
        double vx, vy;
        double size;
        double alpha;
        double maxAlpha;

        Particle(double x, double y, double vx, double vy, double size, double maxAlpha) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.maxAlpha = maxAlpha;
            this.alpha = maxAlpha * 0.5;
        }
    }

    public HologramAssistantView() {
        setAlignment(Pos.CENTER);
        getStyleClass().add("hologram-assistant-view");

        // Try to load custom avatar image if provided in classpath
        try (InputStream is = getClass().getResourceAsStream("/com/friday/assistant_avatar.png")) {
            if (is != null) {
                avatarImage = new Image(is);
            }
        } catch (Exception ignored) {}

        canvas = new Canvas(480, 520);
        getChildren().add(canvas);

        // Bind canvas resize
        widthProperty().addListener((obs, oldV, newV) -> resizeCanvas());
        heightProperty().addListener((obs, oldV, newV) -> resizeCanvas());

        initParticles(45);
        startAnimation();
    }

    private void resizeCanvas() {
        double w = Math.max(320, Math.min(600, getWidth() > 0 ? getWidth() * 0.95 : 480));
        double h = Math.max(400, Math.min(680, getHeight() > 0 ? getHeight() * 0.95 : 520));
        canvas.setWidth(w);
        canvas.setHeight(h);
    }

    private void initParticles(int count) {
        particles.clear();
        for (int i = 0; i < count; i++) {
            particles.add(new Particle(
                    random.nextDouble() * 480,
                    random.nextDouble() * 520,
                    (random.nextDouble() - 0.5) * 0.4,
                    -0.2 - random.nextDouble() * 0.5,
                    1.5 + random.nextDouble() * 2.5,
                    0.2 + random.nextDouble() * 0.6
            ));
        }
    }

    private void startAnimation() {
        timer = new AnimationTimer() {
            private long lastNano = 0;

            @Override
            public void handle(long now) {
                if (lastNano == 0) {
                    lastNano = now;
                    return;
                }
                double elapsed = (now - lastNano) / 1_000_000_000.0;
                lastNano = now;

                angle += elapsed * 22.0; // rotation speed in deg/s
                pulsePhase += elapsed * 2.2;

                render();
            }
        };
        timer.start();
    }

    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0;

        gc.clearRect(0, 0, w, h);

        // 1. Background Holographic Radial Aura
        double auraRadius = Math.min(w, h) * 0.48;
        RadialGradient bgAura = new RadialGradient(
                0, 0, cx, cy, auraRadius, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(0, 229, 255, 0.18 + 0.05 * Math.sin(pulsePhase))),
                new Stop(0.5, Color.rgb(0, 119, 182, 0.09)),
                new Stop(1.0, Color.rgb(10, 17, 36, 0.0))
        );
        gc.setFill(bgAura);
        gc.fillOval(cx - auraRadius, cy - auraRadius, auraRadius * 2, auraRadius * 2);

        // 2. Rotating Holographic HUD Concentric Tech Rings
        renderHologramRings(gc, cx, cy);

        // 3. Floating Hologram Energy Particles
        renderParticles(gc, w, h);

        // 4. Assistant Figure (Image or Procedural Cybernetic Silhouette)
        if (avatarImage != null && !avatarImage.isError()) {
            double imgW = Math.min(w * 0.85, 380);
            double imgH = imgW * (avatarImage.getHeight() / avatarImage.getWidth());
            double imgX = cx - imgW / 2.0;
            double imgY = cy - imgH / 2.0 + Math.sin(pulsePhase) * 6.0;

            gc.save();
            gc.setGlobalAlpha(0.92);
            gc.drawImage(avatarImage, imgX, imgY, imgW, imgH);
            gc.restore();
        } else {
            renderProceduralAssistant(gc, cx, cy, w, h);
        }

        // 5. Floating Glowing Hand Orb / Reactor Core
        renderHandOrb(gc, cx, cy);
    }

    private void renderHologramRings(GraphicsContext gc, double cx, double cy) {
        gc.save();
        double r1 = Math.min(cx, cy) * 0.82;
        double r2 = Math.min(cx, cy) * 0.65;
        double r3 = Math.min(cx, cy) * 0.45;

        // Outer Dotted Ring
        gc.setStroke(Color.rgb(0, 229, 255, 0.25));
        gc.setLineWidth(1.0);
        gc.setLineDashes(6.0, 10.0);
        gc.strokeOval(cx - r1, cy - r1, r1 * 2, r1 * 2);

        // Mid Ring with Segmented Arcs
        gc.setStroke(Color.rgb(72, 202, 228, 0.35));
        gc.setLineWidth(1.5);
        gc.setLineDashes(40.0, 20.0, 15.0, 20.0);
        gc.strokeArc(cx - r2, cy - r2, r2 * 2, r2 * 2, angle, 270, javafx.scene.shape.ArcType.OPEN);

        // Inner Rotating Counter-Ring
        gc.setStroke(Color.rgb(0, 229, 255, 0.45));
        gc.setLineWidth(1.2);
        gc.setLineDashes(18.0, 14.0);
        gc.strokeArc(cx - r3, cy - r3, r3 * 2, r3 * 2, -angle * 1.5, 320, javafx.scene.shape.ArcType.OPEN);

        // Center reticle ticks
        gc.setStroke(Color.rgb(0, 229, 255, 0.2));
        gc.setLineWidth(1.0);
        gc.setLineDashes(null);
        double tick = 14;
        gc.strokeLine(cx - r1 - tick, cy, cx - r1 + tick, cy);
        gc.strokeLine(cx + r1 - tick, cy, cx + r1 + tick, cy);
        gc.strokeLine(cx, cy - r1 - tick, cx, cy - r1 + tick);
        gc.strokeLine(cx, cy + r1 - tick, cx, cy + r1 + tick);

        gc.restore();
    }

    private void renderParticles(GraphicsContext gc, double w, double h) {
        for (Particle p : particles) {
            p.x += p.vx;
            p.y += p.vy;

            if (p.y < 0) {
                p.y = h;
                p.x = random.nextDouble() * w;
            }
            if (p.x < 0) p.x = w;
            if (p.x > w) p.x = 0;

            p.alpha = p.maxAlpha * (0.6 + 0.4 * Math.sin(pulsePhase + p.x));

            gc.setFill(Color.rgb(0, 229, 255, Math.max(0.0, Math.min(1.0, p.alpha))));
            gc.fillOval(p.x - p.size / 2, p.y - p.size / 2, p.size, p.size);
        }
    }

    private void renderProceduralAssistant(GraphicsContext gc, double cx, double cy, double w, double h) {
        double bobbing = Math.sin(pulsePhase) * 5.0;
        double headY = cy - 80 + bobbing;

        gc.save();

        // 1. Hologram Torso / Silhouette Glow
        RadialGradient torsoGlow = new RadialGradient(
                0, 0, cx, cy + 40 + bobbing, 140, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(0, 229, 255, 0.28)),
                new Stop(0.6, Color.rgb(0, 119, 182, 0.12)),
                new Stop(1.0, Color.rgb(0, 0, 0, 0.0))
        );
        gc.setFill(torsoGlow);
        gc.fillOval(cx - 90, cy - 20 + bobbing, 180, 240);

        // 2. Holographic Cybernetical Lines & Silhouette
        gc.setStroke(Color.rgb(0, 229, 255, 0.65));
        gc.setLineWidth(1.5);

        // Head oval
        gc.strokeOval(cx - 32, headY - 45, 64, 80);

        // Hair aura
        gc.setStroke(Color.rgb(72, 202, 228, 0.4));
        gc.strokeArc(cx - 44, headY - 55, 88, 100, 30, 120, javafx.scene.shape.ArcType.OPEN);

        // Neck & Shoulders
        gc.setStroke(Color.rgb(0, 229, 255, 0.55));
        gc.beginPath();
        gc.moveTo(cx - 14, headY + 32);
        gc.lineTo(cx - 18, headY + 58);
        gc.lineTo(cx - 75, headY + 95);
        gc.lineTo(cx - 65, headY + 180);
        gc.stroke();

        gc.beginPath();
        gc.moveTo(cx + 14, headY + 32);
        gc.lineTo(cx + 18, headY + 58);
        gc.lineTo(cx + 75, headY + 95);
        gc.lineTo(cx + 65, headY + 180);
        gc.stroke();

        // Chest Arc Reactor Core
        double coreY = headY + 95;
        RadialGradient coreGlow = new RadialGradient(
                0, 0, cx, coreY, 24, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(255, 255, 255, 0.95)),
                new Stop(0.4, Color.rgb(0, 229, 255, 0.8)),
                new Stop(1.0, Color.rgb(0, 119, 182, 0.0))
        );
        gc.setFill(coreGlow);
        gc.fillOval(cx - 18, coreY - 18, 36, 36);

        gc.setStroke(Color.rgb(0, 229, 255, 0.9));
        gc.setLineWidth(1.5);
        gc.strokeOval(cx - 12, coreY - 12, 24, 24);

        // Cybernetic Circuit Lines on Chest & Arms
        gc.setStroke(Color.rgb(72, 202, 228, 0.35));
        gc.setLineWidth(1.0);
        gc.strokeLine(cx - 10, coreY + 14, cx - 25, coreY + 50);
        gc.strokeLine(cx + 10, coreY + 14, cx + 25, coreY + 50);
        gc.strokeLine(cx - 30, coreY - 10, cx - 55, coreY + 25);
        gc.strokeLine(cx + 30, coreY - 10, cx + 55, coreY + 25);

        gc.restore();
    }

    private void renderHandOrb(GraphicsContext gc, double cx, double cy) {
        // Floating Hologram Orb held in hand (Left-Center)
        double orbX = cx - 110;
        double orbY = cy + 45 + Math.sin(pulsePhase * 1.4) * 6.0;

        gc.save();

        // Orb Outer Glow
        RadialGradient orbGlow = new RadialGradient(
                0, 0, orbX, orbY, 38, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(255, 255, 255, 0.9)),
                new Stop(0.3, Color.rgb(0, 229, 255, 0.75)),
                new Stop(0.7, Color.rgb(0, 119, 182, 0.3)),
                new Stop(1.0, Color.rgb(0, 0, 0, 0.0))
        );
        gc.setFill(orbGlow);
        gc.fillOval(orbX - 38, orbY - 38, 76, 76);

        // Concentric Spherical Rings
        gc.setStroke(Color.rgb(0, 229, 255, 0.85));
        gc.setLineWidth(1.2);
        gc.strokeOval(orbX - 16, orbY - 16, 32, 32);

        gc.setStroke(Color.rgb(72, 202, 228, 0.6));
        gc.strokeArc(orbX - 24, orbY - 24, 48, 48, angle * 2.0, 240, javafx.scene.shape.ArcType.OPEN);

        gc.setStroke(Color.rgb(255, 255, 255, 0.9));
        gc.fillOval(orbX - 4, orbY - 4, 8, 8);

        // Upward Beam Taper
        gc.setStroke(Color.rgb(0, 229, 255, 0.25));
        gc.setLineWidth(1.0);
        gc.strokeLine(orbX, orbY - 20, orbX, orbY - 60);

        gc.restore();
    }
}

