package com.friday.command;

/**
 * Origin source of a submitted assistant command.
 */
public enum CommandSource {
    /** Captured from speech recognition. */
    VOICE,
    /** Typed manually into a text input or HUD prompt field. */
    TEXT_INPUT,
    /** Triggered via a direct HUD button or shortcut card. */
    QUICK_ACTION,
    /** Triggered by internal automation or test harness. */
    INTERNAL
}

