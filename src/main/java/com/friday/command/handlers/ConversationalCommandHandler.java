package com.friday.command.handlers;

import com.friday.command.CommandContext;
import com.friday.command.CommandHandler;
import com.friday.command.CommandResult;
import java.time.LocalTime;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Handles conversational inquiries, greetings, identity questions, and general help requests.
 */
public final class ConversationalCommandHandler implements CommandHandler {

    private static final Set<String> GREETING_KEYWORDS = Set.of(
            "hello", "hi", "hey", "hey friday", "greetings", "good morning",
            "good afternoon", "good evening", "good night"
    );

    private static final Set<String> IDENTITY_KEYWORDS = Set.of(
            "who are you", "what is your name", "what are you", "introduce yourself", "tell me about yourself"
    );

    private static final Set<String> HELP_KEYWORDS = Set.of(
            "help", "what can you do", "commands", "how can you help", "show commands", "list commands"
    );

    private static final Set<String> PLEASANTRY_KEYWORDS = Set.of(
            "how are you", "how are you doing", "are you ready", "thank you", "thanks"
    );

    private final Supplier<LocalTime> timeSupplier;

    public ConversationalCommandHandler() {
        this(LocalTime::now);
    }

    public ConversationalCommandHandler(Supplier<LocalTime> timeSupplier) {
        this.timeSupplier = (timeSupplier != null) ? timeSupplier : LocalTime::now;
    }

    @Override
    public boolean canHandle(CommandContext context) {
        String input = context.getNormalizedInput();
        if (input.isEmpty()) return false;

        return GREETING_KEYWORDS.contains(input)
                || IDENTITY_KEYWORDS.contains(input)
                || HELP_KEYWORDS.contains(input)
                || PLEASANTRY_KEYWORDS.contains(input)
                || input.startsWith("hello")
                || input.startsWith("hey friday");
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String input = context.getNormalizedInput();

        if (IDENTITY_KEYWORDS.contains(input)) {
            return CommandResult.success(
                    "I am F.R.I.D.A.Y., your personal holographic desktop AI assistant. Ready when you are, Manoj.",
                    "F.R.I.D.A.Y. identity acknowledged."
            );
        }

        if (HELP_KEYWORDS.contains(input)) {
            return CommandResult.success(
                    "I can monitor system telemetry (CPU, RAM, Disk), report current time and date, " +
                    "launch approved desktop tools (VS Code, Explorer, Notepad, Calculator), and search the web. " +
                    "Try saying 'system telemetry', 'what time is it', or 'open vs code'.",
                    "Assistance capabilities catalog displayed."
            );
        }

        if (PLEASANTRY_KEYWORDS.contains(input)) {
            if (input.contains("thank")) {
                return CommandResult.success(
                        "Always at your service, Manoj.",
                        "Pleasantry acknowledged."
                );
            }
            return CommandResult.success(
                    "All diagnostic systems are nominal and operating at peak performance. How may I assist you?",
                    "System health nominal."
            );
        }

        // Default greeting
        String greeting = getDynamicGreeting();
        return CommandResult.success(
                greeting + ", Manoj. All systems operational. How can I assist you today?",
                "Greeting dispatched."
        );
    }

    private String getDynamicGreeting() {
        int hour = timeSupplier.get().getHour();
        if (hour >= 5 && hour < 12) return "Good morning";
        if (hour >= 12 && hour < 17) return "Good afternoon";
        if (hour >= 17 && hour < 22) return "Good evening";
        return "Good evening";
    }

    @Override
    public String getCommandName() {
        return "Conversational & Assistance";
    }

    @Override
    public String getDescription() {
        return "Handles greetings, identity inquiries, pleasantries, and general system help.";
    }
}

