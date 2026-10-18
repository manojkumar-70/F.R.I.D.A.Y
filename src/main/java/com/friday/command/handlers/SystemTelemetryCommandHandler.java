package com.friday.command.handlers;

import com.friday.command.CommandContext;
import com.friday.command.CommandHandler;
import com.friday.command.CommandResult;
import com.friday.system.MetricReading;
import com.friday.system.SystemMetricsService;
import com.friday.system.SystemTelemetrySnapshot;
import java.util.Objects;
import java.util.Set;

/**
 * Handles inquiries regarding real-time host system telemetry (CPU, RAM, Disk, and hardware metrics).
 */
public final class SystemTelemetryCommandHandler implements CommandHandler {

    private static final Set<String> CPU_KEYWORDS = Set.of(
            "cpu", "cpu usage", "cpu load", "processor", "processor usage", "what is the cpu usage"
    );

    private static final Set<String> RAM_KEYWORDS = Set.of(
            "ram", "memory", "ram usage", "memory usage", "physical memory", "how much memory is used"
    );

    private static final Set<String> DISK_KEYWORDS = Set.of(
            "disk", "disk space", "storage", "hard drive", "drive usage", "how much disk space is left"
    );

    private static final Set<String> GENERAL_KEYWORDS = Set.of(
            "system status", "telemetry", "system telemetry", "metrics", "system metrics", "hardware status"
    );

    private final SystemMetricsService metricsService;

    public SystemTelemetryCommandHandler() {
        this(new SystemMetricsService());
    }

    public SystemTelemetryCommandHandler(SystemMetricsService metricsService) {
        this.metricsService = Objects.requireNonNull(metricsService, "metricsService must not be null");
    }

    @Override
    public boolean canHandle(CommandContext context) {
        String input = context.getNormalizedInput();
        if (input.isEmpty()) return false;

        return GENERAL_KEYWORDS.contains(input)
                || CPU_KEYWORDS.contains(input)
                || RAM_KEYWORDS.contains(input)
                || DISK_KEYWORDS.contains(input)
                || input.contains("telemetry")
                || input.contains("cpu")
                || input.contains("ram")
                || input.contains("memory")
                || input.contains("disk")
                || input.contains("system status");
    }

    @Override
    public CommandResult execute(CommandContext context) {
        String input = context.getNormalizedInput();
        SystemTelemetrySnapshot snapshot = metricsService.querySnapshot();

        boolean isCpu = input.contains("cpu") || input.contains("processor");
        boolean isRam = input.contains("ram") || input.contains("memory");
        boolean isDisk = input.contains("disk") || input.contains("storage");

        // Specific metric queries
        if (isCpu && !isRam && !isDisk) {
            return formatSingleMetric("CPU", snapshot.cpu());
        }
        if (isRam && !isCpu && !isDisk) {
            return formatSingleMetric("Memory", snapshot.ram());
        }
        if (isDisk && !isCpu && !isRam) {
            return formatSingleMetric("Primary disk", snapshot.disk());
        }

        // Combined overall status report
        StringBuilder sb = new StringBuilder("System telemetry: ");
        sb.append("CPU at ").append(snapshot.cpu().isAvailable() ? snapshot.cpu().formattedPercentage() : "unavailable");
        sb.append(", RAM at ").append(snapshot.ram().isAvailable() ? snapshot.ram().formattedPercentage() : "unavailable");
        sb.append(", Disk at ").append(snapshot.disk().isAvailable() ? snapshot.disk().formattedPercentage() : "unavailable");
        sb.append(".");

        String details = String.format("Telemetry snapshot: CPU=%s, RAM=%s (%s), Disk=%s (%s)",
                snapshot.cpu().formattedPercentage(),
                snapshot.ram().formattedPercentage(),
                snapshot.ram().detail(),
                snapshot.disk().formattedPercentage(),
                snapshot.disk().detail());

        return CommandResult.success(sb.toString(), details);
    }

    private CommandResult formatSingleMetric(String name, MetricReading reading) {
        if (!reading.isAvailable()) {
            return CommandResult.success(
                    name + " telemetry is currently unavailable on this system.",
                    name + " metric unavailable"
            );
        }

        String speech = name + " load is currently " + reading.formattedPercentage() + ".";
        if (reading.detail() != null && !reading.detail().isBlank()) {
            speech = name + " usage is " + reading.formattedPercentage() + ". " + reading.detail() + ".";
        }
        return CommandResult.success(speech, name + " reading: " + reading.formattedPercentage());
    }

    @Override
    public String getCommandName() {
        return "System Telemetry";
    }

    @Override
    public String getDescription() {
        return "Reports real-time CPU, RAM, Disk, and hardware status metrics.";
    }
}
