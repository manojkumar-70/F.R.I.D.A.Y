package com.friday.command;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of command evaluation and execution within the {@link CommandEngine}.
 */
public final class CommandResult {

    private final CommandStatus status;
    private final String responseText;
    private final String details;
    private final boolean requiresAction;
    private final Throwable error;

    private CommandResult(CommandStatus status, String responseText, String details, boolean requiresAction, Throwable error) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.responseText = (responseText != null) ? responseText : "";
        this.details = (details != null) ? details : "";
        this.requiresAction = requiresAction;
        this.error = error;
    }

    public static CommandResult success(String responseText) {
        return new CommandResult(CommandStatus.SUCCESS, responseText, "", false, null);
    }

    public static CommandResult success(String responseText, String details) {
        return new CommandResult(CommandStatus.SUCCESS, responseText, details, false, null);
    }

    public static CommandResult successWithAction(String responseText, String details) {
        return new CommandResult(CommandStatus.SUCCESS, responseText, details, true, null);
    }

    public static CommandResult unknown(String responseText) {
        return new CommandResult(CommandStatus.UNKNOWN_COMMAND, responseText, "", false, null);
    }

    public static CommandResult requiresConfirmation(String prompt, String details) {
        return new CommandResult(CommandStatus.REQUIRES_CONFIRMATION, prompt, details, true, null);
    }

    public static CommandResult rejected(String reason) {
        return new CommandResult(CommandStatus.REJECTED, reason, "", false, null);
    }

    public static CommandResult failed(String errorMessage) {
        return new CommandResult(CommandStatus.FAILED, errorMessage, "", false, null);
    }

    public static CommandResult failed(String errorMessage, Throwable error) {
        return new CommandResult(CommandStatus.FAILED, errorMessage, error != null ? error.getMessage() : "", false, error);
    }

    public CommandStatus getStatus() {
        return status;
    }

    public boolean isSuccess() {
        return status == CommandStatus.SUCCESS;
    }

    public String getResponseText() {
        return responseText;
    }

    public String getDetails() {
        return details;
    }

    public boolean requiresAction() {
        return requiresAction;
    }

    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommandResult that)) return false;
        return requiresAction == that.requiresAction &&
               status == that.status &&
               Objects.equals(responseText, that.responseText) &&
               Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, responseText, details, requiresAction);
    }

    @Override
    public String toString() {
        return "CommandResult{" +
                "status=" + status +
                ", responseText='" + responseText + '\'' +
                ", details='" + details + '\'' +
                '}';
    }
}

