package com.gps.enlace.health;

import com.gps.enlace.live.PositionCache;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class TraccarFeedHealth implements HealthIndicator {
    private final PositionCache cache;

    public TraccarFeedHealth(PositionCache cache) { this.cache = cache; }

    @Override
    public Health health() {
        return switch (cache.getState()) {
            case OK -> Health.up().build();
            case RECONNECTING -> Health.status("RECONNECTING").build();
            case DOWN -> Health.down().build();
        };
    }
}
