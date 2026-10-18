package com.friday.command;

import com.friday.command.handlers.AppLauncherCommandHandler;
import com.friday.command.handlers.AssistantControlCommandHandler;
import com.friday.command.handlers.ConversationalCommandHandler;
import com.friday.command.handlers.SystemTelemetryCommandHandler;
import com.friday.command.handlers.TimeDateCommandHandler;
import com.friday.command.handlers.UnknownCommandHandler;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central command processing and intent routing engine for F.R.I.D.A.Y.
 *
 * <p>Enforces security boundaries, validates raw input independently of normalization via
 * {@link RawInputValidator}, prevents execution of unauthorized shells or unsafe metacharacters,
 * resolves matching {@link CommandHandler}s in priority order, and handles application exceptions safely.
 *
 * <p><b>Stage C1 Limitation:</b> Interactive confirmation workflows (voice prompts and UI modals)
 * are deferred to Stage C2. In Stage C1, any handler returning {@code isSensitive() == true}
 * cannot execute and will yield {@link CommandStatus#REQUIRES_CONFIRMATION}.
 */
public final class CommandEngine {

    public static final int MAX_INPUT_LENGTH = RawInputValidator.MAX_INPUT_LENGTH;

    private final List<CommandHandler> handlers = new CopyOnWriteArrayList<>();
    private final CommandHandler fallbackHandler;

    /**
     * Creates a {@code CommandEngine} preconfigured with the standard production suite of handlers.
     */
    public CommandEngine() {
        this(List.of(
                new ConversationalCommandHandler(),
                new TimeDateCommandHandler(),
                new SystemTelemetryCommandHandler(),
                new AppLauncherCommandHandler(),
                new AssistantControlCommandHandler()
        ), new UnknownCommandHandler());
    }

    /**
     * Creates a {@code CommandEngine} with specified handlers and fallback handler.
     *
     * @param initialHandlers initial handlers to register in order (must not contain null entries)
     * @param fallbackHandler fallback handler for unhandled input (must not be null)
     * @throws IllegalArgumentException if initialHandlers contains null entries or fallbackHandler is null
     */
    public CommandEngine(List<CommandHandler> initialHandlers, CommandHandler fallbackHandler) {
        if (initialHandlers != null) {
            for (int i = 0; i < initialHandlers.size(); i++) {
                CommandHandler h = initialHandlers.get(i);
                if (h == null) {
                    throw new IllegalArgumentException("Handler list cannot contain null elements at index " + i);
                }
                this.handlers.add(h);
            }
        }
        if (fallbackHandler == null) {
            throw new IllegalArgumentException("Fallback handler cannot be null");
        }
        this.fallbackHandler = fallbackHandler;
    }

    /**
     * Evaluates and executes a command string with default {@link CommandSource#TEXT_INPUT}.
     *
     * @param input the raw user command text
     * @return structured {@link CommandResult}
     */
    public CommandResult execute(String input) {
        return execute(input, CommandSource.TEXT_INPUT);
    }

    /**
     * Evaluates and executes a command string from a specific {@link CommandSource}.
     *
     * @param input the raw user command text
     * @param source the origin of the command
     * @return structured {@link CommandResult}
     */
    public CommandResult execute(String input, CommandSource source) {
        // Authoritative raw-input validation BEFORE normalized matching
        CommandResult rawValidationResult = RawInputValidator.validate(input);
        if (rawValidationResult != null) {
            return rawValidationResult;
        }

        CommandContext context = CommandContext.of(input, source);
        if (context.getNormalizedInput().isBlank()) {
            return CommandResult.rejected("Command input contains no actionable alphanumeric characters.");
        }

        return execute(context);
    }

    /**
     * Evaluates and executes a fully populated {@link CommandContext}.
     *
     * @param context the command execution context
     * @return structured {@link CommandResult}
     */
    public CommandResult execute(CommandContext context) {
        if (context == null) {
            return CommandResult.rejected("Command context cannot be null.");
        }

        // Validate raw input security on context
        CommandResult rawValidationResult = RawInputValidator.validate(context.getRawInput());
        if (rawValidationResult != null) {
            return rawValidationResult;
        }

        if (context.getNormalizedInput().isBlank()) {
            return CommandResult.rejected("Command input contains no actionable alphanumeric characters.");
        }

        for (CommandHandler handler : handlers) {
            try {
                if (handler.canHandle(context)) {
                    // Sensitive command confirmation gating: in Stage C1, interactive authorization
                    // is deferred, so sensitive execution is strictly blocked.
                    if (handler.isSensitive()) {
                        return CommandResult.requiresConfirmation(
                                "Confirmation required: execution of sensitive command '" + handler.getCommandName() + "' is restricted.",
                                "Sensitive execution disabled pending interactive authorization workflow."
                        );
                    }
                    return handler.execute(context);
                }
            } catch (Exception e) {
                // Catch standard application exceptions, but let serious JVM Errors (OutOfMemoryError, etc.) propagate
                return CommandResult.failed(
                        "Internal error executing " + handler.getCommandName() + ": " + e.getMessage(),
                        e
                );
            }
        }

        // Delegate to fallback handler if no registered handler matches
        try {
            return fallbackHandler.execute(context);
        } catch (Exception e) {
            return CommandResult.failed("Error executing fallback handler: " + e.getMessage(), e);
        }
    }

    /**
     * Appends a new handler to the end of the handler list.
     *
     * @param handler the handler to append
     * @throws IllegalArgumentException if handler is null
     */
    public void registerHandler(CommandHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        handlers.add(handler);
    }

    /**
     * Prepends a new handler to the front of the handler list (highest priority).
     *
     * @param handler the handler to prepend
     * @throws IllegalArgumentException if handler is null
     */
    public void registerHandlerFirst(CommandHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        handlers.add(0, handler);
    }

    /**
     * Returns an unmodifiable list of all registered handlers in evaluation order.
     */
    public List<CommandHandler> getRegisteredHandlers() {
        return Collections.unmodifiableList(handlers);
    }

    /**
     * Returns the fallback handler.
     */
    public CommandHandler getFallbackHandler() {
        return fallbackHandler;
    }
}
