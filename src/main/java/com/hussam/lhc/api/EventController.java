package com.hussam.lhc.api;

import com.hussam.lhc.database.DatabaseManager;
import com.hussam.lhc.model.ParticleEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.logging.Logger;
import java.util.Map;
import java.util.HashMap;

/**
 * REST controller for querying particle collision events.
 * <p>
 * Provides endpoints to retrieve high-energy events and statistics.
 * Input validation prevents abuse and protects database performance.
 * </p>
 */
@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Event Management", description = "APIs for querying and analyzing particle collision events")
public class EventController {

    private static final Logger LOGGER = Logger.getLogger(EventController.class.getName());
    private static final int DEFAULT_LIMIT = 10;
    private static final double DEFAULT_MIN_ENERGY = 50.0;
    private static final int MAX_LIMIT = 1000;

    private final DatabaseManager databaseManager;

    /**
     * Constructs a new EventController.
     *
     * @param databaseManager the database manager for querying events
     */
    @Autowired
    public EventController(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Retrieves high-energy particle events from the database.
     */
    @GetMapping("/high-energy")
    @Operation(
            summary = "Get high-energy particle events",
            description = "Retrieve particle collision events with energy above a specified threshold. " +
                    "Useful for identifying potential Higgs Boson candidates and other high-energy phenomena."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved high-energy events",
                    content = @Content(schema = @Schema(implementation = ParticleEvent.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error - failed to query the database"
            )
    })
    public ResponseEntity<List<ParticleEvent>> getHighEnergyEvents(
            @Parameter(description = "Maximum number of events to return (1-1000)", example = "10")
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @Parameter(description = "Minimum energy threshold in GeV", example = "50.0")
            @RequestParam(value = "minEnergy", defaultValue = "50.0") double minEnergy) {

        LOGGER.info(String.format("GET /api/events/high-energy - limit=%d, minEnergy=%.2f GeV", limit, minEnergy));

        // Sanitize user input to prevent abuse. Limit must be positive and
        // capped at 1000 to protect database from expensive large queries.
        if (limit <= 0) {
            LOGGER.warning("Invalid limit parameter, using default: " + DEFAULT_LIMIT);
            limit = DEFAULT_LIMIT;
        }

        if (limit > MAX_LIMIT) {
            LOGGER.warning(String.format("Limit %d exceeds maximum %d, using maximum", limit, MAX_LIMIT));
            limit = MAX_LIMIT;
        }

        // Energy must be non-negative; otherwise, no events would match.
        if (minEnergy < 0) {
            LOGGER.warning("Invalid minEnergy parameter, using default: " + DEFAULT_MIN_ENERGY);
            minEnergy = DEFAULT_MIN_ENERGY;
        }

        try {
            List<ParticleEvent> events = databaseManager.queryHighEnergyEvents(limit, minEnergy);
            LOGGER.info(String.format("Retrieved %d events with energy >= %.2f GeV", events.size(), minEnergy));
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            LOGGER.severe(String.format("Failed to query high-energy events: %s", e.getMessage()));
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retrieves statistical information about all processed events.
     */
    @GetMapping("/statistics")
    @Operation(
            summary = "Get event statistics",
            description = "Retrieve statistical information about all processed events including total count, " +
                    "average energy, min/max energy, and count of high-energy events."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved statistics",
                    content = @Content(schema = @Schema(implementation = Map.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error - failed to retrieve statistics"
            )
    })
    public ResponseEntity<Map<String, Object>> getStatistics() {
        LOGGER.info("GET /api/events/statistics");

        try {
            DatabaseManager.DatabaseStatistics stats = databaseManager.getStatistics();

            Map<String, Object> response = new HashMap<>();
            response.put("totalEvents", stats.totalEvents);
            response.put("avgEnergy", String.format("%.2f", stats.avgEnergy));
            response.put("maxEnergy", String.format("%.2f", stats.maxEnergy));
            response.put("minEnergy", String.format("%.2f", stats.minEnergy));
            response.put("highEnergyCount", stats.highEnergyCount);

            LOGGER.info(String.format("Statistics: totalEvents=%d, avgEnergy=%.2f, maxEnergy=%.2f, minEnergy=%.2f, highEnergyCount=%d",
                    stats.totalEvents, stats.avgEnergy, stats.maxEnergy, stats.minEnergy, stats.highEnergyCount));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LOGGER.severe(String.format("Failed to retrieve statistics: %s", e.getMessage()));
            return ResponseEntity.internalServerError().build();
        }
    }
}
