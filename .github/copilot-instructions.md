# GPS Tracking Service (Enlace) - AI Assistant Guide

## Architecture Overview
This Spring Boot 3.5 application creates a GPS tracking service that acts as a **bridge between Traccar GPS server and public sharing**. It provides real-time position streaming via WebSocket and exposes shareable mirror links for GPS tracking.

### Core Components
1. **TraccarClient** (`traccar/TraccarClient.java`) - WebSocket client that maintains persistent connection to external Traccar server, handles authentication via session cookies, and receives real-time position updates
2. **PositionCache** (`live/PositionCache.java`) - Thread-safe in-memory cache using `ConcurrentHashMap` for latest GPS positions per device
3. **MirrorService** (`mirror/MirrorService.java`) - Creates time-limited shareable tokens for public GPS viewing without authentication
4. **Controllers** - Admin endpoints (`/api/admin/*`) and public mirror endpoints (`/api/mirror/{token}/*`)

### Data Flow Pattern
```
Traccar Server → WebSocket → TraccarClient → PositionCache → Controllers → API/SSE
                                            ↓
                                     MirrorService → Public Links
```

## Key Implementation Patterns

### WebSocket Connection Management
- `TraccarClient` auto-reconnects with exponential backoff (500ms to 10s)
- Uses Spring WebFlux `ReactorNettyWebSocketClient` for reactive streams
- Maintains session state via `JSESSIONID` cookie authentication
- Ping/pong heartbeat every 30 seconds to keep connection alive

### Position Data Handling
- `LiveFix` is immutable record-style class with final fields
- Time zone adjustment: Traccar local time (UTC-8) adjusted to server UTC in `PositionCache.isStale()`
- Speed conversion: Traccar knots → km/h (`speed * 1.852`)
- Stale position detection based on configurable minutes threshold

### Mirror Link Security
- Tokens generated via `TokenGenerator` (48-char secure random)
- Time-based expiration (default 24h) checked in `MirrorLinkRepo.findActiveFetchDevice()`
- No authentication required for public endpoints - security through unguessable tokens

## Development Workflow

### Database Migrations
Uses Flyway with PostgreSQL. Migration files in `src/main/resources/db/migration/`:
- `V1__init.sql` - Creates device and mirror_link tables
- `V2__add_app_user_and_device_fk.sql` - Adds user relationships

### Configuration Patterns
- `TraccarProps` uses `@ConfigurationProperties(prefix = "traccar")` for external server config
- Environment-specific properties support via `spring.profiles.active=local`
- CORS configured for local development (`http://127.0.0.1:5175`, `http://localhost:5175`)

### Running the Application
```bash
# Start PostgreSQL database first
# Database: gpstracker, user: gps, password: gps
mvn spring-boot:run
```

### Testing Integration
- Traccar server expected at `http://localhost:8082`
- WebSocket endpoint: `ws://localhost:8082/api/socket`
- Admin API: `http://localhost:8080/api/admin/live`
- Health check: `http://localhost:8080/actuator/health`

## Critical Dependencies
- **Spring WebFlux** - For reactive WebSocket client and SSE streaming
- **PostgreSQL + JPA** - Primary data storage
- **Flyway** - Database migrations
- **Lombok** - Code generation for entities
- **OkHttp** - HTTP client for Traccar API calls

## Common Gotchas
- **Time zones**: Traccar sends local time, adjust by +8h for UTC in staleness calculations
- **WebSocket reconnection**: Connection drops are normal - service auto-recovers via exponential backoff
- **Token expiration**: Public mirror links expire after 24h by default, handle 410 responses gracefully
- **CORS**: Local development requires specific origins in `cors.allowed-origins` property

## Entity Relationships
```
Device (traccar_device_id) ←── MirrorLink (token, expires_at)
     ↓
PositionCache (in-memory) ←── LiveFix (immutable)
```