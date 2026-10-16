package com.friday.system;

/**
 * Immutable model for a single system hardware telemetry reading.
 * Encapsulates the measured percentage, availability status, and descriptive details.
 *
 * @param percentage The percentage value clamped to [0.0, 100.0], or -1.0 when unavailable.
 * @param available  True if genuine hardware telemetry was retrieved successfully.
 * @param detail     Human-readable provenance or explanation (e.g. drive letter, failure cause).
 */
public record MetricReading(double percentage, boolean available, String detail) {

    public static final MetricReading UNAVAILABLE = new MetricReading(-1.0, false, "Unavailable");

    public static MetricReading of(double percentage, String detail) {
        if (Double.isNaN(percentage) || Double.isInfinite(percentage) || percentage < 0.0) {
            return new MetricReading(-1.0, false, detail != null ? detail : "Invalid reading");
        }
        double clamped = Math.min(100.0, Math.max(0.0, percentage));
        return new MetricReading(clamped, true, detail != null ? detail : "");
    }

    public static MetricReading of(double percentage) {
        return of(percentage, "");
    }

    public static MetricReading unavailable(String detail) {
        return new MetricReading(-1.0, false, detail != null ? detail : "Unavailable");
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Formats the percentage for HUD presentation.
     * Returns a rounded percentage like "42%" when available, or "--" when unavailable.
     */
    public String formattedPercentage() {
        if (!available || percentage < 0.0) {
            return "--";
        }
        return String.format("%.0f%%", percentage);
    }
}

