package com.gps.enlace.live;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PositionCache {
    private final ConcurrentHashMap<Long, LiveFix> latest = new ConcurrentHashMap<>();
    private volatile State state = State.RECONNECTING;

    public enum State { OK, RECONNECTING, DOWN }

    public void upsert(LiveFix fix) { latest.put(fix.traccarDeviceId, fix); state = State.OK; }
    public Optional<LiveFix> get(long deviceId) { return Optional.ofNullable(latest.get(deviceId)); }
    public void setState(State s) { this.state = s; }
    public State getState() { return state; }

    public static boolean isStale(Instant fix, int staleMinutes) {
        if (fix == null) return true;

        // adjust Traccar's local time (8h behind UTC) to server time
        Instant adjustedFix = fix.plusSeconds(8 * 3600);

        Instant now = Instant.now();
        long diffSec = java.time.Duration.between(adjustedFix, now).getSeconds();
        boolean stale = now.minusSeconds((long) staleMinutes * 60).isAfter(adjustedFix);

        return stale;
    }


}
