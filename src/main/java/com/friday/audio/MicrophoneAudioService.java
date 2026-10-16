package com.friday.audio;

/**
 * Service interface for microphone audio capture.
 * Implementations manage audio input lifecycle and expose real-time RMS amplitude.
 */
public interface MicrophoneAudioService {

    /**
     * Starts audio capture.
     *
     * @return {@code true} if audio capture started successfully or is already running;
     *         {@code false} if hardware is unavailable or line initialization failed.
     */
    boolean start();

    /**
     * Stops audio capture. Idempotent.
     */
    void stop();

    /**
     * Returns the current RMS amplitude normalized in [0.0, 1.0].
     * Returns 0.0 when unavailable or silent.
     */
    double getAmplitude();

    /**
     * Whether microphone hardware is available.
     */
    boolean isAvailable();

    /**
     * Whether microphone capture is actively running.
     */
    boolean isRunning();
}

