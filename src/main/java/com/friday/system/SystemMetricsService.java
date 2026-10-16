package com.friday.system;

import java.io.File;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

/**
 * Service to query genuine host system hardware telemetry (CPU, RAM, Disk).
 * Eliminates fabricated simulations and models unavailable hardware honestly.
 *
 * Provides an injectable {@link TelemetryProvider} interface for deterministic unit testing.
 */
public final class SystemMetricsService {

    /**
     * Interface abstracting native system queries, allowing tests to inject
     * synthetic hardware states without real OS dependencies.
     */
    public interface TelemetryProvider {
        double getCpuLoad();
        long getTotalPhysicalMemory();
        long getFreePhysicalMemory();
        File getPrimaryDiskRoot();
    }

    private final TelemetryProvider provider;
    private static final SystemMetricsService DEFAULT_INSTANCE = new SystemMetricsService();

    public SystemMetricsService() {
        this(createDefaultProvider());
    }

    public SystemMetricsService(TelemetryProvider provider) {
        this.provider = provider != null ? provider : createDefaultProvider();
    }

    // ── Instance Telemetry Queries ──────────────────────────────────────────

    /**
     * Queries genuine host CPU load.
     * Returns unavailable if the OS bean reports negative (e.g. warming up or unsupported).
     */
    public MetricReading queryCpu() {
        try {
            double load = provider.getCpuLoad();
            if (Double.isNaN(load) || Double.isInfinite(load) || load < 0.0) {
                return MetricReading.unavailable("CPU load telemetry unavailable");
            }
            double pct = Math.min(100.0, Math.max(0.0, load * 100.0));
            return MetricReading.of(pct, "Host System CPU");
        } catch (Exception e) {
            return MetricReading.unavailable("CPU query failed: " + e.getMessage());
        }
    }

    /**
     * Queries genuine host physical memory (RAM).
     * Distinguishes host physical RAM from JVM heap and refuses to report JVM heap as host RAM.
     */
    public MetricReading queryRam() {
        try {
            long total = provider.getTotalPhysicalMemory();
            long free = provider.getFreePhysicalMemory();
            if (total > 0 && free >= 0 && free <= total) {
                double used = (double) (total - free) / (double) total;
                double pct = Math.min(100.0, Math.max(0.0, used * 100.0));
                return MetricReading.of(pct, "Host Physical RAM");
            }
            return MetricReading.unavailable("Host physical RAM metrics unavailable");
        } catch (Exception e) {
            return MetricReading.unavailable("RAM query failed: " + e.getMessage());
        }
    }

    /**
     * Queries genuine disk space for the primary host system drive.
     * Identifies the primary system drive dynamically and rejects zero/invalid totals.
     */
    public MetricReading queryDisk() {
        try {
            File disk = provider.getPrimaryDiskRoot();
            if (disk == null || !disk.exists()) {
                return MetricReading.unavailable("System disk root not found");
            }
            long total = disk.getTotalSpace();
            long free = disk.getFreeSpace();
            if (total > 0 && free >= 0 && free <= total) {
                double used = (double) (total - free) / (double) total;
                double pct = Math.min(100.0, Math.max(0.0, used * 100.0));
                String path = disk.getAbsolutePath();
                return MetricReading.of(pct, "System Drive (" + path + ")");
            }
            return MetricReading.unavailable("Disk space metrics unavailable");
        } catch (Exception e) {
            return MetricReading.unavailable("Disk query failed: " + e.getMessage());
        }
    }

    /**
     * Reports GPU telemetry status.
     * Genuine GPU telemetry requires native platform hooks (DirectX / NVML / JNI).
     * In the absence of native bindings, reports explicitly unavailable rather than a fake simulation.
     */
    public MetricReading queryGpu() {
        return MetricReading.unavailable("GPU telemetry unavailable (requires native driver hooks)");
    }

    /**
     * Takes a combined snapshot of all system telemetry metrics.
     */
    public SystemTelemetrySnapshot querySnapshot() {
        return SystemTelemetrySnapshot.of(queryCpu(), queryGpu(), queryRam(), queryDisk());
    }

    // ── Static Convenience API (Backward Compatibility) ─────────────────────

    public static SystemTelemetrySnapshot getSnapshot() {
        return DEFAULT_INSTANCE.querySnapshot();
    }

    public static MetricReading getCpuReading() {
        return DEFAULT_INSTANCE.queryCpu();
    }

    public static MetricReading getRamReading() {
        return DEFAULT_INSTANCE.queryRam();
    }

    public static MetricReading getDiskReading() {
        return DEFAULT_INSTANCE.queryDisk();
    }

    public static MetricReading getGpuReading() {
        return DEFAULT_INSTANCE.queryGpu();
    }

    /**
     * Returns CPU usage percentage in [0.0, 100.0], or -1.0 if unavailable.
     */
    public static double getCpuUsagePercentage() {
        return getCpuReading().percentage();
    }

    /**
     * Returns host RAM usage percentage in [0.0, 100.0], or -1.0 if unavailable.
     */
    public static double getRamUsagePercentage() {
        return getRamReading().percentage();
    }

    /**
     * Returns disk usage percentage in [0.0, 100.0], or -1.0 if unavailable.
     */
    public static double getDiskUsagePercentage() {
        return getDiskReading().percentage();
    }

    /**
     * Returns GPU usage percentage in [0.0, 100.0], or -1.0 when unavailable.
     */
    public static double getGpuUsagePercentage() {
        return getGpuReading().percentage();
    }

    // ── Private Provider Builders ───────────────────────────────────────────

    private static TelemetryProvider createDefaultProvider() {
        OperatingSystemMXBean bean = null;
        try {
            var mxBean = ManagementFactory.getOperatingSystemMXBean();
            if (mxBean instanceof OperatingSystemMXBean sunBean) {
                bean = sunBean;
            }
        } catch (Exception ignored) {}

        final OperatingSystemMXBean osBean = bean;
        return new TelemetryProvider() {
            @Override
            public double getCpuLoad() {
                if (osBean != null) {
                    try {
                        return osBean.getCpuLoad();
                    } catch (Exception ignored) {}
                }
                return -1.0;
            }

            @Override
            public long getTotalPhysicalMemory() {
                if (osBean != null) {
                    try {
                        return osBean.getTotalMemorySize();
                    } catch (Exception ignored) {}
                }
                return -1L;
            }

            @Override
            public long getFreePhysicalMemory() {
                if (osBean != null) {
                    try {
                        return osBean.getFreeMemorySize();
                    } catch (Exception ignored) {}
                }
                return -1L;
            }

            @Override
            public File getPrimaryDiskRoot() {
                return findPrimarySystemDisk();
            }
        };
    }

    private static File findPrimarySystemDisk() {
        try {
            // 1. Prioritize Windows SystemDrive environment variable (e.g. "C:" or "D:")
            String systemDrive = System.getenv("SystemDrive");
            if (systemDrive != null && !systemDrive.isBlank()) {
                String path = systemDrive.endsWith(File.separator) ? systemDrive : systemDrive + File.separator;
                File drive = new File(path);
                if (drive.exists() && drive.getTotalSpace() > 0) {
                    return drive;
                }
            }

            // 2. Select the first available filesystem root with valid total space
            File[] roots = File.listRoots();
            if (roots != null) {
                for (File root : roots) {
                    if (root != null && root.exists() && root.getTotalSpace() > 0) {
                        return root;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
