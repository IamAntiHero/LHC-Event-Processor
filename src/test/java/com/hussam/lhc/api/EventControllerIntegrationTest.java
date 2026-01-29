package com.hussam.lhc.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hussam.lhc.model.ParticleEvent;
import com.hussam.lhc.model.ParticleType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("EventController Integration Tests")
class EventControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api";
        
        DatabaseManager databaseManager = new DatabaseManager();
        
        List<ParticleEvent> testData = generateTestData(1000);
        databaseManager.insertBatch(testData);
        
        databaseManager.close();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager databaseManager = new DatabaseManager();
        
        try {
            java.lang.reflect.Field connectionField = DatabaseManager.class.getDeclaredField("connection");
            connectionField.setAccessible(true);
            java.sql.Connection conn = (java.sql.Connection) connectionField.get(databaseManager);
            
            try (var stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM particle_events");
                conn.commit();
            }
        } catch (Exception e) {
            System.err.println("Failed to clean up test data: " + e.getMessage());
        }
        
        databaseManager.close();
    }

    @Test
    @DisplayName("GET /api/events/high-energy with default parameters should return 200")
    void testGetHighEnergyEvents_DefaultParameters_Returns200() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/events/high-energy",
                String.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        MediaType contentType = response.getHeaders().getContentType();
        assertNotNull(contentType);
        assertTrue(contentType.includes(MediaType.APPLICATION_JSON));
        
        try {
            List<ParticleEvent> events = objectMapper.readValue(response.getBody(), 
                    new TypeReference<List<ParticleEvent>>() {});
            
            assertNotNull(events);
            assertTrue(events.size() <= 10);
            
            for (ParticleEvent event : events) {
                assertNotNull(event.getEventId());
                assertNotNull(event.getTimestamp());
                assertTrue(event.getEnergyGev() >= 50.0);
                assertNotNull(event.getParticleType());
            }
        } catch (Exception e) {
            fail("Failed to parse response: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("GET /api/events/high-energy with custom limit should return correct number of events")
    void testGetHighEnergyEvents_CustomLimit_ReturnsCorrectCount() {
        int customLimit = 5;
        ResponseEntity<List<ParticleEvent>> response = restTemplate.exchange(
                baseUrl + "/events/high-energy?limit=" + customLimit,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ParticleEvent>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        List<ParticleEvent> events = response.getBody();
        assertTrue(events.size() <= customLimit);
        
        MediaType contentType = response.getHeaders().getContentType();
        assertNotNull(contentType);
        assertTrue(contentType.includes(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("GET /api/events/high-energy with custom minEnergy should filter correctly")
    void testGetHighEnergyEvents_CustomMinEnergy_FiltersCorrectly() {
        double customMinEnergy = 75.0;
        ResponseEntity<List<ParticleEvent>> response = restTemplate.exchange(
                baseUrl + "/events/high-energy?minEnergy=" + customMinEnergy,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ParticleEvent>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        List<ParticleEvent> events = response.getBody();
        for (ParticleEvent event : events) {
            assertTrue(event.getEnergyGev() >= customMinEnergy, 
                    "Energy " + event.getEnergyGev() + " is below threshold " + customMinEnergy);
        }
    }

    @Test
    @DisplayName("GET /api/events/high-energy with limit=0 should use default limit")
    void testGetHighEnergyEvents_LimitZero_UsesDefault() {
        ResponseEntity<List<ParticleEvent>> response = restTemplate.exchange(
                baseUrl + "/events/high-energy?limit=0",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ParticleEvent>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        List<ParticleEvent> events = response.getBody();
        assertTrue(events.size() <= 10);
    }

    @Test
    @DisplayName("GET /api/events/high-energy with negative minEnergy should use default")
    void testGetHighEnergyEvents_NegativeMinEnergy_UsesDefault() {
        ResponseEntity<List<ParticleEvent>> response = restTemplate.exchange(
                baseUrl + "/events/high-energy?minEnergy=-10.0",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ParticleEvent>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("GET /api/events/high-energy with limit exceeding max should use max limit")
    void testGetHighEnergyEvents_LimitExceedsMax_UsesMaxLimit() {
        int excessiveLimit = 2000;
        ResponseEntity<List<ParticleEvent>> response = restTemplate.exchange(
                baseUrl + "/events/high-energy?limit=" + excessiveLimit,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ParticleEvent>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        List<ParticleEvent> events = response.getBody();
        assertTrue(events.size() <= 1000, "Should respect max limit of 1000");
    }

    @Test
    @DisplayName("GET /api/events/high-energy with both custom parameters should work correctly")
    void testGetHighEnergyEvents_CustomLimitAndMinEnergy_ReturnsCorrectResults() {
        int customLimit = 3;
        double customMinEnergy = 80.0;
        ResponseEntity<List<ParticleEvent>> response = restTemplate.exchange(
                baseUrl + "/events/high-energy?limit=" + customLimit + "&minEnergy=" + customMinEnergy,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ParticleEvent>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        List<ParticleEvent> events = response.getBody();
        assertTrue(events.size() <= customLimit);
        
        for (ParticleEvent event : events) {
            assertTrue(event.getEnergyGev() >= customMinEnergy);
        }
    }

    @Test
    @DisplayName("GET /api/events/statistics should return valid statistics")
    void testGetStatistics_ReturnsValidStatistics() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                baseUrl + "/events/statistics",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> stats = response.getBody();
        
        assertNotNull(stats.get("totalEvents"));
        assertNotNull(stats.get("avgEnergy"));
        assertNotNull(stats.get("maxEnergy"));
        assertNotNull(stats.get("minEnergy"));
        assertNotNull(stats.get("highEnergyCount"));
        
        MediaType contentType = response.getHeaders().getContentType();
        assertNotNull(contentType);
        assertTrue(contentType.includes(MediaType.APPLICATION_JSON));
        
        long totalEvents = Long.parseLong(stats.get("totalEvents").toString());
        assertTrue(totalEvents > 0, "Total events should be positive");
    }

    @Test
    @DisplayName("GET /api/events/statistics should have correct data types")
    void testGetStatistics_DataTypesAreCorrect() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                baseUrl + "/events/statistics",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> stats = response.getBody();
        
        assertInstanceOf(String.class, stats.get("avgEnergy"));
        assertInstanceOf(String.class, stats.get("maxEnergy"));
        assertInstanceOf(String.class, stats.get("minEnergy"));
        
        assertTrue(stats.get("avgEnergy").toString().matches("\\d+\\.\\d{2}"));
        assertTrue(stats.get("maxEnergy").toString().matches("\\d+\\.\\d{2}"));
        assertTrue(stats.get("minEnergy").toString().matches("\\d+\\.\\d{2}"));
    }

    @Test
    @DisplayName("GET /api/system/status should return valid system status")
    void testGetSystemStatus_ReturnsValidStatus() {
        ResponseEntity<SystemStatus> response = restTemplate.exchange(
                baseUrl + "/system/status",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<SystemStatus>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        SystemStatus status = response.getBody();
        assertNotNull(status.getStatus());
        assertNotNull(status.getUptime());
        assertTrue(status.getProcessedCount() >= 0);
        assertTrue(status.getEventsPerSecond() >= 0);
        assertTrue(status.getQueueSize() >= 0);
        
        MediaType contentType = response.getHeaders().getContentType();
        assertNotNull(contentType);
        assertTrue(contentType.includes(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("GET /api/system/status should return valid status values")
    void testGetSystemStatus_ValidStatusValues() {
        ResponseEntity<SystemStatus> response = restTemplate.exchange(
                baseUrl + "/system/status",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<SystemStatus>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        SystemStatus status = response.getBody();
        
        List<String> validStatuses = List.of("IDLE", "PROCESSING", "COMPLETE");
        assertTrue(validStatuses.contains(status.getStatus()), 
                "Status should be one of: " + validStatuses);
        
        assertNotNull(status.getUptime());
        assertTrue(!status.getUptime().isEmpty());
    }

    @Test
    @DisplayName("GET /api/events/high-energy should return events ordered by energy descending")
    void testGetHighEnergyEvents_EventsOrderedByEnergyDescending() {
        ResponseEntity<List<ParticleEvent>> response = restTemplate.exchange(
                baseUrl + "/events/high-energy?limit=5",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ParticleEvent>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<ParticleEvent> events = response.getBody();
        
        if (events != null && events.size() > 1) {
            for (int i = 0; i < events.size() - 1; i++) {
                assertTrue(events.get(i).getEnergyGev() >= events.get(i + 1).getEnergyGev(),
                        "Events should be ordered by energy descending");
            }
        }
    }

    @Test
    @DisplayName("GET /api/events/high-energy should return valid UUID and timestamp")
    void testGetHighEnergyEvents_ValidFields() {
        ResponseEntity<List<ParticleEvent>> response = restTemplate.exchange(
                baseUrl + "/events/high-energy?limit=1",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ParticleEvent>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<ParticleEvent> events = response.getBody();
        
        assertNotNull(events);
        assertFalse(events.isEmpty());
        
        ParticleEvent event = events.get(0);
        
        assertDoesNotThrow(() -> UUID.fromString(event.getEventId().toString()),
                "eventId should be a valid UUID");
        
        assertNotNull(event.getTimestamp());
        assertTrue(event.getTimestamp().isBefore(Instant.now().plusSeconds(1)));
        assertTrue(event.getTimestamp().isAfter(Instant.now().minusSeconds(3600)));
        
        assertNotNull(event.getParticleType());
        assertTrue(List.of(ParticleType.ELECTRON, ParticleType.MUON, ParticleType.PROTON)
                .contains(event.getParticleType()));
    }

    @Test
    @DisplayName("GET /api/events/high-energy response should have correct Content-Type header")
    void testGetHighEnergyEvents_HasCorrectContentType() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/events/high-energy",
                String.class
        );

        HttpHeaders headers = response.getHeaders();
        MediaType contentType = headers.getContentType();
        
        assertNotNull(contentType);
        assertEquals("application", contentType.getType());
        assertEquals("json", contentType.getSubtype());
    }

    @Test
    @DisplayName("GET /api/events/statistics response should have correct Content-Type header")
    void testGetStatistics_HasCorrectContentType() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/events/statistics",
                String.class
        );

        MediaType contentType = response.getHeaders().getContentType();
        
        assertNotNull(contentType);
        assertEquals("application", contentType.getType());
        assertEquals("json", contentType.getSubtype());
    }

    @Test
    @DisplayName("GET /api/system/status response should have correct Content-Type header")
    void testGetSystemStatus_HasCorrectContentType() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/system/status",
                String.class
        );

        MediaType contentType = response.getHeaders().getContentType();
        
        assertNotNull(contentType);
        assertEquals("application", contentType.getType());
        assertEquals("json", contentType.getSubtype());
    }

    @Test
    @DisplayName("Multiple sequential requests should return consistent results")
    void testSequentialRequests_ConsistentResults() {
        List<ParticleEvent> firstResponse = restTemplate.exchange(
                baseUrl + "/events/high-energy?limit=5&minEnergy=60.0",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ParticleEvent>>() {}
        ).getBody();
        
        List<ParticleEvent> secondResponse = restTemplate.exchange(
                baseUrl + "/events/high-energy?limit=5&minEnergy=60.0",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ParticleEvent>>() {}
        ).getBody();
        
        assertNotNull(firstResponse);
        assertNotNull(secondResponse);
        assertEquals(firstResponse.size(), secondResponse.size());
    }

    @Test
    @DisplayName("GET /api/events/high-energy with very high minEnergy should return empty or fewer results")
    void testGetHighEnergyEvents_VeryHighMinEnergy_FewerResults() {
        double veryHighEnergy = 999.0;
        ResponseEntity<List<ParticleEvent>> response = restTemplate.exchange(
                baseUrl + "/events/high-energy?minEnergy=" + veryHighEnergy,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ParticleEvent>>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<ParticleEvent> events = response.getBody();
        
        assertNotNull(events);
        for (ParticleEvent event : events) {
            assertTrue(event.getEnergyGev() >= veryHighEnergy);
        }
    }

    private List<ParticleEvent> generateTestData(int count) {
        ParticleType[] particleTypes = ParticleType.values();
        List<ParticleEvent> events = new java.util.ArrayList<>();
        Instant now = Instant.now();
        
        for (int i = 0; i < count; i++) {
            double energy = 10.0 + Math.random() * 990.0;
            ParticleType type = particleTypes[(int) (Math.random() * particleTypes.length)];
            boolean detectedAtTracker = Math.random() > 0.5;
            
            ParticleEvent event = new ParticleEvent(
                    UUID.randomUUID(),
                    now.minusSeconds(i * 60L),
                    energy,
                    type,
                    detectedAtTracker
            );
            events.add(event);
        }
        
        return events;
    }
}
