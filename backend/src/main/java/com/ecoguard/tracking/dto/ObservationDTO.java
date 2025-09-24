package com.ecoguard.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservationDTO {

    private Long id;
    
    private Long deviceId;
    
    private String deviceName;
    
    @NotNull(message = "Observation time is required")
    private LocalDateTime observationTime;
    
    @NotNull(message = "Latitude is required")
    @Min(value = -90, message = "Latitude must be between -90 and 90")
    @Max(value = 90, message = "Latitude must be between -90 and 90")
    private Double latitude;
    
    @NotNull(message = "Longitude is required")
    @Min(value = -180, message = "Longitude must be between -180 and 180")
    @Max(value = 180, message = "Longitude must be between -180 and 180")
    private Double longitude;
    
    @NotNull(message = "Accuracy is required")
    @Min(value = 0, message = "Accuracy must be positive")
    private Double accuracy;
    
    @NotNull(message = "Confidence is required")
    @Min(value = 0, message = "Confidence must be between 0 and 100")
    @Max(value = 100, message = "Confidence must be between 0 and 100")
    private Integer confidence;
    
    private String reporterHash;
    
    private Integer signalStrength;
    
    private Integer batteryLevel;
    
    private String networkType;
    
    private String additionalData;
    
    private LocalDateTime createdAt;
}
