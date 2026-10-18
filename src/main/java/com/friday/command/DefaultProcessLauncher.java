package com.friday.command;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Standard production implementation of {@link ProcessLauncher}.
 *
 * <p>Enforces strict process execution and URI destination policies at the system boundary:
 * <ul>
 *   <li>Executables are executed directly via {@link ProcessBuilder} without shell wrapping.
 *       Direct shell invocations (e.g. {@code cmd.exe}, {@code powershell.exe}) are strictly rejected.</li>
 *   <li>URIs must use the {@code https} (or {@code http}) scheme, must be absolute and hierarchical,
 *       and must not contain user credentials.</li>
 *   <li>URIs targeting loopback, private, link-local, unspecified, multicast, documentation, or other
 *       non-public IP literals (IPv4 and IPv6) and local domain names (e.g. {@code .local}, {@code .internal})
 *       are strictly rejected.</li>
 *   <li>Ambiguous or noncanonical numeric IPv4 representations (single-integer, hex, octal-like leading zeros,
 *       or shortened 1-to-3 part IPv4 forms) are strictly rejected.</li>
 *   <li>IPv6 scoped or zone-ID representations (e.g. {@code %eth0}) are strictly rejected.</li>
 * </ul>
 *
 * <p><b>Security Note:</b> This validation performs syntax-level analysis on the URI and literal
 * hostnames without performing network DNS resolution. It does not prevent DNS rebinding or internal
 * addresses mapped to public domain names.
 */
public final class DefaultProcessLauncher implements ProcessLauncher {

    private static final Set<String> DISALLOWED_SHELLS = Set.of(
            "cmd.exe", "cmd", "powershell.exe", "powershell", "pwsh.exe", "pwsh", "bash", "sh", "wscript.exe", "cscript.exe"
    );

    private static final Set<String> ALLOWED_SCHEMES = Set.of("https", "http");

    private static final Set<String> DISALLOWED_HOST_SUFFIXES = Set.of(
            ".local", ".localhost", ".internal", ".lan", ".home.arpa", ".corp", ".test", ".example", ".invalid"
    );

    @Override
    public void launch(String executable, List<String> args) throws Exception {
        if (executable == null || executable.isBlank()) {
            throw new IllegalArgumentException("Executable cannot be null or blank");
        }

        // Direct shell execution prevention at the launch boundary
        String baseName = executable.toLowerCase(Locale.ROOT).trim();
        int lastSlash = Math.max(baseName.lastIndexOf('/'), baseName.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            baseName = baseName.substring(lastSlash + 1);
        }

        if (DISALLOWED_SHELLS.contains(baseName)) {
            throw new SecurityException("Direct shell execution is strictly prohibited: " + executable);
        }

        List<String> command = new ArrayList<>();
        command.add(executable);
        if (args != null) {
            command.addAll(args);
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.start();
    }

    @Override
    public void openUri(URI uri) throws Exception {
        validateUri(uri);

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
        } else {
            throw new UnsupportedOperationException("Desktop browsing is not supported on this platform");
        }
    }

    /**
     * Validates that the URI conforms to the security policy:
     * <ul>
     *   <li>Must not be null.</li>
     *   <li>Must be absolute and hierarchical (not opaque).</li>
     *   <li>Scheme must be HTTP or HTTPS.</li>
     *   <li>Must not contain user-info credentials.</li>
     *   <li>Host must be non-empty and syntactically valid.</li>
     *   <li>Host must not be localhost or end in disallowed local domain suffixes (e.g. {@code .local}).</li>
     *   <li>Host must not be a non-public, loopback, private, link-local, unspecified, or multicast IP literal.</li>
     *   <li>Host must not use ambiguous or legacy numeric IPv4 forms (integer, hex, octal, shortened).</li>
     *   <li>Host must not use scoped/zone-ID IPv6 representations.</li>
     * </ul>
     *
     * @param uri the URI to validate
     * @throws IllegalArgumentException if the URI is null
     * @throws SecurityException if the URI violates scheme, credential, or destination policies
     */
    public static void validateUri(URI uri) {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        if (!uri.isAbsolute()) {
            throw new SecurityException("URI must be absolute: " + uri);
        }
        if (uri.isOpaque()) {
            throw new SecurityException("Opaque URIs are disallowed: " + uri);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new SecurityException("Disallowed URI scheme: '" + scheme + "'. Only HTTPS/HTTP schemes are permitted.");
        }

        // Reject user-info credentials (e.g. https://user:pass@example.com)
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new SecurityException("URIs with user credentials are prohibited: " + uri);
        }
        if (uri.getRawUserInfo() != null && !uri.getRawUserInfo().isBlank()) {
            throw new SecurityException("URIs with user credentials are prohibited: " + uri);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            String authority = uri.getRawAuthority();
            throw new SecurityException("URI must specify a valid host (authority: " + authority + ")");
        }

        String lowerHost = host.toLowerCase(Locale.ROOT).trim();

        // Reject invalid host characters
        if (lowerHost.contains(" ") || lowerHost.contains("/") || lowerHost.contains("\\") || lowerHost.contains("@")) {
            throw new SecurityException("URI host contains invalid characters: " + host);
        }

        // Reject localhost and local domain suffixes
        if (lowerHost.equals("localhost")) {
            throw new SecurityException("URI destination host is disallowed: " + host);
        }
        for (String suffix : DISALLOWED_HOST_SUFFIXES) {
            if (lowerHost.endsWith(suffix)) {
                throw new SecurityException("URI destination host has disallowed local suffix: " + host);
            }
        }

        // Validate IP literals and ambiguous numeric representations
        validateHostIpLiteralOrNumeric(lowerHost);
    }

    private static void validateHostIpLiteralOrNumeric(String host) {
        String candidate = host;
        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }

        // 1. IPv6 literal check (bracketed host, scoped address, or containing colons)
        if (host.startsWith("[") || host.endsWith("]") || candidate.contains(":") || candidate.contains("%")) {
            validateIpv6Address(host);
            return;
        }

        // 2. Single-integer IPv4 representation check (e.g. 2130706433)
        if (candidate.chars().allMatch(Character::isDigit)) {
            throw new SecurityException("Ambiguous integer IPv4 host representation is disallowed: " + host);
        }

        // 3. Hexadecimal IPv4 representation check (e.g. 0x7f000001 or 0x7f.0.0.1)
        if (candidate.startsWith("0x") || candidate.contains(".0x") || candidate.contains("0x")) {
            throw new SecurityException("Ambiguous hexadecimal IPv4 host representation is disallowed: " + host);
        }

        // 4. Dot-separated numeric representations (shortened, octal-like, or canonical)
        String[] parts = candidate.split("\\.", -1);
        boolean allNumeric = true;
        boolean hasLeadingZeros = false;
        for (String part : parts) {
            if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                allNumeric = false;
                break;
            }
            if (part.length() > 1 && part.startsWith("0")) {
                hasLeadingZeros = true;
            }
        }

        String lastPart = parts[parts.length - 1];
        boolean lastPartIsNumeric = !lastPart.isEmpty() && lastPart.chars().allMatch(Character::isDigit);

        if (allNumeric) {
            if (hasLeadingZeros) {
                throw new SecurityException("Ambiguous or noncanonical numeric IPv4 host with leading zeros is disallowed: " + host);
            }
            if (parts.length != 4) {
                throw new SecurityException("Ambiguous shortened numeric IPv4 host representation is disallowed: " + host);
            }
            validateIpv4Address(candidate);
        } else if (lastPartIsNumeric) {
            // TLD cannot be all-numeric in valid domain names (RFC 1123, RFC 3696)
            throw new SecurityException("Ambiguous noncanonical numeric host representation is disallowed: " + host);
        }
    }

    private static void validateIpv4Address(String ipStr) {
        String[] parts = ipStr.split("\\.", -1);
        if (parts.length != 4) {
            throw new SecurityException("Malformed IPv4 literal: " + ipStr);
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.length() > 1 && part.startsWith("0")) {
                throw new SecurityException("Malformed IPv4 literal with leading zeros: " + ipStr);
            }
            try {
                octets[i] = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new SecurityException("Invalid IPv4 octet in host: " + ipStr);
            }
            if (octets[i] < 0 || octets[i] > 255) {
                throw new SecurityException("IPv4 octet out of range [0-255]: " + ipStr);
            }
        }
        validateIpv4Octets(octets[0], octets[1], octets[2], octets[3], ipStr);
    }

    private static void validateIpv4Octets(int o1, int o2, int o3, int o4, String hostContext) {
        // 0.0.0.0/8 (Current network / unspecified)
        if (o1 == 0) {
            throw new SecurityException("Unspecified IPv4 destination is disallowed: " + hostContext);
        }
        // 127.0.0.0/8 (Loopback)
        if (o1 == 127) {
            throw new SecurityException("Loopback IPv4 destination is disallowed: " + hostContext);
        }
        // 10.0.0.0/8 (Private)
        if (o1 == 10) {
            throw new SecurityException("Private IPv4 destination is disallowed: " + hostContext);
        }
        // 172.16.0.0/12 (Private)
        if (o1 == 172 && (o2 >= 16 && o2 <= 31)) {
            throw new SecurityException("Private IPv4 destination is disallowed: " + hostContext);
        }
        // 192.168.0.0/16 (Private)
        if (o1 == 192 && o2 == 168) {
            throw new SecurityException("Private IPv4 destination is disallowed: " + hostContext);
        }
        // 169.254.0.0/16 (Link-local)
        if (o1 == 169 && o2 == 254) {
            throw new SecurityException("Link-local IPv4 destination is disallowed: " + hostContext);
        }
        // 100.64.0.0/10 (Shared address / CGNAT)
        if (o1 == 100 && (o2 >= 64 && o2 <= 127)) {
            throw new SecurityException("Carrier-grade NAT IPv4 destination is disallowed: " + hostContext);
        }
        // 192.0.2.0/24, 198.51.100.0/24, 203.0.113.0/24 (Documentation)
        if ((o1 == 192 && o2 == 0 && o3 == 2) ||
            (o1 == 198 && o2 == 51 && o3 == 100) ||
            (o1 == 203 && o2 == 0 && o3 == 113)) {
            throw new SecurityException("Documentation IPv4 destination is disallowed: " + hostContext);
        }
        // 224.0.0.0/4 (Multicast)
        if (o1 >= 224 && o1 <= 239) {
            throw new SecurityException("Multicast IPv4 destination is disallowed: " + hostContext);
        }
        // 240.0.0.0/4 (Reserved / Broadcast)
        if (o1 >= 240) {
            throw new SecurityException("Reserved/Broadcast IPv4 destination is disallowed: " + hostContext);
        }
    }

    static void validateIpv6Address(String host) {
        byte[] bytes = parseIpv6Literal(host);
        int[] u = new int[16];
        for (int i = 0; i < 16; i++) {
            u[i] = bytes[i] & 0xFF;
        }

        // 1. Unspecified (::/128)
        boolean allZero = true;
        for (int b : u) {
            if (b != 0) {
                allZero = false;
                break;
            }
        }
        if (allZero) {
            throw new SecurityException("Unspecified IPv6 destination is disallowed: " + host);
        }

        // 2. Loopback (::1/128)
        boolean loopback = true;
        for (int i = 0; i < 15; i++) {
            if (u[i] != 0) {
                loopback = false;
                break;
            }
        }
        if (loopback && u[15] == 1) {
            throw new SecurityException("Loopback IPv6 destination is disallowed: " + host);
        }

        // 3. IPv4-mapped IPv6 address (::ffff:0:0/96)
        boolean mappedPrefix = true;
        for (int i = 0; i < 10; i++) {
            if (u[i] != 0) {
                mappedPrefix = false;
                break;
            }
        }
        if (mappedPrefix && u[10] == 0xFF && u[11] == 0xFF) {
            validateIpv4Octets(u[12], u[13], u[14], u[15], host);
            return;
        }

        // 4. IPv4-compatible IPv6 address (::0:0/96, deprecated)
        boolean compatPrefix = true;
        for (int i = 0; i < 12; i++) {
            if (u[i] != 0) {
                compatPrefix = false;
                break;
            }
        }
        if (compatPrefix) {
            validateIpv4Octets(u[12], u[13], u[14], u[15], host);
            return;
        }

        // 5. Multicast (ff00::/8)
        if (u[0] == 0xFF) {
            throw new SecurityException("Multicast IPv6 destination is disallowed: " + host);
        }

        // 6. Link-local unicast (fe80::/10)
        if (u[0] == 0xFE && (u[1] & 0xC0) == 0x80) {
            throw new SecurityException("Link-local IPv6 destination is disallowed: " + host);
        }

        // 7. Site-local unicast (fec0::/10, deprecated by RFC 3879)
        if (u[0] == 0xFE && (u[1] & 0xC0) == 0xC0) {
            throw new SecurityException("Site-local IPv6 destination is disallowed: " + host);
        }

        // 8. Unique local / private (fc00::/7)
        if ((u[0] & 0xFE) == 0xFC) {
            throw new SecurityException("Private unique-local IPv6 destination is disallowed: " + host);
        }

        // 9. Documentation prefix (2001:db8::/32)
        if (u[0] == 0x20 && u[1] == 0x01 && u[2] == 0x0D && u[3] == 0xB8) {
            throw new SecurityException("Documentation IPv6 destination is disallowed: " + host);
        }

        // 10. Discard-only prefix (100::/64, RFC 6666)
        if (u[0] == 0x01 && u[1] == 0x00 && u[2] == 0 && u[3] == 0 && u[4] == 0 && u[5] == 0 && u[6] == 0 && u[7] == 0) {
            throw new SecurityException("Discard IPv6 destination is disallowed: " + host);
        }
    }

    static byte[] parseIpv6Literal(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            throw new SecurityException("IPv6 host cannot be null or blank");
        }

        String host = rawHost.trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1).trim();
        }

        // 1. Reject bracket mismatches or embedded brackets
        if (host.contains("[") || host.contains("]")) {
            throw new SecurityException("Malformed IPv6 literal with invalid brackets: " + rawHost);
        }

        // 2. Reject zone ID / scope
        if (host.contains("%")) {
            throw new SecurityException("IPv6 scoped or zone-ID addresses are disallowed: " + rawHost);
        }

        if (host.isEmpty()) {
            throw new SecurityException("Empty IPv6 address: " + rawHost);
        }

        // 3. Reject more than two colons, single leading colon, or single trailing colon
        if (host.contains(":::")) {
            throw new SecurityException("Malformed IPv6 literal with consecutive colons (:::): " + rawHost);
        }
        if (host.startsWith(":") && !host.startsWith("::")) {
            throw new SecurityException("Malformed IPv6 literal with single leading colon: " + rawHost);
        }
        if (host.endsWith(":") && !host.endsWith("::")) {
            throw new SecurityException("Malformed IPv6 literal with single trailing colon: " + rawHost);
        }

        // 4. Handle double colon (::) compression
        int firstDoubleColon = host.indexOf("::");
        if (firstDoubleColon >= 0) {
            int secondDoubleColon = host.indexOf("::", firstDoubleColon + 2);
            if (secondDoubleColon >= 0) {
                throw new SecurityException("Malformed IPv6 literal with multiple double-colons (::): " + rawHost);
            }
        }

        String leftStr;
        String rightStr;
        if (firstDoubleColon >= 0) {
            leftStr = host.substring(0, firstDoubleColon);
            rightStr = host.substring(firstDoubleColon + 2);
        } else {
            leftStr = host;
            rightStr = null;
        }

        if (!leftStr.isEmpty() && (leftStr.startsWith(":") || leftStr.endsWith(":"))) {
            throw new SecurityException("Malformed IPv6 literal with misplaced colon: " + rawHost);
        }
        if (rightStr != null && !rightStr.isEmpty() && (rightStr.startsWith(":") || rightStr.endsWith(":"))) {
            throw new SecurityException("Malformed IPv6 literal with misplaced colon: " + rawHost);
        }

        List<Integer> leftWords = new ArrayList<>();
        List<Integer> rightWords = new ArrayList<>();

        if (leftStr.contains(".")) {
            if (rightStr != null) {
                throw new SecurityException("Embedded IPv4 address must be at the end of the IPv6 literal: " + rawHost);
            }
            parseWordsAndEmbeddedIpv4(leftStr, leftWords, rawHost);
        } else {
            parseHexWords(leftStr, leftWords, rawHost);
        }

        if (rightStr != null) {
            if (rightStr.contains(".")) {
                parseWordsAndEmbeddedIpv4(rightStr, rightWords, rawHost);
            } else {
                parseHexWords(rightStr, rightWords, rawHost);
            }
        }

        int totalSpecifiedWords = leftWords.size() + rightWords.size();
        if (firstDoubleColon >= 0) {
            if (totalSpecifiedWords >= 8) {
                throw new SecurityException("Malformed IPv6 literal: too many words with '::' present: " + rawHost);
            }
        } else {
            if (totalSpecifiedWords != 8) {
                throw new SecurityException("Malformed IPv6 literal: expected 8 words but found " + totalSpecifiedWords + ": " + rawHost);
            }
        }

        int zerosToFill = 8 - totalSpecifiedWords;
        int[] allWords = new int[8];
        int idx = 0;
        for (int w : leftWords) {
            allWords[idx++] = w;
        }
        for (int i = 0; i < zerosToFill; i++) {
            allWords[idx++] = 0;
        }
        for (int w : rightWords) {
            allWords[idx++] = w;
        }

        byte[] result = new byte[16];
        for (int i = 0; i < 8; i++) {
            result[2 * i] = (byte) ((allWords[i] >> 8) & 0xFF);
            result[2 * i + 1] = (byte) (allWords[i] & 0xFF);
        }
        return result;
    }

    private static void parseHexWords(String s, List<Integer> words, String rawHost) {
        if (s.isEmpty()) {
            return;
        }
        String[] parts = s.split(":", -1);
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new SecurityException("Malformed IPv6 literal with empty word: " + rawHost);
            }
            if (part.length() > 4) {
                throw new SecurityException("IPv6 hex word exceeds 4 characters: " + rawHost);
            }
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
                if (!isHex) {
                    throw new SecurityException("Invalid character in IPv6 hex word: '" + c + "' in " + rawHost);
                }
            }
            try {
                int val = Integer.parseInt(part, 16);
                words.add(val);
            } catch (NumberFormatException e) {
                throw new SecurityException("Invalid hex word in IPv6 literal: " + rawHost);
            }
        }
    }

    private static void parseWordsAndEmbeddedIpv4(String s, List<Integer> words, String rawHost) {
        int lastColon = s.lastIndexOf(':');
        String hexPrefix = lastColon >= 0 ? s.substring(0, lastColon) : "";
        String ipv4Str = lastColon >= 0 ? s.substring(lastColon + 1) : s;

        if (!hexPrefix.isEmpty()) {
            parseHexWords(hexPrefix, words, rawHost);
        }
        parseEmbeddedIpv4Words(ipv4Str, words, rawHost);
    }

    private static void parseEmbeddedIpv4Words(String ipv4Str, List<Integer> words, String rawHost) {
        String[] octetParts = ipv4Str.split("\\.", -1);
        if (octetParts.length != 4) {
            throw new SecurityException("Malformed embedded IPv4 address in IPv6 literal (expected 4 octets): " + rawHost);
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            String p = octetParts[i];
            if (p.isEmpty()) {
                throw new SecurityException("Malformed embedded IPv4 address with empty octet: " + rawHost);
            }
            if (p.length() > 1 && p.startsWith("0")) {
                throw new SecurityException("Ambiguous embedded IPv4 address with leading zero: " + rawHost);
            }
            for (int j = 0; j < p.length(); j++) {
                char c = p.charAt(j);
                if (c < '0' || c > '9') {
                    throw new SecurityException("Invalid character in embedded IPv4 octet: '" + c + "' in " + rawHost);
                }
            }
            try {
                int val = Integer.parseInt(p);
                if (val < 0 || val > 255) {
                    throw new SecurityException("Embedded IPv4 octet out of range [0-255]: " + rawHost);
                }
                octets[i] = val;
            } catch (NumberFormatException e) {
                throw new SecurityException("Invalid embedded IPv4 octet in IPv6 literal: " + rawHost);
            }
        }
        int word1 = (octets[0] << 8) | octets[1];
        int word2 = (octets[2] << 8) | octets[3];
        words.add(word1);
        words.add(word2);
    }
}
