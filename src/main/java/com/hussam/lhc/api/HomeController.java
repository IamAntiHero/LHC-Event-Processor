package com.hussam.lhc.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Home endpoint that provides API information and available routes.
 */
@RestController
@RequestMapping("/api")
public class HomeController {

    @GetMapping
    public Map<String, Object> home() {
        Map<String, Object> response = new HashMap<>();
        response.put("application", "LHC Event Processor API");
        response.put("version", "1.0.0");
        response.put("status", "running");
        response.put("timestamp", Instant.now().toString());
        response.put("endpoints", new String[]{
                "/api/events/high-energy",
                "/api/events/statistics",
                "/api/system/status"
        });
        return response;
    }
}
