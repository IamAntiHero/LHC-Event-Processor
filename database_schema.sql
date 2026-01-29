-- LHC Event Processor - Database Schema
-- PostgreSQL 15+
-- This script creates the particle_events table and indexes

-- Create table for particle collision events
CREATE TABLE IF NOT EXISTS particle_events (
    event_id UUID PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    energy_gev DOUBLE PRECISION NOT NULL,
    particle_type VARCHAR(20) NOT NULL,
    detected_at_tracker BOOLEAN NOT NULL
);

-- Create index on energy column for high-energy queries (most common use case)
-- DESC order for faster "top N high-energy" queries
CREATE INDEX IF NOT EXISTS idx_energy_gev 
ON particle_events(energy_gev DESC);

-- Create index on timestamp for time-series queries and recent event retrieval
CREATE INDEX IF NOT EXISTS idx_timestamp 
ON particle_events(timestamp DESC);

-- Verify schema creation
\d particle_events

-- Verify indexes created
\di particle_events

-- Example queries for testing

-- Query 1: Get top 10 highest energy events
SELECT event_id, timestamp, energy_gev, particle_type, detected_at_tracker
FROM particle_events
ORDER BY energy_gev DESC
LIMIT 10;

-- Query 2: Get high-energy events (> 50 GeV)
SELECT COUNT(*) as high_energy_count
FROM particle_events
WHERE energy_gev >= 50.0;

-- Query 3: Get statistics
SELECT 
    COUNT(*) as total_events,
    AVG(energy_gev) as avg_energy,
    MAX(energy_gev) as max_energy,
    MIN(energy_gev) as min_energy
FROM particle_events;

-- Query 4: Get events by particle type
SELECT particle_type, COUNT(*) as count
FROM particle_events
GROUP BY particle_type
ORDER BY count DESC;

-- Query 5: Get recent events (last hour)
SELECT event_id, timestamp, energy_gev, particle_type
FROM particle_events
WHERE timestamp > NOW() - INTERVAL '1 hour'
ORDER BY timestamp DESC;

-- Query 6: Check explain plan for high-energy query (should use index)
EXPLAIN ANALYZE
SELECT * FROM particle_events
WHERE energy_gev >= 50.0
ORDER BY energy_gev DESC
LIMIT 10;

-- Expected output should show "Index Scan using idx_energy_gev"

-- Clean up (use with caution!)
-- DROP TABLE IF EXISTS particle_events CASCADE;
