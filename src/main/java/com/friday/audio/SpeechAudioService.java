package com.friday.audio;

/**
 * Contract for any service that provides audio amplitude data (0.0–1.0).
 * Implementations may be backed by real audio hardware or be no-op stubs.
 */
public interface SpeechAudioService {

    /**
     * Returns the current amplitude in the range [0.0, 1.0].
     * Returns 0.0 when unavailable or silent.
     */
    double getAmplitude();

    /**
     * Whether this service is backed by real audio hardware/playback.
     * Returns false for stub/no-op implementations.
     */
    boolean isAvailable();
}

