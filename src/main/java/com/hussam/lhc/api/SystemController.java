package com.hussam.lhc.api;

import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

/**
 * REST controller for system monitoring and status information.
 * <p>
 * Provides endpoints for retrieving current system status including
 * processing state, throughput metrics, and uptime information.
 * </p>
 */
@RestController
@RequestMapping("/api/system")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "System Monitoring", description = "APIs for monitoring system status and performance")
public class SystemController {

    private static final Logger LOGGER = Logger.getLogger(SystemController.class.getName());

    private final SystemMonitor systemMonitor;

    /**
     * Constructs a new SystemController.
     *
     * @param systemMonitor the system monitor for retrieving status information
     */
    @Autowired
    public SystemController(SystemMonitor systemMonitor) {
        this.systemMonitor = systemMonitor;
    }

    /**
     * Retrieves the current system status.
     */
    @GetMapping("/status")
    @Operation(
            summary = "Get system status",
            description = "Retrieve current system status including processing state, " +
                    "number of events processed, events per second, and system uptime."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved system status",
                    content = @Content(schema = @Schema(implementation = SystemStatus.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error - failed to retrieve system status"
            )
    })
    public ResponseEntity<SystemStatus> getSystemStatus() {
        LOGGER.info("GET /api/system/status");

        try {
            SystemStatus status = systemMonitor.getSystemStatus();
            LOGGER.info(String.format("System status: status=%s, processed=%d, eventsPerSec=%.2f, uptime=%s",
                    status.getStatus(), status.getProcessedCount(), 
                    status.getEventsPerSecond(), status.getUptime()));
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            LOGGER.severe(String.format("Failed to retrieve system status: %s", e.getMessage()));
            return ResponseEntity.internalServerError().build();
        }
    }
}
