package com.friday.audio;

/**
 * No-op stub for SpeechAudioService.
 * Used until a real TTS engine is integrated.
 * Always reports unavailable and returns 0.0 amplitude.
 */
public final class NoOpSpeechAudioService implements SpeechAudioService {

    @Override
    public double getAmplitude() {
        return 0.0;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}

