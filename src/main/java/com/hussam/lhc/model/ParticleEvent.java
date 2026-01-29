package com.hussam.lhc.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single particle collision event detected by the LHC.
 * <p>
 * This class is immutable for thread-safety in our concurrent pipeline.
 * All fields are validated at construction time to ensure data integrity.
 * </p>
 */
public class ParticleEvent {
    private final UUID eventId;
    private final Instant timestamp;
    private final double energyGev;
    private final ParticleType particleType;
    private final boolean detectedAtTracker;

    /**
     * Constructs a new ParticleEvent with validated parameters.
     *
     * @param eventId unique identifier for this collision event
     * @param timestamp when the event was detected
     * @param energyGev energy level in GeV (-1.0 is allowed for poison pills)
     * @param particleType type of particle detected
     * @param detectedAtTracker whether particle hit the tracker detector
     * @throws IllegalArgumentException if energyGev is negative (except -1.0)
     * @throws NullPointerException if any required field is null
     */
    public ParticleEvent(UUID eventId, Instant timestamp, double energyGev, ParticleType particleType, boolean detectedAtTracker) {
        this.eventId = Objects.requireNonNull(eventId, "eventId cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp cannot be null");
        if (energyGev < 0 && energyGev != -1.0) {
            throw new IllegalArgumentException("energyGev cannot be negative");
        }
        this.energyGev = energyGev;
        this.particleType = Objects.requireNonNull(particleType, "particleType cannot be null");
        this.detectedAtTracker = detectedAtTracker;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public double getEnergyGev() {
        return energyGev;
    }

    public ParticleType getParticleType() {
        return particleType;
    }

    public boolean isDetectedAtTracker() {
        return detectedAtTracker;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParticleEvent that = (ParticleEvent) o;
        return Double.compare(energyGev, that.energyGev) == 0 &&
                detectedAtTracker == that.detectedAtTracker &&
                Objects.equals(eventId, that.eventId) &&
                Objects.equals(timestamp, that.timestamp) &&
                particleType == that.particleType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, timestamp, energyGev, particleType, detectedAtTracker);
    }

    @Override
    public String toString() {
        return "ParticleEvent{" +
                "eventId=" + eventId +
                ", timestamp=" + timestamp +
                ", energyGev=" + energyGev +
                ", particleType=" + particleType +
                ", detectedAtTracker=" + detectedAtTracker +
                '}';
    }
}