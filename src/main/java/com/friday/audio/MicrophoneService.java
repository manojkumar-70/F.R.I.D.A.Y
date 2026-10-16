package com.friday.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 * Captures microphone audio input on a background daemon thread and exposes
 * the current RMS amplitude as a volatile double in [0.0, 1.0].
 *
 * Usage:
 *   MicrophoneService mic = new MicrophoneService();
 *   mic.start();
 *   double amp = mic.getAmplitude(); // call from any thread
 *   mic.stop();
 *
 * If no microphone hardware is available, isAvailable() returns false
 * and getAmplitude() always returns 0.0.
 */
public final class MicrophoneService implements MicrophoneAudioService {

    private static final int SAMPLE_RATE   = 16000; // Hz
    private static final int SAMPLE_SIZE   = 16;    // bits
    private static final int CHANNELS      = 1;
    private static final int BUFFER_SIZE   = 1024;  // bytes

    private TargetDataLine line;
    private Thread captureThread;
    private volatile boolean running = false;
    private volatile double amplitude = 0.0;
    private final boolean available;

    public MicrophoneService() {
        boolean canOpen = false;
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, CHANNELS, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            if (AudioSystem.isLineSupported(info)) {
                line = (TargetDataLine) AudioSystem.getLine(info);
                canOpen = true;
            }
        } catch (LineUnavailableException | IllegalArgumentException e) {
            // Mic hardware not accessible; service degrades gracefully
        }
        this.available = canOpen;
    }

    /**
     * Start capturing microphone audio. Safe to call multiple times.
     *
     * @return true if capture is running; false if hardware is unavailable or failed to open.
     */
    @Override
    public synchronized boolean start() {
        if (!available) return false;
        if (running) return true;
        try {
            if (line == null) return false;
            line.open();
            line.start();
            running = true;
        } catch (LineUnavailableException | SecurityException e) {
            if (line != null && line.isOpen()) {
                try { line.close(); } catch (Exception ignored) {}
            }
            running = false;
            return false;
        }

        try {
            captureThread = new Thread(this::captureLoop, "friday-mic-capture");
            captureThread.setDaemon(true);
            captureThread.start();
        } catch (Exception e) {
            running = false;
            if (line != null && line.isOpen()) {
                try {
                    line.stop();
                    line.close();
                } catch (Exception ignored) {}
            }
            return false;
        }
        return true;
    }

    /** Stop capturing. Idempotent. */
    @Override
    public synchronized void stop() {
        running = false;
        try {
            if (line != null && line.isOpen()) {
                line.stop();
                line.close();
            }
        } catch (Exception e) {
            System.err.println("[MicrophoneService] Error closing audio line: " + e.getMessage());
        } finally {
            if (captureThread != null && captureThread != Thread.currentThread()) {
                try {
                    captureThread.join(300);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                captureThread = null;
            }
            amplitude = 0.0;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the most recent RMS amplitude in [0.0, 1.0].
     * Safe to read from any thread, including the JavaFX Application Thread.
     */
    @Override
    public double getAmplitude() {
        double val = amplitude;
        if (Double.isNaN(val) || Double.isInfinite(val)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, val));
    }

    /**
     * True only if microphone hardware was found and opened successfully.
     */
    public boolean isAvailable() {
        return available;
    }

    // ---- Private ----

    private void captureLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            while (running) {
                if (line == null || !line.isOpen()) {
                    break;
                }
                int bytesRead = line.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    amplitude = computeRmsNormalized(buffer, bytesRead);
                }
            }
        } catch (Exception e) {
            // Audio capture loop error (e.g. line closed unexpectedly)
        } finally {
            running = false;
            amplitude = 0.0;
        }
    }

    /**
     * Computes the RMS (root mean square) of 16-bit little-endian PCM samples
     * and normalizes to [0.0, 1.0].
     */
    public static double computeRmsNormalized(byte[] pcm, int length) {
        if (pcm == null || length <= 0) return 0.0;
        int clampedLength = Math.min(pcm.length, length);
        int sampleCount = clampedLength / 2; // 16-bit samples
        if (sampleCount == 0) return 0.0;

        long sumOfSquares = 0;
        for (int i = 0; i + 1 < clampedLength; i += 2) {
            // Combine two bytes into a signed 16-bit sample (little-endian)
            short sample = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xFF));
            sumOfSquares += (long) sample * sample;
        }
        double rms = Math.sqrt((double) sumOfSquares / sampleCount);
        // Normalize against maximum possible 16-bit amplitude (32768)
        double normalized = rms / 32768.0;
        if (Double.isNaN(normalized) || Double.isInfinite(normalized)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, normalized));
    }
}

