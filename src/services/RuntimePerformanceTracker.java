package services;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashMap;
import java.util.Map;

public class RuntimePerformanceTracker {

    private final String operation;
    private final long startWallNanos;
    private final long startCpuNanos;
    private final long startUsedMemoryBytes;
    private final long maxHeapBytes;
    private final ThreadMXBean threadBean;

    public RuntimePerformanceTracker(String operation) {
        this.operation = operation;
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.startWallNanos = System.nanoTime();
        this.startCpuNanos = currentCpuTime();
        this.startUsedMemoryBytes = usedMemoryBytes();
        this.maxHeapBytes = Runtime.getRuntime().maxMemory();
    }

    public void printSummary(Map<String, String> details) {
        long endWallNanos = System.nanoTime();
        long endCpuNanos = currentCpuTime();
        long endUsedMemoryBytes = usedMemoryBytes();

        System.out.println();
        System.out.println("=== Performance Summary: " + operation + " ===");
        System.out.printf("Execution Time (ms): %.3f%n", nanosToMillis(endWallNanos - startWallNanos));
        if (endCpuNanos >= 0 && startCpuNanos >= 0) {
            System.out.printf("CPU Time (ms): %.3f%n", nanosToMillis(endCpuNanos - startCpuNanos));
        } else {
            System.out.println("CPU Time (ms): unavailable");
        }
        System.out.printf("Memory Used Start (MB): %.3f%n", bytesToMb(startUsedMemoryBytes));
        System.out.printf("Memory Used End (MB): %.3f%n", bytesToMb(endUsedMemoryBytes));
        System.out.printf("Memory Delta (MB): %.3f%n", bytesToMb(endUsedMemoryBytes - startUsedMemoryBytes));
        System.out.printf("JVM Max Heap (MB): %.3f%n", bytesToMb(maxHeapBytes));
        System.out.println("GPU Usage: unavailable from current Java runtime");
        if (details != null) {
            for (Map.Entry<String, String> entry : details.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
        }
        System.out.println("==============================================");
        System.out.println();
    }

    public void printSummary(String key, String value) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put(key, value);
        printSummary(details);
    }

    private long currentCpuTime() {
        if (threadBean.isCurrentThreadCpuTimeSupported()) {
            try {
                if (!threadBean.isThreadCpuTimeEnabled()) threadBean.setThreadCpuTimeEnabled(true);
                return threadBean.getCurrentThreadCpuTime();
            } catch (UnsupportedOperationException ex) {
                return -1L;
            }
        }
        return -1L;
    }

    private long usedMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private double bytesToMb(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }
}
