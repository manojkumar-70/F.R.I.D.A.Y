package com.friday.command;

/**
 * Execution outcome status of a command processed by the {@link CommandEngine}.
 */
public enum CommandStatus {
    /** Successfully parsed and executed. */
    SUCCESS,
    /** Execution failed due to a runtime error or unavailable resource. */
    FAILED,
    /** Input was recognized as an unknown or unsupported command. */
    UNKNOWN_COMMAND,
    /** Command is recognized but requires explicit user confirmation before executing. */
    REQUIRES_CONFIRMATION,
    /** Command was rejected due to invalid syntax, size limit, or security policy. */
    REJECTED
}

