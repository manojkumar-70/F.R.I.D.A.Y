package com.friday.assistant;

import com.friday.audio.MicrophoneService;
import com.friday.audio.NoOpSpeechAudioService;
import com.friday.audio.SpeechAudioService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Central controller for assistant reactive state.
 *
 * Owns:
 *   - The current {@link AssistantState} as a JavaFX property for UI binding.
 *   - A {@link MicrophoneService} for real microphone amplitude.
 *   - A {@link SpeechAudioService} for speech playback amplitude (stubbed).
 *
 * Wire-up pattern (in MainWindow):
 *   AssistantStateController controller = new AssistantStateController();
 *   hologramView.setController(controller);
 *   waveformMicPanel.setController(controller);
 *   stage.setOnCloseRequest(e -> controller.shutdown());
 */
public final class AssistantStateController {

    private final ObjectProperty<AssistantState> stateProperty =
            new SimpleObjectProperty<>(AssistantState.IDLE);

    private final MicrophoneService microphoneService;
    private final SpeechAudioService speechAudioService;

    public AssistantStateController() {
        this.microphoneService = new MicrophoneService();
        this.speechAudioService = new NoOpSpeechAudioService();
    }

    // ---- State API ----

    /** The current assistant state as a JavaFX observable property. */
    public ObjectProperty<AssistantState> stateProperty() {
        return stateProperty;
    }

    /** Returns the current state. Safe to call from the FX thread. */
    public AssistantState getState() {
        return stateProperty.get();
    }

    /**
     * Transitions to a new state, starting or stopping audio services
     * as appropriate.
     * Must be called on the JavaFX Application Thread.
     */
    public void setState(AssistantState newState) {
        AssistantState previous = stateProperty.get();
        if (previous == newState) return;

        // Stop mic if we're leaving LISTENING
        if (previous == AssistantState.LISTENING) {
            microphoneService.stop();
        }

        stateProperty.set(newState);

        // Start mic if entering LISTENING
        if (newState == AssistantState.LISTENING) {
            microphoneService.start();
        }
    }

    // ---- Amplitude API ----

    /**
     * Returns the current audio amplitude in [0.0, 1.0] for the active state.
     * LISTENING uses real mic data; SPEAKING uses TTS data; others return 0.0.
     * Safe to call from the AnimationTimer (FX thread reads volatile).
     */
    public double getAmplitude() {
        return switch (stateProperty.get()) {
            case LISTENING -> microphoneService.getAmplitude();
            case SPEAKING  -> speechAudioService.getAmplitude();
            default        -> 0.0;
        };
    }

    // ---- Hardware capability queries ----

    /** Whether real microphone hardware is available. */
    public boolean isMicAvailable() {
        return microphoneService.isAvailable();
    }

    /** Whether real speech playback amplitude is available. */
    public boolean isSpeechAvailable() {
        return speechAudioService.isAvailable();
    }

    // ---- Lifecycle ----

    /**
     * Shut down all audio services.
     * Call this when the application window is closing.
     */
    public void shutdown() {
        microphoneService.stop();
    }
}

