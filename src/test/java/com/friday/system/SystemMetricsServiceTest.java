package com.friday.system;

import java.io.File;

/**
 * Focused automated verification for SystemMetricsService, MetricReading, and SystemTelemetrySnapshot.
 * Validates genuine telemetry calculations, explicit availability reporting, NaN/infinity resilience,
 * and eliminates all simulated/fabricated fallbacks using deterministic fake providers.
 */
public class SystemMetricsServiceTest {

    // ── Deterministic Fake Hardware Provider for Isolated Unit Tests ──────────

    private static final class FakeTelemetryProvider implements SystemMetricsService.TelemetryProvider {
        double cpuLoad = 0.42;
        long totalMemory = 16_000_000_000L;
        long freeMemory = 4_000_000_000L; // 75% used
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

    // ── 1. MetricReading Clamping & Formatting ────────────────────────────────

    public void testMetricReadingClampingAndFormatting() {
        MetricReading normal = MetricReading.of(45.6, "Test metric");
        assertTrue(normal.isAvailable(), "Reading should be available");
        assertEquals(45.6, normal.percentage(), 0.001);
        assertEquals("46%", normal.formattedPercentage());

        MetricReading clampedHigh = MetricReading.of(125.0);
        assertTrue(clampedHigh.isAvailable());
        assertEquals(100.0, clampedHigh.percentage(), 0.001);
        assertEquals("100%", clampedHigh.formattedPercentage());

        MetricReading clampedLow = MetricReading.of(-15.0);
        assertFalse(clampedLow.isAvailable(), "Negative input should be marked unavailable");
        assertEquals(-1.0, clampedLow.percentage(), 0.001);
        assertEquals("--", clampedLow.formattedPercentage());

        MetricReading unavail = MetricReading.unavailable("Offline");
        assertFalse(unavail.isAvailable());
        assertEquals(-1.0, unavail.percentage(), 0.001);
        assertEquals("--", unavail.formattedPercentage());
    }

    // ── 2. MetricReading NaN and Infinities Handling ──────────────────────────

    public void testMetricReadingNanAndInfinitiesBecomeUnavailable() {
        MetricReading nanReading = MetricReading.of(Double.NaN, "NaN test");
        assertFalse(nanReading.isAvailable(), "NaN must become unavailable");
        assertEquals(-1.0, nanReading.percentage(), 0.001);
        assertEquals("--", nanReading.formattedPercentage());

        MetricReading posInf = MetricReading.of(Double.POSITIVE_INFINITY, "+Inf test");
        assertFalse(posInf.isAvailable(), "Positive infinity must become unavailable");
        assertEquals(-1.0, posInf.percentage(), 0.001);
        assertEquals("--", posInf.formattedPercentage());

        MetricReading negInf = MetricReading.of(Double.NEGATIVE_INFINITY, "-Inf test");
        assertFalse(negInf.isAvailable(), "Negative infinity must become unavailable");
        assertEquals(-1.0, negInf.percentage(), 0.001);
        assertEquals("--", negInf.formattedPercentage());
    }

    // ── 3. CPU Genuine Calculation & Normalization ────────────────────────────

    public void testCpuGenuineCalculationAndNormalization() {
        FakeTelemetryProvider fake = new FakeTelemetryProvider();
        SystemMetricsService service = new SystemMetricsService(fake);

        fake.cpuLoad = 0.355;
        MetricReading reading = service.queryCpu();
        assertTrue(reading.isAvailable(), "CPU reading should be available");
        assertEquals(35.5, reading.percentage(), 0.001);
        assertEquals("36%", reading.formattedPercentage());

        fake.cpuLoad = 1.0;
        assertEquals(100.0, service.queryCpu().percentage(), 0.001);

        fake.cpuLoad = 0.0;
        assertEquals(0.0, service.queryCpu().percentage(), 0.001);
    }

    // ── 4. CPU Negative Value (Warming Up) Never Generates Random Numbers ─────

    public void testCpuNegativeValueNeverGeneratesRandomNumbers() {
        FakeTelemetryProvider fake = new FakeTelemetryProvider();
        SystemMetricsService service = new SystemMetricsService(fake);

        fake.cpuLoad = -1.0; // Typical OS bean response before first sample
        MetricReading r1 = service.queryCpu();
        assertFalse(r1.isAvailable(), "Negative CPU load must report unavailable");
        assertEquals(-1.0, r1.percentage(), 0.001);
        assertEquals("--", r1.formattedPercentage());

        // Repeated queries must never fabricate values like 18% - 26%
        for (int i = 0; i < 20; i++) {
            MetricReading r = service.queryCpu();
            assertFalse(r.isAvailable(), "CPU must consistently report unavailable when load < 0");
            assertEquals(-1.0, r.percentage(), 0.001);
        }
    }

    // ── 5. CPU NaN and Infinity Inputs ────────────────────────────────────────

    public void testCpuNanAndInfinityInputs() {
        FakeTelemetryProvider fake = new FakeTelemetryProvider();
        SystemMetricsService service = new SystemMetricsService(fake);

        fake.cpuLoad = Double.NaN;
        assertFalse(service.queryCpu().isAvailable());

        fake.cpuLoad = Double.POSITIVE_INFINITY;
        assertFalse(service.queryCpu().isAvailable());
    }

    // ── 6. Host RAM Genuine Calculation ───────────────────────────────────────

    public void testHostRamGenuineCalculation() {
        FakeTelemetryProvider fake = new FakeTelemetryProvider();
        SystemMetricsService service = new SystemMetricsService(fake);

        // 16 GB total, 4 GB free -> 12 GB used = 75.0%
        fake.totalMemory = 16_000_000_000L;
        fake.freeMemory  =  4_000_000_000L;

        MetricReading reading = service.queryRam();
        assertTrue(reading.isAvailable(), "RAM reading should be available");
        assertEquals(75.0, reading.percentage(), 0.001);
        assertEquals("75%", reading.formattedPercentage());
    }

    // ── 7. Host RAM Division by Zero and Bounds Protection ────────────────────

    public void testHostRamDivisionByZeroProtection() {
        FakeTelemetryProvider fake = new FakeTelemetryProvider();
        SystemMetricsService service = new SystemMetricsService(fake);

        fake.totalMemory = 0L;
        fake.freeMemory  = 0L;
        MetricReading rZero = service.queryRam();
        assertFalse(rZero.isAvailable(), "Total memory of 0 must report unavailable");
        assertEquals(-1.0, rZero.percentage(), 0.001);

        fake.totalMemory = -100L;
        assertFalse(service.queryRam().isAvailable());

        // Corrupted reading where free exceeds total
        fake.totalMemory = 1_000L;
        fake.freeMemory  = 2_000L;
        assertFalse(service.queryRam().isAvailable(), "Free memory exceeding total must be marked unavailable");
    }

    // ── 8. Disk Space Calculation with Non-Existent or Null Root ─────────────

    public void testDiskCalculationWithNullOrNonExistentDisk() {
        FakeTelemetryProvider fake = new FakeTelemetryProvider();
        SystemMetricsService service = new SystemMetricsService(fake);

        fake.diskRoot = null;
        MetricReading rNull = service.queryDisk();
        assertFalse(rNull.isAvailable(), "Null disk root must report unavailable");
        assertEquals(-1.0, rNull.percentage(), 0.001);
        assertEquals("--", rNull.formattedPercentage());

        fake.diskRoot = new File("Z:\\non_existent_friday_volume_404");
        MetricReading rMissing = service.queryDisk();
        assertFalse(rMissing.isAvailable(), "Missing volume must report unavailable");
        assertEquals(-1.0, rMissing.percentage(), 0.001);
    }

    // ── 9. GPU Reporting Explicitly Unavailable (No Sinusoidal Simulation) ────

    public void testGpuReportingExplicitlyUnavailable() {
        FakeTelemetryProvider fake = new FakeTelemetryProvider();
        SystemMetricsService service = new SystemMetricsService(fake);

        MetricReading gpu = service.queryGpu();
        assertFalse(gpu.isAvailable(), "GPU telemetry must be marked unavailable without native drivers");
        assertEquals(-1.0, gpu.percentage(), 0.001);
        assertEquals("--", gpu.formattedPercentage());

        // Repeated queries must be deterministic and never fluctuate with sine waves
        for (int i = 0; i < 10; i++) {
            MetricReading g = service.queryGpu();
            assertFalse(g.isAvailable());
            assertEquals(-1.0, g.percentage(), 0.001);
        }
    }

    // ── 10. SystemTelemetrySnapshot Coherence & Immutability ──────────────────

    public void testSystemTelemetrySnapshotCoherenceAndImmutability() {
        FakeTelemetryProvider fake = new FakeTelemetryProvider();
        SystemMetricsService service = new SystemMetricsService(fake);

        fake.cpuLoad = 0.50;
        fake.totalMemory = 10_000_000_000L;
        fake.freeMemory  =  2_000_000_000L; // 80%

        SystemTelemetrySnapshot snapshot = service.querySnapshot();
        assertTrue(snapshot.cpu().isAvailable());
        assertEquals(50.0, snapshot.cpu().percentage(), 0.001);

        assertTrue(snapshot.ram().isAvailable());
        assertEquals(80.0, snapshot.ram().percentage(), 0.001);

        assertFalse(snapshot.gpu().isAvailable());
        assertTrue(snapshot.timestamp() > 0L);

        // Null safe constructor test
        SystemTelemetrySnapshot safe = new SystemTelemetrySnapshot(null, null, null, null, 123L);
        assertFalse(safe.cpu().isAvailable());
        assertFalse(safe.gpu().isAvailable());
        assertFalse(safe.ram().isAvailable());
        assertFalse(safe.disk().isAvailable());
    }

    // ── 11. Static Compatibility API on Real Host System ─────────────────────

    public void testStaticCompatibilityApiOnHostSystem() {
        double cpu = SystemMetricsService.getCpuUsagePercentage();
        if (cpu != -1.0 && (cpu < 0.0 || cpu > 100.0 || Double.isNaN(cpu) || Double.isInfinite(cpu))) {
            throw new AssertionError("Host CPU percentage out of bounds [0, 100] or -1.0: " + cpu);
        }

        double ram = SystemMetricsService.getRamUsagePercentage();
        if (ram != -1.0 && (ram < 0.0 || ram > 100.0 || Double.isNaN(ram) || Double.isInfinite(ram))) {
            throw new AssertionError("Host RAM percentage out of bounds [0, 100] or -1.0: " + ram);
        }

        double disk = SystemMetricsService.getDiskUsagePercentage();
        if (disk != -1.0 && (disk < 0.0 || disk > 100.0 || Double.isNaN(disk) || Double.isInfinite(disk))) {
            throw new AssertionError("Host Disk percentage out of bounds [0, 100] or -1.0: " + disk);
        }

        double gpu = SystemMetricsService.getGpuUsagePercentage();
        assertEquals(-1.0, gpu, 0.001, "Host GPU percentage must be -1.0 (unavailable)");
    }

    // ── 12. Repeated Static Readings Consistency ─────────────────────────────

    public void testRepeatedStaticReadingsConsistency() {
        for (int i = 0; i < 5; i++) {
            double cpu = SystemMetricsService.getCpuUsagePercentage();
            double ram = SystemMetricsService.getRamUsagePercentage();
            double disk = SystemMetricsService.getDiskUsagePercentage();
            double gpu = SystemMetricsService.getGpuUsagePercentage();

            assertTrue(cpu == -1.0 || (cpu >= 0.0 && cpu <= 100.0));
            assertTrue(ram == -1.0 || (ram >= 0.0 && ram <= 100.0));
            assertTrue(disk == -1.0 || (disk >= 0.0 && disk <= 100.0));
            assertEquals(-1.0, gpu, 0.001);
        }
    }

    // ── Assertion Helpers ─────────────────────────────────────────────────────

    private static void assertTrue(boolean condition) {
        assertTrue(condition, "Assertion failed: expected true but was false");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertFalse(boolean condition) {
        assertFalse(condition, "Assertion failed: expected false but was true");
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected + " but was: " + actual);
        }
    }

    private static void assertEquals(double expected, double actual, double delta) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError("Expected: " + expected + " within " + delta + " but was: " + actual);
        }
    }

    private static void assertEquals(double expected, double actual, double delta, String message) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError(message + " - Expected: " + expected + " within " + delta + " but was: " + actual);
        }
    }
}
