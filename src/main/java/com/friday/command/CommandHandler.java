package com.friday.command;

/**
 * Contract for individual intent handlers in the F.R.I.D.A.Y. command engine.
 */
public interface CommandHandler {

    /**
     * Determines whether this handler can process the given command context.
     * Must be fast, side-effect free, and return immediately.
     */
    boolean canHandle(CommandContext context);

    /**
     * Executes the command logic and returns a structured {@link CommandResult}.
     */
    CommandResult execute(CommandContext context);

    /**
     * Human-readable name of the command.
     */
    String getCommandName();

    /**
     * Brief description of the command's purpose and accepted phrases.
     */
    String getDescription();

    /**
     * Whether this command performs sensitive, privileged, or irreversible operations
     * requiring user confirmation. Default is false.
     */
    default boolean isSensitive() {
        return false;
    }
}

