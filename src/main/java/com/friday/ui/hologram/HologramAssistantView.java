package com.friday.ui.hologram;

import com.friday.assistant.AssistantState;
import com.friday.assistant.AssistantStateController;
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
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Central animated holographic assistant visualization.
 *
 * <p>Renders a distinct animation per {@link AssistantState}:
 * <ul>
 *   <li><b>IDLE</b>      — slow rotating concentric rings, gentle glow, drifting cyan particles.
 *   <li><b>LISTENING</b> — bilateral waveform radiating from centre, ring expands with <em>real</em>
 *                           microphone amplitude; banner shown when mic unavailable.
 *   <li><b>THINKING</b>  — fast counter-rotating arcs, tick-mark sweep, data-ring scan.
 *   <li><b>SPEAKING</b>  — waveform synchronized to <em>actual</em> speech playback amplitude,
 *                           core brightness pulses; banner shown when TTS unavailable.
 *   <li><b>ALERT</b>     — restrained red/amber ring accents, blinking warning triangles.
 * </ul>
 *
 * <p>Audio capability is read from the controller; the view never simulates
 * audio-driven motion when the service is unavailable.
 *
 * <p>Canvas resizes with the parent layout.  The {@link AnimationTimer} is
 * stopped in {@link #shutdown()} — call that when removing this view.
 */
public final class HologramAssistantView extends StackPane {

    // ── Constants ──────────────────────────────────────────────────────────────

    /** Cyan palette colours (pre-allocated to avoid per-frame allocation). */
    private static final Color CYAN_FULL    = Color.rgb(0,   229, 255, 1.0);
    private static final Color CYAN_BRIGHT  = Color.rgb(0,   255, 200, 1.0);
    private static final Color CYAN_MID     = Color.rgb(72,  202, 228, 1.0);
    private static final Color BLUE_SCAN    = Color.rgb(30,  140, 255, 1.0);
    private static final Color RED_ALERT    = Color.rgb(230, 57,  70,  1.0);
    private static final Color AMBER_WARN   = Color.rgb(255, 209, 102, 1.0);

    private static final int   PARTICLE_COUNT = 55;
    private static final Font  MONO_BOLD_13   = Font.font("Monospaced", FontWeight.BOLD, 13);
    private static final Font  SYSTEM_11      = Font.font("System", 11);
    private static final Font  SYSTEM_12      = Font.font("System", 12);

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Canvas canvas;
    private final List<Particle> particles = new ArrayList<>(PARTICLE_COUNT);
    private final Random random = new Random();
    private Image avatarImage = null;

    /** Rotation accumulator (degrees). */
    private double angle      = 0;
    /** Global pulse phase (radians). */
    private double pulsePhase = 0;
    /** Alert-specific blink phase (radians). */
    private double alertPhase = 0;
    /** THINKING scan arc phase (degrees). */
    private double scanAngle  = 0;
    /** Smoothed amplitude used for wave rendering — avoids jarring jumps. */
    private double smoothAmp  = 0;

    private AnimationTimer timer;
    private final AssistantStateController controller;

    // ── Particle ──────────────────────────────────────────────────────────────

    private static final class Particle {
        double x, y, vx, vy, size, alpha, maxAlpha;

        Particle(double x, double y, double vx, double vy, double size, double maxAlpha) {
            this.x = x; this.y = y;
            this.vx = vx; this.vy = vy;
            this.size = size;
            this.maxAlpha = maxAlpha;
            this.alpha = maxAlpha * 0.5;
        }
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public HologramAssistantView(AssistantStateController controller) {
        this.controller = controller;
        setAlignment(Pos.CENTER);
        getStyleClass().add("hologram-assistant-view");

        // Optional custom avatar image
        try (InputStream is = getClass().getResourceAsStream("/com/friday/assistant_avatar.png")) {
            if (is != null) avatarImage = new Image(is);
        } catch (Exception ignored) {}

        canvas = new Canvas(440, 440) {
            @Override
            public double minWidth(double height) {
                return 100;
            }

            @Override
            public double minHeight(double width) {
                return 100;
            }

            @Override
            public double prefWidth(double height) {
                return 440;
            }

            @Override
            public double prefHeight(double width) {
                return 440;
            }
        };
        getChildren().add(canvas);

        widthProperty() .addListener((obs, o, n) -> resizeCanvas());
        heightProperty().addListener((obs, o, n) -> resizeCanvas());

        initParticles();
        startAnimation();
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        resizeCanvas();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Stop the animation timer and release resources. */
    public void shutdown() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void resizeCanvas() {
        double w = getWidth();
        double h = getHeight();
        if (w > 0 && h > 0) {
            // Keep hologram circular and responsive to both width and height constraints
            double available = Math.min(w * 0.95, h * 0.95);
            double size = Math.max(160.0, Math.min(620.0, available));
            if (canvas.getWidth() != size || canvas.getHeight() != size) {
                canvas.setWidth(size);
                canvas.setHeight(size);
                initParticles();
            }
        }
    }

    private void initParticles() {
        particles.clear();
        double w = Math.max(200.0, canvas.getWidth());
        double h = Math.max(200.0, canvas.getHeight());
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles.add(new Particle(
                    random.nextDouble() * w,
                    random.nextDouble() * h,
                    (random.nextDouble() - 0.5) * 0.38,
                    -0.18 - random.nextDouble() * 0.45,
                    1.0 + random.nextDouble() * 2.2,
                    0.12 + random.nextDouble() * 0.52
            ));
        }
    }

    private void startAnimation() {
        timer = new AnimationTimer() {
            private long lastNano = 0;

            @Override
            public void handle(long now) {
                if (lastNano == 0) { lastNano = now; return; }
                double dt = Math.min((now - lastNano) / 1_000_000_000.0, 0.05); // cap at 50 ms
                lastNano = now;

                AssistantState state = controller.getState();

                // Speed of ring rotation depends on state
                double rotSpeed = switch (state) {
                    case IDLE      -> 20.0;
                    case LISTENING -> 26.0;
                    case THINKING  -> 82.0;
                    case SPEAKING  -> 34.0;
                    case ALERT     -> 10.0;
                };

                angle      += dt * rotSpeed;
                pulsePhase += dt * 2.0;
                alertPhase += dt * 4.2;
                scanAngle  += dt * 108.0;

                // Low-pass smooth the amplitude to avoid flicker
                double rawAmp = controller.getAmplitude();
                smoothAmp += (rawAmp - smoothAmp) * 0.25;

                render(state);
            }
        };
        timer.start();
    }

    // =========================================================================
    //  Main render dispatcher
    // =========================================================================

    private void render(AssistantState state) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w  = canvas.getWidth();
        double h  = canvas.getHeight();
        double cx = w / 2.0;
        double cy = h / 2.0;

        gc.clearRect(0, 0, w, h);

        renderBackgroundAura(gc, cx, cy, state);

        switch (state) {
            case IDLE      -> renderIdleRings     (gc, cx, cy);
            case LISTENING -> renderListeningRings(gc, cx, cy);
            case THINKING  -> renderThinkingRings (gc, cx, cy);
            case SPEAKING  -> renderSpeakingRings (gc, cx, cy);
            case ALERT     -> renderAlertRings    (gc, cx, cy);
        }

        renderParticles(gc, w, h, state);

        if (avatarImage != null && !avatarImage.isError()) {
            renderAvatarImage(gc, cx, cy, w);
        } else {
            renderProceduralAssistant(gc, cx, cy, state);
        }

        renderHandOrb(gc, cx, cy, state);
        renderStateLabel(gc, cx, h, state);
    }

    // =========================================================================
    //  Background aura
    // =========================================================================

    private void renderBackgroundAura(GraphicsContext gc, double cx, double cy, AssistantState state) {
        double r     = Math.min(cx, cy) * 0.96;
        double pulse = 0.045 * Math.sin(pulsePhase);

        Color inner, mid;
        switch (state) {
            case ALERT -> {
                inner = Color.rgb(200, 30,  30,  clamp(0.14 + pulse));
                mid   = Color.rgb(120, 20,  20,  0.07);
            }
            case THINKING -> {
                inner = Color.rgb(30,  100, 255, clamp(0.17 + pulse));
                mid   = Color.rgb(0,   60,  160, 0.09);
            }
            default -> {
                inner = Color.rgb(0,   229, 255, clamp(0.15 + pulse));
                mid   = Color.rgb(0,   119, 182, 0.08);
            }
        }

        gc.setFill(new RadialGradient(0, 0, cx, cy, r, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, inner),
                new Stop(0.5, mid),
                new Stop(1.0, Color.rgb(10, 17, 36, 0.0))));
        gc.fillOval(cx - r, cy - r, r * 2, r * 2);
    }

    // =========================================================================
    //  IDLE — slow rotating concentric rings, subtle glow and particles
    // =========================================================================

    private void renderIdleRings(GraphicsContext gc, double cx, double cy) {
        gc.save();
        double r1 = Math.min(cx, cy) * 0.84;
        double r2 = Math.min(cx, cy) * 0.66;
        double r3 = Math.min(cx, cy) * 0.46;

        // Outer dotted ring
        gc.setStroke(withAlpha(CYAN_FULL, 0.20));
        gc.setLineWidth(1.0);
        gc.setLineDashes(6.0, 10.0);
        gc.strokeOval(cx - r1, cy - r1, r1 * 2, r1 * 2);

        // Mid segmented arc — rotates slowly
        gc.setStroke(withAlpha(CYAN_MID, 0.30));
        gc.setLineWidth(1.5);
        gc.setLineDashes(42.0, 20.0, 16.0, 20.0);
        gc.strokeArc(cx - r2, cy - r2, r2 * 2, r2 * 2, angle, 270, ArcType.OPEN);

        // Inner counter-rotating ring
        gc.setStroke(withAlpha(CYAN_FULL, 0.40));
        gc.setLineWidth(1.2);
        gc.setLineDashes(18.0, 14.0);
        gc.strokeArc(cx - r3, cy - r3, r3 * 2, r3 * 2, -angle * 1.4, 320, ArcType.OPEN);

        // Crosshair ticks at cardinal points
        gc.setStroke(withAlpha(CYAN_FULL, 0.18));
        gc.setLineWidth(1.0);
        gc.setLineDashes(null);
        drawCardinalTicks(gc, cx, cy, r1, 14);

        // Gentle pulse ring at innermost
        double pulseR = r3 * 0.60 + Math.sin(pulsePhase) * r3 * 0.06;
        gc.setStroke(withAlpha(CYAN_BRIGHT, 0.22 + 0.08 * Math.sin(pulsePhase)));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - pulseR, cy - pulseR, pulseR * 2, pulseR * 2);

        gc.restore();
    }

    // =========================================================================
    //  LISTENING — waveform radiates from centre, ring reacts to real mic amp
    // =========================================================================

    private void renderListeningRings(GraphicsContext gc, double cx, double cy) {
        // Use smoothed amplitude; stays at 0 if mic is not available
        double amp = controller.isMicAvailable() ? smoothAmp : 0.0;

        double baseR     = Math.min(cx, cy) * 0.52;
        double expandedR = baseR + amp * Math.min(cx, cy) * 0.30;

        gc.save();

        // Outer static reference ring
        gc.setStroke(withAlpha(CYAN_FULL, 0.22));
        gc.setLineWidth(1.0);
        gc.setLineDashes(8.0, 12.0);
        gc.strokeOval(cx - baseR * 1.28, cy - baseR * 1.28, baseR * 2.56, baseR * 2.56);

        // Amplitude-expanding ring — solid, gets brighter with louder audio
        double ringAlpha = 0.50 + amp * 0.50;
        gc.setStroke(withAlpha(CYAN_BRIGHT, ringAlpha));
        gc.setLineWidth(1.8 + amp * 2.2);
        gc.setLineDashes(null);
        gc.strokeOval(cx - expandedR, cy - expandedR, expandedR * 2, expandedR * 2);

        // Second inner pulsing ring
        double r2 = baseR * 0.70 + amp * baseR * 0.15;
        gc.setStroke(withAlpha(CYAN_MID, 0.35 + amp * 0.30));
        gc.setLineWidth(1.0);
        gc.strokeOval(cx - r2, cy - r2, r2 * 2, r2 * 2);

        // Radial bars (waveform spokes)
        renderRadialWaveform(gc, cx, cy, expandedR, amp);

        gc.restore();

        // ── Mic-unavailable notice ───────────────────────────────────────────
        if (!controller.isMicAvailable()) {
            gc.save();
            gc.setFill(Color.rgb(255, 180, 0, 0.85));
            gc.setFont(SYSTEM_12);
            String notice = "🎤 Microphone unavailable — audio disabled";
            double tw = notice.length() * 6.4;
            gc.fillText(notice, cx - tw / 2, cy - Math.min(cx, cy) * 0.77);
            gc.restore();
        }
    }

    /** 36 short radial bars evenly around a circle; height driven by amplitude. */
    private void renderRadialWaveform(GraphicsContext gc, double cx, double cy,
                                      double r, double amp) {
        int bars = 36;
        for (int i = 0; i < bars; i++) {
            double theta     = Math.toRadians(i * (360.0 / bars) + angle * 0.45);
            double barHeight = 7 + amp * 20 + Math.sin(pulsePhase * 2 + i * 0.5) * 4.5 * amp;
            double x1 = cx + Math.cos(theta) * r;
            double y1 = cy + Math.sin(theta) * r;
            double x2 = cx + Math.cos(theta) * (r + barHeight);
            double y2 = cy + Math.sin(theta) * (r + barHeight);
            double barAlpha = 0.35 + amp * 0.55;
            gc.setStroke(withAlpha(CYAN_FULL, barAlpha));
            gc.setLineWidth(1.4);
            gc.strokeLine(x1, y1, x2, y2);
        }
    }

    // =========================================================================
    //  THINKING — fast counter-rotating arcs, tick sweep, data ring
    // =========================================================================

    private void renderThinkingRings(GraphicsContext gc, double cx, double cy) {
        gc.save();
        double r1 = Math.min(cx, cy) * 0.82;
        double r2 = Math.min(cx, cy) * 0.63;
        double r3 = Math.min(cx, cy) * 0.44;

        // Fast outer scanning arc
        gc.setStroke(withAlpha(BLUE_SCAN, 0.55));
        gc.setLineWidth(2.0);
        gc.setLineDashes(null);
        gc.strokeArc(cx - r1, cy - r1, r1 * 2, r1 * 2, scanAngle % 360, 130, ArcType.OPEN);

        // Counter-rotating mid dashed arc
        gc.setStroke(withAlpha(CYAN_MID, 0.42));
        gc.setLineWidth(1.8);
        gc.setLineDashes(26.0, 14.0);
        gc.strokeArc(cx - r2, cy - r2, r2 * 2, r2 * 2, -(scanAngle * 0.65) % 360, 210, ArcType.OPEN);

        // Inner data ring with tick marks every 10°
        gc.setStroke(withAlpha(CYAN_FULL, 0.28));
        gc.setLineWidth(1.0);
        gc.setLineDashes(null);
        for (int d = 0; d < 360; d += 10) {
            double theta   = Math.toRadians(d);
            double tickLen = (d % 30 == 0) ? 10 : 5;
            gc.strokeLine(
                    cx + Math.cos(theta) * (r3 - tickLen), cy + Math.sin(theta) * (r3 - tickLen),
                    cx + Math.cos(theta) *  r3,            cy + Math.sin(theta) *  r3);
        }

        // Bright sweep highlight on the inner ring
        gc.setStroke(withAlpha(CYAN_BRIGHT, 0.72));
        gc.setLineWidth(2.5);
        gc.strokeArc(cx - r3, cy - r3, r3 * 2, r3 * 2, scanAngle % 360, 28, ArcType.OPEN);

        // Processing dots — 6 dots on a circle that light up in sequence
        int step = (int) (scanAngle / 60) % 6;
        double dotR = r2 * 0.78;
        for (int i = 0; i < 6; i++) {
            double theta = Math.toRadians(i * 60.0 + scanAngle * 0.5);
            double dotX  = cx + Math.cos(theta) * dotR;
            double dotY  = cy + Math.sin(theta) * dotR;
            double dotA  = (i == step) ? 0.9 : 0.25;
            gc.setFill(withAlpha(CYAN_FULL, dotA));
            gc.fillOval(dotX - 3, dotY - 3, 6, 6);
        }

        gc.restore();
    }

    // =========================================================================
    //  SPEAKING — waveform synchronized to speech amplitude, core pulses
    // =========================================================================

    private void renderSpeakingRings(GraphicsContext gc, double cx, double cy) {
        // Use smoothed amplitude; stays 0 if TTS service not available
        double amp = controller.isSpeechAvailable() ? smoothAmp : 0.0;

        double r1 = Math.min(cx, cy) * 0.80;
        double r2 = Math.min(cx, cy) * 0.60;

        gc.save();

        // Pulsing outer ring — brightness reacts to amplitude
        double pulseAlpha = 0.28 + amp * 0.60 + 0.10 * Math.sin(pulsePhase * 3);
        gc.setStroke(withAlpha(CYAN_FULL, pulseAlpha));
        gc.setLineWidth(1.4 + amp * 1.8);
        gc.setLineDashes(null);
        gc.strokeOval(cx - r1, cy - r1, r1 * 2, r1 * 2);

        // Inner dashed rotating ring
        gc.setStroke(withAlpha(CYAN_MID, 0.38 + amp * 0.38));
        gc.setLineWidth(1.2);
        gc.setLineDashes(20.0, 14.0);
        gc.strokeArc(cx - r2, cy - r2, r2 * 2, r2 * 2, angle, 280, ArcType.OPEN);

        // Horizontal sine waveform across the hologram
        renderHorizontalWaveform(gc, cx, cy, r1, amp);

        gc.restore();

        // ── TTS-unavailable notice ───────────────────────────────────────────
        if (!controller.isSpeechAvailable()) {
            gc.save();
            gc.setFill(withAlpha(CYAN_FULL, 0.52));
            gc.setFont(SYSTEM_11);
            String notice = "[Speech: TTS amplitude unavailable — stub]";
            double tw = notice.length() * 5.8;
            gc.fillText(notice, cx - tw / 2, cy - Math.min(cx, cy) * 0.74);
            gc.restore();
        }
    }

    /** Sine waveform spanning the canvas width — envelope fades at edges. */
    private void renderHorizontalWaveform(GraphicsContext gc, double cx, double cy,
                                           double r, double amp) {
        int    points  = 64;
        double startX  = cx - r;
        double span    = r * 2;

        gc.setStroke(withAlpha(CYAN_BRIGHT, 0.78));
        gc.setLineWidth(1.8);
        gc.setLineDashes(null);
        gc.beginPath();
        for (int i = 0; i < points; i++) {
            double t        = (double) i / (points - 1);
            double x        = startX + t * span;
            double envelope = Math.sin(t * Math.PI);
            double y        = cy + Math.sin(pulsePhase * 3 + t * 12) * (9 + amp * 28) * envelope;
            if (i == 0) gc.moveTo(x, y); else gc.lineTo(x, y);
        }
        gc.stroke();
    }

    // =========================================================================
    //  ALERT — restrained red/amber accents, warning triangles, pulsing rings
    // =========================================================================

    private void renderAlertRings(GraphicsContext gc, double cx, double cy) {
        gc.save();
        double r1 = Math.min(cx, cy) * 0.82;
        double r2 = Math.min(cx, cy) * 0.62;

        double blink = 0.5 + 0.5 * Math.sin(alertPhase);

        // Red outer ring (slow blink)
        gc.setStroke(withAlpha(RED_ALERT, 0.48 + blink * 0.52));
        gc.setLineWidth(2.2);
        gc.setLineDashes(null);
        gc.strokeOval(cx - r1, cy - r1, r1 * 2, r1 * 2);

        // Amber dashed mid ring (slow rotation, opposite direction)
        gc.setStroke(withAlpha(AMBER_WARN, 0.42 + blink * 0.30));
        gc.setLineWidth(1.4);
        gc.setLineDashes(16.0, 10.0);
        gc.strokeArc(cx - r2, cy - r2, r2 * 2, r2 * 2, angle, 250, ArcType.OPEN);

        // Warning triangles at cardinal points
        gc.setLineDashes(null);
        drawWarningTriangle(gc, cx,       cy - r1 - 18, 11, blink);
        drawWarningTriangle(gc, cx,       cy + r1 + 18, 11, blink);
        drawWarningTriangle(gc, cx - r1 - 18, cy,       11, blink);
        drawWarningTriangle(gc, cx + r1 + 18, cy,       11, blink);

        gc.restore();
    }

    /** Small equilateral warning triangle centred at (tx, ty). */
    private void drawWarningTriangle(GraphicsContext gc, double tx, double ty,
                                      double s, double blink) {
        double alpha = 0.58 + blink * 0.42;
        gc.setStroke(withAlpha(AMBER_WARN, alpha));
        gc.setFill  (withAlpha(AMBER_WARN, alpha * 0.28));
        gc.setLineWidth(1.5);
        double[] xs = { tx,     tx - s, tx + s };
        double[] ys = { ty - s, ty + s, ty + s };
        gc.fillPolygon(xs, ys, 3);
        gc.strokePolygon(xs, ys, 3);
    }

    // =========================================================================
    //  Particles (shared; colour varies by state)
    // =========================================================================

    private void renderParticles(GraphicsContext gc, double w, double h,
                                  AssistantState state) {
        for (Particle p : particles) {
            p.x += p.vx;
            p.y += p.vy;
            if (p.y < 0)  { p.y = h; p.x = random.nextDouble() * w; }
            if (p.x < 0)   p.x = w;
            if (p.x > w)   p.x = 0;

            p.alpha = p.maxAlpha * (0.55 + 0.45 * Math.sin(pulsePhase + p.x * 0.03));

            double a = clamp(p.alpha);
            Color c = switch (state) {
                case ALERT    -> Color.rgb(255, 120, 50, a);
                case THINKING -> Color.rgb(60,  180, 255, a);
                default       -> Color.rgb(0,   229, 255, a);
            };
            gc.setFill(c);
            gc.fillOval(p.x - p.size / 2, p.y - p.size / 2, p.size, p.size);
        }
    }

    // =========================================================================
    //  Procedural assistant silhouette (used when no avatar image is available)
    // =========================================================================

    private void renderAvatarImage(GraphicsContext gc, double cx, double cy, double w) {
        double imgW = Math.min(w * 0.84, 370);
        double imgH = imgW * (avatarImage.getHeight() / avatarImage.getWidth());
        double imgX = cx - imgW / 2.0;
        double imgY = cy - imgH / 2.0 + Math.sin(pulsePhase) * 5.5;
        gc.save();
        gc.setGlobalAlpha(0.92);
        gc.drawImage(avatarImage, imgX, imgY, imgW, imgH);
        gc.restore();
    }

    private void renderProceduralAssistant(GraphicsContext gc, double cx, double cy,
                                            AssistantState state) {
        double bob  = Math.sin(pulsePhase) * 4.5;
        double headY = cy - 80 + bob;
        gc.save();

        // Torso ambient glow
        gc.setFill(new RadialGradient(0, 0, cx, cy + 40 + bob, 135, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(0,  229, 255, 0.22)),
                new Stop(0.6, Color.rgb(0,  119, 182, 0.09)),
                new Stop(1.0, Color.rgb(0,  0,   0,   0.00))));
        gc.fillOval(cx - 88, cy - 18 + bob, 176, 235);

        // Head outline
        gc.setStroke(withAlpha(CYAN_FULL, 0.62));
        gc.setLineWidth(1.5);
        gc.setLineDashes(null);
        gc.strokeOval(cx - 30, headY - 44, 60, 78);

        // Hair aura
        gc.setStroke(withAlpha(CYAN_MID, 0.38));
        gc.strokeArc(cx - 42, headY - 54, 84, 98, 30, 120, ArcType.OPEN);

        // Eyes — two small glowing dots
        double eyeY = headY - 14;
        renderGlowDot(gc, cx - 10, eyeY, 3, state);
        renderGlowDot(gc, cx + 10, eyeY, 3, state);

        // Shoulders / neck lines
        gc.setStroke(withAlpha(CYAN_FULL, 0.52));
        gc.beginPath();
        gc.moveTo(cx - 13, headY + 32); gc.lineTo(cx - 17, headY + 56);
        gc.lineTo(cx - 73, headY + 93); gc.lineTo(cx - 62, headY + 176);
        gc.stroke();
        gc.beginPath();
        gc.moveTo(cx + 13, headY + 32); gc.lineTo(cx + 17, headY + 56);
        gc.lineTo(cx + 73, headY + 93); gc.lineTo(cx + 62, headY + 176);
        gc.stroke();

        // Chest core — colour / brightness vary by state and amplitude
        double coreY = headY + 93;
        double amp   = smoothAmp;
        Color coreCenter = switch (state) {
            case ALERT    -> Color.rgb(255, 80,  80,  0.92);
            case SPEAKING -> Color.rgb(255, 255, 255, clamp(0.68 + amp * 0.32));
            default       -> Color.rgb(255, 255, 255, 0.92);
        };
        gc.setFill(new RadialGradient(0, 0, cx, coreY, 22, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, coreCenter),
                new Stop(0.4, Color.rgb(0,  229, 255, 0.78)),
                new Stop(1.0, Color.rgb(0,  119, 182, 0.00))));
        gc.fillOval(cx - 17, coreY - 17, 34, 34);
        gc.setStroke(withAlpha(CYAN_FULL, 0.88));
        gc.setLineWidth(1.4);
        gc.strokeOval(cx - 11, coreY - 11, 22, 22);

        // Circuit connector lines from core
        gc.setStroke(withAlpha(CYAN_MID, 0.32));
        gc.setLineWidth(1.0);
        gc.strokeLine(cx - 9,  coreY + 12, cx - 24, coreY + 48);
        gc.strokeLine(cx + 9,  coreY + 12, cx + 24, coreY + 48);
        gc.strokeLine(cx - 28, coreY - 9,  cx - 52, coreY + 23);
        gc.strokeLine(cx + 28, coreY - 9,  cx + 52, coreY + 23);

        gc.restore();
    }

    /** Small radial-gradient dot — used for eye highlights. */
    private void renderGlowDot(GraphicsContext gc, double x, double y,
                                 double r, AssistantState state) {
        Color c = state == AssistantState.ALERT
                ? Color.rgb(255, 80, 80, 0.9)
                : Color.rgb(0, 229, 255, 0.9);
        gc.setFill(new RadialGradient(0, 0, x, y, r, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, c),
                new Stop(1.0, Color.rgb(0, 0, 0, 0.0))));
        gc.fillOval(x - r, y - r, r * 2, r * 2);
    }

    // =========================================================================
    //  Hand orb (shared)
    // =========================================================================

    private void renderHandOrb(GraphicsContext gc, double cx, double cy,
                                 AssistantState state) {
        double orbX = cx - 108;
        double orbY = cy + 44 + Math.sin(pulsePhase * 1.3) * 5.5;

        gc.save();

        Color orbCenter = switch (state) {
            case ALERT    -> Color.rgb(255, 100, 80, 0.90);
            case THINKING -> Color.rgb(80,  160, 255, 0.90);
            default       -> Color.rgb(255, 255, 255, 0.90);
        };
        gc.setFill(new RadialGradient(0, 0, orbX, orbY, 36, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, orbCenter),
                new Stop(0.3, Color.rgb(0,  229, 255, 0.72)),
                new Stop(0.7, Color.rgb(0,  119, 182, 0.28)),
                new Stop(1.0, Color.rgb(0,  0,   0,   0.00))));
        gc.fillOval(orbX - 36, orbY - 36, 72, 72);

        gc.setStroke(withAlpha(CYAN_FULL, 0.82));
        gc.setLineWidth(1.1);
        gc.setLineDashes(null);
        gc.strokeOval(orbX - 15, orbY - 15, 30, 30);

        gc.setStroke(withAlpha(CYAN_MID, 0.55));
        gc.strokeArc(orbX - 23, orbY - 23, 46, 46, angle * 1.9, 240, ArcType.OPEN);

        // Bright centre dot
        gc.setFill(Color.rgb(255, 255, 255, 0.88));
        gc.fillOval(orbX - 3.5, orbY - 3.5, 7, 7);

        // Vertical tether line above orb
        gc.setStroke(withAlpha(CYAN_FULL, 0.22));
        gc.setLineWidth(1.0);
        gc.strokeLine(orbX, orbY - 19, orbX, orbY - 58);

        gc.restore();
    }

    // =========================================================================
    //  State label overlay
    // =========================================================================

    private void renderStateLabel(GraphicsContext gc, double cx, double h,
                                   AssistantState state) {
        String label = switch (state) {
            case IDLE      -> null; // no label in idle — keep it clean
            case LISTENING -> controller.isMicAvailable()
                    ? "● LISTENING"
                    : "● LISTENING  [no mic — audio disabled]";
            case THINKING  -> "◈ THINKING…";
            case SPEAKING  -> controller.isSpeechAvailable()
                    ? "▶ SPEAKING"
                    : "▶ SPEAKING  [no TTS — stub mode]";
            case ALERT     -> "⚠  ALERT";
        };
        if (label == null) return;

        Color labelColor = switch (state) {
            case ALERT    -> Color.rgb(255, 80,  80,  0.92);
            case THINKING -> Color.rgb(80,  200, 255, 0.88);
            default       -> Color.rgb(0,   229, 255, 0.88);
        };

        gc.save();
        gc.setFill(labelColor);
        gc.setFont(MONO_BOLD_13);
        double tw = label.length() * 8.0;
        gc.fillText(label, cx - tw / 2, h - 20);
        gc.restore();
    }

    // =========================================================================
    //  Utility helpers
    // =========================================================================

    /** Draw short tick marks at the four cardinal points on a circle. */
    private void drawCardinalTicks(GraphicsContext gc, double cx, double cy,
                                    double r, double tickLen) {
        gc.strokeLine(cx - r - tickLen, cy, cx - r + tickLen, cy);
        gc.strokeLine(cx + r - tickLen, cy, cx + r + tickLen, cy);
        gc.strokeLine(cx, cy - r - tickLen, cx, cy - r + tickLen);
        gc.strokeLine(cx, cy + r - tickLen, cx, cy + r + tickLen);
    }

    /**
     * Returns a copy of {@code base} with the given opacity, avoiding
     * per-frame object churn by recycling when alpha is exactly 1.0.
     */
    private static Color withAlpha(Color base, double alpha) {
        double a = clamp(alpha);
        if (a == 1.0) return base;
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
