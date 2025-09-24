package com.ecoguard.tracking.dto;

import com.ecoguard.tracking.entity.TheftReport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PastOrPresent;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TheftReportDTO {

    private Long id;
    
    @NotNull(message = "Device ID is required")
    private Long deviceId;
    
    private String deviceName;
    
    private String deviceModel;
    
    private LocalDateTime reportedAt;
    
    @NotNull(message = "Theft date is required")
    @PastOrPresent(message = "Theft date cannot be in the future")
    private LocalDateTime theftDate;
    
    private String theftLocation;
    
    private Double theftLatitude;
    
    private Double theftLongitude;
    
    private String circumstances;
    
    private String policeReportNumber;
    
    private boolean policeReportFiled;
    
    private TheftReport.TheftReportStatus status;
    
    private boolean alertSent;
    
    private LocalDateTime alertSentAt;
    
    private LocalDateTime resolvedAt;
    
    private String resolutionNotes;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
