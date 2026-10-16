package com.friday.assistant;

/**
 * Reactive assistant state machine states.
 * Each state drives a distinct holographic animation and audio behavior.
 */
public enum AssistantState {
    /** Default: slow rotating rings, gentle breathing glow, drifting particles. */
    IDLE,
    /** Actively capturing microphone input; waveform reacts to real audio amplitude. */
    LISTENING,
    /** Processing query/intent; analytical scanning arcs and sweep reticle. */
    THINKING,
    /** Playing back synthesized speech; waveform synchronized to speech playback. */
    SPEAKING,
    /** Performing desktop action; orbital telemetry ticks and progress arcs. */
    EXECUTING,
    /** Error or alert condition; crimson perimeter pulse and diagnostic fault indicators. */
    ERROR
}

