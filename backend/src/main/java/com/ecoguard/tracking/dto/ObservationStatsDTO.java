package com.ecoguard.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservationStatsDTO {

    private Long deviceId;
    
    private String deviceName;
    
    private int totalObservations;
    
    private int last24HoursObservations;
    
    private int last7DaysObservations;
    
    private LocalDateTime firstObservation;
    
    private LocalDateTime lastObservation;
    
    private Double averageConfidence;
    
    private int uniqueReporters;
    
    private Double lastLatitude;
    
    private Double lastLongitude;
    
    private Double lastAccuracy;
    
    private Integer lastConfidence;
}
