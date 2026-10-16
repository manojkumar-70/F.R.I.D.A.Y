package com.friday.system;

/**
 * Immutable point-in-time snapshot of all host system telemetry readings.
 * Ensures consistent presentation across gauges without torn or mismatched readings.
 *
 * @param cpu       CPU load telemetry reading
 * @param gpu       GPU usage telemetry reading
 * @param ram       RAM utilization telemetry reading
 * @param disk      Filesystem capacity telemetry reading
 * @param timestamp System time in milliseconds when the snapshot was sampled
 */
public record SystemTelemetrySnapshot(
        MetricReading cpu,
        MetricReading gpu,
        MetricReading ram,
        MetricReading disk,
        long timestamp
) {

    public SystemTelemetrySnapshot {
        cpu  = (cpu  != null) ? cpu  : MetricReading.UNAVAILABLE;
        gpu  = (gpu  != null) ? gpu  : MetricReading.UNAVAILABLE;
        ram  = (ram  != null) ? ram  : MetricReading.UNAVAILABLE;
        disk = (disk != null) ? disk : MetricReading.UNAVAILABLE;
    }

    public static SystemTelemetrySnapshot of(
            MetricReading cpu,
            MetricReading gpu,
            MetricReading ram,
            MetricReading disk
    ) {
        return new SystemTelemetrySnapshot(cpu, gpu, ram, disk, System.currentTimeMillis());
    }

    public static SystemTelemetrySnapshot empty() {
        return new SystemTelemetrySnapshot(
                MetricReading.unavailable("Pending initial sample"),
                MetricReading.unavailable("GPU telemetry unavailable"),
                MetricReading.unavailable("Pending initial sample"),
                MetricReading.unavailable("Pending initial sample"),
                System.currentTimeMillis()
        );
    }
}

