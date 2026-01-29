package com.hussam.lhc;

import com.hussam.lhc.ingestion.PipelineManager;
import com.hussam.lhc.ingestion.CsvParser;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.ConsoleHandler;
import java.util.logging.SimpleFormatter;

/**
 * Command-line interface for the LHC Event Processor.
 * <p>
 * Generates test CSV files with realistic particle collision data
 * and runs the processing pipeline for testing and demonstration.
 * </p>
 */
public class Main {

    /**
     * Default constructor.
     * Uses default initialization for all fields.
     */
    public Main() {
    }

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    static {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.INFO);
    }

    /**
     * Main entry point for the LHC Event Processor application.
     *
     * @param args command line arguments (optional: --generate [rows])
     */
    public static void main(String[] args) {
        LOGGER.info("LHC Event Processor - Main Application");
        LOGGER.info("========================================");

        String dataDir = "data";
        String testFile = dataDir + "/test_events.csv";

        try {
            if (args.length == 0 || args[0].equals("--generate")) {
                int rows = args.length > 1 ? Integer.parseInt(args[1]) : 10000;
                generateTestFile(testFile, rows);
                LOGGER.info("Generated test file: " + testFile);
            }

            Path csvPath = Paths.get(testFile);
            if (!Files.exists(csvPath)) {
                LOGGER.severe("Test file not found: " + testFile);
                LOGGER.info("Run with --generate to create test data");
                return;
            }

            LOGGER.info("Starting pipeline with test file: " + testFile);
            PipelineManager manager = new PipelineManager(2, 2, 5000);
            manager.start(java.util.List.of(csvPath), new CsvParser());

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error: " + e.getMessage(), e);
        }
    }

    /**
     * Generates test CSV file with realistic particle collision data.
     * <p>
     * Creates random events with varying energy levels to test the filtering
     * pipeline. Energy distribution ensures ~40% events exceed 50 GeV threshold.
     * </p>
     */
    private static void generateTestFile(String filename, int rows) throws IOException {
        Path path = Paths.get(filename);
        Files.createDirectories(path.getParent());

        Random random = new Random();
        String[] particleTypes = {"ELECTRON", "MUON", "PROTON"};

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path))) {
            writer.println("event_id,timestamp,energy_gev,particle_type,detected_at_tracker");

            for (int i = 0; i < rows; i++) {
                UUID eventId = UUID.randomUUID();
                // Random timestamp in the last 24 hours
                Instant timestamp = Instant.now().minusSeconds(random.nextInt(86400));
                // Energy range: 0.1-125.1 GeV (distribution ensures ~40% > 50 GeV)
                double energy = 0.1 + random.nextDouble() * 125.0;
                String particleType = particleTypes[random.nextInt(particleTypes.length)];
                boolean detected = random.nextBoolean();

                writer.printf("%s,%s,%.2f,%s,%b%n",
                        eventId, timestamp, energy, particleType, detected);
            }
        }

        LOGGER.info("Generated " + rows + " test events");
    }
}
