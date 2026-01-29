package com.hussam.lhc.api;

import com.hussam.lhc.database.DatabaseManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Monitors system status and performance metrics.
 * <p>
 * Tracks uptime, throughput, and provides status via monitoring API.
 * Throughput calculation gives events/second rate for performance tracking.
 * </p>
 */
@Service
public class SystemMonitor {

    private static final Logger LOGGER = Logger.getLogger(SystemMonitor.class.getName());
    private static final Instant APPLICATION_START_TIME = Instant.now();

    private final DatabaseManager databaseManager;

    /**
     * Constructs a new SystemMonitor.
     *
     * @param databaseManager the database manager for retrieving statistics
     */
    @Autowired
    public SystemMonitor(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        LOGGER.info("SystemMonitor initialized. Application started at: " + APPLICATION_START_TIME);
    }

    /**
     * Retrieves the current system status.
     *
     * @return a SystemStatus object containing current system metrics
     */
    public SystemStatus getSystemStatus() {
        DatabaseManager.DatabaseStatistics stats = databaseManager.getStatistics();

        String status = determineStatus(stats);
        long uptimeMillis = Duration.between(APPLICATION_START_TIME, Instant.now()).toMillis();
        String uptime = formatUptime(uptimeMillis);

        // Calculate throughput in events/second. Multiply by 1000 because elapsed
        // time is in milliseconds and we want per-second rate (standard metric).
        double eventsPerSecond = calculateEventsPerSecond(stats.totalEvents, uptimeMillis);
        int queueSize = 0;  // Pipeline not integrated with Spring, assume 0

        LOGGER.info(String.format("System status: %s, totalEvents=%d, uptime=%s, eventsPerSec=%.2f", 
                status, stats.totalEvents, uptime, eventsPerSecond));

        return SystemStatus.builder()
                .status(status)
                .queueSize(queueSize)
                .processedCount(stats.totalEvents)
                .eventsPerSecond(eventsPerSecond)
                .uptime(uptime)
                .build();
    }

    /**
     * Determines the system status based on database statistics.
     */
    private String determineStatus(DatabaseManager.DatabaseStatistics stats) {
        if (stats.totalEvents == 0) {
            return "IDLE";
        }

        // If all events are high-energy, we're done processing everything.
        if (stats.totalEvents > 0 && stats.highEnergyCount == stats.totalEvents) {
            return "COMPLETE";
        }

        return "PROCESSING";
    }

    /**
     * Formats uptime in milliseconds to a human-readable string.
     *
     * @param millis the uptime in milliseconds
     * @return a formatted string (e.g., "1d 2h 3m 4s")
     */
    private String formatUptime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        seconds = seconds % 60;
        minutes = minutes % 60;
        hours = hours % 24;

        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Calculates the events per second throughput.
     *
     * @param totalEvents the total number of events processed
     * @param uptimeMillis the application uptime in milliseconds
     * @return the events per second rate
     */
    private double calculateEventsPerSecond(long totalEvents, long uptimeMillis) {
        if (uptimeMillis <= 0 || totalEvents <= 0) {
            return 0.0;
        }
        
        return (totalEvents * 1000.0) / uptimeMillis;
    }

    /**
     * Returns the application start time.
     *
     * @return the Instant when the application started
     */
    public Instant getApplicationStartTime() {
        return APPLICATION_START_TIME;
    }
}
