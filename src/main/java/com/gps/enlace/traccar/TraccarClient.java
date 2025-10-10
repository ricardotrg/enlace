package com.gps.enlace.traccar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gps.enlace.config.TraccarProps;
import com.gps.enlace.live.LiveFix;
import com.gps.enlace.live.PositionCache;
import jakarta.annotation.PostConstruct;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpHeaders;


@Component
public class TraccarClient {
    private final TraccarProps props;
    private final PositionCache cache;
    private final ObjectMapper mapper = new ObjectMapper();
    private final WebClient http;
    private java.time.Instant sessionAt;

    private volatile String sessionCookie; // JSESSIONID de Traccar

    public TraccarClient(TraccarProps props, PositionCache cache) {
        this.props = props;
        this.cache = cache;
        this.http = WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // ⬅ 10 MB
                .build();
    }

    @PostConstruct
    public void start() {
        loginAndConnect();
    }

    private void loginAndConnect() {
        login().flatMap(cookie -> {
            this.sessionCookie = cookie;
            this.sessionAt = java.time.Instant.now();
            return connectWebSocket();
        }).doOnError(e -> {
            cache.setState(PositionCache.State.DOWN);
            scheduleReconnect(props.getWsReconnectBackoffInitialMs());
        }).subscribe();
    }

    private Mono<String> login() {
        return http.post()
                .uri("/api/session")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters
                        .fromFormData("email", props.getUser())
                        .with("password", props.getPass()))
                .exchangeToMono(resp -> {
                    if (resp.statusCode().is2xxSuccessful()) {
                        var c = resp.cookies().getFirst("JSESSIONID");
                        if (c != null) return Mono.just("JSESSIONID=" + c.getValue());
                    }
                    return Mono.error(new IllegalStateException("Traccar login failed: " + resp.statusCode()));
                });
    }

    private Mono<Void> connectWebSocket() {
        cache.setState(PositionCache.State.RECONNECTING);
        String wsUrl = props.getBaseUrl().replaceFirst("^http", "ws") + props.getWsPath();
        ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", sessionCookie); // usa HttpHeaders, no WebSocketHttpHeaders

        return client.execute(URI.create(wsUrl), headers, session -> {
                    System.out.println("✅ WebSocket handshake successful with Traccar at " + wsUrl);

                    Mono<Void> pinger = Mono.defer(() ->
                            session.send(Mono.just(session.textMessage("{\"action\":\"ping\"}")))
                    ).repeatWhen(flux -> flux.delayElements(Duration.ofSeconds(props.getWsPingIntervalSeconds()))).then();

                    Mono<Void> receiver = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doOnSubscribe(s -> System.out.println("📡 Listening for Traccar position updates..."))
                            .doOnNext(this::handleMessage)
                            .doOnError(e -> System.err.println("❌ Error receiving WS data: " + e.getMessage()))
                            .then();

                    return Mono.when(pinger, receiver);
                })
                .doOnSubscribe(s -> System.out.println("🔌 Connecting to Traccar WS..."))
                .doOnSuccess(v -> System.out.println("⚡ WS session ended, scheduling reconnect..."))
                .doOnError(e -> System.err.println("❌ WS connection error: " + e.getMessage()))
                .doFinally(sig -> cache.setState(PositionCache.State.OK));

    }


    private void scheduleReconnect(int initialMs) {
        cache.setState(PositionCache.State.RECONNECTING);
        int max = props.getWsReconnectBackoffMaxMs();
        int next = Math.min(initialMs * 2, max);
        Mono.delay(Duration.ofMillis(initialMs)).subscribe(t -> loginAndConnect());
    }

    private void handleMessage(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            // Traccar envía objetos como {"positions":[{...}], "events":[...]} etc.
            Optional.ofNullable(root.get("positions")).ifPresent(arr -> {
                for (JsonNode p : arr) {
                    long deviceId = p.path("deviceId").asLong();
                    double lat = p.path("latitude").asDouble();
                    double lon = p.path("longitude").asDouble();
                    Double speed = p.hasNonNull("speed") ? p.get("speed").asDouble() * 1.852 /*knots→kph*/ : null;
                    Double course = p.hasNonNull("course") ? p.get("course").asDouble() : null;
                    Instant fixTime = parseTime(p.path("fixTime").asText(null));
                    if (!Double.isNaN(lat) && !Double.isNaN(lon) && fixTime != null) {
                        cache.upsert(new LiveFix(lat, lon, speed, course, fixTime, deviceId));
                        System.out.printf(
                                "Posición recibida → deviceId=%d lat=%.6f lon=%.6f speed=%.1f heading=%.1f fixTime=%s%n",
                                deviceId, lat, lon,
                                speed != null ? speed : 0,
                                course != null ? course : 0,
                                fixTime
                        );
                    }
                }
            });
            // También puede llegar {"devices":[...]} o heartbeats; los ignoramos.
        } catch (Exception ignore) { }
    }

    // === RUTA / HISTORIAL DESDE TRACCAR ===
    public Mono<List<LiveFix>> fetchRoute(long deviceId, Instant from, Instant to) {
        System.out.println("[FETCH_ROUTE] Starting fetch for deviceId=" + deviceId + " from=" + from + " to=" + to);
        System.out.println("[FETCH_ROUTE] Using cookie=" + sessionCookie);

        String f = from.toString();
        String t = to.toString();

        return http.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/reports/route")
                        .queryParam("deviceId", deviceId)
                        .queryParam("from", f)
                        .queryParam("to", t)
                        .queryParam("format", "json")            // ⬅️ fuerza JSON
                        .build())
                .header("Accept", "application/json")        // ⬅️ fuerza JSON
                .cookie("JSESSIONID", sessionCookie != null ? sessionCookie.replace("JSESSIONID=", "") : "")
                .retrieve()
                .onStatus(
                        status -> {
                            boolean bad = status.value() >= 400;
                            if (bad)
                                System.out.println("[FETCH_ROUTE] HTTP error status=" + status.value());
                            return bad;
                        },
                        resp -> resp.bodyToMono(String.class)
                                .map(body -> new RuntimeException("[FETCH_ROUTE] Remote error body=" + body))
                )
                .bodyToMono(com.fasterxml.jackson.databind.JsonNode.class)
                .map(root -> {
                    System.out.println("[FETCH_ROUTE] Response OK from Traccar");
                    java.util.ArrayList<com.gps.enlace.live.LiveFix> out = new java.util.ArrayList<>();
                    com.fasterxml.jackson.databind.JsonNode arr = root.isArray() ? root : root.get("positions");
                    if (arr != null && arr.isArray()) {
                        System.out.println("[FETCH_ROUTE] Positions received: " + arr.size());
                        for (var p : arr) {
                            double lat = p.path("latitude").asDouble();
                            double lon = p.path("longitude").asDouble();
                            Instant fixTime = Instant.parse(p.path("fixTime").asText());
                            Double speedKph = p.hasNonNull("speed") ? p.get("speed").asDouble() * 1.852 : null;
                            Double headingDeg = p.hasNonNull("course") ? p.get("course").asDouble() : null;
                            out.add(new com.gps.enlace.live.LiveFix(lat, lon, speedKph, headingDeg, fixTime, deviceId));
                        }
                    } else {
                        System.out.println("[FETCH_ROUTE] No positions array in JSON");
                    }
                    return (java.util.List<com.gps.enlace.live.LiveFix>) (java.util.List<?>) out;
                })
                .doOnError(err -> System.out.println("[FETCH_ROUTE] Exception: " + err.getMessage()));
    }


    private Instant parseTime(String iso) {
        try { return iso == null ? null : Instant.parse(iso); }
        catch (Exception e) { return null; }
    }
}
