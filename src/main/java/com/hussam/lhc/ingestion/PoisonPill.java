package com.hussam.lhc.ingestion;

import com.hussam.lhc.model.ParticleEvent;
import com.hussam.lhc.model.ParticleType;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Special ParticleEvent that signals consumers to exit gracefully.
 * <p>
 * Implements the Poison Pill pattern for clean shutdown of consumer threads.
 * Consumers finish their current batch and exit when they receive a poison pill.
 * The atomic flag handles edge cases where queue empties before pill arrives.
 * </p>
 */
public class PoisonPill extends ParticleEvent {
    private static final UUID POISON_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final Instant POISON_TIMESTAMP = Instant.EPOCH;
    // Shared atomic flag ensures all consumers exit even if queue empties unexpectedly.
    private final AtomicBoolean terminateFlag;

    /**
     * Constructs a new PoisonPill with a predefined UUID and timestamp.
     * <p>
     * The poison pill uses energy value -1.0 to distinguish it from normal events.
     * </p>
     */
    public PoisonPill() {
        super(POISON_UUID, POISON_TIMESTAMP, -1.0, ParticleType.ELECTRON, false);
        this.terminateFlag = new AtomicBoolean(false);
    }

    /**
     * Signals that consumers should terminate after processing current work.
     */
    public void signalTermination() {
        terminateFlag.set(true);
    }

    /**
     * Checks whether termination has been signaled.
     *
     * @return true if termination should occur, false otherwise
     */
    public boolean shouldTerminate() {
        return terminateFlag.get();
    }

    /**
     * Determines whether a ParticleEvent is a poison pill.
     *
     * @param event the event to check
     * @return true if the event is a poison pill, false otherwise
     */
    public static boolean isPoisonPill(ParticleEvent event) {
        return event != null &&
               POISON_UUID.equals(event.getEventId()) &&
               POISON_TIMESTAMP.equals(event.getTimestamp()) &&
               event.getEnergyGev() < 0;
    }
}
