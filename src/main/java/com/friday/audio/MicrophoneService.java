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
public final class MicrophoneService {

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

    /** Start capturing microphone audio. Safe to call multiple times. */
    public void start() {
        if (!available || running) return;
        running = true;
        try {
            line.open();
            line.start();
        } catch (LineUnavailableException e) {
            running = false;
            return;
        }
        captureThread = new Thread(this::captureLoop, "friday-mic-capture");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    /** Stop capturing. Idempotent. */
    public void stop() {
        running = false;
        if (line != null && line.isOpen()) {
            line.stop();
            line.close();
        }
        amplitude = 0.0;
    }

    /**
     * Returns the most recent RMS amplitude in [0.0, 1.0].
     * Safe to read from any thread, including the JavaFX Application Thread.
     */
    public double getAmplitude() {
        return amplitude;
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
        while (running) {
            int bytesRead = line.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                amplitude = computeRmsNormalized(buffer, bytesRead);
            }
        }
    }

    /**
     * Computes the RMS (root mean square) of 16-bit little-endian PCM samples
     * and normalizes to [0.0, 1.0].
     */
    private static double computeRmsNormalized(byte[] pcm, int length) {
        long sumOfSquares = 0;
        int sampleCount = length / 2; // 16-bit samples
        for (int i = 0; i + 1 < length; i += 2) {
            // Combine two bytes into a signed 16-bit sample (little-endian)
            short sample = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xFF));
            sumOfSquares += (long) sample * sample;
        }
        if (sampleCount == 0) return 0.0;
        double rms = Math.sqrt((double) sumOfSquares / sampleCount);
        // Normalize against maximum possible 16-bit amplitude (32768)
        double normalized = rms / 32768.0;
        return Math.min(1.0, normalized);
    }
}

