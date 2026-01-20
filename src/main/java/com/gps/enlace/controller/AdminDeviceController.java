package com.gps.enlace.controller;

import com.gps.enlace.domain.Device;
import com.gps.enlace.service.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = {
    "http://127.0.0.1:5175",
    "http://localhost:5175",
    "https://appellate-naively-temperance.ngrok-free.app"
})
public class AdminDeviceController {

    private final DeviceService deviceService;

    public AdminDeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /**
     * Register a new device
     * POST /api/admin/devices
     * Body: {"uniqueId": "079060009083", "name": "Unidad 03", "userId": 1}
     */
    @PostMapping("/devices")
    public ResponseEntity<?> registerDevice(@RequestBody Map<String, Object> body) {
        String uniqueId = (String) body.get("uniqueId");
        String name = (String) body.get("name");
        Long userId = body.get("userId") instanceof Number 
            ? ((Number) body.get("userId")).longValue() 
            : null;

        if (uniqueId == null || uniqueId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "uniqueId is required"));
        }

        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }

        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }

        try {
            Device device = deviceService.registerDevice(uniqueId, name, userId);
            return ResponseEntity.ok(Map.of(
                    "id", device.getId(),
                    "traccarDeviceId", device.getTraccarDeviceId(),
                    "uniqueId", device.getUniqueId(),
                    "name", device.getName(),
                    "userId", device.getUserId() != null ? device.getUserId() : "null",
                    "message", "Device registered successfully"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to register device: " + e.getMessage()));
        }
    }

    /**
     * List all devices
     * GET /api/admin/devices
     */
    @GetMapping("/devices")
    public ResponseEntity<List<Device>> listDevices() {
        List<Device> devices = deviceService.listDevices();
        return ResponseEntity.ok(devices);
    }

    /**
     * Get device by uniqueId
     * GET /api/admin/devices/{uniqueId}
     */
    @GetMapping("/devices/{uniqueId}")
    public ResponseEntity<?> getDevice(@PathVariable String uniqueId) {
        return deviceService.findByUniqueId(uniqueId)
                .map(device -> ResponseEntity.ok(device))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete a device
     * DELETE /api/admin/devices/{uniqueId}
     */
    @DeleteMapping("/devices/{uniqueId}")
    public ResponseEntity<?> deleteDevice(@PathVariable String uniqueId) {
        try {
            deviceService.deleteDevice(uniqueId);
            return ResponseEntity.ok(Map.of("message", "Device deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to delete device: " + e.getMessage()));
        }
    }
}
