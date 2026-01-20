package com.gps.enlace.controller;

import com.gps.enlace.config.TraccarProps;
import com.gps.enlace.live.LiveFix;
import com.gps.enlace.live.PositionCache;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/api/admin")
//@CrossOrigin(origins = {"http://127.0.0.1:5175","http://localhost:5175"})
public class AdminLiveController {

    private final PositionCache cache;
    private final TraccarProps props;
    private final com.gps.enlace.traccar.TraccarClient traccar;

    public AdminLiveController(PositionCache cache, TraccarProps props, com.gps.enlace.traccar.TraccarClient traccar) {
        this.cache = cache; this.props = props; this.traccar = traccar;
    }

    // Devuelve la última posición conocida (JSON) para el dispositivo indicado (o el default configurado).
    @GetMapping(value = "/live", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> latest(@RequestParam(name="traccarDeviceId", required = false) Long deviceId) {
        long id = deviceId != null ? deviceId : props.getDeviceId();
        if (cache.getState() == PositionCache.State.DOWN) return ResponseEntity.status(503).body(Map.of("error","FEED_DOWN"));
        Optional<LiveFix> fix = cache.get(id);
        if (fix.isEmpty()) return ResponseEntity.noContent().build();
        boolean stale = PositionCache.isStale(fix.get().fixTime, Integer.getInteger("position.stale-minutes", 3));
        return ResponseEntity.ok(dto(fix.get(), stale));
    }

    // Abre un stream SSE que envía eventos "position" con la ubicación actual cada vez que cambia.
    @GetMapping(value = "/live/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(name="traccarDeviceId", required = false) Long deviceId) {
        long id = deviceId != null ? deviceId : props.getDeviceId();
        SseEmitter emitter = new SseEmitter(0L);
        // envío inicial y “polling push” simple cada 2s para evitar complejidad de pub/sub
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        final LiveFix[] lastSent = new LiveFix[1];

        Runnable tick = () -> {
            try {
                if (cache.getState() != PositionCache.State.OK) {
                    emitter.send(SseEmitter.event().name("status").data("{\"state\":\"reconnecting\"}"));
                    return;
                }
                Optional<LiveFix> fix = cache.get(id);
                if (fix.isEmpty()) return;
                if (lastSent[0] == null || fix.get().fixTime.isAfter(lastSent[0].fixTime)) {
                    boolean stale = PositionCache.isStale(fix.get().fixTime, Integer.getInteger("position.stale-minutes", 3));
                    emitter.send(SseEmitter.event().name("position").data(dto(fix.get(), stale)));
                    lastSent[0] = fix.get();
                }
            } catch (IOException ignored) { }
        };

        scheduler.scheduleAtFixedRate(tick, 0, 2, TimeUnit.SECONDS);
        emitter.onTimeout(() -> scheduler.shutdownNow());
        emitter.onCompletion(scheduler::shutdownNow);
        return emitter;
    }

    // === HISTORIAL ADMIN: ?deviceId=4&hours=24 ===
    // Devuelve el historial (ruta) del dispositivo para las últimas N horas consultando Traccar.
    @GetMapping(value = "/trail", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> trail(@RequestParam("deviceId") long deviceId,
                                   @RequestParam(name = "hours", defaultValue = "24") int hours) {

        // Traccar server local time (example: UTC-08:00). Move to config if needed.
        var TRACCAR_ZONE = java.time.ZoneId.of("UTC-08:00");

        var toZ   = java.time.ZonedDateTime.now(TRACCAR_ZONE);
        var fromZ = toZ.minusHours(hours);

        java.time.Instant to   = toZ.toInstant();   // send UTC instants
        java.time.Instant from = fromZ.toInstant();

        System.out.println("[TRAIL] deviceId=" + deviceId
                + " hours=" + hours
                + " from(UTC)=" + from
                + " to(UTC)=" + to
                + " from(local)=" + fromZ
                + " to(local)=" + toZ);

        try {
            var list = traccar.fetchRoute(deviceId, from, to).block();
            if (list != null) list.forEach(p -> System.out.println(p.fixTime));

            var dto = list.stream()
                    .map(p -> java.util.Map.<String,Object>of(
                            "lat", p.lat,
                            "lon", p.lon,
                            "fixTime", p.fixTime.toString()
                    ))
                    .collect(java.util.stream.Collectors.toList());

            return ResponseEntity.ok(java.util.Map.of("trail", dto));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(java.util.Map.of("error", "TRACCAR_ROUTE_FAILED"));
        }
    }


    private Map<String,Object> dto(LiveFix f, boolean stale) {
        return Map.of(
                "lat", f.lat,
                "lon", f.lon,
                "speedKph", f.speedKph,
                "headingDeg", f.headingDeg,
                "fixTime", f.fixTime.toString(),
                "deviceId", f.traccarDeviceId,
                "stale", stale
        );
    }
}
