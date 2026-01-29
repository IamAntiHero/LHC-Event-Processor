package com.hussam.lhc.ingestion;

import com.hussam.lhc.model.ParticleEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Producer thread that streams CSV files and publishes events to a queue.
 * <p>
 * Uses streaming I/O to handle large files without loading everything into memory.
 * Backpressure handling prevents overwhelming consumers when producers run fast.
 * </p>
 */
public class CsvProducer implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(CsvProducer.class.getName());
    // Log progress every 10 seconds - frequent enough for monitoring, not too noisy
    private static final long LOG_INTERVAL = 10000;

    private final Path csvFile;
    private final DataParser<ParticleEvent> parser;
    private final BlockingQueue<ParticleEvent> eventQueue;
    private final AtomicLong eventCounter;

    /**
     * Constructs a new CsvProducer.
     *
     * @param csvFile the CSV file to read
     * @param parser the parser to use for converting CSV lines to ParticleEvent objects
     * @param eventQueue the queue to publish parsed events to
     * @param eventCounter the counter for tracking total events processed
     */
    public CsvProducer(Path csvFile, DataParser<ParticleEvent> parser, BlockingQueue<ParticleEvent> eventQueue, AtomicLong eventCounter) {
        this.csvFile = csvFile;
        this.parser = parser;
        this.eventQueue = eventQueue;
        this.eventCounter = eventCounter;
    }

    /**
     * Streams CSV file and publishes events to the queue.
     */
    @Override
    public void run() {
        LOGGER.info(String.format("Starting producer for file: %s", csvFile.getFileName()));
        long startTime = System.currentTimeMillis();
        long lastLogTime = startTime;

        // Stream file line-by-line to avoid loading entire CSV into memory.
        // A 1GB file would cause OutOfMemoryError if loaded all at once.
        try (BufferedReader reader = Files.newBufferedReader(csvFile)) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                lineNumber++;

                try {
                    if (lineNumber == 1 && line.startsWith("event_id")) {
                        LOGGER.info("Skipping header line");
                        continue;
                    }

                    ParticleEvent event = parser.parse(line);

                    // Try non-blocking put first. If queue is full, retry with blocking put.
                    // This provides natural backpressure when consumers can't keep up.
                    boolean offered = eventQueue.offer(event, 1, TimeUnit.SECONDS);
                    if (!offered) {
                        LOGGER.warning(String.format("Queue full, retrying to put event %d", lineNumber));
                        eventQueue.put(event);
                    }

                    long count = eventCounter.incrementAndGet();
                    long currentTime = System.currentTimeMillis();

                    if (currentTime - lastLogTime >= LOG_INTERVAL) {
                        long elapsed = currentTime - startTime;
                        double eventsPerSec = (count * 1000.0) / elapsed;
                        LOGGER.info(String.format("Processed %,d events (%.0f events/sec)", count, eventsPerSec));
                        lastLogTime = currentTime;
                    }

                } catch (InterruptedException e) {
                    LOGGER.info("Producer interrupted, shutting down gracefully");
                    Thread.currentThread().interrupt();
                    break;
                } catch (ParseException e) {
                    LOGGER.log(Level.WARNING, String.format("Failed to parse line %d: %s", lineNumber, line), e);
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.info(String.format("Producer completed for file %s. Processed %,d events in %,d ms (%.0f events/sec)",
                    csvFile.getFileName(), eventCounter.get(), elapsed, (eventCounter.get() * 1000.0) / elapsed));

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, String.format("Failed to read file: %s", csvFile), e);
        }
    }
}
