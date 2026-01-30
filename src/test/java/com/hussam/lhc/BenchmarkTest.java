package com.hussam.lhc;

import com.hussam.lhc.database.DatabaseManager;
import com.hussam.lhc.ingestion.CsvParser;
import com.hussam.lhc.ingestion.PipelineManager;
import com.hussam.lhc.model.ParticleEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@Import(com.hussam.lhc.UnifiedTestConfig.class)
public class BenchmarkTest {

    private static final Logger LOGGER = Logger.getLogger(BenchmarkTest.class.getName());
    private static final Logger BENCHMARK_LOGGER = Logger.getLogger("BENCHMARK_LOGGER");

    static {
        try {
            FileHandler fileHandler = new FileHandler("benchmark_results.txt", true);
            fileHandler.setFormatter(new SimpleFormatter());
            BENCHMARK_LOGGER.addHandler(fileHandler);
            BENCHMARK_LOGGER.setUseParentHandlers(false);
        } catch (Exception e) {
            System.err.println("Failed to initialize benchmark logger: " + e.getMessage());
        }
    }

    private static final int BASELINE_EVENTS = 10_000;
    private static final int MULTI_2X2_EVENTS = 50_000;
    private static final int MULTI_4X4_EVENTS = 100_000;
    private static final int STRESS_TEST_EVENTS = 100_000;
    private static final int END_TO_END_EVENTS = 1_000_000;

    private List<BenchmarkResult> results = new ArrayList<>();

    @TempDir
    Path tempDir;

    private static final AtomicInteger maxQueueSizeObserved = new AtomicInteger(0);

    @BeforeAll
    static void setupBenchmark() {
        LOGGER.info("========================================");
        LOGGER.info("  LHC Event Processor - Benchmark Suite");
        LOGGER.info("========================================");
        BENCHMARK_LOGGER.info("LHC Event Processor - Benchmark Results");
        BENCHMARK_LOGGER.info("========================================");
        BENCHMARK_LOGGER.info("");
        BENCHMARK_LOGGER.info("Benchmark Metrics Explanation:");
        BENCHMARK_LOGGER.info("----------------------------------------");
        BENCHMARK_LOGGER.info("  Time (s)      : Total processing time in seconds");
        BENCHMARK_LOGGER.info("  Throughput    : Events processed per second");
        BENCHMARK_LOGGER.info("  Memory        : Heap memory used during benchmark");
        BENCHMARK_LOGGER.info("  Queue         : Maximum queue size reached (utilization %)");
        BENCHMARK_LOGGER.info("  DB Qry        : Average database query time in ms");
        BENCHMARK_LOGGER.info("");
    }

    @AfterAll
    static void teardownBenchmark() {
        LOGGER.info("");
        LOGGER.info("========================================");
        LOGGER.info("  Benchmark Suite Completed");
        LOGGER.info("========================================");
        LOGGER.info("");
        LOGGER.info("Results saved to: benchmark_results.txt");
    }

    @Test
    @Order(1)
    @DisplayName("Baseline: 1 producer, 1 consumer, 10,000 events")
    public void testBaselinePerformance() throws Exception {
        String testName = "Baseline (1x1)";
        int producerThreads = 1;
        int consumerThreads = 1;
        int queueCapacity = 5000;

        Path testFile = createTestFile(tempDir, BASELINE_EVENTS, "baseline_1x1.csv");

        BenchmarkResult result = runBenchmark(testName, testFile, producerThreads, consumerThreads, 
                queueCapacity, BASELINE_EVENTS);
        results.add(result);

        logResult(result);
        BENCHMARK_LOGGER.info(result.toFormattedString());

        Assertions.assertTrue(result.throughput > 1000,
                "Baseline throughput should be > 1000 events/sec");
        Assertions.assertTrue(result.memoryUsed < 500_000_000L,
                "Memory usage should be < 500MB");
    }

    @Test
    @Order(2)
    @DisplayName("Multi-threaded 2x2: 2 producers, 2 consumers, 50,000 events")
    public void testMultiThreaded2x2Performance() throws Exception {
        String testName = "Multi-threaded (2x2)";
        int producerThreads = 2;
        int consumerThreads = 2;
        int queueCapacity = 10000;

        Path testFile = createTestFile(tempDir, MULTI_2X2_EVENTS, "multithreaded_2x2.csv");

        BenchmarkResult result = runBenchmark(testName, testFile, producerThreads, consumerThreads, 
                queueCapacity, MULTI_2X2_EVENTS);
        results.add(result);

        logResult(result);
        BENCHMARK_LOGGER.info(result.toFormattedString());

        Assertions.assertTrue(result.throughput > 5000,
                "2x2 configuration should achieve > 5,000 events/sec");
        Assertions.assertTrue(result.memoryUsed < 1_000_000_000L,
                "Memory usage should be < 1GB");
    }

    @Test
    @Order(3)
    @DisplayName("Multi-threaded 4x4: 4 producers, 4 consumers, 100,000 events")
    public void testMultiThreaded4x4Performance() throws Exception {
        String testName = "Multi-threaded (4x4)";
        int producerThreads = 4;
        int consumerThreads = 4;
        int queueCapacity = 20000;

        Path testFile = createTestFile(tempDir, MULTI_4X4_EVENTS, "multithreaded_4x4.csv");

        BenchmarkResult result = runBenchmark(testName, testFile, producerThreads, consumerThreads, 
                queueCapacity, MULTI_4X4_EVENTS);
        results.add(result);

        logResult(result);
        BENCHMARK_LOGGER.info(result.toFormattedString());

        Assertions.assertTrue(result.throughput > 30000,
                "4x4 configuration should achieve > 30,000 events/sec");
        Assertions.assertTrue(result.memoryUsed < 2_000_000_000L,
                "Memory usage should be < 2GB");
    }

    @Test
    @Order(4)
    @DisplayName("Database Stress Test: Insert 100,000 high-energy events")
    public void testDatabaseStress() throws Exception {
        String testName = "Database Stress Test";
        BENCHMARK_LOGGER.info("");
        BENCHMARK_LOGGER.info("Running: " + testName);
        BENCHMARK_LOGGER.info("----------------------------------------");

        maxQueueSizeObserved.set(0);
        long startTime = System.nanoTime();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        QueueMonitor queueMonitor = null;
        ScheduledExecutorService monitorExecutor = null;

        try {
            com.hussam.lhc.UnifiedTestConfig.InMemoryTestDatabase testDbManager = new com.hussam.lhc.UnifiedTestConfig.InMemoryTestDatabase();
            PipelineManager manager = new PipelineManager(4, 4, 20000, testDbManager);
            Path testFile = createTestFile(tempDir, STRESS_TEST_EVENTS, "stress_test.csv");

            monitorExecutor = Executors.newSingleThreadScheduledExecutor();
            queueMonitor = new QueueMonitor(manager, maxQueueSizeObserved);
            monitorExecutor.scheduleAtFixedRate(queueMonitor, 100, 100, TimeUnit.MILLISECONDS);

            manager.start(java.util.List.of(testFile), new CsvParser());

            if (monitorExecutor != null) {
                monitorExecutor.shutdown();
                try {
                    monitorExecutor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            long duration = System.nanoTime() - startTime;
            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryUsed = endMemory - startMemory;

            double durationSeconds = duration / 1_000_000_000.0;
            double throughput = STRESS_TEST_EVENTS / durationSeconds;

            int maxQueueSize = maxQueueSizeObserved.get();
            double queueUtilization = (maxQueueSize * 100.0) / 20000;

            DatabaseManager dbManager = DatabaseManager.getInstance();
            long avgDbQueryTimeMs = measureAverageQueryTime(dbManager);

            BenchmarkResult result = new BenchmarkResult(
                    testName,
                    4, 4, 20000,
                    STRESS_TEST_EVENTS,
                    duration,
                    throughput,
                    startMemory,
                    endMemory,
                    memoryUsed,
                    maxQueueSize, queueUtilization, avgDbQueryTimeMs,
                    "PASSED"
            );

            results.add(result);
            logResult(result);
            BENCHMARK_LOGGER.info(result.toFormattedString());

            BENCHMARK_LOGGER.info("  Max Queue Size: " + maxQueueSize + " / 20000" +
                    " (" + String.format("%.1f%%", queueUtilization) + ")");
            BENCHMARK_LOGGER.info("  Avg DB Query Time: " + avgDbQueryTimeMs + " ms");
            BENCHMARK_LOGGER.info("");

            Assertions.assertTrue(throughput > 10000,
                    "Stress test should achieve > 10,000 events/sec");
            Assertions.assertTrue(result.memoryUsed < 2_000_000_000L,
                    "Memory usage should be < 2GB");

        } catch (OutOfMemoryError e) {
            if (monitorExecutor != null) {
                monitorExecutor.shutdownNow();
            }
            long duration = System.nanoTime() - startTime;
            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            BenchmarkResult result = new BenchmarkResult(
                    testName, 4, 4, 20000, STRESS_TEST_EVENTS,
                    duration, 0.0, startMemory, endMemory,
                    endMemory - startMemory,
                    maxQueueSizeObserved.get(), 0.0, 0,
                    "FAILED: OutOfMemoryError - " + e.getMessage());
            results.add(result);
            logResult(result);
            BENCHMARK_LOGGER.info(result.toFormattedString());
            throw e;
        } catch (Exception e) {
            if (monitorExecutor != null) {
                monitorExecutor.shutdownNow();
            }
            long duration = System.nanoTime() - startTime;
            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            BenchmarkResult result = new BenchmarkResult(
                    testName, 4, 4, 20000, STRESS_TEST_EVENTS,
                    duration, 0.0, startMemory, endMemory,
                    endMemory - startMemory,
                    maxQueueSizeObserved.get(), 0.0, 0,
                    "FAILED: " + e.getMessage());
            results.add(result);
            logResult(result);
            BENCHMARK_LOGGER.info(result.toFormattedString());
            throw e;
        }
    }

    @Test
    @Order(5)
    @DisplayName("End-to-End: Full pipeline with 1,000,000 events")
    public void testEndToEndPerformance() throws Exception {
        String testName = "End-to-End Pipeline";
        int producerThreads = 4;
        int consumerThreads = 4;
        int queueCapacity = 20000;

        Path testFile = createTestFile(tempDir, END_TO_END_EVENTS, "end_to_end.csv");

        BenchmarkResult result = runBenchmark(testName, testFile, producerThreads, consumerThreads, 
                queueCapacity, END_TO_END_EVENTS);
        results.add(result);

        logResult(result);
        BENCHMARK_LOGGER.info(result.toFormattedString());

        Assertions.assertTrue(result.throughput > 30000,
                "End-to-end should achieve > 30,000 events/sec");
        Assertions.assertTrue(result.memoryUsed < 3_000_000_000L,
                "Memory usage should be < 3GB");
    }

    @Test
    @Order(6)
    @DisplayName("Performance Comparison Summary")
    public void testPerformanceComparison() {
        LOGGER.info("");
        LOGGER.info("========================================");
        LOGGER.info("  Performance Comparison Summary");
        LOGGER.info("========================================");
        LOGGER.info("");
        
        printComparisonTable(results);

        BENCHMARK_LOGGER.info("");
        BENCHMARK_LOGGER.info("========================================");
        BENCHMARK_LOGGER.info("  Performance Comparison Summary");
        BENCHMARK_LOGGER.info("========================================");
        BENCHMARK_LOGGER.info("");
        printComparisonTable(results);
    }

    private Path createTestFile(Path dir, int eventCount, String filename) throws Exception {
        Path file = dir.resolve(filename);
        LOGGER.info("Creating test file: " + filename + " with " + eventCount + " events");

        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write("event_id,timestamp,energy_gev,particle_type,detected_at_tracker\n");

            for (int i = 0; i < eventCount; i++) {
                String eventId = java.util.UUID.randomUUID().toString();
                String timestamp = Instant.now().minusSeconds(eventCount - i).toString();
                double energy = 0.1 + Math.random() * 125.0;
                String particleType = java.util.List.of("ELECTRON", "MUON", "PROTON").get((int)(Math.random() * 3));
                boolean detectedAtTracker = Math.random() > 0.5;

                writer.write(String.format("%s,%s,%.2f,%s,%b%n",
                        eventId, timestamp, energy, particleType, detectedAtTracker));

                if ((i + 1) % 10000 == 0) {
                    LOGGER.info("Generated " + (i + 1) + " of " + eventCount + " events");
                }
            }
        }

        LOGGER.info("Test file created: " + file);
        LOGGER.info("File size: " + (Files.size(file) / 1024) + " KB");
        return file;
    }

    private BenchmarkResult runBenchmark(String testName, Path testFile,
                                      int producerThreads, int consumerThreads,
                                      int queueCapacity, int eventCount) throws Exception {
        BENCHMARK_LOGGER.info("");
        BENCHMARK_LOGGER.info("Running: " + testName);
        BENCHMARK_LOGGER.info("----------------------------------------");
        BENCHMARK_LOGGER.info("Configuration:");
        BENCHMARK_LOGGER.info("  Producer Threads: " + producerThreads);
        BENCHMARK_LOGGER.info("  Consumer Threads: " + consumerThreads);
        BENCHMARK_LOGGER.info("  Queue Capacity: " + queueCapacity);
        BENCHMARK_LOGGER.info("  Event Count: " + eventCount);
        BENCHMARK_LOGGER.info("");

        maxQueueSizeObserved.set(0);

        long startTime = System.nanoTime();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        QueueMonitor queueMonitor = null;
        ScheduledExecutorService monitorExecutor = null;

        try {
            com.hussam.lhc.UnifiedTestConfig.InMemoryTestDatabase testDbManager = new com.hussam.lhc.UnifiedTestConfig.InMemoryTestDatabase();
            PipelineManager manager = new PipelineManager(producerThreads, consumerThreads, queueCapacity, testDbManager);

            monitorExecutor = Executors.newSingleThreadScheduledExecutor();
            queueMonitor = new QueueMonitor(manager, maxQueueSizeObserved);
            monitorExecutor.scheduleAtFixedRate(queueMonitor, 100, 100, TimeUnit.MILLISECONDS);

            manager.start(java.util.List.of(testFile), new CsvParser());

            if (monitorExecutor != null) {
                monitorExecutor.shutdown();
                try {
                    monitorExecutor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            long duration = System.nanoTime() - startTime;
            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryUsed = endMemory - startMemory;

            double durationSeconds = duration / 1_000_000_000.0;
            double throughput = eventCount / durationSeconds;

            int maxQueueSize = maxQueueSizeObserved.get();
            double queueUtilization = (maxQueueSize * 100.0) / queueCapacity;

            DatabaseManager dbManager = DatabaseManager.getInstance();
            long avgDbQueryTimeMs = measureAverageQueryTime(dbManager);

            BenchmarkResult result = new BenchmarkResult(
                    testName,
                    producerThreads, consumerThreads, queueCapacity,
                    eventCount, duration, throughput,
                    startMemory, endMemory, memoryUsed,
                    maxQueueSize, queueUtilization,
                    avgDbQueryTimeMs,
                    "PASSED"
            );

            BENCHMARK_LOGGER.info("  Max Queue Size: " + maxQueueSize + " / " + queueCapacity +
                    " (" + String.format("%.1f%%", queueUtilization) + ")");
            BENCHMARK_LOGGER.info("  Avg DB Query Time: " + avgDbQueryTimeMs + " ms");
            BENCHMARK_LOGGER.info("");

            return result;

        } catch (OutOfMemoryError e) {
            if (monitorExecutor != null) {
                monitorExecutor.shutdownNow();
            }
            long duration = System.nanoTime() - startTime;
            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            BenchmarkResult result = new BenchmarkResult(
                    testName, producerThreads, consumerThreads, queueCapacity,
                    eventCount, duration, 0.0,
                    startMemory, endMemory,
                    endMemory - startMemory,
                    maxQueueSizeObserved.get(), 0.0, 0,
                    "FAILED: OutOfMemoryError - " + e.getMessage());
            return result;
        } catch (Exception e) {
            if (monitorExecutor != null) {
                monitorExecutor.shutdownNow();
            }
            long duration = System.nanoTime() - startTime;
            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            BenchmarkResult result = new BenchmarkResult(
                    testName, producerThreads, consumerThreads, queueCapacity,
                    eventCount, duration, 0.0,
                    startMemory, endMemory,
                    endMemory - startMemory,
                    maxQueueSizeObserved.get(), 0.0, 0,
                    "FAILED: " + e.getMessage());
            return result;
        }
    }

    private long measureAverageQueryTime(DatabaseManager dbManager) {
        int queryCount = 5;
        long totalTime = 0;

        for (int i = 0; i < queryCount; i++) {
            long queryStart = System.nanoTime();
            try {
                List<ParticleEvent> events = dbManager.queryHighEnergyEvents(10, 50.0);
                long queryTime = System.nanoTime() - queryStart;
                totalTime += queryTime;
            } catch (Exception e) {
                LOGGER.warning("Query failed: " + e.getMessage());
            }
        }

        return (totalTime / queryCount) / 1_000_000;
    }

    private void logResult(BenchmarkResult result) {
        LOGGER.info("");
        LOGGER.info("Benchmark Result: " + result.testName);
        LOGGER.info("----------------------------------------");
        LOGGER.info("  Duration: " + formatDuration(result.durationNanos));
        LOGGER.info("  Throughput: " + String.format("%.0f", result.throughput) + " events/sec");
        LOGGER.info("  Memory Used: " + (result.memoryUsed / 1024 / 1024) + " MB");
        LOGGER.info("  Max Queue Size: " + result.maxQueueSize + " / " + result.queueCapacity +
                " (" + String.format("%.1f%%", result.queueUtilization) + ")");
        LOGGER.info("  Avg DB Query Time: " + result.avgDbQueryTimeMs + " ms");
        LOGGER.info("  Status: " + result.status);
    }

    private void printComparisonTable(List<BenchmarkResult> results) {
        BENCHMARK_LOGGER.info("");
        BENCHMARK_LOGGER.info("+--------------------+----------+----------+----------+----------+----------+---------+--------+");
        BENCHMARK_LOGGER.info("| Test Name          | Events   | Threads  | Time (s) | Throughput| Memory  | Queue   | DB Qry |");
        BENCHMARK_LOGGER.info("+--------------------+----------+----------+----------+----------+----------+---------+--------+");

        for (BenchmarkResult result : results) {
            String name = result.testName.length() > 18 ?
                    result.testName.substring(0, 18) : result.testName;
            BENCHMARK_LOGGER.info(String.format("| %-19s | %8d | %s | %8.2f | %8.0f | %7.1fMB | %5.1f%% | %4dms |",
                    name, result.eventCount,
                    result.producerThreads + "x" + result.consumerThreads,
                    result.durationNanos / 1_000_000_000.0,
                    result.throughput,
                    result.memoryUsed / 1024.0 / 1024.0,
                    result.queueUtilization,
                    result.avgDbQueryTimeMs));
        }

        BENCHMARK_LOGGER.info("+--------------------+----------+----------+----------+----------+----------+---------+--------+");
        BENCHMARK_LOGGER.info("");

        if (results.size() >= 3) {
            double baselineThroughput = results.get(0).throughput;
            double lastThroughput = results.get(results.size() - 1).throughput;
            double speedup = lastThroughput / baselineThroughput;

            BENCHMARK_LOGGER.info("Performance Analysis:");
            BENCHMARK_LOGGER.info("----------------------------------------");
            BENCHMARK_LOGGER.info("  Baseline Throughput: " + String.format("%.0f", baselineThroughput) + " events/sec");
            BENCHMARK_LOGGER.info("  Best Throughput: " + String.format("%.0f", lastThroughput) + " events/sec");
            BENCHMARK_LOGGER.info("  Speedup: " + String.format("%.2fx", speedup));

            long baselineMemory = results.get(0).memoryUsed;
            long peakMemory = results.stream().mapToLong(r -> r.memoryUsed).max().orElse(0);
            BENCHMARK_LOGGER.info("  Memory Efficiency: " + String.format("%.2f MB/1K events",
                    (peakMemory / 1024.0 / 1024.0) / (results.get(results.size() - 1).eventCount / 1000.0)));

            double avgQueueUtil = results.stream()
                    .mapToDouble(r -> r.queueUtilization)
                    .average()
                    .orElse(0.0);
            BENCHMARK_LOGGER.info("  Avg Queue Utilization: " + String.format("%.1f%%", avgQueueUtil));

            long avgDbQueryTime = results.stream()
                    .mapToLong(r -> r.avgDbQueryTimeMs)
                    .filter(t -> t > 0)
                    .sum() / results.stream().mapToLong(r -> r.avgDbQueryTimeMs).filter(t -> t > 0).count();
            BENCHMARK_LOGGER.info("  Avg DB Query Time: " + avgDbQueryTime + " ms");
            BENCHMARK_LOGGER.info("");
        }
    }

    private String formatDuration(long nanos) {
        double seconds = nanos / 1_000_000_000.0;
        
        if (seconds < 60) {
            return String.format("%.2fs", seconds);
        } else if (seconds < 3600) {
            long minutes = (long) (seconds / 60);
            double secs = seconds % 60;
            return String.format("%dm %.1fs", minutes, secs);
        } else {
            long hours = (long) (seconds / 3600);
            long minutes = (long) ((seconds % 3600) / 60);
            double secs = seconds % 60;
            return String.format("%dh %dm %.1fs", hours, minutes, secs);
        }
    }

    private static class BenchmarkResult {
        final String testName;
        final int producerThreads;
        final int consumerThreads;
        final int queueCapacity;
        final int eventCount;
        final long durationNanos;
        final double throughput;
        final long startMemory;
        final long endMemory;
        final long memoryUsed;
        final int maxQueueSize;
        final double queueUtilization;
        final long avgDbQueryTimeMs;
        final String status;

        BenchmarkResult(String testName, int producerThreads, int consumerThreads,
                      int queueCapacity, int eventCount, long durationNanos,
                      double throughput, long startMemory, long endMemory,
                      long memoryUsed, int maxQueueSize, double queueUtilization,
                      long avgDbQueryTimeMs, String status) {
            this.testName = testName;
            this.producerThreads = producerThreads;
            this.consumerThreads = consumerThreads;
            this.queueCapacity = queueCapacity;
            this.eventCount = eventCount;
            this.durationNanos = durationNanos;
            this.throughput = throughput;
            this.startMemory = startMemory;
            this.endMemory = endMemory;
            this.memoryUsed = memoryUsed;
            this.maxQueueSize = maxQueueSize;
            this.queueUtilization = queueUtilization;
            this.avgDbQueryTimeMs = avgDbQueryTimeMs;
            this.status = status;
        }

        String toFormattedString() {
            return String.format("%-19s | %8d | %s | %8.2f | %8.0f | %7.1fMB | %5.1f%% | %4dms | %s",
                    testName.length() > 18 ? testName.substring(0, 18) : testName,
                    eventCount, producerThreads + "x" + consumerThreads,
                    durationNanos / 1_000_000_000.0, throughput,
                    memoryUsed / 1024.0 / 1024.0, queueUtilization, avgDbQueryTimeMs, status);
        }
    }

    private static class QueueMonitor implements Runnable {
        private final PipelineManager manager;
        private final AtomicInteger maxQueueSize;
        private volatile boolean running = true;

        QueueMonitor(PipelineManager manager, AtomicInteger maxQueueSize) {
            this.manager = manager;
            this.maxQueueSize = maxQueueSize;
        }

        @Override
        public void run() {
            if (!running) return;

            int currentSize = manager.getCurrentQueueSize();
            int currentMax = maxQueueSize.get();
            if (currentSize > currentMax) {
                maxQueueSize.compareAndSet(currentMax, currentSize);
            }
        }

        public void stop() {
            running = false;
        }
    }
}
