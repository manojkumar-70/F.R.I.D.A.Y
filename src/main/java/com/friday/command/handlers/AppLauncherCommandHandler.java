package com.friday.command.handlers;

import com.friday.command.CommandContext;
import com.friday.command.CommandHandler;
import com.friday.command.CommandResult;
import com.friday.command.DefaultProcessLauncher;
import com.friday.command.ProcessLauncher;
import com.friday.command.RawInputValidator;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Handles launching approved desktop applications and web queries.
 *
 * <p>Enforces a strict whitelist: only explicitly approved applications (VS Code, Calculator,
 * Notepad, File Explorer, and Web Browser) and structured web searches can be handled.
 * Unknown application commands, arbitrary shell commands, and shell metacharacters are strictly rejected.
 *
 * <p>VS Code is resolved to a direct executable (e.g. {@code Code.exe}) without invoking a shell.
 */
public final class AppLauncherCommandHandler implements CommandHandler {

    // ── Explicit Whitelist of Approved Application Intents ───────────────────

    private static final Set<String> VS_CODE_INTENTS = Set.of(
            "code", "vscode", "vs code", "visual studio code",
            "open code", "open vscode", "open vs code", "open visual studio code",
            "launch code", "launch vscode", "launch vs code", "launch visual studio code"
    );

    private static final Set<String> CALC_INTENTS = Set.of(
            "calc", "calculator",
            "open calc", "open calculator",
            "launch calc", "launch calculator"
    );

    private static final Set<String> NOTEPAD_INTENTS = Set.of(
            "notepad",
            "open notepad",
            "launch notepad"
    );

    private static final Set<String> EXPLORER_INTENTS = Set.of(
            "explorer", "file explorer", "files",
            "open explorer", "open file explorer", "open files",
            "launch explorer", "launch file explorer", "launch files"
    );

    private static final Set<String> BROWSER_INTENTS = Set.of(
            "browser", "web browser",
            "open browser", "open web browser", "open web",
            "launch browser", "launch web browser"
    );

    private final ProcessLauncher launcher;
    private final Supplier<Optional<String>> vsCodeResolver;

    public AppLauncherCommandHandler() {
        this(new DefaultProcessLauncher(), AppLauncherCommandHandler::resolveDefaultVsCode);
    }

    public AppLauncherCommandHandler(ProcessLauncher launcher) {
        this(launcher, AppLauncherCommandHandler::resolveDefaultVsCode);
    }

    public AppLauncherCommandHandler(ProcessLauncher launcher, String explicitVsCodeExecutable) {
        this(launcher, () -> Optional.ofNullable(explicitVsCodeExecutable).filter(s -> !s.isBlank()));
    }

    public AppLauncherCommandHandler(ProcessLauncher launcher, Supplier<Optional<String>> vsCodeResolver) {
        this.launcher = Objects.requireNonNull(launcher, "launcher must not be null");
        this.vsCodeResolver = (vsCodeResolver != null) ? vsCodeResolver : AppLauncherCommandHandler::resolveDefaultVsCode;
    }

    @Override
    public boolean canHandle(CommandContext context) {
        String input = context.getNormalizedInput();
        if (input.isEmpty()) return false;

        return VS_CODE_INTENTS.contains(input)
                || CALC_INTENTS.contains(input)
                || NOTEPAD_INTENTS.contains(input)
                || EXPLORER_INTENTS.contains(input)
                || BROWSER_INTENTS.contains(input)
                || isWebSearch(input);
    }

    @Override
    public CommandResult execute(CommandContext context) {
        // Authoritative raw-input validation defense-in-depth
        CommandResult rawSecurityResult = RawInputValidator.validate(context.getRawInput());
        if (rawSecurityResult != null) {
            return rawSecurityResult;
        }

        String input = context.getNormalizedInput();

        try {
            // 1. Visual Studio Code — Direct executable launch (no shell invocation)
            if (VS_CODE_INTENTS.contains(input)) {
                Optional<String> execOpt = vsCodeResolver.get();
                if (execOpt.isEmpty()) {
                    return CommandResult.failed(
                            "Visual Studio Code executable (Code.exe) could not be located on this system. " +
                            "Please ensure VS Code is installed or specify its binary location."
                    );
                }
                String executablePath = execOpt.get();
                launcher.launch(executablePath, List.of("."));
                return CommandResult.successWithAction(
                        "Opening Visual Studio Code in the current workspace.",
                        "Launched: " + executablePath + " ."
                );
            }

            // 2. Windows Calculator
            if (CALC_INTENTS.contains(input)) {
                launcher.launch("calc.exe", List.of());
                return CommandResult.successWithAction(
                        "Launching Windows Calculator.",
                        "Launched: calc.exe"
                );
            }

            // 3. Notepad
            if (NOTEPAD_INTENTS.contains(input)) {
                launcher.launch("notepad.exe", List.of());
                return CommandResult.successWithAction(
                        "Opening Notepad.",
                        "Launched: notepad.exe"
                );
            }

            // 4. File Explorer
            if (EXPLORER_INTENTS.contains(input)) {
                launcher.launch("explorer.exe", List.of());
                return CommandResult.successWithAction(
                        "Opening File Explorer.",
                        "Launched: explorer.exe"
                );
            }

            // 5. Web Browser Home
            if (BROWSER_INTENTS.contains(input)) {
                URI browserUri = URI.create("https://google.com");
                launcher.openUri(browserUri);
                return CommandResult.successWithAction(
                        "Opening web browser.",
                        "Opened URL: https://google.com"
                );
            }

            // 6. Web Search (strictly separated from app launch)
            if (isWebSearch(input)) {
                String query = extractSearchQuery(input);
                if (query.isBlank()) {
                    return CommandResult.failed("Please specify a search query.");
                }
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                URI searchUri = URI.create("https://www.google.com/search?q=" + encoded);
                launcher.openUri(searchUri);
                return CommandResult.successWithAction(
                        "Searching the web for: " + query + ".",
                        "Opened search: " + searchUri
                );
            }

            // Unapproved application target (direct execute invocation of unsupported command)
            return CommandResult.rejected(
                    "Command rejected: application not in the approved whitelist. " +
                    "Approved apps are VS Code, Calculator, Notepad, File Explorer, and Web Browser."
            );
        } catch (Exception ex) {
            return CommandResult.failed("Failed to launch target application: " + ex.getMessage(), ex);
        }
    }

    private static boolean isWebSearch(String input) {
        return input.startsWith("search for ") ||
               input.startsWith("search ") ||
               input.startsWith("google for ") ||
               input.startsWith("google ");
    }

    private static String extractSearchQuery(String input) {
        if (input.startsWith("search for ")) {
            return input.substring("search for ".length()).trim();
        }
        if (input.startsWith("search ")) {
            return input.substring("search ".length()).trim();
        }
        if (input.startsWith("google for ")) {
            return input.substring("google for ".length()).trim();
        }
        if (input.startsWith("google ")) {
            return input.substring("google ".length()).trim();
        }
        return "";
    }

    /**
     * Resolves an installed VS Code executable on the host system without invoking a shell.
     * Checks common Windows user and system installation directories, followed by PATH.
     */
    public static Optional<String> resolveDefaultVsCode() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (isWindows) {
            // 1. Check %LOCALAPPDATA%\Programs\Microsoft VS Code\Code.exe (default user installer)
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                File candidate = new File(localAppData, "Programs/Microsoft VS Code/Code.exe");
                if (candidate.isFile() && candidate.canExecute()) {
                    return Optional.of(candidate.getAbsolutePath());
                }
            }

            // 2. Check %ProgramFiles%\Microsoft VS Code\Code.exe (system 64-bit installer)
            String progFiles = System.getenv("ProgramFiles");
            if (progFiles != null && !progFiles.isBlank()) {
                File candidate = new File(progFiles, "Microsoft VS Code/Code.exe");
                if (candidate.isFile() && candidate.canExecute()) {
                    return Optional.of(candidate.getAbsolutePath());
                }
            }

            // 3. Check %ProgramFiles(x86)%\Microsoft VS Code\Code.exe (system 32-bit installer)
            String progFilesX86 = System.getenv("ProgramFiles(x86)");
            if (progFilesX86 != null && !progFilesX86.isBlank()) {
                File candidate = new File(progFilesX86, "Microsoft VS Code/Code.exe");
                if (candidate.isFile() && candidate.canExecute()) {
                    return Optional.of(candidate.getAbsolutePath());
                }
            }

            // 4. Scan PATH directories for Code.exe
            String pathEnv = System.getenv("PATH");
            if (pathEnv != null && !pathEnv.isBlank()) {
                for (String dir : pathEnv.split(Pattern.quote(File.pathSeparator))) {
                    if (!dir.isBlank()) {
                        File candidate = new File(dir.trim(), "Code.exe");
                        if (candidate.isFile() && candidate.canExecute()) {
                            return Optional.of(candidate.getAbsolutePath());
                        }
                    }
                }
            }
            return Optional.empty();
        } else {
            // Unix / macOS known locations
            String[] commonPaths = {
                    "/usr/bin/code",
                    "/usr/local/bin/code",
                    "/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code"
            };
            for (String p : commonPaths) {
                File f = new File(p);
                if (f.isFile() && f.canExecute()) {
                    return Optional.of(f.getAbsolutePath());
                }
            }

            String pathEnv = System.getenv("PATH");
            if (pathEnv != null && !pathEnv.isBlank()) {
                for (String dir : pathEnv.split(Pattern.quote(File.pathSeparator))) {
                    if (!dir.isBlank()) {
                        File candidate = new File(dir.trim(), "code");
                        if (candidate.isFile() && candidate.canExecute()) {
                            return Optional.of(candidate.getAbsolutePath());
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public String getCommandName() {
        return "Application Launcher & Web Search";
    }

    @Override
    public String getDescription() {
        return "Safely launches whitelisted desktop applications (VS Code, Notepad, Calculator, Explorer) and web queries.";
    }
}
