package com.friday.command;

import java.util.regex.Pattern;

/**
 * Authoritative raw-input security validator for the F.R.I.D.A.Y. command subsystem.
 *
 * <p>Validates unmodified raw input strings prior to normalization or intent matching.
 * Rejects shell metacharacters, backslashes, control characters, tabs, and null bytes
 * to prevent command injection, argument splitting, and encoding obfuscation.
 */
public final class RawInputValidator {

    public static final int MAX_INPUT_LENGTH = 500;
    private static final Pattern SHELL_METACHARS = Pattern.compile("[;&|><$`\\\\]");

    private RawInputValidator() {
        // Utility class
    }

    /**
     * Validates a raw command input string.
     *
     * @param raw the raw, un-normalized command text
     * @return a rejected {@link CommandResult} if input violates policy, or {@code null} if safe
     */
    public static CommandResult validate(String raw) {
        if (raw == null) {
            return CommandResult.rejected("Command input cannot be null.");
        }
        if (raw.isBlank()) {
            return CommandResult.rejected("Command input cannot be empty.");
        }
        if (raw.length() > MAX_INPUT_LENGTH) {
            return CommandResult.rejected("Command input exceeds maximum allowed length of " + MAX_INPUT_LENGTH + " characters.");
        }

        // Check for shell metacharacters: ; & | > < $ ` \
        if (SHELL_METACHARS.matcher(raw).find()) {
            return CommandResult.rejected("Command rejected: unsafe shell metacharacters detected in raw input.");
        }

        // Check for control characters (including null bytes, newlines, carriage returns, and tabs)
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\0' || Character.isISOControl(c)) {
                return CommandResult.rejected("Command rejected: control character or null byte detected in raw input at index " + i + ".");
            }
        }

        return null;
    }

    /**
     * Helper to check whether raw input is safe.
     *
     * @param raw the raw input
     * @return {@code true} if safe, {@code false} if invalid or unsafe
     */
    public static boolean isSafe(String raw) {
        return validate(raw) == null;
    }
}

