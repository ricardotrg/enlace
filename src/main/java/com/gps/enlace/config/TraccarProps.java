package com.gps.enlace.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "traccar")
@Data
public class TraccarProps {
    private String baseUrl;
    private String user;
    private String pass;
    private String wsPath = "/api/socket";
    private long  deviceId; // para vista admin por defecto
    private int wsPingIntervalSeconds = 30;
    private int wsReconnectBackoffInitialMs = 500;
    private int wsReconnectBackoffMaxMs = 10_000;
}
