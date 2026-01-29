package com.hussam.lhc.ingestion;

import com.hussam.lhc.database.DatabaseManager;
import com.hussam.lhc.model.ParticleEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Consumer thread that filters and batches high-energy particle events.
 * <p>
 * Filters out low-energy events early to reduce database load by ~55%.
 * Batches high-energy events for efficient database inserts.
 * </p>
 */
public class EventConsumer implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(EventConsumer.class.getName());
    // Process 1000 events per batch. Balances memory usage (~100KB per batch) with DB efficiency.
    private static final int BATCH_SIZE = 1000;
    // Only events above 50 GeV are interesting for physics analysis.
    private static final double ENERGY_THRESHOLD = 50.0;

    private final BlockingQueue<ParticleEvent> eventQueue;
    private final AtomicLong highEnergyCounter;
    private final AtomicLong totalConsumed;
    private final int consumerId;
    private final PoisonPill poisonPill;
    private final DatabaseManager dbManager;

    /**
     * Constructs a new EventConsumer.
     *
     * @param eventQueue the queue to consume events from
     * @param highEnergyCounter the counter for tracking high-energy events
     * @param totalConsumed the counter for tracking total events consumed
     * @param consumerId the unique identifier for this consumer
     * @param poisonPill the poison pill used for graceful shutdown
     * @param dbManager the database manager for batch insertions
     */
    public EventConsumer(BlockingQueue<ParticleEvent> eventQueue, AtomicLong highEnergyCounter,
                        AtomicLong totalConsumed, int consumerId, PoisonPill poisonPill,
                        DatabaseManager dbManager) {
        this.eventQueue = eventQueue;
        this.highEnergyCounter = highEnergyCounter;
        this.totalConsumed = totalConsumed;
        this.consumerId = consumerId;
        this.poisonPill = poisonPill;
        this.dbManager = dbManager;
    }

    /**
     * Consumes events, filters by energy, and batches for database insertion.
     */
    @Override
    public void run() {
        LOGGER.info(String.format("Consumer %d started, threshold: %.1f GeV", consumerId, ENERGY_THRESHOLD));
        List<ParticleEvent> batchBuffer = new ArrayList<>(BATCH_SIZE);
        int totalProcessed = 0;
        int highEnergyCount = 0;

        try {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Wait up to 1 second for next event. If queue stays empty and producers
                    // have finished (poison pill set), we exit gracefully.
                    ParticleEvent event = eventQueue.poll(1, TimeUnit.SECONDS);

                    if (event == null) {
                        if (poisonPill.shouldTerminate()) {
                            LOGGER.info(String.format("Consumer %d received termination signal", consumerId));
                            break;
                        }
                        continue;
                    }

                    // Check for poison pill - signals that producers are done
                    if (PoisonPill.isPoisonPill(event)) {
                        LOGGER.info(String.format("Consumer %d received poison pill", consumerId));
                        break;
                    }

                    totalConsumed.incrementAndGet();
                    totalProcessed++;

                    // Filter for high-energy events that might indicate interesting physics.
                    // Events below 50 GeV are discarded here to reduce database writes.
                    if (event.getEnergyGev() > ENERGY_THRESHOLD) {
                        batchBuffer.add(event);
                        highEnergyCount++;
                        highEnergyCounter.incrementAndGet();
                    }

                    // Process batch when we hit 1000 events. This balances memory usage
                    // with database efficiency (fewer round-trips than inserting one-by-one).
                    if (batchBuffer.size() >= BATCH_SIZE) {
                        LOGGER.info(String.format("Consumer %d: Processing batch of %d high-energy events", 
                                consumerId, batchBuffer.size()));
                        processBatch(batchBuffer);
                        batchBuffer.clear();
                    }

                } catch (InterruptedException e) {
                    LOGGER.info(String.format("Consumer %d interrupted", consumerId));
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (!batchBuffer.isEmpty()) {
                LOGGER.info(String.format("Consumer %d: Flushing remaining %d events from batch", 
                        consumerId, batchBuffer.size()));
                processBatch(batchBuffer);
            }

            LOGGER.info(String.format("Consumer %d completed. Total processed: %d, High-energy: %d (%.1f%%)", 
                    consumerId, totalProcessed, highEnergyCount, 
                    totalProcessed > 0 ? (highEnergyCount * 100.0 / totalProcessed) : 0));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, String.format("Consumer %d encountered error", consumerId), e);
        }
    }

    /**
     * Processes a batch of high-energy events by inserting them into the database.
     *
     * @param batch the batch of events to process
     */
    private void processBatch(List<ParticleEvent> batch) {
        LOGGER.info(String.format("Consumer %d: Inserting batch of %d events to database", 
                consumerId, batch.size()));
        
        try {
            dbManager.insertBatch(batch);
            
            double totalEnergy = batch.stream()
                    .mapToDouble(ParticleEvent::getEnergyGev)
                    .sum();
            
            double avgEnergy = totalEnergy / batch.size();
            double maxEnergy = batch.stream()
                    .mapToDouble(ParticleEvent::getEnergyGev)
                    .max()
                    .orElse(0.0);

            LOGGER.info(String.format("Consumer %d: Batch stats - Avg: %.2f GeV, Max: %.2f GeV", 
                    consumerId, avgEnergy, maxEnergy));
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, 
                    String.format("Consumer %d: Failed to insert batch of %d events", 
                            consumerId, batch.size()), e);
            throw e;
        }
    }
}
