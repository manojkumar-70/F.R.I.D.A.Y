package com.friday.system;

/**
 * Focused automated verification for SystemMetricsService.
 * Validates that all hardware metric getters return safe, bounded percentage values [0.0, 100.0]
 * and handle repeated invocations without throwing exceptions.
 */
public class SystemMetricsServiceTest {

    public void testCpuUsagePercentageWithinBounds() {
        double cpu = SystemMetricsService.getCpuUsagePercentage();
        if (Double.isNaN(cpu) || Double.isInfinite(cpu) || cpu < 0.0 || cpu > 100.0) {
            throw new AssertionError("CPU percentage out of bounds [0, 100]: " + cpu);
        }
    }

    public void testRamUsagePercentageWithinBounds() {
        double ram = SystemMetricsService.getRamUsagePercentage();
        if (Double.isNaN(ram) || Double.isInfinite(ram) || ram < 0.0 || ram > 100.0) {
            throw new AssertionError("RAM percentage out of bounds [0, 100]: " + ram);
        }
    }

    public void testDiskUsagePercentageWithinBounds() {
        double disk = SystemMetricsService.getDiskUsagePercentage();
        if (Double.isNaN(disk) || Double.isInfinite(disk) || disk < 0.0 || disk > 100.0) {
            throw new AssertionError("Disk percentage out of bounds [0, 100]: " + disk);
        }
    }

    public void testGpuUsagePercentageWithinBounds() {
        double gpu = SystemMetricsService.getGpuUsagePercentage();
        if (Double.isNaN(gpu) || Double.isInfinite(gpu) || gpu < 0.0 || gpu > 100.0) {
            throw new AssertionError("GPU percentage out of bounds [0, 100]: " + gpu);
        }
    }

    public void testRepeatedReadingsConsistency() {
        for (int i = 0; i < 5; i++) {
            double cpu = SystemMetricsService.getCpuUsagePercentage();
            double ram = SystemMetricsService.getRamUsagePercentage();
            if (cpu < 0.0 || cpu > 100.0 || ram < 0.0 || ram > 100.0) {
                throw new AssertionError("Inconsistent readings on iteration " + i);
            }
        }
    }
}
