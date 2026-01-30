package com.hussam.lhc.ingestion;

import com.hussam.lhc.database.DatabaseManager;
import com.hussam.lhc.model.ParticleEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrates the producer-consumer pipeline for processing particle events.
 * <p>
 * Coordinates CSV producers and filtering consumers using thread pools.
 * Uses bounded queue for backpressure when producers outpace consumers.
 * </p>
 */
public class PipelineManager {
    private static final Logger LOGGER = Logger.getLogger(PipelineManager.class.getName());
    // Bounded queue prevents memory overflow if producers outpace consumers.
    // Capacity of 10,000 events balances memory (~1MB) with throughput.
    private static final int DEFAULT_QUEUE_CAPACITY = 10000;
    // Producer count matches CPU cores (I/O bound) - reads from disk.
    private static final int DEFAULT_PRODUCER_THREADS = 4;
    // Consumer count is doubled (CPU bound) - filters and processes events.
    private static final int DEFAULT_CONSUMER_THREADS = 4;

    private final int producerThreads;
    private final int consumerThreads;
    private final int queueCapacity;

    private BlockingQueue<ParticleEvent> eventQueue;
    private ExecutorService producerPool;
    private ExecutorService consumerPool;
    private PoisonPill poisonPill;
    private AtomicLong eventCounter;
    private AtomicLong highEnergyCounter;
    private AtomicLong totalConsumed;
    private DatabaseManager dbManager;
    private boolean isRunning;

    /**
     * Constructs a PipelineManager with the specified configuration.
     *
     * @param producerThreads the number of producer threads
     * @param consumerThreads the number of consumer threads
     * @param queueCapacity the capacity of the event queue
     */
    public PipelineManager(int producerThreads, int consumerThreads, int queueCapacity) {
        this.producerThreads = producerThreads;
        this.consumerThreads = consumerThreads;
        this.queueCapacity = queueCapacity;
        this.isRunning = false;
    }

    /**
     * Constructs a PipelineManager with the specified configuration and custom database manager.
     *
     * @param producerThreads the number of producer threads
     * @param consumerThreads the number of consumer threads
     * @param queueCapacity the capacity of the event queue
     * @param dbManager the database manager to use
     */
    public PipelineManager(int producerThreads, int consumerThreads, int queueCapacity, DatabaseManager dbManager) {
        this.producerThreads = producerThreads;
        this.consumerThreads = consumerThreads;
        this.queueCapacity = queueCapacity;
        this.dbManager = dbManager;
        this.isRunning = false;
    }

    /**
     * Constructs a PipelineManager with default configuration.
     */
    public PipelineManager() {
        this(DEFAULT_PRODUCER_THREADS, DEFAULT_CONSUMER_THREADS, DEFAULT_QUEUE_CAPACITY);
    }

    /**
     * Starts the pipeline processing for the given CSV files.
     * <p>
     * This method creates producer threads for each file and consumer threads
     * to process events. It blocks until all processing is complete.
     * </p>
     *
     * @param csvFiles the list of CSV files to process
     * @param parser the data parser to use for reading files
     */
    public void start(List<Path> csvFiles, DataParser parser) {
        if (isRunning) {
            LOGGER.warning("Pipeline is already running");
            return;
        }

        LOGGER.info(String.format("Starting pipeline with %d producer(s) and %d consumer(s), queue capacity: %d",
                producerThreads, consumerThreads, queueCapacity));

        eventCounter = new AtomicLong(0);
        highEnergyCounter = new AtomicLong(0);
        totalConsumed = new AtomicLong(0);
        poisonPill = new PoisonPill();
        // Use ArrayBlockingQueue for bounded capacity and thread-safe operations.
        eventQueue = new ArrayBlockingQueue<>(queueCapacity);
        if (dbManager == null) {
            dbManager = DatabaseManager.getInstance();
        }

        // Create fixed-size thread pools to control resource usage.
        // Non-daemon threads ensure clean shutdown before JVM exit.
        producerPool = Executors.newFixedThreadPool(producerThreads,
                r -> {
                    Thread t = new Thread(r, "Producer-" + threadCounter());
                    t.setDaemon(false);
                    return t;
                });

        consumerPool = Executors.newFixedThreadPool(consumerThreads,
                r -> {
                    Thread t = new Thread(r, "Consumer-" + threadCounter());
                    t.setDaemon(false);
                    return t;
                });

        long startTime = System.currentTimeMillis();

        try {
            for (int i = 0; i < consumerThreads; i++) {
                EventConsumer consumer = new EventConsumer(eventQueue, highEnergyCounter, totalConsumed, i, poisonPill, dbManager);
                consumerPool.submit(consumer);
            }

            for (Path file : csvFiles) {
                CsvProducer producer = new CsvProducer(file, parser, eventQueue, eventCounter);
                producerPool.submit(producer);
            }

            isRunning = true;
            LOGGER.info("Pipeline started successfully");

            // Wait for all producers to finish reading files before stopping consumers.
            producerPool.shutdown();
            producerPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            // Send one poison pill per consumer to signal that no more events are coming.
            // This ensures each consumer exits cleanly even if queue is empty.
            LOGGER.info("All producers completed, sending poison pills to consumers");
            for (int i = 0; i < consumerThreads; i++) {
                eventQueue.put(poisonPill);
            }
            poisonPill.signalTermination();

            consumerPool.shutdown();
            consumerPool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            long elapsed = System.currentTimeMillis() - startTime;

            LOGGER.info(String.format("Pipeline completed successfully in %d ms", elapsed));
            LOGGER.info(String.format("Statistics: Total events: %d, High-energy events: %d (%.2f%%), Processed: %d",
                    eventCounter.get(), highEnergyCounter.get(),
                    eventCounter.get() > 0 ? (highEnergyCounter.get() * 100.0 / eventCounter.get()) : 0, 
                    totalConsumed.get()));
            LOGGER.info(String.format("Throughput: %.0f events/second", (eventCounter.get() * 1000.0) / elapsed));

            DatabaseManager.DatabaseStatistics dbStats = dbManager.getStatistics();
            LOGGER.info("Database Statistics: " + dbStats.toString());

        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Pipeline interrupted during execution", e);
            Thread.currentThread().interrupt();
            shutdownNow();
        } finally {
            if (dbManager != null) {
                dbManager.shutdown();
            }
            isRunning = false;
        }
    }

    /**
     * Immediately shuts down all producer and consumer threads.
     * <p>
     * This method attempts to gracefully shutdown the thread pools within 10 seconds,
     * then forcibly interrupts any remaining threads.
     * </p>
     */
    public void shutdownNow() {
        LOGGER.warning("Initiating immediate shutdown");

        if (producerPool != null && !producerPool.isShutdown()) {
            producerPool.shutdownNow();
            try {
                if (!producerPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOGGER.warning("Producer pool did not terminate in time");
                }
            } catch (InterruptedException e) {
                LOGGER.log(Level.SEVERE, "Interrupted while waiting for producer pool shutdown", e);
                Thread.currentThread().interrupt();
            }
        }

        if (consumerPool != null && !consumerPool.isShutdown()) {
            consumerPool.shutdownNow();
            try {
                if (!consumerPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOGGER.warning("Consumer pool did not terminate in time");
                }
            } catch (InterruptedException e) {
                LOGGER.log(Level.SEVERE, "Interrupted while waiting for consumer pool shutdown", e);
                Thread.currentThread().interrupt();
            }
        }

        if (poisonPill != null) {
            poisonPill.signalTermination();
        }

        if (dbManager != null) {
            dbManager.shutdown();
        }

        isRunning = false;
        LOGGER.info("Immediate shutdown completed");
    }

    /**
     * Returns the current number of events in the queue.
     *
     * @return the current queue size, or 0 if the pipeline is not started
     */
    public int getCurrentQueueSize() {
        return eventQueue != null ? eventQueue.size() : 0;
    }

    /**
     * Returns the remaining capacity of the event queue.
     *
     * @return the remaining queue capacity, or 0 if the pipeline is not started
     */
    public int getRemainingCapacity() {
        return eventQueue != null ? eventQueue.remainingCapacity() : 0;
    }

    /**
     * Returns the total number of events processed so far.
     *
     * @return the total processed count
     */
    public long getProcessedCount() {
        return totalConsumed.get();
    }

    /**
     * Returns the total number of high-energy events processed.
     *
     * @return the high-energy event count
     */
    public long getHighEnergyCount() {
        return highEnergyCounter.get();
    }

    /**
     * Indicates whether the pipeline is currently running.
     *
     * @return true if the pipeline is running, false otherwise
     */
    public boolean isRunning() {
        return isRunning;
    }

    private static int threadCounter = 0;
    private synchronized static int threadCounter() {
        return threadCounter++;
    }

    /**
     * Main entry point for running the pipeline manager from the command line.
     *
     * @param args command line arguments, where each argument should be a path to a CSV file
     */
    public static void main(String[] args) {
        LOGGER.info("LHC Event Processor - Starting Pipeline Manager");

        List<Path> testFiles = new ArrayList<>();
        for (String arg : args) {
            testFiles.add(Path.of(arg));
        }

        if (testFiles.isEmpty()) {
            LOGGER.warning("No CSV files provided. Usage: java PipelineManager <file1.csv> <file2.csv> ...");
            return;
        }

        PipelineManager manager = new PipelineManager(2, 2, 5000);
        manager.start(testFiles, new CsvParser());
    }
}
