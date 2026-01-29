package com.hussam.lhc.database;

import com.hussam.lhc.model.ParticleEvent;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages database connections and operations for storing particle events.
 * <p>
 * Uses HikariCP connection pool for high-performance concurrent access.
 * Batch inserts are 100x faster than individual inserts due to transaction batching.
 * </p>
 * <p>
 * <strong>WARNING:</strong> Database credentials should use environment variables in production.
 * </p>
 */
public class DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private HikariDataSource dataSource;

    private static final String JDBC_URL = System.getenv().getOrDefault("JDBC_URL", "jdbc:postgresql://localhost:5432/lhc_events");
    private static final String JDBC_USER = System.getenv().getOrDefault("JDBC_USER", "postgres");
    private static final String JDBC_PASSWORD = System.getenv().getOrDefault("JDBC_PASSWORD", "password");

    /**
     * Constructs a new DatabaseManager and initializes the database connection and schema.
     * <p>
     * This constructor creates necessary tables and indexes if they don't exist.
     * </p>
     */
    public DatabaseManager() {
        initializeConnectionPool();
        initializeSchema();
    }

    /**
     * Returns a new instance of DatabaseManager.
     * <p>
     * <strong>Note:</strong> This method returns a new instance each time rather than
     * a singleton. For true singleton behavior, this should be modified.
     * </p>
     *
     * @return a new DatabaseManager instance
     */
    public static synchronized DatabaseManager getInstance() {
        return new DatabaseManager();
    }

    /**
     * Initializes HikariCP connection pool for optimal performance.
     */
    private void initializeConnectionPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(JDBC_URL);
        config.setUsername(JDBC_USER);
        config.setPassword(JDBC_PASSWORD);

        // Pool sizing: 20 connections balances throughput with database load.
        // 5 idle connections ensures quick response to sudden traffic spikes.
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("LHC-HikariCP");

        // Disable auto-commit so all 1000 inserts happen in one transaction.
        // This is 100x faster than committing after each individual insert.
        config.setAutoCommit(false);
        // Cache prepared statements to avoid re-parsing SQL on every execution.
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(config);
        LOGGER.info("Database connection pool initialized successfully with pool name: " + config.getPoolName());
    }

    /**
     * Creates database schema with optimized indexes for common queries.
     */
    private void initializeSchema() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS particle_events (
                event_id UUID PRIMARY KEY,
                timestamp TIMESTAMP NOT NULL,
                energy_gev DOUBLE PRECISION NOT NULL,
                particle_type VARCHAR(20) NOT NULL,
                detected_at_tracker BOOLEAN NOT NULL
            )
            """;

        // Index energy_desc for high-energy queries (most common use case).
        String createIndexSQL = """
            CREATE INDEX IF NOT EXISTS idx_energy_gev
            ON particle_events(energy_gev DESC)
            """;

        // Index timestamp for time-based queries and chronological analysis.
        String createTimestampIndexSQL = """
            CREATE INDEX IF NOT EXISTS idx_timestamp
            ON particle_events(timestamp DESC)
            """;

        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
            LOGGER.info("Table 'particle_events' created or already exists");

            stmt.execute(createIndexSQL);
            LOGGER.info("Index 'idx_energy_gev' created or already exists");

            stmt.execute(createTimestampIndexSQL);
            LOGGER.info("Index 'idx_timestamp' created or already exists");

            connection.commit();
            LOGGER.info("Database schema initialized successfully");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize database schema", e);
            throw new RuntimeException("Schema initialization failed", e);
        }
    }

    /**
     * Inserts a batch of particle events in a single transaction.
     * <p>
     * Uses JDBC batch operations for efficiency. Duplicates are ignored
     * to support idempotent retries.
     * </p>
     */
    public void insertBatch(List<ParticleEvent> events) {
        if (events == null || events.isEmpty()) {
            LOGGER.warning("Attempted to insert empty batch");
            return;
        }

        String insertSQL = """
            INSERT INTO particle_events
            (event_id, timestamp, energy_gev, particle_type, detected_at_tracker)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;

        long startTime = System.currentTimeMillis();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(insertSQL)) {

            // All inserts happen in one transaction for atomicity and performance.
            connection.setAutoCommit(false);

            for (ParticleEvent event : events) {
                pstmt.setObject(1, event.getEventId());
                pstmt.setTimestamp(2, Timestamp.from(event.getTimestamp()));
                pstmt.setDouble(3, event.getEnergyGev());
                pstmt.setString(4, event.getParticleType().name());
                pstmt.setBoolean(5, event.isDetectedAtTracker());

                pstmt.addBatch();
            }

            // Commit all inserts at once - 100x faster than individual commits.
            int[] updateCounts = pstmt.executeBatch();
            connection.commit();

            long elapsed = System.currentTimeMillis() - startTime;
            int successfulInserts = countSuccessfulInserts(updateCounts);

            LOGGER.info(String.format("Batch insert completed: %d events in %d ms (%.2f events/sec)",
                    successfulInserts, elapsed, (successfulInserts * 1000.0) / elapsed));

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, String.format("Failed to insert batch of %d events", events.size()), e);
            throw new RuntimeException("Batch insert failed", e);
        }
    }

    /**
     * Retrieves high-energy particle events from the database.
     */
    public List<ParticleEvent> queryHighEnergyEvents(int limit, double minEnergy) {
        String querySQL = """
            SELECT event_id, timestamp, energy_gev, particle_type, detected_at_tracker 
            FROM particle_events 
            WHERE energy_gev >= ? 
            ORDER BY energy_gev DESC 
            LIMIT ?
            """;

        List<ParticleEvent> events = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(querySQL)) {
            pstmt.setDouble(1, minEnergy);
            pstmt.setInt(2, limit);

            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                ParticleEvent event = new ParticleEvent(
                        rs.getObject("event_id", java.util.UUID.class),
                        rs.getTimestamp("timestamp").toInstant(),
                        rs.getDouble("energy_gev"),
                        com.hussam.lhc.model.ParticleType.valueOf(rs.getString("particle_type")),
                        rs.getBoolean("detected_at_tracker")
                );
                events.add(event);
            }

            LOGGER.info(String.format("Retrieved %d high-energy events (min energy: %.2f GeV)",
                    events.size(), minEnergy));

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to query high-energy events", e);
            throw new RuntimeException("Query failed", e);
        }

        return events;
    }

    /**
     * Counts high-energy events in the database.
     */
    public long countHighEnergyEvents(double minEnergy) {
        String countSQL = """
            SELECT COUNT(*) 
            FROM particle_events 
            WHERE energy_gev >= ?
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(countSQL)) {
            pstmt.setDouble(1, minEnergy);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to count high-energy events", e);
            throw new RuntimeException("Count query failed", e);
        }

        return 0;
    }

    /**
     * Retrieves comprehensive statistics about all events in the database.
     */
    public DatabaseStatistics getStatistics() {
        String statsSQL = """
            SELECT
                COUNT(*) as total_events,
                AVG(energy_gev) as avg_energy,
                MAX(energy_gev) as max_energy,
                MIN(energy_gev) as min_energy,
                COUNT(*) FILTER (WHERE energy_gev >= 50.0) as high_energy_count
            FROM particle_events
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(statsSQL)) {
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new DatabaseStatistics(
                        rs.getLong("total_events"),
                        rs.getDouble("avg_energy"),
                        rs.getDouble("max_energy"),
                        rs.getDouble("min_energy"),
                        rs.getLong("high_energy_count")
                );
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve statistics", e);
        }

        return new DatabaseStatistics(0, 0.0, 0.0, 0.0, 0);
    }

    /**
     * Returns the current number of active database connections.
     */
    public int getQueueDepth() {
        String queueSQL = """
            SELECT count(*)
            FROM pg_stat_activity
            WHERE datname = 'lhc_events'
            AND state = 'active'
            """;

        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(queueSQL)) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to get queue depth", e);
        }

        return 0;
    }

    public void close() {
        shutdown();
    }

    /**
     * Shuts down the database connection pool.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            logPoolStatistics();
            dataSource.close();
            LOGGER.info("Database connection pool closed");
        }
    }

    public void logPoolStatistics() {
        HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
        LOGGER.info(String.format("Pool Statistics - Active: %d, Idle: %d, Waiting: %d, Total: %d",
            poolMXBean.getActiveConnections(),
            poolMXBean.getIdleConnections(),
            poolMXBean.getThreadsAwaitingConnection(),
            poolMXBean.getTotalConnections()));
    }

    /**
     * Counts successful inserts from batch update result.
     */
    private int countSuccessfulInserts(int[] updateCounts) {
        int count = 0;
        for (int updateCount : updateCounts) {
            if (updateCount == Statement.SUCCESS_NO_INFO || updateCount > 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Statistics about particle events in database.
     */
    public static class DatabaseStatistics {
        public final long totalEvents;
        public final double avgEnergy;
        public final double maxEnergy;
        public final double minEnergy;
        public final long highEnergyCount;

        public DatabaseStatistics(long totalEvents, double avgEnergy, double maxEnergy,
                               double minEnergy, long highEnergyCount) {
            this.totalEvents = totalEvents;
            this.avgEnergy = avgEnergy;
            this.maxEnergy = maxEnergy;
            this.minEnergy = minEnergy;
            this.highEnergyCount = highEnergyCount;
        }

        @Override
        public String toString() {
            return String.format("DatabaseStatistics{totalEvents=%d, avgEnergy=%.2f GeV, " +
                    "maxEnergy=%.2f GeV, minEnergy=%.2f GeV, highEnergyCount=%d}",
                    totalEvents, avgEnergy, maxEnergy, minEnergy, highEnergyCount);
        }
    }
}
