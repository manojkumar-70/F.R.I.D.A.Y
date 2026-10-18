package com.friday.command.handlers;

import com.friday.command.CommandContext;
import com.friday.command.CommandHandler;
import com.friday.command.CommandResult;

/**
 * Fallback handler invoked when no registered {@link CommandHandler} recognizes the command input.
 */
public final class UnknownCommandHandler implements CommandHandler {

    @Override
    public boolean canHandle(CommandContext context) {
        return true;
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String input = context.getRawInput();
        String speech = "I'm sorry, Manoj. I didn't recognize that command. Say 'help' to see what I can do.";
        String details = "Unrecognized input: '" + input + "'";
        return CommandResult.unknown(speech);
    }

    @Override
    public String getCommandName() {
        return "Unknown Command Fallback";
    }

    @Override
    public String getDescription() {
        return "Provides helpful fallback feedback when a command is not understood.";
    }
}

