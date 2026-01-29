package com.hussam.lhc.ingestion;

import com.hussam.lhc.model.ParticleEvent;
import com.hussam.lhc.model.ParticleType;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Parses CSV lines into ParticleEvent objects.
 * <p>
 * Expected format: event_id,timestamp,energy_gev,particle_type,detected_at_tracker
 * Strict validation ensures data quality before entering the pipeline.
 * </p>
 */
public class CsvParser implements DataParser<ParticleEvent> {

    public CsvParser() {
    }

    private static final String DELIMITER = ",";
    private static final int EXPECTED_FIELDS = 5;

    /**
     * Parses a CSV line into a ParticleEvent object.
     *
     * @param line the CSV line to parse
     * @return a ParticleEvent object parsed from the line
     * @throws ParseException if the line cannot be parsed or is invalid
     */
    @Override
    public ParticleEvent parse(String line) throws ParseException {
        if (line == null || line.trim().isEmpty()) {
            throw new ParseException("Input line is null or empty");
        }

        String[] fields = line.split(DELIMITER);
        // Validate structure early to fail fast on malformed data
        if (fields.length != EXPECTED_FIELDS) {
            throw new ParseException("Expected " + EXPECTED_FIELDS + " fields, got " + fields.length + ": " + line);
        }

        try {
            UUID eventId = parseEventId(fields[0].trim());
            Instant timestamp = parseTimestamp(fields[1].trim());
            double energyGev = parseEnergy(fields[2].trim());
            ParticleType particleType = parseParticleType(fields[3].trim());
            boolean detectedAtTracker = parseBoolean(fields[4].trim());

            return new ParticleEvent(eventId, timestamp, energyGev, particleType, detectedAtTracker);
        } catch (Exception e) {
            throw new ParseException("Failed to parse line: " + line, e);
        }
    }

    /**
     * Parses a UUID from the given string value.
     *
     * @param value the string representation of a UUID
     * @return the parsed UUID
     * @throws ParseException if the value is not a valid UUID format
     */
    private UUID parseEventId(String value) throws ParseException {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ParseException("Invalid UUID format: " + value, e);
        }
    }

    /**
     * Parses an ISO-8601 timestamp from the given string value.
     *
     * @param value the string representation of a timestamp
     * @return the parsed Instant
     * @throws ParseException if the value is not a valid ISO-8601 timestamp
     */
    private Instant parseTimestamp(String value) throws ParseException {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new ParseException("Invalid timestamp format (expected ISO-8601): " + value, e);
        }
    }

    /**
     * Parses an energy value from the given string value.
     *
     * @param value the string representation of energy in GeV
     * @return the parsed energy value
     * @throws ParseException if the value is not a valid number or is negative
     */
    private double parseEnergy(String value) throws ParseException {
        try {
            double energy = Double.parseDouble(value);
            if (energy < 0) {
                throw new ParseException("Energy cannot be negative: " + energy);
            }
            return energy;
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid energy value: " + value, e);
        }
    }

    /**
     * Parses a particle type from the given string value.
     *
     * @param value the string representation of a particle type (case-insensitive)
     * @return the parsed ParticleType
     * @throws ParseException if the value is not a valid particle type
     */
    private ParticleType parseParticleType(String value) throws ParseException {
        try {
            return ParticleType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ParseException("Invalid particle type: " + value + ". Valid values: ELECTRON, MUON, PROTON", e);
        }
    }

    /**
     * Parses a boolean value from the given string value.
     *
     * @param value the string representation of a boolean ("true" or "false", case-insensitive)
     * @return the parsed boolean value
     * @throws ParseException if the value is not "true" or "false"
     */
    private boolean parseBoolean(String value) throws ParseException {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        } else if ("false".equalsIgnoreCase(value)) {
            return false;
        } else {
            throw new ParseException("Invalid boolean value: " + value + ". Expected 'true' or 'false'");
        }
    }
}
