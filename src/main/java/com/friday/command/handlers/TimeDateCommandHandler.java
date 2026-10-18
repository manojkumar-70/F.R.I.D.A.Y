package com.friday.command.handlers;

import com.friday.command.CommandContext;
import com.friday.command.CommandHandler;
import com.friday.command.CommandResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Handles time and date queries with localized formatting.
 */
public final class TimeDateCommandHandler implements CommandHandler {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);

    private static final Set<String> TIME_KEYWORDS = Set.of(
            "time", "what time is it", "current time", "what is the time", "tell me the time"
    );

    private static final Set<String> DATE_KEYWORDS = Set.of(
            "date", "what date is today", "what is today's date", "today's date",
            "current date", "what day is today", "what day is it", "what is the date"
    );

    private static final Set<String> COMBINED_KEYWORDS = Set.of(
            "time and date", "date and time", "what is the date and time", "what is the time and date"
    );

    private final Supplier<LocalDateTime> dateTimeSupplier;

    public TimeDateCommandHandler() {
        this(LocalDateTime::now);
    }

    public TimeDateCommandHandler(Supplier<LocalDateTime> dateTimeSupplier) {
        this.dateTimeSupplier = (dateTimeSupplier != null) ? dateTimeSupplier : LocalDateTime::now;
    }

    @Override
    public boolean canHandle(CommandContext context) {
        String input = context.getNormalizedInput();
        if (input.isEmpty()) return false;

        return COMBINED_KEYWORDS.contains(input)
                || TIME_KEYWORDS.contains(input)
                || DATE_KEYWORDS.contains(input)
                || input.contains("time")
                || input.contains("date")
                || input.contains("day is today");
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String input = context.getNormalizedInput();
        LocalDateTime now = dateTimeSupplier.get();
        String timeStr = now.format(TIME_FORMATTER);
        String dateStr = now.format(DATE_FORMATTER);

        boolean queryTime = input.contains("time") || TIME_KEYWORDS.contains(input);
        boolean queryDate = input.contains("date") || input.contains("day") || DATE_KEYWORDS.contains(input);

        if (queryTime && queryDate) {
            String speech = "It is currently " + timeStr + " on " + dateStr + ".";
            return CommandResult.success(speech, "Current time and date: " + timeStr + ", " + dateStr);
        } else if (queryDate) {
            String speech = "Today is " + dateStr + ".";
            return CommandResult.success(speech, "Current date: " + dateStr);
        } else {
            String speech = "It is currently " + timeStr + ".";
            return CommandResult.success(speech, "Current time: " + timeStr);
        }
    }

    @Override
    public String getCommandName() {
        return "Time & Date";
    }

    @Override
    public String getDescription() {
        return "Reports current local time and date.";
    }
}

