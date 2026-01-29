# LHC Event Processor - API Endpoints Reference

## Available Endpoints

### 1. GET /api
**Description:** API information and available endpoints

**Example:**
```bash
curl http://localhost:8080/api
```

**Response:**
```json
{
  "application": "LHC Event Processor API",
  "version": "1.0.0",
  "status": "running",
  "timestamp": "2024-01-15T10:30:00Z",
  "endpoints": [
    "/api",
    "/api/events/high-energy",
    "/api/events/statistics",
    "/api/system/status"
  ]
}
```

---

### 2. GET /api/events/high-energy
**Description:** Query high-energy particle collision events

**Query Parameters:**

| Parameter | Type | Default | Max | Description |
|-----------|------|----------|------|-------------|
| `limit` | int | 10 | 1000 | Maximum number of events to return |
| `minEnergy` | double | 50.0 | N/A | Minimum energy threshold in GeV |

**Examples:**

**Example 1: Default Parameters**
```bash
curl http://localhost:8080/api/events/high-energy
```

**Response:**
```json
[
  {
    "eventId": "7f83b165-7f19-3405-bc85-0da05aeb8012",
    "timestamp": "2024-01-15T10:30:00Z",
    "energyGev": 124.97,
    "particleType": "PROTON",
    "detectedAtTracker": true
  },
  {
    "eventId": "550e8400-e29b-41d4-a716-446655440000",
    "timestamp": "2024-01-15T10:30:01Z",
    "energyGev": 119.88,
    "particleType": "MUON",
    "detectedAtTracker": false
  }
]
```

**Example 2: Custom Limit**
```bash
curl http://localhost:8080/api/events/high-energy?limit=5
```

**Example 3: Custom Energy Threshold**
```bash
curl http://localhost:8080/api/events/high-energy?minEnergy=100.0
```

**Example 4: Both Parameters**
```bash
curl "http://localhost:8080/api/events/high-energy?limit=20&minEnergy=75.0"
```

---

### 3. GET /api/events/statistics
Get database statistics.

**Query Parameters:**
- None required

**Response Fields:**

| Field | Type | Format | Description |
|-------|------|---------|-------------|
| `totalEvents` | Number | Integer | Total number of events in database |
| `avgEnergy` | String | X.XX | Average energy in GeV (2 decimal places) |
| `maxEnergy` | String | X.XX | Maximum energy in GeV (2 decimal places) |
| `minEnergy` | String | X.XX | Minimum energy in GeV (2 decimal places) |
| `highEnergyCount` | Number | Integer | Count of events with energy >= 50.0 GeV |

**Note:** Energy fields are returned as strings to preserve 2-decimal formatting. Count fields are integers.

---

### 4. GET /api/system/status
Get system health and performance metrics.

**Query Parameters:**
- None required

**Response Fields:**

| Field | Type | Values | Description |
|-------|------|---------|-------------|
| `status` | String | "IDLE", "PROCESSING", "COMPLETE" | Current system state |
| `queueSize` | Number | Integer | Current queue size (0 if not processing) |
| `processedCount` | Number | Long | Total events processed |
| `eventsPerSecond` | Number | Float | Throughput (events/second) |
| `uptime` | String | Xd Xh Xm Xs | Application uptime |

**Status Values:**
- `"IDLE"` - No events in database
- `"PROCESSING"` - Events exist, actively processing
- `"COMPLETE"` - All events are high-energy (pipeline complete)

---

## Quick Test Commands

```bash
# Test API is running
curl http://localhost:8080/api

# Get top 10 high-energy events
curl http://localhost:8080/api/events/high-energy

# Get top 5 high-energy events
curl http://localhost:8080/api/events/high-energy?limit=5

# Get events >= 100 GeV
curl http://localhost:8080/api/events/high-energy?minEnergy=100.0

# Get top 20 events >= 75 GeV
curl "http://localhost:8080/api/events/high-energy?limit=20&minEnergy=75.0"

# Get database statistics
curl http://localhost:8080/api/events/statistics

# Get system status
curl http://localhost:8080/api/system/status
```

---

## Response Codes

| Status | Description |
|---------|-------------|
| 200 OK | Request successful |
| 400 Bad Request | Invalid parameters |
| 404 Not Found | Endpoint not found |
| 500 Internal Server Error | Database or server error |

---

## CORS

Cross-Origin Resource Sharing is enabled for all origins:
```java
@CrossOrigin(origins = "*", maxAge = 3600)
```

**Frontend Example:**
```javascript
fetch('http://localhost:8080/api/events/high-energy')
  .then(response => response.json())
  .then(data => console.log(data));
```

---

## JSON Format

All responses are in JSON format with `Content-Type: application/json`.

**ParticleEvent Object:**
```json
{
  "eventId": "UUID (string)",
  "timestamp": "ISO-8601 timestamp",
  "energyGev": "double (GeV)",
  "particleType": "ELECTRON|MUON|PROTON",
  "detectedAtTracker": "boolean"
}
```

---

## Error Handling

### Example Error Response
```bash
curl http://localhost:8080/api/events/nonexistent
```

**Response:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "No handler found for GET /api/events/nonexistent",
  "path": "/api/events/nonexistent"
}
```

---

## Performance

### Expected Response Times

| Limit | Expected Time | Notes |
|-------|---------------|-------|
| 10 | < 50ms | Default, fast |
| 100 | < 200ms | Small dataset |
| 500 | < 800ms | Medium dataset |
| 1000 | < 2s | Large dataset, uses max limit |

### Caching

Not implemented yet. Future enhancement.

---

## Rate Limiting

Not implemented yet. Future enhancement.

---

## Authentication

Not implemented yet. Public API for demonstration purposes.
