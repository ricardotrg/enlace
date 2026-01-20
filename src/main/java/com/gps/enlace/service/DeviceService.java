package com.gps.enlace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gps.enlace.domain.Device;
import com.gps.enlace.repo.DeviceRepo;
import com.gps.enlace.traccar.TraccarClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class DeviceService {
    private final DeviceRepo deviceRepo;
    private final TraccarClient traccarClient;

    public DeviceService(DeviceRepo deviceRepo, TraccarClient traccarClient) {
        this.deviceRepo = deviceRepo;
        this.traccarClient = traccarClient;
    }

    /**
     * Register a new device in both Traccar and local DB
     * @param uniqueId The GPS device identifier (not necessarily IMEI)
     * @param name Display name for the device
     * @param userId The user ID to associate with this device
     * @return The created device with mapping
     */
    @Transactional
    public Device registerDevice(String uniqueId, String name, Long userId) {
        // Check if device already exists locally
        Optional<Device> existing = deviceRepo.findByUniqueId(uniqueId);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Device with uniqueId " + uniqueId + " already exists");
        }

        // Create device in Traccar
        JsonNode traccarDevice = traccarClient.createDevice(uniqueId, name).block();
        
        if (traccarDevice == null) {
            throw new RuntimeException("Failed to create device in Traccar");
        }

        // Extract Traccar's assigned deviceId
        Long traccarDeviceId = traccarDevice.get("id").asLong();
        
        // Store mapping in local DB
        Device device = new Device();
        device.setTraccarDeviceId(traccarDeviceId);
        device.setUniqueId(uniqueId);
        device.setName(name);
        device.setUserId(userId);
        
        return deviceRepo.save(device);
    }

    /**
     * List all devices from local DB
     */
    public List<Device> listDevices() {
        return deviceRepo.findAll();
    }

    /**
     * Find device by uniqueId
     */
    public Optional<Device> findByUniqueId(String uniqueId) {
        return deviceRepo.findByUniqueId(uniqueId);
    }

    /**
     * Fetch all devices from Traccar (for sync purposes)
     */
    public List<JsonNode> fetchTraccarDevices() {
        return traccarClient.fetchDevices().block();
    }

    /**
     * Delete a device from both Traccar and local DB
     * @param uniqueId The device uniqueId to delete
     */
    @Transactional
    public void deleteDevice(String uniqueId) {
        // Find device locally
        Device device = deviceRepo.findByUniqueId(uniqueId)
                .orElseThrow(() -> new IllegalArgumentException("Device with uniqueId " + uniqueId + " not found"));

        // Delete from Traccar first
        try {
            traccarClient.deleteDevice(device.getTraccarDeviceId()).block();
        } catch (Exception e) {
            System.err.println("Warning: Failed to delete device from Traccar: " + e.getMessage());
            // Continue with local deletion even if Traccar fails
        }

        // Delete from local DB
        deviceRepo.delete(device);
    }
}
