package com.gps.enlace.controller;

import com.gps.enlace.dto.DeviceItemDto;
import com.gps.enlace.repo.DeviceRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/client")
@CrossOrigin(origins = {"http://127.0.0.1:5175","http://localhost:5175"})
public class ClientDeviceController {

    private final DeviceRepo devices;

    @Value("${demo.user-id:1}")
    private Long demoUserId;

    public ClientDeviceController(DeviceRepo devices) {
        this.devices = devices;
    }

    @GetMapping("/devices")
    public ResponseEntity<List<DeviceItemDto>> listDevices(
            @RequestHeader(value = "X-User-Id", required = false) Long overrideUserId) {

        Long userId = overrideUserId != null ? overrideUserId : demoUserId;

        var out = devices.findAllByUserId(userId).stream()
                .map(d -> new DeviceItemDto(d.getId(), d.getTraccarDeviceId(), d.getName()))
                .toList();

        return ResponseEntity.ok(out);
    }
}
