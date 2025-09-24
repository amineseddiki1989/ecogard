package com.ecoguard.tracking.controller;

import com.ecoguard.tracking.dto.DeviceDTO;
import com.ecoguard.tracking.entity.Device;
import com.ecoguard.tracking.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public ResponseEntity<List<DeviceDTO>> getUserDevices(@AuthenticationPrincipal UserDetails userDetails) {
        // In a real application, you would get the user ID from the authenticated user
        // For this prototype, we'll use a hardcoded user ID
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Getting devices for user ID: {}", userId);
        List<DeviceDTO> devices = deviceService.getUserDevices(userId);
        return ResponseEntity.ok(devices);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeviceDTO> getDeviceById(@PathVariable Long id, 
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Getting device with ID: {}", id);
        DeviceDTO device = deviceService.getDeviceById(id);
        return ResponseEntity.ok(device);
    }

    @GetMapping("/partition/{partitionUuid}")
    public ResponseEntity<DeviceDTO> getDeviceByPartitionUuid(@PathVariable String partitionUuid,
                                                            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Getting device with partition UUID: {}", partitionUuid);
        DeviceDTO device = deviceService.getDeviceByPartitionUuid(partitionUuid);
        return ResponseEntity.ok(device);
    }

    @PostMapping
    public ResponseEntity<DeviceDTO> registerDevice(@Valid @RequestBody DeviceDTO deviceDTO,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        // In a real application, you would get the user ID from the authenticated user
        Long userId = 1L; // This would be retrieved from the authenticated user
        
        log.debug("Registering new device for user ID: {}", userId);
        DeviceDTO registeredDevice = deviceService.registerDevice(userId, deviceDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredDevice);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeviceDTO> updateDevice(@PathVariable Long id, 
                                                @Valid @RequestBody DeviceDTO deviceDTO,
                                                @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Updating device with ID: {}", id);
        DeviceDTO updatedDevice = deviceService.updateDevice(id, deviceDTO);
        return ResponseEntity.ok(updatedDevice);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<DeviceDTO> updateDeviceStatus(@PathVariable Long id, 
                                                      @RequestParam Device.DeviceStatus status,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Updating status of device with ID: {} to {}", id, status);
        DeviceDTO updatedDevice = deviceService.updateDeviceStatus(id, status);
        return ResponseEntity.ok(updatedDevice);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDevice(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Deleting device with ID: {}", id);
        deviceService.deleteDevice(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stolen")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DeviceDTO>> getAllStolenDevices() {
        log.debug("Getting all stolen devices");
        List<DeviceDTO> stolenDevices = deviceService.getAllStolenDevices();
        return ResponseEntity.ok(stolenDevices);
    }

    @GetMapping("/stolen/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DeviceDTO>> getAllActivelyStolenDevices() {
        log.debug("Getting all actively stolen devices");
        List<DeviceDTO> activelyStolenDevices = deviceService.getAllActivelyStolenDevices();
        return ResponseEntity.ok(activelyStolenDevices);
    }
}
