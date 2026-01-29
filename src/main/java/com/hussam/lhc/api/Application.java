package com.hussam.lhc.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;

/**
 * Spring Boot application entry point for the LHC Event Processor API.
 * <p>
 * Launches REST API server on port 8080. ComponentScan scans all packages
 * under com.hussam.lhc for Spring-managed beans.
 * </p>
 */
@SpringBootApplication
@ComponentScan("com.hussam.lhc")
public class Application {

    /**
     * Default constructor.
     * Uses default initialization for all fields.
     */
    public Application() {
    }

    /**
     * Launches the Spring Boot application.
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Application.class);
        // Disable banner for cleaner console output in production
        app.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        app.run(args);
    }

    /**
     * Prints API information after successful startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("  LHC Event Processor API");
        System.out.println("  Started successfully on http://localhost:8080");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Available endpoints:");
        System.out.println("  GET /api/events/high-energy");
        System.out.println("  GET /api/events/statistics");
        System.out.println("  GET /api/system/status");
        System.out.println();
    }
}
