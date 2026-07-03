/*
 * Performance Monitor
 */
package simplegenomehub.util.performance;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Performance monitoring utility to track operations and identify bottlenecks
 * 
 * @author SimpleGenomeHub
 */
public class PerformanceMonitor {
    
    private static final Logger logger = Logger.getLogger(PerformanceMonitor.class.getName());
    
    // Track operation timings
    private static final ConcurrentHashMap<String, Long> operationStartTimes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, OperationStats> operationStats = new ConcurrentHashMap<>();
    
    /**
     * Statistics for an operation
     */
    public static class OperationStats {
        private long totalTime;
        private int count;
        private long minTime;
        private long maxTime;
        private long lastTime;
        
        public OperationStats() {
            this.totalTime = 0;
            this.count = 0;
            this.minTime = Long.MAX_VALUE;
            this.maxTime = 0;
            this.lastTime = 0;
        }
        
        public void addTime(long timeMs) {
            totalTime += timeMs;
            count++;
            minTime = Math.min(minTime, timeMs);
            maxTime = Math.max(maxTime, timeMs);
            lastTime = timeMs;
        }
        
        public double getAverageTime() {
            return count > 0 ? (double) totalTime / count : 0;
        }
        
        public String getSummary() {
            if (count == 0) return "No operations recorded";
            return String.format("Ops: %d, Avg: %.1fms, Min: %dms, Max: %dms, Last: %dms",
                count, getAverageTime(), minTime, maxTime, lastTime);
        }
        
        // Getters
        public long getTotalTime() { return totalTime; }
        public int getCount() { return count; }
        public long getMinTime() { return minTime == Long.MAX_VALUE ? 0 : minTime; }
        public long getMaxTime() { return maxTime; }
        public long getLastTime() { return lastTime; }
    }
    
    /**
     * Start timing an operation
     */
    public static void startOperation(String operationName) {
        operationStartTimes.put(operationName, System.currentTimeMillis());
    }
    
    /**
     * End timing an operation and record the result
     */
    public static long endOperation(String operationName) {
        Long startTime = operationStartTimes.remove(operationName);
        if (startTime == null) {
            logger.warning("No start time recorded for operation: " + operationName);
            return 0;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Record statistics
        operationStats.computeIfAbsent(operationName, k -> new OperationStats())
                     .addTime(duration);
        
        // Log slow operations
        if (duration > 5000) { // > 5 seconds
            logger.warning(String.format("Slow operation '%s' took %d ms", operationName, duration));
        } else if (duration > 1000) { // > 1 second
            logger.info(String.format("Operation '%s' took %d ms", operationName, duration));
        }
        
        return duration;
    }
    
    /**
     * Get statistics for an operation
     */
    public static OperationStats getOperationStats(String operationName) {
        return operationStats.get(operationName);
    }
    
    /**
     * Get memory usage information
     */
    public static String getMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory();
        
        return String.format("Memory - Used: %.1fMB, Free: %.1fMB, Total: %.1fMB, Max: %.1fMB",
            usedMemory / 1024.0 / 1024.0,
            freeMemory / 1024.0 / 1024.0,
            totalMemory / 1024.0 / 1024.0,
            maxMemory / 1024.0 / 1024.0);
    }
    
    /**
     * Force garbage collection and measure memory freed
     */
    public static long forceGarbageCollection() {
        Runtime runtime = Runtime.getRuntime();
        long beforeGC = runtime.totalMemory() - runtime.freeMemory();
        
        System.gc();
        Thread.yield(); // Give GC a chance
        
        try {
            Thread.sleep(100); // Wait a bit for GC
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long afterGC = runtime.totalMemory() - runtime.freeMemory();
        long freed = beforeGC - afterGC;
        
        logger.info(String.format("Garbage collection freed %.1fMB", freed / 1024.0 / 1024.0));
        return freed;
    }
    
    /**
     * Get comprehensive performance report
     */
    public static String getPerformanceReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("Performance Report\n");
        report.append("==================\n\n");
        
        // Memory statistics
        report.append("Memory Usage:\n");
        report.append(getMemoryStats()).append("\n\n");
        
        // Operation statistics
        if (operationStats.isEmpty()) {
            report.append("No operations recorded\n");
        } else {
            report.append("Operation Statistics:\n");
            report.append("--------------------\n");
            
            operationStats.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(
                    e2.getValue().getTotalTime(), 
                    e1.getValue().getTotalTime()))
                .forEach(entry -> {
                    report.append(String.format("%-30s : %s\n", 
                        entry.getKey(), entry.getValue().getSummary()));
                });
        }
        
        return report.toString();
    }
    
    /**
     * Clear all statistics
     */
    public static void clear() {
        operationStartTimes.clear();
        operationStats.clear();
        logger.info("Performance statistics cleared");
    }
    
    /**
     * Log current performance status
     */
    public static void logStatus() {
        logger.info("Performance Status: " + operationStats.size() + " operations tracked");
        logger.info(getMemoryStats());
    }
}