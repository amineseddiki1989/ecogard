package com.ecoguard.tracking.dto;

import com.ecoguard.tracking.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {

    private Long id;
    
    private Long deviceId;
    
    private String deviceName;
    
    private Notification.NotificationType type;
    
    private String title;
    
    private String message;
    
    private boolean read;
    
    private LocalDateTime readAt;
    
    private String actionUrl;
    
    private String actionText;
    
    private LocalDateTime createdAt;
}
