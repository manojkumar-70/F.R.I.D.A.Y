package com.friday.assistant;

/**
 * Reactive assistant state machine states.
 * Each state drives a distinct holographic animation and audio behavior.
 */
public enum AssistantState {
    /** Default: slow rotating rings, gentle glow, drifting particles. */
    IDLE,
    /** Actively capturing microphone input; waveform reacts to real amplitude. */
    LISTENING,
    /** Processing a query; faster scanning arcs and data-ring sweep. */
    THINKING,
    /** Playing back synthesized speech; waveform pulses with real audio amplitude. */
    SPEAKING,
    /** Warning condition; red/amber ring accents and alert indicators. */
    ALERT
}

