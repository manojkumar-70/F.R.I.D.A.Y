package com.friday.assistant;

import com.friday.audio.MicrophoneAudioService;
import com.friday.audio.MicrophoneService;
import com.friday.audio.NoOpSpeechAudioService;
import com.friday.audio.SpeechAudioService;
import java.util.concurrent.atomic.AtomicLong;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/**
 * Central controller for assistant reactive state, transition safety, and audio service coordination.
 *
 * Owns:
 *   - The current {@link AssistantState} as a JavaFX observable property for UI binding.
 *   - A {@link MicrophoneAudioService} for real microphone capture and RMS amplitude.
 *   - A {@link SpeechAudioService} for speech playback amplitude.
 *   - Validated state transition logic preventing invalid states and resource leaks.
 */
public final class AssistantStateController {

    private final ReadOnlyObjectWrapper<AssistantState> stateWrapper;
    private final MicrophoneAudioService microphoneService;
    private final SpeechAudioService speechAudioService;

    private volatile AssistantState currentState = AssistantState.IDLE;
    private final AtomicLong stateSequence = new AtomicLong(0);
    private volatile String lastErrorMessage = null;
    private volatile boolean isShutdown = false;
    private volatile double taskProgress = -1.0; // -1.0 = indeterminate

    public AssistantStateController() {
        this(new MicrophoneService(), new NoOpSpeechAudioService());
    }

    public AssistantStateController(MicrophoneAudioService microphoneService, SpeechAudioService speechAudioService) {
        this.stateWrapper = new ReadOnlyObjectWrapper<>(AssistantState.IDLE);
        this.microphoneService = microphoneService != null ? microphoneService : new MicrophoneService();
        this.speechAudioService = speechAudioService != null ? speechAudioService : new NoOpSpeechAudioService();
    }

    // ---- State API & Transition Validation ----

    /** The current assistant state as a JavaFX read-only observable property. */
    public ReadOnlyObjectProperty<AssistantState> stateProperty() {
        return stateWrapper.getReadOnlyProperty();
    }

    /** Returns the current state. Safe to call synchronously from any thread. */
    public AssistantState getState() {
        return currentState;
    }

    /**
     * Enforces the explicit state-transition matrix.
     *
     * Permitted transitions:
     *   IDLE      -> LISTENING, THINKING, EXECUTING, ERROR
     *   LISTENING -> THINKING, IDLE, ERROR
     *   THINKING  -> EXECUTING, SPEAKING, IDLE, ERROR
     *   EXECUTING -> THINKING, SPEAKING, IDLE, ERROR
     *   SPEAKING  -> IDLE, LISTENING, ERROR
     *   ERROR     -> IDLE
     *
     * Self-transition (from == to) is always valid as a neutral no-op.
     */
    public static boolean isValidTransition(AssistantState from, AssistantState to) {
        if (from == to) return true;
        if (to == null) return false;

        return switch (from) {
            case IDLE -> to == AssistantState.LISTENING
                      || to == AssistantState.THINKING
                      || to == AssistantState.EXECUTING
                      || to == AssistantState.ERROR;
            case LISTENING -> to == AssistantState.THINKING
                           || to == AssistantState.IDLE
                           || to == AssistantState.ERROR;
            case THINKING -> to == AssistantState.EXECUTING
                          || to == AssistantState.SPEAKING
                          || to == AssistantState.IDLE
                          || to == AssistantState.ERROR;
            case EXECUTING -> to == AssistantState.THINKING
                           || to == AssistantState.SPEAKING
                           || to == AssistantState.IDLE
                           || to == AssistantState.ERROR;
            case SPEAKING -> to == AssistantState.IDLE
                          || to == AssistantState.LISTENING
                          || to == AssistantState.ERROR;
            case ERROR -> to == AssistantState.IDLE;
        };
    }

    /** Returns whether a transition to {@code target} is currently permitted. */
    public boolean canTransitionTo(AssistantState target) {
        if (isShutdown) return false;
        return isValidTransition(currentState, target);
    }

    /**
     * Transitions to a new state if valid.
     * Starts microphone capture on entering LISTENING; stops capture on exit.
     * If microphone capture fails to start, safely transitions to ERROR without leaving
     * the controller reporting LISTENING.
     *
     * @param newState the target assistant state
     * @return true if the transition succeeded or was a no-op; false if rejected or failed.
     */
    public boolean transitionTo(AssistantState newState) {
        return transitionTo(newState, null);
    }

    /**
     * Transitions to a new state with an optional error description (used when entering ERROR).
     */
    public synchronized boolean transitionTo(AssistantState newState, String errorMessage) {
        if (isShutdown) {
            System.err.println("[AssistantStateController] Transition rejected: controller has been shut down.");
            return false;
        }
        if (newState == null) {
            System.err.println("[AssistantStateController] Transition rejected: target state cannot be null.");
            return false;
        }

        AssistantState previous = currentState;
        if (previous == newState) {
            return true;
        }

        if (!isValidTransition(previous, newState)) {
            System.err.println("[AssistantStateController] Invalid transition rejected: "
                    + previous + " -> " + newState);
            return false;
        }

        // Microphone lifecycle management on entering LISTENING
        if (newState == AssistantState.LISTENING) {
            boolean micStarted = false;
            try {
                micStarted = microphoneService.start();
            } catch (Exception e) {
                micStarted = false;
            }

            if (!micStarted) {
                this.lastErrorMessage = (errorMessage != null) ? errorMessage : "Microphone capture failed to start";
                System.err.println("[AssistantStateController] " + this.lastErrorMessage);
                this.currentState = AssistantState.ERROR;
                long seq = stateSequence.incrementAndGet();
                updateStateProperty(AssistantState.ERROR, seq);
                return false;
            }
        }

        // Microphone lifecycle management on leaving LISTENING
        if (previous == AssistantState.LISTENING && newState != AssistantState.LISTENING) {
            try {
                microphoneService.stop();
            } catch (Exception e) {
                System.err.println("[AssistantStateController] Error stopping microphone: " + e.getMessage());
            }
        }

        if (newState == AssistantState.ERROR) {
            this.lastErrorMessage = (errorMessage != null) ? errorMessage : "System error condition";
        } else if (newState == AssistantState.IDLE) {
            this.lastErrorMessage = null;
            this.taskProgress = -1.0;
        }

        this.currentState = newState;
        long seq = stateSequence.incrementAndGet();
        updateStateProperty(newState, seq);
        return true;
    }

    /**
     * Public alias for {@link #transitionTo(AssistantState)} preserving backward compatibility.
     */
    public boolean setState(AssistantState newState) {
        return transitionTo(newState);
    }

    private void updateStateProperty(AssistantState newState, long seq) {
        if (Platform.isFxApplicationThread()) {
            stateWrapper.set(newState);
        } else {
            try {
                Platform.runLater(() -> {
                    if (seq == stateSequence.get()) {
                        stateWrapper.set(newState);
                    }
                });
            } catch (IllegalStateException notRunning) {
                // Headless test environment without JavaFX toolkit
                stateWrapper.set(newState);
            }
        }
    }

    // ---- Amplitude API ----

    /**
     * Returns the current audio amplitude strictly clamped in [0.0, 1.0].
     * LISTENING uses real mic data; SPEAKING uses speech playback data; others return 0.0.
     * Safe to call from the AnimationTimer (FX thread reads volatile).
     */
    public double getAmplitude() {
        double raw = switch (currentState) {
            case LISTENING -> microphoneService.getAmplitude();
            case SPEAKING  -> speechAudioService.getAmplitude();
            default        -> 0.0;
        };
        if (Double.isNaN(raw) || Double.isInfinite(raw)) return 0.0;
        return Math.max(0.0, Math.min(1.0, raw));
    }

    // ---- Task Progress API for EXECUTING state ----

    /**
     * Returns the current task execution progress in [0.0, 1.0], or -1.0 for indeterminate progress.
     */
    public double getTaskProgress() {
        double val = taskProgress;
        if (Double.isNaN(val) || Double.isInfinite(val)) return -1.0;
        return val;
    }

    /**
     * Sets the current task execution progress.
     * Pass a value in [0.0, 1.0], or -1.0 for indeterminate rotating progress.
     */
    public void setTaskProgress(double progress) {
        if (Double.isNaN(progress)) {
            this.taskProgress = -1.0;
        } else if (Double.isInfinite(progress)) {
            this.taskProgress = (progress > 0) ? 1.0 : -1.0;
        } else if (progress < 0.0) {
            this.taskProgress = -1.0;
        } else {
            this.taskProgress = Math.min(1.0, progress);
        }
    }

    // ---- Diagnostics & Error Recovery ----

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.lastErrorMessage = errorMessage;
    }

    /** Clears error state and safely returns to IDLE. */
    public boolean resetError() {
        if (currentState == AssistantState.ERROR) {
            return transitionTo(AssistantState.IDLE);
        }
        return true;
    }

    // ---- Hardware capability queries ----

    /** Whether real microphone hardware is available. */
    public boolean isMicAvailable() {
        return microphoneService.isAvailable();
    }

    /** Whether the microphone is actively capturing audio. */
    public boolean isMicRunning() {
        return microphoneService.isRunning();
    }

    /** Whether real speech playback amplitude is available. */
    public boolean isSpeechAvailable() {
        return speechAudioService.isAvailable();
    }

    public boolean isShutdown() {
        return isShutdown;
    }

    // ---- Lifecycle ----

    /**
     * Shut down all audio services and reset state.
     * Idempotent; safe to call multiple times.
     */
    public synchronized void shutdown() {
        if (isShutdown) return;
        isShutdown = true;
        try {
            microphoneService.stop();
        } catch (Exception ignored) {}

        if (currentState != AssistantState.IDLE) {
            currentState = AssistantState.IDLE;
            long seq = stateSequence.incrementAndGet();
            updateStateProperty(AssistantState.IDLE, seq);
        }
    }
}
