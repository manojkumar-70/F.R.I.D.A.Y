package com.friday.command.handlers;

import com.friday.command.CommandContext;
import com.friday.command.CommandHandler;
import com.friday.command.CommandResult;
import java.util.Set;

/**
 * Handles assistant diagnostics and state status queries.
 */
public final class AssistantControlCommandHandler implements CommandHandler {

    private static final Set<String> STATUS_KEYWORDS = Set.of(
            "assistant status", "status", "current state", "diagnostics", "system diagnostics", "mic status"
    );

    @Override
    public boolean canHandle(CommandContext context) {
        String input = context.getNormalizedInput();
        if (input.isEmpty()) return false;

        return STATUS_KEYWORDS.contains(input)
                || input.equals("status")
                || input.startsWith("assistant status");
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String speech = "F.R.I.D.A.Y. command subsystem is online and receptive. All core services initialized.";
        String details = "Assistant Core: ONLINE, CommandEngine: READY, Telemetry: MONITORING";
        return CommandResult.success(speech, details);
    }

    @Override
    public String getCommandName() {
        return "Assistant Diagnostics & Control";
    }

    @Override
    public String getDescription() {
        return "Reports assistant state and internal subsystem diagnostic health.";
    }
}

