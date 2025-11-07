package com.gps.enlace.controller;

import com.gps.enlace.live.LiveFix;
import com.gps.enlace.live.PositionCache;
import com.gps.enlace.mirror.MirrorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
//@CrossOrigin(origins = {"http://127.0.0.1:5175","http://localhost:5175"})
public class MirrorController {

    private final MirrorService mirrorService;
    private final PositionCache cache;
    private final com.gps.enlace.traccar.TraccarClient traccar;

    @Value("${position.stale-minutes:10}")
    private int staleMinutes;

    public MirrorController(MirrorService mirrorService, PositionCache cache,
                            com.gps.enlace.traccar.TraccarClient traccar) {
        this.mirrorService = mirrorService;
        this.cache = cache;
        this.traccar = traccar;
    }

    /* ---- ADMIN: crear enlace espejo ---- */
    @PostMapping("/mirror")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "X-Forwarded-Proto", required = false) String proto,
                                    @RequestHeader(value = "Host", required = false) String host) {
        Long traccarDeviceId = body.get("traccarDeviceId") instanceof Number 
            ? ((Number) body.get("traccarDeviceId")).longValue() 
            : null;
        
        if (traccarDeviceId == null) {
            return ResponseEntity.badRequest().body(Map.of("error","MISSING_DEVICE_ID"));
        }
        
        Integer expirationHours = body.get("expirationHours") instanceof Number
            ? ((Number) body.get("expirationHours")).intValue()
            : null;
        
        var ml = mirrorService.createForTraccarDevice(traccarDeviceId, expirationHours);
        String scheme = (proto != null ? proto : "http");
        String base = (host != null ? scheme + "://" + host : "");
        String url = base + "/ver/" + ml.getToken();
        return ResponseEntity.created(URI.create(url)).body(Map.of(
                "token", ml.getToken(),
                "url", url,
                "expiresAt", ml.getExpiresAt().toString()
        ));
    }

    /* ---- PÚBLICO: último fix ---- */
    @GetMapping("/mirror/{token}/latest")
    public ResponseEntity<?> latest(@PathVariable String token) {
        var opt = mirrorService.resolveActive(token);
        if (opt.isEmpty()) return ResponseEntity.status(410).body(Map.of("error","TOKEN_EXPIRED_OR_INVALID"));
        var lf = mirrorService.latestByToken(token);
        if (lf.isEmpty()) return ResponseEntity.noContent().build();
        boolean stale = PositionCache.isStale(lf.get().fixTime, staleMinutes);
        return ResponseEntity.ok(dto(lf.get(), stale));
    }

    /* ---- PÚBLICO: historial /trail ---- */
    @GetMapping(value="/mirror/{token}/trail", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> publicTrail(@PathVariable String token,
                                         @RequestParam(name="hours", defaultValue = "24") int hours) {
        var optId = mirrorService.resolveActiveDeviceId(token);
        if (optId.isEmpty()) {
            return ResponseEntity.status(410).body(Map.of("error","TOKEN_EXPIRED_OR_INVALID"));
        }
        long deviceId = optId.get();

        Instant to = Instant.now();
        Instant from = to.minusSeconds((long) hours * 3600);

        try {
            var list = traccar.fetchRoute(deviceId, from, to).block();
            var dto = list.stream()
                    .map(p -> Map.of("lat", p.lat, "lon", p.lon, "fixTime", p.fixTime.toString()))
                    .toList();
            return ResponseEntity.ok(Map.of("trail", dto));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(502).body(Map.of("error","TRACCAR_ROUTE_FAILED"));
        }
    }

    /* ---- PÚBLICO: stream SSE ---- */
    @GetMapping(value="/mirror/{token}/stream", produces= MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@PathVariable String token) {
        var optId = mirrorService.resolveActiveDeviceId(token);
        if (optId.isEmpty()) return ResponseEntity.status(HttpStatus.GONE).build();
        long traccarDeviceId = optId.get();

        SseEmitter emitter = new SseEmitter(0L);
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        final LiveFix[] last = new LiveFix[1];

        // envío inicial si existe
        mirrorService.latestByToken(token).ifPresent(f -> {
            try {
                boolean stale = PositionCache.isStale(f.fixTime, staleMinutes);
                emitter.send(SseEmitter.event().name("position").data(dto(f, stale)));
                last[0] = f;
            } catch (IOException ignored) {}
        });

        Runnable tick = () -> {
            try {
                var linkOpt = mirrorService.resolveActive(token);
                if (linkOpt.isEmpty()) {
                    emitter.send(SseEmitter.event()
                            .name("expired")
                            .data("{\"error\":\"TOKEN_EXPIRED\"}"));
                    emitter.complete();
                    scheduler.shutdownNow();
                    return;
                }

                if (cache.getState() != PositionCache.State.OK) {
                    emitter.send(SseEmitter.event().name("status").data("{\"state\":\"reconnecting\"}"));
                    return;
                }
                Optional<LiveFix> cur = cache.get(traccarDeviceId);
                if (cur.isEmpty()) return;
                if (last[0] == null || cur.get().fixTime.isAfter(last[0].fixTime)) {
                    boolean stale = PositionCache.isStale(cur.get().fixTime, staleMinutes);
                    emitter.send(SseEmitter.event().name("position").data(dto(cur.get(), stale)));
                    last[0] = cur.get();
                }
            } catch (IOException ignored) {}
        };

        scheduler.scheduleAtFixedRate(tick, 1, 2, TimeUnit.SECONDS);
        emitter.onTimeout(scheduler::shutdownNow);
        emitter.onCompletion(scheduler::shutdownNow);

        return ResponseEntity.ok(emitter);
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
