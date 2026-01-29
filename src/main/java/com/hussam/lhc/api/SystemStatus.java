package com.hussam.lhc.api;

/**
 * Immutable snapshot of the LHC Event Processor system status.
 * <p>
 * Contains processing state, queue depth, throughput, and uptime metrics.
 * Builder pattern allows flexible construction with optional fields.
 * </p>
 */
public class SystemStatus {
    private final String status;
    private final int queueSize;
    private final long processedCount;
    private final double eventsPerSecond;
    private final String uptime;

    /**
     * Constructs a new SystemStatus.
     *
     * @param status the current system status
     * @param queueSize the current queue size
     * @param processedCount the total number of events processed
     * @param eventsPerSecond the current throughput in events per second
     * @param uptime the application uptime in human-readable format
     */
    public SystemStatus(String status, int queueSize, long processedCount,
                      double eventsPerSecond, String uptime) {
        this.status = status;
        this.queueSize = queueSize;
        this.processedCount = processedCount;
        this.eventsPerSecond = eventsPerSecond;
        this.uptime = uptime;
    }

    public String getStatus() {
        return status;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public long getProcessedCount() {
        return processedCount;
    }

    public double getEventsPerSecond() {
        return eventsPerSecond;
    }

    public String getUptime() {
        return uptime;
    }

    @Override
    public String toString() {
        return String.format("SystemStatus{status='%s', queueSize=%d, processedCount=%d, " +
                "eventsPerSecond=%.2f, uptime='%s'}",
                status, queueSize, processedCount, eventsPerSecond, uptime);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing SystemStatus with optional parameters.
     */
    public static class Builder {

        public Builder() {
        }

        private String status = "IDLE";
        private int queueSize = 0;
        private long processedCount = 0;
        private double eventsPerSecond = 0.0;
        private String uptime = "0s";

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder queueSize(int queueSize) {
            this.queueSize = queueSize;
            return this;
        }

        public Builder processedCount(long processedCount) {
            this.processedCount = processedCount;
            return this;
        }

        public Builder eventsPerSecond(double eventsPerSecond) {
            this.eventsPerSecond = eventsPerSecond;
            return this;
        }

        public Builder uptime(String uptime) {
            this.uptime = uptime;
            return this;
        }

        public SystemStatus build() {
            return new SystemStatus(status, queueSize, processedCount, eventsPerSecond, uptime);
        }
    }
}
