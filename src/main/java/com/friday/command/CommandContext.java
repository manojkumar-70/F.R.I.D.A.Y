package com.friday.command;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Encapsulates a command invocation request with normalized text and invocation metadata.
 */
public final class CommandContext {

    private static final Pattern CLEAN_PATTERN = Pattern.compile("[^a-zA-Z0-9\\s]");

    private final String rawInput;
    private final String normalizedInput;
    private final CommandSource source;
    private final Instant timestamp;

    public CommandContext(String rawInput, CommandSource source, Instant timestamp) {
        this.rawInput = (rawInput != null) ? rawInput : "";
        this.source = (source != null) ? source : CommandSource.TEXT_INPUT;
        this.timestamp = (timestamp != null) ? timestamp : Instant.now();
        this.normalizedInput = normalize(this.rawInput);
    }

    public static CommandContext of(String rawInput) {
        return new CommandContext(rawInput, CommandSource.TEXT_INPUT, Instant.now());
    }

    public static CommandContext of(String rawInput, CommandSource source) {
        return new CommandContext(rawInput, source, Instant.now());
    }

    public String getRawInput() {
        return rawInput;
    }

    public String getNormalizedInput() {
        return normalizedInput;
    }

    public CommandSource getSource() {
        return source;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Checks whether the normalized input contains the specified keyword or phrase.
     */
    public boolean contains(String phrase) {
        if (phrase == null || phrase.isBlank()) return false;
        return normalizedInput.contains(normalize(phrase));
    }

    /**
     * Checks whether the normalized input matches the specified phrase exactly.
     */
    public boolean matchesExact(String phrase) {
        if (phrase == null) return false;
        return normalizedInput.equals(normalize(phrase));
    }

    /**
     * Checks whether the normalized input starts with the specified prefix phrase.
     */
    public boolean startsWith(String prefix) {
        if (prefix == null || prefix.isBlank()) return false;
        return normalizedInput.startsWith(normalize(prefix));
    }

    /**
     * Normalizes an input string: lower-case, trims whitespace, strips punctuation, collapses multiple spaces.
     */
    public static String normalize(String input) {
        if (input == null) return "";
        String cleaned = CLEAN_PATTERN.matcher(input.toLowerCase()).replaceAll(" ");
        return cleaned.trim().replaceAll("\\s+", " ");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommandContext that)) return false;
        return Objects.equals(rawInput, that.rawInput) &&
               source == that.source &&
               Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawInput, source, timestamp);
    }

    @Override
    public String toString() {
        return "CommandContext{input='" + rawInput + "', source=" + source + "}";
    }
}

