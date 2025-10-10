package com.gps.enlace.live;

import java.time.Instant;

public class LiveFix {
    public final double lat;
    public final double lon;
    public final Double speedKph;
    public final Double headingDeg;
    public final Instant fixTime;
    public final long traccarDeviceId;

    public LiveFix(double lat, double lon, Double speedKph, Double headingDeg, Instant fixTime, long traccarDeviceId) {
        this.lat = lat;
        this.lon = lon;
        this.speedKph = speedKph;
        this.headingDeg = headingDeg;
        this.fixTime = fixTime;
        this.traccarDeviceId = traccarDeviceId;
    }
}
