package com.friday.command;

import com.friday.command.handlers.AppLauncherCommandHandler;
import com.friday.command.handlers.AssistantControlCommandHandler;
import com.friday.command.handlers.ConversationalCommandHandler;
import com.friday.command.handlers.SystemTelemetryCommandHandler;
import com.friday.command.handlers.TimeDateCommandHandler;
import com.friday.command.handlers.UnknownCommandHandler;
import com.friday.system.SystemMetricsService;
import java.io.File;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Deterministic automated verification suite for CommandEngine, CommandContext, CommandResult,
 * DefaultProcessLauncher, RawInputValidator, and built-in command handlers.
 */
public class CommandEngineTest {

    // ── Test Doubles ─────────────────────────────────────────────────────────

    private static final class TestProcessLauncher implements ProcessLauncher {
        final List<String> launchedExecutables = new ArrayList<>();
        final List<List<String>> launchedArgs = new ArrayList<>();
        final List<URI> openedUris = new ArrayList<>();
        boolean shouldThrowOnLaunch = false;
        boolean shouldThrowOnBrowse = false;

        @Override
        public void launch(String executable, List<String> args) throws Exception {
            if (shouldThrowOnLaunch) {
                throw new RuntimeException("Simulated process launch error");
            }
            launchedExecutables.add(executable);
            launchedArgs.add(args != null ? new ArrayList<>(args) : List.of());
        }

        @Override
        public void openUri(URI uri) throws Exception {
            if (shouldThrowOnBrowse) {
                throw new RuntimeException("Simulated URI open error");
            }
            openedUris.add(uri);
        }
    }

    private static final class FakeTelemetryProvider implements SystemMetricsService.TelemetryProvider {
        double cpuLoad = 0.42;
        long totalMemory = 16_000_000_000L;
        long freeMemory = 4_000_000_000L;
        File diskRoot = null;

        @Override
        public double getCpuLoad() {
            return cpuLoad;
        }

        @Override
        public long getTotalPhysicalMemory() {
            return totalMemory;
        }

        @Override
        public long getFreePhysicalMemory() {
            return freeMemory;
        }

        @Override
        public File getPrimaryDiskRoot() {
            return diskRoot;
        }
    }

    // ── Factory Helpers ──────────────────────────────────────────────────────

    private CommandEngine createTestEngine(TestProcessLauncher launcher,
                                           LocalDateTime fixedDateTime,
                                           SystemMetricsService metricsService) {
        return createTestEngine(launcher, fixedDateTime, metricsService, () -> Optional.of("C:\\Mock\\VSCode\\Code.exe"));
    }

    private CommandEngine createTestEngine(TestProcessLauncher launcher,
                                           LocalDateTime fixedDateTime,
                                           SystemMetricsService metricsService,
                                           Supplier<Optional<String>> vsCodeResolver) {
        ConversationalCommandHandler convHandler = new ConversationalCommandHandler(
                () -> fixedDateTime != null ? fixedDateTime.toLocalTime() : LocalTime.of(10, 0)
        );
        TimeDateCommandHandler timeHandler = new TimeDateCommandHandler(
                () -> fixedDateTime != null ? fixedDateTime : LocalDateTime.of(2026, 10, 18, 19, 50, 0)
        );
        SystemTelemetryCommandHandler telemetryHandler = new SystemTelemetryCommandHandler(
                metricsService != null ? metricsService : new SystemMetricsService()
        );
        AppLauncherCommandHandler appHandler = new AppLauncherCommandHandler(
                launcher != null ? launcher : new TestProcessLauncher(),
                vsCodeResolver
        );
        AssistantControlCommandHandler controlHandler = new AssistantControlCommandHandler();

        return new CommandEngine(List.of(
                convHandler,
                timeHandler,
                telemetryHandler,
                appHandler,
                controlHandler
        ), new UnknownCommandHandler());
    }

    // ── 1. Input Validation Tests ────────────────────────────────────────────

    public void testNullInputRejection() {
        CommandEngine engine = new CommandEngine();
        CommandResult result = engine.execute((String) null);
        assertEquals(CommandStatus.REJECTED, result.getStatus(), "Null input should be rejected");
        assertTrue(result.getResponseText().contains("cannot be null"), "Response should explain null constraint");
    }

    public void testEmptyAndWhitespaceInputRejection() {
        CommandEngine engine = new CommandEngine();

        CommandResult empty = engine.execute("");
        assertEquals(CommandStatus.REJECTED, empty.getStatus(), "Empty input should be rejected");

        CommandResult spaces = engine.execute("    \t   ");
        assertEquals(CommandStatus.REJECTED, spaces.getStatus(), "Whitespace/tab input should be rejected");
    }

    public void testExcessiveLengthInputRejection() {
        CommandEngine engine = new CommandEngine();
        String hugeInput = "a".repeat(CommandEngine.MAX_INPUT_LENGTH + 1);
        CommandResult result = engine.execute(hugeInput);
        assertEquals(CommandStatus.REJECTED, result.getStatus(), "Overlong input should be rejected");
        assertTrue(result.getResponseText().contains("exceeds maximum"), "Response should explain size limit");
    }

    public void testPunctuationOnlyInputRejection() {
        CommandEngine engine = new CommandEngine();
        CommandResult result = engine.execute("??? !!! ... ---");
        assertEquals(CommandStatus.REJECTED, result.getStatus(), "Punctuation-only input should be rejected");
        assertTrue(result.getResponseText().contains("no actionable"), "Should indicate no alphanumeric content");
    }

    // ── 2. Raw-Input Security Validation Tests ───────────────────────────────

    public void testRawInputSecurityRejectsShellMetacharactersAndBackslash() {
        TestProcessLauncher launcher = new TestProcessLauncher();
        CommandEngine engine = createTestEngine(launcher, null, null);

        String[] unsafeInputs = {
                "open notepad; rmdir /s /q c:\\",
                "open calc | curl http://evil.com",
                "open explorer > out.txt",
                "open code && del file.txt",
                "open `calc`",
                "open $HOME",
                "open calc < input.txt",
                "open notepad\\evil"
        };

        for (String unsafe : unsafeInputs) {
            CommandResult result = engine.execute(unsafe);
            assertEquals(CommandStatus.REJECTED, result.getStatus(), "Unsafe input should be rejected: " + unsafe);
            assertTrue(result.getResponseText().contains("metacharacters"), "Rejection should cite metacharacters: " + unsafe);
            assertTrue(launcher.launchedExecutables.isEmpty(), "Launcher must NEVER be called for unsafe input: " + unsafe);
            assertTrue(launcher.openedUris.isEmpty(), "Browser must NEVER be opened for unsafe input: " + unsafe);
        }
    }

    public void testRawInputSecurityRejectsControlCharactersTabsAndNullBytes() {
        TestProcessLauncher launcher = new TestProcessLauncher();
        CommandEngine engine = createTestEngine(launcher, null, null);

        String[] controlInputs = {
                "open notepad\0evil",
                "open calc\ncalc",
                "open calc\rcalc",
                "open calc\tcalc",
                "open explorer\u0007beep",
                "open code\u001b[2J"
        };

        for (String ctrl : controlInputs) {
            CommandResult result = engine.execute(ctrl);
            assertEquals(CommandStatus.REJECTED, result.getStatus(), "Control chars should be rejected");
            assertTrue(launcher.launchedExecutables.isEmpty(), "Launcher must NEVER be called for control chars");
            assertTrue(launcher.openedUris.isEmpty(), "Browser must NEVER be opened for control chars");
        }
    }

    public void testDirectHandlerExecutionRejectsUnsafeRawInput() {
        TestProcessLauncher launcher = new TestProcessLauncher();
        AppLauncherCommandHandler handler = new AppLauncherCommandHandler(launcher);

        // Direct execution of handler bypassing CommandEngine must still reject unsafe raw input
        CommandContext context = CommandContext.of("open calc; del file.txt");
        CommandResult result = handler.execute(context);

        assertEquals(CommandStatus.REJECTED, result.getStatus(), "Direct execution must reject unsafe raw input");
        assertTrue(launcher.launchedExecutables.isEmpty(), "Launcher must NEVER be called on unsafe direct execution");
    }

    // ── 3. App Launcher Direct Execution Tests (No Shell) ────────────────────

    public void testAppLauncherVsCodeUsesDirectExecutableWithoutShell() {
        TestProcessLauncher launcher = new TestProcessLauncher();
        String mockCodePath = "C:\\Tools\\VSCode\\Code.exe";
        CommandEngine engine = createTestEngine(launcher, null, null, () -> Optional.of(mockCodePath));

        CommandResult result = engine.execute("open vs code");
        assertEquals(CommandStatus.SUCCESS, result.getStatus(), "VS Code command should succeed");
        assertTrue(result.requiresAction(), "Should require action flag");

        assertEquals(1, launcher.launchedExecutables.size(), "Should launch exactly 1 process");
        String exec = launcher.launchedExecutables.get(0);
        assertEquals(mockCodePath, exec, "Should launch the resolved Code.exe executable directly");
        assertTrue(!exec.equalsIgnoreCase("cmd.exe") && !exec.equalsIgnoreCase("cmd"),
                "VS Code launch must NEVER invoke cmd.exe");
        assertTrue(!exec.equalsIgnoreCase("powershell.exe") && !exec.equalsIgnoreCase("powershell"),
                "VS Code launch must NEVER invoke powershell.exe");

        assertEquals(1, launcher.launchedArgs.size(), "Should pass argument list");
        assertEquals(List.of("."), launcher.launchedArgs.get(0), "Should pass exactly ['.'] argument");
    }

    public void testAppLauncherVsCodeReturnsFailedWhenNotResolvable() {
        TestProcessLauncher launcher = new TestProcessLauncher();
        // Inject resolver returning empty (VS Code not found)
        CommandEngine engine = createTestEngine(launcher, null, null, Optional::empty);

        CommandResult result = engine.execute("open vs code");
        assertEquals(CommandStatus.FAILED, result.getStatus(), "Should return FAILED when VS Code cannot be resolved");
        assertTrue(result.getResponseText().contains("could not be located"),
                "Response should explain resolution failure: " + result.getResponseText());
        assertTrue(launcher.launchedExecutables.isEmpty(), "Launcher must NEVER be invoked when resolution fails");
    }

    public void testAppLauncherCalculator() {
        TestProcessLauncher launcher = new TestProcessLauncher();
        CommandEngine engine = createTestEngine(launcher, null, null);

        CommandResult result = engine.execute("launch calculator");
        assertEquals(CommandStatus.SUCCESS, result.getStatus(), "Calculator command should succeed");
        assertEquals(1, launcher.launchedExecutables.size(), "Should launch 1 process");
        assertEquals("calc.exe", launcher.launchedExecutables.get(0), "Should launch calc.exe");
    }

    public void testAppLauncherNotepad() {
        TestProcessLauncher launcher = new TestProcessLauncher();
        CommandEngine engine = createTestEngine(launcher, null, null);

        CommandResult result = engine.execute("open notepad");
        assertEquals(CommandStatus.SUCCESS, result.getStatus(), "Notepad command should succeed");
        assertEquals(1, launcher.launchedExecutables.size(), "Should launch 1 process");
        assertEquals("notepad.exe", launcher.launchedExecutables.get(0), "Should launch notepad.exe");
    }

    public void testAppLauncherExplorer() {
        TestProcessLauncher launcher = new TestProcessLauncher();
        CommandEngine engine = createTestEngine(launcher, null, null);

        CommandResult result = engine.execute("open file explorer");
        assertEquals(CommandStatus.SUCCESS, result.getStatus(), "Explorer command should succeed");
        assertEquals(1, launcher.launchedExecutables.size(), "Should launch 1 process");
        assertEquals("explorer.exe", launcher.launchedExecutables.get(0), "Should launch explorer.exe");
    }

    public void testAppLauncherBrowserHome() {
        TestProcessLauncher launcher = new TestProcessLauncher();
        CommandEngine engine = createTestEngine(launcher, null, null);

        CommandResult result = engine.execute("open browser");
        assertEquals(CommandStatus.SUCCESS, result.getStatus(), "Browser command should succeed");
        assertEquals(1, launcher.openedUris.size(), "Should open 1 URI");
        assertEquals(URI.create("https://google.com"), launcher.openedUris.get(0), "Should browse to Google home");
    }

    public void testAppLauncherWebSearch() {
        TestProcessLauncher launcher = new TestProcessLauncher();
        CommandEngine engine = createTestEngine(launcher, null, null);

        CommandResult result = engine.execute("search for java 26");
        assertEquals(CommandStatus.SUCCESS, result.getStatus(), "Web search should succeed");
        assertEquals(1, launcher.openedUris.size(), "Should open 1 URI");
        assertTrue(launcher.openedUris.get(0).toString().contains("java+26"),
                "Should URL encode query: " + launcher.openedUris.get(0));
    }

    // ── 4. Restricted Application Matching Tests ─────────────────────────────

    public void testRestrictedMatchingRejectsUnapprovedApplicationIntents() {
        TestProcessLauncher launcher = new TestProcessLauncher();
        CommandEngine engine = createTestEngine(launcher, null, null);

        String[] unapprovedCommands = {
                "open steam",
                "launch powershell",
                "open terminal",
                "open chrome",
                "launch discord",
                "open cmd",
                "launch spotify"
        };

        for (String unapproved : unapprovedCommands) {
            CommandResult result = engine.execute(unapproved);
            // Because unapproved commands do not match AppLauncherCommandHandler's explicit intent whitelist,
            // they fall through to UnknownCommandHandler
            assertEquals(CommandStatus.UNKNOWN_COMMAND, result.getStatus(),
                    "Unapproved command must not be handled by AppLauncher: " + unapproved);
            assertTrue(launcher.launchedExecutables.isEmpty(), "Launcher must NEVER be called for: " + unapproved);
            assertTrue(launcher.openedUris.isEmpty(), "Browser must NEVER be opened for: " + unapproved);
        }
    }

    public void testDirectExecutionRejectsUnapprovedCommand() {
        TestProcessLauncher launcher = new TestProcessLauncher();
        AppLauncherCommandHandler handler = new AppLauncherCommandHandler(launcher);

        CommandContext unapprovedContext = CommandContext.of("open steam");
        CommandResult result = handler.execute(unapprovedContext);

        assertEquals(CommandStatus.REJECTED, result.getStatus(), "Direct execution of unapproved app must be rejected");
        assertTrue(result.getResponseText().contains("whitelist"), "Response should cite whitelist policy");
        assertTrue(launcher.launchedExecutables.isEmpty(), "Launcher must not be invoked");
    }

    // ── 5. Process Launcher Boundary Security Policy Tests ───────────────────

    public void testDefaultProcessLauncherRejectsDirectShellInvocation() {
        DefaultProcessLauncher launcher = new DefaultProcessLauncher();

        String[] forbiddenShells = {"cmd.exe", "cmd", "powershell.exe", "powershell", "bash", "sh"};
        for (String shell : forbiddenShells) {
            assertThrows(SecurityException.class, () -> launcher.launch(shell, List.of("/c", "dir")));
        }
    }

    public void testDefaultProcessLauncherValidPublicUris() {
        // Valid public HTTPS/HTTP URIs
        DefaultProcessLauncher.validateUri(URI.create("https://google.com"));
        DefaultProcessLauncher.validateUri(URI.create("https://www.google.com/search?q=test"));
        DefaultProcessLauncher.validateUri(URI.create("https://github.com"));
        DefaultProcessLauncher.validateUri(URI.create("http://example.com/info"));
        DefaultProcessLauncher.validateUri(URI.create("http://8.8.8.8/"));
        DefaultProcessLauncher.validateUri(URI.create("https://[2606:4700:4700::1111]/"));
        DefaultProcessLauncher.validateUri(URI.create("https://[2001:4860:4860::8888]/"));
        DefaultProcessLauncher.validateUri(URI.create("http://123.com/"));
        DefaultProcessLauncher.validateUri(URI.create("http://37signals.com/"));
        DefaultProcessLauncher.validateUri(URI.create("http://news.ycombinator.com/"));

        // Valid public IPv4-mapped IPv6 URIs (dotted-decimal and hexadecimal)
        DefaultProcessLauncher.validateUri(URI.create("https://[::ffff:8.8.8.8]/"));
        DefaultProcessLauncher.validateUri(URI.create("https://[::ffff:808:808]/"));
        DefaultProcessLauncher.validateUri(URI.create("https://[::ffff:0808:0808]/"));
        DefaultProcessLauncher.validateUri(URI.create("https://[0:0:0:0:0:ffff:8.8.8.8]/"));
        DefaultProcessLauncher.validateUri(URI.create("https://[::ffff:1.1.1.1]/"));

        // Direct package-private IPv6 literal validation
        DefaultProcessLauncher.validateIpv6Address("2606:4700:4700::1111");
        DefaultProcessLauncher.validateIpv6Address("[2606:4700:4700::1111]");
        DefaultProcessLauncher.validateIpv6Address("2001:4860:4860::8888");
        DefaultProcessLauncher.validateIpv6Address("::ffff:8.8.8.8");
        DefaultProcessLauncher.validateIpv6Address("[::ffff:8.8.8.8]");
        DefaultProcessLauncher.validateIpv6Address("::ffff:808:808");
        DefaultProcessLauncher.validateIpv6Address("0:0:0:0:0:ffff:8.8.8.8");
    }

    public void testDefaultProcessLauncherRejectsAmbiguousNumericIpv4Hosts() {
        // Single-integer decimal representation (e.g. 2130706433 = 127.0.0.1)
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://2130706433/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://0/")));

        // Octal-like leading zeros (e.g. 0177.0.0.1 = 127.0.0.1)
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://0177.0.0.1/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://010.0.0.1/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://0000.0000.0000.0000/")));

        // Hexadecimal forms (e.g. 0x7f000001 = 127.0.0.1)
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://0x7f000001/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://0x7F000001/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://0x7f.0.0.1/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://127.0.0.0x1/")));

        // Shortened IPv4 forms (1, 2, or 3 parts resolving to loopback or private ranges)
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://127.1/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://127.0.1/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://10.1/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://192.168.1/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://0x7f.1/")));
    }

    public void testDefaultProcessLauncherRejectsDisallowedIpv6AndZoneIdLiterals() {
        // Scoped or zone-ID IPv6 representations
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://[fe80::1%25eth0]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://[fe80::1%eth0]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://[::1%251]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("fe80::1%eth0"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("[fe80::1%eth0]"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("fe80::1%25eth0"));

        // Loopback IPv6
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[::1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[0:0:0:0:0:0:0:1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("[::1]"));

        // Unspecified IPv6
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[::]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[::0]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[0:0:0:0:0:0:0:0]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("[::]"));

        // Link-local unicast (fe80::/10)
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[fe80::1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[fe90::1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[fea0::1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[feb0::1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("fe80::1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("[fe80::1]"));

        // Site-local unicast (fec0::/10)
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[fec0::1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("fec0::1"));

        // Unique local / private (fc00::/7)
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[fc00::1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[fd12:3456::1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("fc00::1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("fd12:3456::1"));

        // Multicast (ff00::/8)
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[ff02::1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[ff01::1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("ff02::1"));

        // Documentation (2001:db8::/32) and Discard (100::/64)
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[2001:db8::1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[2001:0db8:85a3::1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[100::1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("2001:db8::1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("100::1"));

        // Deprecated IPv4-compatible IPv6 addresses
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::127.0.0.1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::10.0.0.1"));
    }

    public void testDefaultProcessLauncherIpv4MappedVariants() {
        // Prohibited dotted-decimal mapped destinations
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[::ffff:127.0.0.1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[::ffff:192.168.1.1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[::ffff:10.0.0.1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[::ffff:172.16.0.1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[::ffff:169.254.1.1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[0:0:0:0:0:ffff:10.0.0.1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::ffff:127.0.0.1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("[::ffff:192.168.1.1]"));

        // Prohibited hex-encoded mapped destinations
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[::ffff:7f00:1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[::ffff:7f00:0001]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[::ffff:c0a8:101]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[::ffff:0a00:1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://[0:0:0:0:0:ffff:7f00:1]/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::ffff:7f00:1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("[::ffff:7f00:0001]"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::ffff:c0a8:101"));

        // Malformed mapped-address strings (never silently ignored)
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::ffff:256.0.0.1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::ffff:999.1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::ffff:127.0.0"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::ffff:127.0.0.1.1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::ffff:0127.0.0.1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::ffff:gggg:1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("::ffff:12345:1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("192.168.1.1:ffff::1"));
    }

    public void testMalformedUriStringsAndIpv6LiteralsRejected() {
        // Parsing boundary at URI.create()
        assertThrows(IllegalArgumentException.class, () -> URI.create("http://[:::1]/"));
        assertThrows(IllegalArgumentException.class, () -> URI.create("http://[invalid:ipv6]/"));
        assertThrows(IllegalArgumentException.class, () -> URI.create("http://[1234:5678:9abc:def0:1234:5678:9abc:def0:1234]/"));
        assertThrows(IllegalArgumentException.class, () -> URI.create("http://[fe80::1%]/"));

        // Strict numeric IPv6 parser boundary
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address(":::1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("1:2:3:4:5:6:7:8:9"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("1:2:3:4:5:6:7"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("1::2::3"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("1:2:3:4:5:6:7:8::"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("12345::1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address(":1:2:3:4:5:6:7:8"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("1:2:3:4:5:6:7:8:"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("2001:xyz::1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("[2001:db8::1"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("2001:db8::1]"));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address(""));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address("   "));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateIpv6Address(null));
    }

    public void testDefaultProcessLauncherGeneralUriPolicy() {
        // Null URI
        assertThrows(IllegalArgumentException.class, () -> DefaultProcessLauncher.validateUri(null));

        // Relative and Opaque URIs
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("/path/to/page")));

        // Disallowed schemes
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("file:///C:/Windows/System32/calc.exe")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("javascript:alert(1)")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("shell:RecycleBinFolder")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("data:text/plain,malicious")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("ftp://example.com/")));

        // User info credentials
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("https://user:pass@google.com/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://admin@google.com/")));

        // Disallowed local hostnames
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://localhost:8080/admin")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://myhost.local/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://router.internal/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://gateway.lan/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://server.corp/")));

        // Disallowed IPv4 literals (loopback, private, link-local, unspecified, multicast, broadcast)
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://127.0.0.1:3000/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://127.1.2.3/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://10.0.0.1/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://172.16.0.1/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://172.31.255.255/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://192.168.1.1/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://169.254.1.1/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://0.0.0.0/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://100.64.0.1/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://224.0.0.1/")));
        assertThrows(SecurityException.class, () -> DefaultProcessLauncher.validateUri(URI.create("http://240.0.0.1/")));
    }

    // ── 6. Sensitive Command Execution Confirmation Tests ────────────────────

    public void testSensitiveCommandExecutionBlockedInStageC1() {
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), null, null);

        final boolean[] executed = {false};
        CommandHandler sensitiveHandler = new CommandHandler() {
            @Override
            public boolean canHandle(CommandContext context) {
                return context.getNormalizedInput().equals("format drive");
            }

            @Override
            public CommandResult execute(CommandContext context) {
                executed[0] = true;
                return CommandResult.success("Drive formatted");
            }

            @Override
            public String getCommandName() {
                return "Format Drive";
            }

            @Override
            public String getDescription() {
                return "Destructive operation";
            }

            @Override
            public boolean isSensitive() {
                return true;
            }
        };

        engine.registerHandlerFirst(sensitiveHandler);
        CommandResult result = engine.execute("format drive");

        assertEquals(CommandStatus.REQUIRES_CONFIRMATION, result.getStatus(),
                "Sensitive command must yield REQUIRES_CONFIRMATION");
        assertTrue(result.getResponseText().contains("Confirmation required"), "Must explain confirmation requirement");
        assertTrue(!executed[0], "Sensitive handler execute() must NEVER be invoked without validated authorization");
    }

    // ── 7. Constructor & Handler Validation Tests ────────────────────────────

    public void testConstructorRejectsNullHandlerInList() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CommandEngine(Arrays.asList(new ConversationalCommandHandler(), null), new UnknownCommandHandler());
        });
    }

    public void testConstructorRejectsNullFallbackHandler() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CommandEngine(List.of(new ConversationalCommandHandler()), null);
        });
    }

    public void testRegisterHandlerRejectsNull() {
        CommandEngine engine = new CommandEngine();
        assertThrows(IllegalArgumentException.class, () -> engine.registerHandler(null));
        assertThrows(IllegalArgumentException.class, () -> engine.registerHandlerFirst(null));
    }

    // ── 8. Exception Handling Tests (JVM Errors vs Exceptions) ────────────────

    public void testStandardExceptionIsCaughtSafely() {
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), null, null);

        CommandHandler faultyHandler = new CommandHandler() {
            @Override
            public boolean canHandle(CommandContext context) {
                return context.getNormalizedInput().equals("throw error");
            }

            @Override
            public CommandResult execute(CommandContext context) {
                throw new IllegalStateException("Simulated application exception");
            }

            @Override
            public String getCommandName() {
                return "Faulty Handler";
            }

            @Override
            public String getDescription() {
                return "Throws exception";
            }
        };

        engine.registerHandlerFirst(faultyHandler);
        CommandResult result = engine.execute("throw error");

        assertEquals(CommandStatus.FAILED, result.getStatus(), "Standard exception should result in FAILED status");
        assertTrue(result.getResponseText().contains("Internal error"), "Response should explain internal error");
    }

    public void testJvmErrorsAreNotSwallowed() {
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), null, null);

        CommandHandler fatalHandler = new CommandHandler() {
            @Override
            public boolean canHandle(CommandContext context) {
                return context.getNormalizedInput().equals("oom");
            }

            @Override
            public CommandResult execute(CommandContext context) {
                throw new OutOfMemoryError("Simulated JVM OOM Error");
            }

            @Override
            public String getCommandName() {
                return "Fatal Handler";
            }

            @Override
            public String getDescription() {
                return "Throws Error";
            }
        };

        engine.registerHandlerFirst(fatalHandler);

        // Serious JVM Errors must NOT be caught and must propagate out of execute()
        assertThrows(OutOfMemoryError.class, () -> engine.execute("oom"));
    }

    // ── 9. Conversational, Time, Date & Telemetry Tests ───────────────────────

    public void testConversationalGreetings() {
        LocalDateTime morning = LocalDateTime.of(2026, 10, 18, 9, 30, 0);
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), morning, null);

        CommandResult resHello = engine.execute("Hello, Friday!");
        assertEquals(CommandStatus.SUCCESS, resHello.getStatus(), "Greeting should succeed");
        assertTrue(resHello.getResponseText().contains("Good morning"), "Morning greeting expected: " + resHello.getResponseText());

        LocalDateTime evening = LocalDateTime.of(2026, 10, 18, 20, 0, 0);
        CommandEngine eveningEngine = createTestEngine(new TestProcessLauncher(), evening, null);
        CommandResult resEvening = eveningEngine.execute("hey friday");
        assertEquals(CommandStatus.SUCCESS, resEvening.getStatus(), "Evening greeting should succeed");
        assertTrue(resEvening.getResponseText().contains("Good evening"), "Evening greeting expected: " + resEvening.getResponseText());
    }

    public void testConversationalIdentityAndHelp() {
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), null, null);

        CommandResult idRes = engine.execute("Who are you?");
        assertEquals(CommandStatus.SUCCESS, idRes.getStatus(), "Identity query should succeed");
        assertTrue(idRes.getResponseText().contains("F.R.I.D.A.Y."), "Response should state identity: " + idRes.getResponseText());

        CommandResult helpRes = engine.execute("what can you do");
        assertEquals(CommandStatus.SUCCESS, helpRes.getStatus(), "Help query should succeed");
        assertTrue(helpRes.getResponseText().contains("system telemetry"), "Response should outline capabilities");
    }

    public void testConversationalPleasantries() {
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), null, null);

        CommandResult thanksRes = engine.execute("thank you");
        assertEquals(CommandStatus.SUCCESS, thanksRes.getStatus(), "Thanks query should succeed");
        assertTrue(thanksRes.getResponseText().contains("service"), "Response should acknowledge thanks");

        CommandResult statusRes = engine.execute("how are you");
        assertEquals(CommandStatus.SUCCESS, statusRes.getStatus(), "Pleasantry should succeed");
        assertTrue(statusRes.getResponseText().contains("nominal"), "Response should report nominal status");
    }

    public void testTimeQuery() {
        LocalDateTime fixed = LocalDateTime.of(2026, 10, 18, 19, 45, 0);
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), fixed, null);

        CommandResult result = engine.execute("what time is it");
        assertEquals(CommandStatus.SUCCESS, result.getStatus(), "Time query should succeed");
        assertTrue(result.getResponseText().contains("7:45 PM"), "Response should format 7:45 PM: " + result.getResponseText());
    }

    public void testDateQuery() {
        LocalDateTime fixed = LocalDateTime.of(2026, 10, 18, 19, 45, 0);
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), fixed, null);

        CommandResult result = engine.execute("what is today's date");
        assertEquals(CommandStatus.SUCCESS, result.getStatus(), "Date query should succeed");
        assertTrue(result.getResponseText().contains("Sunday, October 18, 2026"),
                "Response should format full date: " + result.getResponseText());
    }

    public void testCombinedTimeAndDateQuery() {
        LocalDateTime fixed = LocalDateTime.of(2026, 10, 18, 19, 45, 0);
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), fixed, null);

        CommandResult result = engine.execute("what is the date and time");
        assertEquals(CommandStatus.SUCCESS, result.getStatus(), "Combined time and date should succeed");
        assertTrue(result.getResponseText().contains("7:45 PM"), "Should contain time");
        assertTrue(result.getResponseText().contains("Sunday, October 18, 2026"), "Should contain date");
    }

    public void testCpuTelemetryQuery() {
        FakeTelemetryProvider provider = new FakeTelemetryProvider();
        provider.cpuLoad = 0.355;
        SystemMetricsService service = new SystemMetricsService(provider);
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), null, service);

        CommandResult result = engine.execute("what is the cpu usage");
        assertEquals(CommandStatus.SUCCESS, result.getStatus(), "CPU query should succeed");
        assertTrue(result.getResponseText().contains("36%"), "Response should report 36%: " + result.getResponseText());
    }

    public void testRamTelemetryQuery() {
        FakeTelemetryProvider provider = new FakeTelemetryProvider();
        provider.totalMemory = 16_000_000_000L;
        provider.freeMemory = 4_000_000_000L; // 75% used
        SystemMetricsService service = new SystemMetricsService(provider);
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), null, service);

        CommandResult result = engine.execute("memory usage");
        assertEquals(CommandStatus.SUCCESS, result.getStatus(), "RAM query should succeed");
        assertTrue(result.getResponseText().contains("75%"), "Response should report 75%: " + result.getResponseText());
    }

    public void testOverallTelemetryQuery() {
        FakeTelemetryProvider provider = new FakeTelemetryProvider();
        provider.cpuLoad = 0.20;
        provider.totalMemory = 10_000_000_000L;
        provider.freeMemory = 5_000_000_000L;
        SystemMetricsService service = new SystemMetricsService(provider);
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), null, service);

        CommandResult result = engine.execute("system status");
        assertEquals(CommandStatus.SUCCESS, result.getStatus(), "System status query should succeed");
        assertTrue(result.getResponseText().contains("CPU at 20%"), "Response should contain CPU: " + result.getResponseText());
        assertTrue(result.getResponseText().contains("RAM at 50%"), "Response should contain RAM: " + result.getResponseText());
    }

    public void testUnavailableTelemetryQuery() {
        FakeTelemetryProvider provider = new FakeTelemetryProvider();
        provider.cpuLoad = -1.0; // unavailable
        SystemMetricsService service = new SystemMetricsService(provider);
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), null, service);

        CommandResult result = engine.execute("cpu load");
        assertEquals(CommandStatus.SUCCESS, result.getStatus(), "CPU query should return cleanly");
        assertTrue(result.getResponseText().contains("unavailable"), "Should report unavailable: " + result.getResponseText());
    }

    public void testAssistantStatusQuery() {
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), null, null);
        CommandResult result = engine.execute("assistant status");
        assertEquals(CommandStatus.SUCCESS, result.getStatus());
        assertTrue(result.getResponseText().contains("subsystem is online"), "Response should indicate online status");
    }

    public void testUnknownCommandFallback() {
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), null, null);

        CommandResult result = engine.execute("xyz123 random gibberish command");
        assertEquals(CommandStatus.UNKNOWN_COMMAND, result.getStatus(), "Unrecognized input should return UNKNOWN_COMMAND");
        assertTrue(result.getResponseText().contains("didn't recognize"), "Response should politely indicate fallback");
    }

    public void testInputNormalization() {
        CommandContext ctx = CommandContext.of("   HELLO,   F.R.I.D.A.Y.!?   ");
        assertEquals("hello f r i d a y", ctx.getNormalizedInput(), "Punctuation stripped and spaces collapsed");
        assertTrue(ctx.startsWith("hello"), "Normalized should start with hello");
    }

    public void testHandlerPriorityOverride() {
        CommandEngine engine = createTestEngine(new TestProcessLauncher(), null, null);

        CommandHandler customHandler = new CommandHandler() {
            @Override
            public boolean canHandle(CommandContext context) {
                return context.getNormalizedInput().contains("hello");
            }

            @Override
            public CommandResult execute(CommandContext context) {
                return CommandResult.success("Custom greeting override!");
            }

            @Override
            public String getCommandName() {
                return "Custom Greeting";
            }

            @Override
            public String getDescription() {
                return "Overrides default greeting";
            }
        };

        engine.registerHandlerFirst(customHandler);
        CommandResult result = engine.execute("hello");
        assertEquals(CommandStatus.SUCCESS, result.getStatus());
        assertEquals("Custom greeting override!", result.getResponseText(), "Prepend handler should take precedence");
    }

    // ── Assertions ───────────────────────────────────────────────────────────

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual) {
        assertEquals(expected, actual, "Values should be equal");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private static void assertThrows(Class<? extends Throwable> expectedType, ThrowingRunnable action) {
        try {
            action.run();
            throw new AssertionError("Expected " + expectedType.getSimpleName() + " was not thrown");
        } catch (Throwable t) {
            if (!expectedType.isInstance(t)) {
                throw new AssertionError("Expected " + expectedType.getSimpleName() + " but caught " + t.getClass().getSimpleName(), t);
            }
        }
    }
}
