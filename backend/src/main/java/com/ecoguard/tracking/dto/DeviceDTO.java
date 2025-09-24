package com.ecoguard.tracking.dto;

import com.ecoguard.tracking.entity.Device;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceDTO {

    private Long id;
    
    @NotBlank(message = "Partition UUID is required")
    private String partitionUuid;
    
    @NotBlank(message = "Device name is required")
    @Size(min = 2, max = 100, message = "Device name must be between 2 and 100 characters")
    private String name;
    
    @NotBlank(message = "Device model is required")
    private String model;
    
    private Device.DeviceStatus status;
    
    private LocalDateTime registrationDate;
    
    private LocalDateTime lastSeen;
    
    private Double lastLatitude;
    
    private Double lastLongitude;
    
    private Double lastAccuracy;
    
    private boolean hasActiveTheftReport;
    
    private int observationCount;
    
    private LocalDateTime createdAt;
}
